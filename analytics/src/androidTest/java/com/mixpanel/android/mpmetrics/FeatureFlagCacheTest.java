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
 * End-to-end coverage for the VariantLookupPolicy-driven variant persistence wiring.
 *
 * <p>Each test pre-seeds the per-instance stored-prefs file (the same one
 * PersistentIdentity uses) with a known persisted /flags/ response, then constructs
 * a MixpanelAPI with a counting HTTP service and asserts the persisted value is
 * served (or cleared) per the configured VariantLookupPolicy.
 *
 * <p>Note: this test extends {@link MixpanelAPI} directly rather than via
 * {@link TestUtils.CleanMixpanelAPI} because the latter wipes the stored prefs
 * file at construction, which would clobber the seed before the persistence load runs.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class FeatureFlagCacheTest {

  private static final String TOKEN_PERSISTENCE_UNTIL_NETWORK_SUCCESS = "TestPersistence_PersistenceUntilNetworkSuccess";
  private static final String TOKEN_RESET = "TestPersistence_Reset";
  private static final String TOKEN_NETWORK_ONLY_STALE = "TestPersistence_NetworkOnlyStale";
  private static final String TOKEN_TTL_LOOKUP = "TestPersistence_TtlLookup";
  private static final String TOKEN_NETWORK_FIRST_FAIL = "TestPersistence_NetworkFirstFail";
  private static final String TOKEN_OPT_OUT = "TestPersistence_OptOut";
  private static final String TOKEN_TTL_AWAIT_FETCH = "TestPersistence_TtlAwaitFetch";
  private static final String TOKEN_EMPTY_BLOB_TTL = "TestPersistence_EmptyBlobTtl";
  private static final String FLAG_NAME = "persisted-flag";
  private static final String PERSISTED_VARIANT_KEY = "persisted-variant";
  private static final String PERSISTED_VARIANT_VALUE = "persisted-value";
  private static final String BLOB_KEY = "mixpanel.flags.persistence";

  private Context mContext;
  private Future<SharedPreferences> mMockReferrerPrefs;

  @Before
  public void setUp() {
    mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    mMockReferrerPrefs = new TestUtils.EmptyPreferences(
        InstrumentationRegistry.getInstrumentation().getContext());
    // Wipe the stored prefs files this test class uses so prior runs don't leak in.
    storedPrefs(TOKEN_PERSISTENCE_UNTIL_NETWORK_SUCCESS).edit().clear().commit();
    storedPrefs(TOKEN_RESET).edit().clear().commit();
    storedPrefs(TOKEN_NETWORK_ONLY_STALE).edit().clear().commit();
    storedPrefs(TOKEN_TTL_LOOKUP).edit().clear().commit();
    storedPrefs(TOKEN_NETWORK_FIRST_FAIL).edit().clear().commit();
    storedPrefs(TOKEN_OPT_OUT).edit().clear().commit();
    storedPrefs(TOKEN_TTL_AWAIT_FETCH).edit().clear().commit();
    storedPrefs(TOKEN_EMPTY_BLOB_TTL).edit().clear().commit();
  }

  // -----------------------------------------------------------------------
  // PersistenceUntilNetworkSuccess serves persisted variants without a network call
  // -----------------------------------------------------------------------

  @Test
  public void testPersistenceUntilNetworkSuccess_ServesPersistedVariantWithoutNetwork() throws Exception {
    // Arrange: seed the stored-prefs file with a blob keyed to the SDK's distinct_id.
    seedPersistedResponse(TOKEN_PERSISTENCE_UNTIL_NETWORK_SUCCESS, /*distinctId*/ TOKEN_PERSISTENCE_UNTIL_NETWORK_SUCCESS);

    FeatureFlagOptions flagOptions = new FeatureFlagOptions.Builder()
        .enabled(true)
        .prefetchFlags(false)  // Don't race the persistence load with a network fetch.
        .variantLookupPolicy(VariantLookupPolicy.persistenceUntilNetworkSuccess(TimeUnit.HOURS.toMillis(24)))
        .build();
    MixpanelOptions opts = new MixpanelOptions.Builder()
        .featureFlagOptions(flagOptions)
        .build();

    final AtomicInteger flagsHttpCallCount = new AtomicInteger(0);
    MixpanelAPI mixpanel = newMixpanel(
        TOKEN_PERSISTENCE_UNTIL_NETWORK_SUCCESS, opts, new CountingHangingHttpService(flagsHttpCallCount));

    // Act: ask asynchronously so the lookup queues behind the persistence-load Runnable
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
    assertEquals("Should serve the persisted variant key", PERSISTED_VARIANT_KEY, variant.key);
    assertEquals(PERSISTED_VARIANT_VALUE, variant.value);
    assertTrue(
        "Source should be Persistence for a persisted variant",
        variant.source instanceof MixpanelFlagVariant.Source.Persistence);
    assertTrue(
        "persistedAtMillis should be set",
        ((MixpanelFlagVariant.Source.Persistence) variant.source).persistedAtMillis > 0);
    assertEquals(
        "No network call should fire when PersistenceUntilNetworkSuccess can satisfy the lookup",
        0, flagsHttpCallCount.get());
  }

  // -----------------------------------------------------------------------
  // reset() clears the persisted variants
  // -----------------------------------------------------------------------

  @Test
  public void testReset_ClearsPersistedVariants() throws Exception {
    // Arrange: seed persistence + create PersistenceUntilNetworkSuccess-configured SDK.
    seedPersistedResponse(TOKEN_RESET, /*distinctId*/ TOKEN_RESET);

    FeatureFlagOptions flagOptions = new FeatureFlagOptions.Builder()
        .enabled(true)
        .prefetchFlags(false)
        .variantLookupPolicy(VariantLookupPolicy.persistenceUntilNetworkSuccess(TimeUnit.HOURS.toMillis(24)))
        .build();
    MixpanelOptions opts = new MixpanelOptions.Builder()
        .featureFlagOptions(flagOptions)
        .build();

    final AtomicInteger flagsHttpCallCount = new AtomicInteger(0);
    MixpanelAPI mixpanel = newMixpanel(
        TOKEN_RESET, opts, new CountingHangingHttpService(flagsHttpCallCount));

    // Sanity: blob exists before reset.
    assertNotNull(
        "Persisted blob should exist after seeding",
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
        "reset() should remove the persisted flags blob (via clearPreferences())",
        storedPrefs(TOKEN_RESET).getString(BLOB_KEY, null));
  }

  // -----------------------------------------------------------------------
  // NetworkOnly wipes stale persisted blobs left over from a prior config
  // -----------------------------------------------------------------------

  @Test
  public void testNetworkOnly_OnInitClearsStalePersistedBlob() throws Exception {
    // Arrange: a stale persisted blob exists on disk (e.g., from a prior release that used
    // PersistenceUntilNetworkSuccess). The customer has now reconfigured to NetworkOnly.
    seedPersistedResponse(TOKEN_NETWORK_ONLY_STALE, /*distinctId*/ TOKEN_NETWORK_ONLY_STALE);
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
        "NetworkOnly construction should wipe a stale persisted blob from disk",
        storedPrefs(TOKEN_NETWORK_ONLY_STALE).getString(BLOB_KEY, null));
  }

  // -----------------------------------------------------------------------
  // TTL is rechecked at lookup time — expired persisted variants are not
  // served, and the on-disk blob is left intact.
  // -----------------------------------------------------------------------

  @Test
  public void testGetVariant_DoesNotServeExpiredPersistenceButLeavesBlobOnDisk() throws Exception {
    // Arrange: seed persistence with persistedAt=now. Configure PersistenceUntilNetworkSuccess with a short
    // TTL so the persisted values are valid at init-time load but expire shortly thereafter.
    seedPersistedResponse(TOKEN_TTL_LOOKUP, /*distinctId*/ TOKEN_TTL_LOOKUP);

    final long shortTtlMs = 100L;
    FeatureFlagOptions flagOptions = new FeatureFlagOptions.Builder()
        .enabled(true)
        .prefetchFlags(false)
        .variantLookupPolicy(VariantLookupPolicy.persistenceUntilNetworkSuccess(shortTtlMs))
        .build();
    MixpanelOptions opts = new MixpanelOptions.Builder()
        .featureFlagOptions(flagOptions)
        .build();

    final AtomicInteger flagsHttpCallCount = new AtomicInteger(0);
    MixpanelAPI mixpanel = newMixpanel(
        TOKEN_TTL_LOOKUP, opts, new CountingHangingHttpService(flagsHttpCallCount));

    // Sanity #1: the persisted blob loads at init (TTL not yet expired) and getVariantSync
    // serves it. Wait briefly for _loadPersistedVariants to drain on the FF handler thread.
    long deadline = System.currentTimeMillis() + 2_000L;
    while (System.currentTimeMillis() < deadline && !mixpanel.getFlags().areFlagsReady()) {
      Thread.sleep(20);
    }
    assertTrue(
        "Sanity: persisted blob should have loaded into mFlags before TTL expired",
        mixpanel.getFlags().areFlagsReady());

    // Act: sleep past TTL, then look up. The variant in mFlags is now stale per its
    // persistedAtMillis stamp.
    Thread.sleep(shortTtlMs * 3);

    MixpanelFlagVariant fb = new MixpanelFlagVariant("dev-fallback-key", "dev-fallback-value");
    MixpanelFlagVariant served = mixpanel.getFlags().getVariantSync(FLAG_NAME, fb);

    // Assert: served the developer fallback (not the now-expired persisted variant) AND the
    // persisted blob is still on disk (TTL expiry doesn't trigger a clear).
    assertEquals(
        "Expired persisted variant should not be served; expected the developer fallback",
        "dev-fallback-key", served.key);
    assertNotNull(
        "TTL expiry must not clear the on-disk persisted blob",
        storedPrefs(TOKEN_TTL_LOOKUP).getString(BLOB_KEY, null));
  }

  // -----------------------------------------------------------------------
  // Async getVariant under PersistenceUntilNetworkSuccess: when the persisted snapshot loaded
  // at init has since expired, the lookup falls through to a network fetch and
  // returns its result instead of bailing to the developer fallback.
  // -----------------------------------------------------------------------

  @Test
  public void testGetVariantAsync_AwaitsFetchWhenPersistedSnapshotIsExpired() throws Exception {
    // Arrange: seed persistence with persistedAt=now. Configure PersistenceUntilNetworkSuccess with a short
    // TTL so the persisted values load at init but expire shortly after.
    seedPersistedResponse(TOKEN_TTL_AWAIT_FETCH, /*distinctId*/ TOKEN_TTL_AWAIT_FETCH);

    final long shortTtlMs = 100L;
    FeatureFlagOptions flagOptions = new FeatureFlagOptions.Builder()
        .enabled(true)
        .prefetchFlags(false)
        .variantLookupPolicy(VariantLookupPolicy.persistenceUntilNetworkSuccess(shortTtlMs))
        .build();
    MixpanelOptions opts = new MixpanelOptions.Builder()
        .featureFlagOptions(flagOptions)
        .build();

    final AtomicInteger flagsHttpCallCount = new AtomicInteger(0);
    MixpanelAPI mixpanel = newMixpanel(
        TOKEN_TTL_AWAIT_FETCH, opts,
        new CountingHangingHttpService(flagsHttpCallCount));

    // Wait for the init-time persistence load to drain.
    long deadline = System.currentTimeMillis() + 2_000L;
    while (System.currentTimeMillis() < deadline && !mixpanel.getFlags().areFlagsReady()) {
      Thread.sleep(20);
    }
    assertTrue(
        "Sanity: persisted blob should have loaded into mFlags before TTL expired",
        mixpanel.getFlags().areFlagsReady());

    // Act: sleep past TTL so the in-memory snapshot is now stale, then async-lookup.
    Thread.sleep(shortTtlMs * 3);

    final CountDownLatch done = new CountDownLatch(1);
    final AtomicReference<MixpanelFlagVariant> received = new AtomicReference<>();
    mixpanel.getFlags().getVariant(
        FLAG_NAME,
        new MixpanelFlagVariant("dev-fallback-key", "dev-fallback-value"),
        result -> {
          received.set(result);
          done.countDown();
        });

    // Assert: the lookup awaited a network fetch (CountingHanging returns empty flags, so
    // post-fetch the lookup returns the developer fallback) and the network was attempted.
    assertTrue("getVariant callback should fire", done.await(5, TimeUnit.SECONDS));
    assertEquals(
        "Expired persisted snapshot must trigger a network fetch on async lookup",
        1, flagsHttpCallCount.get());
    MixpanelFlagVariant variant = received.get();
    assertNotNull(variant);
    assertEquals(
        "Network response had no flags so the fallback should be served",
        "dev-fallback-key", variant.key);
    assertTrue(
        "Source should be Fallback when the network returned no matching flag",
        variant.source instanceof MixpanelFlagVariant.Source.Fallback);
  }

  // -----------------------------------------------------------------------
  // Empty persisted blob with an expired TTL must NOT serve fallback silently —
  // the lookup falls through to a fetch (matches iOS / fixes a real bug where
  // a previously-empty /flags/ response would lock the SDK into fallback forever).
  // -----------------------------------------------------------------------

  @Test
  public void testGetVariantAsync_EmptyPersistedBlobPastTtlAwaitsFetch() throws Exception {
    // Arrange: seed a blob that loaded successfully but contained no variants. Use a short
    // TTL so the blob ages out almost immediately.
    seedEmptyPersistedResponse(TOKEN_EMPTY_BLOB_TTL, /*distinctId*/ TOKEN_EMPTY_BLOB_TTL);

    final long shortTtlMs = 100L;
    FeatureFlagOptions flagOptions = new FeatureFlagOptions.Builder()
        .enabled(true)
        .prefetchFlags(false)
        .variantLookupPolicy(VariantLookupPolicy.persistenceUntilNetworkSuccess(shortTtlMs))
        .build();
    MixpanelOptions opts = new MixpanelOptions.Builder()
        .featureFlagOptions(flagOptions)
        .build();

    final AtomicInteger flagsHttpCallCount = new AtomicInteger(0);
    MixpanelAPI mixpanel = newMixpanel(
        TOKEN_EMPTY_BLOB_TTL, opts, new CountingHangingHttpService(flagsHttpCallCount));

    // Wait for the empty blob to load — areFlagsReady() flips true once mFlags is the
    // (empty) loaded map.
    long deadline = System.currentTimeMillis() + 2_000L;
    while (System.currentTimeMillis() < deadline && !mixpanel.getFlags().areFlagsReady()) {
      Thread.sleep(20);
    }
    assertTrue(
        "Sanity: empty blob should have loaded (areFlagsReady == true)",
        mixpanel.getFlags().areFlagsReady());

    // Act: sleep past TTL, then async-lookup. Pre-fix, the immediate-serve path would bail
    // to fallback because there were no variants to sample a persistedAt from. Post-fix,
    // loadedFlagsAreStale() consults the manager-level mLoadedBlobPersistedAtMillis directly
    // and forces the lookup through the fetch path.
    Thread.sleep(shortTtlMs * 3);

    final CountDownLatch done = new CountDownLatch(1);
    mixpanel.getFlags().getVariant(
        FLAG_NAME,
        new MixpanelFlagVariant("dev-fallback-key", "dev-fallback-value"),
        result -> done.countDown());

    assertTrue("getVariant callback should fire", done.await(5, TimeUnit.SECONDS));
    assertEquals(
        "Stale empty blob must trigger a network fetch on async lookup",
        1, flagsHttpCallCount.get());
  }

  // -----------------------------------------------------------------------
  // optOutTracking() clears the persisted variants
  // -----------------------------------------------------------------------

  @Test
  public void testOptOutTracking_ClearsPersistedVariants() throws Exception {
    // Arrange: seed persistence + create PersistenceUntilNetworkSuccess-configured SDK.
    seedPersistedResponse(TOKEN_OPT_OUT, /*distinctId*/ TOKEN_OPT_OUT);

    FeatureFlagOptions flagOptions = new FeatureFlagOptions.Builder()
        .enabled(true)
        .prefetchFlags(false)
        .variantLookupPolicy(VariantLookupPolicy.persistenceUntilNetworkSuccess(TimeUnit.HOURS.toMillis(24)))
        .build();
    MixpanelOptions opts = new MixpanelOptions.Builder()
        .featureFlagOptions(flagOptions)
        .build();

    final AtomicInteger flagsHttpCallCount = new AtomicInteger(0);
    MixpanelAPI mixpanel = newMixpanel(
        TOKEN_OPT_OUT, opts, new CountingHangingHttpService(flagsHttpCallCount));

    assertNotNull(
        "Persisted blob should exist after seeding",
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

    // Assert: opt-out wiped the persisted variants — the prior user can't be served their
    // variants after opting out.
    assertNull(
        "optOutTracking() should remove the persisted flags blob",
        storedPrefs(TOKEN_OPT_OUT).getString(BLOB_KEY, null));
  }

  // -----------------------------------------------------------------------
  // NetworkFirst falls back to persisted values when the fetch fails
  // -----------------------------------------------------------------------

  @Test
  public void testNetworkFirst_ServesPersistedVariantWhenFetchFails() throws Exception {
    // Arrange: persisted blob exists, NetworkFirst configured, HTTP fails on /flags/.
    seedPersistedResponse(TOKEN_NETWORK_FIRST_FAIL, /*distinctId*/ TOKEN_NETWORK_FIRST_FAIL);

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
    // serve from the persisted values that the init-time persistence load left in mFlags.
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
        "Expected the persisted variant key, not the developer fallback",
        PERSISTED_VARIANT_KEY, variant.key);
    assertEquals(PERSISTED_VARIANT_VALUE, variant.value);
    assertTrue(
        "Source should be Persistence (persisted value served because network failed)",
        variant.source instanceof MixpanelFlagVariant.Source.Persistence);
    assertEquals(
        "Network call should have been attempted exactly once",
        1, flagsHttpCallCount.get());
  }

  // -----------------------------------------------------------------------
  // helpers
  // -----------------------------------------------------------------------

  private SharedPreferences storedPrefs(String token) {
    // Mirrors MixpanelAPI.storedPrefsName(token, instanceName=null) — the file shared
    // with PersistentIdentity that now also holds the flag persistence blob.
    return mContext.getSharedPreferences(
        "com.mixpanel.android.mpmetrics.MixpanelAPI_" + token, Context.MODE_PRIVATE);
  }

  /**
   * Writes a persisted /flags/ response blob keyed by the given distinct_id, matching the SDK's
   * runtime distinct_id at construction time.
   */
  private void seedPersistedResponse(String token, String distinctId) throws Exception {
    org.json.JSONObject variant = new org.json.JSONObject();
    variant.put("variant_key", PERSISTED_VARIANT_KEY);
    variant.put("variant_value", PERSISTED_VARIANT_VALUE);
    org.json.JSONObject flags = new org.json.JSONObject();
    flags.put(FLAG_NAME, variant);
    org.json.JSONObject response = new org.json.JSONObject();
    response.put("flags", flags);

    org.json.JSONObject blob = new org.json.JSONObject();
    blob.put("persistedAt", System.currentTimeMillis());
    blob.put("distinctId", distinctId);
    blob.put("response", response);

    storedPrefs(token).edit().putString(BLOB_KEY, blob.toString()).commit();
  }

  /**
   * Writes a persisted blob whose response contains an empty flags map — simulates a prior
   * session where /flags/ returned successfully but with no variants assigned to the user.
   * Used to verify the SDK still consults the blob's TTL even when there are no variants
   * to inspect.
   */
  private void seedEmptyPersistedResponse(String token, String distinctId) throws Exception {
    org.json.JSONObject response = new org.json.JSONObject();
    response.put("flags", new org.json.JSONObject());

    org.json.JSONObject blob = new org.json.JSONObject();
    blob.put("persistedAt", System.currentTimeMillis());
    blob.put("distinctId", distinctId);
    blob.put("response", response);

    storedPrefs(token).edit().putString(BLOB_KEY, blob.toString()).commit();
  }

  /**
   * Builds a MixpanelAPI with the supplied HTTP service, a no-op app_open, and — importantly —
   * the default getPersistentIdentity (no wipe). The token is also seeded as the distinct_id
   * and anonymous_id so the SDK's runtime distinct_id deterministically matches anything our
   * tests seed via {@link #seedPersistedResponse}.
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
