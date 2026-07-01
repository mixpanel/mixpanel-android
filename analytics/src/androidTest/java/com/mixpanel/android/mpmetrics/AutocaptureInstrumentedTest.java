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
public class AutocaptureInstrumentedTest {

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
        Thread.sleep(2000);

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

        // Create MixpanelAPI directly with trackAutomaticEvents=true
        // (autocapture events use the isAutomaticEvent=true flag in track())
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
        try (ActivityScenario<XmlAutocaptureTestActivity> scenario =
                     ActivityScenario.launch(XmlAutocaptureTestActivity.class)) {

            // Wait for autocapture to attach to window
            Thread.sleep(500);

            // Click the rule1_btn button
            onView(withId(XmlAutocaptureTestActivity.ID_RULE1_BTN)).perform(click());

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
        try (ActivityScenario<XmlAutocaptureTestActivity> scenario =
                     ActivityScenario.launch(XmlAutocaptureTestActivity.class)) {

            Thread.sleep(500);

            // Get rage zone coordinates for rapid touch injection
            final int[] location = new int[2];
            scenario.onActivity(activity -> {
                View rageZone = activity.findViewById(XmlAutocaptureTestActivity.ID_RAGE_ZONE);
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
        try (ActivityScenario<XmlAutocaptureTestActivity> scenario =
                     ActivityScenario.launch(XmlAutocaptureTestActivity.class)) {

            Thread.sleep(500);

            // Click the dead button (has NO click listener)
            onView(withId(XmlAutocaptureTestActivity.ID_DEAD_XML_BTN)).perform(click());

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
        try (ActivityScenario<XmlAutocaptureTestActivity> scenario =
                     ActivityScenario.launch(XmlAutocaptureTestActivity.class)) {

            Thread.sleep(500);

            // Click button with resource ID only (no contentDescription)
            onView(withId(XmlAutocaptureTestActivity.ID_RULE2_BTN)).perform(click());

            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Event should be captured", event);

            JSONObject properties = event.getJSONObject("properties");

            // Verify $el_id uses resource ID name (android.R.id.button1 resolves to "button1")
            assertEquals("button1", properties.getString("$el_id"));
        }
    }

    @Test
    public void testMultipleClicksGenerateMultipleEvents() throws Exception {
        try (ActivityScenario<XmlAutocaptureTestActivity> scenario =
                     ActivityScenario.launch(XmlAutocaptureTestActivity.class)) {

            Thread.sleep(500);

            // Click 3 different buttons with spacing to avoid rage click
            onView(withId(XmlAutocaptureTestActivity.ID_RULE1_BTN)).perform(click());
            Thread.sleep(200);

            onView(withId(XmlAutocaptureTestActivity.ID_RULE2_BTN)).perform(click());
            Thread.sleep(200);

            onView(withId(XmlAutocaptureTestActivity.ID_RULE3_BTN)).perform(click());
            Thread.sleep(200);

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
        try (ActivityScenario<XmlAutocaptureTestActivity> scenario =
                     ActivityScenario.launch(XmlAutocaptureTestActivity.class)) {

            Thread.sleep(500);

            // Click a button
            onView(withId(XmlAutocaptureTestActivity.ID_RULE1_BTN)).perform(click());

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

    @Test
    public void testElementIdResolutionRule3HashFallback() throws Exception {
        try (ActivityScenario<XmlAutocaptureTestActivity> scenario =
                     ActivityScenario.launch(XmlAutocaptureTestActivity.class)) {

            Thread.sleep(500);

            // Click button with no contentDescription and invalid resource ID (10003)
            // This forces resolveElementId to fall through Rule 1 and Rule 2 to the hash fallback
            onView(withId(XmlAutocaptureTestActivity.ID_RULE3_BTN)).perform(click());

            JSONObject event = mEvents.poll(10, TimeUnit.SECONDS);
            assertNotNull("Event should be captured", event);

            JSONObject properties = event.getJSONObject("properties");

            // Verify $el_id uses hash fallback format: ClassName_view_<hexHashCode>
            String elId = properties.getString("$el_id");
            assertTrue("$el_id should start with 'Button_view_' for hash fallback, got: " + elId,
                    elId.matches("Button_view_[0-9a-f]+"));
        }
    }

}
