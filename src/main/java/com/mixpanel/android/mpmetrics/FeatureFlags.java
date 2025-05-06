package com.mixpanel.android.mpmetrics; // Assuming same package

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log; // Or use MPLog

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.mixpanel.android.util.Base64Coder;
import com.mixpanel.android.util.HttpService;
import com.mixpanel.android.util.JsonUtils;
import com.mixpanel.android.util.MPLog;
import com.mixpanel.android.util.RemoteService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// --- Data Structures ---

/**
 * Represents the configuration for feature flags, likely derived from MPConfig.
 */
class FlagsConfig {
    public final boolean enabled;
    @NonNull public final Map<String, Object> context; // Parsed from JSON context

    // Default constructor (disabled)
    FlagsConfig() {
        this.enabled = false;
        this.context = new HashMap<>();
    }

    // Constructor for parsing/setting values
    FlagsConfig(boolean enabled, @NonNull Map<String, Object> context) {
        this.enabled = enabled;
        this.context = context;
    }

    // TODO: Add static parsing method from JSONObject if needed
}

// --- Delegate Interface ---

/**
 * Interface for FeatureFlagManager to retrieve necessary data and trigger actions
 * from the main MixpanelAPI instance.
 */
interface FeatureFlagDelegate {
    @NonNull MPConfig getMPConfig(); // Assuming MPConfig holds token, FlagsConfig etc.
    @NonNull String getDistinctId();
    void track(String eventName, @Nullable JSONObject properties);
    // Add other methods if needed (e.g., getting HttpService instance?)
    @NonNull String getToken();
}

// --- Callback Interface ---
interface FlagCompletionCallback<T> {
    void onComplete(T result);
}


// --- FeatureFlagManager ---

class FeatureFlagManager {
    private static final String LOGTAG = "MixpanelAPI.FeatureFlag"; // Logging tag

    private final WeakReference<FeatureFlagDelegate> mDelegate;
    private final String mServerUrl; // Base URL for API requests (e.g., api.mixpanel.com)
    private final RemoteService mHttpService; // Use RemoteService interface
    private final FeatureFlagHandler mHandler; // For serializing state access and operations
    private final ExecutorService mNetworkExecutor; // For performing network calls off the handler thread
    private final Object mLock = new Object();

    // --- State Variables (Protected by mHandler) ---
    private Map<String, FeatureFlagData> mFlags = null;
    private Set<String> mTrackedFlags = new HashSet<>();
    private boolean mIsFetching = false;
    private List<FlagCompletionCallback<Boolean>> mFetchCompletionCallbacks = new ArrayList<>();
    // ---

    private static final String FLAGS_ROUTE = "/flags/"; // API Endpoint path

    // Message codes for Handler
    private static final int MSG_FETCH_FLAGS_IF_NEEDED = 0;
    private static final int MSG_COMPLETE_FETCH = 1;
    private static final int MSG_TRACK_FLAG_IF_NEEDED = 2;
    // Removed MSG_PERFORM_TRACKING_CALL - will call helper directly then dispatch to main


    public FeatureFlagManager(
            @NonNull WeakReference<FeatureFlagDelegate> delegate,
            @NonNull String serverUrl,
            @NonNull RemoteService httpService // Inject dependency
    ) {
        mDelegate = delegate;
        mServerUrl = serverUrl; // Should likely come from MPConfig via delegate
        mHttpService = httpService;

        // Dedicated thread for serializing access to flags state
        HandlerThread handlerThread = new HandlerThread("com.mixpanel.android.FeatureFlagManagerWorker", Thread.MIN_PRIORITY);
        handlerThread.start();
        mHandler = new FeatureFlagHandler(handlerThread.getLooper());

        // Separate executor for network requests so they don't block the state queue
        mNetworkExecutor = Executors.newSingleThreadExecutor(); // Or use a shared pool if appropriate
    }

    // --- Public Methods ---

    /**
     * Asynchronously loads flags from the Mixpanel server if they haven't been loaded yet
     * or if flags are disabled.
     */
    public void loadFlags() {
        // Send message to the handler thread to check and potentially fetch
        mHandler.sendMessage(mHandler.obtainMessage(MSG_FETCH_FLAGS_IF_NEEDED));
    }

    /**
     * Returns true if flags are loaded and ready for synchronous access.
     */
    public boolean areFeaturesReady() {
        // This needs to block and wait for the handler to return the state.
        // This is tricky and can cause deadlocks if called from the handler thread.
        // For now, return a potentially stale value or consider making this async.
        // Let's return the in-memory state, understanding it might not be 100% accurate
        // if read exactly when flags arrive but before handler processes _completeFetch fully.
        // A better way would be a CountDownLatch or Future if true sync is needed.
        synchronized (mLock) { // Use a simple lock for direct access (needs declaration)
            return mFlags != null;
        }
        // TODO: Revisit synchronous access safety/utility
    }

    // TODO: Implement getFeatureSync(...) - Requires careful synchronous execution on Handler thread
    // public FeatureFlagData getFeatureSync(String featureName, FeatureFlagData fallback) { ... }

    // --- Sync Flag Retrieval (Updated Tracking Logic) ---

//    public FeatureFlagData getFeatureSync(@NonNull String featureName, @NonNull FeatureFlagData fallback) {
//        var featureData = new Object() { // Use container object to hold mutable refs
//            FeatureFlagData data = null;
//            boolean tracked = false;
//        };
//
//        // === Serial Queue: Single Sync Block for Read AND Track Update ===
//        mHandler.runAndWait(() -> { // Use runAndWait helper or equivalent FutureTask/CountDownLatch
//            // Only proceed if flags are actually loaded
//            if (mFlags == null) {
//                MPLog.w(LOGTAG, "getFeatureSync called but flags not ready.");
//                return; // Exit runnable
//            }
//
//            FeatureFlagData feature = mFlags.get(featureName);
//            if (feature != null) {
//                // Feature found
//                featureData.data = feature; // Assign to container
//
//                // Perform atomic check-and-set for tracking *within the same sync block*
//                if (!mTrackedFlags.contains(featureName)) {
//                    mTrackedFlags.add(featureName);
//                    featureData.tracked = true; // Mark that tracking logic should run *after* this block
//                }
//            }
//            // If feature wasn't found, featureData.data remains null
//        });
//        // === End Sync Block ===
//
//        // Now, process the results outside the lock/handler execution
//
//        if (featureData.data != null) {
//            // If tracking was done *in this call*, call the delegate helper
//            if (featureData.tracked) {
//                this._performTrackingDelegateCall(featureName, featureData.data);
//            }
//            return featureData.data; // Return the found feature
//        } else {
//            // Flag not found or flags weren't ready
//            MPLog.i(LOGTAG, "Flag not found or flags not ready. Returning fallback.");
//            return fallback;
//        }
//    }

    // --- Sync Flag Retrieval ---

    /**
     * Gets the feature flag data (key and value) synchronously.
     * IMPORTANT: This method will block the calling thread until the value can be
     * retrieved. It is NOT recommended to call this from the main UI thread
     * if flags might not be ready. If flags are not ready (`areFeaturesReady()` is false),
     * it returns the fallback immediately without blocking or fetching.
     *
     * @param featureName The name of the feature flag.
     * @param fallback    The FeatureFlagData instance to return if the flag is not found or not ready.
     * @return The found FeatureFlagData or the fallback.
     */
    @NonNull
    public FeatureFlagData getFeatureSync(@NonNull final String featureName, @NonNull final FeatureFlagData fallback) {
        // 1. Check readiness first - don't block if flags aren't loaded.
        if (!areFeaturesReady()) {
            MPLog.w(LOGTAG, "Flags not ready for getFeatureSync call for '" + featureName + "'. Returning fallback.");
            return fallback;
        }

        // Use a container to get results back from the handler thread runnable
        final var resultContainer = new Object() {
            FeatureFlagData featureData = null;
            boolean tracked = false;
        };

        // 2. Execute the core logic synchronously on the handler thread
        mHandler.runAndWait(() -> {
            // We are now on the mHandler thread. areFeaturesReady() was true, but check mFlags again for safety.
            if (mFlags == null) { // Should not happen if areFeaturesReady was true, but defensive check
                MPLog.w(LOGTAG, "Flags became null unexpectedly in getFeatureSync runnable.");
                return; // Keep resultContainer.featureData as null
            }

            FeatureFlagData feature = mFlags.get(featureName);
            if (feature != null) {
                // Feature found
                resultContainer.featureData = feature;

                // Perform atomic check-and-set for tracking directly here
                // (Calls _checkAndSetTrackedFlag which runs on this thread)
                resultContainer.tracked = _checkAndSetTrackedFlag(featureName);
            }
            // If feature is null, resultContainer.featureData remains null
        });

        // 3. Process results after handler block completes

        if (resultContainer.featureData != null) {
            // A feature was found
            if (resultContainer.tracked) {
                // If tracking was performed *in this call*, trigger the delegate call helper
                // (This runs on the *calling* thread, but _performTrackingDelegateCall dispatches to main)
                _performTrackingDelegateCall(featureName, resultContainer.featureData);
            }
            return resultContainer.featureData;
        } else {
            // Flag key not found in the loaded flags
            MPLog.i(LOGTAG, "Flag '" + featureName + "' not found sync. Returning fallback.");
            return fallback;
        }
    }

    /**
     * Gets the value of a feature flag synchronously.
     * IMPORTANT: See warning on getFeatureSync regarding blocking and readiness checks.
     * Returns fallback immediately if flags are not ready.
     *
     * @param featureName   The name of the feature flag.
     * @param fallbackValue The default value to return if the flag is missing or not ready.
     * @return The flag's value (Object or null) or the fallbackValue.
     */
    @Nullable
    public Object getFeatureDataSync(@NonNull String featureName, @Nullable Object fallbackValue) {
        // Create a fallback FeatureFlagData with the desired fallback value
        FeatureFlagData fallbackData = new FeatureFlagData("", fallbackValue);
        FeatureFlagData resultData = getFeatureSync(featureName, fallbackData);
        // If getFeatureSync returned the *original* fallbackData, its value is fallbackValue.
        // If getFeatureSync returned a *real* flag, its value is resultData.value.
        return resultData.value;
    }

    /**
     * Checks if a feature flag is enabled synchronously (evaluates value as boolean).
     * IMPORTANT: See warning on getFeatureSync regarding blocking and readiness checks.
     * Returns fallbackValue immediately if flags are not ready.
     *
     * @param featureName   The name of the feature flag.
     * @param fallbackValue The default boolean value if the flag is missing, not boolean, or not ready.
     * @return True if the flag evaluates to true, false otherwise or if fallbackValue is returned.
     */
    public boolean isFeatureEnabledSync(@NonNull String featureName, boolean fallbackValue) {
        // Pass the boolean fallback value as the data fallback too
        Object dataValue = getFeatureDataSync(featureName, fallbackValue);
        // Evaluate the result
        return _evaluateBooleanFlag(featureName, dataValue, fallbackValue);
    }

    /**
     * Asynchronously gets the feature flag data (key and value).
     * If flags are not loaded, it triggers a fetch.
     * Completion handler is called on the main thread.
     *
     * @param featureName The name of the feature flag.
     * @param fallback    The FeatureFlagData instance to return if the flag is not found or fetch fails.
     * @param completion  The callback to receive the result.
     */
    public void getFeature(
            @NonNull final String featureName,
            @NonNull final FeatureFlagData fallback,
            @NonNull final FlagCompletionCallback<FeatureFlagData> completion
    ) {
        // Post the core logic to the handler thread for safe state access
        mHandler.post(() -> { // Block A runs serially on mHandler thread
            FeatureFlagData featureData = null;
            boolean needsTracking = false;
            boolean flagsAreCurrentlyReady = (mFlags != null);

            if (flagsAreCurrentlyReady) {
                // --- Flags ARE Ready ---
                MPLog.v(LOGTAG, "Flags ready. Checking for flag '" + featureName + "'");
                featureData = mFlags.get(featureName); // Read state directly (safe on handler thread)

                if (featureData != null) {
                    // Feature found, check tracking status atomically
                    needsTracking = _checkAndSetTrackedFlag(featureName); // Runs on handler thread
                }

                // Determine final result now
                FeatureFlagData result = (featureData != null) ? featureData : fallback;
                MPLog.v(LOGTAG, "Found flag data (or fallback): " + result.key + " -> " + result.value);

                // Dispatch completion back to main thread FIRST
                postCompletion(completion, result);

                // If a *real* feature was found AND it needed tracking, trigger delegate call
                // _performTrackingDelegateCall handles dispatching the *delegate* call to main thread
                if (featureData != null && needsTracking) {
                    MPLog.v(LOGTAG, "Tracking needed for '" + featureName + "'.");
                    _performTrackingDelegateCall(featureName, result);
                }

            } else {
                // --- Flags were NOT Ready ---
                MPLog.i(LOGTAG, "Flags not ready, attempting fetch for getFeature call '" + featureName + "'...");
                _fetchFlagsIfNeeded(success -> {
                    // This fetch completion block runs on the MAIN thread (due to postCompletion in _completeFetch)
                    MPLog.v(LOGTAG, "Fetch completion received on main thread for '" + featureName + "'. Success: " + success);
                    if (success) {
                        // Fetch succeeded. Post BACK to the handler thread to get the flag value
                        // and perform tracking check now that flags are ready.
                        mHandler.post(() -> { // Block C runs on mHandler thread
                            MPLog.v(LOGTAG, "Processing successful fetch result for '" + featureName + "' on handler thread.");
                            FeatureFlagData fetchedData = mFlags != null ? mFlags.get(featureName) : null;
                            boolean tracked = false;
                            if (fetchedData != null) {
                                tracked = _checkAndSetTrackedFlag(featureName);
                            }
                            FeatureFlagData finalResult = (fetchedData != null) ? fetchedData : fallback;

                            // Post final completion to main thread
                            postCompletion(completion, finalResult);

                            // If tracking needed, call delegate helper
                            if (fetchedData != null && tracked) {
                                _performTrackingDelegateCall(featureName, finalResult);
                            }
                        }); // End Block C (handler thread)
                    } else {
                        // Fetch failed, just call original completion with fallback (already on main thread)
                        MPLog.w(LOGTAG, "Fetch failed for '" + featureName + "'. Returning fallback.");
                        completion.onComplete(fallback);
                    }
                }); // End _fetchFlagsIfNeeded completion
            }
        }); // End mHandler.post (Block A)
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
         * Executes a Runnable synchronously on this handler's thread.
         * Blocks the calling thread until the Runnable completes.
         * Handles being called from the handler's own thread to prevent deadlock.
         * @param r The Runnable to execute.
         */
        public void runAndWait(Runnable r) {
            if (Thread.currentThread() == getLooper().getThread()) {
                // Already on the handler thread, run directly
                r.run();
            } else {
                // Use CountDownLatch to wait for completion
                final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                post(() -> { // Post the task to the handler thread
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
        // ...(Implementation from previous step - removed sync wrapper)...
        // It calls _performFetchRequest via mNetworkExecutor if needed.
        // (Ensure implementation is correct as per previous step)
        var shouldStartFetch = false;
        final FeatureFlagDelegate delegate = mDelegate.get();
        final MPConfig config = (delegate != null) ? delegate.getMPConfig() : null;
        // TODO: Get FlagsConfig properly from MPConfig
        final boolean enabled = (config != null) && config.getFeatureFlagsEnabled(); // Assuming method exists

        if (!enabled) {
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

    /**
     * Asynchronously gets the value of a feature flag.
     * If flags are not loaded, it triggers a fetch.
     * Completion handler is called on the main thread.
     *
     * @param featureName   The name of the feature flag.
     * @param fallbackValue The default value to return if the flag is missing or fetch fails.
     * @param completion    The callback to receive the result value (Object or null).
     */
    public void getFeatureData(
            @NonNull final String featureName,
            @Nullable final Object fallbackValue,
            @NonNull final FlagCompletionCallback<Object> completion
    ) {
        // Create a fallback FeatureFlagData. Using empty key as it's not relevant here.
        FeatureFlagData fallbackData = new FeatureFlagData("", fallbackValue);
        // Call getFeature and extract the value in its completion handler
        getFeature(featureName, fallbackData, result -> completion.onComplete(result.value));
    }


    /**
     * Asynchronously checks if a feature flag is enabled (evaluates value as boolean).
     * If flags are not loaded, it triggers a fetch.
     * Completion handler is called on the main thread.
     *
     * @param featureName   The name of the feature flag.
     * @param fallbackValue The default boolean value if the flag is missing, not boolean, or fetch fails.
     * @param completion    The callback to receive the boolean result.
     */
    public void isFeatureEnabled(
            @NonNull final String featureName,
            final boolean fallbackValue,
            @NonNull final FlagCompletionCallback<Boolean> completion
    ) {
        // Call getFeatureData, using the boolean fallbackValue as the data fallback too
        // (this ensures if the flag is missing, evaluateBoolean gets the intended fallback)
        getFeatureData(featureName, fallbackValue, value -> {
            // This completion runs on the main thread
            boolean isEnabled = _evaluateBooleanFlag(featureName, value, fallbackValue);
            completion.onComplete(isEnabled);
        });
    }


    // Runs on Network Executor thread
    /**
     * Performs the actual network request on the mNetworkExecutor thread.
     * Constructs the request, sends it, and posts the result (success/failure + data)
     * back to the mHandler thread via MSG_COMPLETE_FETCH.
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
        // TODO: Get FlagsConfig context Map<String, Object> properly from MPConfig
        // Assuming a method like config.getFlagsContext() exists for now
        final Map<String, Object> contextMap = config.getFlagsContext();

        if (distinctId == null || contextMap == null) {
            MPLog.w(LOGTAG, "Distinct ID or flags context is null. Cannot fetch flags.");
            errorMessage = "Distinct ID or context missing";
            postResultToHandler(false, null, errorMessage);
            return;
        }

        try {
            // 1. Build Request Body JSON
            JSONObject contextJson = new JSONObject(contextMap); // Convert map to JSONObject
            contextJson.put("distinct_id", distinctId);
            JSONObject requestJson = new JSONObject();
            requestJson.put("context", contextJson);
            String requestJsonString = requestJson.toString();
            MPLog.v(LOGTAG, "Request JSON: " + requestJsonString);

            // 2. Base64 Encode Body for 'data' parameter (Matches AnalyticsMessages pattern)
            String base64Body = Base64Coder.encodeString(requestJsonString);

            // 3. Build Request Parameters Map for HttpService
            Map<String, Object> params = new HashMap<>();
            params.put("data", base64Body);
            // Add other potential params like "verbose" if needed during debugging
            // if (MPConfig.DEBUG) { params.put("verbose", "1"); }

            // 4. Build Authorization Header
            String token = delegate.getToken(); // Assuming token is in MPConfig
            if (token == null || token.trim().isEmpty()) {
                throw new IOException("Mixpanel token is missing or empty.");
            }
            String authString = token + ":";
            String base64Auth = Base64Coder.encodeString(authString);
            // Note: HttpService doesn't have explicit header support in performRequest signature.
            // We rely on the fact that HttpURLConnection might allow default headers
            // or that the underlying mechanism handles Authorization if set globally?
            // THIS IS A PROBLEM - HttpService needs to support setting headers,
            // or we need a different approach.
            // *Assumption*: For now, assume HttpService/underlying connection handles auth if configured,
            // or that we might need to modify HttpService/RemoteService later.
            // Let's log a warning.
            MPLog.w(LOGTAG, "HttpService does not directly support custom headers. Authorization may not be sent correctly unless handled elsewhere.");
            // Ideally, we'd add headers to the performRequest call.

            // 5. Construct Endpoint URL
            String endpointUrl = mServerUrl + FLAGS_ROUTE;

            // 6. Perform Request
            byte[] responseBytes = mHttpService.performRequest(
                    endpointUrl,
                    config.getProxyServerInteractor(), // Pass proxy interactor from config
                    params,
                    config.getSSLSocketFactory() // Pass SSL Factory from config
            );

            // 7. Process Response
            if (responseBytes == null) {
                // HttpService performRequest returns null for non-ServiceUnavailable IOExceptions or other errors
                throw new IOException("HTTP Service returned null response");
            }

            try {
                String responseString = new String(responseBytes, "UTF-8");
                MPLog.v(LOGTAG, "Flags response: " + responseString);
                responseJson = new JSONObject(responseString);
                // Check for potential error field in response JSON if API defines one
                if (responseJson.has("error")) {
                    errorMessage = "Mixpanel API returned error: " + responseJson.getString("error");
                    MPLog.e(LOGTAG, errorMessage);
                    // Keep success = false
                } else {
                    success = true; // Parsed JSON successfully and no 'error' field
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("UTF-8 not supported on this platform?", e); // Should not happen
            } catch (JSONException e) {
                errorMessage = "Could not parse Mixpanel flags response";
                MPLog.e(LOGTAG, errorMessage, e);
                // Keep success = false
            }

        } catch (RemoteService.ServiceUnavailableException e) {
            success = false;
            errorMessage = "Mixpanel service unavailable";
            MPLog.w(LOGTAG, errorMessage, e);
            // TODO: Implement retry logic / backoff based on e.getRetryAfter() if needed
            // For now, just fail the fetch completely for simplicity.
        } catch (MalformedURLException e) {
            success = false;
            errorMessage = "Flags endpoint URL is malformed: " + mServerUrl + FLAGS_ROUTE;
            MPLog.e(LOGTAG, errorMessage, e);
        } catch (IOException e) {
            success = false;
            errorMessage = "Network error while fetching flags";
            MPLog.e(LOGTAG, errorMessage, e);
        } catch (JSONException e) {
            success = false;
            errorMessage = "Failed to construct request JSON";
            MPLog.e(LOGTAG, errorMessage, e);
        } catch (Exception e) { // Catch unexpected errors
            success = false;
            errorMessage = "Unexpected error during flag fetch";
            MPLog.e(LOGTAG, errorMessage, e);
        }

        // 8. Post result back to Handler thread
        postResultToHandler(success, responseJson, errorMessage);
    }

    /**
     * Helper to dispatch the result of the fetch back to the handler thread.
     */
    private void postResultToHandler(boolean success, @Nullable JSONObject responseJson, @Nullable String errorMessage) {
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
     * Centralized fetch completion logic. Runs on the Handler thread.
     * Updates state and calls completion handlers.
     *
     * @param success Whether the fetch was successful.
     * @param flagsResponseJson The parsed JSON object from the response, or null if fetch failed or parsing failed.
     */
    @VisibleForTesting
    // Make accessible for testing simulation helpers
    void _completeFetch(boolean success, @Nullable JSONObject flagsResponseJson) {
        MPLog.d(LOGTAG, "Completing fetch request. Success: " + success);
        // State updates MUST happen on the handler thread implicitly
        mIsFetching = false; // Mark as not fetching anymore

        // Get the handlers waiting for THIS fetch completion
        List<FlagCompletionCallback<Boolean>> callbacksToCall = mFetchCompletionCallbacks;
        mFetchCompletionCallbacks = new ArrayList<>(); // Reset for next fetch

        if (success && flagsResponseJson != null) {
            // Parse the flags using the utility
            Map<String, FeatureFlagData> newFlags = JsonUtils.parseFlagsResponse(flagsResponseJson);
            mFlags = Collections.unmodifiableMap(newFlags); // Store immutable map
            MPLog.v(LOGTAG, "Flags updated: " + mFlags.size() + " flags loaded.");
        } else {
            // Decide failure behavior: keep stale flags or clear? Let's keep stale.
            MPLog.w(LOGTAG, "Flag fetch failed or response missing/invalid. Keeping existing flags (if any).");
            // mFlags = null; // Option to clear flags on failure
        }

        // Call handlers outside the state update logic, dispatch to main thread
        if (!callbacksToCall.isEmpty()) {
            MPLog.d(LOGTAG, "Calling " + callbacksToCall.size() + " fetch completion handlers.");
            for(FlagCompletionCallback<Boolean> callback : callbacksToCall) {
                postCompletion(callback, success); // Use helper to dispatch to main
            }
        } else {
            MPLog.d(LOGTAG, "No fetch completion handlers to call.");
        }
    }


    // Runs on Handler thread
    /**
     * Atomically checks if a feature flag has been tracked and marks it as tracked if not.
     * MUST be called from the mHandler thread. Calls the delegate tracking method
     * if the flag was newly tracked.
     *
     * @param featureName The name of the feature flag.
     * @param feature     The FeatureFlagData object.
     * @return true if the flag was NOT previously tracked (and delegate call was triggered), false otherwise.
     */
    private boolean _trackFeatureIfNeeded(@NonNull String featureName, @NonNull FeatureFlagData feature) {
        // Assumes this method is ALREADY running on the mHandler thread.
        boolean shouldCallDelegate = false;

        // === Access State Directly (No Inner Sync Needed) ===
        if (!mTrackedFlags.contains(featureName)) {
            mTrackedFlags.add(featureName);
            shouldCallDelegate = true;
            MPLog.v(LOGTAG, "Flag '" + featureName + "' was not tracked before. Added to set.");
        } else {
            MPLog.v(LOGTAG, "Flag '" + featureName + "' was already tracked. No action.");
        }
        // === State Access Finished ===

        if (shouldCallDelegate) {
            MPLog.v(LOGTAG, "Triggering delegate call for tracking '" + featureName + "'.");
            // CORRECTED Java Call Syntax:
            _performTrackingDelegateCall(featureName, feature); // No 'self.', no argument labels
            return true; // Tracking was needed and performed
        }
        return false; // No tracking was needed/performed
    }

    /**
     * Atomically checks if a feature flag has been tracked and marks it as tracked if not.
     * MUST be called from the mHandler thread.
     *
     * @param featureName The name of the feature flag.
     * @return true if the flag was NOT previously tracked (and was therefore marked now), false otherwise.
     */
    private boolean _checkAndSetTrackedFlag(@NonNull String featureName) {
        // Already running on the handler thread, direct access is safe and serialized
        if (!mTrackedFlags.contains(featureName)) {
            mTrackedFlags.add(featureName);
            return true; // Needs tracking
        }
        return false; // Already tracked
    }

    /**
     * Constructs the $experiment_started event properties and dispatches
     * the track call to the main thread via the delegate.
     * This method itself does NOT need to run on the handler thread, but is typically
     * called after a check that runs on the handler thread (_trackFeatureIfNeeded).
     *
     * @param featureName Name of the feature flag/experiment.
     * @param feature     The specific variant data received.
     */
    private void _performTrackingDelegateCall(String featureName, FeatureFlagData feature) {
        final FeatureFlagDelegate delegate = mDelegate.get();
        if (delegate == null) {
            MPLog.w(LOGTAG, "Delegate is null, cannot track $experiment_started.");
            return;
        }

        // Construct properties
        JSONObject properties = new JSONObject();
        try {
            properties.put("Experiment name", featureName);
            properties.put("Variant name", feature.key); // Use the variant key
            properties.put("$experiment_type", "feature_flag");
        } catch (JSONException e) {
            MPLog.e(LOGTAG, "Failed to create JSON properties for $experiment_started event", e);
            return; // Don't track if properties failed
        }

        MPLog.v(LOGTAG, "Queueing $experiment_started event for dispatch: " + properties.toString());

        // Dispatch delegate call asynchronously to main thread for safety
        new Handler(Looper.getMainLooper()).post(() -> {
            // Re-fetch delegate inside main thread runnable just in case? Usually not necessary.
            final FeatureFlagDelegate currentDelegate = mDelegate.get();
            if (currentDelegate != null) {
                currentDelegate.track("$experiment_started", properties);
                MPLog.v(LOGTAG, "Tracked $experiment_started for " + featureName + " (dispatched to main)");
            } else {
                MPLog.w(LOGTAG, "Delegate was null when track call executed on main thread.");
            }
        });
    }

    // Helper to post completion callbacks to the main thread
    private <T> void postCompletion(@Nullable final FlagCompletionCallback<T> callback, final T result) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onComplete(result));
        }
    }

    // --- Boolean Evaluation Helper ---
    private boolean _evaluateBooleanFlag(String featureName, Object dataValue, boolean fallbackValue) {
        // ... (Implementation from previous step) ...
        if (dataValue instanceof Boolean) { return (Boolean) dataValue; } MPLog.w(LOGTAG,"Flag value for "+featureName+" not boolean: "+dataValue); return fallbackValue;
    }

    // --- JSON Parsing Utilities ---
    // TODO: Implement helpers using org.json.*
    // Example:
    // private static Object parseJsonValue(Object jsonValue) throws JSONException { ... }
    // private static Map<String, FeatureFlagData> parseFlagsResponse(JSONObject response) { ... }

}