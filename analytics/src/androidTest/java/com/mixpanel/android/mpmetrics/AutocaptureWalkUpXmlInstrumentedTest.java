package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Future;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumentation tests for walk-up-to-clickable-parent behavior in XML views.
 *
 * <p>Verifies that SemanticExtractor.walkUpToClickableParent() correctly:
 * <ul>
 *   <li>Walks up from non-interactive leaves to the nearest clickable ancestor</li>
 *   <li>Does NOT walk up when the tapped view is itself clickable</li>
 *   <li>Stops at the first (innermost) clickable ancestor in nested clickable hierarchies</li>
 *   <li>Finds clickable ancestors within the 10-level depth limit</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class AutocaptureWalkUpXmlInstrumentedTest {

    private static final String TEST_TOKEN = "WALKUP_XML_TEST_TOKEN";

    private final BlockingQueue<JSONObject> mEvents = new LinkedBlockingQueue<>();
    private MixpanelAPI mMixpanel;
    private MPDbAdapter mMockAdapter;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        // Hard-kill existing AnalyticsMessages instance for clean state
        AnalyticsMessages messages = AnalyticsMessages.getInstance(
                mContext,
                MPConfig.getInstance(mContext, null));
        messages.hardKill();
        for (int i = 0; i < 40 && !messages.isDead(); i++) {
            Thread.sleep(50);
        }

        // Clear events queue
        mEvents.clear();

        // Create mock preferences for test isolation
        final TestUtils.EmptyPreferences mockPreferences = new TestUtils.EmptyPreferences(mContext);

        // Create MPDbAdapter mock that captures events to BlockingQueue
        final MPConfig config = MPConfig.getInstance(mContext, null);
        mMockAdapter = new MPDbAdapter(mContext, config) {
            @Override
            public int addJSON(JSONObject message, String token, MPDbAdapter.Table table) {
                if (table == MPDbAdapter.Table.EVENTS) {
                    try {
                        // Only capture autocapture events ($mp_click, $mp_rage_click, $mp_dead_click)
                        String eventName = message.optString("event", "");
                        if (eventName.startsWith("$mp_")) {
                            mEvents.add(message);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("WalkUpXmlTest", "Error capturing event", e);
                    }
                }
                return super.addJSON(message, token, table);
            }
        };

        // Create custom AnalyticsMessages with mocked adapter
        final AnalyticsMessages customMessages = new AnalyticsMessages(mContext, config) {
            @Override
            protected MPDbAdapter makeDbAdapter(Context context) {
                return mMockAdapter;
            }
        };

        // Configure autocapture options (all features enabled)
        AutocaptureOptions autocaptureOptions = new AutocaptureOptions.Builder().build();

        MixpanelOptions options = new MixpanelOptions.Builder()
                .autocaptureOptions(autocaptureOptions)
                .build();

        // Create MixpanelAPI with trackAutomaticEvents=true
        mMixpanel = new MixpanelAPI(mContext, mockPreferences, TEST_TOKEN, config, options, true) {
            @Override
            AnalyticsMessages getAnalyticsMessages() {
                return customMessages;
            }

            @Override
            PersistentIdentity getPersistentIdentity(
                    final Context context,
                    final Future<SharedPreferences> referrerPreferences,
                    final String token,
                    final String instanceName,
                    final DeviceIdProvider deviceIdProvider) {
                String instanceKey = instanceName != null ? instanceName : token;
                final String prefsName = "com.mixpanel.android.mpmetrics.MixpanelAPI_" + instanceKey;
                final SharedPreferences ret = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
                ret.edit().clear().commit();

                final String timeEventsPrefsName = "com.mixpanel.android.mpmetrics.MixpanelAPI.TimeEvents_" + instanceKey;
                final SharedPreferences timeSharedPrefs = context.getSharedPreferences(timeEventsPrefsName, Context.MODE_PRIVATE);
                timeSharedPrefs.edit().clear().commit();

                final String mixpanelPrefsName = "com.mixpanel.android.mpmetrics.Mixpanel";
                final SharedPreferences mpSharedPrefs = context.getSharedPreferences(mixpanelPrefsName, Context.MODE_PRIVATE);
                mpSharedPrefs.edit().clear().putBoolean(token, true).putBoolean("has_launched", true).apply();

                return super.getPersistentIdentity(context, referrerPreferences, token, instanceName, deviceIdProvider);
            }
        };
    }

    @After
    public void tearDown() throws Exception {
        if (mMixpanel != null) {
            // Clear super properties
            mMixpanel.clearSuperProperties();

            // Flush any pending events
            mMixpanel.flush();
        }

        // Clear events queue
        mEvents.clear();

        // Delete Mixpanel database
        mContext.deleteDatabase("mixpanel");

        // Clean up Mixpanel data
        TestUtils.cleanUpMixpanelData(mContext);
    }

    /**
     * Sends a tap (DOWN + UP) at the center of the view with the given ID.
     */
    private void tapViewById(ActivityScenario<WalkUpXmlTestActivity> scenario, int viewId) {
        final int[] location = new int[2];
        scenario.onActivity(activity -> {
            View view = activity.findViewById(viewId);
            view.getLocationOnScreen(location);
            location[0] += view.getWidth() / 2;
            location[1] += view.getHeight() / 2;
        });

        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        long downTime = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(
                downTime, downTime, android.view.MotionEvent.ACTION_DOWN,
                location[0], location[1], 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(
                downTime, downTime + 50, android.view.MotionEvent.ACTION_UP,
                location[0], location[1], 0);
        instrumentation.sendPointerSync(down);
        instrumentation.sendPointerSync(up);
        down.recycle();
        up.recycle();
    }

    /**
     * Tapping a non-interactive TextView (no contentDescription) inside a clickable container
     * should walk up to the clickable parent and use the parent's contentDescription as $el_id.
     */
    @Test
    public void testWalkUp_NonInteractiveLeafNoIdentity_GetsParentId() throws Exception {
        try (ActivityScenario<WalkUpXmlTestActivity> scenario =
                     ActivityScenario.launch(WalkUpXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            tapViewById(scenario, WalkUpXmlTestActivity.ID_BASIC_LEAF);

            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Click event should be captured", event);
            assertEquals("$mp_click", event.getString("event"));

            JSONObject properties = event.getJSONObject("properties");
            assertEquals("card_container", properties.getString("$el_id"));
        }
    }

    /**
     * Tapping a non-interactive TextView that has its own contentDescription inside a clickable
     * container should still walk up to the clickable parent. The leaf's contentDescription is
     * ignored because the leaf is not interactive.
     */
    @Test
    public void testWalkUp_NonInteractiveLeafWithContentDescription_GetsParentId() throws Exception {
        try (ActivityScenario<WalkUpXmlTestActivity> scenario =
                     ActivityScenario.launch(WalkUpXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            tapViewById(scenario, WalkUpXmlTestActivity.ID_LABELED_LEAF);

            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Click event should be captured", event);
            assertEquals("$mp_click", event.getString("event"));

            JSONObject properties = event.getJSONObject("properties");
            assertEquals("parent_of_labeled", properties.getString("$el_id"));
        }
    }

    /**
     * Tapping a clickable View that has no contentDescription or valid resource ID inside a
     * clickable parent should NOT walk up. The leaf is clickable so it owns its click.
     * Falls back to hash-based $el_id.
     */
    @Test
    public void testNoWalkUp_ClickableLeafNoIdentity_GetsOwnHash() throws Exception {
        try (ActivityScenario<WalkUpXmlTestActivity> scenario =
                     ActivityScenario.launch(WalkUpXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            tapViewById(scenario, WalkUpXmlTestActivity.ID_CLICKABLE_LEAF_NO_ID);

            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Click event should be captured", event);
            assertEquals("$mp_click", event.getString("event"));

            JSONObject properties = event.getJSONObject("properties");
            String elId = properties.getString("$el_id");
            assertTrue("$el_id should use hash fallback format View_<hex>, got: " + elId,
                    elId.matches("View_[0-9a-f]+"));
        }
    }

    /**
     * Tapping a clickable Button with contentDescription inside a clickable parent should NOT
     * walk up. The leaf is clickable so it owns its click and uses its own contentDescription.
     */
    @Test
    public void testNoWalkUp_ClickableLeafWithIdentity_GetsOwnId() throws Exception {
        try (ActivityScenario<WalkUpXmlTestActivity> scenario =
                     ActivityScenario.launch(WalkUpXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            tapViewById(scenario, WalkUpXmlTestActivity.ID_CLICKABLE_LEAF_WITH_ID);

            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Click event should be captured", event);
            assertEquals("$mp_click", event.getString("event"));

            JSONObject properties = event.getJSONObject("properties");
            assertEquals("inner_clickable_btn", properties.getString("$el_id"));
        }
    }

    /**
     * Tapping a non-interactive TextView with no contentDescription inside a non-clickable
     * container (no clickable ancestor at all) should fall back to hash-based $el_id.
     */
    @Test
    public void testNoWalkUp_NonInteractiveLeafNoClickableAncestor_HashFallback() throws Exception {
        try (ActivityScenario<WalkUpXmlTestActivity> scenario =
                     ActivityScenario.launch(WalkUpXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            tapViewById(scenario, WalkUpXmlTestActivity.ID_NON_CLICKABLE_LEAF);

            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Click event should be captured", event);
            assertEquals("$mp_click", event.getString("event"));

            JSONObject properties = event.getJSONObject("properties");
            String elId = properties.getString("$el_id");
            assertTrue("$el_id should use hash fallback format TextView_<hex>, got: " + elId,
                    elId.matches("TextView_[0-9a-f]+"));
        }
    }

    /**
     * Tapping a non-interactive TextView with contentDescription inside a non-clickable
     * container should use the leaf's own contentDescription since there is no clickable
     * ancestor to walk up to.
     */
    @Test
    public void testNoWalkUp_NonInteractiveLeafWithIdentityNoClickableAncestor_GetsOwnId() throws Exception {
        try (ActivityScenario<WalkUpXmlTestActivity> scenario =
                     ActivityScenario.launch(WalkUpXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            tapViewById(scenario, WalkUpXmlTestActivity.ID_NON_CLICKABLE_LABELED_LEAF);

            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Click event should be captured", event);
            assertEquals("$mp_click", event.getString("event"));

            JSONObject properties = event.getJSONObject("properties");
            assertEquals("orphan_label", properties.getString("$el_id"));
        }
    }

    /**
     * Tapping a non-interactive leaf inside nested clickables (outer > inner > leaf) should
     * walk up and stop at the innermost clickable parent, not the outer one.
     */
    @Test
    public void testWalkUp_NestedClickables_StopsAtInner() throws Exception {
        try (ActivityScenario<WalkUpXmlTestActivity> scenario =
                     ActivityScenario.launch(WalkUpXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            tapViewById(scenario, WalkUpXmlTestActivity.ID_INNER_LEAF);

            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Click event should be captured", event);
            assertEquals("$mp_click", event.getString("event"));

            JSONObject properties = event.getJSONObject("properties");
            assertEquals("inner_clickable", properties.getString("$el_id"));
        }
    }

    /**
     * Tapping a non-interactive leaf that is 9 levels deep inside a clickable ancestor should
     * successfully walk up and find the clickable parent (within the 10-level limit).
     */
    @Test
    public void testWalkUp_DeepNesting_FindsClickableWithin10Levels() throws Exception {
        try (ActivityScenario<WalkUpXmlTestActivity> scenario =
                     ActivityScenario.launch(WalkUpXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            tapViewById(scenario, WalkUpXmlTestActivity.ID_DEEP_LEAF);

            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Click event should be captured", event);
            assertEquals("$mp_click", event.getString("event"));

            JSONObject properties = event.getJSONObject("properties");
            assertEquals("deep_parent", properties.getString("$el_id"));
        }
    }

    /**
     * Tapping a non-interactive leaf inside a disabled clickable parent should stop at
     * the disabled parent — it's still clickable, so it owns the click identity.
     * A disabled delete button in a product card should still collect its own identity
     * for dead click tracking.
     */
    @Test
    public void testWalkUp_DisabledClickableParent_StopsAtDisabledParent() throws Exception {
        try (ActivityScenario<WalkUpXmlTestActivity> scenario =
                     ActivityScenario.launch(WalkUpXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            tapViewById(scenario, WalkUpXmlTestActivity.ID_DISABLED_LEAF);

            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Click event should be captured", event);
            assertEquals("$mp_click", event.getString("event"));

            JSONObject properties = event.getJSONObject("properties");
            assertEquals("disabled_parent", properties.getString("$el_id"));
        }
    }

    /**
     * Tapping a disabled clickable button directly should keep its own identity.
     * A disabled button is still clickable, so no walk-up occurs — the user is
     * specifically targeting that element (e.g. rage-tapping a disabled delete button).
     */
    @Test
    public void testNoWalkUp_DisabledButtonTappedDirectly_KeepsOwnId() throws Exception {
        try (ActivityScenario<WalkUpXmlTestActivity> scenario =
                     ActivityScenario.launch(WalkUpXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            tapViewById(scenario, WalkUpXmlTestActivity.ID_DISABLED_PARENT);

            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Click event should be captured", event);
            assertEquals("$mp_click", event.getString("event"));

            JSONObject properties = event.getJSONObject("properties");
            assertEquals("disabled_parent", properties.getString("$el_id"));
        }
    }
}
