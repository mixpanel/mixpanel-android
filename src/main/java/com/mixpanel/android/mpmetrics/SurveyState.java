package com.mixpanel.android.mpmetrics;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;

import com.mixpanel.android.surveys.SurveyActivity;
import com.mixpanel.android.util.StackBlurManager;

public class SurveyState implements Parcelable {
	public static void proposeSurvey(final Survey s, final Activity parentActivity, final String distinctId, final String token) {
        final long currentTime = System.currentTimeMillis();
        final long deltaTime = currentTime - sSurveyStateLockMillis;
        synchronized(sSurveyStateLock) {
            if (sShowingIntentId > 0 && deltaTime > MAX_LOCK_TIME_MILLIS) {
                Log.i(LOGTAG, "SurveyState set long, long ago, without showing.");
                sSurveyState = null;
            }
            if (null == sSurveyState) {
                sSurveyState = new SurveyState(s, parentActivity, distinctId, token);
                sSurveyState.initializeAndLaunch();
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
        assert(null == proposed || proposed.isReady());
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
            } else if (! sSurveyState.isReady()) {
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
        out.putBoolean(IS_READY_BUNDLE_KEY, mIsReady);
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
    /* package */ SurveyState(final Survey s, final Activity parentActivity, final String distinctId, final String token) {
        mSurvey = s;
        mParentActivity = parentActivity;
        mDistinctId = distinctId;
        mToken = token;
        mIsReady = false;
        mAnswers = new AnswerMap();
    }

    private SurveyState(Bundle read) {
        mDistinctId = read.getString(DISTINCT_ID_BUNDLE_KEY);
        mToken = read.getString(TOKEN_BUNDLE_KEY);
        mHighlightColor = read.getInt(HIGHLIGHT_COLOR_BUNDLE_KEY);
        mIsReady = read.getBoolean(IS_READY_BUNDLE_KEY);
        mAnswers = (AnswerMap) read.getParcelable(ANSWERS_BUNDLE_KEY);
        mParentActivity = null;

        mBackground = null;
        final byte[] backgroundCompressed = read.getByteArray(BACKGROUND_COMPRESSED_BUNDLE_KEY);
        if (null != backgroundCompressed) {
            mBackground = BitmapFactory.decodeByteArray(backgroundCompressed, 0, backgroundCompressed.length);
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

    private boolean isReady() {
        return mIsReady;
    }

    private void initializeAndLaunch() {
        final SurveyState self = this;
        mParentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final SurveyInitializationTask task = new SurveyInitializationTask(self);
                task.execute();
            }
        });
    }

    private class SurveyInitializationTask extends AsyncTask<Void, Void, Void> {
        public SurveyInitializationTask(SurveyState parentState) {
            mParentState = parentState;
        }

        @Override
        protected void onPreExecute() {
            final View someView = mParentActivity.findViewById(android.R.id.content);
            final View rootView = someView.getRootView();
            final boolean originalCacheState = rootView.isDrawingCacheEnabled();
            rootView.setDrawingCacheEnabled(true);
            rootView.layout(0, 0, rootView.getMeasuredWidth(), rootView.getMeasuredHeight());
            rootView.buildDrawingCache(true);

            // We could get a null or zero px bitmap if the rootView hasn't been measured
            // appropriately. This is ok, and we should handle it gracefully.
            final Bitmap original = rootView.getDrawingCache();
            Bitmap scaled = null;
            if (null != original) {
                final int scaledWidth = original.getWidth() / 2;
                final int scaledHeight = original.getHeight() / 2;
                if (scaledWidth > 0 && scaledHeight > 0) {
                    scaled = Bitmap.createScaledBitmap(original, scaledWidth, scaledHeight, false);
                }
            }
            if (! originalCacheState) {
                rootView.setDrawingCacheEnabled(false);
            }
            mSourceImage = scaled;
        }

        @Override
        protected Void doInBackground(Void ...params) {
            if (null == mSourceImage) {
                mCalculatedHighlightColor = Color.WHITE;
                mSourceImage = null;
                return null;
            }

            // This is purely an optimization- all kinds of great
            // stuff could happen after we make this check, which is ok.
            synchronized (sSurveyStateLock) {
                if (sSurveyState != mParentState) {
                    isWasted = true;
                    return null;
                }
            }

            final long startTime = System.currentTimeMillis();
            final Bitmap background1px = Bitmap.createScaledBitmap(mSourceImage, 1, 1, true);
            mCalculatedHighlightColor = background1px.getPixel(0, 0);

            StackBlurManager.process(mSourceImage, 20);
            final long endTime = System.currentTimeMillis();
            if (MPConfig.DEBUG) Log.d(LOGTAG, "Blur took " + (endTime - startTime) + " millis");

            final Canvas canvas = new Canvas(mSourceImage);
            canvas.drawColor(GRAY_72PERCENT_OPAQUE, PorterDuff.Mode.SRC_ATOP);
            return null;
        }

        @Override
        protected void onPostExecute(Void _) {
            if (isWasted) {
                return; // No need to launch anything
            }
            mBackground = mSourceImage;
            mHighlightColor = mCalculatedHighlightColor;

            final Intent surveyIntent = new Intent(mParentActivity.getApplicationContext(), SurveyActivity.class);
            surveyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            surveyIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

            synchronized (sSurveyStateLock) {
                mIsReady = true;
                sNextIntentId++;
                surveyIntent.putExtra("intentID", sNextIntentId);
            }
            mParentActivity.startActivity(surveyIntent);
        }

        private final SurveyState mParentState;
        private boolean isWasted = false;
        private Bitmap mSourceImage;
        private int  mCalculatedHighlightColor;
    } // SurveyInitializationTask

    public static class AnswerMap extends HashMap<Integer, String> implements Parcelable {

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            final Bundle out = new Bundle();
            for (final Map.Entry<Integer, String> entry:entrySet()) {
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

        private static final long serialVersionUID = -2359922757820889025L;
    }

    public static final Parcelable.Creator<SurveyState> CREATOR = new Parcelable.Creator<SurveyState>() {
        @Override
        public SurveyState createFromParcel(Parcel in) {
            final Bundle read = new Bundle();
            read.readFromParcel(in);
            return new SurveyState(read);
        }

        @Override
        public SurveyState[] newArray(int size) {
            return new SurveyState[size];
        }
    };

    private final Survey mSurvey;
    private final Activity mParentActivity;
    private final String mDistinctId;
    private final String mToken;
    private final AnswerMap mAnswers;
    private Bitmap mBackground;
    private int mHighlightColor;
    private boolean mIsReady;

    private static final Object sSurveyStateLock = new Object();
    private static long sSurveyStateLockMillis = -1;
    private static SurveyState sSurveyState = null;
    private static int sNextIntentId = 0;
    private static long sIntentIdLockMillis = -1;
    private static int sShowingIntentId = -1;

    private static final int GRAY_72PERCENT_OPAQUE = Color.argb(186, 28, 28, 28);
    private static final String LOGTAG = "MixpanelAPI SurveyState";
    private static final long MAX_LOCK_TIME_MILLIS = 12 * 60 * 60 * 1000; // Twelve hour timeout on survey activities

    private static final String SURVEY_BUNDLE_KEY = "com.mixpanel.android.mpmetrics.SurveyState.SURVEY_BUNDLE_KEY";
    private static final String DISTINCT_ID_BUNDLE_KEY = "com.mixpanel.android.mpmetrics.SurveyState.DISTINCT_ID_BUNDLE_KEY";
    private static final String TOKEN_BUNDLE_KEY = "com.mixpanel.android.mpmetrics.SurveyState.TOKEN_BUNDLE_KEY";
    private static final String HIGHLIGHT_COLOR_BUNDLE_KEY = "com.mixpanel.android.mpmetrics.SurveyState.HIGHLIGHT_COLOR_BUNDLE_KEY";
    private static final String IS_READY_BUNDLE_KEY = "com.mixpanel.android.mpmetrics.SurveyState.IS_READY_BUNDLE_KEY";
    private static final String ANSWERS_BUNDLE_KEY = "com.mixpanel.android.mpmetrics.SurveyState.ANSWERS_BUNDLE_KEY";
    private static final String BACKGROUND_COMPRESSED_BUNDLE_KEY = "com.mixpanel.android.mpmetrics.SurveyState.BACKGROUND_COMPRESSED_BUNDLE_KEY";
}
