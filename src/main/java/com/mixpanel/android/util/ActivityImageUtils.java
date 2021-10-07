package com.mixpanel.android.util;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import androidx.annotation.Nullable;
import android.view.View;

public class ActivityImageUtils {

    /**
     * @return the desired Bitmap or null in case rootView hasn't been measured appropriately or it's grabbed before layout.
     */
    public static @Nullable Bitmap getScaledScreenshot(final Activity activity, int scaleWidth, int scaleHeight, boolean relativeScaleIfTrue) {
        final View someView = activity.findViewById(android.R.id.content);
        final View rootView = someView.getRootView();
        if (rootView.getWidth() <= 0 || rootView.getHeight() <= 0) {
            return null;
        }
        final Bitmap original = Bitmap.createBitmap(rootView.getWidth(), rootView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(original);

        Drawable backgroundDrawable = rootView.getBackground();
        if (backgroundDrawable != null) {
            backgroundDrawable.draw(canvas);
        } else {
            canvas.drawColor(Color.WHITE);
        }
        rootView.draw(canvas);

        Bitmap scaled = null;
        if (null != original && original.getWidth() > 0 && original.getHeight() > 0) {
            if (relativeScaleIfTrue) {
                scaleWidth = original.getWidth() / scaleWidth;
                scaleHeight = original.getHeight() / scaleHeight;
            }
            if (scaleWidth > 0 && scaleHeight > 0) {
                try {
                    scaled = Bitmap.createScaledBitmap(original, scaleWidth, scaleHeight, false);
                } catch (OutOfMemoryError error) {
                    MPLog.i(LOGTAG, "Not enough memory to produce scaled image, returning a null screenshot");
                }
            }
        }
        return scaled;
    }

    public static int getHighlightColorFromBackground(final Activity activity) {
        int incolor = Color.BLACK;
        final Bitmap screenshot1px = getScaledScreenshot(activity, 1, 1, false);
        if (null != screenshot1px) {
            incolor = screenshot1px.getPixel(0, 0);
        }
        return getHighlightColor(incolor);
    }

    public static int getHighlightColorFromBitmap(final Bitmap bitmap) {
        int incolor = Color.BLACK;
        if (null != bitmap) {
            final Bitmap bitmap1px = Bitmap.createScaledBitmap(bitmap, 1, 1, false);
            incolor = bitmap1px.getPixel(0, 0);
        }
        return getHighlightColor(incolor);
    }

    public static int getHighlightColor(int sampleColor) {
        // Set a constant value level in HSV, in case the averaged color is too light or too dark.
        float[] hsvBackground = new float[3];
        Color.colorToHSV(sampleColor, hsvBackground);
        hsvBackground[2] = 0.3f; // value parameter

        return Color.HSVToColor(0xf2, hsvBackground);
    }

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.ActImgUtils";
}
