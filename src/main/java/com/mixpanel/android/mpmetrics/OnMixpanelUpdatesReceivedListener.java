package com.mixpanel.android.mpmetrics;

/**
 * For use with {@link MixpanelAPI.People#addOnMixpanelUpdatesReceivedListener(OnMixpanelUpdatesReceivedListener)}
 */
public interface OnMixpanelUpdatesReceivedListener {
    /**
     * Called when the Mixpanel library has updates, for example, Surveys or Notifications.
     * This method will not be called once per update, but rather any time a batch of updates
     * becomes available. The related updates can be checked with
     * {@link MixpanelAPI.People#getSurveyIfAvailable()} or {@link MixpanelAPI.People#getNotificationIfAvailable()}
     */
    public void onMixpanelUpdatesReceived();
}
