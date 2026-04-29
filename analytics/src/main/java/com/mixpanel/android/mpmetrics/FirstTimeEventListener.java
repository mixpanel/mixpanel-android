package com.mixpanel.android.mpmetrics;

import org.json.JSONObject;

/**
 * Listener interface for first-time event checking.
 * Package-private for internal use only.
 */
interface FirstTimeEventListener {
    void onEventTracked(String eventName, JSONObject properties);
}
