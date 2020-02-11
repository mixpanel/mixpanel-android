package com.mixpanel.android.mpmetrics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.mixpanel.android.util.JSONUtils;

import org.json.JSONException;
import org.json.JSONObject;

public class MixpanelPushNotificationDismissedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        String campaignId = intent.getStringExtra("mp_campaign_id");
        String messageId = intent.getStringExtra("mp_message_id");
        String canonicalId = intent.getStringExtra("mp_canonical_notification_id");
        String extraLogData = intent.getStringExtra("mp");

        try {
            final JSONObject pushProps;
            if (extraLogData != null) {
                pushProps = new JSONObject(extraLogData);
            } else {
                pushProps = new JSONObject();
            }
            JSONUtils.putIfNotNull(pushProps, "campaign_id", Integer.valueOf(campaignId));
            JSONUtils.putIfNotNull(pushProps, "message_id", Integer.valueOf(messageId));
            JSONUtils.putIfNotNull(pushProps,"$android_notification_id", canonicalId);
            MixpanelAPI.allInstances(new MixpanelAPI.InstanceProcessor() {
                @Override
                public void process(MixpanelAPI m) {
                    m.track("$push_notification_dismissed", pushProps);
                    m.flushNoDecideCheck();
                }
            });
        } catch (JSONException e) {}
    }
}
