package com.mixpanel.android.mpmetrics;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class InAppButton implements Parcelable {

    private static final String LOGTAG = "MixpanelAPI.InAppButton";

    private JSONObject mDescription;
    private String mText;
    private int mTextColor;
    private int mBackgroundColor;
    private int mBorderColor;
    private String mCtaUrl;

    public InAppButton(Parcel in) {
        JSONObject temp = new JSONObject();
        try {
            temp = new JSONObject(in.readString());
        } catch (JSONException e) {
            Log.e(LOGTAG, "Error reading JSON when creating InAppButton from Parcel");
        }
        mDescription = temp;
        mText = in.readString();
        mTextColor = in.readInt();
        mBackgroundColor = in.readInt();
        mBorderColor = in.readInt();
        mCtaUrl = in.readString();
    }

    /* package */ InAppButton(JSONObject description) throws JSONException {
        mDescription = description;
        mText = description.getString("text");
        mTextColor = description.getInt("text_color");
        mBackgroundColor = description.getInt("bg_color");
        mBorderColor = description.getInt("border_color");
        mCtaUrl = description.getString("cta_url");
    }

    public String getText() {
        return mText;
    }

    public int getTextColor() {
        return mTextColor;
    }

    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    public int getBorderColor() {
        return mBorderColor;
    }

    public String getCtaUrl() {
        return mCtaUrl;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mDescription.toString());
        dest.writeString(mText);
        dest.writeInt(mTextColor);
        dest.writeInt(mBackgroundColor);
        dest.writeInt(mBorderColor);
        dest.writeString(mCtaUrl);
    }

    @Override
    public String toString() {
        return mDescription.toString();
    }

    public static final Parcelable.Creator<InAppButton> CREATOR = new Parcelable.Creator<InAppButton>() {

        @Override
        public InAppButton createFromParcel(Parcel source) {
            return new InAppButton(source);
        }

        @Override
        public InAppButton[] newArray(int size) {
            return new InAppButton[size];
        }
    };
}
