package com.mixpanel.android.surveys;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.CheckedTextView;

// TODO this feels a bit hacky, it might be better to contain or even reimpliment
// rather than subclass here.
public class SurveyChoiceView extends CheckedTextView {

    public SurveyChoiceView(Context context) {
        super(context);
        initSurveyChoiceView();
    }

    public SurveyChoiceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initSurveyChoiceView();
    }

    public SurveyChoiceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initSurveyChoiceView();
    }

    @Override
    public void setCheckMarkDrawable(Drawable d) {
        super.setCheckMarkDrawable(d);
        mSurveyChoiceCheckMark = d;
    }

    @Override
    public void setChecked(boolean checked) {
        final boolean wasChecked = isChecked();
        super.setChecked(checked);
        if (isChecked() && ! wasChecked) {
            final Animation transition = new SetCheckAnimation();
            transition.setDuration(ANIMATION_DURATION);
            startAnimation(transition);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final Drawable checkMarkDrawable = mSurveyChoiceCheckMark;
        final int checkmarkWidth = checkMarkDrawable == null ? 0 : checkMarkDrawable.getIntrinsicWidth();

        // TODO the padding stuff is a HACK. You may need to fix it before merge.
        final int originalPadding = getPaddingLeft();
        final int paddingTop = getPaddingTop();
        final int paddingRight = getPaddingRight();
        final int paddingBottom = getPaddingBottom();

        // Hide the checkmark during our parent drawing
        setCheckMarkDrawable(null);
        final int textPaddingLeft = (int) (originalPadding + (mTextLeftOffset * checkmarkWidth));
        setPadding(textPaddingLeft, paddingTop, paddingRight, paddingBottom);
        super.onDraw(canvas);
        final int checkPaddingLeft = (int) (originalPadding + (mCheckmarkLeftOffset * checkmarkWidth));
        setPadding(checkPaddingLeft, paddingTop, paddingRight, paddingBottom);
        setCheckMarkDrawable(checkMarkDrawable);

        // Largely cribbed from android.widget.CheckedTextView
        if (null != checkMarkDrawable) {
            final int verticalGravity = getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
            final int height = checkMarkDrawable.getIntrinsicHeight();

            int y = 0;

            switch (verticalGravity) {
                case Gravity.BOTTOM:
                    y = getHeight() - height;
                    break;
                case Gravity.CENTER_VERTICAL:
                    y = (getHeight() - height) / 2;
                    break;
            }

            final int top = y;
            final int bottom = top + height;
            final int left = checkPaddingLeft;
            final int right = left + checkmarkWidth;
            checkMarkDrawable.setBounds(getScrollX() + left, top, getScrollX() + right, bottom);
            checkMarkDrawable.draw(canvas);
        }

        setPadding(originalPadding, paddingTop, paddingRight, paddingBottom); // TODO if this requests a redraw, we're in a world of hurt.
    }

    private void initSurveyChoiceView() {
        mCheckmarkLeftOffset = 0;
        mTextLeftOffset = 1.5f;
    }

    private class SetCheckAnimation extends Animation {
        @Override
        public boolean willChangeTransformationMatrix() {
            return false;
        }

        @Override
        public boolean willChangeBounds() {
            return false;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            float checkmarkOffset = 0;
            float textOffset = 1.0f;
            if (interpolatedTime <= 0.5f) {
                checkmarkOffset = interpolatedTime - 0.5f; // First half of the animation, checkmark moves right
            } else {
                textOffset = textOffset + (interpolatedTime - 0.5f) * 2; // second half of the animation, text moves right
            }
            mCheckmarkLeftOffset = checkmarkOffset;
            mTextLeftOffset = textOffset;
            requestLayout();
        }
    }// class SetCheckAnimation

    private Drawable mSurveyChoiceCheckMark; // getCheckMarkDrawable() is only available in newer APIs
    private float mCheckmarkLeftOffset; // offset of checkmark drawable from left edge, expressed in checkmark widths
    private float mTextLeftOffset; // offset of text from left edge, expressed in checkmark widths
    private static int ANIMATION_DURATION = 2000; // Waaaay too slow, for debugging
}
