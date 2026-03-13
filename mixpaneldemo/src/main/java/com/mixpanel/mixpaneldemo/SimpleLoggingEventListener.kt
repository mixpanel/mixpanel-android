package com.mixpanel.mixpaneldemo

import android.util.Log
import com.mixpanel.android.eventbridge.MixpanelEventListener
import org.json.JSONObject

/**
 * A simple implementation of MixpanelEventListener that logs
 * all tracked events using Android's Logcat.
 */
class SimpleLoggingEventListener : MixpanelEventListener {

    companion object {
        private const val TAG = "MixpanelEventBridge"
    }

    override fun onEventTracked(event: Map<String, Any>) {
        val eventName = event["eventName"] as? String ?: "unknown"
        val properties = event["properties"] as? JSONObject

        Log.i(TAG, "Event tracked: '$eventName'")
        properties?.let {
            Log.d(TAG, "Properties: ${it.toString(2)}")
        }
    }
}
