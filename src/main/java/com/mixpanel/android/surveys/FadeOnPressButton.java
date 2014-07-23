package com.mixpanel.android.surveys;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.Button;

import android.R.attr;

@TargetApi(14)
/**
 * Part of the Mixpanel Surveys user interface.
 *
 * Users of the library should not instantiate this class directly.
 */
public class FadeOnPressButton extends Button {

    public FadeOnPressButton(Context context) {
        super(context);
    }

    public FadeOnPressButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FadeOnPressButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void drawableStateChanged() {
        final int[] state = getDrawableState();
        boolean isPressed = false;
        for (final int i:state) {
            if (i == attr.state_pressed) {
                if (!mIsFaded) setAlphaBySDK(0.5f);
                isPressed = true;
                break;
            }
        }
        if (mIsFaded && !isPressed) {
            setAlphaBySDK(1.0f);
            mIsFaded = true;
        }
        super.drawableStateChanged();
    }

    private void setAlphaBySDK(float alpha) {
        setAlpha(alpha);
    }

    private boolean mIsFaded;
}
