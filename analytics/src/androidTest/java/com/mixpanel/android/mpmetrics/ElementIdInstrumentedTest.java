package com.mixpanel.android.mpmetrics;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end tests for {@code $el_id} resolution: real taps through the autocapture pipeline,
 * resolved by the SDK's built-in rules.
 *
 * <p>Complements {@link com.mixpanel.android.autocapture.DefaultViewElementIdExtractorTest}, which
 * covers the full priority matrix directly. These tests prove the wiring — {@link MixpanelOptions}
 * to {@link AutocaptureOptions} to the hit-test path — end to end on real taps.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ElementIdInstrumentedTest {

    private static final String TEST_TOKEN = "EL_ID_TEST_TOKEN";

    /** Matches the anonymous fallback: SimpleClassName_hexHash. */
    private static final String HASH_ID_PATTERN = "[A-Za-z0-9$_]+_[0-9a-f]+";

    private final BlockingQueue<JSONObject> mEvents = new LinkedBlockingQueue<>();
    private MixpanelAPI mMixpanel;
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

        mEvents.clear();
    }

    @After
    public void tearDown() {
        if (mMixpanel != null) {
            mMixpanel.clearSuperProperties();
            mMixpanel.flush();
            mMixpanel = null;
        }
        mEvents.clear();
        mContext.deleteDatabase("mixpanel");
        TestUtils.cleanUpMixpanelData(mContext);
    }

    // ============ Default resolution order ============

    @Test
    public void testDefaultResolution_ResourceEntryNameWinsOverContentDescription() throws Exception {
        initMixpanel(new AutocaptureOptions.Builder().build());

        try (ActivityScenario<ElementIdTestActivity> scenario =
                     ActivityScenario.launch(ElementIdTestActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            onView(withId(ElementIdTestActivity.ID_CHECKOUT_BTN)).perform(click());

            JSONObject properties = awaitClickProperties();
            assertEquals("mp_test_checkout_button", properties.getString("$el_id"));
            // The contentDescription is not reported at all — no $attr-aria-label property exists.
            assertTrue("$attr-aria-label must never be present",
                    !properties.has("$attr-aria-label"));
            assertTrue("contentDescription must not appear in the payload: " + properties,
                    !properties.toString().contains(
                            ElementIdTestActivity.CHECKOUT_CONTENT_DESCRIPTION));
        }
    }

    @Test
    public void testDefaultResolution_ResourceEntryNameWithoutContentDescription() throws Exception {
        initMixpanel(new AutocaptureOptions.Builder().build());

        try (ActivityScenario<ElementIdTestActivity> scenario =
                     ActivityScenario.launch(ElementIdTestActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            onView(withId(ElementIdTestActivity.ID_BARE_BTN)).perform(click());

            JSONObject properties = awaitClickProperties();
            assertEquals("mp_test_bare_button", properties.getString("$el_id"));
        }
    }

    @Test
    public void testDefaultResolution_ContentDescriptionIsNeverUsed() throws Exception {
        initMixpanel(new AutocaptureOptions.Builder().build());

        try (ActivityScenario<ElementIdTestActivity> scenario =
                     ActivityScenario.launch(ElementIdTestActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            onView(withId(ElementIdTestActivity.ID_CONTENT_DESC_ONLY_BTN)).perform(click());

            JSONObject properties = awaitClickProperties();
            String elId = properties.getString("$el_id");
            assertTrue("Expected the structural hash fallback, got: " + elId,
                    elId.matches(HASH_ID_PATTERN));
            assertTrue("contentDescription must not appear in the payload: " + properties,
                    !properties.toString().contains(ElementIdTestActivity.CONTENT_DESC_ONLY_LABEL));
        }
    }

    @Test
    public void testReactNativeLabelIsNotReported() throws Exception {
        // React Native sets contentDescription from accessibilityLabel regardless of the accessible
        // prop. No accessibility text is read any more, so no variant of this shape can leak.
        initMixpanel(new AutocaptureOptions.Builder().build());

        try (ActivityScenario<ElementIdTestActivity> scenario =
                     ActivityScenario.launch(ElementIdTestActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            onView(withId(ElementIdTestActivity.ID_RN_INACCESSIBLE_BTN)).perform(click());

            JSONObject properties = awaitClickProperties();
            String elId = properties.getString("$el_id");
            assertTrue("Expected the anonymous hash id, got: " + elId,
                    elId.matches(HASH_ID_PATTERN));
            assertTrue("Label must not leak into $el_id: " + elId,
                    !elId.contains("4321"));
            assertNull("Label must not be reported as $attr-aria-label",
                    properties.optString("$attr-aria-label", null));
        }
    }

    // ============ Helpers ============

    /**
     * Polls for the next {@code $mp_click} event and returns its properties.
     *
     * <p>Skips any other autocapture event so a stray rage/dead click can't fail the assertion.
     */
    private JSONObject awaitClickProperties() throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        while (System.currentTimeMillis() < deadline) {
            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            if (event == null) {
                break;
            }
            if ("$mp_click".equals(event.optString("event"))) {
                JSONObject properties = event.getJSONObject("properties");
                assertNotNull("Event properties should exist", properties);
                return properties;
            }
        }
        throw new AssertionError("No $mp_click event captured");
    }

    /** Builds a MixpanelAPI whose events land in {@link #mEvents}, using the given options. */
    private void initMixpanel(AutocaptureOptions autocaptureOptions) {
        final TestUtils.EmptyPreferences mockPreferences = new TestUtils.EmptyPreferences(mContext);
        final MPConfig config = MPConfig.getInstance(mContext, null);

        final MPDbAdapter mockAdapter = new MPDbAdapter(mContext, config) {
            @Override
            public int addJSON(JSONObject message, String token, MPDbAdapter.Table table) {
                if (table == MPDbAdapter.Table.EVENTS) {
                    try {
                        if (message.optString("event", "").startsWith("$mp_")) {
                            mEvents.add(message);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("ElementIdExtractorTest", "Error capturing event", e);
                    }
                }
                return super.addJSON(message, token, table);
            }
        };

        final AnalyticsMessages customMessages = new AnalyticsMessages(mContext, config) {
            @Override
            protected MPDbAdapter makeDbAdapter(Context context) {
                return mockAdapter;
            }
        };

        MixpanelOptions options = new MixpanelOptions.Builder()
                .autocaptureOptions(autocaptureOptions)
                .build();

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
                final String prefsName =
                        "com.mixpanel.android.mpmetrics.MixpanelAPI_" + instanceKey;
                context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                        .edit().clear().commit();

                final String timeEventsPrefsName =
                        "com.mixpanel.android.mpmetrics.MixpanelAPI.TimeEvents_" + instanceKey;
                context.getSharedPreferences(timeEventsPrefsName, Context.MODE_PRIVATE)
                        .edit().clear().commit();

                final String mixpanelPrefsName = "com.mixpanel.android.mpmetrics.Mixpanel";
                context.getSharedPreferences(mixpanelPrefsName, Context.MODE_PRIVATE)
                        .edit().clear()
                        .putBoolean(token, true)
                        .putBoolean("has_launched", true)
                        .apply();

                return super.getPersistentIdentity(
                        context, referrerPreferences, token, instanceName, deviceIdProvider);
            }
        };
    }
}
