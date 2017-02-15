package com.mixpanel.android.mpmetrics;

import android.app.Activity;
import android.os.Parcel;
import android.os.Parcelable;

import com.mixpanel.android.util.JSONUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a mini in-app notification delivered from Mixpanel. Under ordinary circumstances,
 * most code won't have to interact with this class directly, but rather will display
 * InAppNotifications using {@link com.mixpanel.android.mpmetrics.MixpanelAPI.People#showNotificationIfAvailable(Activity)}
 */
public class MiniInAppNotification extends InAppNotification {

    private final String mCtaUrl;
    private final int mImageTintColor;
    private final int mBorderColor;

    public MiniInAppNotification(Parcel in) {
        super(in);
        mCtaUrl = in.readString();
        mImageTintColor = in.readInt();
        mBorderColor = in.readInt();
    }

    /* package */ MiniInAppNotification(JSONObject description) throws BadDecideObjectException {
        super(description);

        try {
            mCtaUrl = JSONUtils.optionalStringKey(description, "cta_url");
            mImageTintColor = description.getInt("image_tint_color");
            mBorderColor = description.getInt("border_color");
        } catch (final JSONException e) {
            throw new BadDecideObjectException("Notification JSON was unexpected or bad", e);
        }
    }

    public String getCtaUrl() {
        return mCtaUrl;
    }

    public int getImageTintColor() {
        return mImageTintColor;
    }

    public int getBorderColor() {
        return mBorderColor;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(mCtaUrl);
        dest.writeInt(mImageTintColor);
        dest.writeInt(mBorderColor);
    }

    @Override
    public Type getType() {
        return Type.MINI;
    }

    public static final Parcelable.Creator<MiniInAppNotification> CREATOR = new Parcelable.Creator<MiniInAppNotification>() {

        @Override
        public MiniInAppNotification createFromParcel(Parcel source) {
            return new MiniInAppNotification(source);
        }

        @Override
        public MiniInAppNotification[] newArray(int size) {
            return new MiniInAppNotification[size];
        }
    };
}
