package com.mixpanel.android.mpmetrics;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.mixpanel.android.util.MPLog;

import org.json.JSONException;
import org.json.JSONObject;

public class MixpanelNotificationRouteActivity extends Activity {

    protected final String LOGTAG = "MixpanelAPI.MixpanelNotificationRouteActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent routeIntent = getIntent();
        Bundle extras = routeIntent.getExtras();

        if (null == routeIntent) {
            MPLog.i(LOGTAG, "Notification route activity given null intent.");
            return;
        }

        trackAction(routeIntent);

        Intent notificationIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(routeIntent.getExtras().getCharSequence("uri").toString()));
        if (extras.getBoolean("sticky")) {
            cancelNotification(extras);
        }
        startActivity(notificationIntent);
    }

    private void cancelNotification(Bundle extras) {
        int notificationId = extras.getInt("notificationId");
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
        notificationManager.cancel(notificationId);
    }

    protected void trackAction(Intent routeIntent) {
        Bundle intentExtras = routeIntent.getExtras();

        final String actionId = intentExtras.getCharSequence("actionId").toString();
        if (null == actionId) {
            MPLog.i(LOGTAG, "Notification action click logged with no actionId.");
            return;
        }

        final String uri = intentExtras.getCharSequence("uri").toString();
        if (null == uri) {
            MPLog.i(LOGTAG, "Notification action click logged with no actionId.");
            return;
        }

        final String messageId = intentExtras.getCharSequence("messageId").toString();
        if (null == messageId) {
            MPLog.i(LOGTAG, "Notification action click logged with no messageId.");
            return;
        }

        final String campaignId = intentExtras.getCharSequence("campaignId").toString();
        if (null == campaignId) {
            MPLog.i(LOGTAG, "Notification action click logged with no campaignId.");
            return;
        }

        MixpanelAPI.allInstances(new MixpanelAPI.InstanceProcessor() {
            @Override
            public void process(MixpanelAPI api) {
                if (api.isAppInForeground()) {
                    JSONObject pushProps = new JSONObject();

                    try {
                        pushProps.put("actionId", actionId);
                        pushProps.put("uri", uri);
                        pushProps.put("messageId", messageId);
                        pushProps.put("campaignId", campaignId);
                    } catch (JSONException e) {
                        MPLog.e(LOGTAG, "Error loading tracking JSON properties.");
                    }

                    api.track("Notification Action Click", pushProps);
                }
            }
        });
    }
}
