package com.mixpanel.mixpaneldemo

import android.util.Log
import com.mixpanel.android.util.MixpanelNetworkErrorListener
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * A simple implementation of MixpanelNetworkErrorListener that logs
 * the encountered network errors using Android's Logcat.
 */
class SimpleLoggingErrorListener : MixpanelNetworkErrorListener {

    companion object {
        private const val TAG = "MixpanelNetworkError"
    }

    override fun onNetworkError(endpointUrl: String, ipAddress: String, durationMillis: Long, exception: Exception) {
        Log.w(TAG, "Mixpanel network error for endpoint: $endpointUrl (IP: $ipAddress, duration: $durationMillis ms)")

        when (exception) {
            // --- Specific SSL/TLS Issues ---
            is SSLPeerUnverifiedException -> {
                Log.e(TAG, "--> SSLPeerUnverifiedException occurred (Certificate validation issue?).", exception)
            }
            is SSLHandshakeException -> {
                Log.e(TAG, "--> SSLHandshakeException occurred (Handshake phase failure).", exception)
            }
            is SSLException -> {
                Log.e(TAG, "--> General SSLException occurred.", exception)
            }

            // --- Specific Connection/Network Issues ---
            is ConnectException -> {
                // TCP connection attempt failure (e.g., connection refused)
                Log.e(TAG, "--> ConnectException occurred (Connection refused/TCP layer issue?).", exception)
            }
            is SocketException -> {
                // Catch other socket-level errors (e.g., "Broken pipe", "Socket closed")
                Log.e(TAG, "--> SocketException occurred (Post-connection socket issue?).", exception)
            }
            is SocketTimeoutException -> {
                // Timeout during connection or read/write
                Log.e(TAG, "--> Socket Timeout occurred.", exception)
            }
            is UnknownHostException -> {
                // DNS resolution failure
                Log.e(TAG, "--> Unknown Host Exception (DNS issue?).", exception)
            }
            is EOFException -> {
                // Often indicates connection closed unexpectedly
                Log.w(TAG, "--> EOFException occurred (Connection closed unexpectedly?).", exception)
            }

            // --- General I/O Catch-all ---
            is IOException -> {
                // Catches other IOExceptions (like stream errors, etc.) not handled above
                Log.e(TAG, "--> General IOException occurred.", exception)
            }

            // --- Non-I/O Catch-all ---
            else -> {
                Log.e(TAG, "--> An unexpected non-IOException occurred.", exception)
            }
        }
    }
}
