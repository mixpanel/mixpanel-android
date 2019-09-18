package com.mixpanel.android.mpmetrics;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;

import com.mixpanel.android.util.MPLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MixpanelPushNotification {
    protected final String LOGTAG = "MixpanelAPI.MixpanelPushNotification";

    public MixpanelPushNotification(Context context, ResourceIds drawableIds) {
        this(context, new Notification.Builder(context), drawableIds, System.currentTimeMillis());
    }

    public MixpanelPushNotification(Context context, Notification.Builder builder, ResourceIds drawableIds, long now) {
        this.context = context;
        this.builder = builder;
        this.drawableIds = drawableIds;
        this.now = now;
    }

    public Notification createNotification(Intent inboundIntent) {
        final NotificationData data = readInboundIntent(inboundIntent);
        if (null == data) {
            return null;
        }

        MPLog.d(LOGTAG, "MP FCM notification received: " + data.message);
        final PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                data.intent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        builder.
                setDefaults(MPConfig.getInstance(context).getNotificationDefaults()).
                setWhen(now).
                setContentTitle(data.title).
                setContentText(data.message).
                setTicker(data.message).
                setContentIntent(contentIntent);

        maybeSetNotificationBarIcon(data);
        maybeSetLargeIcon(data);
        maybeSetExpandableNotification(data);
        maybeSetCustomIconColor(data);
        maybeAddActionButtons(data);
        maybeSetChannel();

        final Notification n = buildNotification();
        n.flags |= Notification.FLAG_AUTO_CANCEL;

        return n;
    }

    protected void maybeSetNotificationBarIcon(NotificationData data) {
        // For Android 5.0+ (Lollipop), any non-transparent pixels are turned white, so users generally specify
        // icons for these devices and regular full-color icons for older devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && data.whiteIcon != NotificationData.NOT_SET) {
            builder.setSmallIcon(data.whiteIcon);
        } else {
            builder.setSmallIcon(data.icon);
        }
    }

    protected void maybeSetLargeIcon(NotificationData data) {
        if (data.largeIcon != NotificationData.NOT_SET) {
            builder.setLargeIcon(getBitmapFromResourceId(data.largeIcon));
        }
    }

    protected void maybeSetExpandableNotification(NotificationData data) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.setStyle(new Notification.BigTextStyle().bigText(data.message));
        }
    }

    protected void maybeSetCustomIconColor(NotificationData data) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (data.color != NotificationData.NOT_SET) {
                builder.setColor(data.color);
            }
        }
    }

    protected void maybeAddActionButtons(NotificationData data) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            for (int i = 0; i < data.buttons.size(); i++) {
                NotificationButtonData btn = data.buttons.get(i);
                builder.addAction(this.createAction(btn.icon, btn.label, btn.uri));
            }
        }
    }

    @TargetApi(20)
    protected Notification.Action createAction(int icon, CharSequence title, String uri) {
        return (new Notification.Action.Builder(icon, title, createActionIntent(uri))).build();
    }

    protected PendingIntent createActionIntent(String uri) {
        return PendingIntent.getActivity(
                context,
                0,
                new Intent(Intent.ACTION_VIEW, Uri.parse(uri)),
                PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    protected void maybeSetChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager mNotificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            String channelId = MPConfig.getInstance(context).getNotificationChannelId();
            String channelName = MPConfig.getInstance(context).getNotificationChannelName();
            int importance = MPConfig.getInstance(context).getNotificationChannelImportance();

            NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
            int notificationDefaults = MPConfig.getInstance(context).getNotificationDefaults();

            if (notificationDefaults == Notification.DEFAULT_VIBRATE || notificationDefaults == Notification.DEFAULT_ALL) {
                channel.enableVibration(true);
            }

            if (notificationDefaults == Notification.DEFAULT_LIGHTS || notificationDefaults == Notification.DEFAULT_ALL) {
                channel.enableLights(true);
                channel.setLightColor(Color.WHITE);
            }

            mNotificationManager.createNotificationChannel(channel);

            builder.setChannelId(channelId);
        }
    }

    protected Bitmap getBitmapFromResourceId(int resourceId) {
        return BitmapFactory.decodeResource(context.getResources(), resourceId);
    }

    /* package */ NotificationData readInboundIntent(Intent inboundIntent) {

        final String message = inboundIntent.getStringExtra("mp_message");
        final String iconName = inboundIntent.getStringExtra("mp_icnm");
        final String largeIconName = inboundIntent.getStringExtra("mp_icnm_l");
        final String whiteIconName = inboundIntent.getStringExtra("mp_icnm_w");
        final String uriString = inboundIntent.getStringExtra("mp_cta");
        CharSequence notificationTitle = inboundIntent.getStringExtra("mp_title");
        final String colorName = inboundIntent.getStringExtra("mp_color");
        final String buttonsJsonStr = inboundIntent.getStringExtra("mp_buttons");
        final String campaignId = inboundIntent.getStringExtra("mp_campaign_id");
        final String messageId = inboundIntent.getStringExtra("mp_message_id");
        final String extraLogData = inboundIntent.getStringExtra("mp");
        int color = NotificationData.NOT_SET;
        List<NotificationButtonData> buttons = new ArrayList<>();

        trackCampaignReceived(campaignId, messageId, extraLogData);

        if (colorName != null) {
            try {
                color = Color.parseColor(colorName);
            } catch (IllegalArgumentException e) {}
        }

        if (message == null) {
            return null;
        }

        int notificationIcon = NotificationData.NOT_SET;
        if (null != iconName) {
            if (drawableIds.knownIdName(iconName)) {
                notificationIcon = drawableIds.idFromName(iconName);
            }
        }

        int largeNotificationIcon = NotificationData.NOT_SET;
        if (null != largeIconName) {
            if (drawableIds.knownIdName(largeIconName)) {
                largeNotificationIcon = drawableIds.idFromName(largeIconName);
            }
        }

        int whiteNotificationIcon = NotificationData.NOT_SET;
        if (null != whiteIconName) {
            if (drawableIds.knownIdName(whiteIconName)) {
                whiteNotificationIcon = drawableIds.idFromName(whiteIconName);
            }
        }

        if (notificationIcon == NotificationData.NOT_SET) {
            notificationIcon = getDefaultIcon();
        }

        if (null == notificationTitle) {
            notificationTitle = getDefaultTitle();
        }

        if (null != buttonsJsonStr) {
            try {
                JSONArray buttonsArr = new JSONArray(buttonsJsonStr);
                for (int i = 0; i < buttonsArr.length(); i++) {
                    JSONObject buttonObj = buttonsArr.getJSONObject(i);

                    // get button icon from name if one sent
                    int btnIcon = NotificationData.NOT_SET;
                    if (buttonObj.has("icnm")) {
                        String btnIconName = buttonObj.getString("icnm");
                        if (drawableIds.knownIdName(btnIconName)) {
                            btnIcon = drawableIds.idFromName(btnIconName);
                        }
                    }

                    // handle button label
                    final String btnLabel = buttonObj.getString("lbl");

                    // handle button action
                    final String btnUri = buttonObj.getString("uri");

                    buttons.add(new NotificationButtonData(btnIcon, btnLabel, btnUri));
                }
            } catch (JSONException e) {
                MPLog.e(LOGTAG, "Exception parsing buttons payload", e);
            }
        }

        Uri uri = null;
        if (null != uriString) {
            uri = Uri.parse(uriString);
        }
        final Intent intent;
        if (null == uri) {
            intent = getDefaultIntent();
        } else {
            intent = buildIntentForUri(uri);
        }

        final Intent notificationIntent = buildNotificationIntent(intent, campaignId, messageId, extraLogData);

        return new NotificationData(notificationIcon, largeNotificationIcon, whiteNotificationIcon, notificationTitle, message, notificationIntent, color, buttons);
    }

    protected ApplicationInfo getAppInfo() {
        try {
            return context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
        } catch (final PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    protected CharSequence getDefaultTitle() {
        ApplicationInfo appInfo = getAppInfo();
        if (null != appInfo) {
            return context.getPackageManager().getApplicationLabel(appInfo);
        } else {
            return "A message for you";
        }
    }

    protected int getDefaultIcon() {
        ApplicationInfo appInfo = getAppInfo();
        if (null != appInfo) {
            return appInfo.icon;
        } else {
            return android.R.drawable.sym_def_app_icon;
        }
    }

    protected Intent getDefaultIntent() {
        return context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
    }

    protected Intent buildIntentForUri(Uri uri) {
        return new Intent(Intent.ACTION_VIEW, uri);
    }

    protected Intent buildNotificationIntent(Intent intent, String campaignId, String messageId, String extraLogData) {
        if (campaignId != null) {
            intent.putExtra("mp_campaign_id", campaignId);
        }

        if (messageId != null) {
            intent.putExtra("mp_message_id", messageId);
        }

        if (extraLogData != null) {
            intent.putExtra("mp", extraLogData);
        }

        return intent;
    }

    protected void trackCampaignReceived(final String campaignId, final String messageId, final String extraLogData) {
        if (campaignId != null && messageId != null) {
            MixpanelAPI.allInstances(new MixpanelAPI.InstanceProcessor() {
                @Override
                public void process(MixpanelAPI api) {
                    if(api.isAppInForeground()) {
                        JSONObject pushProps = new JSONObject();
                        try {
                            if (extraLogData != null) {
                                pushProps = new JSONObject(extraLogData);
                            }
                        } catch (JSONException e) {}

                        try {
                            pushProps.put("campaign_id", Integer.valueOf(campaignId).intValue());
                            pushProps.put("message_id", Integer.valueOf(messageId).intValue());
                            pushProps.put("message_type", "push");
                            api.track("$campaign_received", pushProps);
                        } catch (JSONException e) {}
                    }
                }
            });
        }
    }

    protected Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return builder.build();
        } else {
            return builder.getNotification();
        }
    }

    protected static class NotificationData {
        protected NotificationData(int anIcon, int aLargeIcon, int aWhiteIcon, CharSequence aTitle, String aMessage, Intent anIntent, int aColor, List<NotificationButtonData> aButtons) {
            icon = anIcon;
            largeIcon = aLargeIcon;
            whiteIcon = aWhiteIcon;
            title = aTitle;
            message = aMessage;
            intent = anIntent;
            color = aColor;
            buttons = aButtons;
        }

        public final int icon;
        public final int largeIcon;
        public final int whiteIcon;
        public final CharSequence title;
        public final String message;
        public final Intent intent;
        public final int color;
        public final List<NotificationButtonData> buttons;

        public static final int NOT_SET = -1;
    }

    protected static class NotificationButtonData {
        public NotificationButtonData(int anIcon, String aLabel, String aUri) {
            icon = anIcon;
            label = aLabel;
            uri = aUri;
        }

        public final int icon;
        public final String label;
        public final String uri;
    }

    protected Context context;
    protected Notification.Builder builder;
    protected ResourceIds drawableIds;
    protected long now;
}
