package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.*;

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
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLSocketFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

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
        Object result =
            mResults.poll(FeatureFlagManagerTest.ASYNC_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
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

    mFeatureFlagManager =
        new FeatureFlagManager(
            mMockDelegate, // Pass delegate directly, manager will wrap in WeakReference
            mMockRemoteService,
            new FlagsConfig(true, new JSONObject()));
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

    mMockDelegate.configToReturn =
        new MPConfig(new Bundle(), mContext, TEST_TOKEN) {
          @Override
          public String getEventsEndpoint() { // Ensure server URL source
            return TEST_SERVER_URL + "/track/";
          }

          @Override
          public String getFlagsEndpoint() { // Ensure server URL source
            return TEST_SERVER_URL + "/flags/";
          }
        };

    mFeatureFlagManager = new FeatureFlagManager(mMockDelegate, mMockRemoteService, flagsConfig);
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
  public void testLoadFlags_whenEnabled_andFetchSucceeds_flagsBecomeReady()
      throws InterruptedException {
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

    CapturedRequest request =
        mMockRemoteService.takeRequest(ASYNC_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
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

    assertFalse(
        "Flags should not be ready after failed fetch", mFeatureFlagManager.areFlagsReady());
    CapturedRequest request =
        mMockRemoteService.takeRequest(ASYNC_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
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
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));
    mFeatureFlagManager.loadFlags();
    // Wait for flags to load
    for (int i = 0; i < 20 && !mFeatureFlagManager.areFlagsReady(); ++i) Thread.sleep(100);
    assertTrue(mFeatureFlagManager.areFlagsReady());

    MixpanelFlagVariant fallback = new MixpanelFlagVariant("fallback_key", "fallback_value");
    MixpanelFlagVariant result = mFeatureFlagManager.getVariantSync("test_flag", fallback);

    assertEquals("Should return actual flag key", "variant_A", result.key);
    assertEquals("Should return actual flag value", "hello", result.value);
  }

  @Test
  public void testGetVariantSync_flagsReady_flagMissing_returnsFallback()
      throws InterruptedException {
    setupFlagsConfig(true, new JSONObject());
    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("another_flag", new MixpanelFlagVariant("variant_B", 123));
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));
    mFeatureFlagManager.loadFlags();
    for (int i = 0; i < 20 && !mFeatureFlagManager.areFlagsReady(); ++i) Thread.sleep(100);
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
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));
    mFeatureFlagManager.loadFlags();
    for (int i = 0; i < 20 && !mFeatureFlagManager.areFlagsReady(); ++i) Thread.sleep(100);
    assertTrue(mFeatureFlagManager.areFlagsReady());

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<MixpanelFlagVariant> resultRef = new AtomicReference<>();
    MixpanelFlagVariant fallback = new MixpanelFlagVariant("fb", false);

    mFeatureFlagManager.getVariant(
        "async_flag",
        fallback,
        result -> {
          resultRef.set(result);
          latch.countDown();
        });

    assertTrue(
        "Callback should complete within timeout",
        latch.await(ASYNC_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    assertNotNull(resultRef.get());
    assertEquals("v_async", resultRef.get().key);
    assertEquals(true, resultRef.get().value);
  }

  @Test
  public void testGetVariant_Async_flagsNotReady_fetchSucceeds() throws InterruptedException {
    setupFlagsConfig(true, new JSONObject()); // Enabled for fetch
    assertFalse(mFeatureFlagManager.areFlagsReady());

    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put(
        "fetch_flag_async", new MixpanelFlagVariant("fetched_variant", "fetched_value"));
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));
    // No loadFlags() call here, getFeature should trigger it

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<MixpanelFlagVariant> resultRef = new AtomicReference<>();
    MixpanelFlagVariant fallback = new MixpanelFlagVariant("fb_fetch", "fb_val_fetch");

    mFeatureFlagManager.getVariant(
        "fetch_flag_async",
        fallback,
        result -> {
          resultRef.set(result);
          latch.countDown();
        });

    assertTrue(
        "Callback should complete within timeout",
        latch.await(ASYNC_TEST_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS)); // Longer timeout for fetch
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
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));
    mFeatureFlagManager.loadFlags();
    for (int i = 0; i < 20 && !mFeatureFlagManager.areFlagsReady(); ++i) Thread.sleep(100);
    assertTrue(mFeatureFlagManager.areFlagsReady());

    mMockDelegate.resetTrackCalls();
    mMockDelegate.trackCalledLatch = new CountDownLatch(1);

    MixpanelFlagVariant fallback = new MixpanelFlagVariant("", null);
    mFeatureFlagManager.getVariantSync("track_flag_sync", fallback); // First call, should track
    assertTrue(
        "Track should have been called",
        mMockDelegate.trackCalledLatch.await(ASYNC_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
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
  public void testGetVariant_Async_flagsNotReady_fetchFails_returnsFallback()
      throws InterruptedException {
    setupFlagsConfig(true, new JSONObject());
    assertFalse(mFeatureFlagManager.areFlagsReady());

    mMockRemoteService.addError(new IOException("Simulated fetch failure"));

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<MixpanelFlagVariant> resultRef = new AtomicReference<>();
    final MixpanelFlagVariant fallback = new MixpanelFlagVariant("fb_async_fail", "val_async_fail");

    mFeatureFlagManager.getVariant(
        "some_flag_on_fail",
        fallback,
        result -> {
          resultRef.set(result);
          latch.countDown();
        });

    assertTrue(
        "Callback should complete within timeout",
        latch.await(ASYNC_TEST_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS));
    assertNotNull(resultRef.get());
    assertEquals(fallback.key, resultRef.get().key);
    assertEquals(fallback.value, resultRef.get().value);
    assertFalse(mFeatureFlagManager.areFlagsReady());
    assertEquals(0, mMockDelegate.trackCalls.size()); // No tracking on fallback
  }

  @Test
  public void testIsEnabledSync_flagsReady_flagExistsWithBooleanTrue() throws InterruptedException {
    setupFlagsConfig(true, new JSONObject());
    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("bool_flag_true", new MixpanelFlagVariant("enabled", true));
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));
    mFeatureFlagManager.loadFlags();
    for (int i = 0; i < 20 && !mFeatureFlagManager.areFlagsReady(); ++i) Thread.sleep(100);
    assertTrue(mFeatureFlagManager.areFlagsReady());

    boolean result = mFeatureFlagManager.isEnabledSync("bool_flag_true", false);
    assertTrue("Should return true when flag value is true", result);
  }

  @Test
  public void testIsEnabledSync_flagsReady_flagExistsWithBooleanFalse()
      throws InterruptedException {
    setupFlagsConfig(true, new JSONObject());
    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("bool_flag_false", new MixpanelFlagVariant("disabled", false));
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));
    mFeatureFlagManager.loadFlags();
    for (int i = 0; i < 20 && !mFeatureFlagManager.areFlagsReady(); ++i) Thread.sleep(100);
    assertTrue(mFeatureFlagManager.areFlagsReady());

    boolean result = mFeatureFlagManager.isEnabledSync("bool_flag_false", true);
    assertFalse("Should return false when flag value is false", result);
  }

  @Test
  public void testIsEnabledSync_flagsReady_flagDoesNotExist_returnsFallback()
      throws InterruptedException {
    setupFlagsConfig(true, new JSONObject());
    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("other_flag", new MixpanelFlagVariant("v1", "value"));
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));
    mFeatureFlagManager.loadFlags();
    for (int i = 0; i < 20 && !mFeatureFlagManager.areFlagsReady(); ++i) Thread.sleep(100);
    assertTrue(mFeatureFlagManager.areFlagsReady());

    boolean result = mFeatureFlagManager.isEnabledSync("missing_bool_flag", true);
    assertTrue("Should return fallback value (true) when flag doesn't exist", result);

    boolean result2 = mFeatureFlagManager.isEnabledSync("missing_bool_flag", false);
    assertFalse("Should return fallback value (false) when flag doesn't exist", result2);
  }

  @Test
  public void testIsEnabledSync_flagsNotReady_returnsFallback() {
    setupFlagsConfig(true, null);
    assertFalse(mFeatureFlagManager.areFlagsReady());

    boolean result = mFeatureFlagManager.isEnabledSync("any_flag", true);
    assertTrue("Should return fallback value (true) when flags not ready", result);

    boolean result2 = mFeatureFlagManager.isEnabledSync("any_flag", false);
    assertFalse("Should return fallback value (false) when flags not ready", result2);
  }

  @Test
  public void testIsEnabledSync_flagsReady_nonBooleanValue_returnsFallback()
      throws InterruptedException {
    setupFlagsConfig(true, new JSONObject());
    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("string_flag", new MixpanelFlagVariant("v1", "not_a_boolean"));
    serverFlags.put("number_flag", new MixpanelFlagVariant("v2", 123));
    serverFlags.put("null_flag", new MixpanelFlagVariant("v3", null));
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));
    mFeatureFlagManager.loadFlags();
    for (int i = 0; i < 20 && !mFeatureFlagManager.areFlagsReady(); ++i) Thread.sleep(100);
    assertTrue(mFeatureFlagManager.areFlagsReady());

    assertTrue(
        "String value should return fallback true",
        mFeatureFlagManager.isEnabledSync("string_flag", true));
    assertFalse(
        "String value should return fallback false",
        mFeatureFlagManager.isEnabledSync("string_flag", false));

    assertTrue(
        "Number value should return fallback true",
        mFeatureFlagManager.isEnabledSync("number_flag", true));
    assertFalse(
        "Number value should return fallback false",
        mFeatureFlagManager.isEnabledSync("number_flag", false));

    assertTrue(
        "Null value should return fallback true",
        mFeatureFlagManager.isEnabledSync("null_flag", true));
    assertFalse(
        "Null value should return fallback false",
        mFeatureFlagManager.isEnabledSync("null_flag", false));
  }

  @Test
  public void testIsEnabled_Async_flagsReady_booleanTrue() throws InterruptedException {
    setupFlagsConfig(true, new JSONObject());
    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("async_bool_true", new MixpanelFlagVariant("v_true", true));
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));
    mFeatureFlagManager.loadFlags();
    for (int i = 0; i < 20 && !mFeatureFlagManager.areFlagsReady(); ++i) Thread.sleep(100);
    assertTrue(mFeatureFlagManager.areFlagsReady());

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Boolean> resultRef = new AtomicReference<>();

    mFeatureFlagManager.isEnabled(
        "async_bool_true",
        false,
        result -> {
          resultRef.set(result);
          latch.countDown();
        });

    assertTrue(
        "Callback should complete within timeout",
        latch.await(ASYNC_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    assertNotNull(resultRef.get());
    assertTrue("Should return true for boolean true flag", resultRef.get());
  }

  @Test
  public void testIsEnabled_Async_flagsReady_booleanFalse() throws InterruptedException {
    setupFlagsConfig(true, new JSONObject());
    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("async_bool_false", new MixpanelFlagVariant("v_false", false));
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));
    mFeatureFlagManager.loadFlags();
    for (int i = 0; i < 20 && !mFeatureFlagManager.areFlagsReady(); ++i) Thread.sleep(100);
    assertTrue(mFeatureFlagManager.areFlagsReady());

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Boolean> resultRef = new AtomicReference<>();

    mFeatureFlagManager.isEnabled(
        "async_bool_false",
        true,
        result -> {
          resultRef.set(result);
          latch.countDown();
        });

    assertTrue(
        "Callback should complete within timeout",
        latch.await(ASYNC_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    assertNotNull(resultRef.get());
    assertFalse("Should return false for boolean false flag", resultRef.get());
  }

  @Test
  public void testIsEnabled_Async_flagsNotReady_fetchSucceeds() throws InterruptedException {
    setupFlagsConfig(true, new JSONObject());
    assertFalse(mFeatureFlagManager.areFlagsReady());

    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("fetch_bool_flag", new MixpanelFlagVariant("fetched", true));
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Boolean> resultRef = new AtomicReference<>();

    mFeatureFlagManager.isEnabled(
        "fetch_bool_flag",
        false,
        result -> {
          resultRef.set(result);
          latch.countDown();
        });

    assertTrue(
        "Callback should complete within timeout",
        latch.await(ASYNC_TEST_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS));
    assertNotNull(resultRef.get());
    assertTrue("Should return true after successful fetch", resultRef.get());
    assertTrue(mFeatureFlagManager.areFlagsReady());
  }

  @Test
  public void testIsEnabled_Async_nonBooleanValue_returnsFallback() throws InterruptedException {
    setupFlagsConfig(true, new JSONObject());
    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("string_async", new MixpanelFlagVariant("v_str", "hello"));
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));
    mFeatureFlagManager.loadFlags();
    for (int i = 0; i < 20 && !mFeatureFlagManager.areFlagsReady(); ++i) Thread.sleep(100);
    assertTrue(mFeatureFlagManager.areFlagsReady());

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Boolean> resultRef = new AtomicReference<>();

    mFeatureFlagManager.isEnabled(
        "string_async",
        true,
        result -> {
          resultRef.set(result);
          latch.countDown();
        });

    assertTrue(
        "Callback should complete within timeout",
        latch.await(ASYNC_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    assertNotNull(resultRef.get());
    assertTrue("Should return fallback (true) for non-boolean value", resultRef.get());
  }

  @Test
  public void testIsEnabled_Async_fetchFails_returnsFallback() throws InterruptedException {
    setupFlagsConfig(true, new JSONObject());
    assertFalse(mFeatureFlagManager.areFlagsReady());

    mMockRemoteService.addError(new IOException("Network error"));

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Boolean> resultRef = new AtomicReference<>();

    mFeatureFlagManager.isEnabled(
        "fail_flag",
        true,
        result -> {
          resultRef.set(result);
          latch.countDown();
        });

    assertTrue(
        "Callback should complete within timeout",
        latch.await(ASYNC_TEST_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS));
    assertNotNull(resultRef.get());
    assertTrue("Should return fallback (true) when fetch fails", resultRef.get());
    assertFalse(mFeatureFlagManager.areFlagsReady());
  }

  @Test
  public void testConcurrentLoadFlagsCalls() throws InterruptedException {
    // Setup with flags enabled
    setupFlagsConfig(true, new JSONObject());

    // Track number of network requests made
    final AtomicInteger requestCount = new AtomicInteger(0);

    // Prepare response data
    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("concurrent_flag", new MixpanelFlagVariant("test_variant", "test_value"));

    // Create a custom MockRemoteService that counts requests and introduces delay
    MockRemoteService customMockService =
        new MockRemoteService() {
          @Override
          public byte[] performRequest(
              String endpointUrl,
              ProxyServerInteractor interactor,
              Map<String, Object> params,
              Map<String, String> headers,
              byte[] requestBodyBytes,
              SSLSocketFactory socketFactory)
              throws ServiceUnavailableException, IOException {
            // Count the request
            requestCount.incrementAndGet();

            // Introduce a delay to simulate network latency
            try {
              Thread.sleep(100);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }

            // Return the prepared response
            return super.performRequest(
                endpointUrl, interactor, params, headers, requestBodyBytes, socketFactory);
          }
        };

    // Add response to the custom mock service
    customMockService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));

    // Use reflection to set the custom mock service
    try {
      Field httpServiceField = FeatureFlagManager.class.getDeclaredField("mHttpService");
      httpServiceField.setAccessible(true);
      httpServiceField.set(mFeatureFlagManager, customMockService);
    } catch (Exception e) {
      fail("Failed to set mock http service: " + e.getMessage());
    }

    // Number of concurrent threads
    final int threadCount = 10;
    final CountDownLatch startLatch = new CountDownLatch(1);
    final CountDownLatch completionLatch = new CountDownLatch(threadCount);
    final List<Thread> threads = new ArrayList<>();
    final AtomicInteger successCount = new AtomicInteger(0);

    // Create multiple threads that will call loadFlags concurrently
    for (int i = 0; i < threadCount; i++) {
      Thread thread =
          new Thread(
              () -> {
                try {
                  // Wait for signal to start all threads simultaneously
                  startLatch.await();
                  // Call loadFlags
                  mFeatureFlagManager.loadFlags();
                  successCount.incrementAndGet();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  completionLatch.countDown();
                }
              });
      threads.add(thread);
      thread.start();
    }

    // Start all threads at the same time
    startLatch.countDown();

    // Wait for all threads to complete
    assertTrue(
        "All threads should complete within timeout",
        completionLatch.await(5000, TimeUnit.MILLISECONDS));

    // Wait a bit more for all loadFlags operations to complete
    Thread.sleep(500);

    // Verify results
    assertEquals("All threads should have completed successfully", threadCount, successCount.get());

    // Only one network request should have been made despite multiple concurrent calls
    // This verifies that loadFlags properly handles concurrent calls
    assertEquals(
        "Should only make one network request for concurrent loadFlags calls",
        1,
        requestCount.get());

    // Verify flags are ready
    assertTrue("Flags should be ready after concurrent loads", mFeatureFlagManager.areFlagsReady());

    // Test accessing the flag synchronously
    MixpanelFlagVariant variant =
        mFeatureFlagManager.getVariantSync("concurrent_flag", new MixpanelFlagVariant("default"));
    assertNotNull("Flag variant should not be null", variant);
    assertEquals("test_variant", variant.key);
    assertEquals("test_value", variant.value);
  }

  @Test
  public void testConcurrentGetVariantCalls_whenFlagsNotReady() throws InterruptedException {
    // Setup with flags enabled
    setupFlagsConfig(true, new JSONObject());

    // Prepare response data that will be delayed
    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("concurrent_get_flag1", new MixpanelFlagVariant("variant1", "value1"));
    serverFlags.put("concurrent_get_flag2", new MixpanelFlagVariant("variant2", "value2"));
    serverFlags.put("concurrent_get_flag3", new MixpanelFlagVariant("variant3", "value3"));

    // Create a mock service that introduces significant delay to simulate slow network
    final AtomicInteger requestCount = new AtomicInteger(0);
    MockRemoteService delayedMockService =
        new MockRemoteService() {
          @Override
          public byte[] performRequest(
              String endpointUrl,
              ProxyServerInteractor interactor,
              Map<String, Object> params,
              Map<String, String> headers,
              byte[] requestBodyBytes,
              SSLSocketFactory socketFactory)
              throws ServiceUnavailableException, IOException {
            requestCount.incrementAndGet();
            // Introduce significant delay to ensure getVariant calls happen before flags are ready
            try {
              Thread.sleep(500);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            return super.performRequest(
                endpointUrl, interactor, params, headers, requestBodyBytes, socketFactory);
          }
        };

    // Add response to the mock service
    delayedMockService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));

    // Use reflection to set the custom mock service
    try {
      Field httpServiceField = FeatureFlagManager.class.getDeclaredField("mHttpService");
      httpServiceField.setAccessible(true);
      httpServiceField.set(mFeatureFlagManager, delayedMockService);
    } catch (Exception e) {
      fail("Failed to set mock http service: " + e.getMessage());
    }

    // Trigger loadFlags which will be delayed
    mFeatureFlagManager.loadFlags();

    // Verify flags are not ready yet
    assertFalse(
        "Flags should not be ready immediately after loadFlags",
        mFeatureFlagManager.areFlagsReady());

    // Number of concurrent threads calling getVariant
    final int threadCount = 20;
    final CountDownLatch startLatch = new CountDownLatch(1);
    final CountDownLatch completionLatch = new CountDownLatch(threadCount);
    final List<Thread> threads = new ArrayList<>();
    final Map<Integer, MixpanelFlagVariant> results = new HashMap<>();
    final AtomicInteger successCount = new AtomicInteger(0);

    // Create multiple threads that will call getVariant concurrently while flags are loading
    for (int i = 0; i < threadCount; i++) {
      final int threadIndex = i;
      final String flagName =
          "concurrent_get_flag" + ((i % 3) + 1); // Rotate through 3 different flags
      final MixpanelFlagVariant fallback =
          new MixpanelFlagVariant("fallback" + threadIndex, "fallback_value" + threadIndex);

      Thread thread =
          new Thread(
              () -> {
                try {
                  // Wait for signal to start all threads simultaneously
                  startLatch.await();

                  // Use async getVariant with callback
                  final CountDownLatch variantLatch = new CountDownLatch(1);
                  final AtomicReference<MixpanelFlagVariant> variantRef = new AtomicReference<>();

                  mFeatureFlagManager.getVariant(
                      flagName,
                      fallback,
                      variant -> {
                        variantRef.set(variant);
                        variantLatch.countDown();
                      });

                  // Wait for callback
                  if (variantLatch.await(2000, TimeUnit.MILLISECONDS)) {
                    results.put(threadIndex, variantRef.get());
                    successCount.incrementAndGet();
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  completionLatch.countDown();
                }
              });
      threads.add(thread);
      thread.start();
    }

    // Start all threads at the same time (while flags are still loading)
    startLatch.countDown();

    // Wait for all threads to complete
    assertTrue(
        "All threads should complete within timeout",
        completionLatch.await(3000, TimeUnit.MILLISECONDS));

    // Verify results
    assertEquals("All threads should have completed successfully", threadCount, successCount.get());

    // Only one network request should have been made
    assertEquals("Should only make one network request", 1, requestCount.get());

    // Verify flags are now ready
    assertTrue(
        "Flags should be ready after all getVariant calls complete",
        mFeatureFlagManager.areFlagsReady());

    // Verify all threads got the correct values (not fallbacks)
    for (int i = 0; i < threadCount; i++) {
      MixpanelFlagVariant result = results.get(i);
      assertNotNull("Thread " + i + " should have a result", result);

      int flagIndex = (i % 3) + 1;
      String expectedKey = "variant" + flagIndex;
      String expectedValue = "value" + flagIndex;

      assertEquals("Thread " + i + " should have correct variant key", expectedKey, result.key);
      assertEquals(
          "Thread " + i + " should have correct variant value", expectedValue, result.value);

      // Verify it's not the fallback
      assertNotEquals("Thread " + i + " should not have fallback key", "fallback" + i, result.key);
    }
  }

  @Test
  public void testRequestBodyConstruction_performFetchRequest()
      throws InterruptedException, JSONException {
    // Setup with flags enabled and specific context data
    JSONObject contextData = new JSONObject();
    contextData.put("$os", "Android");
    contextData.put("$os_version", "13");
    contextData.put("custom_property", "test_value");
    setupFlagsConfig(true, contextData);

    // Set distinct ID for the request
    mMockDelegate.distinctIdToReturn = "test_user_123";

    // Create response data
    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("test_flag", new MixpanelFlagVariant("variant_a", "value_a"));
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));

    // Trigger loadFlags to initiate the request
    mFeatureFlagManager.loadFlags();

    // Capture the request
    CapturedRequest capturedRequest = mMockRemoteService.takeRequest(1000, TimeUnit.MILLISECONDS);
    assertNotNull("Request should have been made", capturedRequest);

    // Verify the endpoint URL
    assertTrue(
        "URL should contain /flags endpoint", capturedRequest.endpointUrl.contains("/flags"));

    // Parse and verify the request body
    assertNotNull("Request should have body", capturedRequest.requestBodyBytes);
    JSONObject requestBody = capturedRequest.getRequestBodyAsJson();
    assertNotNull("Request body should be valid JSON", requestBody);

    // Log the actual request body for debugging
    MPLog.v("FeatureFlagManagerTest", "Request body: " + requestBody.toString());

    // Verify context is included
    assertTrue("Request should contain context", requestBody.has("context"));
    JSONObject requestContext = requestBody.getJSONObject("context");

    // Verify distinct_id is in the context
    assertTrue("Context should contain distinct_id", requestContext.has("distinct_id"));
    assertEquals(
        "Context should contain correct distinct_id",
        "test_user_123",
        requestContext.getString("distinct_id"));

    // Verify the context contains the expected properties from FlagsConfig
    assertEquals("Context should contain $os", "Android", requestContext.getString("$os"));
    assertEquals(
        "Context should contain $os_version", "13", requestContext.getString("$os_version"));
    assertEquals(
        "Context should contain custom_property",
        "test_value",
        requestContext.getString("custom_property"));

    // Verify headers
    assertNotNull("Request should have headers", capturedRequest.headers);
    assertEquals(
        "Content-Type should be application/json with charset",
        "application/json; charset=utf-8",
        capturedRequest.headers.get("Content-Type"));

    // Wait for flags to be ready
    for (int i = 0; i < 20 && !mFeatureFlagManager.areFlagsReady(); i++) {
      Thread.sleep(100);
    }
    assertTrue("Flags should be ready", mFeatureFlagManager.areFlagsReady());
  }

  @Test
  public void testRequestBodyConstruction_withNullContext()
      throws InterruptedException, JSONException {
    // Setup with flags enabled but null context
    setupFlagsConfig(true, null);

    // Set distinct ID
    mMockDelegate.distinctIdToReturn = "user_456";

    // Create response
    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("flag", new MixpanelFlagVariant("v", "val"));
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));

    // Trigger request
    mFeatureFlagManager.loadFlags();

    // Capture request
    CapturedRequest capturedRequest = mMockRemoteService.takeRequest(1000, TimeUnit.MILLISECONDS);
    assertNotNull("Request should have been made", capturedRequest);

    // Parse request body
    JSONObject requestBody = capturedRequest.getRequestBodyAsJson();

    // Verify context exists
    assertTrue("Request should contain context", requestBody.has("context"));
    JSONObject requestContext = requestBody.getJSONObject("context");

    // Verify distinct_id is in context
    assertEquals(
        "Context should contain correct distinct_id",
        "user_456",
        requestContext.getString("distinct_id"));

    // When FlagsConfig context is null, the context object should only contain distinct_id
    assertEquals(
        "Context should only contain distinct_id when FlagsConfig context is null",
        1,
        requestContext.length());
  }

  @Test
  public void testRequestBodyConstruction_withEmptyDistinctId()
      throws InterruptedException, JSONException {
    // Setup with flags enabled
    setupFlagsConfig(true, new JSONObject());

    // Set empty distinct ID
    mMockDelegate.distinctIdToReturn = "";

    // Create response
    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("flag", new MixpanelFlagVariant("v", "val"));
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));

    // Trigger request
    mFeatureFlagManager.loadFlags();

    // Capture request
    CapturedRequest capturedRequest = mMockRemoteService.takeRequest(1000, TimeUnit.MILLISECONDS);
    assertNotNull("Request should have been made", capturedRequest);

    // Parse request body
    JSONObject requestBody = capturedRequest.getRequestBodyAsJson();

    // Verify context exists
    assertTrue("Request should contain context", requestBody.has("context"));
    JSONObject requestContext = requestBody.getJSONObject("context");

    // Verify distinct_id is included in context even when empty
    assertTrue("Context should contain distinct_id field", requestContext.has("distinct_id"));
    assertEquals(
        "Context should contain empty distinct_id", "", requestContext.getString("distinct_id"));
  }

  @Test
  public void testFlagsConfigContextUsage_initialContext()
      throws InterruptedException, JSONException {
    // Test that initial context from FlagsConfig is properly used
    JSONObject initialContext = new JSONObject();
    initialContext.put("app_version", "1.0.0");
    initialContext.put("platform", "Android");
    initialContext.put("custom_prop", "initial_value");

    setupFlagsConfig(true, initialContext);

    // Create response
    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("test_flag", new MixpanelFlagVariant("v1", "value1"));
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));

    // Trigger request
    mFeatureFlagManager.loadFlags();

    // Capture and verify request
    CapturedRequest capturedRequest = mMockRemoteService.takeRequest(1000, TimeUnit.MILLISECONDS);
    assertNotNull("Request should have been made", capturedRequest);

    JSONObject requestBody = capturedRequest.getRequestBodyAsJson();
    JSONObject requestContext = requestBody.getJSONObject("context");

    // Verify all initial context properties are included
    assertEquals(
        "app_version should be preserved", "1.0.0", requestContext.getString("app_version"));
    assertEquals("platform should be preserved", "Android", requestContext.getString("platform"));
    assertEquals(
        "custom_prop should be preserved",
        "initial_value",
        requestContext.getString("custom_prop"));

    // Verify distinct_id is added to context
    assertTrue("distinct_id should be added to context", requestContext.has("distinct_id"));
  }

  @Test
  public void testFlagsConfigContextUsage_contextMerging()
      throws InterruptedException, JSONException {
    // Test that distinct_id doesn't override existing context properties
    JSONObject initialContext = new JSONObject();
    initialContext.put("distinct_id", "should_be_overridden"); // This should be overridden
    initialContext.put("user_type", "premium");
    initialContext.put("$os", "Android");

    setupFlagsConfig(true, initialContext);

    // Set a different distinct_id via delegate
    mMockDelegate.distinctIdToReturn = "actual_distinct_id";

    // Create response
    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("flag", new MixpanelFlagVariant("v", "val"));
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));

    // Trigger request
    mFeatureFlagManager.loadFlags();

    // Capture and verify request
    CapturedRequest capturedRequest = mMockRemoteService.takeRequest(1000, TimeUnit.MILLISECONDS);
    JSONObject requestBody = capturedRequest.getRequestBodyAsJson();
    JSONObject requestContext = requestBody.getJSONObject("context");

    // Verify distinct_id from delegate overrides the one in initial context
    assertEquals(
        "distinct_id should be from delegate, not initial context",
        "actual_distinct_id",
        requestContext.getString("distinct_id"));

    // Verify other properties are preserved
    assertEquals("user_type should be preserved", "premium", requestContext.getString("user_type"));
    assertEquals("$os should be preserved", "Android", requestContext.getString("$os"));
  }

  @Test
  public void testFlagsConfigContextUsage_emptyContext()
      throws InterruptedException, JSONException {
    // Test behavior with empty context object
    JSONObject emptyContext = new JSONObject();
    setupFlagsConfig(true, emptyContext);

    mMockDelegate.distinctIdToReturn = "test_user";

    // Create response
    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("flag", new MixpanelFlagVariant("v", "val"));
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));

    // Trigger request
    mFeatureFlagManager.loadFlags();

    // Capture and verify request
    CapturedRequest capturedRequest = mMockRemoteService.takeRequest(1000, TimeUnit.MILLISECONDS);
    JSONObject requestBody = capturedRequest.getRequestBodyAsJson();
    JSONObject requestContext = requestBody.getJSONObject("context");

    // Context should only contain distinct_id when initial context is empty
    assertEquals("Context should only contain distinct_id", 1, requestContext.length());
    assertEquals(
        "distinct_id should be present", "test_user", requestContext.getString("distinct_id"));
  }

  @Test
  public void testFlagsConfigContextUsage_complexNestedContext()
      throws InterruptedException, JSONException {
    // Test that complex nested objects in context are preserved
    JSONObject nestedObj = new JSONObject();
    nestedObj.put("city", "San Francisco");
    nestedObj.put("country", "USA");

    JSONObject initialContext = new JSONObject();
    initialContext.put("location", nestedObj);
    initialContext.put("features_enabled", new JSONArray().put("feature1").put("feature2"));
    initialContext.put("is_beta", true);
    initialContext.put("score", 95.5);

    setupFlagsConfig(true, initialContext);

    // Create response
    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("flag", new MixpanelFlagVariant("v", "val"));
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));

    // Trigger request
    mFeatureFlagManager.loadFlags();

    // Capture and verify request
    CapturedRequest capturedRequest = mMockRemoteService.takeRequest(1000, TimeUnit.MILLISECONDS);
    JSONObject requestBody = capturedRequest.getRequestBodyAsJson();
    JSONObject requestContext = requestBody.getJSONObject("context");

    // Verify complex nested structures are preserved
    JSONObject locationInRequest = requestContext.getJSONObject("location");
    assertEquals("city should be preserved", "San Francisco", locationInRequest.getString("city"));
    assertEquals("country should be preserved", "USA", locationInRequest.getString("country"));

    JSONArray featuresInRequest = requestContext.getJSONArray("features_enabled");
    assertEquals("features array length should be preserved", 2, featuresInRequest.length());
    assertEquals("feature1 should be preserved", "feature1", featuresInRequest.getString(0));
    assertEquals("feature2 should be preserved", "feature2", featuresInRequest.getString(1));

    assertTrue("is_beta should be preserved", requestContext.getBoolean("is_beta"));
    assertEquals("score should be preserved", 95.5, requestContext.getDouble("score"), 0.001);

    // And distinct_id should still be added
    assertTrue("distinct_id should be added", requestContext.has("distinct_id"));
  }

  @Test
  public void testFlagsConfigContextUsage_specialCharactersInContext()
      throws InterruptedException, JSONException {
    // Test that special characters and unicode in context are handled properly
    JSONObject initialContext = new JSONObject();
    initialContext.put("emoji", "🚀🎉");
    initialContext.put("special_chars", "!@#$%^&*()_+-=[]{}|;':\",./<>?");
    initialContext.put("unicode", "你好世界");
    initialContext.put("newline", "line1\nline2");

    setupFlagsConfig(true, initialContext);

    // Create response
    Map<String, MixpanelFlagVariant> serverFlags = new HashMap<>();
    serverFlags.put("flag", new MixpanelFlagVariant("v", "val"));
    mMockRemoteService.addResponse(
        createFlagsResponseJson(serverFlags).getBytes(StandardCharsets.UTF_8));

    // Trigger request
    mFeatureFlagManager.loadFlags();

    // Capture and verify request
    CapturedRequest capturedRequest = mMockRemoteService.takeRequest(1000, TimeUnit.MILLISECONDS);
    JSONObject requestBody = capturedRequest.getRequestBodyAsJson();
    JSONObject requestContext = requestBody.getJSONObject("context");

    // Verify special characters are preserved correctly
    assertEquals("emoji should be preserved", "🚀🎉", requestContext.getString("emoji"));
    assertEquals(
        "special_chars should be preserved",
        "!@#$%^&*()_+-=[]{}|;':\",./<>?",
        requestContext.getString("special_chars"));
    assertEquals("unicode should be preserved", "你好世界", requestContext.getString("unicode"));
    assertEquals(
        "newline should be preserved", "line1\nline2", requestContext.getString("newline"));
  }
}
