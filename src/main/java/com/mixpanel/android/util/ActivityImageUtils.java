package com.mixpanel.android.util;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;

public class ActivityImageUtils {
    public static Bitmap getScaledScreenshot(final Activity activity) {
        final View someView = activity.findViewById(android.R.id.content);
        final View rootView = someView.getRootView();
        final boolean originalCacheState = rootView.isDrawingCacheEnabled();
        rootView.setDrawingCacheEnabled(true);
        rootView.buildDrawingCache(true);

        // We could get a null or zero px bitmap if the rootView hasn't been measured
        // appropriately, or we grab it before layout.
        // This is ok, and we should handle it gracefully.
        final Bitmap original = rootView.getDrawingCache();
        Bitmap scaled = null;
        if (null != original && original.getWidth() > 0 && original.getHeight() > 0) {
            final int scaledWidth = original.getWidth() / 2;
            final int scaledHeight = original.getHeight() / 2;
            if (scaledWidth > 0 && scaledHeight > 0) {
                scaled = Bitmap.createScaledBitmap(original, scaledWidth, scaledHeight, false);
            }
        }
        if (!originalCacheState) {
            rootView.setDrawingCacheEnabled(false);
        }
        return scaled;
    }

    public static int getHighlightColor(final Bitmap screenshot) {
        int averageColor = Color.BLACK;

        try {
            final Bitmap screenshot1px = Bitmap.createScaledBitmap(screenshot, 1, 1, true);
            averageColor = screenshot1px.getPixel(0, 0);
        // It's possible that the bitmap processing sucked up all of the memory,
        } catch (final OutOfMemoryError e) { }

        // Set a constant value level in HSV, in case the averaged color is too light or too dark.
        float[] hsvBackground = new float[3];
        Color.colorToHSV(averageColor, hsvBackground);
        hsvBackground[2] = 0.3f; // value parameter

        return Color.HSVToColor(0xcc, hsvBackground);
    }

    public interface OnBackgroundCapturedListener {
        public void OnBackgroundCaptured(Bitmap bitmapCaptured);
    }
}