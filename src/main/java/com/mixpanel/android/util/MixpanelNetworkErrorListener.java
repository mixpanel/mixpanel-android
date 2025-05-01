package com.mixpanel.android.util;

public interface MixpanelNetworkErrorListener {
    /**
     * Called when a network request within the Mixpanel SDK fails.
     * This method may be called on a background thread.
     *
     * @param endpointUrl The URL that failed.
     * @param ipAddress The IP address resolved from the endpointUrl's hostname for this attempt (may be null if DNS lookup failed).
     * @param durationMillis The approximate duration in milliseconds from the start of this specific connection attempt until the exception was thrown.
     * @param exception The exception that occurred (e.g., IOException, EOFException, etc.).
     */
    void onNetworkError(String endpointUrl, String ipAddress, long durationMillis, Exception exception);
}
