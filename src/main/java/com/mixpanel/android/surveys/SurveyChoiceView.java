package com.mixpanel.android.surveys;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.CheckedTextView;

// XXX: CheckedTextView is a bit hostile to this kind of extension.
// Consider reimplementing as a child of View, that contains a Drawable and a TextView
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
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        final float density = metrics.density;

        int checkmarkWidth = 0;
        if (null != checkMarkDrawable && isChecked()) {
            checkmarkWidth = (int) (CHECKMARK_HEIGHT_DP * density);
        }
        final int checkmarkHeight = checkmarkWidth;
        final int boxPaddingTop = (int) (BOX_PADDING_TOP_DP * density);
        final int boxPaddingLeft = (int) (BOX_PADDING_LEFT_DP * density);

        // Hide the checkmark during our parent drawing
        setCheckMarkDrawable(null);
        final int textPaddingLeft = (int) (boxPaddingLeft + (mTextLeftOffset * checkmarkWidth));
        setPadding(textPaddingLeft, boxPaddingTop, boxPaddingLeft, boxPaddingTop);
        super.onDraw(canvas);
        final int checkPaddingLeft = (int) (boxPaddingLeft + (mCheckmarkLeftOffset * checkmarkWidth));
        setPadding(checkPaddingLeft, boxPaddingTop, boxPaddingLeft, boxPaddingTop);
        setCheckMarkDrawable(checkMarkDrawable);

        if (null != checkMarkDrawable) {
            final int verticalGravity = getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
            int y = 0;

            switch (verticalGravity) {
                case Gravity.BOTTOM:
                    y = getHeight() - checkmarkHeight;
                    break;
                case Gravity.CENTER_VERTICAL:
                    y = (getHeight() - checkmarkHeight) / 2;
                    break;
            }

            final int top = y;
            final int bottom = top + checkmarkHeight;
            final int left = checkPaddingLeft;
            final int right = left + checkmarkWidth;
            checkMarkDrawable.setBounds(getScrollX() + left, top, getScrollX() + right, bottom);
            checkMarkDrawable.draw(canvas);
        }

        setPadding(boxPaddingLeft, boxPaddingTop, boxPaddingLeft, boxPaddingTop);
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

    // Nice to have- these as LayoutParameters/Styled attributes
    private static final int ANIMATION_DURATION = 300;
    private static final int CHECKMARK_HEIGHT_DP = 14; // Current code assumes a SQUARE CHECKMARK.
    private static final int BOX_PADDING_LEFT_DP = 22;
    private static final int BOX_PADDING_TOP_DP = 12;
}
