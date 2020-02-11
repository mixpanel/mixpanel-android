package com.mixpanel.android.mpmetrics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MixpanelPushNotificationDismissedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        MixpanelAPI.trackPushNotificationEventFromIntent(
                context,
                intent,
                "$push_notification_dismissed"
        );
    }
}
