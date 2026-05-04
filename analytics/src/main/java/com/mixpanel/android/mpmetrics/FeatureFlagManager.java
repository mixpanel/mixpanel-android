package com.mixpanel.android.mpmetrics;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.mixpanel.android.util.Base64Coder;
import com.mixpanel.android.util.JsonUtils;
import com.mixpanel.android.util.MPConstants;
import com.mixpanel.android.util.MPLog;
import com.mixpanel.android.util.RemoteService;
import com.mixpanel.android.util.W3CTraceContext;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class FeatureFlagManager implements MixpanelAPI.Flags {
  private static final String LOGTAG = "MixpanelAPI.FeatureFlagManager";

  private final WeakReference<FeatureFlagDelegate> mDelegate;
  private final FlagsConfig mFlagsConfig;
  private final String mFlagsEndpoint; // e.g. https://api.mixpanel.com/flags/
  private final String mFlagsRecordingEndpoint; // e.g. https://api.mixpanel.com/flags/
  private final RemoteService mHttpService; // Use RemoteService interface
  private final FeatureFlagHandler mHandler; // For serializing state access and operations
  private final ExecutorService
      mNetworkExecutor; // For performing network calls off the handler thread
  private final Object mLock = new Object();
  // SharedPreferences (shared with PersistentIdentity) used to cache the most recent /flags/
  // response. null when VariantLookupPolicy is NetworkOnly — caching is opt-in via the policy.
  @Nullable private final Future<SharedPreferences> mCachePrefs;

  // Cache blob layout — single key in the shared stored-prefs file. No version field:
  // any parse failure clears the blob, and the next successful fetch overwrites with the
  // current shape. This makes incompatible changes self-healing rather than gated.
  private static final String CACHE_BLOB_KEY = "mixpanel.flags.cache";
  private static final String CACHE_FIELD_CACHED_AT = "cachedAt";
  private static final String CACHE_FIELD_DISTINCT_ID = "distinctId";
  private static final String CACHE_FIELD_RESPONSE = "response";

  // --- State Variables (Protected by mHandler) ---
  private volatile Map<String, MixpanelFlagVariant> mFlags = null;
  // True when mFlags was populated from the on-disk cache and we have not yet seen the
  // initial network response for the current user/context. Only set for NetworkFirst —
  // CacheFirst serves cached values immediately. Async lookups gate on this to honor the
  // NetworkFirst spec ("await on network call, only serve persisted values if it fails")
  // while still letting sync lookups + areFlagsReady() see the cached values.
  private volatile boolean mAwaitingInitialNetworkResponse = false;
  private final Set<String> mTrackedFlags = new HashSet<>();
  private boolean mIsFetching = false;
  private List<FlagCompletionCallback<Boolean>> mFetchCompletionCallbacks = new ArrayList<>();
  private volatile JSONObject mCustomContext;
  // Track last fetch time and latency for $experiment_started event
  private volatile FetchTiming mFetchTiming = FetchTiming.neverFetched();

  // First-time event targeting state (Handler thread only)
  private Map<String, FirstTimeEventDefinition> mPendingFirstTimeEvents = Collections.synchronizedMap(new HashMap<>());
  private final Set<String> mActivatedFirstTimeEvents = Collections.synchronizedSet(new HashSet<>());

  // Bumped on reset() so in-flight fetches dispatched pre-reset don't poison post-reset state.
  private int mFetchGeneration = 0;

  // ---

  /**
   * Immutable class to hold fetch timing information atomically. This ensures thread-safe access to
   * both timing fields together.
   */
  private static class FetchTiming {
    private static final long NEVER_FETCHED = -1L;

    final long timeLastFetched;
    final long fetchLatencyMs;

    /**
     * Creates a FetchTiming instance.
     *
     * @param timeLastFetched Absolute timestamp from System.currentTimeMillis()
     * @param fetchLatencyMs Elapsed time in milliseconds (should be calculated using
     *     System.nanoTime())
     */
    private FetchTiming(long timeLastFetched, long fetchLatencyMs) {
      this.timeLastFetched = timeLastFetched;
      this.fetchLatencyMs = fetchLatencyMs;
    }

    static FetchTiming neverFetched() {
      return new FetchTiming(NEVER_FETCHED, NEVER_FETCHED);
    }

    boolean hasBeenFetched() {
      return timeLastFetched != NEVER_FETCHED;
    }
  }

  /** Mutable container for passing results back from handler thread runnables. */
  private static class SyncResultContainer {
    MixpanelFlagVariant flagVariant = null;
    boolean tracked = false;
  }

  // Message codes for Handler
  private static final int MSG_FETCH_FLAGS_IF_NEEDED = 0;
  private static final int MSG_COMPLETE_FETCH = 1;

  public FeatureFlagManager(
      @NonNull FeatureFlagDelegate delegate,
      @NonNull RemoteService httpService,
      @NonNull FlagsConfig flagsConfig) {
    this(delegate, httpService, flagsConfig, null);
  }

  public FeatureFlagManager(
      @NonNull FeatureFlagDelegate delegate,
      @NonNull RemoteService httpService,
      @NonNull FlagsConfig flagsConfig,
      @Nullable Future<SharedPreferences> cachePrefs) {
    mDelegate = new WeakReference<>(delegate);
    mFlagsEndpoint = delegate.getMPConfig().getFlagsEndpoint();
    mFlagsRecordingEndpoint = delegate.getMPConfig().getFlagsRecordingEndpoint();
    mHttpService = httpService;
    mFlagsConfig = flagsConfig;
    mCachePrefs = cachePrefs;
    try {
      mCustomContext = new JSONObject(flagsConfig.context.toString());
    } catch (JSONException e) {
      mCustomContext = new JSONObject();
    }

    // Dedicated thread for serializing access to flags state
    HandlerThread handlerThread =
        new HandlerThread("com.mixpanel.android.FeatureFlagManagerWorker", Thread.MIN_PRIORITY);
    handlerThread.start();
    mHandler = new FeatureFlagHandler(handlerThread.getLooper());

    // Separate executor for network requests so they don't block the state queue
    mNetworkExecutor = Executors.newSingleThreadExecutor();

    // Initialize cache state on the handler thread so SharedPreferences I/O does not block
    // the caller (typically the main thread during MixpanelAPI construction).
    //  - Caching policies (CacheFirst / NetworkFirst): load the cached blob into mFlags.
    //  - NetworkOnly: wipe any stale cache blob left over from a prior policy configuration.
    if (mCachePrefs != null) {
      if (isCachingPolicy()) {
        mHandler.post(this::_loadCachedVariants);
      } else {
        mHandler.post(this::clearCacheOnDisk);
      }
    }
  }

  /**
   * Returns {@code true} when the configured policy reads/writes the on-disk cache
   * (CacheFirst or NetworkFirst). NetworkOnly returns {@code false}.
   */
  private boolean isCachingPolicy() {
    return !(mFlagsConfig.variantLookupPolicy instanceof VariantLookupPolicy.NetworkOnly);
  }

  /**
   * Returns {@code true} if the variant was loaded from the on-disk cache and is now past
   * the configured TTL. Network-sourced variants and developer-supplied fallbacks always
   * return {@code false}. Used in lookup paths to suppress serving stale cached values
   * (without clearing the on-disk blob — the blob may become valid again under a longer
   * TTL config or be overwritten by the next successful fetch).
   */
  private boolean isExpiredCacheVariant(@Nullable MixpanelFlagVariant variant) {
    if (variant == null || !(variant.source instanceof MixpanelFlagVariant.Source.Cache)) {
      return false;
    }
    final long cachedAt = ((MixpanelFlagVariant.Source.Cache) variant.source).cachedAtMillis;
    final long ttl = cacheTtlMillis();
    return ttl > 0 && cachedAt > 0 && (System.currentTimeMillis() - cachedAt) > ttl;
  }

  // --- Public Methods ---

  /** Asynchronously loads flags from the Mixpanel server if they haven't been loaded yet */
  public void loadFlags() {
    // Send message to the handler thread to check and potentially fetch
    mHandler.sendMessage(mHandler.obtainMessage(MSG_FETCH_FLAGS_IF_NEEDED));
  }

  /**
   * Clears all in-memory feature flag state: cached flags, tracked-flag set, fetch timing,
   * first-time event state, and the persisted-cache fallback. Also wipes the on-disk cache
   * blob so a freshly-identified user can't be served the prior user's variants — note that
   * {@code MixpanelAPI.reset()} also calls {@code PersistentIdentity.clearPreferences()}
   * which wipes the same shared file, so the disk clear here is defensive (idempotent) but
   * keeps this method self-contained.
   *
   * <p>Posts to the handler thread so the mutation is serialized with reads and fetches. Any
   * in-flight fetch dispatched before this call is discarded when it completes (via the
   * generation check in {@code _completeFetch}). Pending fetch-completion callbacks are invoked
   * with {@code false} so callers don't hang.
   */
  public void reset() {
    mHandler.post(
        () -> {
          mFetchGeneration++;

          synchronized (mLock) {
            mFlags = null;
          }
          mTrackedFlags.clear();
          mFetchTiming = FetchTiming.neverFetched();
          mPendingFirstTimeEvents = Collections.synchronizedMap(new HashMap<>());
          mActivatedFirstTimeEvents.clear();
          mAwaitingInitialNetworkResponse = false;

          if (mCachePrefs != null) {
            clearCacheOnDisk();
          }

          List<FlagCompletionCallback<Boolean>> orphaned = mFetchCompletionCallbacks;
          mFetchCompletionCallbacks = new ArrayList<>();
          mIsFetching = false;
          for (FlagCompletionCallback<Boolean> cb : orphaned) {
            postCompletion(cb, false);
          }
        });
  }

  @Override
  public void setContext(@NonNull Map<String, Object> context, @NonNull FlagCompletionCallback<Boolean> completion) {
    try {
      mCustomContext = new JSONObject(context);
    } catch (Exception e) {
      MPLog.e(LOGTAG, "Failed to set custom context", e);
      mCustomContext = new JSONObject();
    }
    mHandler.post(() -> _fetchFlagsIfNeeded(completion));
  }

  /**
   * Asynchronously loads flags from the Mixpanel server with a completion callback.
   * The callback is invoked on the main thread with true on success and false on failure.
   */
  public void loadFlags(@Nullable FlagCompletionCallback<Boolean> callback) {
    mHandler.post(() -> _fetchFlagsIfNeeded(callback));
  }

  /**
   * Returns true if flag variants are in memory and available for synchronous access.
   * Includes variants restored from the on-disk cache; callers that need to distinguish
   * fresh-from-network values from cached ones should inspect {@code variant.source} on the
   * served {@link MixpanelFlagVariant}. See {@link MixpanelAPI.Flags#areFlagsReady()} for
   * the full caller-facing contract.
   */
  public boolean areFlagsReady() {
    synchronized (mLock) {
      return mFlags != null;
    }
  }

  // --- Sync Flag Retrieval ---

  /**
   * Gets the feature flag variant (key and value) synchronously. IMPORTANT: This method will block
   * the calling thread until the value can be retrieved. It is NOT recommended to call this from
   * the main UI thread if flags might not be ready. If flags are not ready (`areFlagsReady()` is
   * false), it returns the fallback immediately without blocking or fetching.
   *
   * @param flagName The name of the feature flag.
   * @param fallback The MixpanelFlagVariant instance to return if the flag is not found or not
   *     ready.
   * @return The found MixpanelFlagVariant or the fallback.
   */
  @NonNull
  public MixpanelFlagVariant getVariantSync(
      @NonNull final String flagName, @NonNull final MixpanelFlagVariant fallback) {
    // 1. Check readiness first - don't block if flags aren't loaded.
    if (!areFlagsReady()) {
      MPLog.w(
          LOGTAG,
          "Flags not ready for getVariantSync call for '" + flagName + "'. Returning fallback.");
      return fallback;
    }

    // Use a container to get results back from the handler thread runnable
    final SyncResultContainer resultContainer = new SyncResultContainer();

    // 2. Execute the core logic synchronously on the handler thread
    mHandler.runAndWait(
        () -> {
          // We are now on the mHandler thread. areFlagsReady() was true, but check mFlags again for
          // safety.
          if (mFlags == null) { // Should not happen if areFlagsReady was true, but defensive check
            MPLog.w(LOGTAG, "Flags became null unexpectedly in getVariantSync runnable.");
            return; // Keep resultContainer.flagVariant as null
          }

          MixpanelFlagVariant variant = mFlags.get(flagName);
          // Suppress stale cached variants — leave the disk blob alone, just don't serve.
          if (isExpiredCacheVariant(variant)) {
            MPLog.d(
                LOGTAG,
                "Cached variant for '" + flagName + "' is past TTL; returning fallback.");
            variant = null;
          }
          if (variant != null) {
            resultContainer.flagVariant = variant;

            // Perform atomic check-and-set for tracking directly here
            // (Calls _checkAndSetTrackedFlag which runs on this thread)
            resultContainer.tracked = _checkAndSetTrackedFlag(flagName);
          }
          // If variant is null, resultContainer.flagVariant remains null
        });

    // 3. Process results after handler block completes

    if (resultContainer.flagVariant != null) {
      if (resultContainer.tracked) {
        // If tracking was performed *in this call*, trigger the delegate call helper
        // (This runs on the *calling* thread, but _performTrackingDelegateCall dispatches to main)
        _performTrackingDelegateCall(flagName, resultContainer.flagVariant);
      }
      return resultContainer.flagVariant;
    } else {
      // Flag key not found in the loaded flags
      MPLog.i(LOGTAG, "Flag '" + flagName + "' not found sync. Returning fallback.");
      return fallback;
    }
  }

  /**
   * Gets the value of a feature flag synchronously. IMPORTANT: See warning on getVariantSync
   * regarding blocking and readiness checks. Returns fallback immediately if flags are not ready.
   *
   * @param flagName The name of the feature flag.
   * @param fallbackValue The default value to return if the flag is missing or not ready.
   * @return The flag's value (Object or null) or the fallbackValue.
   */
  @Nullable
  public Object getVariantValueSync(@NonNull String flagName, @Nullable Object fallbackValue) {
    MixpanelFlagVariant fallbackVariant = new MixpanelFlagVariant("", fallbackValue);
    MixpanelFlagVariant resultVariant = getVariantSync(flagName, fallbackVariant);
    // If getVariantSync returned the *original* fallbackValue, its value is fallbackValue.
    // If getVariantSync returned a *real* flag, its value is resultVariant.value.
    return resultVariant.value;
  }

  /**
   * Checks if a feature flag is enabled synchronously (evaluates value as boolean). IMPORTANT: See
   * warning on getVariantSync regarding blocking and readiness checks. Returns fallbackValue
   * immediately if flags are not ready.
   *
   * @param flagName The name of the feature flag.
   * @param fallbackValue The default boolean value if the flag is missing, not boolean, or not
   *     ready.
   * @return True if the flag evaluates to true, false otherwise or if fallbackValue is returned.
   */
  public boolean isEnabledSync(@NonNull String flagName, boolean fallbackValue) {
    Object variantValue = getVariantValueSync(flagName, fallbackValue);
    return _evaluateBooleanFlag(flagName, variantValue, fallbackValue);
  }

  /**
   * Checks if a tracked event matches any pending first-time event conditions.
   * If a match is found, the corresponding flag variant is activated and a recording API call is made.
   * This method is called from MixpanelAPI.track() and is thread-safe.
   *
   * @param eventName The name of the tracked event
   * @param properties The event properties
   */
  public void checkFirstTimeEvent(@NonNull String eventName, @NonNull JSONObject properties) {
    mHandler.post(() -> _checkFirstTimeEventOnHandlerThread(eventName, properties));
  }


  /**
   * Asynchronously gets the feature flag variant (key and value). If flags are not loaded, it
   * triggers a fetch. Completion handler is called on the main thread.
   *
   * @param flagName The name of the feature flag.
   * @param fallback The MixpanelFlagVariant instance to return if the flag is not found or fetch
   *     fails.
   * @param completion The callback to receive the result.
   */
  public void getVariant(
      @NonNull final String flagName,
      @NonNull final MixpanelFlagVariant fallback,
      @NonNull final FlagCompletionCallback<MixpanelFlagVariant> completion) {
    // Post the core logic to the handler thread for safe state access
    mHandler.post(
        () -> {
          // Serve immediately when mFlags is populated and we're not in the NetworkFirst-init
          // window (waiting for the first network response after a cache hit).
          boolean canServeImmediately = mFlags != null && !mAwaitingInitialNetworkResponse;

          if (canServeImmediately) {
            MPLog.v(LOGTAG, "Flags ready. Checking for flag '" + flagName + "'");
            MixpanelFlagVariant flagVariant = mFlags.get(flagName);
            // Suppress stale cached variants — leave the disk blob alone, just don't serve.
            if (isExpiredCacheVariant(flagVariant)) {
              MPLog.d(
                  LOGTAG,
                  "Cached variant for '" + flagName + "' is past TTL; returning fallback.");
              flagVariant = null;
            }
            final MixpanelFlagVariant variantForLambda = flagVariant;
            boolean needsTracking = (variantForLambda != null) && _checkAndSetTrackedFlag(flagName);
            MixpanelFlagVariant result = (variantForLambda != null) ? variantForLambda : fallback;

            new Handler(Looper.getMainLooper())
                .post(
                    () -> {
                      completion.onComplete(result);
                      if (variantForLambda != null && needsTracking) {
                        _performTrackingDelegateCall(flagName, result);
                      }
                    });
          } else {
            // Either mFlags is null OR we're a NetworkFirst caller awaiting the initial
            // network response. Trigger a fetch and serve from mFlags after it completes.
            // On failure, mFlags is left untouched: for NetworkFirst with a cache hit it
            // still holds the cached values; for everything else it's still null and we
            // fall back to the developer-supplied fallback.
            MPLog.i(
                LOGTAG,
                "Flags not yet servable, attempting fetch for getVariant '" + flagName + "'...");
            _fetchFlagsIfNeeded(
                success -> {
                  // Fetch completion runs on the main thread; hop back to handler so we can
                  // read mFlags and run the tracking check atomically.
                  mHandler.post(
                      () -> {
                        MixpanelFlagVariant fetchedVariant =
                            mFlags != null ? mFlags.get(flagName) : null;
                        // If a fetch failure left mFlags holding stale cached values, treat
                        // expired entries as not-found and return the developer fallback.
                        if (isExpiredCacheVariant(fetchedVariant)) {
                          fetchedVariant = null;
                        }
                        final MixpanelFlagVariant variantForLambda = fetchedVariant;
                        boolean tracked =
                            (variantForLambda != null) && _checkAndSetTrackedFlag(flagName);
                        MixpanelFlagVariant finalResult =
                            (variantForLambda != null) ? variantForLambda : fallback;

                        new Handler(Looper.getMainLooper())
                            .post(
                                () -> {
                                  completion.onComplete(finalResult);
                                  if (variantForLambda != null && tracked) {
                                    _performTrackingDelegateCall(flagName, finalResult);
                                  }
                                });
                      });
                });
          }
        });
  }

  /**
   * Asynchronously gets the value of a feature flag. If flags are not loaded, it triggers a fetch.
   * Completion handler is called on the main thread.
   *
   * @param flagName The name of the feature flag.
   * @param fallbackValue The default value to return if the flag is missing or fetch fails.
   * @param completion The callback to receive the result value (Object or null).
   */
  public void getVariantValue(
      @NonNull final String flagName,
      @Nullable final Object fallbackValue,
      @NonNull final FlagCompletionCallback<Object> completion) {
    // Create a fallback MixpanelFlagVariant. Using empty key as it's not relevant here.
    MixpanelFlagVariant fallbackVariant = new MixpanelFlagVariant("", fallbackValue);
    // Call getVariant and extract the value in its completion handler
    getVariant(flagName, fallbackVariant, result -> completion.onComplete(result.value));
  }

  /**
   * Asynchronously checks if a feature flag is enabled (evaluates value as boolean). If flags are
   * not loaded, it triggers a fetch. Completion handler is called on the main thread.
   *
   * @param flagName The name of the feature flag.
   * @param fallbackValue The default boolean value if the flag is missing, not boolean, or fetch
   *     fails.
   * @param completion The callback to receive the boolean result.
   */
  public void isEnabled(
      @NonNull final String flagName,
      final boolean fallbackValue,
      @NonNull final FlagCompletionCallback<Boolean> completion) {
    // Call getVariantValue, using the boolean fallbackValue as the fallback too
    // (this ensures if the flag is missing, evaluateBoolean gets the intended fallback)
    getVariantValue(
        flagName,
        fallbackValue,
        value -> {
          // This completion runs on the main thread
          boolean isEnabled = _evaluateBooleanFlag(flagName, value, fallbackValue);
          completion.onComplete(isEnabled);
        });
  }

  @Override
  @NonNull
  public Map<String, MixpanelFlagVariant> getAllVariantsSync() {
    final Map<String, MixpanelFlagVariant> snapshot;
    synchronized (mLock) {
      if (mFlags == null) {
        return Collections.emptyMap();
      }
      snapshot = mFlags;
    }
    // mFlags is wholesale-replaced on every state change, so all entries share a single
    // Source.Cache(cachedAt) — checking any one entry tells us whether the whole map is
    // expired. Sample the first; if expired, return an empty map (matching the per-variant
    // "don't serve expired cache" rule from getVariant).
    if (!snapshot.isEmpty()) {
      MixpanelFlagVariant first = snapshot.values().iterator().next();
      if (isExpiredCacheVariant(first)) {
        return Collections.emptyMap();
      }
    }
    return Collections.unmodifiableMap(snapshot);
  }

  @Override
  public void getAllVariants(@NonNull final FlagCompletionCallback<Map<String, MixpanelFlagVariant>> completion) {
    mHandler.post(() -> {
      boolean canServeImmediately = mFlags != null && !mAwaitingInitialNetworkResponse;

      if (canServeImmediately) {
        // Route through getAllVariantsSync so the TTL check on cached entries applies here too.
        postCompletion(completion, getAllVariantsSync());
      } else {
        MPLog.i(LOGTAG, "Flags not yet servable, attempting fetch for getAllVariants...");
        _fetchFlagsIfNeeded(success -> {
          // Fetch completion runs on the main thread. After it fires, mFlags may have been
          // refreshed (success), left as cached values (NetworkFirst failure with cache hit),
          // or remain null (no cache + failure). Always serve from getAllVariantsSync, which
          // returns an empty map when mFlags is null.
          completion.onComplete(getAllVariantsSync());
        });
      }
    });
  }

  // --- Handler ---

  private class FeatureFlagHandler extends Handler {
    public FeatureFlagHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
      switch (msg.what) {
        case MSG_FETCH_FLAGS_IF_NEEDED:
          _fetchFlagsIfNeeded(null); // Assume completion passed via instance var now
          break;

        case MSG_COMPLETE_FETCH:
          // Extract results from the Message Bundle
          Bundle data = msg.getData();
          int generation = data.getInt("generation");
          if (generation != mFetchGeneration) {
            MPLog.d(
                LOGTAG,
                "Discarding flag fetch result from stale generation "
                    + generation
                    + " (current "
                    + mFetchGeneration
                    + ")");
            break;
          }
          boolean success = data.getBoolean("success");
          String responseJsonString = data.getString("responseJson"); // Can be null
          String errorMessage = data.getString("errorMessage"); // Can be null

          JSONObject responseJson = null;
          if (success && responseJsonString != null) {
            try {
              responseJson = new JSONObject(responseJsonString);
            } catch (JSONException e) {
              MPLog.e(LOGTAG, "Could not parse response JSON string in completeFetch", e);
              success = false; // Treat parse failure as overall failure
              errorMessage = "Failed to parse flags response JSON.";
            }
          }
          if (!success && errorMessage != null) {
            MPLog.w(LOGTAG, "Flag fetch failed: " + errorMessage);
          }
          // Call the internal completion logic
          _completeFetch(success, responseJson);
          break;

        default:
          MPLog.e(LOGTAG, "Unknown message type " + msg.what);
      }
    }

    /**
     * Executes a Runnable synchronously on this handler's thread. Blocks the calling thread until
     * the Runnable completes. Handles being called from the handler's own thread to prevent
     * deadlock.
     *
     * @param r The Runnable to execute.
     */
    public void runAndWait(Runnable r) {
      if (Thread.currentThread() == getLooper().getThread()) {
        // Already on the handler thread, run directly
        r.run();
      } else {
        // Use CountDownLatch to wait for completion
        final java.util.concurrent.CountDownLatch latch =
            new java.util.concurrent.CountDownLatch(1);
        post(
            () -> { // Post the task to the handler thread
              try {
                r.run();
              } finally {
                latch.countDown(); // Signal completion even if Runnable throws
              }
            });

        // Wait for the latch
        try {
          latch.await();
        } catch (InterruptedException e) {
          MPLog.e(LOGTAG, "Interrupted waiting for handler task", e);
          Thread.currentThread().interrupt(); // Preserve interrupt status
        }
      }
    }
  }

  // --- Internal Methods (run on Handler thread or background executor) ---

  // Runs on Handler thread
  private void _fetchFlagsIfNeeded(@Nullable FlagCompletionCallback<Boolean> completion) {
    // It calls _performFetchRequest via mNetworkExecutor if needed.
    boolean shouldStartFetch = false;

    if (!mFlagsConfig.enabled) {
      MPLog.i(LOGTAG, "Feature flags are disabled, not fetching.");
      postCompletion(completion, false);
      return;
    }

    if (completion != null) {
      mFetchCompletionCallbacks.add(completion);
    }

    if (!mIsFetching) {
      mIsFetching = true;
      shouldStartFetch = true;
    } else {
      MPLog.d(LOGTAG, "Fetch already in progress, queueing completion handler.");
    }

    if (shouldStartFetch) {
      MPLog.d(LOGTAG, "Starting flag fetch (dispatching network request)...");
      final int generation = mFetchGeneration;
      mNetworkExecutor.execute(() -> _performFetchRequest(generation));
    }
  }

  // Runs on Network Executor thread
  /**
   * Performs the actual network request on the mNetworkExecutor thread. Constructs the request,
   * sends it, and posts the result (success/failure + data) back to the mHandler thread via
   * MSG_COMPLETE_FETCH.
   */
  private void _performFetchRequest(int generation) {
    long fetchStartNanos = System.nanoTime(); // For measuring elapsed time
    long fetchStartMillis = System.currentTimeMillis(); // For absolute timestamp
    MPLog.v(LOGTAG, "Performing fetch request on thread: " + Thread.currentThread().getName());
    boolean success = false;
    JSONObject responseJson = null; // To hold parsed successful response
    String errorMessage = "Delegate or config not available"; // Default error

    final FeatureFlagDelegate delegate = mDelegate.get();
    if (delegate == null) {
      MPLog.w(LOGTAG, "Delegate became null before network request could start.");
      postResultToHandler(generation, false, null, errorMessage);
      return;
    }

    final MPConfig config = delegate.getMPConfig();
    final String distinctId = delegate.getDistinctId();
    final String deviceId = delegate.getAnonymousId();

    if (distinctId == null) {
      MPLog.w(LOGTAG, "Distinct ID is null. Cannot fetch flags.");
      errorMessage = "Distinct ID is null.";
      postResultToHandler(generation, false, null, errorMessage);
      return;
    }

    try {
      // 1. Build Query Parameters
      // Defensive copy: we mutate contextJson below (adding distinct_id, device_id)
      JSONObject contextJson = new JSONObject(mCustomContext.toString());
      contextJson.put("distinct_id", distinctId);
      if (deviceId != null) {
        contextJson.put("device_id", deviceId);
      }

      Map<String, Object> params = new HashMap<>();
      params.put("context", contextJson.toString());
      params.put("token", delegate.getToken());
      params.put("mp_lib", "android");
      params.put("$lib_version", MPConfig.VERSION);

      MPLog.v(LOGTAG, "Request query parameters: " + params.toString());

      // 2. Build Headers
      String token = delegate.getToken();
      if (token == null || token.trim().isEmpty()) {
        throw new IOException("Mixpanel token is missing or empty.");
      }
      String authString = token + ":";
      String base64Auth = Base64Coder.encodeString(authString);
      Map<String, String> headers = new HashMap<>();
      headers.put("Authorization", "Basic " + base64Auth);

      // 3. Perform GET Request
      RemoteService.RequestResult result =
          mHttpService.performRequest(
              RemoteService.HttpMethod.GET,
              mFlagsEndpoint,
              config.getProxyServerInteractor(),
              params,
              headers,
              null, // No request body for GET requests
              config.getSSLSocketFactory());
      byte[] responseBytes = result.getResponse();

      // 4. Process Response
      if (responseBytes == null) {
        errorMessage = "Received non-successful HTTP status or null response from flags endpoint.";
        MPLog.w(LOGTAG, errorMessage);
      } else {
        try {
          String responseString = new String(responseBytes, "UTF-8");
          MPLog.v(LOGTAG, "Flags response: " + responseString);
          responseJson = new JSONObject(responseString);
          if (responseJson.has("error")) {
            errorMessage = "Mixpanel API returned error: " + responseJson.getString("error");
            MPLog.e(LOGTAG, errorMessage);
            // Keep success = false
          } else {
            success = true; // Parsed JSON successfully and no 'error' field
          }
        } catch (UnsupportedEncodingException e) {
          throw new RuntimeException(
              "UTF-8 not supported on this platform?", e); // Should not happen
        } catch (JSONException e) {
          errorMessage = "Could not parse Mixpanel flags response";
          MPLog.e(LOGTAG, errorMessage, e);
          // Keep success = false
        }
      }
    } catch (RemoteService.ServiceUnavailableException e) {
      errorMessage = "Mixpanel service unavailable";
      MPLog.w(LOGTAG, errorMessage, e);
      // TODO: Implement retry logic / backoff based on e.getRetryAfter() if needed?
      // For now, just fail the fetch completely for simplicity.
    } catch (MalformedURLException e) {
      errorMessage = "Flags endpoint URL is malformed: " + mFlagsEndpoint;
      MPLog.e(LOGTAG, errorMessage, e);
    } catch (IOException e) {
      errorMessage = "Network error while fetching flags";
      MPLog.e(LOGTAG, errorMessage, e);
    } catch (JSONException e) {
      errorMessage = "Failed to construct request JSON";
      MPLog.e(LOGTAG, errorMessage, e);
    } catch (Exception e) { // Catch unexpected errors
      errorMessage = "Unexpected error during flag fetch";
      MPLog.e(LOGTAG, errorMessage, e);
    }

    long fetchEndNanos = System.nanoTime();
    long fetchEndMillis = System.currentTimeMillis();

    // Calculate latency using nanoTime (convert to milliseconds)
    long fetchLatencyMs = (fetchEndNanos - fetchStartNanos) / 1_000_000;

    // Update fetch timing atomically with absolute timestamp and accurate latency
    mFetchTiming = new FetchTiming(fetchEndMillis, fetchLatencyMs);
    // 5. Post result back to Handler thread
    postResultToHandler(generation, success, responseJson, errorMessage);
  }

  /** Helper to dispatch the result of the fetch back to the handler thread. */
  private void postResultToHandler(
      int generation,
      boolean success,
      @Nullable JSONObject responseJson,
      @Nullable String errorMessage) {
    // Use a Bundle to pass multiple arguments efficiently
    android.os.Bundle resultData = new android.os.Bundle();
    resultData.putInt("generation", generation);
    resultData.putBoolean("success", success);
    if (success && responseJson != null) {
      resultData.putString("responseJson", responseJson.toString());
    } else if (!success && errorMessage != null) {
      resultData.putString("errorMessage", errorMessage);
    }

    Message msg = mHandler.obtainMessage(MSG_COMPLETE_FETCH);
    msg.setData(resultData);
    mHandler.sendMessage(msg);
  }

  // Runs on Handler thread
  /**
   * Centralized fetch completion logic. Runs on the Handler thread. Updates state and calls
   * completion handlers.
   *
   * @param success Whether the fetch was successful.
   * @param flagsResponseJson The parsed JSON object from the response, or null if fetch failed or
   *     parsing failed.
   */
  @VisibleForTesting
  void _completeFetch(boolean success, @Nullable JSONObject flagsResponseJson) {
    MPLog.d(LOGTAG, "Completing fetch request. Success: " + success);
    // State updates MUST happen on the handler thread implicitly
    mIsFetching = false;

    List<FlagCompletionCallback<Boolean>> callbacksToCall = mFetchCompletionCallbacks;
    mFetchCompletionCallbacks = new ArrayList<>();

    // Whatever the outcome, we've now seen a network response (or definitively failed to
    // get one). NetworkFirst async lookups can stop awaiting the initial response.
    mAwaitingInitialNetworkResponse = false;

    if (success && flagsResponseJson != null) {
      try {
        Map<String, MixpanelFlagVariant> rawFlags = JsonUtils.parseFlagsResponse(flagsResponseJson);

        // Parse pending first-time events
        Map<String, FirstTimeEventDefinition> newPendingEvents = parsePendingFirstTimeEvents(flagsResponseJson);

        // Merge with activated events to preserve session state
        mergeWithActivatedEvents(rawFlags, newPendingEvents);

        // Stamp NETWORK source on every variant before exposing through mFlags.
        Map<String, MixpanelFlagVariant> newFlags = stampSource(rawFlags, MixpanelFlagVariant.Source.network());

        // Update state — mFlags goes from cached values (or null) to fresh network values.
        synchronized (mLock) {
          mFlags = Collections.unmodifiableMap(newFlags);
        }
        mPendingFirstTimeEvents = newPendingEvents;

        // Persist the raw response to disk so future sessions / failed fetches can fall back.
        // Storing the wire format (rather than re-serializing the parsed variants) keeps
        // JsonUtils.parseFlagsResponse as the single source of truth and carries
        // pending_first_time_events along for free. Writes happen for any caching policy;
        // NetworkOnly skips them.
        if (isCachingPolicy() && mCachePrefs != null) {
          writeCacheToDisk(flagsResponseJson);
        }

        MPLog.v(LOGTAG, "Flags updated: " + mFlags.size() + " flags loaded, " +
                newPendingEvents.size() + " pending first-time events.");
      } catch (Exception e) {
        MPLog.e(LOGTAG, "Unexpected error parsing flags response", e);
        success = false;  // Mark as failure so callbacks receive correct status
      }
    } else {
      // Fetch failed or returned an unparseable response. mFlags is left untouched —
      // for NetworkFirst with a cache hit it still holds the cached values (Source.Cache),
      // for any other configuration it stays null and async lookups serve the fallback.
      MPLog.w(
          LOGTAG,
          "Flag fetch failed or response missing/invalid. Keeping existing flags (if any).");
    }

    // Call handlers outside the state update logic, dispatch to main thread
    if (!callbacksToCall.isEmpty()) {
      MPLog.d(LOGTAG, "Calling " + callbacksToCall.size() + " fetch completion handlers.");
      for (FlagCompletionCallback<Boolean> callback : callbacksToCall) {
        postCompletion(callback, success);
      }
    } else {
      MPLog.d(LOGTAG, "No fetch completion handlers to call.");
    }
  }

  /**
   * Atomically checks if a feature flag has been tracked and marks it as tracked if not. MUST be
   * called from the mHandler thread.
   *
   * @param flagName The name of the feature flag.
   * @return true if the flag was NOT previously tracked (and was therefore marked now), false
   *     otherwise.
   */
  private boolean _checkAndSetTrackedFlag(@NonNull String flagName) {
    // Already running on the handler thread, direct access is safe and serialized
    if (!mTrackedFlags.contains(flagName)) {
      mTrackedFlags.add(flagName);
      return true; // Needs tracking
    }
    return false; // Already tracked
  }

  /**
   * Constructs the $experiment_started event properties and dispatches the track call to the main
   * thread via the delegate. This method itself does NOT need to run on the handler thread, but is
   * typically called after a check that runs on the handler thread (_trackFeatureIfNeeded).
   *
   * @param flagName Name of the feature flag.
   * @param variant The specific variant received.
   */
  private void _performTrackingDelegateCall(String flagName, MixpanelFlagVariant variant) {
    final FeatureFlagDelegate delegate = mDelegate.get();
    if (delegate == null) {
      MPLog.w(LOGTAG, "Delegate is null, cannot track $experiment_started.");
      return;
    }

    // Construct properties
    JSONObject properties = new JSONObject();
    try {
      properties.put("Experiment name", flagName);
      properties.put("Variant name", variant.key); // Use the variant key
      properties.put("$experiment_type", "feature_flag");

      // Include timing information if available
      FetchTiming timing = mFetchTiming;
      if (timing.hasBeenFetched()) {
        properties.put("timeLastFetched", timing.timeLastFetched);
        properties.put("fetchLatencyMs", timing.fetchLatencyMs);
      }

      if (variant.experimentID != null) {
        properties.put("$experiment_id", variant.experimentID);
      }
      if (variant.isExperimentActive != null) {
        properties.put("$is_experiment_active", variant.isExperimentActive);
      }
      if (variant.isQATester != null) {
        properties.put("$is_qa_tester", variant.isQATester);
      }
    } catch (JSONException e) {
      MPLog.e(LOGTAG, "Failed to create JSON properties for $experiment_started event", e);
      return; // Don't track if properties failed
    }

    MPLog.v(LOGTAG, "Queueing $experiment_started event for dispatch: " + properties.toString());

    // Dispatch delegate call asynchronously to main thread for safety
    new Handler(Looper.getMainLooper())
        .post(
            () -> {
              // Re-fetch delegate inside main thread runnable just in case? Usually not necessary.
              final FeatureFlagDelegate currentDelegate = mDelegate.get();
              if (currentDelegate != null) {
                currentDelegate.track("$experiment_started", properties);
                MPLog.v(
                    LOGTAG,
                    "Tracked $experiment_started for " + flagName + " (dispatched to main)");
              } else {
                MPLog.w(LOGTAG, "Delegate was null when track call executed on main thread.");
              }
            });
  }

  // Helper to post completion callbacks to the main thread
  private <T> void postCompletion(
      @Nullable final FlagCompletionCallback<T> callback, final T result) {
    if (callback != null) {
      new Handler(Looper.getMainLooper()).post(() -> callback.onComplete(result));
    }
  }

  // --- Boolean Evaluation Helper ---
  private boolean _evaluateBooleanFlag(
      String flagName, Object variantValue, boolean fallbackValue) {
    if (variantValue instanceof Boolean) {
      return (Boolean) variantValue;
    }
    MPLog.w(LOGTAG, "Flag value for " + flagName + " not boolean: " + variantValue);
    return fallbackValue;
  }

  // --- First-Time Event Targeting Methods ---

  /**
   * Checks if the tracked event matches any pending first-time event conditions.
   * Runs on Handler thread for thread-safe access to mPendingFirstTimeEvents and mActivatedFirstTimeEvents.
   */
  private void _checkFirstTimeEventOnHandlerThread(String eventName, JSONObject properties) {
    if (mPendingFirstTimeEvents.isEmpty()) {
      return; // No pending events to check
    }

    // Iterate through all pending events using Iterator to allow safe removal
    java.util.Iterator<Map.Entry<String, FirstTimeEventDefinition>> iterator =
        mPendingFirstTimeEvents.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, FirstTimeEventDefinition> entry = iterator.next();
      String compositeKey = entry.getKey();
      FirstTimeEventDefinition def = entry.getValue();

      // Skip if already activated
      if (mActivatedFirstTimeEvents.contains(compositeKey)) {
        continue;
      }

      // Check event name match (case-sensitive)
      if (!eventName.equals(def.eventName)) {
        continue;
      }

      // Check property filters if present
      if (def.propertyFilters != null && def.propertyFilters.length() > 0) {
        if (!FirstTimeEventChecker.propertyFiltersMatch(properties, def.propertyFilters)) {
          continue; // Property filters didn't match
        }
      }

      // Match found - activate this event
      _activateFirstTimeEvent(compositeKey, def, iterator);
    }
  }

  /**
   * Activates a first-time event: updates the flag variant, marks as activated, and fires recording API.
   * Runs on Handler thread.
   */
  private void _activateFirstTimeEvent(String compositeKey, FirstTimeEventDefinition def,
                                        java.util.Iterator<Map.Entry<String, FirstTimeEventDefinition>> iterator) {
    // Add to activated set
    mActivatedFirstTimeEvents.add(compositeKey);

    // Remove from pending map using iterator to avoid ConcurrentModificationException
    iterator.remove();

    // Update the flag variant (synchronized access to mFlags)
    synchronized (mLock) {
      if (mFlags == null) {
        mFlags = new HashMap<>();
      }
      Map<String, MixpanelFlagVariant> mutableFlags = new HashMap<>(mFlags);
      // Stamp the activated variant with NETWORK source — it came from the flags API response.
      mutableFlags.put(
          def.flagKey,
          def.pendingVariant.withSource(MixpanelFlagVariant.Source.network()));
      mFlags = Collections.unmodifiableMap(mutableFlags);
    }
    // Deliberately not persisted: activations live only in memory for this session. The cache
    // stores the raw /flags/ response untouched, so on next app start the activated variant
    // briefly appears as still-pending until either (a) the user re-fires the activating event
    // or (b) the next /flags/ network response lands (which the server should return reflecting
    // the activation, since _fireRecordingAPI below tells it about the activation). The window
    // is small and the alternative (mutating the cache from this code path) was rejected to
    // keep the cache strictly a passive copy of what the server returned.

    // Fire recording API (fire-and-forget)
    _fireRecordingAPI(def);
  }

  /**
   * Submits a recording API call to the network executor.
   * Fire-and-forget - errors are logged but not propagated.
   */
  private void _fireRecordingAPI(FirstTimeEventDefinition def) {
    mNetworkExecutor.execute(() -> {
      try {
        _performRecordingRequest(def);
        MPLog.v(LOGTAG, "First-time event recorded successfully: " + def.getCompositeKey());
      } catch (RemoteService.ServiceUnavailableException e) {
        MPLog.w(LOGTAG, "Recording API failed for event " + def.getCompositeKey() +
                " - Service unavailable: " + e.getMessage() +
                ". Event tracked locally but server may not be notified.", e);
      } catch (IOException e) {
        MPLog.w(LOGTAG, "Recording API failed for event " + def.getCompositeKey() +
                " - Network error: " + e.getMessage() +
                ". Event tracked locally but server may not be notified.", e);
      } catch (JSONException e) {
        MPLog.e(LOGTAG, "Recording API failed for event " + def.getCompositeKey() +
                " - Invalid request data: " + e.getMessage() +
                ". This may indicate a bug.", e);
      } catch (Exception e) {
        MPLog.e(LOGTAG, "Recording API failed for event " + def.getCompositeKey() +
                " - Unexpected error: " + e.getClass().getName() + ": " + e.getMessage(), e);
      }
    });
  }

  /**
   * Performs the HTTP POST request to record the first-time event activation.
   * Runs on Network Executor thread.
   * @throws IOException on network errors
   * @throws JSONException on JSON construction errors
   * @throws RemoteService.ServiceUnavailableException on service unavailability
   */
  private void _performRecordingRequest(FirstTimeEventDefinition def) throws IOException, JSONException, RemoteService.ServiceUnavailableException {
    final FeatureFlagDelegate delegate = mDelegate.get();
    if (delegate == null) {
      MPLog.w(LOGTAG, "Delegate is null, cannot record first-time event");
      return;
    }

    // Build endpoint URL using the flags recording endpoint
    String endpoint = mFlagsRecordingEndpoint + def.flagId + "/first-time-events";

    // Build request body
    JSONObject body = new JSONObject();
    body.put("distinct_id", delegate.getDistinctId());
    body.put("project_id", def.projectId);
    body.put("first_time_event_hash", def.firstTimeEventHash);

    byte[] requestBodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);

    // Build headers with Basic auth
    String token = delegate.getToken();
    String authString = token + ":";
    String base64Auth = Base64Coder.encodeString(authString);

    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Basic " + base64Auth);
    headers.put("Content-Type", "application/json; charset=utf-8");
    headers.put("traceparent", W3CTraceContext.generateTraceparent());

    // Perform POST request
    RemoteService.RequestResult result = mHttpService.performRequest(
            RemoteService.HttpMethod.POST,
            endpoint,
            delegate.getMPConfig().getProxyServerInteractor(),
            null, // No URL params
            headers,
            requestBodyBytes,
            delegate.getMPConfig().getSSLSocketFactory()
    );

    if (!result.isSuccess()) {
      MPLog.w(LOGTAG, "Recording API returned non-success response for event " + def.getCompositeKey());
    }
  }

  /**
   * Parses the pending_first_time_events array from the flags API response.
   * Returns a map keyed by composite key (flag_key:first_time_event_hash).
   */
  private Map<String, FirstTimeEventDefinition> parsePendingFirstTimeEvents(JSONObject response) {
    Map<String, FirstTimeEventDefinition> result = new HashMap<>();

    try {
      if (!response.has(MPConstants.Flags.PENDING_FIRST_TIME_EVENTS)) {
        return result; // No pending events in response
      }

      JSONArray pendingArray = response.getJSONArray(MPConstants.Flags.PENDING_FIRST_TIME_EVENTS);
      for (int i = 0; i < pendingArray.length(); i++) {
        try {
          JSONObject eventObj = pendingArray.getJSONObject(i);

          // Extract required fields
          String flagKey = eventObj.getString(MPConstants.Flags.FLAG_KEY);
          String flagId = eventObj.getString(MPConstants.Flags.FLAG_ID);
          Long projectId = eventObj.getLong(MPConstants.Flags.PROJECT_ID);
          String firstTimeEventHash = eventObj.getString(MPConstants.Flags.FIRST_TIME_EVENT_HASH);
          String eventName = eventObj.getString(MPConstants.Flags.EVENT_NAME);

          // Extract optional property filters
          JSONObject propertyFilters = eventObj.optJSONObject(MPConstants.Flags.PROPERTY_FILTERS);

          // Parse pending variant
          JSONObject pendingVariantObj = eventObj.getJSONObject(MPConstants.Flags.PENDING_VARIANT);
          MixpanelFlagVariant pendingVariant = JsonUtils.parseFlagVariant(pendingVariantObj);

          // Create definition
          FirstTimeEventDefinition def = new FirstTimeEventDefinition(
                  flagKey,
                  flagId,
                  projectId,
                  firstTimeEventHash,
                  eventName,
                  propertyFilters,
                  pendingVariant
          );

          result.put(def.getCompositeKey(), def);
        } catch (Exception e) {
          MPLog.e(LOGTAG, "Failed to parse pending first-time event at index " + i + ": " + e.getMessage());
          // Skip this invalid event, continue with others
        }
      }

      MPLog.d(LOGTAG, "Parsed " + result.size() + " pending first-time events");
    } catch (JSONException e) {
      MPLog.e(LOGTAG, "Failed to parse pending_first_time_events array: " + e.getMessage());
    }

    return result;
  }

  /**
   * Merges newly fetched flags and pending events with activated events to preserve session state.
   * Modifies the flags map in-place to preserve activated flag variants.
   */
  private void mergeWithActivatedEvents(
          Map<String, MixpanelFlagVariant> flags,
          Map<String, FirstTimeEventDefinition> newPendingEvents) {

    // For each activated composite key, preserve the activated flag variant
    for (String activatedCompositeKey : mActivatedFirstTimeEvents) {
      // Extract flag key from composite key (format: "flag_key:hash")
      // Use defensive parsing to avoid ArrayIndexOutOfBoundsException
      int colonIndex = activatedCompositeKey.indexOf(":");
      if (colonIndex == -1) {
        MPLog.w(LOGTAG, "Malformed composite key (missing colon): " + activatedCompositeKey);
        continue;
      }
      String flagKey = activatedCompositeKey.substring(0, colonIndex);

      // Check if this flag exists in current mFlags (activated variant)
      MixpanelFlagVariant activatedVariant;
      synchronized (mLock) {
        if (mFlags != null && mFlags.containsKey(flagKey)) {
          activatedVariant = mFlags.get(flagKey);
        } else {
          continue; // No activated variant to preserve
        }
      }

      // Preserve this activated variant in the new flags map
      flags.put(flagKey, activatedVariant);

      // Remove this event from newPendingEvents if present (already activated)
      newPendingEvents.remove(activatedCompositeKey);
    }
  }

  // --- Persistence Helpers (run on Handler thread) ---

  /**
   * Loads the cached /flags/ response from storage and parses it into mFlags +
   * mPendingFirstTimeEvents. Both CacheFirst and NetworkFirst populate mFlags directly so that
   * sync lookups and {@link #areFlagsReady()} reflect the cache. The difference between policies
   * is enforced at async-lookup time via {@link #mAwaitingInitialNetworkResponse}: NetworkFirst
   * sets it true so async lookups await the network call before serving, per the ERD.
   */
  private void _loadCachedVariants() {
    final CachedFlagsResponse cached = readCacheFromDisk();
    if (cached == null) {
      return;
    }
    Map<String, MixpanelFlagVariant> parsed = JsonUtils.parseFlagsResponse(cached.response);
    Map<String, MixpanelFlagVariant> stamped = stampSource(
        parsed,
        MixpanelFlagVariant.Source.cache(cached.cachedAtMillis));
    synchronized (mLock) {
      // Defer to network values if a fetch already raced ahead of us.
      if (mFlags == null) {
        mFlags = Collections.unmodifiableMap(stamped);
        mPendingFirstTimeEvents = parsePendingFirstTimeEvents(cached.response);
        if (mFlagsConfig.variantLookupPolicy instanceof VariantLookupPolicy.NetworkFirst) {
          mAwaitingInitialNetworkResponse = true;
        }
        MPLog.v(LOGTAG, "Loaded " + stamped.size() + " cached variants into memory.");
      }
    }
  }

  /**
   * Reads, validates, and returns the cached /flags/ response from disk, or {@code null} if
   * nothing is cached, the fingerprint doesn't match the current user/context, the entry is
   * past TTL, or the blob is unparseable. Malformed blobs are cleared as a side effect so we
   * don't get stuck rejecting them on every call.
   *
   * <p>Runs on the handler thread (called from {@code _loadCachedVariants}).
   */
  @Nullable
  private CachedFlagsResponse readCacheFromDisk() {
    if (mCachePrefs == null) {
      return null;
    }
    final SharedPreferences prefs = awaitCachePrefs();
    if (prefs == null) {
      return null;
    }
    final String raw = prefs.getString(CACHE_BLOB_KEY, null);
    if (raw == null) {
      return null;
    }
    final FeatureFlagDelegate delegate = mDelegate.get();
    final String currentDistinctId = delegate == null ? null : delegate.getDistinctId();
    if (currentDistinctId == null) {
      // No identity to validate against — can't trust the cache.
      return null;
    }
    try {
      JSONObject blob = new JSONObject(raw);
      String storedDistinctId = blob.optString(CACHE_FIELD_DISTINCT_ID, null);
      if (!currentDistinctId.equals(storedDistinctId)) {
        MPLog.d(LOGTAG, "Cached flags belong to a different distinct_id; ignoring cache.");
        return null;
      }
      long cachedAt = blob.optLong(CACHE_FIELD_CACHED_AT, 0L);
      long ttl = cacheTtlMillis();
      if (ttl > 0 && cachedAt > 0 && (System.currentTimeMillis() - cachedAt) > ttl) {
        MPLog.d(LOGTAG, "Cached flags expired; ignoring cache.");
        return null;
      }
      JSONObject response = blob.optJSONObject(CACHE_FIELD_RESPONSE);
      if (response == null) {
        return null;
      }
      return new CachedFlagsResponse(response, cachedAt);
    } catch (JSONException e) {
      MPLog.w(LOGTAG, "Failed to parse cached flags blob; clearing.", e);
      prefs.edit().remove(CACHE_BLOB_KEY).apply();
      return null;
    } catch (Exception e) {
      MPLog.e(LOGTAG, "Unexpected error loading cached flags", e);
      return null;
    }
  }

  /**
   * Writes the given /flags/ response to disk under the current distinct_id, wrapped in the
   * cachedAt + distinctId envelope. No-op (logs) on write failure.
   */
  private void writeCacheToDisk(@NonNull JSONObject response) {
    final SharedPreferences prefs = awaitCachePrefs();
    if (prefs == null) {
      return;
    }
    final FeatureFlagDelegate delegate = mDelegate.get();
    final String distinctId = delegate == null ? null : delegate.getDistinctId();
    if (distinctId == null) {
      // Nothing to key the cache under — don't write a blob the next reader can't validate.
      return;
    }
    try {
      JSONObject blob = new JSONObject();
      blob.put(CACHE_FIELD_CACHED_AT, System.currentTimeMillis());
      blob.put(CACHE_FIELD_DISTINCT_ID, distinctId);
      blob.put(CACHE_FIELD_RESPONSE, response);
      prefs.edit().putString(CACHE_BLOB_KEY, blob.toString()).apply();
    } catch (Exception e) {
      MPLog.e(LOGTAG, "Failed to cache flags response", e);
    }
  }

  private void clearCacheOnDisk() {
    final SharedPreferences prefs = awaitCachePrefs();
    if (prefs != null) {
      prefs.edit().remove(CACHE_BLOB_KEY).apply();
    }
  }

  @Nullable
  private SharedPreferences awaitCachePrefs() {
    if (mCachePrefs == null) {
      return null;
    }
    try {
      return mCachePrefs.get();
    } catch (Exception e) {
      MPLog.e(LOGTAG, "Failed to load SharedPreferences for cached flags", e);
      return null;
    }
  }

  /**
   * Returns the configured TTL in milliseconds, or 0 to disable expiry checks. NetworkOnly
   * is unreachable here (no cache prefs would have been provided) but treated as no-cache
   * defensively.
   */
  private long cacheTtlMillis() {
    VariantLookupPolicy policy = mFlagsConfig.variantLookupPolicy;
    if (policy instanceof VariantLookupPolicy.CacheFirst) {
      return ((VariantLookupPolicy.CacheFirst) policy).cacheTtlMillis;
    }
    if (policy instanceof VariantLookupPolicy.NetworkFirst) {
      return ((VariantLookupPolicy.NetworkFirst) policy).cacheTtlMillis;
    }
    return 0L;
  }

  /**
   * Two-tuple holding a parsed /flags/ response body and the timestamp at which it was cached.
   * Used both as the in-memory NetworkFirst fallback and as the return type of
   * {@link #readCacheFromDisk()}.
   */
  private static final class CachedFlagsResponse {
    @NonNull final JSONObject response;
    final long cachedAtMillis;

    CachedFlagsResponse(@NonNull JSONObject response, long cachedAtMillis) {
      this.response = response;
      this.cachedAtMillis = cachedAtMillis;
    }
  }


  /**
   * Returns a new map whose values are copies of the input variants stamped with the given
   * source metadata. Input map is not mutated.
   */
  @NonNull
  private static Map<String, MixpanelFlagVariant> stampSource(
      @NonNull Map<String, MixpanelFlagVariant> in,
      @NonNull MixpanelFlagVariant.Source source) {
    Map<String, MixpanelFlagVariant> out = new HashMap<>(in.size());
    for (Map.Entry<String, MixpanelFlagVariant> entry : in.entrySet()) {
      out.put(entry.getKey(), entry.getValue().withSource(source));
    }
    return out;
  }
}
