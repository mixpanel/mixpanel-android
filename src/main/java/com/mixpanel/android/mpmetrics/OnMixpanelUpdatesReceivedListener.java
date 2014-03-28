package com.mixpanel.android.mpmetrics;

/**
 * For use with MixpanelAPI.addOnMixpanelUpdatesReceivedListener()
 */
public interface OnMixpanelUpdatesReceivedListener {
    /**
     * Called when the Mixpanel library has updates, for example, Surveys or Notifications.
     * This method will not be called once per update, but rather any time a batch of updates
     * becomes available. The related updates can be checked with
     * MixpanelAPI.getSurveyIfAvailable() or MixpanelAPI.getNotificationIfAvailable()
     */
    public void onMixpanelUpdatesReceived();
}
