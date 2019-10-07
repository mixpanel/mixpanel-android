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

        final Intent routeIntent = getIntent();
        Bundle extras = routeIntent.getExtras();

        if (null == routeIntent) {
            MPLog.d(LOGTAG, "Notification route activity given null intent.");
            return;
        }

        trackAction(routeIntent);

        final Intent notificationIntent = handleRouteIntent(routeIntent);


        if (!extras.getBoolean("sticky")) {
            cancelNotification(extras);
        }
        startActivity(notificationIntent);
    }

    protected Intent handleRouteIntent(Intent routeIntent) {
        String actionType = routeIntent.getExtras().getCharSequence("actionType").toString();

        if (actionType.equals("browser") || actionType.equals("deeplink")) {
            return new Intent(Intent.ACTION_VIEW, Uri.parse(routeIntent.getExtras().getCharSequence("uri").toString()));
        } else if (actionType.equals("webview")) {
            return new Intent(this.getApplicationContext(), MixpanelWebViewActivity.class).
                    putExtra("uri", routeIntent.getExtras().getCharSequence("uri").toString()).
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } else {
            //use homescreen as default if no actionType specified
            return this.getPackageManager().getLaunchIntentForPackage(this.getPackageName());
        }
    }

    protected void cancelNotification(Bundle extras) {
        int notificationId = extras.getInt("notificationId");
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
        notificationManager.cancel(notificationId);
    }

    protected void trackAction(Intent routeIntent) {
        Bundle intentExtras = routeIntent.getExtras();

        CharSequence actionIdChars = intentExtras.getCharSequence("actionId");
        if (null == actionIdChars) {
            MPLog.d(LOGTAG, "Notification action click logged with no actionId.");
            return;
        }

        CharSequence uriChars = intentExtras.getCharSequence("uri");
        if (null == uriChars) {
            MPLog.d(LOGTAG, "Notification action click logged with no uri.");
            return;
        }

        CharSequence messageIdChars = intentExtras.getCharSequence("messageId");
        if (null == messageIdChars) {
            MPLog.d(LOGTAG, "Notification action click logged with no messageId.");
            return;
        }

        CharSequence campaignIdChars = intentExtras.getCharSequence("campaignId");
        if (null == campaignIdChars) {
            MPLog.d(LOGTAG, "Notification action click logged with no campaignId.");
            return;
        }

        final String actionId = actionIdChars.toString();
        final String actionType = actionTypeChars.toString();
        final String label = labelChars.toString();
        final String messageId = messageIdChars.toString();
        final String campaignId = campaignIdChars.toString();

        MixpanelAPI.allInstances(new MixpanelAPI.InstanceProcessor() {
            @Override
            public void process(MixpanelAPI api) {
                JSONObject pushProps = new JSONObject();
                String tapTarget;
                try {
                    if (actionId.equals("notificationClick")) {
                        pushProps.put("tap_target", "notification");
                    } else {
                        pushProps.put("tap_target", "button");
                        pushProps.put("button_id", actionId);
                        pushProps.put("button_label", label);
                    }

                    pushProps.put("message_id", messageId);
                    pushProps.put("campaign_id", campaignId);
                } catch (JSONException e) {
                    MPLog.e(LOGTAG, "Error loading tracking JSON properties.");
                }
                api.track("$push_notification_tap", pushProps);
            }
        });
    }

}
