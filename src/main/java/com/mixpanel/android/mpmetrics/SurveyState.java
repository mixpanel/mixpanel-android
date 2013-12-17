package com.mixpanel.android.mpmetrics;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.mixpanel.android.surveys.SurveyActivity;

/**
 * This is a class intended for internal use by the library.
 * Users of the library should not interact with it directly.
 */
public class SurveyState implements Parcelable {
    public static void proposeSurvey(final Survey s,
            final Activity parentActivity,
            final String distinctId,
            final String token,
            Bitmap background,
            int highlightColor) {
        final long currentTime = System.currentTimeMillis();
        final long deltaTime = currentTime - sSurveyStateLockMillis;
        synchronized(sSurveyStateLock) {
            if (sShowingIntentId > 0 && deltaTime > MAX_LOCK_TIME_MILLIS) {
                Log.i(LOGTAG, "SurveyState set long, long ago, without showing.");
                sSurveyState = null;
            }
            if (null == sSurveyState) {
                sSurveyState = new SurveyState(s, distinctId, token, background, highlightColor);
                sSurveyState.initializeAndLaunch(parentActivity);
            } else {
                if (MPConfig.DEBUG) Log.d(LOGTAG, "Already showing (or cooking) a survey, declining to show another.");
            }
        } // synchronized
    }

    public static void releaseSurvey(int intentId) {
        synchronized(sSurveyStateLock) {
            if (intentId == sShowingIntentId) {
                sShowingIntentId = -1;
                sSurveyState = null;
            }
        }
    }

    public static SurveyState claimSurveyState(SurveyState proposed, int intentId) {
        assert(null == proposed);
        final long currentTime = System.currentTimeMillis();
        final long deltaTime = currentTime - sIntentIdLockMillis;
        synchronized(sSurveyStateLock) {
            if (sShowingIntentId > 0 && deltaTime > MAX_LOCK_TIME_MILLIS) {
                Log.i(LOGTAG, "Survey activity claimed but never released lock, possible force quit.");
                sShowingIntentId = -1;
            }
            if (sShowingIntentId > 0 && sShowingIntentId != intentId) {
                return null;
            } else if (null != proposed) {
                sShowingIntentId = intentId;
                sSurveyState = proposed;
                return proposed;
            } else if (sSurveyState == null) {
                return null;
            } else {
                sShowingIntentId = intentId;
                return sSurveyState;
            }
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        final Bundle out = new Bundle();
        out.putString(DISTINCT_ID_BUNDLE_KEY, mDistinctId);
        out.putString(TOKEN_BUNDLE_KEY, mToken);
        out.putInt(HIGHLIGHT_COLOR_BUNDLE_KEY, mHighlightColor);
        out.putParcelable(ANSWERS_BUNDLE_KEY, mAnswers);

        byte[] backgroundCompressed = null;
        if (mBackground != null) {
            final ByteArrayOutputStream bs = new ByteArrayOutputStream();
            mBackground.compress(Bitmap.CompressFormat.PNG, 20, bs);
            backgroundCompressed = bs.toByteArray();
        }
        out.putByteArray(BACKGROUND_COMPRESSED_BUNDLE_KEY, backgroundCompressed);

        final String surveyJson = mSurvey.toJSON();
        out.putString(SURVEY_BUNDLE_KEY, surveyJson);

        dest.writeBundle(out);
    }

    public Survey getSurvey() {
        return mSurvey;
    }

    public String getDistinctId() {
       return mDistinctId;
    }

    public String getToken() {
        return mToken;
    }

    public Bitmap getBackground() {
        return mBackground;
    }

    public AnswerMap getAnswers() {
        return mAnswers;
    }

    public int getHighlightColor() {
        return mHighlightColor;
    }

    // Package access for testing only- DO NOT CALL in library code
    /* package */ SurveyState(final Survey s, final String distinctId, final String token, Bitmap background, int highlightColor) {
        mSurvey = s;
        mDistinctId = distinctId;
        mToken = token;
        mAnswers = new AnswerMap();
        mBackground = background;
        mHighlightColor = highlightColor;
    }

    // Bundle must have the same ClassLoader that loaded this constructor.
    private SurveyState(Bundle read) {
        mDistinctId = read.getString(DISTINCT_ID_BUNDLE_KEY);
        mToken = read.getString(TOKEN_BUNDLE_KEY);
        mHighlightColor = read.getInt(HIGHLIGHT_COLOR_BUNDLE_KEY);
        mAnswers = read.getParcelable(ANSWERS_BUNDLE_KEY);

        final byte[] backgroundCompressed = read.getByteArray(BACKGROUND_COMPRESSED_BUNDLE_KEY);
        if (null != backgroundCompressed) {
            mBackground = BitmapFactory.decodeByteArray(backgroundCompressed, 0, backgroundCompressed.length);
        } else {
            mBackground = null;
        }

        final String surveyJsonString = read.getString(SURVEY_BUNDLE_KEY);
        try {
            final JSONObject surveyJson = new JSONObject(surveyJsonString);
            mSurvey = new Survey(surveyJson);
        } catch (final JSONException e) {
            throw new RuntimeException("Survey serialization resulted in a corrupted parcel");
        } catch (final Survey.BadSurveyException e) {
            throw new RuntimeException("Survey serialization resulted in a corrupted parcel");
        }
    }

    private void initializeAndLaunch(Activity parentActivity) {
        final Intent surveyIntent = new Intent(parentActivity.getApplicationContext(), SurveyActivity.class);
        surveyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        surveyIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        synchronized (sSurveyStateLock) {
            sNextIntentId++;
            surveyIntent.putExtra("intentID", sNextIntentId);
        }
        parentActivity.startActivity(surveyIntent);
    }


    /**
     * This class is intended for internal use by the Mixpanel library.
     * Users of the library should not interact directly with this class.
     */
    public static class AnswerMap implements Parcelable {

        @SuppressLint("UseSparseArrays")
        public AnswerMap() {
            mMap = new HashMap<Integer, String>();
        }

        public void put(Integer i, String s) {
            mMap.put(i, s);
        }

        public String get(Integer i) {
            return mMap.get(i);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            final Bundle out = new Bundle();
            for (final Map.Entry<Integer, String> entry:mMap.entrySet()) {
                final String keyString = Integer.toString(entry.getKey());
                out.putString(keyString, entry.getValue());
            }
            dest.writeBundle(out);
        }

        public static final Parcelable.Creator<AnswerMap> CREATOR =
            new Parcelable.Creator<AnswerMap>() {
            @Override
            public AnswerMap createFromParcel(Parcel in) {
                final Bundle read = new Bundle();
                final AnswerMap ret = new AnswerMap();
                read.readFromParcel(in);
                for (final String kString:read.keySet()) {
                    final Integer kInt = Integer.valueOf(kString);
                    ret.put(kInt, read.getString(kString));
                }
                return ret;
            }

            @Override
            public AnswerMap[] newArray(int size) {
                return new AnswerMap[size];
            }
        };

        private final HashMap<Integer, String> mMap;
    }

    public static final Parcelable.Creator<SurveyState> CREATOR = new Parcelable.Creator<SurveyState>() {
        @Override
        public SurveyState createFromParcel(Parcel in) {
            final Bundle read = new Bundle(SurveyState.class.getClassLoader());
            read.readFromParcel(in);
            return new SurveyState(read);
        }

        @Override
        public SurveyState[] newArray(int size) {
            return new SurveyState[size];
        }
    };

    private final Survey mSurvey;
    private final String mDistinctId;
    private final String mToken;
    private final AnswerMap mAnswers;
    private final Bitmap mBackground;
    private final int mHighlightColor;

    private static final Object sSurveyStateLock = new Object();
    private static long sSurveyStateLockMillis = -1;
    private static SurveyState sSurveyState = null;
    private static int sNextIntentId = 0;
    private static long sIntentIdLockMillis = -1;
    private static int sShowingIntentId = -1;

    private static final String LOGTAG = "MixpanelAPI SurveyState";
    private static final long MAX_LOCK_TIME_MILLIS = 12 * 60 * 60 * 1000; // Twelve hour timeout on survey activities

    private static final String SURVEY_BUNDLE_KEY = "com.mixpanel.android.mpmetrics.SurveyState.SURVEY_BUNDLE_KEY";
    private static final String DISTINCT_ID_BUNDLE_KEY = "com.mixpanel.android.mpmetrics.SurveyState.DISTINCT_ID_BUNDLE_KEY";
    private static final String TOKEN_BUNDLE_KEY = "com.mixpanel.android.mpmetrics.SurveyState.TOKEN_BUNDLE_KEY";
    private static final String HIGHLIGHT_COLOR_BUNDLE_KEY = "com.mixpanel.android.mpmetrics.SurveyState.HIGHLIGHT_COLOR_BUNDLE_KEY";
    private static final String ANSWERS_BUNDLE_KEY = "com.mixpanel.android.mpmetrics.SurveyState.ANSWERS_BUNDLE_KEY";
    private static final String BACKGROUND_COMPRESSED_BUNDLE_KEY = "com.mixpanel.android.mpmetrics.SurveyState.BACKGROUND_COMPRESSED_BUNDLE_KEY";
}
