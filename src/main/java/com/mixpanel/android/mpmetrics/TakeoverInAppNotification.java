package com.mixpanel.android.mpmetrics;

import android.app.Activity;
import android.os.Parcel;
import android.os.Parcelable;

import com.mixpanel.android.util.JSONUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Represents a takeover in-app notification delivered from Mixpanel. Under ordinary circumstances,
 * most code won't have to interact with this class directly, but rather will display
 * InAppNotifications using {@link com.mixpanel.android.mpmetrics.MixpanelAPI.People#showNotificationIfAvailable(Activity)}
 */
public class TakeoverInAppNotification extends InAppNotification {

    private final ArrayList<InAppButton> mButtons;
    private final int mCloseButtonColor;
    private final String mTitle;
    private final int mTitleColor;
    private final boolean mShouldFadeImage;

    public TakeoverInAppNotification(Parcel in) {
        super(in);
        mButtons = in.createTypedArrayList(InAppButton.CREATOR);
        mCloseButtonColor = in.readInt();
        mTitle = in.readString();
        mTitleColor = in.readInt();
        mShouldFadeImage = in.readByte() != 0;
    }

    /* package */ TakeoverInAppNotification(JSONObject description) throws BadDecideObjectException {
        super(description);

        try {
            JSONArray buttonsArray = description.getJSONArray("buttons");
            mButtons = new ArrayList<>();
            for (int i = 0; i < buttonsArray.length(); i++) {
                JSONObject buttonJson = (JSONObject) buttonsArray.get(i);
                mButtons.add(new InAppButton(buttonJson));
            }
            mCloseButtonColor = description.getInt("close_color");
            mTitle = JSONUtils.optionalStringKey(description, "title");
            mTitleColor = description.optInt("title_color");
            mShouldFadeImage = getExtras().getBoolean("image_fade");
        } catch (final JSONException e) {
            throw new BadDecideObjectException("Notification JSON was unexpected or bad", e);
        }
    }

    public boolean hasTitle() {
        return mTitle != null;
    }

    public String getTitle() {
        return mTitle;
    }

    public int getTitleColor() {
        return mTitleColor;
    }

    public int getCloseColor() {
        return mCloseButtonColor;
    }

    public InAppButton getButton(int index) {
        return mButtons.size() > index ? mButtons.get(index) : null;
    }

    public int getNumButtons() {
        return mButtons.size();
    }

    public boolean setShouldShowShadow() {
        return mShouldFadeImage;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeTypedList(mButtons);
        dest.writeInt(mCloseButtonColor);
        dest.writeString(mTitle);
        dest.writeInt(mTitleColor);
        dest.writeByte((byte) (mShouldFadeImage ? 1 : 0));
    }

    @Override
    public Type getType() {
        return Type.TAKEOVER;
    }

    public static final Parcelable.Creator<TakeoverInAppNotification> CREATOR = new Parcelable.Creator<TakeoverInAppNotification>() {

        @Override
        public TakeoverInAppNotification createFromParcel(Parcel source) {
            return new TakeoverInAppNotification(source);
        }

        @Override
        public TakeoverInAppNotification[] newArray(int size) {
            return new TakeoverInAppNotification[size];
        }
    };
}
