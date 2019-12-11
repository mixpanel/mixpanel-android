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

public class MixpanelPushNotification {
    private final String LOGTAG = "MixpanelAPI.MixpanelPushNotification";

    protected final static String TAP_TARGET_BUTTON = "button";
    protected final static String TAP_TARGET_NOTIFICATION = "notification";

    private final static String VISIBILITY_PRIVATE = "VISIBILITY_PRIVATE";
    private final static String VISIBILITY_PUBLIC = "VISIBILITY_PUBLIC";
    private final static String VISIBILITY_SECRET = "VISIBILITY_SECRET";

    protected final int ROUTING_REQUEST_CODE;

    private static final String DATETIME_NO_TZ = "yyyy-MM-dd'T'HH:mm:ss";
    private static final String DATETIME_WITH_TZ = "yyyy-MM-dd'T'HH:mm:ssz";
    private static final String DATETIME_ZULU_TZ = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private Context mContext;
    private ResourceIds mDrawableIds;
    private Notification.Builder mBuilder;
    private long mNow;
    private MixpanelNotificationData mData;
    private int notificationId;
    private boolean hasOnTapError = false;

    public MixpanelPushNotification(Context context) {
        this(context, new Notification.Builder(context), System.currentTimeMillis());
    }

    public MixpanelPushNotification(Context context, Notification.Builder builder, long now) {
        this.mContext = context;
        this.mBuilder = builder;
        this.mDrawableIds = getResourceIds(context);
        this.mNow = now;
        this.ROUTING_REQUEST_CODE = (int) now;
        this.notificationId = (int) now;
    }

    /* package */ Notification createNotification(Intent inboundIntent) {
        parseIntent(inboundIntent);

        if (this.mData == null) {
            return null;
        }

        if (this.mData.isSilent()) {
            MPLog.d(LOGTAG, "Notification will not be shown because \'mp_silent = true\'");
            return null;
        }

        if (this.mData.getMessage() == null) {
            MPLog.d(LOGTAG, "Notification will not be shown because 'mp_message' was null");
            return null;
        }

        if (this.mData.getMessage().equals("")) {
            MPLog.d(LOGTAG, "Notification will not be shown because 'mp_message' was empty");
            return null;
        }

        buildNotificationFromData();

        Notification n;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            n = mBuilder.build();
        } else {
            n = mBuilder.getNotification();
        }

        if (!mData.isSticky()) {
            n.flags |= Notification.FLAG_AUTO_CANCEL;
        }

        return n;
    }

    /* package */ void parseIntent(Intent inboundIntent) {
        List<MixpanelNotificationData.MixpanelNotificationButtonData> buttons = new ArrayList<>();
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

        mData = new MixpanelNotificationData();
        mData.setMessage(message);
        mData.setLargeIconName(largeIconName);
        mData.setExpandableImageUrl(expandableImageURL);
        mData.setTag(notificationTag);
        mData.setGroupKey(groupKey);
        mData.setTicker(ticker);
        mData.setTimeString(timeString);
        mData.setCampaignId(campaignId);
        mData.setMessageId(messageId);
        mData.setButtons(buildButtons(buttonsJsonStr));

        int badgeCount = MixpanelNotificationData.NOT_SET;
        if (null != badgeCountStr) {
            try {
                badgeCount = Integer.parseInt(badgeCountStr);
                if (badgeCount < 0) {
                    badgeCount = 0;
                }
            } catch (NumberFormatException e) {
                badgeCount = 0;
            }
        }
        mData.setBadgeCount(badgeCount);

        int visibility = Notification.VISIBILITY_PRIVATE;
        if (null != visibilityStr) {
            switch (visibilityStr) {
                case MixpanelPushNotification.VISIBILITY_SECRET:
                    visibility = Notification.VISIBILITY_SECRET;
                    break;
                case MixpanelPushNotification.VISIBILITY_PUBLIC:
                    visibility = Notification.VISIBILITY_PUBLIC;
                    break;
                case MixpanelPushNotification.VISIBILITY_PRIVATE:
                default:
                    visibility = Notification.VISIBILITY_PRIVATE;
            }
        }
        mData.setVisibility(visibility);

        if (channelId != null) {
            mData.setChannelId(channelId);
        }

        int color = MixpanelNotificationData.NOT_SET;
        if (colorName != null) {
            try {
                color = Color.parseColor(colorName);
            } catch (IllegalArgumentException e) {}
        }
        mData.setColor(color);

        if (notificationSubText != null && notificationSubText.length() == 0) {
            notificationSubText = null;
        }
        mData.setSubText(notificationSubText);

        boolean isSilent = silent != null && silent.equals("true");
        mData.setSilent(isSilent);

        boolean sticky = stickyString != null && stickyString.equals("true");
        mData.setSticky(sticky);

        int notificationIcon = MixpanelNotificationData.NOT_SET;
        if (iconName != null) {
            if (mDrawableIds.knownIdName(iconName)) {
                notificationIcon = mDrawableIds.idFromName(iconName);
            }
        }
        if (notificationIcon == MixpanelNotificationData.NOT_SET) {
            notificationIcon = getDefaultIcon();
        }
        mData.setIcon(notificationIcon);

        int whiteNotificationIcon = MixpanelNotificationData.NOT_SET;
        if (whiteIconName != null) {
            if (mDrawableIds.knownIdName(whiteIconName)) {
                whiteNotificationIcon = mDrawableIds.idFromName(whiteIconName);
            }
        }
        mData.setWhiteIcon(whiteNotificationIcon);

        if (notificationTitle == null || notificationTitle.length() == 0) {
            notificationTitle = getDefaultTitle();
        }
        mData.setTitle(notificationTitle);

        MixpanelNotificationData.PushTapAction onTap = buildOnTap(onTapStr);
        if (null == onTap) {
            onTap = buildOnTapFromURI(uriString);
        }
        if (null == onTap) {
            onTap = getDefaultOnTap();
        }
        mData.setOnTap(onTap);
    }

    protected void buildNotificationFromData() {
        final PendingIntent contentIntent = PendingIntent.getActivity(
                mContext,
                ROUTING_REQUEST_CODE,
                getRoutingIntent(mData.getOnTap()),
                PendingIntent.FLAG_CANCEL_CURRENT
        );

        mBuilder.
                setContentTitle(mData.getTitle()).
                setContentText(mData.getMessage()).
                setTicker(null == mData.getTicker() ? mData.getMessage() : mData.getTicker()).
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

    protected void maybeSetSubText() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && mData.getSubText() != null) {
            mBuilder.setSubText(mData.getSubText());
        }
    }

    protected void maybeSetNotificationBarIcon() {
        // For Android 5.0+ (Lollipop), any non-transparent pixels are turned white, so users generally specify
        // icons for these devices and regular full-color icons for older devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mData.getWhiteIcon() != MixpanelNotificationData.NOT_SET) {
            mBuilder.setSmallIcon(mData.getWhiteIcon());
        } else {
            mBuilder.setSmallIcon(mData.getIcon());
        }
    }

    protected void maybeSetLargeIcon() {
        if (mData.getLargeIconName() != null) {
            if (mDrawableIds.knownIdName(mData.getLargeIconName())) {
                mBuilder.setLargeIcon(getBitmapFromResourceId(mDrawableIds.idFromName(mData.getLargeIconName())));
            } else if (mData.getLargeIconName().startsWith("http")) {
                Bitmap imageBitmap = getBitmapFromUrl(mData.getLargeIconName());
                if (imageBitmap != null) {
                    mBuilder.setLargeIcon(imageBitmap);
                }
            } else {
                MPLog.d(LOGTAG, "large icon data was sent but did match a resource name or a valid url: " + mData.getLargeIconName());
            }
        }
    }

    protected void maybeSetExpandableNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (null != mData.getExpandableImageUrl() && mData.getExpandableImageUrl().startsWith("http")) {
                try {
                    Bitmap imageBitmap = getBitmapFromUrl(mData.getExpandableImageUrl());
                    if (imageBitmap == null) {
                        setBigTextStyle(mData.getMessage());
                    } else {
                        setBigPictureStyle(imageBitmap);
                    }
                } catch (Exception e) {
                    setBigTextStyle(mData.getMessage());
                }
            } else {
                setBigTextStyle(mData.getMessage());
            }
        }
    }

    protected void setBigTextStyle(String message) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mBuilder.setStyle(new Notification.BigTextStyle().bigText(message));
        }
    }

    protected void setBigPictureStyle(Bitmap imageBitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mBuilder.setStyle(new Notification.BigPictureStyle().bigPicture(imageBitmap));
        }
    }

    protected void maybeSetCustomIconColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mData.getColor() != MixpanelNotificationData.NOT_SET) {
                mBuilder.setColor(mData.getColor());
            }
        }
    }

    protected void maybeAddActionButtons() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            for (int i = 0; i < mData.getButtons().size(); i++) {
                MixpanelNotificationData.MixpanelNotificationButtonData btn = mData.getButtons().get(i);
                mBuilder.addAction(this.createAction(btn.getLabel(), btn.getOnTap(), btn.getId(), i + 1));
            }
        }
    }

    protected List<MixpanelNotificationData.MixpanelNotificationButtonData> buildButtons(String buttonsJsonStr) {
        List<MixpanelNotificationData.MixpanelNotificationButtonData> buttons = new ArrayList<>();
        if (null != buttonsJsonStr) {
            try {
                JSONArray buttonsArr = new JSONArray(buttonsJsonStr);
                for (int i = 0; i < buttonsArr.length(); i++) {
                    JSONObject buttonObj = buttonsArr.getJSONObject(i);

                    // handle button label
                    final String btnLabel = buttonObj.getString("lbl");

                    // handle button action
                    final MixpanelNotificationData.PushTapAction pushAction = buildOnTap(buttonObj.getString("ontap"));

                    //handle button id
                    final String btnId = buttonObj.getString("id");

                    if (pushAction == null || btnLabel == null || btnId == null) {
                        MPLog.d(LOGTAG, "Null button data received. No buttons will be rendered.");
                    } else {
                        buttons.add(new MixpanelNotificationData.MixpanelNotificationButtonData(btnLabel, pushAction, btnId));
                    }
                }
            } catch (JSONException e) {
                MPLog.e(LOGTAG, "Exception parsing buttons payload", e);
            }
        }

        return buttons;
    }

    protected MixpanelNotificationData.PushTapAction buildOnTap(String onTapStr) {
        MixpanelNotificationData.PushTapAction onTap = null;
        if (null != onTapStr) {
            try {
                final JSONObject onTapJSON = new JSONObject(onTapStr);
                final String typeFromJSON = onTapJSON.getString("type");

                if (!typeFromJSON.equals(MixpanelNotificationData.PushTapTarget.HOMESCREEN.getTarget())) {
                    final String uriFromJSON = onTapJSON.getString("uri");
                    onTap = new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapTarget.fromString(typeFromJSON), uriFromJSON);
                } else {
                    onTap = new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapTarget.fromString(typeFromJSON));
                }

                if (onTap.getActionType().getTarget().equals(MixpanelNotificationData.PushTapTarget.ERROR.getTarget())) {
                    hasOnTapError = true;
                    onTap = new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapTarget.HOMESCREEN);
                }
            } catch (JSONException e){
                MPLog.d(LOGTAG, "Exception occurred while parsing ontap");
                onTap = null;
            }
        }

        return onTap;
    }

    protected MixpanelNotificationData.PushTapAction buildOnTapFromURI(String uriString) {
        MixpanelNotificationData.PushTapAction onTap = null;

        if (null != uriString) {
            onTap = new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapTarget.URL_IN_BROWSER, uriString);
        }

        return onTap;
    }

    protected MixpanelNotificationData.PushTapAction getDefaultOnTap() {
        return new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapTarget.HOMESCREEN);
    }

    @TargetApi(20)
    protected Notification.Action createAction(CharSequence title, MixpanelNotificationData.PushTapAction onTap, String actionId, int index) {
        return (new Notification.Action.Builder(MixpanelNotificationData.NOT_SET, title, createActionIntent(onTap, actionId, title, index))).build();
    }

    protected PendingIntent createActionIntent(MixpanelNotificationData.PushTapAction onTap, String buttonId, CharSequence label, int index) {
        Intent routingIntent = getRoutingIntent(onTap, buttonId, label);
        return PendingIntent.getActivity(mContext, ROUTING_REQUEST_CODE + index, routingIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    protected Intent getRoutingIntent(MixpanelNotificationData.PushTapAction onTap, String buttonId, CharSequence label) {
        Bundle options = buildBundle(onTap, buttonId, label);

        Intent routingIntent = new Intent().
                setClass(mContext, MixpanelNotificationRouteActivity.class).
                putExtras(options).
                setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        return routingIntent;
    }

    protected Intent getRoutingIntent(MixpanelNotificationData.PushTapAction onTap) {
        Bundle options = buildBundle(onTap);

        Intent routingIntent = new Intent().
                setClass(mContext, MixpanelNotificationRouteActivity.class).
                putExtras(options).
                setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        return routingIntent;
    }

    /**
     * Util method to let subclasses customize the payload through the push notification intent.
     *
     * Creates an intent to start the routing activity with a bundle describing the new intent
     * the routing activity should launch.
     *
     * Uses FLAG_ACTIVITY_NO_HISTORY so that the routing activity does not appear in the back stack
     * in Android.
     *
     * @param onTap The PushTapAction for the intent this bundle is a member of
     *
     */
    protected Bundle buildBundle(MixpanelNotificationData.PushTapAction onTap) {
        Bundle options = new Bundle();
        options.putCharSequence("tapTarget", TAP_TARGET_NOTIFICATION);
        options.putCharSequence("actionType", onTap.getActionType().getTarget());
        options.putCharSequence("uri", onTap.getUri());
        options.putCharSequence("messageId", mData.getMessageId());
        options.putCharSequence("campaignId", mData.getCampaignId());
        options.putInt("notificationId", notificationId);
        options.putBoolean("sticky", mData.isSticky());
        options.putCharSequence("tag", mData.getTag());

        return options;
    }

    /**
     * Util method to let subclasses customize the payload through the push notification intent.
     *
     * Creates an intent to start the routing activity with a bundle describing the new intent
     * the routing activity should launch.
     *
     * Uses FLAG_ACTIVITY_NO_HISTORY so that the routing activity does not appear in the back stack
     * in Android.
     *
     * @param onTap The PushTapAction for the intent this bundle is a member of
     * @param buttonId The buttonId for the Notification action this bundle will be a member of
     * @param buttonLabel The label for the button that will appear in the notification which
     *                    this bundle will me a member of
     *
     */
    protected Bundle buildBundle(MixpanelNotificationData.PushTapAction onTap, String buttonId, CharSequence buttonLabel) {
        Bundle options = buildBundle(onTap);
        options.putCharSequence("tapTarget", TAP_TARGET_BUTTON);
        options.putCharSequence("buttonId", buttonId);
        options.putCharSequence("label", buttonLabel);
        return options;
    }

    protected void maybeSetChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager mNotificationManager =
                    (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

            String channelId = mData.getChannelId() == null ? MPConfig.getInstance(mContext).getNotificationChannelId() : mData.getChannelId();
            String channelName = MPConfig.getInstance(mContext).getNotificationChannelName();

            NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
            mNotificationManager.createNotificationChannel(channel);

            mBuilder.setChannelId(channelId);
        } else {
            mBuilder.setDefaults(MPConfig.getInstance(mContext).getNotificationDefaults());
        }
    }

    protected void maybeSetNotificationBadge() {
        if (mData.getBadgeCount() > 0) {
            mBuilder.setNumber(mData.getBadgeCount());
        }
    }

    protected void maybeSetTime() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mBuilder.setShowWhen(true);
        }

        if (mData.getTimeString() == null) {
            mBuilder.setWhen(mNow);
        } else {
            Date dt = parseDateTime(DATETIME_WITH_TZ, mData.getTimeString());

            if (null == dt) {
                dt = parseDateTime(DATETIME_ZULU_TZ, mData.getTimeString());
            }

            if (null == dt) {
                dt = parseDateTime(DATETIME_NO_TZ, mData.getTimeString());
            }

            if (null == dt) {
                MPLog.d(LOGTAG,"Unable to parse date string into datetime: " + mData.getTimeString());
            } else {
                mBuilder.setWhen(dt.getTime());
            }
        }
    }

    protected void maybeSetVisibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBuilder.setVisibility(mData.getVisibility());
        }
    }

    protected ApplicationInfo getAppInfo() {
        try {
            return mContext.getPackageManager().getApplicationInfo(mContext.getPackageName(), 0);
        } catch (final PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    protected CharSequence getDefaultTitle() {
        ApplicationInfo appInfo = getAppInfo();
        if (null != appInfo) {
            return mContext.getPackageManager().getApplicationLabel(appInfo);
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

    protected int getNotificationId(){
        return this.notificationId;
    }

    protected boolean isValid() {
        return mData != null && !hasOnTapError;
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

    /* package */ MixpanelNotificationData getData() {
        return mData;
    }

    /* package */ Bitmap getBitmapFromResourceId(int resourceId) {
        return BitmapFactory.decodeResource(mContext.getResources(), resourceId);
    }

    /* package */ Bitmap getBitmapFromUrl(String url) {
        ImageStore is = new ImageStore(mContext, "MixpanelPushNotification");
        try {
            return is.getImage(url);
        } catch (ImageStore.CantGetImageException e) {
            return null;
        }
    }

    /* package */ ResourceIds getResourceIds(Context context) {
        final MPConfig config = MPConfig.getInstance(context);
        String resourcePackage = config.getResourcePackageName();
        if (null == resourcePackage) {
            resourcePackage = context.getPackageName();
        }
        return new ResourceReader.Drawables(resourcePackage, context);
    }
}
