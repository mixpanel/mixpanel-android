package com.mixpanel.android.mpmetrics;

import org.json.JSONObject;

/**
 * Interface for FeatureFlagManager to retrieve necessary data and trigger actions from the main
 * MixpanelAPI instance.
 */
interface FeatureFlagDelegate {
  MPConfig getMPConfig();

  String getDistinctId();

  /**
   * Returns the anonymous device ID for this installation. This ID is persistent across app
   * sessions and is used to identify the device in feature flag requests as the device_id field.
   *
   * @return The anonymous device ID, or null if not available
   */
  String getAnonymousId();

  void track(String eventName, JSONObject properties);

  String getToken();
}
