package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import com.mixpanel.android.util.HttpService;
import com.mixpanel.android.util.ProxyServerInteractor;
import com.mixpanel.android.util.RemoteService;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * End-to-end coverage for the VariantLookupPolicy-driven variant cache wiring.
 *
 * <p>Each test pre-seeds the per-instance stored-prefs file (the same one
 * PersistentIdentity uses) with a known cached /flags/ response, then constructs
 * a MixpanelAPI with a counting HTTP service and asserts the cached value is
 * served (or cleared) per the configured VariantLookupPolicy.
 *
 * <p>Note: this test extends {@link MixpanelAPI} directly rather than via
 * {@link TestUtils.CleanMixpanelAPI} because the latter wipes the stored prefs
 * file at construction, which would clobber the seed before the cache load runs.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class FeatureFlagCacheTest {

  private static final String TOKEN_CACHE_FIRST = "TestPersistence_CacheFirst";
  private static final String TOKEN_RESET = "TestPersistence_Reset";
  private static final String TOKEN_WRITE_ONLY = "TestPersistence_WriteOnly";
  private static final String TOKEN_NETWORK_FIRST_FAIL = "TestPersistence_NetworkFirstFail";
  private static final String FLAG_NAME = "cached-flag";
  private static final String CACHED_VARIANT_KEY = "cached-variant";
  private static final String CACHED_VARIANT_VALUE = "cached-value";
  private static final String BLOB_KEY = "mixpanel.flags.cache";

  private Context mContext;
  private Future<SharedPreferences> mMockReferrerPrefs;

  @Before
  public void setUp() {
    mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    mMockReferrerPrefs = new TestUtils.EmptyPreferences(
        InstrumentationRegistry.getInstrumentation().getContext());
    // Wipe the stored prefs files this test class uses so prior runs don't leak in.
    storedPrefs(TOKEN_CACHE_FIRST).edit().clear().commit();
    storedPrefs(TOKEN_RESET).edit().clear().commit();
    storedPrefs(TOKEN_WRITE_ONLY).edit().clear().commit();
    storedPrefs(TOKEN_NETWORK_FIRST_FAIL).edit().clear().commit();
  }

  // -----------------------------------------------------------------------
  // CacheFirst serves cached variants without a network call
  // -----------------------------------------------------------------------

  @Test
  public void testCacheFirst_ServesCachedVariantWithoutNetwork() throws Exception {
    // Arrange: seed the stored-prefs file with a fingerprint matching the SDK's
    // distinct_id and (empty) context.
    seedCachedResponse(TOKEN_CACHE_FIRST, /*distinctId*/ TOKEN_CACHE_FIRST);

    FeatureFlagOptions flagOptions = new FeatureFlagOptions.Builder()
        .enabled(true)
        .prefetchFlags(false)  // Don't race the cache load with a network fetch.
        .variantLookupPolicy(VariantLookupPolicy.cacheFirst(TimeUnit.HOURS.toMillis(24)))
        .build();
    MixpanelOptions opts = new MixpanelOptions.Builder()
        .featureFlagOptions(flagOptions)
        .build();

    final AtomicInteger flagsHttpCallCount = new AtomicInteger(0);
    MixpanelAPI mixpanel = newMixpanel(
        TOKEN_CACHE_FIRST, opts, new CountingHangingHttpService(flagsHttpCallCount));

    // Act: ask asynchronously so the lookup queues behind the cache-load Runnable
    // on the FF handler — both run on the same thread, FIFO.
    final CountDownLatch done = new CountDownLatch(1);
    final AtomicReference<MixpanelFlagVariant> received = new AtomicReference<>();
    mixpanel.getFlags().getVariant(
        FLAG_NAME,
        new MixpanelFlagVariant("fallback-key", "fallback-value"),
        result -> {
          received.set(result);
          done.countDown();
        });

    // Assert
    assertTrue("getVariant callback should fire", done.await(5, TimeUnit.SECONDS));
    MixpanelFlagVariant variant = received.get();
    assertNotNull(variant);
    assertEquals("Should serve the cached variant key", CACHED_VARIANT_KEY, variant.key);
    assertEquals(CACHED_VARIANT_VALUE, variant.value);
    assertTrue(
        "Source should be Cache for a cached variant",
        variant.source instanceof MixpanelFlagVariant.Source.Cache);
    assertTrue(
        "cachedAtMillis should be set",
        ((MixpanelFlagVariant.Source.Cache) variant.source).cachedAtMillis > 0);
    assertEquals(
        "No network call should fire when CacheFirst can satisfy the lookup",
        0, flagsHttpCallCount.get());
  }

  // -----------------------------------------------------------------------
  // reset() clears the cached variants
  // -----------------------------------------------------------------------

  @Test
  public void testReset_ClearsCachedVariants() throws Exception {
    // Arrange: seed cache + create CacheFirst-configured SDK.
    seedCachedResponse(TOKEN_RESET, /*distinctId*/ TOKEN_RESET);

    FeatureFlagOptions flagOptions = new FeatureFlagOptions.Builder()
        .enabled(true)
        .prefetchFlags(false)
        .variantLookupPolicy(VariantLookupPolicy.cacheFirst(TimeUnit.HOURS.toMillis(24)))
        .build();
    MixpanelOptions opts = new MixpanelOptions.Builder()
        .featureFlagOptions(flagOptions)
        .build();

    final AtomicInteger flagsHttpCallCount = new AtomicInteger(0);
    MixpanelAPI mixpanel = newMixpanel(
        TOKEN_RESET, opts, new CountingHangingHttpService(flagsHttpCallCount));

    // Sanity: blob exists before reset.
    assertNotNull(
        "Cache blob should exist after seeding",
        storedPrefs(TOKEN_RESET).getString(BLOB_KEY, null));

    // Act
    mixpanel.reset();

    // PersistentIdentity.clearPreferences() is the producing call; it runs synchronously
    // on the calling thread, so the blob should be gone immediately. Poll briefly anyway
    // in case any wrapper hops to a handler.
    long deadline = System.currentTimeMillis() + 5_000L;
    while (System.currentTimeMillis() < deadline) {
      if (storedPrefs(TOKEN_RESET).getString(BLOB_KEY, null) == null) {
        break;
      }
      Thread.sleep(50);
    }

    // Assert
    assertNull(
        "reset() should remove the cached flags blob (via clearPreferences())",
        storedPrefs(TOKEN_RESET).getString(BLOB_KEY, null));
  }

  // -----------------------------------------------------------------------
  // cacheVariants=true + networkOnly: writes happen, reads do not
  // -----------------------------------------------------------------------

  @Test
  public void testCacheVariants_NetworkOnly_WritesResponseButDoesNotRead() throws Exception {
    // Arrange: empty cache (setUp() wiped it). Configure cacheVariants=true alongside
    // networkOnly — the migration-warm-up case. A successful fetch should write the
    // response to disk even though the lookup policy never reads it.
    final org.json.JSONObject responseFromServer = new org.json.JSONObject();
    org.json.JSONObject variant = new org.json.JSONObject();
    variant.put("variant_key", "fresh-variant");
    variant.put("variant_value", "fresh-value");
    org.json.JSONObject flags = new org.json.JSONObject();
    flags.put(FLAG_NAME, variant);
    responseFromServer.put("flags", flags);

    FeatureFlagOptions flagOptions = new FeatureFlagOptions.Builder()
        .enabled(true)
        .prefetchFlags(false)
        .variantLookupPolicy(VariantLookupPolicy.networkOnly())
        .cacheVariants(true)
        .build();
    MixpanelOptions opts = new MixpanelOptions.Builder()
        .featureFlagOptions(flagOptions)
        .build();

    final AtomicInteger flagsHttpCallCount = new AtomicInteger(0);
    final byte[] responseBytes =
        responseFromServer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    MixpanelAPI mixpanel = newMixpanel(
        TOKEN_WRITE_ONLY,
        opts,
        new FixedResponseHttpService(flagsHttpCallCount, responseBytes));

    // Sanity: nothing in cache before the fetch.
    assertNull(
        "Cache blob should not exist before any fetch",
        storedPrefs(TOKEN_WRITE_ONLY).getString(BLOB_KEY, null));

    // Act: trigger a flag fetch and wait for completion.
    final CountDownLatch fetchDone = new CountDownLatch(1);
    mixpanel.getFlags().loadFlags(success -> fetchDone.countDown());
    assertTrue("loadFlags callback should fire", fetchDone.await(5, TimeUnit.SECONDS));

    // The write happens on the FF handler thread inside _completeFetch — give it a moment.
    long deadline = System.currentTimeMillis() + 5_000L;
    String blob = null;
    while (System.currentTimeMillis() < deadline) {
      blob = storedPrefs(TOKEN_WRITE_ONLY).getString(BLOB_KEY, null);
      if (blob != null) break;
      Thread.sleep(50);
    }

    // Assert: blob exists, contains our response.
    assertNotNull(
        "cacheVariants=true should write the response to disk even with networkOnly",
        blob);
    org.json.JSONObject parsed = new org.json.JSONObject(blob);
    assertEquals(
        "Cached response body should match what the server returned",
        responseFromServer.toString(),
        parsed.getJSONObject("response").toString());
    assertEquals(1, flagsHttpCallCount.get());
  }

  // -----------------------------------------------------------------------
  // NetworkFirst falls back to cached values when the fetch fails
  // -----------------------------------------------------------------------

  @Test
  public void testNetworkFirst_ServesCachedVariantWhenFetchFails() throws Exception {
    // Arrange: cache exists, NetworkFirst configured, HTTP fails on /flags/.
    seedCachedResponse(TOKEN_NETWORK_FIRST_FAIL, /*distinctId*/ TOKEN_NETWORK_FIRST_FAIL);

    FeatureFlagOptions flagOptions = new FeatureFlagOptions.Builder()
        .enabled(true)
        .prefetchFlags(false)
        .variantLookupPolicy(VariantLookupPolicy.networkFirst(TimeUnit.HOURS.toMillis(24)))
        .build();
    MixpanelOptions opts = new MixpanelOptions.Builder()
        .featureFlagOptions(flagOptions)
        .build();

    final AtomicInteger flagsHttpCallCount = new AtomicInteger(0);
    MixpanelAPI mixpanel = newMixpanel(
        TOKEN_NETWORK_FIRST_FAIL, opts, new FailingFlagsHttpService(flagsHttpCallCount));

    // Act: async getVariant should await the network call (NetworkFirst), see it fail, then
    // serve from the cached values that the init-time cache load left in mFlags.
    final CountDownLatch done = new CountDownLatch(1);
    final AtomicReference<MixpanelFlagVariant> received = new AtomicReference<>();
    mixpanel.getFlags().getVariant(
        FLAG_NAME,
        new MixpanelFlagVariant("dev-fallback-key", "dev-fallback-value"),
        result -> {
          received.set(result);
          done.countDown();
        });

    // Assert
    assertTrue("getVariant callback should fire", done.await(5, TimeUnit.SECONDS));
    MixpanelFlagVariant variant = received.get();
    assertNotNull(variant);
    assertEquals(
        "Expected the cached variant key, not the developer fallback",
        CACHED_VARIANT_KEY, variant.key);
    assertEquals(CACHED_VARIANT_VALUE, variant.value);
    assertTrue(
        "Source should be Cache (cached value served because network failed)",
        variant.source instanceof MixpanelFlagVariant.Source.Cache);
    assertEquals(
        "Network call should have been attempted exactly once",
        1, flagsHttpCallCount.get());
  }

  // -----------------------------------------------------------------------
  // helpers
  // -----------------------------------------------------------------------

  private SharedPreferences storedPrefs(String token) {
    // Mirrors MixpanelAPI.storedPrefsName(token, instanceName=null) — the file shared
    // with PersistentIdentity that now also holds the flag cache blob.
    return mContext.getSharedPreferences(
        "com.mixpanel.android.mpmetrics.MixpanelAPI_" + token, Context.MODE_PRIVATE);
  }

  /**
   * Writes a cached /flags/ response blob with a fingerprint that matches the SDK's
   * runtime fingerprint at construction time (distinct_id + empty context).
   */
  private void seedCachedResponse(String token, String distinctId) throws Exception {
    final String contextString = "{}"; // FeatureFlagOptions defaults to empty JSONObject
    final String fingerprint = sha256(distinctId + "|" + contextString);

    org.json.JSONObject variant = new org.json.JSONObject();
    variant.put("variant_key", CACHED_VARIANT_KEY);
    variant.put("variant_value", CACHED_VARIANT_VALUE);
    org.json.JSONObject flags = new org.json.JSONObject();
    flags.put(FLAG_NAME, variant);
    org.json.JSONObject response = new org.json.JSONObject();
    response.put("flags", flags);

    org.json.JSONObject blob = new org.json.JSONObject();
    blob.put("cachedAt", System.currentTimeMillis());
    blob.put("fingerprint", fingerprint);
    blob.put("response", response);

    storedPrefs(token).edit().putString(BLOB_KEY, blob.toString()).commit();
  }

  /**
   * Builds a MixpanelAPI with the supplied HTTP service, a no-op app_open, and — importantly —
   * the default getPersistentIdentity (no wipe). The token is also seeded as the distinct_id
   * and anonymous_id so the SDK's runtime fingerprint deterministically matches anything our
   * tests seed via {@link #seedCachedResponse}.
   */
  private MixpanelAPI newMixpanel(String token, MixpanelOptions opts, RemoteService httpService) {
    storedPrefs(token).edit()
        .putString("events_distinct_id", token)
        .putString("anonymous_id", token)
        .commit();
    return new MixpanelAPI(
        mContext, mMockReferrerPrefs, token,
        MPConfig.getInstance(mContext, null), opts, false) {
      @Override
      protected RemoteService getHttpService() {
        return httpService;
      }

      @Override
      /* package */ boolean sendAppOpen() {
        return false;
      }
    };
  }

  private static String sha256(String input) throws Exception {
    java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
    byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder(digest.length * 2);
    for (byte b : digest) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
  }

  /**
   * HTTP service that counts /flags/ calls. Returns an empty success response so that
   * if the test logic is wrong and a network call does fire, we get a fast assertion
   * failure rather than a hang.
   */
  private static final class CountingHangingHttpService extends HttpService {
    private final AtomicInteger mCalls;

    CountingHangingHttpService(AtomicInteger calls) {
      this.mCalls = calls;
    }

    @Override
    public RemoteService.RequestResult performRequest(
        String endpointUrl,
        ProxyServerInteractor interactor,
        Map<String, Object> params,
        Map<String, String> headers,
        byte[] requestBodyBytes,
        SSLSocketFactory socketFactory)
        throws ServiceUnavailableException, IOException {
      return performRequest(
          RemoteService.HttpMethod.POST, endpointUrl, interactor, params,
          headers, requestBodyBytes, socketFactory);
    }

    @Override
    public RemoteService.RequestResult performRequest(
        RemoteService.HttpMethod method,
        String endpointUrl,
        ProxyServerInteractor interactor,
        Map<String, Object> params,
        Map<String, String> headers,
        byte[] requestBodyBytes,
        SSLSocketFactory socketFactory)
        throws ServiceUnavailableException, IOException {
      if (endpointUrl != null && endpointUrl.contains("/flags/")) {
        mCalls.incrementAndGet();
        return RemoteService.RequestResult.success(
            "{\"flags\":{}}".getBytes(), endpointUrl);
      }
      return RemoteService.RequestResult.success(new byte[]{'1'}, endpointUrl);
    }
  }

  /**
   * HTTP service that counts /flags/ calls and returns a fixed response body. Lets tests
   * verify write-side behavior with realistic-looking server output.
   */
  private static final class FixedResponseHttpService extends HttpService {
    private final AtomicInteger mCalls;
    private final byte[] mResponseBytes;

    FixedResponseHttpService(AtomicInteger calls, byte[] responseBytes) {
      this.mCalls = calls;
      this.mResponseBytes = responseBytes;
    }

    @Override
    public RemoteService.RequestResult performRequest(
        String endpointUrl,
        ProxyServerInteractor interactor,
        Map<String, Object> params,
        Map<String, String> headers,
        byte[] requestBodyBytes,
        SSLSocketFactory socketFactory)
        throws ServiceUnavailableException, IOException {
      return performRequest(
          RemoteService.HttpMethod.POST, endpointUrl, interactor, params,
          headers, requestBodyBytes, socketFactory);
    }

    @Override
    public RemoteService.RequestResult performRequest(
        RemoteService.HttpMethod method,
        String endpointUrl,
        ProxyServerInteractor interactor,
        Map<String, Object> params,
        Map<String, String> headers,
        byte[] requestBodyBytes,
        SSLSocketFactory socketFactory)
        throws ServiceUnavailableException, IOException {
      if (endpointUrl != null && endpointUrl.contains("/flags/")) {
        mCalls.incrementAndGet();
        return RemoteService.RequestResult.success(mResponseBytes, endpointUrl);
      }
      return RemoteService.RequestResult.success(new byte[]{'1'}, endpointUrl);
    }
  }

  /**
   * HTTP service that counts /flags/ calls and throws an IOException for them, simulating a
   * network failure. Other endpoints succeed silently.
   */
  private static final class FailingFlagsHttpService extends HttpService {
    private final AtomicInteger mCalls;

    FailingFlagsHttpService(AtomicInteger calls) {
      this.mCalls = calls;
    }

    @Override
    public RemoteService.RequestResult performRequest(
        String endpointUrl,
        ProxyServerInteractor interactor,
        Map<String, Object> params,
        Map<String, String> headers,
        byte[] requestBodyBytes,
        SSLSocketFactory socketFactory)
        throws ServiceUnavailableException, IOException {
      return performRequest(
          RemoteService.HttpMethod.POST, endpointUrl, interactor, params,
          headers, requestBodyBytes, socketFactory);
    }

    @Override
    public RemoteService.RequestResult performRequest(
        RemoteService.HttpMethod method,
        String endpointUrl,
        ProxyServerInteractor interactor,
        Map<String, Object> params,
        Map<String, String> headers,
        byte[] requestBodyBytes,
        SSLSocketFactory socketFactory)
        throws ServiceUnavailableException, IOException {
      if (endpointUrl != null && endpointUrl.contains("/flags/")) {
        mCalls.incrementAndGet();
        throw new IOException("Simulated network failure for /flags/ in test");
      }
      return RemoteService.RequestResult.success(new byte[]{'1'}, endpointUrl);
    }
  }
}
