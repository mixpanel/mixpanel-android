package com.mixpanel.android.mpmetrics;

import com.mixpanel.android.identity.MixpanelIdentity;
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

  /**
   * Returns the current user, company and device ids. On a hierarchical groups project the flags
   * requests send the user and company ids as $user_id and $company_id.
   *
   * @return The current identity
   */
  MixpanelIdentity getIdentity();

  void track(String eventName, JSONObject properties);

  String getToken();
}
