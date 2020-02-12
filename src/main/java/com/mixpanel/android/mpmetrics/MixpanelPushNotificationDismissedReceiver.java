package com.mixpanel.android.mpmetrics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MixpanelPushNotificationDismissedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null && action.equals(MixpanelPushNotification.PUSH_DISMISS_ACTION)) {
            MixpanelAPI.trackPushNotificationEventFromIntent(
                    context,
                    intent,
                    "$push_notification_dismissed"
            );
        }
    }
}
