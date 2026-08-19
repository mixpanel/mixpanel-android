package com.mixpanel.android.mpmetrics;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.mixpanel.android.autocapture.ViewElementIdExtractor;

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
 * End-to-end tests for {@code $el_id} resolution: real taps through the autocapture pipeline, with
 * and without a host-app supplied {@link ViewElementIdExtractor}.
 *
 * <p>Complements {@link com.mixpanel.android.autocapture.DefaultViewElementIdExtractorTest}, which
 * covers the full priority matrix at the extractor level. These tests prove the wiring —
 * {@link MixpanelOptions} to {@link AutocaptureOptions} to the hit-test path — and that a custom
 * extractor is authoritative even when it returns null or throws.
 *
 * <p>Mixpanel is initialized per test via {@link #initMixpanel(AutocaptureOptions)} rather than in
 * {@code @Before}, because each test needs different autocapture options.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ElementIdExtractorInstrumentedTest {

    private static final String TEST_TOKEN = "EL_ID_EXTRACTOR_TEST_TOKEN";

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
            // The contentDescription still travels as the accessibility label — only $el_id changed.
            assertEquals(ElementIdTestActivity.CHECKOUT_CONTENT_DESCRIPTION,
                    properties.getString("$attr-aria-label"));
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
    public void testDefaultResolution_ContentDescriptionWhenIdHasNoEntryName() throws Exception {
        initMixpanel(new AutocaptureOptions.Builder().build());

        try (ActivityScenario<ElementIdTestActivity> scenario =
                     ActivityScenario.launch(ElementIdTestActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            onView(withId(ElementIdTestActivity.ID_CONTENT_DESC_ONLY_BTN)).perform(click());

            JSONObject properties = awaitClickProperties();
            assertEquals(ElementIdTestActivity.CONTENT_DESC_ONLY_LABEL,
                    properties.getString("$el_id"));
        }
    }

    @Test
    public void testReactNativeInaccessibleLabelIsNotReported() throws Exception {
        // accessible={false} in React Native leaves the view important for accessibility with its
        // contentDescription intact, so neither $el_id nor $attr-aria-label may carry that label.
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

    // ============ Custom extractor ============

    @Test
    public void testCustomExtractorReplacesDefaultResolution() throws Exception {
        initMixpanel(new AutocaptureOptions.Builder()
                .viewElementIdExtractor(view -> "custom_el_id")
                .build());

        try (ActivityScenario<ElementIdTestActivity> scenario =
                     ActivityScenario.launch(ElementIdTestActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            onView(withId(ElementIdTestActivity.ID_CHECKOUT_BTN)).perform(click());

            JSONObject properties = awaitClickProperties();
            assertEquals("custom_el_id", properties.getString("$el_id"));
        }
    }

    @Test
    public void testCustomExtractorReceivesTheTappedView() throws Exception {
        // The extractor is handed the hit-tested view, so it can key off the app's own metadata.
        initMixpanel(new AutocaptureOptions.Builder()
                .viewElementIdExtractor(view ->
                        view.getId() == ElementIdTestActivity.ID_CHECKOUT_BTN ? "saw_checkout" : null)
                .build());

        try (ActivityScenario<ElementIdTestActivity> scenario =
                     ActivityScenario.launch(ElementIdTestActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            onView(withId(ElementIdTestActivity.ID_CHECKOUT_BTN)).perform(click());

            JSONObject properties = awaitClickProperties();
            assertEquals("saw_checkout", properties.getString("$el_id"));
        }
    }

    @Test
    public void testCustomExtractorReturningNullFallsBackToAnonymousId() throws Exception {
        // A null return means "report nothing identifying" — the SDK must NOT quietly fall back to
        // the view metadata the developer declined to expose.
        initMixpanel(new AutocaptureOptions.Builder()
                .viewElementIdExtractor(view -> null)
                .build());

        try (ActivityScenario<ElementIdTestActivity> scenario =
                     ActivityScenario.launch(ElementIdTestActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            onView(withId(ElementIdTestActivity.ID_CHECKOUT_BTN)).perform(click());

            JSONObject properties = awaitClickProperties();
            String elId = properties.getString("$el_id");
            assertTrue("Expected the anonymous hash id, got: " + elId,
                    elId.matches(HASH_ID_PATTERN));
            assertTrue("Resource entry name must not leak: " + elId,
                    !elId.contains("mp_test_checkout_button"));
            assertTrue("contentDescription must not leak into $el_id: " + elId,
                    !elId.contains("Checkout"));
        }
    }

    @Test
    public void testCustomExtractorReturningEmptyStringFallsBackToAnonymousId() throws Exception {
        initMixpanel(new AutocaptureOptions.Builder()
                .viewElementIdExtractor(view -> "")
                .build());

        try (ActivityScenario<ElementIdTestActivity> scenario =
                     ActivityScenario.launch(ElementIdTestActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            onView(withId(ElementIdTestActivity.ID_CHECKOUT_BTN)).perform(click());

            JSONObject properties = awaitClickProperties();
            String elId = properties.getString("$el_id");
            assertTrue("Expected the anonymous hash id, got: " + elId,
                    elId.matches(HASH_ID_PATTERN));
        }
    }

    @Test
    public void testThrowingCustomExtractorDoesNotBreakTracking() throws Exception {
        initMixpanel(new AutocaptureOptions.Builder()
                .viewElementIdExtractor(view -> {
                    throw new IllegalStateException("host app bug");
                })
                .build());

        try (ActivityScenario<ElementIdTestActivity> scenario =
                     ActivityScenario.launch(ElementIdTestActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            onView(withId(ElementIdTestActivity.ID_CHECKOUT_BTN)).perform(click());

            // The event still lands, with an anonymous id — a host-app bug must never drop events
            // or crash the app.
            JSONObject properties = awaitClickProperties();
            String elId = properties.getString("$el_id");
            assertTrue("Expected the anonymous hash id, got: " + elId,
                    elId.matches(HASH_ID_PATTERN));
        }
    }

    // ============ Options plumbing ============

    @Test
    public void testOptionsDefaultToNoExtractor() {
        assertNull(new AutocaptureOptions.Builder().build().getViewElementIdExtractor());
    }

    @Test
    public void testOptionsRetainAndCopyTheExtractor() {
        ViewElementIdExtractor extractor = new ViewElementIdExtractor() {
            @Nullable
            @Override
            public String extractElementId(@NonNull View view) {
                return "id";
            }
        };

        AutocaptureOptions options = new AutocaptureOptions.Builder()
                .viewElementIdExtractor(extractor)
                .build();
        assertSame(extractor, options.getViewElementIdExtractor());

        AutocaptureOptions copy = new AutocaptureOptions.Builder(options).build();
        assertSame("Builder(source) must carry the extractor over",
                extractor, copy.getViewElementIdExtractor());
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
