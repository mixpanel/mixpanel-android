package com.mixpanel.mixpaneldemo

import android.util.Log
import com.mixpanel.android.util.MixpanelNetworkErrorListener

/**
 * A simple implementation of MixpanelNetworkErrorListener that logs
 * the encountered network errors using Android's Logcat.
 */
class SimpleLoggingErrorListener : MixpanelNetworkErrorListener {

    companion object {
        private const val TAG = "MixpanelNetworkError"
    }

    override fun onNetworkError(endpointUrl: String, exception: Exception) {
        Log.e(TAG, "Mixpanel network request failed for endpoint: $endpointUrl", exception)
    }
}