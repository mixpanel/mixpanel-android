package com.mixpanel.android.mpmetrics;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.URLUtil;

import com.mixpanel.android.util.JSONUtils;
import com.mixpanel.android.util.MPLog;
import com.mixpanel.android.mpmetrics.MixpanelNotificationData.PushTapActionType;

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
            MixpanelFCMMessagingService fcmMessagingService = new MixpanelFCMMessagingService();
            NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
            fcmMessagingService.cancelNotification(extras, notificationManager);
        }
        startActivity(notificationIntent);
    }

    protected Intent handleRouteIntent(Intent routeIntent) {
        CharSequence actionTypeChars = routeIntent.getExtras().getCharSequence("actionType");
        PushTapActionType target;
        if (null == actionTypeChars) {
            MPLog.d(LOGTAG, "Notification action click logged with no action type");
            target = PushTapActionType.HOMESCREEN;
        } else {
            target = PushTapActionType.fromString(actionTypeChars.toString());
        }

        CharSequence uri = routeIntent.getExtras().getCharSequence("uri");

        final Intent defaultIntent = this.getPackageManager().getLaunchIntentForPackage(this.getPackageName());

        switch (target) {
            case HOMESCREEN:
                return defaultIntent;
            case URL_IN_BROWSER:
                if (URLUtil.isValidUrl(uri.toString())) {
                    return new Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString()));
                } else {
                    MPLog.d(LOGTAG, "Wanted to open url in browser but url is invalid: " + uri.toString() + ". Starting default intent");
                    return defaultIntent;
                }
            case DEEP_LINK:
                return new Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString()));
            default:
                return defaultIntent;
        }
    }

    protected void trackAction(Intent routeIntent) {
        Bundle intentExtras = routeIntent.getExtras();

        final String tapTarget = getStringFromBundle(intentExtras, "mp_tap_target");
        final String tapActionType = getStringFromBundle(intentExtras, "mp_tap_action_type");
        final String tapActionUri = getStringFromBundle(intentExtras, "mp_tap_action_uri");
        final String messageId = getStringFromBundle(intentExtras, "mp_message_id");
        final String campaignId = getStringFromBundle(intentExtras, "mp_campaign_id");
        final String canonicalId = getStringFromBundle(intentExtras, "mp_canonical_notification_id");
        final String extraLogData = getStringFromBundle(intentExtras, "mp");
        final Boolean sticky = getBooleanFromBundle(intentExtras, "mp_is_sticky");

        final String buttonId;
        final String buttonLabel;
        if (tapTarget != null && tapTarget.equals(MixpanelPushNotification.TAP_TARGET_BUTTON)) {
            buttonId = getStringFromBundle(intentExtras, "mp_button_id");
            buttonLabel = getStringFromBundle(intentExtras, "mp_button_label");
        } else {
            buttonId = null;
            buttonLabel = null;
        }

        MixpanelAPI.allInstances(new MixpanelAPI.InstanceProcessor() {
            @Override
            public void process(MixpanelAPI api) {
                JSONObject pushProps = new JSONObject();
                try {
                    if (extraLogData != null) {
                        pushProps = new JSONObject(extraLogData);
                    }
                } catch (JSONException e) {}
                try {
                    JSONUtils.putIfNotNull(pushProps, "message_id", messageId); // no $ prefix for historical consistency
                    JSONUtils.putIfNotNull(pushProps, "campaign_id", campaignId); // no $ prefix for historical consistency
                    JSONUtils.putIfNotNull(pushProps, "$tap_target", tapTarget);
                    JSONUtils.putIfNotNull(pushProps, "$tap_action_type", tapActionType);
                    JSONUtils.putIfNotNull(pushProps, "$tap_action_uri", tapActionUri);
                    JSONUtils.putIfNotNull(pushProps, "$is_sticky", sticky);
                    JSONUtils.putIfNotNull(pushProps, "$button_id", buttonId);
                    JSONUtils.putIfNotNull(pushProps, "$button_label", buttonLabel);
                    JSONUtils.putIfNotNull(pushProps, "$android_notification_id", canonicalId);
                } catch (JSONException e) {
                    MPLog.e(LOGTAG, "Error loading tracking JSON properties.");
                }
                api.track("$push_notification_tap", pushProps);
            }
        });
    }

    private String getStringFromBundle(Bundle bundle, String key) {
        String val = null;
        CharSequence valChars = bundle.getCharSequence(key);
        if (null == valChars) {
            MPLog.d(LOGTAG, "$push_notification_tap logged with no \"" + key + "\" property");
        } else {
            val = valChars.toString();
        }
        return val;
    }

    private Boolean getBooleanFromBundle(Bundle bundle, String key) {
        Boolean val = null;
        if (!bundle.containsKey(key)) {
            MPLog.d(LOGTAG, "$push_notification_tap logged with no \"" + key + "\" property");
        } else {
            val = bundle.getBoolean(key);
        }
        return val;
    }

}
