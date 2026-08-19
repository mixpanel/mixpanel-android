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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumentation tests for autocapture functionality.
 *
 * <p>These tests verify the happy path scenarios for click, rage click, and dead click detection.
 * They follow the SDK's established testing patterns using BlockingQueue for async verification
 * and CleanMixpanelAPI for test isolation.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class AutocaptureXmlInstrumentedTest {

    private static final String TEST_TOKEN = "AUTOCAPTURE_TEST_TOKEN";

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
                        android.util.Log.e("AutocaptureTest", "Error capturing event", e);
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

        // Create MixpanelAPI with trackAutomaticEvents=true for most tests
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

    @Test
    public void testXmlClickEventBasic() throws Exception {
        try (ActivityScenario<AutocaptureXmlTestActivity> scenario =
                     ActivityScenario.launch(AutocaptureXmlTestActivity.class)) {

            // Wait for autocapture to attach to window
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // Click the rule1_btn button
            onView(withId(AutocaptureXmlTestActivity.ID_RULE1_BTN)).perform(click());

            // Poll for the event with 10-second timeout
            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Click event should be captured", event);

            // Verify event name
            assertEquals("$mp_click", event.getString("event"));

            // Verify event properties
            JSONObject properties = event.getJSONObject("properties");
            assertNotNull("Event properties should exist", properties);

            // Verify element ID uses contentDescription (Rule 1)
            assertEquals("rule1_btn", properties.getString("$el_id"));

            // Verify element tag name
            assertEquals("Button", properties.getString("$el_tag_name"));

            // Verify coordinates are present and non-negative
            assertTrue("X coordinate should be non-negative",
                    properties.getDouble("$x") >= 0);
            assertTrue("Y coordinate should be non-negative",
                    properties.getDouble("$y") >= 0);
        }
    }

    @Test
    public void testRageClickDetection() throws Exception {
        try (ActivityScenario<AutocaptureXmlTestActivity> scenario =
                     ActivityScenario.launch(AutocaptureXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // Get rage zone coordinates for rapid touch injection
            final int[] location = new int[2];
            scenario.onActivity(activity -> {
                View rageZone = activity.findViewById(AutocaptureXmlTestActivity.ID_RAGE_ZONE);
                rageZone.getLocationOnScreen(location);
                location[0] += rageZone.getWidth() / 2;
                location[1] += rageZone.getHeight() / 2;
            });

            // Inject 4 rapid tap events using Instrumentation (bypasses Espresso UI idle wait)
            android.app.Instrumentation instrumentation =
                    InstrumentationRegistry.getInstrumentation();
            for (int i = 0; i < 4; i++) {
                long downTime = android.os.SystemClock.uptimeMillis();
                android.view.MotionEvent down = android.view.MotionEvent.obtain(
                        downTime, downTime,
                        android.view.MotionEvent.ACTION_DOWN,
                        location[0], location[1], 0);
                android.view.MotionEvent up = android.view.MotionEvent.obtain(
                        downTime, downTime + 10,
                        android.view.MotionEvent.ACTION_UP,
                        location[0], location[1], 0);
                instrumentation.sendPointerSync(down);
                instrumentation.sendPointerSync(up);
                down.recycle();
                up.recycle();
            }

            // Collect all events
            java.util.List<JSONObject> events = new java.util.ArrayList<>();
            JSONObject event;
            while ((event = mEvents.poll(2, TimeUnit.SECONDS)) != null) {
                events.add(event);
            }

            // Find rage click event
            JSONObject rageClickEvent = null;
            for (JSONObject e : events) {
                if ("$mp_rage_click".equals(e.getString("event"))) {
                    rageClickEvent = e;
                    break;
                }
            }

            // Assert rage click event exists
            assertNotNull("Rage click event should be captured", rageClickEvent);

            // Verify rage click event properties
            JSONObject properties = rageClickEvent.getJSONObject("properties");
            assertEquals("rage_zone", properties.getString("$el_id"));
            assertEquals("View", properties.getString("$el_tag_name"));
        }
    }

    @Test
    public void testDeadClickDetection() throws Exception {
        try (ActivityScenario<AutocaptureXmlTestActivity> scenario =
                     ActivityScenario.launch(AutocaptureXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // Click the dead button (has NO click listener)
            onView(withId(AutocaptureXmlTestActivity.ID_DEAD_XML_BTN)).perform(click());

            // First, collect the $mp_click event
            JSONObject clickEvent = mEvents.poll(2, TimeUnit.SECONDS);
            assertNotNull("Click event should be captured first", clickEvent);
            assertEquals("$mp_click", clickEvent.getString("event"));

            // Now wait for the dead click event (1000ms detection delay)
            JSONObject deadClickEvent = mEvents.poll(3, TimeUnit.SECONDS);
            assertNotNull("Dead click event should be captured", deadClickEvent);

            // Verify event name
            assertEquals("$mp_dead_click", deadClickEvent.getString("event"));

            // Verify properties match dead button
            JSONObject properties = deadClickEvent.getJSONObject("properties");
            assertEquals("dead_xml_btn", properties.getString("$el_id"));
            assertEquals("Button", properties.getString("$el_tag_name"));
        }
    }

    @Test
    public void testElementIdResolutionRule2() throws Exception {
        try (ActivityScenario<AutocaptureXmlTestActivity> scenario =
                     ActivityScenario.launch(AutocaptureXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // Click button with resource ID only (no contentDescription)
            onView(withId(AutocaptureXmlTestActivity.ID_RULE2_BTN)).perform(click());

            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Event should be captured", event);

            JSONObject properties = event.getJSONObject("properties");

            // Verify $el_id uses resource ID name (android.R.id.button1 resolves to "button1")
            assertEquals("button1", properties.getString("$el_id"));
        }
    }

    @Test
    public void testMultipleClicksGenerateMultipleEvents() throws Exception {
        try (ActivityScenario<AutocaptureXmlTestActivity> scenario =
                     ActivityScenario.launch(AutocaptureXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // Click 3 different buttons, waiting for idle between each
            onView(withId(AutocaptureXmlTestActivity.ID_RULE1_BTN)).perform(click());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            onView(withId(AutocaptureXmlTestActivity.ID_RULE2_BTN)).perform(click());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            onView(withId(AutocaptureXmlTestActivity.ID_RULE3_BTN)).perform(click());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // Collect all events and filter for $mp_click only
            // (dead click events may also fire since empty listeners cause no UI change)
            java.util.List<JSONObject> clickEvents = new java.util.ArrayList<>();
            JSONObject event;
            while ((event = mEvents.poll(2, TimeUnit.SECONDS)) != null) {
                if ("$mp_click".equals(event.getString("event"))) {
                    clickEvents.add(event);
                }
            }

            // Assert exactly 3 click events captured
            assertEquals("Should capture exactly 3 click events", 3, clickEvents.size());
        }
    }

    @Test
    public void testClickEventHasTokenProperty() throws Exception {
        try (ActivityScenario<AutocaptureXmlTestActivity> scenario =
                     ActivityScenario.launch(AutocaptureXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // Click a button
            onView(withId(AutocaptureXmlTestActivity.ID_RULE1_BTN)).perform(click());

            // Poll for the event
            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Event should be captured", event);

            // Verify standard Mixpanel properties exist
            JSONObject properties = event.getJSONObject("properties");

            // Assert distinct_id exists
            assertTrue("distinct_id should exist", properties.has("distinct_id"));
            assertNotNull("distinct_id should not be null", properties.get("distinct_id"));

            // Assert token matches (token is in properties)
            assertEquals("Token should match", TEST_TOKEN, properties.getString("token"));
        }
    }

    // Note: Dialog/BottomSheet/Popup autocapture tests require the full Application lifecycle
    // (WindowSpy + ActivityLifecycleCallbacks) which isn't available in instrumented tests
    // with a mock MixpanelAPI. These are tested manually via the demo app's
    // "Multi-Window / Overlay" section in both Compose and XML test screens.

    /**
     * Regression test: autocapture events must NOT be gated by trackAutomaticEvents flag.
     * The trackAutomaticEvents flag controls legacy $ae_ lifecycle events only.
     * Autocapture events ($mp_click, $mp_rage_click, $mp_dead_click) must flow
     * regardless of this flag.
     *
     * @see <a href="https://github.com/mixpanel/mixpanel-android/pull/982#issuecomment-4860033738">PR #982 review - Issue 1</a>
     */
    @Test
    public void testAutocaptureEventsNotDroppedWhenTrackAutomaticEventsFalse() throws Exception {
        // Create a separate MixpanelAPI with trackAutomaticEvents=false
        final BlockingQueue<JSONObject> events = new LinkedBlockingQueue<>();
        final TestUtils.EmptyPreferences prefs = new TestUtils.EmptyPreferences(mContext);
        final MPConfig config = MPConfig.getInstance(mContext, null);

        final MPDbAdapter adapter = new MPDbAdapter(mContext, config) {
            @Override
            public int addJSON(JSONObject message, String token, MPDbAdapter.Table table) {
                if (table == MPDbAdapter.Table.EVENTS) {
                    try {
                        String eventName = message.optString("event", "");
                        if (eventName.startsWith("$mp_")) {
                            events.add(message);
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
                return super.addJSON(message, token, table);
            }
        };

        final AnalyticsMessages messages = new AnalyticsMessages(mContext, config) {
            @Override
            protected MPDbAdapter makeDbAdapter(Context context) {
                return adapter;
            }
        };

        AutocaptureOptions autocaptureOpts = new AutocaptureOptions.Builder().build();
        MixpanelOptions opts = new MixpanelOptions.Builder()
                .autocaptureOptions(autocaptureOpts)
                .build();

        // trackAutomaticEvents = FALSE
        MixpanelAPI api = new MixpanelAPI(mContext, prefs, "TRACK_AUTO_FALSE_TOKEN", config, opts, false) {
            @Override
            AnalyticsMessages getAnalyticsMessages() {
                return messages;
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

        try (ActivityScenario<AutocaptureXmlTestActivity> scenario =
                     ActivityScenario.launch(AutocaptureXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            onView(withId(AutocaptureXmlTestActivity.ID_RULE1_BTN)).perform(click());

            JSONObject event = events.poll(10, TimeUnit.SECONDS);
            assertNotNull(
                    "Autocapture $mp_click must not be dropped when trackAutomaticEvents=false",
                    event);
            assertEquals("$mp_click", event.getString("event"));
        } finally {
            api.clearSuperProperties();
            api.flush();
        }
    }

    /**
     * Regression test: a swipe that returns to the starting position must NOT register as a tap.
     *
     * Before the fix, touch slop was only checked at ACTION_UP by comparing final vs initial
     * position. A quick down-swipe-up-swipe that ended at the start point would falsely pass
     * the check. Now, ACTION_MOVE events are tracked and a tap is rejected if any intermediate
     * move exceeds the touch slop threshold.
     */
    @Test
    public void testSwipeBackToSamePositionDoesNotFireClick() throws Exception {
        try (ActivityScenario<AutocaptureXmlTestActivity> scenario =
                     ActivityScenario.launch(AutocaptureXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // Get button center coordinates
            final int[] location = new int[2];
            scenario.onActivity(activity -> {
                View btn = activity.findViewById(AutocaptureXmlTestActivity.ID_RULE1_BTN);
                btn.getLocationOnScreen(location);
                location[0] += btn.getWidth() / 2;
                location[1] += btn.getHeight() / 2;
            });

            android.app.Instrumentation instrumentation =
                    InstrumentationRegistry.getInstrumentation();
            long downTime = android.os.SystemClock.uptimeMillis();

            // ACTION_DOWN at button center
            android.view.MotionEvent down = android.view.MotionEvent.obtain(
                    downTime, downTime,
                    android.view.MotionEvent.ACTION_DOWN,
                    location[0], location[1], 0);
            instrumentation.sendPointerSync(down);
            down.recycle();

            // ACTION_MOVE 200px down (well beyond touch slop)
            android.view.MotionEvent move1 = android.view.MotionEvent.obtain(
                    downTime, downTime + 50,
                    android.view.MotionEvent.ACTION_MOVE,
                    location[0], location[1] + 200, 0);
            instrumentation.sendPointerSync(move1);
            move1.recycle();

            // ACTION_MOVE back to original position
            android.view.MotionEvent move2 = android.view.MotionEvent.obtain(
                    downTime, downTime + 100,
                    android.view.MotionEvent.ACTION_MOVE,
                    location[0], location[1], 0);
            instrumentation.sendPointerSync(move2);
            move2.recycle();

            // ACTION_UP at original position
            android.view.MotionEvent up = android.view.MotionEvent.obtain(
                    downTime, downTime + 150,
                    android.view.MotionEvent.ACTION_UP,
                    location[0], location[1], 0);
            instrumentation.sendPointerSync(up);
            up.recycle();

            // Wait briefly — no $mp_click event should fire
            JSONObject event = mEvents.poll(2, TimeUnit.SECONDS);
            assertTrue(
                    "Swipe-back-to-same-position should NOT fire $mp_click event",
                    event == null || !"$mp_click".equals(event.optString("event")));
        }
    }

    /**
     * A clickable container with child text but no contentDescription must not leak
     * the child's visible text into $attr-aria-label or $el_id.
     *
     * <p>This simulates a React Native Pressable where the developer did not set
     * accessibilityLabel — the container has visible child text but contentDescription
     * is null. The SDK must never read child text as a substitute.
     */
    @Test
    public void testNullContentDescription_ChildTextDoesNotLeakIntoAriaLabel() throws Exception {
        try (ActivityScenario<AutocaptureXmlTestActivity> scenario =
                     ActivityScenario.launch(AutocaptureXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // Tap the container that has a child label but NO contentDescription
            final int[] location = new int[2];
            scenario.onActivity(activity -> {
                View v = activity.findViewById(AutocaptureXmlTestActivity.ID_NOT_IMPORTANT_VIEW);
                v.getLocationOnScreen(location);
                location[0] += v.getWidth() / 2;
                location[1] += v.getHeight() / 2;
            });

            android.app.Instrumentation instrumentation =
                    InstrumentationRegistry.getInstrumentation();
            long downTime = android.os.SystemClock.uptimeMillis();
            android.view.MotionEvent down = android.view.MotionEvent.obtain(
                    downTime, downTime,
                    android.view.MotionEvent.ACTION_DOWN,
                    location[0], location[1], 0);
            android.view.MotionEvent up = android.view.MotionEvent.obtain(
                    downTime, downTime + 10,
                    android.view.MotionEvent.ACTION_UP,
                    location[0], location[1], 0);
            instrumentation.sendPointerSync(down);
            instrumentation.sendPointerSync(up);
            down.recycle();
            up.recycle();

            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Click event should still be captured", event);
            assertEquals("$mp_click", event.getString("event"));

            JSONObject properties = event.getJSONObject("properties");

            // $el_id must NOT contain the child label text
            String elId = properties.getString("$el_id");
            assertTrue(
                    "$el_id should not contain child text. Got: " + elId,
                    !elId.contains("Sensitive"));

            // $attr-aria-label must be absent — null contentDescription means no aria-label
            assertTrue(
                    "$attr-aria-label must not be present when contentDescription is null",
                    !properties.has("$attr-aria-label"));
        }
    }

    // ============ Accessibility Guard Tests ============

    /**
     * Scenario 1: contentDescription is null → child text must NOT leak.
     *
     * <p>This is the existing test (testNullContentDescription_ChildTextDoesNotLeakIntoAriaLabel)
     * but listed here for completeness. The ID_NOT_IMPORTANT_VIEW element has
     * importantForAccessibility=AUTO (default), contentDescription=null, and a child
     * TextView with "Sensitive Account 1234".
     *
     * @see #testNullContentDescription_ChildTextDoesNotLeakIntoAriaLabel()
     */

    /**
     * Scenario 2: View IS important for accessibility, but contentDescription is null.
     * Child text must NOT leak into $el_id or $attr-aria-label.
     *
     * <p>Simulates a React Native Pressable with accessible={true} but no
     * accessibilityLabel set. importantForAccessibility defaults to YES.
     * The view's child text must never be used as a substitute for contentDescription.
     */
    @Test
    public void testAccessibleNoCd_ChildTextDoesNotLeak() throws Exception {
        try (ActivityScenario<AutocaptureXmlTestActivity> scenario =
                     ActivityScenario.launch(AutocaptureXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            final int[] location = new int[2];
            scenario.onActivity(activity -> {
                View v = activity.findViewById(AutocaptureXmlTestActivity.ID_ACCESSIBLE_NO_CD);
                v.getLocationOnScreen(location);
                location[0] += v.getWidth() / 2;
                location[1] += v.getHeight() / 2;
            });

            android.app.Instrumentation instrumentation =
                    InstrumentationRegistry.getInstrumentation();
            long downTime = android.os.SystemClock.uptimeMillis();
            android.view.MotionEvent down = android.view.MotionEvent.obtain(
                    downTime, downTime,
                    android.view.MotionEvent.ACTION_DOWN,
                    location[0], location[1], 0);
            android.view.MotionEvent up = android.view.MotionEvent.obtain(
                    downTime, downTime + 10,
                    android.view.MotionEvent.ACTION_UP,
                    location[0], location[1], 0);
            instrumentation.sendPointerSync(down);
            instrumentation.sendPointerSync(up);
            down.recycle();
            up.recycle();

            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Click event should still be captured", event);
            assertEquals("$mp_click", event.getString("event"));

            JSONObject properties = event.getJSONObject("properties");

            String elId = properties.getString("$el_id");
            assertTrue(
                    "$el_id should not contain child text. Got: " + elId,
                    !elId.contains("Sensitive") && !elId.contains("5678"));

            assertTrue(
                    "$attr-aria-label must not be present when contentDescription is null",
                    !properties.has("$attr-aria-label"));
        }
    }

    /**
     * Scenario 3: View is NOT important for accessibility AND has no contentDescription.
     * Child text must NOT leak into $el_id or $attr-aria-label.
     *
     * <p>This is covered by testNullContentDescription_ChildTextDoesNotLeakIntoAriaLabel()
     * which uses ID_NOT_IMPORTANT_VIEW (importantForAccessibility=AUTO with no
     * contentDescription). Listed for completeness.
     *
     * @see #testNullContentDescription_ChildTextDoesNotLeakIntoAriaLabel()
     */

    /**
     * Scenario 4: View is NOT important for accessibility but HAS a contentDescription.
     * The contentDescription must NOT appear in $el_id or $attr-aria-label because
     * isImportantForAccessibility() returns false.
     */
    @Test
    public void testNotImportantWithCd_ContentDescDoesNotLeak() throws Exception {
        try (ActivityScenario<AutocaptureXmlTestActivity> scenario =
                     ActivityScenario.launch(AutocaptureXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            final int[] location = new int[2];
            scenario.onActivity(activity -> {
                View v = activity.findViewById(AutocaptureXmlTestActivity.ID_NOT_IMPORTANT_WITH_CD);
                v.getLocationOnScreen(location);
                location[0] += v.getWidth() / 2;
                location[1] += v.getHeight() / 2;
            });

            android.app.Instrumentation instrumentation =
                    InstrumentationRegistry.getInstrumentation();
            long downTime = android.os.SystemClock.uptimeMillis();
            android.view.MotionEvent down = android.view.MotionEvent.obtain(
                    downTime, downTime,
                    android.view.MotionEvent.ACTION_DOWN,
                    location[0], location[1], 0);
            android.view.MotionEvent up = android.view.MotionEvent.obtain(
                    downTime, downTime + 10,
                    android.view.MotionEvent.ACTION_UP,
                    location[0], location[1], 0);
            instrumentation.sendPointerSync(down);
            instrumentation.sendPointerSync(up);
            down.recycle();
            up.recycle();

            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Click event should still be captured", event);
            assertEquals("$mp_click", event.getString("event"));

            JSONObject properties = event.getJSONObject("properties");

            // Neither contentDescription nor child text must appear in $el_id
            String elId = properties.getString("$el_id");
            assertTrue(
                    "$el_id should not contain contentDescription. Got: " + elId,
                    !elId.contains("Sensitive") && !elId.contains("9999"));
            assertTrue(
                    "$el_id should not contain child text. Got: " + elId,
                    !elId.contains("Some Label"));

            // $attr-aria-label must be absent — neither contentDescription nor child text
            assertTrue(
                    "$attr-aria-label must not be present when view is not important for accessibility",
                    !properties.has("$attr-aria-label"));
        }
    }

    /**
     * Positive case: View IS important for accessibility AND HAS contentDescription.
     * The contentDescription SHOULD appear in both $el_id and $attr-aria-label.
     */
    @Test
    public void testAccessibleWithCd_ContentDescIsCaptured() throws Exception {
        try (ActivityScenario<AutocaptureXmlTestActivity> scenario =
                     ActivityScenario.launch(AutocaptureXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            final int[] location = new int[2];
            scenario.onActivity(activity -> {
                View v = activity.findViewById(AutocaptureXmlTestActivity.ID_ACCESSIBLE_WITH_CD);
                v.getLocationOnScreen(location);
                location[0] += v.getWidth() / 2;
                location[1] += v.getHeight() / 2;
            });

            android.app.Instrumentation instrumentation =
                    InstrumentationRegistry.getInstrumentation();
            long downTime = android.os.SystemClock.uptimeMillis();
            android.view.MotionEvent down = android.view.MotionEvent.obtain(
                    downTime, downTime,
                    android.view.MotionEvent.ACTION_DOWN,
                    location[0], location[1], 0);
            android.view.MotionEvent up = android.view.MotionEvent.obtain(
                    downTime, downTime + 10,
                    android.view.MotionEvent.ACTION_UP,
                    location[0], location[1], 0);
            instrumentation.sendPointerSync(down);
            instrumentation.sendPointerSync(up);
            down.recycle();
            up.recycle();

            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Click event should still be captured", event);
            assertEquals("$mp_click", event.getString("event"));

            JSONObject properties = event.getJSONObject("properties");

            // contentDescription SHOULD be used as $el_id
            assertEquals("Intended Label", properties.getString("$el_id"));

            // $attr-aria-label SHOULD be present
            assertEquals("Intended Label", properties.getString("$attr-aria-label"));
        }
    }

    // ============ Visibility Tests ============

    /**
     * An INVISIBLE view must not produce any autocapture event.
     * View.INVISIBLE means the view is not drawn but still occupies layout space.
     * Tapping at its coordinates should not capture its contentDescription.
     */
    @Test
    public void testInvisibleView_NoEventCaptured() throws Exception {
        try (ActivityScenario<AutocaptureXmlTestActivity> scenario =
                     ActivityScenario.launch(AutocaptureXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // The invisible button sits far down the activity's ScrollView, so its on-screen
            // coordinates start out below the window. Scroll it into the viewport first:
            // Android 14+ uses targeted input injection, which rejects events aimed at a point
            // outside a window owned by this process ("Targeted input event injection ... was not
            // directed at a window owned by uid ..."). INVISIBLE views still occupy layout space,
            // so ScrollView scrolls to them normally.
            scenario.onActivity(activity -> {
                View v = activity.findViewById(AutocaptureXmlTestActivity.ID_INVISIBLE_BTN);
                if (v != null && v.getWidth() > 0 && v.getHeight() > 0) {
                    v.requestRectangleOnScreen(
                            new android.graphics.Rect(0, 0, v.getWidth(), v.getHeight()), true);
                }
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // Get the location where the invisible button would be
            final int[] location = new int[2];
            final boolean[] found = {false};
            scenario.onActivity(activity -> {
                View v = activity.findViewById(AutocaptureXmlTestActivity.ID_INVISIBLE_BTN);
                if (v != null && v.getWidth() > 0) {
                    v.getLocationOnScreen(location);
                    location[0] += v.getWidth() / 2;
                    location[1] += v.getHeight() / 2;
                    // Only inject when the point lands inside this app's own content area.
                    // Points over the system bars belong to another uid, and points off-screen
                    // belong to no window at all — both make sendPointerSync throw.
                    android.graphics.Rect contentFrame = new android.graphics.Rect();
                    activity.getWindow().getDecorView()
                            .getWindowVisibleDisplayFrame(contentFrame);
                    found[0] = contentFrame.contains(location[0], location[1]);
                }
            });

            if (!found[0]) {
                // INVISIBLE view has no tappable on-screen area — nothing to tap, nothing to assert
                return;
            }

            android.app.Instrumentation instrumentation =
                    InstrumentationRegistry.getInstrumentation();
            long downTime = android.os.SystemClock.uptimeMillis();
            android.view.MotionEvent down = android.view.MotionEvent.obtain(
                    downTime, downTime,
                    android.view.MotionEvent.ACTION_DOWN,
                    location[0], location[1], 0);
            android.view.MotionEvent up = android.view.MotionEvent.obtain(
                    downTime, downTime + 10,
                    android.view.MotionEvent.ACTION_UP,
                    location[0], location[1], 0);
            instrumentation.sendPointerSync(down);
            instrumentation.sendPointerSync(up);
            down.recycle();
            up.recycle();

            // Wait briefly — should get no event, or at least not one with the invisible view's identity
            JSONObject event = mEvents.poll(2, TimeUnit.SECONDS);
            if (event != null) {
                JSONObject properties = event.getJSONObject("properties");
                String elId = properties.optString("$el_id", "");
                String ariaLabel = properties.optString("$attr-aria-label", "");
                assertTrue(
                        "Invisible view's contentDescription must not appear in $el_id. Got: " + elId,
                        !elId.contains("invisible_btn"));
                assertTrue(
                        "Invisible view's contentDescription must not appear in $attr-aria-label. Got: " + ariaLabel,
                        !ariaLabel.contains("invisible_btn"));
            }
            // If event is null, test passes — no event captured for invisible view
        }
    }

    /**
     * A zero-alpha view (fully transparent) must not produce autocapture events
     * with its identity. The view is in the layout but invisible to the user.
     */
    @Test
    public void testZeroAlphaView_NoEventCaptured() throws Exception {
        try (ActivityScenario<AutocaptureXmlTestActivity> scenario =
                     ActivityScenario.launch(AutocaptureXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // Use performClick() directly since Android won't inject touch events
            // into zero-alpha views (they fail with IllegalArgumentException).
            // performClick() bypasses the visibility check and triggers the click listener,
            // which lets us verify our autocapture code properly guards against invisible views.
            scenario.onActivity(activity -> {
                View v = activity.findViewById(AutocaptureXmlTestActivity.ID_ZERO_ALPHA_BTN);
                if (v != null) {
                    v.performClick();
                }
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            JSONObject event = mEvents.poll(2, TimeUnit.SECONDS);
            if (event != null) {
                JSONObject properties = event.getJSONObject("properties");
                String elId = properties.optString("$el_id", "");
                String ariaLabel = properties.optString("$attr-aria-label", "");
                assertTrue(
                        "Zero-alpha view's contentDescription must not appear in $el_id. Got: " + elId,
                        !elId.contains("zero_alpha_btn"));
                assertTrue(
                        "Zero-alpha view's contentDescription must not appear in $attr-aria-label. Got: " + ariaLabel,
                        !ariaLabel.contains("zero_alpha_btn"));
            }
        }
    }

    @Test
    public void testElementIdResolutionRule3HashFallback() throws Exception {
        try (ActivityScenario<AutocaptureXmlTestActivity> scenario =
                     ActivityScenario.launch(AutocaptureXmlTestActivity.class)) {

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // Click button with no contentDescription and invalid resource ID (10003)
            // This forces resolveElementId to fall through Rule 1 and Rule 2 to the hash fallback
            onView(withId(AutocaptureXmlTestActivity.ID_RULE3_BTN)).perform(click());

            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Event should be captured", event);

            JSONObject properties = event.getJSONObject("properties");

            // Verify $el_id uses hash fallback format: ClassName_<hexHashCode>
            String elId = properties.getString("$el_id");
            assertTrue("$el_id should start with 'Button_' for hash fallback, got: " + elId,
                    elId.matches("Button_[0-9a-f]+"));
        }
    }

}
