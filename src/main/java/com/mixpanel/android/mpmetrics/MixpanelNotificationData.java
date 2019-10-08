package com.mixpanel.android.mpmetrics;

import android.content.Intent;

import java.util.List;

/* package */ class MixpanelNotificationData {
    public static final int NOT_SET = -1;
    public static final String DEFAULT_CHANNEL_ID = "mp";

    private int mIcon = NOT_SET;
    private int mWhiteIcon = NOT_SET;
    private int mBadgeCount = NOT_SET;
    private int mColor = NOT_SET;
    private String mExpandableImageUrl;
    private CharSequence mTitle;
    private CharSequence mSubText;
    private String mMessage;
    private Intent mIntent;
    private List<MixpanelNotificationButtonData> mButtons;
    private String mChannelId = DEFAULT_CHANNEL_ID;
    private String mTag;
    private String mGroupKey;
    private String mTicker;
    private boolean mSticky;
    private String mTimeString;
    private int mVisibility;
    private boolean mSilent;
    private String mLargeIconName;

    public int getIcon() {
        return mIcon;
    }

    public void setIcon(int icon) {
        this.mIcon = icon;
    }

    public String getLargeIconName() {
        return mLargeIconName;
    }

    public void setLargeIconName(String largeIconName) {
        this.mLargeIconName = largeIconName;
    }

    public int getWhiteIcon() {
        return mWhiteIcon;
    }

    public void setWhiteIcon(int whiteIcon) {
        this.mWhiteIcon = whiteIcon;
    }

    public String getExpandableImageUrl() {
        return mExpandableImageUrl;
    }

    public void setExpandableImageUrl(String expandableImageUrl) {
        this.mExpandableImageUrl = expandableImageUrl;
    }

    public CharSequence getTitle() {
        return mTitle;
    }

    public void setTitle(CharSequence title) {
        this.mTitle = title;
    }

    public CharSequence getSubText() {
        return mSubText;
    }

    public void setSubText(CharSequence subText) {
        this.mSubText = subText;
    }

    public String getMessage() {
        return mMessage;
    }

    public void setMessage(String message) {
        this.mMessage = message;
    }

    public Intent getIntent() {
        return mIntent;
    }

    public void setIntent(Intent intent) {
        this.mIntent = intent;
    }

    public int getColor() {
        return mColor;
    }

    public void setColor(int color) {
        this.mColor = color;
    }

    public List<MixpanelNotificationButtonData> getButtons() {
        return mButtons;
    }

    public void setButtons(List<MixpanelNotificationButtonData> buttons) {
        this.mButtons = buttons;
    }

    public int getBadgeCount() {
        return mBadgeCount;
    }

    public void setBadgeCount(int badgeCount) {
        this.mBadgeCount = badgeCount;
    }

    public String getChannelId() {
        return mChannelId;
    }

    public void setChannelId(String channelId) {
        this.mChannelId = channelId;
    }

    public String getTag() {
        return mTag;
    }

    public void setTag(String tag) {
        this.mTag = tag;
    }

    public String getGroupKey() {
        return mGroupKey;
    }

    public void setGroupKey(String groupKey) {
        this.mGroupKey = groupKey;
    }

    public String getTicker() {
        return mTicker;
    }

    public void setTicker(String ticker) {
        this.mTicker = ticker;
    }

    public boolean isSticky() {
        return mSticky;
    }

    public void setSticky(boolean sticky) {
        this.mSticky = sticky;
    }

    public String getTimeString() {
        return mTimeString;
    }

    public void setTimeString(String timeString) {
        this.mTimeString = timeString;
    }

    public int getVisibility() {
        return mVisibility;
    }

    public void setVisibility(int visibility) {
        this.mVisibility = visibility;
    }

    public boolean isSilent() {
        return mSilent;
    }

    public void setSilent(boolean silent) {
        this.mSilent = silent;
    }

    /* package */ static class MixpanelNotificationButtonData {
        private int mIcon;
        private String mLabel;
        private String mUri;

        public int getIcon() {
            return mIcon;
        }

        public void setIcon(int icon) {
            this.mIcon = icon;
        }

        public String getLabel() {
            return mLabel;
        }

        public void setLabel(String label) {
            this.mLabel = label;
        }

        public String getUri() {
            return mUri;
        }

        public void setUri(String uri) {
            this.mUri = uri;
        }
    }
}
