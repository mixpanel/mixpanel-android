package com.mixpanel.android.mpmetrics;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;

import com.mixpanel.android.util.StackBlurManager;

/* package */ class BackgroundCapture {

    public static void captureBackground(final Activity parentActivity, final OnBackgroundCapturedListener listener) {
        parentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final BackgroundCaptureTask task = new BackgroundCaptureTask(parentActivity, listener);
                task.execute();
            }
        });
    }

    public interface OnBackgroundCapturedListener {
        public void OnBackgroundCaptured(Bitmap bitmapCaptured, int highlightColorCaptured);
    }

    private static class BackgroundCaptureTask extends AsyncTask<Void, Void, Void> {
        public BackgroundCaptureTask(Activity parentActivity, OnBackgroundCapturedListener listener) {
            mParentActivity = parentActivity;
            mListener = listener;
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

            try {
                final long startTime = System.currentTimeMillis();
                final Bitmap background1px = Bitmap.createScaledBitmap(mSourceImage, 1, 1, true);
                mCalculatedHighlightColor = background1px.getPixel(0, 0);

                StackBlurManager.process(mSourceImage, 20);

                final long endTime = System.currentTimeMillis();
                if (MPConfig.DEBUG) Log.d(LOGTAG, "Blur took " + (endTime - startTime) + " millis");

                final Canvas canvas = new Canvas(mSourceImage);
                canvas.drawColor(GRAY_72PERCENT_OPAQUE, PorterDuff.Mode.SRC_ATOP);
            } catch (final OutOfMemoryError e) {
                // It's possible that the bitmap processing was what sucked up all of the memory,
                // So we try to recover here.
                mCalculatedHighlightColor = Color.WHITE;
                mSourceImage = null;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void _) {
            mListener.OnBackgroundCaptured(mSourceImage, mCalculatedHighlightColor);
        }

        private final OnBackgroundCapturedListener mListener;
        private final Activity mParentActivity;
        private Bitmap mSourceImage;
        private int  mCalculatedHighlightColor;
    } // SurveyInitializationTask

    private static final String LOGTAG = "MixpanelAPI BackgroundCapture";
    private static final int GRAY_72PERCENT_OPAQUE = Color.argb(186, 28, 28, 28);
}
