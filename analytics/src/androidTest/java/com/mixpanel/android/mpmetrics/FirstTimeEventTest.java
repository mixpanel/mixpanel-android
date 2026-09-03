package com.mixpanel.android.mpmetrics;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.mixpanel.android.util.MPConstants;
import com.mixpanel.android.util.OfflineMode;
import com.mixpanel.android.util.ProxyServerInteractor;
import com.mixpanel.android.util.RemoteService;
import com.mixpanel.android.util.RemoteService.HttpMethod;
import com.mixpanel.android.util.RemoteService.ServiceUnavailableException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Instrumented tests for First-Time Event Targeting feature.
 * Tests event matching, property filtering, session persistence, and recording API calls.
 */
@RunWith(AndroidJUnit4.class)
public class FirstTimeEventTest {
    private Context mContext;
    private BlockingQueue<RecordingAPICall> mRecordingCalls;
    private BlockingQueue<Boolean> mFlagLoadComplete;
    private MixpanelAPI mMixpanel;
    private ActivityScenario<TestActivity> mActivityScenario;

    private static final String SEMVER_OPERATOR = "semver_compare";
    private static final String SEMVER_PROPERTY = "app_version";
    private static final String SEMVER_TARGET = "1.2.3";

    private static final String DATETIME_OPERATOR = "datetime_compare";
    private static final String DATETIME_PROPERTY = "signup";
    /** 2026-07-16T00:00:00Z in epoch milliseconds. */
    private static final long DATETIME_TARGET_MS = 1784160000000L;
    private static final String ON_TARGET = "2026-07-16T00:00:00Z";
    private static final String DAY_BEFORE = "2026-07-15T00:00:00Z";
    private static final String DAY_AFTER = "2026-07-17T00:00:00Z";

    /**
     * One comparator symbol, with a property value the filter accepts and one it rejects.
     */
    private static final class SymbolCase {
        final String symbol;
        final String matching;
        final String notMatching;

        SymbolCase(String symbol, String matching, String notMatching) {
            this.symbol = symbol;
            this.matching = matching;
            this.notMatching = notMatching;
        }
    }

    /** 1.10.0 is on the matching side of > and >=, so a lexicographic comparison fails here. */
    private static final SymbolCase[] SEMVER_CASES = {
            new SymbolCase("===", "1.2.3", "1.2.4"),
            new SymbolCase("!==", "1.2.4", "1.2.3"),
            new SymbolCase("<", "1.2.2", "1.2.3"),
            new SymbolCase("<=", "1.2.3", "1.2.4"),
            new SymbolCase(">", "1.10.0", "1.2.3"),
            new SymbolCase(">=", "1.10.0", "1.2.2")
    };

    /** The subjects sit a day either side of the target and on it. */
    private static final SymbolCase[] DATETIME_CASES = {
            new SymbolCase("===", ON_TARGET, DAY_AFTER),
            new SymbolCase("!==", DAY_AFTER, ON_TARGET),
            new SymbolCase("<", DAY_BEFORE, ON_TARGET),
            new SymbolCase("<=", ON_TARGET, DAY_AFTER),
            new SymbolCase(">", DAY_AFTER, ON_TARGET),
            new SymbolCase(">=", ON_TARGET, DAY_BEFORE)
    };

    /** Each symbol gets its own event, so a matching case cannot spend an event another row needs. */
    private static String operatorEventName(String operator, String symbol) {
        return operator + " " + symbol;
    }

    private static String operatorHash(String operator, String symbol) {
        return "hash-" + operator + "-" + symbol;
    }

    /** Adds one pending first-time event per comparator symbol to the mock /flags response. */
    private static void appendOperatorEvents(
            JSONArray pendingEvents,
            String operator,
            String property,
            Object target,
            SymbolCase[] cases) throws JSONException {
        for (SymbolCase symbolCase : cases) {
            JSONObject filters = new JSONObject().put(operator, new JSONArray()
                    .put(new JSONObject().put("var", property))
                    .put(symbolCase.symbol)
                    .put(target));

            JSONObject pendingVariant = new JSONObject();
            pendingVariant.put(MPConstants.Flags.VARIANT_KEY, "premium");
            pendingVariant.put(MPConstants.Flags.VARIANT_VALUE, true);

            JSONObject event = new JSONObject();
            event.put(MPConstants.Flags.FLAG_KEY, operator + "-" + symbolCase.symbol + "-flag");
            event.put(MPConstants.Flags.FLAG_ID, "flag_id_" + operator + "_" + symbolCase.symbol);
            event.put(MPConstants.Flags.PROJECT_ID, "12345");
            event.put(MPConstants.Flags.FIRST_TIME_EVENT_HASH, operatorHash(operator, symbolCase.symbol));
            event.put(MPConstants.Flags.EVENT_NAME, operatorEventName(operator, symbolCase.symbol));
            event.put(MPConstants.Flags.PROPERTY_FILTERS, filters);
            event.put(MPConstants.Flags.PENDING_VARIANT, pendingVariant);

            pendingEvents.put(event);
        }
    }

    /**
     * Captures recording API calls for verification.
     */
    private static class RecordingAPICall {
        final String endpoint;
        final JSONObject body;
        final Map<String, String> headers;

        RecordingAPICall(String endpoint, JSONObject body, Map<String, String> headers) {
            this.endpoint = endpoint;
            this.body = body;
            this.headers = headers;
        }
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mRecordingCalls = new LinkedBlockingQueue<>();
        mFlagLoadComplete = new LinkedBlockingQueue<>();

        // Create MixpanelAPI instance with mock HTTP service
        // This must be done before launching the activity so lifecycle callbacks are registered
        TestUtils.cleanUpMixpanelData(mContext);
        mMixpanel = TestUtils.createMixpanelAPIWithMockHttpService(mContext, createMockHttpService());

        // Launch an activity to bring the app to foreground, which triggers onForeground()
        // This is required for feature flags to load (see commit a55681bb)
        mActivityScenario = ActivityScenario.launch(TestActivity.class);

        // Ensure distinct ID is initialized before any flag operations
        String distinctId = mMixpanel.getDistinctId();
        assertNotNull("Distinct ID should be initialized", distinctId);
    }

    @After
    public void tearDown() {
        if (mActivityScenario != null) {
            mActivityScenario.close();
        }
    }

    /**
     * Creates a mock HTTP service that captures recording API calls.
     */
    private RemoteService createMockHttpService() {
        return new RemoteService() {
            @Override
            public boolean isOnline(Context context, OfflineMode offlineMode) {
                return true; // Assume online for tests
            }

            @Override
            public void checkIsServerBlocked() {
                // No-op for tests
            }

            @Override
            public RequestResult performRequest(
                    String endpointUrl,
                    ProxyServerInteractor interactor,
                    Map<String, Object> params,
                    Map<String, String> headers,
                    byte[] requestBodyBytes,
                    javax.net.ssl.SSLSocketFactory socketFactory)
                    throws ServiceUnavailableException, java.io.IOException {
                // Delegate to the 7-parameter version with POST as default
                return performRequest(
                    HttpMethod.POST,
                    endpointUrl,
                    interactor,
                    params,
                    headers,
                    requestBodyBytes,
                    socketFactory
                );
            }

            @Override
            public RequestResult performRequest(
                    HttpMethod method,
                    String endpointUrl,
                    ProxyServerInteractor interactor,
                    Map<String, Object> params,
                    Map<String, String> headers,
                    byte[] requestBodyBytes,
                    javax.net.ssl.SSLSocketFactory socketFactory)
                    throws ServiceUnavailableException, java.io.IOException {

                try {
                    // Capture recording API calls
                    if (endpointUrl.contains("/first-time-events")) {
                        JSONObject body = requestBodyBytes != null ?
                                new JSONObject(new String(requestBodyBytes, StandardCharsets.UTF_8)) :
                                new JSONObject();
                        mRecordingCalls.add(new RecordingAPICall(endpointUrl, body, headers));
                        return createSuccessResult("{}");
                    }

                    // Mock flags fetch response
                    if (endpointUrl.contains("/flags")) {
                        RequestResult result = createSuccessResult(createFlagsResponse());
                        mFlagLoadComplete.offer(true);  // Signal that flags have loaded
                        return result;
                    }

                    return createSuccessResult("{}");
                } catch (Exception e) {
                    throw new IOException("Error in mock HTTP service", e);
                }
            }

            private RequestResult createSuccessResult(String responseBody) {
                return RequestResult.success(
                    responseBody.getBytes(StandardCharsets.UTF_8),
                    "mock-url"
                );
            }

            private String createFlagsResponse() throws Exception {
                JSONObject response = new JSONObject();

                // Add flags
                JSONObject flags = new JSONObject();
                JSONObject testFlagVariant = new JSONObject();
                testFlagVariant.put(MPConstants.Flags.VARIANT_KEY, "control");
                testFlagVariant.put(MPConstants.Flags.VARIANT_VALUE, false);
                flags.put("test_flag", testFlagVariant);
                response.put(MPConstants.Flags.FLAGS_KEY, flags);

                // Add pending first-time events
                JSONArray pendingEvents = new JSONArray();

                // Event 1: Simple event name match (no property filters)
                JSONObject event1 = new JSONObject();
                event1.put(MPConstants.Flags.FLAG_KEY, "test_flag");
                event1.put(MPConstants.Flags.FLAG_ID, "flag_id_1");
                event1.put(MPConstants.Flags.PROJECT_ID, "12345");
                event1.put(MPConstants.Flags.FIRST_TIME_EVENT_HASH, "hash_1");
                event1.put(MPConstants.Flags.EVENT_NAME, "Purchase");

                JSONObject pendingVariant1 = new JSONObject();
                pendingVariant1.put(MPConstants.Flags.VARIANT_KEY, "treatment");
                pendingVariant1.put(MPConstants.Flags.VARIANT_VALUE, true);
                event1.put(MPConstants.Flags.PENDING_VARIANT, pendingVariant1);

                pendingEvents.put(event1);

                // Event 2: Event with property filters (JsonLogic)
                JSONObject event2 = new JSONObject();
                event2.put(MPConstants.Flags.FLAG_KEY, "filter_flag");
                event2.put(MPConstants.Flags.FLAG_ID, "flag_id_2");
                event2.put(MPConstants.Flags.PROJECT_ID, "12345");
                event2.put(MPConstants.Flags.FIRST_TIME_EVENT_HASH, "hash_2");
                event2.put(MPConstants.Flags.EVENT_NAME, "AddToCart");

                // JsonLogic: {"==": [{"var": "category"}, "electronics"]}
                JSONObject propertyFilters = new JSONObject();
                propertyFilters.put("==", new JSONArray()
                        .put(new JSONObject().put("var", "category"))
                        .put("electronics"));
                event2.put(MPConstants.Flags.PROPERTY_FILTERS, propertyFilters);

                JSONObject pendingVariant2 = new JSONObject();
                pendingVariant2.put(MPConstants.Flags.VARIANT_KEY, "variant_a");
                pendingVariant2.put(MPConstants.Flags.VARIANT_VALUE, "filtered");
                event2.put(MPConstants.Flags.PENDING_VARIANT, pendingVariant2);

                pendingEvents.put(event2);

                // Events 3+: one per comparator symbol, for each custom operator
                appendOperatorEvents(pendingEvents, SEMVER_OPERATOR, SEMVER_PROPERTY,
                        SEMVER_TARGET, SEMVER_CASES);
                appendOperatorEvents(pendingEvents, DATETIME_OPERATOR, DATETIME_PROPERTY,
                        DATETIME_TARGET_MS, DATETIME_CASES);

                response.put(MPConstants.Flags.PENDING_FIRST_TIME_EVENTS, pendingEvents);

                return response.toString();
            }
        };
    }

    @Test
    public void testExactEventNameMatching_CaseSensitive() throws Exception {
        // Load flags with pending event for "Purchase"
        mMixpanel.getFlags().loadFlags();
        Boolean flagsLoaded = mFlagLoadComplete.poll(2, TimeUnit.SECONDS);
        assertNotNull("Flags should load successfully", flagsLoaded);

        // Track "purchase" (lowercase) - should NOT match
        mMixpanel.track("purchase");

        // Verify no recording API call was made
        RecordingAPICall call = mRecordingCalls.poll(1, TimeUnit.SECONDS);
        assertNull("Lowercase 'purchase' should not match 'Purchase'", call);

        // Track "Purchase" (exact match) - SHOULD match
        mMixpanel.track("Purchase");

        // Verify recording API call was made
        call = mRecordingCalls.poll(2, TimeUnit.SECONDS);
        assertNotNull("Exact match 'Purchase' should trigger recording", call);
        assertTrue(call.endpoint.contains("/first-time-events"));
        assertEquals("hash_1", call.body.getString("first_time_event_hash"));
    }

    @Test
    public void testPropertyFilterMatching_CaseSensitive() throws Exception {
        // Load flags with pending event for "AddToCart" with category filter for "electronics"
        mMixpanel.getFlags().loadFlags();
        Boolean flagsLoaded = mFlagLoadComplete.poll(2, TimeUnit.SECONDS);
        assertNotNull("Flags should load successfully", flagsLoaded);

        // Track AddToCart with category="ELECTRONICS" (uppercase) - should NOT match
        JSONObject props = new JSONObject();
        props.put("category", "ELECTRONICS"); // Uppercase - won't match "electronics"
        mMixpanel.track("AddToCart", props);

        // Verify no recording API call was made (case-sensitive property matching)
        RecordingAPICall call = mRecordingCalls.poll(1, TimeUnit.SECONDS);
        assertNull("Property filter should NOT match different case", call);

        // Track AddToCart with category="electronics" (exact match) - SHOULD match
        JSONObject exactProps = new JSONObject();
        exactProps.put("category", "electronics");
        mMixpanel.track("AddToCart", exactProps);

        // Verify recording API call was made
        call = mRecordingCalls.poll(2, TimeUnit.SECONDS);
        assertNotNull("Property filter should match with exact case", call);
        assertEquals("hash_2", call.body.getString("first_time_event_hash"));
    }

    @Test
    public void testPropertyFilterNoMatch() throws Exception {
        // Load flags
        mMixpanel.getFlags().loadFlags();
        Boolean flagsLoaded = mFlagLoadComplete.poll(2, TimeUnit.SECONDS);
        assertNotNull("Flags should load successfully", flagsLoaded);

        // Track AddToCart with different category - should NOT match
        JSONObject props = new JSONObject();
        props.put("category", "clothing");
        mMixpanel.track("AddToCart", props);

        // Verify no recording API call
        RecordingAPICall call = mRecordingCalls.poll(1, TimeUnit.SECONDS);
        assertNull("Different category should not match filter", call);
    }

    @Test
    public void testEventActivatesOnlyOnce() throws Exception {
        // Load flags
        mMixpanel.getFlags().loadFlags();
        Boolean flagsLoaded = mFlagLoadComplete.poll(2, TimeUnit.SECONDS);
        assertNotNull("Flags should load successfully", flagsLoaded);

        // Track Purchase first time - should activate
        mMixpanel.track("Purchase");

        RecordingAPICall call1 = mRecordingCalls.poll(2, TimeUnit.SECONDS);
        assertNotNull("First tracking should activate", call1);

        // Track Purchase second time - should NOT activate again
        mMixpanel.track("Purchase");

        RecordingAPICall call2 = mRecordingCalls.poll(1, TimeUnit.SECONDS);
        assertNull("Second tracking should not activate again", call2);
    }

    @Test
    public void testSessionPersistenceAcrossRefetch() throws Exception {
        // Load flags
        mMixpanel.getFlags().loadFlags();
        Boolean flagsLoaded = mFlagLoadComplete.poll(2, TimeUnit.SECONDS);
        assertNotNull("Flags should load successfully", flagsLoaded);

        // Track Purchase to activate
        mMixpanel.track("Purchase");

        RecordingAPICall call = mRecordingCalls.poll(2, TimeUnit.SECONDS);
        assertNotNull("Event should activate", call);

        // Get flag variant - should be treatment (activated variant)
        Object variantValue = mMixpanel.getFlags().getVariantValueSync("test_flag", false);
        assertEquals("Variant should be activated (true)", true, variantValue);

        // Refetch flags (simulates new fetch)
        mMixpanel.getFlags().loadFlags();
        flagsLoaded = mFlagLoadComplete.poll(2, TimeUnit.SECONDS);
        assertNotNull("Flags should load successfully on refetch", flagsLoaded);

        // Variant should still be treatment (session persistence)
        variantValue = mMixpanel.getFlags().getVariantValueSync("test_flag", false);
        assertEquals("Activated variant should persist across refetch", true, variantValue);

        // Tracking Purchase again should not activate (already activated in session)
        mMixpanel.track("Purchase");

        RecordingAPICall call2 = mRecordingCalls.poll(1, TimeUnit.SECONDS);
        assertNull("Event should not activate again after refetch", call2);
    }

    @Test
    public void testRecordingAPIPayload() throws Exception {
        // Load flags
        mMixpanel.getFlags().loadFlags();
        Boolean flagsLoaded = mFlagLoadComplete.poll(2, TimeUnit.SECONDS);
        assertNotNull("Flags should load successfully", flagsLoaded);

        // Track Purchase
        mMixpanel.track("Purchase");

        // Verify recording API call payload
        RecordingAPICall call = mRecordingCalls.poll(2, TimeUnit.SECONDS);
        assertNotNull("Recording call should be made", call);

        // Check endpoint
        assertTrue("Endpoint should contain flag_id", call.endpoint.contains("flag_id_1"));
        assertTrue("Endpoint should contain /first-time-events", call.endpoint.contains("/first-time-events"));

        // Check body
        assertTrue("Body should have distinct_id", call.body.has("distinct_id"));
        assertEquals("12345", call.body.getString("project_id"));
        assertEquals("hash_1", call.body.getString("first_time_event_hash"));

        // Check headers
        assertTrue("Should have Authorization header", call.headers.containsKey("Authorization"));
        assertTrue("Should have Content-Type header", call.headers.containsKey("Content-Type"));
        assertTrue("Should have traceparent header", call.headers.containsKey("traceparent"));

        // Verify traceparent format (W3C: "00-{trace-id}-{span-id}-01")
        String traceparent = call.headers.get("traceparent");
        assertNotNull(traceparent);
        String[] parts = traceparent.split("-");
        assertEquals("Traceparent should have 4 parts", 4, parts.length);
        assertEquals("00", parts[0]); // version
        assertEquals(32, parts[1].length()); // trace-id (32 hex chars)
        assertEquals(16, parts[2].length()); // span-id (16 hex chars)
        assertEquals("01", parts[3]); // flags
    }

    @Test
    public void testNullPropertyFilters_AlwaysMatches() throws Exception {
        // This test verifies that events with no property filters match on event name alone
        // The "Purchase" event in our mock response has no property_filters field

        mMixpanel.getFlags().loadFlags();
        Boolean flagsLoaded = mFlagLoadComplete.poll(2, TimeUnit.SECONDS);
        assertNotNull("Flags should load successfully", flagsLoaded);

        // Track Purchase without any properties - should match
        mMixpanel.track("Purchase");

        RecordingAPICall call = mRecordingCalls.poll(2, TimeUnit.SECONDS);
        assertNotNull("Event with null property filters should match on name alone", call);

        // Track Purchase WITH properties - should still match (filters are null)
        mMixpanel.track("Purchase", new JSONObject().put("amount", 100));

        // Should not activate again (already activated), but this confirms filters were null
        RecordingAPICall call2 = mRecordingCalls.poll(1, TimeUnit.SECONDS);
        assertNull("Should not activate twice", call2);
    }

    @Test
    public void testActivationProducesExpectedVariantValues() throws Exception {
        // Load flags - test_flag starts with variant_key="control", variant_value=false
        mMixpanel.getFlags().loadFlags();
        Boolean flagsLoaded = mFlagLoadComplete.poll(2, TimeUnit.SECONDS);
        assertNotNull("Flags should load successfully", flagsLoaded);

        // Verify initial variant before activation
        MixpanelFlagVariant fallback = new MixpanelFlagVariant("fallback", "default");
        MixpanelFlagVariant initialVariant = mMixpanel.getFlags().getVariantSync("test_flag", fallback);
        assertEquals("Initial variant key should be 'control'", "control", initialVariant.key);
        assertEquals("Initial variant value should be false", false, initialVariant.value);

        // Track Purchase event to activate first-time event
        mMixpanel.track("Purchase");

        // Wait for recording API call to confirm activation
        RecordingAPICall call = mRecordingCalls.poll(2, TimeUnit.SECONDS);
        assertNotNull("First-time event should activate and record", call);
        assertEquals("hash_1", call.body.getString("first_time_event_hash"));

        // Verify variant has been updated to pending variant values
        MixpanelFlagVariant activatedVariant = mMixpanel.getFlags().getVariantSync("test_flag", fallback);
        assertEquals("Activated variant key should be 'treatment' from pending variant", "treatment", activatedVariant.key);
        assertEquals("Activated variant value should be true from pending variant", true, activatedVariant.value);
    }

    @Test
    public void testSemverPropertyFilterEverySymbol() throws Exception {
        assertEverySymbol(SEMVER_OPERATOR, SEMVER_PROPERTY, SEMVER_CASES);
    }

    @Test
    public void testDatetimePropertyFilterEverySymbol() throws Exception {
        assertEverySymbol(DATETIME_OPERATOR, DATETIME_PROPERTY, DATETIME_CASES);
    }

    /**
     * Drives every comparator symbol through the runtime-event path in both directions.
     *
     * <p>The rejecting value is tracked first because a non-match consumes nothing; the accepting
     * value then proves the pending event was still live, so the absence above was the filter
     * failing closed rather than the event having already been spent.
     */
    private void assertEverySymbol(String operator, String property, SymbolCase[] cases)
            throws Exception {
        mMixpanel.getFlags().loadFlags();
        assertNotNull("Flags should load successfully", mFlagLoadComplete.poll(2, TimeUnit.SECONDS));

        for (SymbolCase symbolCase : cases) {
            String eventName = operatorEventName(operator, symbolCase.symbol);
            String context = operator + " " + symbolCase.symbol;

            mMixpanel.track(eventName, new JSONObject().put(property, symbolCase.notMatching));
            assertNull(context + " should not match " + symbolCase.notMatching,
                    mRecordingCalls.poll(1, TimeUnit.SECONDS));

            mMixpanel.track(eventName, new JSONObject().put(property, symbolCase.matching));
            RecordingAPICall call = mRecordingCalls.poll(2, TimeUnit.SECONDS);
            assertNotNull(context + " should match " + symbolCase.matching, call);
            assertEquals(context, operatorHash(operator, symbolCase.symbol),
                    call.body.getString("first_time_event_hash"));
        }
    }

    @Test
    public void testPropertyFilterFailsClosedOnUnreadableProperty() throws Exception {
        mMixpanel.getFlags().loadFlags();
        assertNotNull("Flags should load successfully", mFlagLoadComplete.poll(2, TimeUnit.SECONDS));

        assertFailsClosed(SEMVER_OPERATOR, SEMVER_PROPERTY, "not-a-version", "1.2.3");
        assertFailsClosed(DATETIME_OPERATOR, DATETIME_PROPERTY, "yesterday", ON_TARGET);
    }

    /**
     * A property the operator cannot read leaves the flag alone under both {@code ===} and
     * {@code !==}, so an unreadable value cannot invert into a match rather than failing closed.
     */
    private void assertFailsClosed(String operator, String property, String unreadable, String matching)
            throws Exception {
        for (String symbol : new String[] {"===", "!=="}) {
            mMixpanel.track(operatorEventName(operator, symbol),
                    new JSONObject().put(property, unreadable));
            assertNull(operator + " " + symbol + " should fail closed on " + unreadable,
                    mRecordingCalls.poll(1, TimeUnit.SECONDS));
        }

        // The === event is still pending, so a value that does match proves the absences above were
        // the filter failing closed rather than the fixture being spent
        mMixpanel.track(operatorEventName(operator, "==="), new JSONObject().put(property, matching));
        assertNotNull(operator + " === should still match " + matching,
                mRecordingCalls.poll(2, TimeUnit.SECONDS));
    }

    @Test
    public void testMalformedPendingEvents_DoesNotCrash() throws Exception {
        // This test verifies that malformed pending first-time events from the backend
        // are handled gracefully without crashing the host app (core SDK principle)

        // Create a custom HTTP service that returns malformed pending events
        RemoteService malformedService = new RemoteService() {
            @Override
            public boolean isOnline(Context context, OfflineMode offlineMode) {
                return true;
            }

            @Override
            public void checkIsServerBlocked() {
                // No-op
            }

            @Override
            public RequestResult performRequest(
                    String endpointUrl,
                    ProxyServerInteractor interactor,
                    Map<String, Object> params,
                    Map<String, String> headers,
                    byte[] requestBodyBytes,
                    javax.net.ssl.SSLSocketFactory socketFactory)
                    throws ServiceUnavailableException, java.io.IOException {
                return performRequest(
                    HttpMethod.POST,
                    endpointUrl,
                    interactor,
                    params,
                    headers,
                    requestBodyBytes,
                    socketFactory
                );
            }

            @Override
            public RequestResult performRequest(
                    HttpMethod method,
                    String endpointUrl,
                    ProxyServerInteractor interactor,
                    Map<String, Object> params,
                    Map<String, String> headers,
                    byte[] requestBodyBytes,
                    javax.net.ssl.SSLSocketFactory socketFactory)
                    throws ServiceUnavailableException, java.io.IOException {

                try {
                    if (endpointUrl.contains("/flags")) {
                        // Return response with malformed pending events
                        JSONObject response = new JSONObject();
                        
                        // Add valid flags
                        JSONObject flags = new JSONObject();
                        JSONObject testFlagVariant = new JSONObject();
                        testFlagVariant.put(MPConstants.Flags.VARIANT_KEY, "control");
                        testFlagVariant.put(MPConstants.Flags.VARIANT_VALUE, false);
                        flags.put("test_flag", testFlagVariant);
                        response.put(MPConstants.Flags.FLAGS_KEY, flags);

                        // Add pending events with various malformed data
                        JSONArray pendingEvents = new JSONArray();

                        // Event 1: Empty flag_key (should be rejected by constructor validation)
                        JSONObject event1 = new JSONObject();
                        event1.put(MPConstants.Flags.FLAG_KEY, "");  // Empty string - invalid
                        event1.put(MPConstants.Flags.FLAG_ID, "flag_id_1");
                        event1.put(MPConstants.Flags.PROJECT_ID, "12345");
                        event1.put(MPConstants.Flags.FIRST_TIME_EVENT_HASH, "hash_1");
                        event1.put(MPConstants.Flags.EVENT_NAME, "Purchase");
                        JSONObject pendingVariant1 = new JSONObject();
                        pendingVariant1.put(MPConstants.Flags.VARIANT_KEY, "treatment");
                        pendingVariant1.put(MPConstants.Flags.VARIANT_VALUE, true);
                        event1.put(MPConstants.Flags.PENDING_VARIANT, pendingVariant1);
                        pendingEvents.put(event1);

                        // Event 2: Missing required field (will throw JSONException)
                        JSONObject event2 = new JSONObject();
                        event2.put(MPConstants.Flags.FLAG_KEY, "test_flag");
                        // Missing FLAG_ID - will cause JSONException
                        event2.put(MPConstants.Flags.PROJECT_ID, "12345");
                        event2.put(MPConstants.Flags.FIRST_TIME_EVENT_HASH, "hash_2");
                        event2.put(MPConstants.Flags.EVENT_NAME, "AddToCart");
                        JSONObject pendingVariant2 = new JSONObject();
                        pendingVariant2.put(MPConstants.Flags.VARIANT_KEY, "variant_a");
                        pendingVariant2.put(MPConstants.Flags.VARIANT_VALUE, "filtered");
                        event2.put(MPConstants.Flags.PENDING_VARIANT, pendingVariant2);
                        pendingEvents.put(event2);

                        // Event 3: Valid event (should be parsed successfully)
                        JSONObject event3 = new JSONObject();
                        event3.put(MPConstants.Flags.FLAG_KEY, "valid_flag");
                        event3.put(MPConstants.Flags.FLAG_ID, "flag_id_3");
                        event3.put(MPConstants.Flags.PROJECT_ID, "12345");
                        event3.put(MPConstants.Flags.FIRST_TIME_EVENT_HASH, "hash_3");
                        event3.put(MPConstants.Flags.EVENT_NAME, "ValidEvent");
                        JSONObject pendingVariant3 = new JSONObject();
                        pendingVariant3.put(MPConstants.Flags.VARIANT_KEY, "valid_variant");
                        pendingVariant3.put(MPConstants.Flags.VARIANT_VALUE, "valid");
                        event3.put(MPConstants.Flags.PENDING_VARIANT, pendingVariant3);
                        pendingEvents.put(event3);

                        response.put(MPConstants.Flags.PENDING_FIRST_TIME_EVENTS, pendingEvents);

                        RequestResult result = RequestResult.success(
                            response.toString().getBytes(StandardCharsets.UTF_8),
                            "mock-url"
                        );
                        mFlagLoadComplete.offer(true);
                        return result;
                    }

                    return RequestResult.success(
                        "{}".getBytes(StandardCharsets.UTF_8),
                        "mock-url"
                    );
                } catch (Exception e) {
                    throw new IOException("Error in mock HTTP service", e);
                }
            }
        };

        // Clean up and create new Mixpanel instance with malformed service
        if (mActivityScenario != null) {
            mActivityScenario.close();
        }
        TestUtils.cleanUpMixpanelData(mContext);
        mMixpanel = TestUtils.createMixpanelAPIWithMockHttpService(mContext, malformedService);

        // Launch activity to trigger flag loading
        mActivityScenario = ActivityScenario.launch(TestActivity.class);

        // Ensure distinct ID is initialized
        String distinctId = mMixpanel.getDistinctId();
        assertNotNull("Distinct ID should be initialized", distinctId);

        // Load flags - should not crash despite malformed events
        mMixpanel.getFlags().loadFlags();

        // Wait for flag loading to complete
        Boolean flagsLoaded = mFlagLoadComplete.poll(2, TimeUnit.SECONDS);
        assertNotNull("Flags should load successfully despite malformed events", flagsLoaded);

        // Verify that flags were loaded (valid flag data should be available)
        Object variantValue = mMixpanel.getFlags().getVariantValueSync("test_flag", "fallback");
        assertEquals("Valid flag should be available", false, variantValue);

        // The test verifies:
        // 1. SDK didn't crash despite receiving malformed events (IllegalArgumentException and JSONException)
        // 2. Valid flag data was still loaded and is accessible
        // 3. Error handling in parsePendingFirstTimeEvents gracefully skipped malformed events
        // If we get here without crashing, the fix is working correctly
    }
}
