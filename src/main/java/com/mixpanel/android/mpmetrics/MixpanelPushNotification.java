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
import android.os.Build;
import android.os.Bundle;

import com.mixpanel.android.util.ImageStore;
import com.mixpanel.android.util.MPLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Routing activity to introduce mixpanel tracking to notification actions
 *
 * <p>This class acts as a liaison between a notification's actions and the
 * handling of that action by Android so that we can make tracking calls to
 * the Mixpanel API.
 *
 * <p>To enable this activity in your apps you must add the activity in the
 * Android Manifest XML file for that project.
 *
 *<pre>
 *{@code
 *
 *<activity android:name="com.mixpanel.android.mpmetrics.MixpanelNotificationRouteActivity">
 *    <intent-filter>
 *        <action android:name="android.intent.action.VIEW"/>
 *    </intent-filter>
 *</activity>
 *}
 *</pre>
 */
public class MixpanelPushNotification {
    protected final String LOGTAG = "MixpanelAPI.MixpanelPushNotification";
    protected final static String TAP_TARGET_BUTTON = "button";
    protected final static String TAP_TARGET_NOTIFICATION = "notification";
    protected final int ROUTING_REQUEST_CODE = (int) System.currentTimeMillis();
    public NotificationData data;
    public int notificationId;

    static final String DATETIME_NO_TZ = "yyyy-MM-dd'T'HH:mm:ss";
    static final String DATETIME_WITH_TZ = "yyyy-MM-dd'T'HH:mm:ssz";
    static final String DATETIME_ZULU_TZ = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    public MixpanelPushNotification(Context context, ResourceIds drawableIds) {
        this(context, new Notification.Builder(context), drawableIds, System.currentTimeMillis());
    }

    public MixpanelPushNotification(Context context, Notification.Builder builder, ResourceIds drawableIds, long now) {
        this.context = context;
        this.builder = builder;
        this.drawableIds = drawableIds;
        this.now = now;
        this.notificationId = (int) System.currentTimeMillis();
    }

    protected void parseIntent(Intent inboundIntent) {
        final String message = inboundIntent.getStringExtra("mp_message");
        final String iconName = inboundIntent.getStringExtra("mp_icnm");
        final String largeIconName = inboundIntent.getStringExtra("mp_icnm_l");
        final String whiteIconName = inboundIntent.getStringExtra("mp_icnm_w");
        final String expandableImageURL = inboundIntent.getStringExtra("mp_img");
        final String uriString = inboundIntent.getStringExtra("mp_cta");
        final String onTapStr = inboundIntent.getStringExtra("mp_ontap");
        CharSequence notificationTitle = inboundIntent.getStringExtra("mp_title");
        CharSequence notificationSubText = inboundIntent.getStringExtra("mp_subtxt");
        final String colorName = inboundIntent.getStringExtra("mp_color");
        final String buttonsJsonStr = inboundIntent.getStringExtra("mp_buttons");
        final String campaignId = inboundIntent.getStringExtra("mp_campaign_id");
        final String messageId = inboundIntent.getStringExtra("mp_message_id");
        final String extraLogData = inboundIntent.getStringExtra("mp");
        final String badgeCountStr = inboundIntent.getStringExtra("mp_bdgcnt");
        final String channelId = inboundIntent.getStringExtra("mp_channel_id");
        final String notificationTag = inboundIntent.getStringExtra("mp_tag");
        final String groupKey = inboundIntent.getStringExtra("mp_groupkey");
        final String ticker = inboundIntent.getStringExtra("mp_ticker");
        final String stickyString = inboundIntent.getStringExtra("mp_sticky");
        final String timeString = inboundIntent.getStringExtra("mp_time");
        final String visibilityStr = inboundIntent.getStringExtra("mp_visibility");
        final String silent = inboundIntent.getStringExtra("mp_silent");

        trackCampaignReceived(campaignId, messageId, extraLogData);

        if (null == message) {
            return;
        }

        if (null == notificationTitle) {
            notificationTitle = getDefaultTitle();
        }

        if (null != notificationSubText && notificationSubText.length() == 0) {
            notificationSubText = null;
        }

        int visibility = Notification.VISIBILITY_PRIVATE;
        if (null != visibilityStr) {
            visibility = Integer.parseInt(visibilityStr);
        }

        int color = NotificationData.NOT_SET;
        if (null != colorName) {
            try {
                color = Color.parseColor(colorName);
            } catch (IllegalArgumentException e) {}
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

        boolean isSilent = null != silent && silent.equals("true");
        boolean sticky = null != stickyString && stickyString.equals("true");

        List<NotificationButtonData> buttons = buildButtons(buttonsJsonStr);
        PushTapAction onTap = buildOnTap(onTapStr);
        if (null == onTap) {
            onTap = buildOnTapFromURI(uriString);
        }
        if (null == onTap) {
            onTap = getDefaultOnTap();
        }

        this.data = new NotificationData(notificationIcon, largeIconName, whiteNotificationIcon, expandableImageURL, notificationTitle, notificationSubText, message, onTap, color, buttons, badgeCount, channelId, notificationTag, groupKey, ticker, sticky, timeString, visibility, isSilent, campaignId, messageId);
    }

    protected void buildNotificationFromData() {
        final PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                ROUTING_REQUEST_CODE,
                getRoutingIntent(data.onTap),
                PendingIntent.FLAG_CANCEL_CURRENT
        );

        builder.
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

        if (null == this.data) {
            return null;
        }

        if (this.data.silent) {
            MPLog.i(LOGTAG, "Notification will not be shown because \'mp_silent = true\'");
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
                builder.addAction(this.createAction(btn.icon, btn.label, btn.onTap, btn.buttonId, i + 1));
            }
        }
    }

    protected List<NotificationButtonData> buildButtons(String buttonsJsonStr) {
        List<NotificationButtonData> buttons = new ArrayList<>();
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
                    final JSONObject pushActionJSON = buttonObj.getJSONObject("ontap");
                    final PushTapTarget target = PushTapTarget.fromString(pushActionJSON.getString("type"));
                    final PushTapAction pushAction = new PushTapAction(target, pushActionJSON.getString("uri"));

                    //handle button id
                    final String btnId = buttonObj.getString("id");

                    buttons.add(new NotificationButtonData(btnIcon, btnLabel, pushAction, btnId));
                }
            } catch (JSONException e) {
                MPLog.e(LOGTAG, "Exception parsing buttons payload", e);
            }
        }

        return buttons;
    }

    protected PushTapAction buildOnTap(String onTapStr) {
        PushTapAction onTap = null;
        if (null != onTapStr) {
            try {
                final JSONObject onTapJSON = new JSONObject(onTapStr);
                final String uriFromJSON = onTapJSON.getString("uri");
                if (uriFromJSON != null) {
                    onTap = new PushTapAction(PushTapTarget.fromString(onTapJSON.getString("type")), uriFromJSON);
                } else {
                    onTap = new PushTapAction(PushTapTarget.fromString(onTapJSON.getString("type")));
                }

            } catch (JSONException e){
                MPLog.d(LOGTAG, "Couldn't parse JSON Object for \'mp_ontap\'");
                onTap = null;
            }
        }

        return onTap;
    }

    protected PushTapAction buildOnTapFromURI(String uriString) {
        PushTapAction onTap = null;

        if (null != uriString) {
            onTap = new PushTapAction(PushTapTarget.URL_IN_BROWSER, uriString);
        }

        return onTap;
    }

    protected PushTapAction getDefaultOnTap() {
        return new PushTapAction(PushTapTarget.HOMESCREEN);
    }

    @TargetApi(20)
    protected Notification.Action createAction(int icon, CharSequence title, PushTapAction onTap, String actionId, int index) {
        return (new Notification.Action.Builder(icon, title, createActionIntent(onTap, actionId, title, index))).build();
    }

    protected PendingIntent createActionIntent(PushTapAction onTap, String buttonId, CharSequence label, int index) {
        Intent routingIntent = getRoutingIntent(onTap, buttonId, label);
        return PendingIntent.getActivity(context, ROUTING_REQUEST_CODE + index, routingIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    protected Intent getRoutingIntent(PushTapAction onTap, String buttonId, CharSequence label) {
        Bundle options = buildBundle(onTap, buttonId, label);

        Intent routingIntent = new Intent().
                setClass(context, MixpanelNotificationRouteActivity.class).
                putExtras(options).
                setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        verifyIntentPackage(routingIntent);
        return routingIntent;
    }

    protected Intent getRoutingIntent(PushTapAction onTap) {
        Bundle options = buildBundle(onTap);

        Intent routingIntent = new Intent().
                setClass(context, MixpanelNotificationRouteActivity.class).
                putExtras(options).
                setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        verifyIntentPackage(routingIntent);
        return routingIntent;
    }

    protected Bundle buildBundle(PushTapAction onTap) {
        /**
         * Util method to let subclasses customize the payload through the push notification intent.
         *
         * Creates an intent to start the routing activity with a bundle describing the new intent
         * the routing activity should launch.
         *
         * Uses FLAG_ACTIVITY_NO_HISTORY so that the routing activity does not appear in the back stack
         * in Android.
         *
         * @param uri The target uri for the notification action
         * @param actionId The actionId for the notification action - either for
         *                 a button or the general notification
         *
         */
        Bundle options = new Bundle();
        options.putCharSequence("tapTarget", TAP_TARGET_NOTIFICATION);
        options.putCharSequence("actionType", onTap.actionType.getTarget());
        options.putCharSequence("uri", onTap.uri);
        options.putCharSequence("messageId", data.messageId);
        options.putCharSequence("campaignId", data.campaignId);
        options.putInt("notificationId", notificationId);
        options.putBoolean("sticky", data.sticky);

        return options;
    }

    protected Bundle buildBundle(PushTapAction onTap, String buttonId, CharSequence buttonLabel) {
    /**
     * Util method to let subclasses customize the payload through the push notification intent.
     *
     * Creates an intent to start the routing activity with a bundle describing the new intent
     * the routing activity should launch.
     *
     * Uses FLAG_ACTIVITY_NO_HISTORY so that the routing activity does not appear in the back stack
     * in Android.
     *
     * @param uri The target uri for the notification action
     * @param actionId The actionId for the notification action - either for
     *                 a button or the general notification
     *
     */
        Bundle options = new Bundle();
        options.putCharSequence("tapTarget", TAP_TARGET_BUTTON);
        options.putCharSequence("buttonId", buttonId);
        options.putCharSequence("label", buttonLabel);
        options.putCharSequence("actionType", onTap.actionType.getTarget());
        options.putCharSequence("uri", onTap.uri);
        options.putCharSequence("messageId", data.messageId);
        options.putCharSequence("campaignId", data.campaignId);
        options.putInt("notificationId", notificationId);
        options.putBoolean("sticky", data.sticky);

        return options;
    }

    protected void verifyIntentPackage(Intent intent) {
        String appPackage = context.getPackageName() + "/com.mixpanel.android.mpmetrics.MixpanelNotificationRouteActivity";
        PackageManager packageManager = context.getPackageManager();

        List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

        if (activities.size() == 0) {
            MPLog.e(LOGTAG, "No activities found to handle: " + appPackage);
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
        } else {
            builder.setDefaults(MPConfig.getInstance(context).getNotificationDefaults());
        }
    }

    protected void maybeSetNotificationBadge() {
        if (data.badgeCount > 0) {
            builder.setNumber(data.badgeCount);
        }
    }

    protected void maybeSetTime() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            builder.setShowWhen(true);
        }

        if (data.timeString == null) {
            builder.setWhen(now);
        } else {
            Date dt = parseDateTime(DATETIME_WITH_TZ, data.timeString);

            if (null == dt) {
                dt = parseDateTime(DATETIME_ZULU_TZ, data.timeString);
            }

            if (null == dt) {
                dt = parseDateTime(DATETIME_NO_TZ, data.timeString);
            }

            if (null == dt) {
                MPLog.d(LOGTAG,"Unable to parse date string into datetime: " + data.timeString);
            } else {
                builder.setWhen(dt.getTime());
            }

        }
    }

    private Date parseDateTime(String format, String datetime) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
            if (format.equals(DATETIME_ZULU_TZ)) {
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            }
            return sdf.parse(datetime);
        } catch (ParseException e) {
            return null;
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
        protected NotificationData(int anIcon, String aLargeIcon, int aWhiteIcon, String anExpandableImageUrl, CharSequence aTitle, CharSequence aSubText, String aMessage, PushTapAction anOnTap, int aColor, List<NotificationButtonData> aButtons, int aBadgeCount, String aChannelId, String aNotificationTag, String aGroupKey, String aTicker, boolean aSticky, String aTimeString, int aVisibility, boolean isSilent, String aCampaignId, String aMessageId) {
            icon = anIcon;
            largeIcon = aLargeIcon;
            whiteIcon = aWhiteIcon;
            expandableImageUrl = anExpandableImageUrl;
            title = aTitle;
            subText = aSubText;
            message = aMessage;
            onTap = anOnTap;
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
        public final PushTapAction onTap;
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
        protected NotificationButtonData(int anIcon, String aLabel, PushTapAction anOnTap, String bId) {
            icon = anIcon;
            label = aLabel;
            onTap = anOnTap;
            buttonId = bId;
        }

        public final int icon;
        public final String label;
        public final PushTapAction onTap;
        public final String buttonId;
    }

    protected static class PushTapAction {
        public PushTapAction(PushTapTarget type, String aUri) {
            actionType = type;
            uri = aUri;
        }

        public PushTapAction(PushTapTarget type) {
            this(type, null);
        }

        public final PushTapTarget actionType;
        public final String uri;
    }

    protected enum PushTapTarget {
        HOMESCREEN("homescreen"),
        URL_IN_BROWSER("browser"),
        URL_IN_WEBVIEW("webview"),
        DEEP_LINK("deeplink");

        private String target;

        PushTapTarget(String envTarget) {
            this.target = envTarget;
        }

        public String getTarget() {
            return target;
        }

        public static PushTapTarget fromString(String target) {
            for (PushTapTarget entry : PushTapTarget.values()) {
                if (entry.getTarget().equals(target)) {
                    return entry;
                }
            }
            throw new IllegalArgumentException("No enum found for string: " + target);
        }
    }

    protected Context context;
    protected Notification.Builder builder;
    protected ResourceIds drawableIds;
    protected long now;
}
