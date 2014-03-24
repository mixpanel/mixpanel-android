package com.mixpanel.android.surveys;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.ImageView;


public class FadingImageView extends ImageView {
    public FadingImageView(final Context context) {
        super(context);
        initFadingImageView();
    }

    public FadingImageView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        initFadingImageView();
    }

    public FadingImageView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        initFadingImageView();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final Rect clip = canvas.getClipBounds();
        final int restoreTo = canvas.saveLayer(0, 0, clip.width(), clip.height(), null, Canvas.ALL_SAVE_FLAG);
        super.onDraw(canvas);

        final int height = getHeight();
        final int width = getWidth();
        mGradientMatrix.setScale(1, height);
        mGradientShader.setLocalMatrix(mGradientMatrix);
        canvas.drawRect(0, 0, width, height, mGradientPaint);
        canvas.restoreToCount(restoreTo);
    }

    private void initFadingImageView() {
        // Approach modeled after View.ScrollabilityCache from the framework
        mGradientPaint = new Paint();
        mGradientMatrix = new Matrix();
        mGradientShader = new LinearGradient(
            0, 0, 0, 1, // x0, y0, x1, y1
            new int[]  {0xFF000000, 0xFF000000, 0xE5000000, 0x00000000},
            new float[]{0.0f,       0.7f,       0.8f,       1.0f},
            Shader.TileMode.CLAMP
        );

        mGradientPaint.setShader(mGradientShader);
        mGradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

    }

    private Paint mGradientPaint;
    private Matrix mGradientMatrix;
    private Shader mGradientShader;
}
