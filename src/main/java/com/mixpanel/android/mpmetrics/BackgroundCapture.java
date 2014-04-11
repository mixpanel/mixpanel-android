package com.mixpanel.android.mpmetrics;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.AsyncTask;

import com.mixpanel.android.util.ActivityImageUtils;
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
        public void onBackgroundCaptured(Bitmap bitmapCaptured, int highlightColorCaptured);
    }

    private static class BackgroundCaptureTask extends AsyncTask<Void, Void, Void> {
        public BackgroundCaptureTask(Activity parentActivity, OnBackgroundCapturedListener listener) {
            mParentActivity = parentActivity;
            mListener = listener;
            mCalculatedHighlightColor = Color.BLACK;
        }

        @Override
        protected void onPreExecute() {
            mSourceImage = ActivityImageUtils.getScaledScreenshot(mParentActivity, 2, 2, true);
            mCalculatedHighlightColor = ActivityImageUtils.getHighlightColorFromBitmap(mSourceImage);
        }

        @Override
        protected Void doInBackground(Void ...params) {
            if (null != mSourceImage) {
                try {
                    StackBlurManager.process(mSourceImage, 20);
                    final Canvas canvas = new Canvas(mSourceImage);
                    canvas.drawColor(GRAY_72PERCENT_OPAQUE, PorterDuff.Mode.SRC_ATOP);
                } catch (final ArrayIndexOutOfBoundsException e) {
                    // Workaround for a bug in the algorithm while we wait
                    // for folks to move to gradle/AndroidStudio/other Renderscript-friendly build tools
                    mSourceImage = null;
                } catch (final OutOfMemoryError e) {
                    // It's possible that the bitmap processing was what sucked up all of the memory,
                    // So we try to recover here.
                    mSourceImage = null;
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void _) {
            mListener.onBackgroundCaptured(mSourceImage, mCalculatedHighlightColor);
        }

        private final OnBackgroundCapturedListener mListener;
        private final Activity mParentActivity;
        private Bitmap mSourceImage;
        private int mCalculatedHighlightColor;
    }


    private static final int GRAY_72PERCENT_OPAQUE = Color.argb(186, 28, 28, 28);

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI BackgroundCapture";
}
