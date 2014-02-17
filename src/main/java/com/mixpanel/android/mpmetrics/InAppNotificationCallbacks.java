package com.mixpanel.android.mpmetrics;

/**
 * For use with the Mixpanel API. InAppNotificationCallbacks.foundNotification() will be called with the library gets a notification from Mixpanel.
 */
public interface InAppNotificationCallbacks {
    /**
     * foundNotification will be called when the MixpanelAPI check for available notifications
     * returns. The argument will be null if no survey was available, or
     * a non-null survey if one exists to be shown to the user.
     *
     * foundNotification() should be safe to call from an arbitrary thread.
     */
    public void foundNotification(InAppNotification n);
}
