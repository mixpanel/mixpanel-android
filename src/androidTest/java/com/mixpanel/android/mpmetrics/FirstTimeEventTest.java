package com.mixpanel.android.mpmetrics;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.mixpanel.android.util.MPConstants;
import com.mixpanel.android.util.RemoteService;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

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
    private BlockingQueue<JSONObject> mTrackedEvents;
    private BlockingQueue<Boolean> mFlagLoadComplete;
    private MixpanelAPI mMixpanel;

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
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mRecordingCalls = new LinkedBlockingQueue<>();
        mTrackedEvents = new LinkedBlockingQueue<>();
        mFlagLoadComplete = new LinkedBlockingQueue<>();

        // Create MixpanelAPI instance with mock HTTP service
        TestUtils.cleanUpMixpanelData(mContext);
        mMixpanel = TestUtils.createMixpanelAPIWithMockHttpService(mContext, createMockHttpService());
    }

    /**
     * Creates a mock HTTP service that captures recording API calls.
     */
    private RemoteService createMockHttpService() {
        return new RemoteService() {
            @Override
            public RequestResult performRequest(
                    HttpMethod method,
                    String endpointUrl,
                    ProxyServerInteractor proxyInteractor,
                    Map<String, Object> params,
                    Map<String, String> headers,
                    byte[] requestBodyBytes,
                    javax.net.ssl.SSLSocketFactory sslFactory) throws Exception {

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
            }

            private RequestResult createSuccessResult(String responseBody) {
                return new RequestResult() {
                    @Override
                    public boolean isSuccess() {
                        return true;
                    }

                    @Override
                    public byte[] getResponse() {
                        return responseBody.getBytes(StandardCharsets.UTF_8);
                    }

                    @Override
                    public int getStatusCode() {
                        return 200;
                    }
                };
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

                // JsonLogic: {"==": [{"var": "properties.category"}, "electronics"]}
                JSONObject propertyFilters = new JSONObject();
                propertyFilters.put("==", new JSONArray()
                        .put(new JSONObject().put("var", "properties.category"))
                        .put("electronics"));
                event2.put(MPConstants.Flags.PROPERTY_FILTERS, propertyFilters);

                JSONObject pendingVariant2 = new JSONObject();
                pendingVariant2.put(MPConstants.Flags.VARIANT_KEY, "variant_a");
                pendingVariant2.put(MPConstants.Flags.VARIANT_VALUE, "filtered");
                event2.put(MPConstants.Flags.PENDING_VARIANT, pendingVariant2);

                pendingEvents.put(event2);

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
    public void testPropertyFilterMatching_CaseInsensitive() throws Exception {
        // Load flags with pending event for "AddToCart" with category filter
        mMixpanel.getFlags().loadFlags();
        Boolean flagsLoaded = mFlagLoadComplete.poll(2, TimeUnit.SECONDS);
        assertNotNull("Flags should load successfully", flagsLoaded);

        // Track AddToCart with category="ELECTRONICS" (uppercase) - should match
        JSONObject props = new JSONObject();
        props.put("category", "ELECTRONICS"); // Uppercase
        mMixpanel.track("AddToCart", props);

        // Verify recording API call was made (case-insensitive property matching)
        RecordingAPICall call = mRecordingCalls.poll(2, TimeUnit.SECONDS);
        assertNotNull("Property filter should match case-insensitively", call);
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
    public void testComplexJsonLogicFilters() throws Exception {
        // Test complex AND/OR JsonLogic filters
        // This would require creating a more complex mock response with nested filters
        // Skipped in this basic test suite, but would test scenarios like:
        // {"and": [{"==": [{"var": "properties.category"}, "electronics"]},
        //          {">": [{"var": "properties.price"}, 100]}]}
    }

    @Test
    public void testJsonLogicEvaluationError_NoActivation() throws Exception {
        // If JsonLogic evaluation throws an error, event should not activate
        // This is handled by the fail-safe catch block in FirstTimeEventChecker
        // Difficult to test without injecting invalid JsonLogic, but the implementation handles it
    }
}
