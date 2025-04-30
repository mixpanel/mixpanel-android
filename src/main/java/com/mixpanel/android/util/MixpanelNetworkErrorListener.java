package com.mixpanel.android.util;

public interface MixpanelNetworkErrorListener {
    /**
     * Called when a network request within the Mixpanel SDK fails.
     * This method may be called on a background thread.
     *
     * @param endpointUrl The URL that failed.
     * @param exception The exception that occurred (e.g., IOException, ServiceUnavailableException).
     */
    void onNetworkError(String endpointUrl, Exception exception);
}