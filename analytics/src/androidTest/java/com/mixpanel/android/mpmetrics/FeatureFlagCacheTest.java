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
  private static final String TOKEN_NETWORK_ONLY_STALE = "TestPersistence_NetworkOnlyStale";
  private static final String TOKEN_TTL_LOOKUP = "TestPersistence_TtlLookup";
  private static final String TOKEN_NETWORK_FIRST_FAIL = "TestPersistence_NetworkFirstFail";
  private static final String TOKEN_OPT_OUT = "TestPersistence_OptOut";
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
    storedPrefs(TOKEN_NETWORK_ONLY_STALE).edit().clear().commit();
    storedPrefs(TOKEN_TTL_LOOKUP).edit().clear().commit();
    storedPrefs(TOKEN_NETWORK_FIRST_FAIL).edit().clear().commit();
    storedPrefs(TOKEN_OPT_OUT).edit().clear().commit();
  }

  // -----------------------------------------------------------------------
  // CacheFirst serves cached variants without a network call
  // -----------------------------------------------------------------------

  @Test
  public void testCacheFirst_ServesCachedVariantWithoutNetwork() throws Exception {
    // Arrange: seed the stored-prefs file with a blob keyed to the SDK's distinct_id.
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
  public void testNetworkOnly_OnInitClearsStaleCacheBlob() throws Exception {
    // Arrange: a stale cache blob exists on disk (e.g., from a prior release that used
    // CacheFirst). The customer has now reconfigured to NetworkOnly.
    seedCachedResponse(TOKEN_NETWORK_ONLY_STALE, /*distinctId*/ TOKEN_NETWORK_ONLY_STALE);
    assertNotNull(
        "Sanity: blob should exist before construction",
        storedPrefs(TOKEN_NETWORK_ONLY_STALE).getString(BLOB_KEY, null));

    FeatureFlagOptions flagOptions = new FeatureFlagOptions.Builder()
        .enabled(true)
        .prefetchFlags(false)
        .variantLookupPolicy(VariantLookupPolicy.networkOnly())
        .build();
    MixpanelOptions opts = new MixpanelOptions.Builder()
        .featureFlagOptions(flagOptions)
        .build();

    // Act: construct MixpanelAPI with NetworkOnly. The init-time wipe is posted to the
    // FF handler thread.
    final AtomicInteger flagsHttpCallCount = new AtomicInteger(0);
    MixpanelAPI mixpanel = newMixpanel(
        TOKEN_NETWORK_ONLY_STALE, opts, new CountingHangingHttpService(flagsHttpCallCount));

    // Wait for the wipe runnable to drain.
    long deadline = System.currentTimeMillis() + 5_000L;
    while (System.currentTimeMillis() < deadline) {
      if (storedPrefs(TOKEN_NETWORK_ONLY_STALE).getString(BLOB_KEY, null) == null) {
        break;
      }
      Thread.sleep(50);
    }

    // Assert: the stale blob is gone.
    assertNull(
        "NetworkOnly construction should wipe a stale cache blob from disk",
        storedPrefs(TOKEN_NETWORK_ONLY_STALE).getString(BLOB_KEY, null));
  }

  // -----------------------------------------------------------------------
  // TTL is rechecked at lookup time — expired cached variants are not served,
  // and the on-disk blob is left intact.
  // -----------------------------------------------------------------------

  @Test
  public void testGetVariant_DoesNotServeExpiredCacheButLeavesBlobOnDisk() throws Exception {
    // Arrange: seed cache with cachedAt=now. Configure CacheFirst with a short TTL so the
    // cached values are valid at init-time load but expire shortly thereafter.
    seedCachedResponse(TOKEN_TTL_LOOKUP, /*distinctId*/ TOKEN_TTL_LOOKUP);

    final long shortTtlMs = 100L;
    FeatureFlagOptions flagOptions = new FeatureFlagOptions.Builder()
        .enabled(true)
        .prefetchFlags(false)
        .variantLookupPolicy(VariantLookupPolicy.cacheFirst(shortTtlMs))
        .build();
    MixpanelOptions opts = new MixpanelOptions.Builder()
        .featureFlagOptions(flagOptions)
        .build();

    final AtomicInteger flagsHttpCallCount = new AtomicInteger(0);
    MixpanelAPI mixpanel = newMixpanel(
        TOKEN_TTL_LOOKUP, opts, new CountingHangingHttpService(flagsHttpCallCount));

    // Sanity #1: the cache loads at init (TTL not yet expired) and getVariantSync serves it.
    // Wait briefly for _loadCachedVariants to drain on the FF handler thread.
    long deadline = System.currentTimeMillis() + 2_000L;
    while (System.currentTimeMillis() < deadline && !mixpanel.getFlags().areFlagsReady()) {
      Thread.sleep(20);
    }
    assertTrue(
        "Sanity: cache should have loaded into mFlags before TTL expired",
        mixpanel.getFlags().areFlagsReady());

    // Act: sleep past TTL, then look up. The variant in mFlags is now stale per its
    // cachedAtMillis stamp.
    Thread.sleep(shortTtlMs * 3);

    MixpanelFlagVariant fb = new MixpanelFlagVariant("dev-fallback-key", "dev-fallback-value");
    MixpanelFlagVariant served = mixpanel.getFlags().getVariantSync(FLAG_NAME, fb);

    // Assert: served the developer fallback (not the now-expired cached variant) AND the
    // cache blob is still on disk (TTL expiry doesn't trigger a clear).
    assertEquals(
        "Expired cached variant should not be served; expected the developer fallback",
        "dev-fallback-key", served.key);
    assertNotNull(
        "TTL expiry must not clear the on-disk cache blob",
        storedPrefs(TOKEN_TTL_LOOKUP).getString(BLOB_KEY, null));
  }

  // -----------------------------------------------------------------------
  // optOutTracking() clears the cached variants
  // -----------------------------------------------------------------------

  @Test
  public void testOptOutTracking_ClearsCachedVariants() throws Exception {
    // Arrange: seed cache + create CacheFirst-configured SDK.
    seedCachedResponse(TOKEN_OPT_OUT, /*distinctId*/ TOKEN_OPT_OUT);

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
        TOKEN_OPT_OUT, opts, new CountingHangingHttpService(flagsHttpCallCount));

    assertNotNull(
        "Cache blob should exist after seeding",
        storedPrefs(TOKEN_OPT_OUT).getString(BLOB_KEY, null));

    // Act
    mixpanel.optOutTracking();

    long deadline = System.currentTimeMillis() + 5_000L;
    while (System.currentTimeMillis() < deadline) {
      if (storedPrefs(TOKEN_OPT_OUT).getString(BLOB_KEY, null) == null) {
        break;
      }
      Thread.sleep(50);
    }

    // Assert: opt-out wiped the cached variants — the prior user can't be served their
    // variants after opting out.
    assertNull(
        "optOutTracking() should remove the cached flags blob",
        storedPrefs(TOKEN_OPT_OUT).getString(BLOB_KEY, null));
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
   * Writes a cached /flags/ response blob keyed by the given distinct_id, matching the SDK's
   * runtime distinct_id at construction time.
   */
  private void seedCachedResponse(String token, String distinctId) throws Exception {
    org.json.JSONObject variant = new org.json.JSONObject();
    variant.put("variant_key", CACHED_VARIANT_KEY);
    variant.put("variant_value", CACHED_VARIANT_VALUE);
    org.json.JSONObject flags = new org.json.JSONObject();
    flags.put(FLAG_NAME, variant);
    org.json.JSONObject response = new org.json.JSONObject();
    response.put("flags", flags);

    org.json.JSONObject blob = new org.json.JSONObject();
    blob.put("cachedAt", System.currentTimeMillis());
    blob.put("distinctId", distinctId);
    blob.put("response", response);

    storedPrefs(token).edit().putString(BLOB_KEY, blob.toString()).commit();
  }

  /**
   * Builds a MixpanelAPI with the supplied HTTP service, a no-op app_open, and — importantly —
   * the default getPersistentIdentity (no wipe). The token is also seeded as the distinct_id
   * and anonymous_id so the SDK's runtime distinct_id deterministically matches anything our
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
