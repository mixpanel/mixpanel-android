package com.mixpanel.android.mpmetrics;

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
import com.mixpanel.android.util.MPLog;
import com.mixpanel.android.util.RemoteService;
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
import org.json.JSONException;
import org.json.JSONObject;

class FeatureFlagManager implements MixpanelAPI.Flags {
  private static final String LOGTAG = "MixpanelAPI.FeatureFlagManager";

  private final WeakReference<FeatureFlagDelegate> mDelegate;
  private final FlagsConfig mFlagsConfig;
  private final String mFlagsEndpoint; // e.g. https://api.mixpanel.com/flags/
  private final RemoteService mHttpService; // Use RemoteService interface
  private final FeatureFlagHandler mHandler; // For serializing state access and operations
  private final ExecutorService
      mNetworkExecutor; // For performing network calls off the handler thread
  private final Object mLock = new Object();

  // --- State Variables (Protected by mHandler) ---
  private volatile Map<String, MixpanelFlagVariant> mFlags = null;
  private final Set<String> mTrackedFlags = new HashSet<>();
  private boolean mIsFetching = false;
  private List<FlagCompletionCallback<Boolean>> mFetchCompletionCallbacks = new ArrayList<>();
  // ---

  // Message codes for Handler
  private static final int MSG_FETCH_FLAGS_IF_NEEDED = 0;
  private static final int MSG_COMPLETE_FETCH = 1;

  public FeatureFlagManager(
      @NonNull FeatureFlagDelegate delegate,
      @NonNull RemoteService httpService,
      @NonNull FlagsConfig flagsConfig) {
    mDelegate = new WeakReference<>(delegate);
    mFlagsEndpoint = delegate.getMPConfig().getFlagsEndpoint();
    mHttpService = httpService;
    mFlagsConfig = flagsConfig;

    // Dedicated thread for serializing access to flags state
    HandlerThread handlerThread =
        new HandlerThread("com.mixpanel.android.FeatureFlagManagerWorker", Thread.MIN_PRIORITY);
    handlerThread.start();
    mHandler = new FeatureFlagHandler(handlerThread.getLooper());

    // Separate executor for network requests so they don't block the state queue
    mNetworkExecutor = Executors.newSingleThreadExecutor();
  }

  // --- Public Methods ---

  /** Asynchronously loads flags from the Mixpanel server if they haven't been loaded yet */
  public void loadFlags() {
    // Send message to the handler thread to check and potentially fetch
    mHandler.sendMessage(mHandler.obtainMessage(MSG_FETCH_FLAGS_IF_NEEDED));
  }

  /** Returns true if flags are loaded and ready for synchronous access. */
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
    final var resultContainer =
        new Object() {
          MixpanelFlagVariant flagVariant = null;
          boolean tracked = false;
        };

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
        () -> { // Block A: Initial processing, runs serially on mHandler thread
          MixpanelFlagVariant flagVariant;
          boolean needsTracking;
          boolean flagsAreCurrentlyReady = (mFlags != null);

          if (flagsAreCurrentlyReady) {
            // --- Flags ARE Ready ---
            MPLog.v(LOGTAG, "Flags ready. Checking for flag '" + flagName + "'");
            flagVariant = mFlags.get(flagName); // Read state directly (safe on handler thread)

            if (flagVariant != null) {
              needsTracking = _checkAndSetTrackedFlag(flagName); // Runs on handler thread
            } else {
              needsTracking = false;
            }

            MixpanelFlagVariant result = (flagVariant != null) ? flagVariant : fallback;
            MPLog.v(
                LOGTAG, "Found flag variant (or fallback): " + result.key + " -> " + result.value);

            // Dispatch completion and potential tracking to main thread
            new Handler(Looper.getMainLooper())
                .post(
                    () -> { // Block B: User completion and subsequent tracking logic, runs on Main
                            // Thread
                      completion.onComplete(result);
                      if (flagVariant != null && needsTracking) {
                        MPLog.v(LOGTAG, "Tracking needed for '" + flagName + "'.");
                        // _performTrackingDelegateCall handles its own main thread dispatch for the
                        // delegate.
                        _performTrackingDelegateCall(flagName, result);
                      }
                    }); // End Block B (Main Thread)

          } else {
            // --- Flags were NOT Ready ---
            MPLog.i(
                LOGTAG,
                "Flags not ready, attempting fetch for getVariant call '" + flagName + "'...");
            _fetchFlagsIfNeeded(
                success -> {
                  // This fetch completion block itself runs on the MAIN thread (due to
                  // postCompletion in _completeFetch)
                  MPLog.v(
                      LOGTAG,
                      "Fetch completion received on main thread for '"
                          + flagName
                          + "'. Success: "
                          + success);
                  if (success) {
                    // Fetch succeeded. Post BACK to the handler thread to get the flag value
                    // and perform tracking check now that flags are ready.
                    mHandler.post(
                        () -> { // Block C: Post-fetch processing, runs on mHandler thread
                          MPLog.v(
                              LOGTAG,
                              "Processing successful fetch result for '"
                                  + flagName
                                  + "' on handler thread.");
                          MixpanelFlagVariant fetchedVariant =
                              mFlags != null ? mFlags.get(flagName) : null;
                          boolean tracked;
                          if (fetchedVariant != null) {
                            tracked = _checkAndSetTrackedFlag(flagName);
                          } else {
                            tracked = false;
                          }
                          MixpanelFlagVariant finalResult =
                              (fetchedVariant != null) ? fetchedVariant : fallback;

                          // Dispatch final user completion and potential tracking to main thread
                          new Handler(Looper.getMainLooper())
                              .post(
                                  () -> { // Block D: User completion and subsequent tracking, runs
                                          // on Main Thread
                                    completion.onComplete(finalResult);
                                    if (fetchedVariant != null && tracked) {
                                      _performTrackingDelegateCall(flagName, finalResult);
                                    }
                                  }); // End Block D (Main Thread)
                        }); // End Block C (handler thread)
                  } else {
                    // Fetch failed, just call original completion with fallback (already on main
                    // thread)
                    MPLog.w(LOGTAG, "Fetch failed for '" + flagName + "'. Returning fallback.");
                    completion.onComplete(fallback);
                  }
                }); // End _fetchFlagsIfNeeded completion
            // No return here needed as _fetchFlagsIfNeeded's completion handles the original
            // callback
          }
        }); // End mHandler.post (Block A)
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
    var shouldStartFetch = false;

    if (!mFlagsConfig.enabled) {
      MPLog.i(LOGTAG, "Feature flags are disabled, not fetching.");
      postCompletion(completion, false);
      return;
    }

    if (!mIsFetching) {
      mIsFetching = true;
      shouldStartFetch = true;
      if (completion != null) {
        mFetchCompletionCallbacks.add(completion);
      }
    } else {
      MPLog.d(LOGTAG, "Fetch already in progress, queueing completion handler.");
      if (completion != null) {
        mFetchCompletionCallbacks.add(completion);
      }
    }

    if (shouldStartFetch) {
      MPLog.d(LOGTAG, "Starting flag fetch (dispatching network request)...");
      mNetworkExecutor.execute(this::_performFetchRequest);
    }
  }

  // Runs on Network Executor thread
  /**
   * Performs the actual network request on the mNetworkExecutor thread. Constructs the request,
   * sends it, and posts the result (success/failure + data) back to the mHandler thread via
   * MSG_COMPLETE_FETCH.
   */
  private void _performFetchRequest() {
    MPLog.v(LOGTAG, "Performing fetch request on thread: " + Thread.currentThread().getName());
    boolean success = false;
    JSONObject responseJson = null; // To hold parsed successful response
    String errorMessage = "Delegate or config not available"; // Default error

    final FeatureFlagDelegate delegate = mDelegate.get();
    if (delegate == null) {
      MPLog.w(LOGTAG, "Delegate became null before network request could start.");
      postResultToHandler(false, null, errorMessage);
      return;
    }

    final MPConfig config = delegate.getMPConfig();
    final String distinctId = delegate.getDistinctId();

    if (distinctId == null) {
      MPLog.w(LOGTAG, "Distinct ID is null. Cannot fetch flags.");
      errorMessage = "Distinct ID is null.";
      postResultToHandler(false, null, errorMessage);
      return;
    }

    try {
      // 1. Build Request Body JSON
      JSONObject contextJson = new JSONObject(mFlagsConfig.context.toString());
      contextJson.put("distinct_id", distinctId);
      JSONObject requestJson = new JSONObject();
      requestJson.put("context", contextJson);
      String requestJsonString = requestJson.toString();
      MPLog.v(LOGTAG, "Request JSON Body: " + requestJsonString);
      byte[] requestBodyBytes = requestJsonString.getBytes(StandardCharsets.UTF_8); // Get raw bytes

      // 3. Build Headers
      String token = delegate.getToken(); // Assuming token is in MPConfig
      if (token == null || token.trim().isEmpty()) {
        throw new IOException("Mixpanel token is missing or empty.");
      }
      String authString = token + ":";
      String base64Auth = Base64Coder.encodeString(authString);
      Map<String, String> headers = new HashMap<>();
      headers.put("Authorization", "Basic " + base64Auth);
      headers.put("Content-Type", "application/json; charset=utf-8"); // Explicitly set content type

      // 4. Perform Request
      byte[] responseBytes =
          mHttpService.performRequest( // <-- Use consolidated method
              mFlagsEndpoint,
              config.getProxyServerInteractor(),
              null, // Pass null for params when sending raw body
              headers,
              requestBodyBytes, // Pass raw JSON body bytes
              config.getSSLSocketFactory());

      // 5. Process Response
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

    // 6. Post result back to Handler thread
    postResultToHandler(success, responseJson, errorMessage);
  }

  /** Helper to dispatch the result of the fetch back to the handler thread. */
  private void postResultToHandler(
      boolean success, @Nullable JSONObject responseJson, @Nullable String errorMessage) {
    // Use a Bundle to pass multiple arguments efficiently
    android.os.Bundle resultData = new android.os.Bundle();
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

    if (success && flagsResponseJson != null) {
      Map<String, MixpanelFlagVariant> newFlags = JsonUtils.parseFlagsResponse(flagsResponseJson);
      synchronized (mLock) {
        mFlags = Collections.unmodifiableMap(newFlags);
      }
      MPLog.v(LOGTAG, "Flags updated: " + mFlags.size() + " flags loaded.");
    } else {
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
}
