package com.mixpanel.android.util;

import android.content.Context;
import android.graphics.Color;
import android.util.DisplayMetrics;

public class ViewUtils {

    public static float dpToPx(float dp, Context context){
        float px = dp * ((float)context.getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
        return px;
    }

    public static int mixColors(int color1, int color2) {
        float r = (Color.red(color1)/2) + (Color.red(color2)/2);
        float g = (Color.green(color1)/2) + (Color.green(color2)/2);
        float b = (Color.blue(color1)/2) + (Color.blue(color2)/2);
        return Color.rgb((int) r, (int) g, (int) b);
    }
}
