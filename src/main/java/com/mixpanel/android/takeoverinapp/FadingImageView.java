package com.mixpanel.android.takeoverinapp;

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
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (mShouldShowShadow) {
            mHeight = getHeight();
            mWidth = getWidth();

            int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
            mGradientMatrix.setScale(1, parentHeight);
            mAlphaGradientShader.setLocalMatrix(mGradientMatrix);
            mDarkenGradientShader.setLocalMatrix(mGradientMatrix);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (mShouldShowShadow) {
            // We have to override this low level draw method instead of onDraw, because by the time
            // onDraw is called, the Canvas with the background has already been saved, so we can't
            // actually clear it with our opacity gradient.
            final Rect clip = canvas.getClipBounds();
            final int restoreTo = canvas.saveLayer(0, 0, clip.width(), clip.height(), null, Canvas.ALL_SAVE_FLAG);

            super.draw(canvas);

            canvas.drawRect(0, 0, mWidth, mHeight, mAlphaGradientPaint);
            canvas.restoreToCount(restoreTo);
        } else {
            super.draw(canvas);
        }
    }

    private void initFadingImageView() {
        // Approach modeled after View.ScrollabilityCache from the framework
        mGradientMatrix = new Matrix();

        mAlphaGradientPaint = new Paint();
        mAlphaGradientShader = new LinearGradient(
            0, 0, 0, 1, // x0, y0, x1, y1
            new int[]  {0xFF000000, 0xFF000000, 0xE5000000, 0x00000000},
            new float[]{0.0f,       0.2f,       0.4f,       1.0f},
            Shader.TileMode.CLAMP
        );
        mAlphaGradientPaint.setShader(mAlphaGradientShader);
        mAlphaGradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        mDarkenGradientPaint = new Paint();
        mDarkenGradientShader = new LinearGradient(
            0, 0, 0, 1, // x0, y0, x1, y1
            new int[]  {0x00000000, 0x00000000, 0xFF000000, 0xFF000000},
            new float[]{0.0f,       0.85f,      0.98f,      1.0f      },
            Shader.TileMode.CLAMP
        );
        mDarkenGradientPaint.setShader(mDarkenGradientShader);
        mAlphaGradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    public void showShadow(boolean shouldShowShadow) {
        mShouldShowShadow = shouldShowShadow;
    }

    private Matrix mGradientMatrix;
    private Paint mAlphaGradientPaint;
    private Shader mAlphaGradientShader;
    private Paint mDarkenGradientPaint;
    private Shader mDarkenGradientShader;
    private int mHeight;
    private int mWidth;
    private boolean mShouldShowShadow;
}
