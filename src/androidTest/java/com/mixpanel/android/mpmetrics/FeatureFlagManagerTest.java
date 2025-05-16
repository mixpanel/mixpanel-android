package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.mixpanel.android.util.MPLog;
import com.mixpanel.android.util.OfflineMode; // Assuming this exists
import com.mixpanel.android.util.ProxyServerInteractor;
import com.mixpanel.android.util.RemoteService;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLSocketFactory;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FeatureFlagManagerTest {

    private FeatureFlagManager mFeatureFlagManager;
    private MockFeatureFlagDelegate mMockDelegate;
    private MockRemoteService mMockRemoteService;
    private MPConfig mTestConfig;
    private Context mContext;

    private static final String TEST_SERVER_URL = "https://test.mixpanel.com";
    private static final String TEST_DISTINCT_ID = "test_distinct_id";
    private static final String TEST_TOKEN = "test_token";
    private static final long ASYNC_TEST_TIMEOUT_MS = 2000; // 2 seconds

    // Helper class for capturing requests made to the mock service
    private static class CapturedRequest {
        final String endpointUrl;
        final Map<String, String> headers;
        final byte[] requestBodyBytes;

        CapturedRequest(String endpointUrl, Map<String, String> headers, byte[] requestBodyBytes) {
            this.endpointUrl = endpointUrl;
            this.headers = headers;
            this.requestBodyBytes = requestBodyBytes;
        }

        public JSONObject getRequestBodyAsJson() throws JSONException {
            if (requestBodyBytes == null) return null;
            return new JSONObject(new String(requestBodyBytes, StandardCharsets.UTF_8));
        }
    }

    private static class MockFeatureFlagDelegate implements FeatureFlagDelegate {
        MPConfig configToReturn;
        String distinctIdToReturn = TEST_DISTINCT_ID;
        String tokenToReturn = TEST_TOKEN;
        List<TrackCall> trackCalls = new ArrayList<>();
        CountDownLatch trackCalledLatch; // Optional: for tests waiting for track

        static class TrackCall {
            final String eventName;
            final JSONObject properties;
            TrackCall(String eventName, JSONObject properties) {
                this.eventName = eventName;
                this.properties = properties;
            }
        }

        public MockFeatureFlagDelegate(MPConfig config) {
            this.configToReturn = config;
        }

        @Override
        public MPConfig getMPConfig() {
            return configToReturn;
        }

        @Override
        public String getDistinctId() {
            return distinctIdToReturn;
        }

        @Override
        public void track(String eventName, JSONObject properties) {
            MPLog.v("FeatureFlagManagerTest", "MockDelegate.track called: " + eventName);
            trackCalls.add(new TrackCall(eventName, properties));
            if (trackCalledLatch != null) {
                trackCalledLatch.countDown();
            }
        }

        @Override
        public String getToken() {
            return tokenToReturn;
        }

        public void resetTrackCalls() {
            trackCalls.clear();
            trackCalledLatch = null;
        }
    }

    private static class MockRemoteService implements RemoteService {
        // Queue to hold responses/exceptions to be returned by performRequest
        private final BlockingQueue<Object> mResults = new ArrayBlockingQueue<>(10);
        // Queue to capture actual requests made
        private final BlockingQueue<CapturedRequest> mCapturedRequests = new ArrayBlockingQueue<>(10);

        @Override
        public boolean isOnline(Context context, OfflineMode offlineMode) {
            return true; // Assume online for tests unless specified
        }

        @Override
        public void checkIsMixpanelBlocked() {
            // No-op for tests
        }

        @Override
        public byte[] performRequest(
                @NonNull String endpointUrl,
                @Nullable ProxyServerInteractor interactor,
                @Nullable Map<String, Object> params,
                @Nullable Map<String, String> headers,
                @Nullable byte[] requestBodyBytes,
                @Nullable SSLSocketFactory socketFactory)
                throws ServiceUnavailableException, IOException {

            mCapturedRequests.offer(new CapturedRequest(endpointUrl, headers, requestBodyBytes));

            try {
                Object result = mResults.poll(FeatureFlagManagerTest.ASYNC_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (result == null) {
                    throw new IOException("MockRemoteService timed out waiting for a result to be queued.");
                }
                if (result instanceof IOException) {
                    throw (IOException) result;
                }
                if (result instanceof ServiceUnavailableException) {
                    throw (ServiceUnavailableException) result;
                }
                if (result instanceof RuntimeException) { // For other test exceptions
                    throw (RuntimeException) result;
                }
                return (byte[]) result;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("MockRemoteService interrupted.", e);
            }
        }

        public void addResponse(byte[] responseBytes) {
            mResults.offer(responseBytes);
        }

        public void addResponse(JSONObject responseJson) {
            mResults.offer(responseJson.toString().getBytes(StandardCharsets.UTF_8));
        }

        public void addError(Exception e) {
            mResults.offer(e);
        }

        public CapturedRequest takeRequest(long timeout, TimeUnit unit) throws InterruptedException {
            return mCapturedRequests.poll(timeout, unit);
        }

        public void reset() {
            mResults.clear();
            mCapturedRequests.clear();
        }
    }

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // MPConfig requires a context and a token (even if we override methods)
        // Create a basic MPConfig. Specific flag settings will be set via MockDelegate.
        mTestConfig = new MPConfig(new Bundle(), mContext, TEST_TOKEN);


        mMockDelegate = new MockFeatureFlagDelegate(mTestConfig);
        mMockRemoteService = new MockRemoteService();

        mFeatureFlagManager = new FeatureFlagManager(
                mMockDelegate, // Pass delegate directly, manager will wrap in WeakReference
                mMockRemoteService,
                new FlagsConfig(true, new JSONObject())
        );
        MPLog.setLevel(MPLog.VERBOSE); // Enable verbose logging for tests
    }

    @After
    public void tearDown() {
        // Ensure handler thread is quit if it's still running, though manager re-creation handles it
        // For more robust cleanup, FeatureFlagManager could have a .release() method
    }

    // Helper method to create a valid flags JSON response string
    private String createFlagsResponseJson(Map<String, MixpanelFlagVariant> flags) {
        JSONObject flagsObject = new JSONObject();
        try {
            for (Map.Entry<String, MixpanelFlagVariant> entry : flags.entrySet()) {
                JSONObject flagDef = new JSONObject();
                flagDef.put("variant_key", entry.getValue().key);
                // Need to handle different types for value properly
                if (entry.getValue().value == null) {
                    flagDef.put("variant_value", JSONObject.NULL);
                } else {
                    flagDef.put("variant_value", entry.getValue().value);
                }
                flagsObject.put(entry.getKey(), flagDef);
            }
            return new JSONObject().put("flags", flagsObject).toString();
        } catch (JSONException e) {
            throw new RuntimeException("Error creating test JSON", e);
        }
    }

    // Helper to simulate MPConfig having specific FlagsConfig
    private void setupFlagsConfig(boolean enabled, @Nullable JSONObject context) {
        final JSONObject finalContext = (context == null) ? new JSONObject() : context;
        final FlagsConfig flagsConfig = new FlagsConfig(enabled, finalContext);

        mMockDelegate.configToReturn = new MPConfig(new Bundle(), mContext, TEST_TOKEN) {
            @Override
            public String getEventsEndpoint() { // Ensure server URL source
                return TEST_SERVER_URL + "/track/";
            }

            @Override
            public String getFlagsEndpoint() { // Ensure server URL source
                return TEST_SERVER_URL + "/flags/";
            }
        };

        mFeatureFlagManager = new FeatureFlagManager(
            mMockDelegate,
            mMockRemoteService,
            flagsConfig
        );
    }

    // ---- Test Cases ----

    @Test
    public void testAreFlagsReady_initialState() {
        assertFalse("Features should not be ready initially", mFeatureFlagManager.areFlagsReady());
    }

    @Test
    public void testLoadFlags_whenDisabled_doesNotFetch() throws InterruptedException {
        setupFlagsConfig(false, null); // Flags disabled
        mFeatureFlagManager.loadFlags();

        // Wait a bit to ensure no network call is attempted
        Thread.sleep(200); // Give handler thread time to process if it were to fetch

        CapturedRequest request = mMockRemoteService.takeRequest(100, TimeUnit.MILLISECONDS);
        assertNull("No network request should be made when flags are disabled", request);
        assertFalse("areFeaturesReady should be false", mFeatureFlagManager.areFlagsReady());
    }

    @Test
    public void testLoadFlags_whenEnabled_andFetchSucceeds_flagsBecomeReady() throws InterruptedException {
        setupFlagsConfig(true, new JSONObject()); // Flags enabled

        Map<String, MixpanelFlagVariant> testFlags = new HashMap<>();
        testFlags.put("flag1", new MixpanelFlagVariant("v1", true));
        String responseJson = createFlagsResponseJson(testFlags);
        mMockRemoteService.addResponse(responseJson.getBytes(StandardCharsets.UTF_8));

        mFeatureFlagManager.loadFlags();

        // Wait for fetch to complete (network call + handler processing)
        // Ideally, use CountDownLatch if loadFlags had a completion,
        // but for now, poll areFeaturesReady or wait a fixed time.
        boolean ready = false;
        for (int i = 0; i < 20; i++) { // Poll for up to 2 seconds
            if (mFeatureFlagManager.areFlagsReady()) {
                ready = true;
                break;
            }
            Thread.sleep(100);
        }
        assertTrue("Flags should become ready after successful fetch", ready);

        CapturedRequest request = mMockRemoteService.takeRequest(ASYNC_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertNotNull("A network request should have been made", request);
        assertTrue("Endpoint should be for flags", request.endpointUrl.endsWith("/flags/"));
    }


    @Test
    public void testLoadFlags_whenEnabled_andFetchFails_flagsNotReady() throws InterruptedException {
        setupFlagsConfig(true, new JSONObject());
        mMockRemoteService.addError(new IOException("Network unavailable"));

        mFeatureFlagManager.loadFlags();

        // Wait a bit to see if flags become ready (they shouldn't)
        Thread.sleep(500); // Enough time for the fetch attempt and failure processing

        assertFalse("Flags should not be ready after failed fetch", mFeatureFlagManager.areFlagsReady());
        CapturedRequest request = mMockRemoteService.takeRequest(ASYNC_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertNotNull("A network request should have been attempted", request);
    }

    @Test
    public void testGetVariantSync_flagsNotReady_returnsFallback() {
        setupFlagsConfig(true, null); // Enabled, but no flags loaded yet
        assertFalse(mFeatureFlagManager.areFlagsReady());

        MixpanelFlagVariant fallback = new MixpanelFlagVariant("fb_key", "fb_value");
        MixpanelFlagVariant result = mFeatureFlagManager.getVariantSync("my_flag", fallback);

        assertEquals("Should return fallback key", fallback.key, result.key);
        assertEquals("Should return fallback value", fallback.value, result.value);
    }

    @Test
    public void testGetVariantSync_flagsReady_flagExists() throws InterruptedException {
        setupFlagsConfig(true, new JSONObject());
        Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
        serverFlags.put("test_flag", new MixpanelFlagVariant("variant_A", "hello"));
        mMockRemoteService.addResponse(createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));
        mFeatureFlagManager.loadFlags();
        // Wait for flags to load
        for(int i = 0; i<20 && !mFeatureFlagManager.areFlagsReady(); ++i) Thread.sleep(100);
        assertTrue(mFeatureFlagManager.areFlagsReady());

        MixpanelFlagVariant fallback = new MixpanelFlagVariant("fallback_key", "fallback_value");
        MixpanelFlagVariant result = mFeatureFlagManager.getVariantSync("test_flag", fallback);

        assertEquals("Should return actual flag key", "variant_A", result.key);
        assertEquals("Should return actual flag value", "hello", result.value);
    }

    @Test
    public void testGetVariantSync_flagsReady_flagMissing_returnsFallback() throws InterruptedException {
        setupFlagsConfig(true, new JSONObject());
        Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
        serverFlags.put("another_flag", new MixpanelFlagVariant("variant_B", 123));
        mMockRemoteService.addResponse(createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));
        mFeatureFlagManager.loadFlags();
        for(int i = 0; i<20 && !mFeatureFlagManager.areFlagsReady(); ++i) Thread.sleep(100);
        assertTrue(mFeatureFlagManager.areFlagsReady());

        MixpanelFlagVariant fallback = new MixpanelFlagVariant("fb_key_sync", "fb_value_sync");
        MixpanelFlagVariant result = mFeatureFlagManager.getVariantSync("non_existent_flag", fallback);

        assertEquals("Should return fallback key", fallback.key, result.key);
        assertEquals("Should return fallback value", fallback.value, result.value);
    }

    @Test
    public void testGetVariant_Async_flagsReady_flagExists() throws InterruptedException {
        setupFlagsConfig(true, new JSONObject());
        Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
        serverFlags.put("async_flag", new MixpanelFlagVariant("v_async", true));
        mMockRemoteService.addResponse(createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));
        mFeatureFlagManager.loadFlags();
        for(int i = 0; i<20 && !mFeatureFlagManager.areFlagsReady(); ++i) Thread.sleep(100);
        assertTrue(mFeatureFlagManager.areFlagsReady());

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<MixpanelFlagVariant> resultRef = new AtomicReference<>();
        MixpanelFlagVariant fallback = new MixpanelFlagVariant("fb", false);

        mFeatureFlagManager.getVariant("async_flag", fallback, result -> {
            resultRef.set(result);
            latch.countDown();
        });

        assertTrue("Callback should complete within timeout", latch.await(ASYNC_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertNotNull(resultRef.get());
        assertEquals("v_async", resultRef.get().key);
        assertEquals(true, resultRef.get().value);
    }

    @Test
    public void testGetVariant_Async_flagsNotReady_fetchSucceeds() throws InterruptedException {
        setupFlagsConfig(true, new JSONObject()); // Enabled for fetch
        assertFalse(mFeatureFlagManager.areFlagsReady());

        Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
        serverFlags.put("fetch_flag_async", new MixpanelFlagVariant("fetched_variant", "fetched_value"));
        mMockRemoteService.addResponse(createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));
        // No loadFlags() call here, getFeature should trigger it

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<MixpanelFlagVariant> resultRef = new AtomicReference<>();
        MixpanelFlagVariant fallback = new MixpanelFlagVariant("fb_fetch", "fb_val_fetch");

        mFeatureFlagManager.getVariant("fetch_flag_async", fallback, result -> {
            resultRef.set(result);
            latch.countDown();
        });

        assertTrue("Callback should complete within timeout", latch.await(ASYNC_TEST_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS)); // Longer timeout for fetch
        assertNotNull(resultRef.get());
        assertEquals("fetched_variant", resultRef.get().key);
        assertEquals("fetched_value", resultRef.get().value);
        assertTrue(mFeatureFlagManager.areFlagsReady());
    }

    @Test
    public void testTracking_getVariantSync_calledOnce() throws InterruptedException, JSONException {
        setupFlagsConfig(true, new JSONObject());
        Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
        serverFlags.put("track_flag_sync", new MixpanelFlagVariant("v_track_sync", "val"));
        mMockRemoteService.addResponse(createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));
        mFeatureFlagManager.loadFlags();
        for(int i = 0; i<20 && !mFeatureFlagManager.areFlagsReady(); ++i) Thread.sleep(100);
        assertTrue(mFeatureFlagManager.areFlagsReady());

        mMockDelegate.resetTrackCalls();
        mMockDelegate.trackCalledLatch = new CountDownLatch(1);

        MixpanelFlagVariant fallback = new MixpanelFlagVariant("", null);
        mFeatureFlagManager.getVariantSync("track_flag_sync", fallback); // First call, should track
        assertTrue("Track should have been called", mMockDelegate.trackCalledLatch.await(ASYNC_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals("Track should be called once", 1, mMockDelegate.trackCalls.size());

        // Second call, should NOT track again
        mFeatureFlagManager.getVariantSync("track_flag_sync", fallback);
        // Allow some time for potential erroneous track call
        Thread.sleep(200);
        assertEquals("Track should still be called only once", 1, mMockDelegate.trackCalls.size());

        MockFeatureFlagDelegate.TrackCall call = mMockDelegate.trackCalls.get(0);
        assertEquals("$experiment_started", call.eventName);
        assertEquals("track_flag_sync", call.properties.getString("Experiment name"));
        assertEquals("v_track_sync", call.properties.getString("Variant name"));
    }

    @Test
    public void testGetVariant_Async_flagsNotReady_fetchFails_returnsFallback() throws InterruptedException {
        setupFlagsConfig(true, new JSONObject());
        assertFalse(mFeatureFlagManager.areFlagsReady());

        mMockRemoteService.addError(new IOException("Simulated fetch failure"));

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<MixpanelFlagVariant> resultRef = new AtomicReference<>();
        final MixpanelFlagVariant fallback = new MixpanelFlagVariant("fb_async_fail", "val_async_fail");

        mFeatureFlagManager.getVariant("some_flag_on_fail", fallback, result -> {
            resultRef.set(result);
            latch.countDown();
        });

        assertTrue("Callback should complete within timeout", latch.await(ASYNC_TEST_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS));
        assertNotNull(resultRef.get());
        assertEquals(fallback.key, resultRef.get().key);
        assertEquals(fallback.value, resultRef.get().value);
        assertFalse(mFeatureFlagManager.areFlagsReady());
        assertEquals(0, mMockDelegate.trackCalls.size()); // No tracking on fallback
    }

    // TODO: More tests for getFeatureDataSync, isFeatureEnabledSync
    // TODO: More tests for getFeatureData (async), isFeatureEnabled (async)
    // TODO: Test concurrent calls to loadFlags
    // TODO: Test concurrent calls to getFeature when flags are not ready
    // TODO: Test request body construction in _performFetchRequest (via MockRemoteService)
    // TODO: Test FlagsConfig context usage
}