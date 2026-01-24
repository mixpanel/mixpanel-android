package com.mixpanel.android.mpmetrics;

import org.json.JSONObject;

/**
 * Callback interface for first-time event checking.
 * Package-private for internal use only.
 */
interface FirstTimeEventCallback {
    void onEventTracked(String eventName, JSONObject properties);
}
