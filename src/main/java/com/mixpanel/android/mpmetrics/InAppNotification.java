package com.mixpanel.android.mpmetrics;

import android.graphics.Bitmap;
import android.media.Image;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents an in-app notification delivered from Mixpanel.
 */
public class InAppNotification {
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

    /* package */public InAppNotification(JSONObject description) throws BadDecideObjectException {
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
        return twoXFromUrl(mImageUrl);
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

    /* package */ static String twoXFromUrl(String url) {
        final Matcher matcher = FILE_EXTENSION_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.replaceFirst("@2x$1");
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
