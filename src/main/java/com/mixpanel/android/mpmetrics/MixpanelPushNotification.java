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
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.mixpanel.android.util.ImageStore;
import com.mixpanel.android.util.MPLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class MixpanelPushNotification {
    protected final String LOGTAG = "MixpanelAPI.MixpanelPushNotification";
    public NotificationData data;
    public int notificationId;

    public MixpanelPushNotification(Context context, ResourceIds drawableIds) {
        this(context, new Notification.Builder(context), drawableIds, System.currentTimeMillis());
    }

    public MixpanelPushNotification(Context context, Notification.Builder builder, ResourceIds drawableIds, long now) {
        this.context = context;
        this.builder = builder;
        this.drawableIds = drawableIds;
        this.now = now;
        this.notificationId = new Random().nextInt(Integer.MAX_VALUE);
    }

    protected void parseIntent(Intent inboundIntent) {
        final String message = inboundIntent.getStringExtra("mp_message");
        final String iconName = inboundIntent.getStringExtra("mp_icnm");
        final String largeIconName = inboundIntent.getStringExtra("mp_icnm_l");
        final String whiteIconName = inboundIntent.getStringExtra("mp_icnm_w");
        final String expandableImageURL = inboundIntent.getStringExtra("mp_img");
        final String uriString = inboundIntent.getStringExtra("mp_cta");
        CharSequence notificationTitle = inboundIntent.getStringExtra("mp_title");
        CharSequence notificationSubText = inboundIntent.getStringExtra("mp_subtxt");
        final String colorName = inboundIntent.getStringExtra("mp_color");
        final String buttonsJsonStr = inboundIntent.getStringExtra("mp_buttons");
        final String campaignId = inboundIntent.getStringExtra("mp_campaign_id");
        final String messageId = inboundIntent.getStringExtra("mp_message_id");
        final String extraLogData = inboundIntent.getStringExtra("mp");
        int color = NotificationData.NOT_SET;
        List<NotificationButtonData> buttons = new ArrayList<>();
        final String badgeCountStr = inboundIntent.getStringExtra("mp_bdgcnt");
        final String channelId = inboundIntent.getStringExtra("mp_channel_id");
        final String notificationTag = inboundIntent.getStringExtra("mp_tag");
        final String groupKey = inboundIntent.getStringExtra("mp_groupkey");
        final String ticker = inboundIntent.getStringExtra("mp_ticker");
        final String stickyString = inboundIntent.getStringExtra("mp_sticky");
        final String timeString = inboundIntent.getStringExtra("mp_time");
        final int visibility = inboundIntent.getIntExtra("mp_visibility", Notification.VISIBILITY_PRIVATE);
        final String silent = inboundIntent.getStringExtra("mp_silent");

        trackCampaignReceived(campaignId, messageId, extraLogData);

        if (colorName != null) {
            try {
                color = Color.parseColor(colorName);
            } catch (IllegalArgumentException e) {}
        }

        if (message == null) {
            return;
        }

        if (null != notificationSubText && notificationSubText.length() == 0) {
            notificationSubText = null;
        }

        boolean isSilent = null != silent && silent.equals("true") ? true : false;

        boolean sticky = false;
        if (null != stickyString && stickyString.equals("true")) {
            sticky = true;
        }

        int badgeCount = NotificationData.NOT_SET;
        if (null != badgeCountStr) {
            badgeCount = Integer.parseInt(badgeCountStr);
        }

        int notificationIcon = NotificationData.NOT_SET;
        if (null != iconName) {
            if (drawableIds.knownIdName(iconName)) {
                notificationIcon = drawableIds.idFromName(iconName);
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

                    //handle button id
                    final String btnId = buttonObj.getString("id");

                    buttons.add(new NotificationButtonData(btnIcon, btnLabel, btnUri, btnId));
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
        this.data = new NotificationData(notificationIcon, largeIconName, whiteNotificationIcon, expandableImageURL, notificationTitle, notificationSubText, message, notificationIntent, color, buttons, badgeCount, channelId, notificationTag, groupKey, ticker, sticky, timeString, visibility, isSilent, campaignId, messageId);
    }

    protected void buildNotificationFromData() {

        final PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                data.intent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        builder.
                setDefaults(MPConfig.getInstance(context).getNotificationDefaults()).
                setContentTitle(data.title).
                setContentText(data.message).
                setTicker(null == data.ticker ? data.message : data.ticker).
                setContentIntent(contentIntent);

        maybeSetNotificationBarIcon();
        maybeSetLargeIcon();
        maybeSetExpandableNotification();
        maybeSetCustomIconColor();
        maybeAddActionButtons();
        maybeSetChannel();
        maybeSetNotificationBadge();
        maybeSetTime();
        maybeSetVisibility();
        maybeSetSubText();
    }

    protected Notification createNotification(Intent inboundIntent) {
        this.parseIntent(inboundIntent);

        if (null == this.data || this.data.silent) {
            return null;
        }

        this.buildNotificationFromData();

        final Notification n = builderToNotification();

        if (!data.sticky) {
            n.flags |= Notification.FLAG_AUTO_CANCEL;
        }

        return n;
    }

    protected void maybeSetSubText() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && null != data.subText) {
            builder.setSubText(data.subText);
        }
    }

    protected void maybeSetNotificationBarIcon() {
        // For Android 5.0+ (Lollipop), any non-transparent pixels are turned white, so users generally specify
        // icons for these devices and regular full-color icons for older devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && data.whiteIcon != NotificationData.NOT_SET) {
            builder.setSmallIcon(data.whiteIcon);
        } else {
            builder.setSmallIcon(data.icon);
        }
    }

    protected void maybeSetLargeIcon() {
        if (null != data.largeIcon) {
            if (drawableIds.knownIdName(data.largeIcon)) {
                builder.setLargeIcon(getBitmapFromResourceId(drawableIds.idFromName(data.largeIcon)));
            } else if (data.largeIcon.startsWith("http")) {
                Bitmap imageBitmap = getBitmapFromUrl(data.largeIcon);
                if (null != imageBitmap) {
                    builder.setLargeIcon(imageBitmap);
                }
            } else {
                MPLog.d(LOGTAG, "large icon data was sent but did match a resource name or a valid url: " + data.largeIcon);
            }
        }
    }

    protected void maybeSetExpandableNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (null != data.expandableImageUrl && data.expandableImageUrl.startsWith("http")) {
                try {
                    Bitmap imageBitmap = getBitmapFromUrl(data.expandableImageUrl);

                    if (null == imageBitmap) {
                        setBigTextStyle(data.message);
                    } else {
                        setBigPictureStyle(imageBitmap);
                    }
                } catch (Exception e) {
                    setBigTextStyle(data.message);
                }
            } else {
                setBigTextStyle(data.message);
            }
        }
    }

    protected void setBigTextStyle(String message) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.setStyle(new Notification.BigTextStyle().bigText(message));
        }
    }

    protected void setBigPictureStyle(Bitmap imageBitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.setStyle(new Notification.BigPictureStyle().bigPicture(imageBitmap));
        }
    }

    protected void maybeSetCustomIconColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (data.color != NotificationData.NOT_SET) {
                builder.setColor(data.color);
            }
        }
    }

    protected void maybeAddActionButtons() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            for (int i = 0; i < data.buttons.size(); i++) {
                NotificationButtonData btn = data.buttons.get(i);
                builder.addAction(this.createAction(btn.icon, btn.label, btn.uri, btn.actionId));
            }
        }
    }

    @TargetApi(20)
    protected Notification.Action createAction(int icon, CharSequence title, String uri, String actionId) {
        return (new Notification.Action.Builder(icon, title, createActionIntent(uri, actionId))).build();
    }

    protected PendingIntent createActionIntent(String uri, String actionId) {
        Bundle options = new Bundle();
        options.putCharSequence("actionId", actionId);
        options.putCharSequence("uri", uri);
        options.putCharSequence("messageId", data.messageId);
        options.putCharSequence("campaignId", data.campaignId);
        options.putInt("notificationId", notificationId);
        options.putBoolean("sticky", data.sticky);

        Intent routingIntent = new Intent();
        routingIntent.setClass(context, MixpanelNotificationRouteActivity.class);
        routingIntent.putExtras(options);
        routingIntent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        verifyIntentPackage(routingIntent);

        return PendingIntent.getActivity(context, 0, routingIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    protected void verifyIntentPackage(Intent intent) {
        String appPackage = context.getPackageName() + "/com.mixpanel.android.mpmetrics.MixpanelNotificationRouteActivity";
        PackageManager packageManager = context.getPackageManager();

        List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

        if (activities.size() == 0) {
            MPLog.e(LOGTAG, "No activities found to handle Notification Routing Activity");
        }
    }

    protected void maybeSetChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager mNotificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            String channelId = null == data.channelId ? MPConfig.getInstance(context).getNotificationChannelId() : data.channelId;
            String channelName = MPConfig.getInstance(context).getNotificationChannelName();

            NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
            mNotificationManager.createNotificationChannel(channel);

            builder.setChannelId(channelId);
        }
    }

    protected void maybeSetNotificationBadge() {
        if (data.badgeCount > 0) {
            builder.setNumber(data.badgeCount);
        }
    }

    protected void maybeSetTime() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setShowWhen(true);
            if (null == data.timeString) {
                builder.setWhen(now);
            } else {
                Instant instant = Instant.parse(data.timeString);
                builder.setWhen(instant.toEpochMilli());
            }
        }
    }

    protected void maybeSetVisibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(data.visibility);
        }
    }

    protected Bitmap getBitmapFromResourceId(int resourceId) {
        return BitmapFactory.decodeResource(context.getResources(), resourceId);
    }

    protected Bitmap getBitmapFromUrl(String url) {
        ImageStore is = new ImageStore(context, "MixpanelPushNotification");
        try {
            return is.getImage(url);
        } catch (ImageStore.CantGetImageException e) {
            return null;
        }
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
        if (null != campaignId) {
            intent.putExtra("mp_campaign_id", campaignId);
        }

        if (null != messageId) {
            intent.putExtra("mp_message_id", messageId);
        }

        if (null != extraLogData) {
            intent.putExtra("mp", extraLogData);
        }

        return intent;
    }

    protected void trackCampaignReceived(final String campaignId, final String messageId, final String extraLogData) {
        if (null != campaignId && null != messageId) {
            MixpanelAPI.allInstances(new MixpanelAPI.InstanceProcessor() {
                @Override
                public void process(MixpanelAPI api) {
                    if(api.isAppInForeground()) {
                        JSONObject pushProps = new JSONObject();
                        try {
                            if (null != extraLogData) {
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

    protected Notification builderToNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return builder.build();
        } else {
            return builder.getNotification();
        }
    }

    protected static class NotificationData {
        protected NotificationData(int anIcon, String aLargeIcon, int aWhiteIcon, String anExpandableImageUrl, CharSequence aTitle, CharSequence aSubText, String aMessage, Intent anIntent, int aColor, List<NotificationButtonData> aButtons, int aBadgeCount, String aChannelId, String aNotificationTag, String aGroupKey, String aTicker, boolean aSticky, String aTimeString, int aVisibility, boolean isSilent, String aCampaignId, String aMessageId) {
            icon = anIcon;
            largeIcon = aLargeIcon;
            whiteIcon = aWhiteIcon;
            expandableImageUrl = anExpandableImageUrl;
            title = aTitle;
            subText = aSubText;
            message = aMessage;
            intent = anIntent;
            color = aColor;
            buttons = aButtons;
            badgeCount = aBadgeCount;
            channelId = null == aChannelId ? NotificationData.DEFAULT_CHANNEL_ID : aChannelId;
            tag = aNotificationTag;
            groupKey = aGroupKey;
            ticker = aTicker;
            sticky = aSticky;
            timeString = aTimeString;
            visibility = aVisibility;
            silent = isSilent;
            campaignId = aCampaignId;
            messageId = aMessageId;

        }

        public final int icon;
        public final String largeIcon;
        public final int whiteIcon;
        public final String expandableImageUrl;
        public final CharSequence title;
        public final CharSequence subText;
        public final String message;
        public final Intent intent;
        public final int color;
        public final List<NotificationButtonData> buttons;
        public final int badgeCount;
        public final String channelId;
        public final String tag;
        public final String groupKey;
        public final String ticker;
        public final boolean sticky;
        public final String timeString;
        public final int visibility;
        public final boolean silent;
        public final String campaignId;
        public final String messageId;

        public static final int NOT_SET = -1;
        public static final String DEFAULT_CHANNEL_ID = "mp";

    }

    protected static class NotificationButtonData {
        public NotificationButtonData(int anIcon, String aLabel, String aUri, String aId) {
            icon = anIcon;
            label = aLabel;
            uri = aUri;
            actionId = aId;
        }

        public final int icon;
        public final String label;
        public final String uri;
        public final String actionId;
    }

    protected Context context;
    protected Notification.Builder builder;
    protected ResourceIds drawableIds;
    protected long now;
}
