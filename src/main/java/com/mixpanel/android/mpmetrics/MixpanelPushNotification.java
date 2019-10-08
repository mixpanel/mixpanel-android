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

import com.mixpanel.android.util.ImageStore;
import com.mixpanel.android.util.MPLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MixpanelPushNotification {
    private final String LOGTAG = "MixpanelAPI.MixpanelPushNotification";

    private static final String DATETIME_NO_TZ = "yyyy-MM-dd'T'HH:mm:ss";
    private static final String DATETIME_WITH_TZ = "yyyy-MM-dd'T'HH:mm:ssz";
    private static final String DATETIME_ZULU_TZ = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private Context mContext;
    private ResourceIds mDrawableIds;
    private Notification.Builder mBuilder;
    private long mNow;
    private MixpanelNotificationData mData;

    public MixpanelPushNotification(Context context) {
        this(context, new Notification.Builder(context), System.currentTimeMillis());
    }

    public MixpanelPushNotification(Context context, Notification.Builder builder, long now) {
        this.mContext = context;
        this.mBuilder = builder;
        this.mDrawableIds = getResourceIds(context);
        this.mNow = now;
    }

    /* package */ Notification createNotification(Intent inboundIntent) {
        parseIntent(inboundIntent);

        if (this.mData == null) {
            return null;
        }

        if (this.mData.isSilent()) {
            MPLog.i(LOGTAG, "Notification will not be shown because \'mp_silent = true\'");
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
        CharSequence notificationTitle = inboundIntent.getStringExtra("mp_title");
        CharSequence notificationSubText = inboundIntent.getStringExtra("mp_subtxt");
        final String colorName = inboundIntent.getStringExtra("mp_color");
        final String buttonsJsonStr = inboundIntent.getStringExtra("mp_buttons");
        final String campaignId = inboundIntent.getStringExtra("mp_campaign_id");
        final String messageId = inboundIntent.getStringExtra("mp_message_id");
        final String extraLogData = inboundIntent.getStringExtra("mp");
        final int badgeCount = inboundIntent.getIntExtra("mp_bdgcnt", MixpanelNotificationData.NOT_SET);
        final String channelId = inboundIntent.getStringExtra("mp_channel_id");
        final String notificationTag = inboundIntent.getStringExtra("mp_tag");
        final String groupKey = inboundIntent.getStringExtra("mp_groupkey");
        final String ticker = inboundIntent.getStringExtra("mp_ticker");
        final String stickyString = inboundIntent.getStringExtra("mp_sticky");
        final String timeString = inboundIntent.getStringExtra("mp_time");
        final int visibility = inboundIntent.getIntExtra("mp_visibility", Notification.VISIBILITY_PRIVATE);
        final String silent = inboundIntent.getStringExtra("mp_silent");

        trackCampaignReceived(campaignId, messageId, extraLogData);

        if (message == null) {
            return;
        }

        mData = new MixpanelNotificationData();
        mData.setMessage(message);
        mData.setLargeIconName(largeIconName);
        mData.setExpandableImageUrl(expandableImageURL);
        mData.setBadgeCount(badgeCount);
        mData.setTag(notificationTag);
        mData.setGroupKey(groupKey);
        mData.setTicker(ticker);
        mData.setTimeString(timeString);
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

        if (buttonsJsonStr != null) {
            try {
                JSONArray buttonsArr = new JSONArray(buttonsJsonStr);
                for (int i = 0; i < buttonsArr.length(); i++) {
                    MixpanelNotificationData.MixpanelNotificationButtonData buttonData;
                    buttonData = new MixpanelNotificationData.MixpanelNotificationButtonData();
                    JSONObject buttonObj = buttonsArr.getJSONObject(i);

                    int btnIcon = MixpanelNotificationData.NOT_SET;
                    if (buttonObj.has("icnm")) {
                        String btnIconName = buttonObj.getString("icnm");
                        if (mDrawableIds.knownIdName(btnIconName)) {
                            btnIcon = mDrawableIds.idFromName(btnIconName);
                        }
                    }
                    buttonData.setIcon(btnIcon);

                    final String btnLabel = buttonObj.getString("lbl");
                    buttonData.setLabel(btnLabel);
                    final String btnUri = buttonObj.getString("uri");
                    buttonData.setUri(btnUri);
                    buttons.add(buttonData);
                }
            } catch (JSONException e) {
                MPLog.e(LOGTAG, "Exception parsing buttons payload", e);
            }
        }
        mData.setButtons(buttons);

        Uri uri = null;
        if (uriString != null) {
            uri = Uri.parse(uriString);
        }
        final Intent intent;
        if (uri == null) {
            intent = getDefaultIntent();
        } else {
            intent = buildIntentForUri(uri);
        }

        final Intent notificationIntent = buildNotificationIntent(intent, campaignId, messageId, extraLogData);
        mData.setIntent(notificationIntent);
    }

    protected void buildNotificationFromData() {
        final PendingIntent contentIntent = PendingIntent.getActivity(
                mContext,
                0,
                mData.getIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        mBuilder.
                setContentTitle(mData.getTitle()).
                setContentText(mData.getMessage()).
                setTicker(mData.getTicker()== null ? mData.getMessage() : mData.getTicker()).
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
                mBuilder.addAction(this.createAction(btn.getIcon(), btn.getLabel(), btn.getUri()));
            }
        }
    }

    private PendingIntent createActionIntent(String uri) {
        return PendingIntent.getActivity(
                mContext,
                0,
                new Intent(Intent.ACTION_VIEW, Uri.parse(uri)),
                PendingIntent.FLAG_UPDATE_CURRENT
        );
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

    private ApplicationInfo getAppInfo() {
        try {
            return mContext.getPackageManager().getApplicationInfo(mContext.getPackageName(), 0);
        } catch (final PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /* package */ Intent buildNotificationIntent(Intent intent, String campaignId, String messageId, String extraLogData) {
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

    /* package */ Intent getDefaultIntent() {
        return mContext.getPackageManager().getLaunchIntentForPackage(mContext.getPackageName());
    }

    /* package */ Intent buildIntentForUri(Uri uri) {
        return new Intent(Intent.ACTION_VIEW, uri);
    }

    /* package */ MixpanelNotificationData getData() {
        return mData;
    }

    /* package */ Bitmap getBitmapFromResourceId(int resourceId) {
        return BitmapFactory.decodeResource(mContext.getResources(), resourceId);
    }

    @TargetApi(20)
    /* package */ Notification.Action createAction(int icon, CharSequence title, String uri) {
        return (new Notification.Action.Builder(icon, title, createActionIntent(uri))).build();
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
