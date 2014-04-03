package com.mixpanel.android.surveys;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;

import com.mixpanel.android.R;


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

        mHeight = getHeight();
        mWidth = getWidth();
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            // For Portrait takeover notifications, we have to fade out into the notification text
            // at the bottom of the screen.

            final View root = (View) this.getRootView();
            final View bottomWrapperView = (View) root.findViewById(R.id.com_mixpanel_android_notification_bottom_wrapper);

            // bottomWrapperView should have been measured already, so it's height should exist
            // Still, guard against potential weird black magic rendering issues.
            int bottomWrapperHeight = 0;
            if (null != bottomWrapperView && bottomWrapperView.getHeight() != 0) {
                bottomWrapperHeight = bottomWrapperView.getHeight();
            }

            // We don't want the fade out to end right at the beginning of the text, so we give it
            // give it a few extra dp's of room.
            Resources r = getResources();
            float extraPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 15, r.getDisplayMetrics());
            mGradientMatrix.setScale(1, parentHeight - bottomWrapperHeight + extraPx);
        } else {
            mGradientMatrix.setScale(1, parentHeight);
        }
        mAlphaGradientShader.setLocalMatrix(mGradientMatrix);
        mDarkenGradientShader.setLocalMatrix(mGradientMatrix);
    }

    @Override
    public void draw(Canvas canvas) {
        // We have to override this low level draw method instead of onDraw, because by the time
        // onDraw is called, the Canvas with the background has already been saved, so we can't
        // actually clear it with our opacity gradient.
        final Rect clip = canvas.getClipBounds();
        final int restoreTo = canvas.saveLayer(0, 0, clip.width(), clip.height(), null, Canvas.ALL_SAVE_FLAG);

        super.draw(canvas);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            canvas.drawRect(0, 0, mWidth, mHeight, mAlphaGradientPaint);
        } else {
            canvas.drawRect(getPaddingLeft(), getPaddingTop(),
                            mWidth - getPaddingRight(),
                            mHeight - getPaddingBottom(),
                            mDarkenGradientPaint);
        }
        canvas.restoreToCount(restoreTo);
    }

    private void initFadingImageView() {
        // Approach modeled after View.ScrollabilityCache from the framework
        mGradientMatrix = new Matrix();

        mAlphaGradientPaint = new Paint();
        mAlphaGradientShader = new LinearGradient(
            0, 0, 0, 1, // x0, y0, x1, y1
            new int[]  {0xFF000000, 0xFF000000, 0xE5000000, 0x00000000},
            new float[]{0.0f,       0.7f,       0.8f,       1.0f},
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

    private Matrix mGradientMatrix;
    private Paint mAlphaGradientPaint;
    private Shader mAlphaGradientShader;
    private Paint mDarkenGradientPaint;
    private Shader mDarkenGradientShader;
    private int mHeight;
    private int mWidth;
}
