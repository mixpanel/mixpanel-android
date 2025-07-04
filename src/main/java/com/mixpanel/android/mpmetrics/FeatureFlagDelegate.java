package com.mixpanel.android.mpmetrics;

import org.json.JSONObject;

/**
 * Interface for FeatureFlagManager to retrieve necessary data and trigger actions
 * from the main MixpanelAPI instance.
 */
interface FeatureFlagDelegate {
    MPConfig getMPConfig();
    String getDistinctId();
    String getAnonymousId();
    void track(String eventName, JSONObject properties);
    String getToken();
}