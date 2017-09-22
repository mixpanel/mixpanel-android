package com.mixpanel.android.takeoverinapp;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.ImageView;

/**
 * Part of the Mixpanel Notifications user interface.
 *
 * Users of the library should not instantiate this class directly.
 */
public class MiniCircleImageView extends ImageView {
    public MiniCircleImageView(Context context) {
        super(context);
        init();
    }

    public MiniCircleImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MiniCircleImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    @SuppressWarnings("deprecation")
    private void init() {
        mWhitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mWhitePaint.setColor(getResources().getColor(android.R.color.white, null));
        } else {
            mWhitePaint.setColor(getResources().getColor(android.R.color.white));
        }
        mWhitePaint.setStyle(Paint.Style.STROKE);

        Resources r = getResources();
        float strokePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, r.getDisplayMetrics());
        mWhitePaint.setStrokeWidth(strokePx);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        mCanvasWidth = w;
        mCanvasHeight = h;
    }

    private Paint mWhitePaint;
    private int mCanvasWidth;
    private int mCanvasHeight;
}
