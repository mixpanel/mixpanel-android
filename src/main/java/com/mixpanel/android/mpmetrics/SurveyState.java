package com.mixpanel.android.mpmetrics;

import java.io.ByteArrayOutputStream;

import com.mixpanel.android.surveys.SurveyActivity;
import com.mixpanel.android.util.StackBlurManager;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;

public class SurveyState {
    public static void proposeSurvey(final Survey s, final Activity parentActivity, final String distinctId, final String token) {
        synchronized(sSurveyStateLock) {
            if (null == sSurveyState) {
                sSurveyState = new SurveyState(s, parentActivity, distinctId, token);
                sSurveyState.initializeAndLaunch();
            }
        }
    }

    public static void releaseSurvey() {
        synchronized(sSurveyStateLock) {
            sSurveyState = null;
        }
    }

    private SurveyState(final Survey s, final Activity parentActivity, final String distinctId, final String token) {
        mSurvey = s;
        mParentActivity = parentActivity;
        mDistinctId = distinctId;
        mToken = token;
    }

    private void initializeAndLaunch() {
        mParentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final SurveyInitializationTask task = new SurveyInitializationTask();
                task.execute();
            }
        });
    }

    private class SurveyInitializationTask extends AsyncTask<Void, Void, ProcessedBitmap> {
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
        protected ProcessedBitmap doInBackground(Void ...params) {
            if (null == mSourceImage) {
                return new ProcessedBitmap(null, Color.WHITE);
            }
            final long startTime = System.currentTimeMillis();
            final Bitmap background1px = Bitmap.createScaledBitmap(mSourceImage, 1, 1, true);
            final int highlightColor = background1px.getPixel(0, 0);

            StackBlurManager.process(mSourceImage, 20);
            final long endTime = System.currentTimeMillis();
            if (MPConfig.DEBUG) Log.d(LOGTAG, "Blur took " + (endTime - startTime) + " millis");

            final Canvas canvas = new Canvas(mSourceImage);
            canvas.drawColor(GRAY_72PERCENT_OPAQUE, PorterDuff.Mode.SRC_ATOP);

            final ByteArrayOutputStream bs = new ByteArrayOutputStream();
            mSourceImage.compress(Bitmap.CompressFormat.PNG, 20, bs);
            final byte[] backgroundCompressed = bs.toByteArray();
            if (MPConfig.DEBUG) Log.d(LOGTAG, "Background (compressed) to bytes: " + backgroundCompressed.length);

            return new ProcessedBitmap(backgroundCompressed, highlightColor);
        }

        @Override
        protected void onPostExecute(ProcessedBitmap processed) {
            final Intent surveyIntent = new Intent(mParentActivity.getApplicationContext(), SurveyActivity.class);
            surveyIntent.putExtra("distinctId", mDistinctId);
            surveyIntent.putExtra("token", mToken);
            surveyIntent.putExtra("surveyJson", mSurvey.toJSON());
            surveyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            surveyIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

            final byte[] backgroundCompressed = processed.getBackgroundCompressed();
            if (null != backgroundCompressed) {
                surveyIntent.putExtra("backgroundCompressed", backgroundCompressed);
            }
            surveyIntent.putExtra("highlightColor", processed.getHighlightColor());
            mParentActivity.startActivity(surveyIntent);
        }

        private Bitmap mSourceImage;
    }

    private static class ProcessedBitmap {
        public ProcessedBitmap(final byte[] backgroundCompressed, final int highlightColor) {
            mHighlightColor = highlightColor;
            mBackgroundCompressed = backgroundCompressed;
        }

        public byte[] getBackgroundCompressed() {
            return mBackgroundCompressed;
        }

        public int getHighlightColor() {
            return mHighlightColor;
        }

        private final byte[] mBackgroundCompressed;
        private final int mHighlightColor;
    }

    private final Survey mSurvey;
    private final Activity mParentActivity;
    private final String mDistinctId;
    private final String mToken;

    private static final Object sSurveyStateLock = new Object();
    private static SurveyState sSurveyState = null;

    private static final int GRAY_72PERCENT_OPAQUE = Color.argb(186, 28, 28, 28);
    private static final String LOGTAG = "MixpanelAPI SurveyState";
}
