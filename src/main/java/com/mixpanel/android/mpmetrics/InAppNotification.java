package com.mixpanel.android.mpmetrics;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a fullscreen in-app notification delivered from Mixpanel.
 */
public class InAppNotification implements Parcelable {
    public enum Type {
        UNKNOWN {
            @Override
            public String toString() {
                return "*unknown_type*";
            }
        },
        MINI {
            @Override
            public String toString() {
                return "mini";
            }
        },
        TAKEOVER {
            @Override
            public String toString() {
                return "takeover";
            }
        };
    }

    public InAppNotification(Parcel in) {
        JSONObject temp = new JSONObject();
        try {
            temp = new JSONObject(in.readString());
        } catch (JSONException e) {
            Log.e(LOGTAG, "Error reading JSON when creating InAppNotification from Parcel");
        }
        mDescription = temp; // mDescription is final
        mId = in.readInt();
        mMessageId = in.readInt();
        mType = in.readString();
        mTitle = in.readString();
        mBody = in.readString();
        mImageUrl = in.readString();
        mCallToAction = in.readString();
        mCallToActionUrl = in.readString();

        mImage = (Bitmap) in.readParcelable(Bitmap.class.getClassLoader());
    }

    /* package */ InAppNotification(JSONObject description) throws BadDecideObjectException {
        try {
            mDescription = description;
            mId = description.getInt("id");
            mMessageId = description.getInt("message_id");
            mType = description.getString("type");
            mTitle = description.getString("title");
            mBody = description.getString("body");
            mImageUrl = description.getString("image_url");
            mImage = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888);

            // "cta" here is an unfortunate abbreviation of "Call To Action"
            mCallToAction = description.getString("cta");
            mCallToActionUrl = description.getString("cta_url");
        } catch (final JSONException e) {
            throw new BadDecideObjectException("Notification JSON was unexpected or bad", e);
        }
    }

    /* package */ String toJSON() {
        return mDescription.toString();
    }

    /* package */ JSONObject getCampaignProperties() {
        final JSONObject ret = new JSONObject();
        try {
            ret.put("campaign_id", getId());
            ret.put("message_id", getMessageId());
            ret.put("message_type", "inapp");
            ret.put("message_subtype", mType);
        } catch (JSONException e) {
            Log.e(LOGTAG, "Impossible JSON Exception", e);
        }

        return ret;
    }

    public int getId() {
        return mId;
    }

    public int getMessageId() {
        return mMessageId;
    }

    public Type getType() {
        if (Type.MINI.toString().equals(mType)) {
            return Type.MINI;
        }
        if (Type.TAKEOVER.toString().equals(mType)) {
            return Type.TAKEOVER;
        }
        return Type.UNKNOWN;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getBody() {
        return mBody;
    }

    public String getImageUrl() {
        return mImageUrl;
    }

    public String getImage2xUrl() {
        return sizeSuffixUrl(mImageUrl, "@2x");
    }

    public String getImage4xUrl() {
        return sizeSuffixUrl(mImageUrl, "@4x");
    }

    public String getCallToAction() {
        return mCallToAction;
    }

    public String getCallToActionUrl() {
        return mCallToActionUrl;
    }

    /* package */ void setImage(final Bitmap image) {
        mImage = image;
    }

    public Bitmap getImage() {
        return mImage;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mDescription.toString());
        dest.writeInt(mId);
        dest.writeInt(mMessageId);
        dest.writeString(mType);
        dest.writeString(mTitle);
        dest.writeString(mBody);
        dest.writeString(mImageUrl);
        dest.writeString(mCallToAction);
        dest.writeString(mCallToActionUrl);
        dest.writeParcelable(mImage, flags);
    }

    public static final Parcelable.Creator<InAppNotification> CREATOR = new Parcelable.Creator<InAppNotification>() {

        @Override
        public InAppNotification createFromParcel(Parcel source) {
            return new InAppNotification(source);
        }

        @Override
        public InAppNotification[] newArray(int size) {
            return new InAppNotification[size];
        }
    };

    /* package */ static String sizeSuffixUrl(String url, String sizeSuffix) {
        final Matcher matcher = FILE_EXTENSION_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.replaceFirst(sizeSuffix + "$1");
        } else {
            return url;
        }
    }

    private Bitmap mImage;

    private final JSONObject mDescription;
    private final int mId;
    private final int mMessageId;
    private final String mType;
    private final String mTitle;
    private final String mBody;
    private final String mImageUrl;
    private final String mCallToAction;
    private final String mCallToActionUrl;

    private static final String LOGTAG = "MixpanelAPI InAppNotification";
    private static final Pattern FILE_EXTENSION_PATTERN = Pattern.compile("(\\.[^./]+$)");
}
