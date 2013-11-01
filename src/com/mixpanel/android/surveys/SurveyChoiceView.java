package com.mixpanel.android.surveys;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.CheckedTextView;

// TODO this feels a bit hacky, it might be better to contain or even reimpliment
// rather than subclass here.
public class SurveyChoiceView extends CheckedTextView {

    public SurveyChoiceView(Context context) {
        super(context);
    }

    public SurveyChoiceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SurveyChoiceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setCheckMarkDrawable(Drawable d) {
        super.setCheckMarkDrawable(d);
        mSurveyChoiceCheckMark = d;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Hide the checkmark during our parent drawing
        final Drawable checkMarkDrawable = mSurveyChoiceCheckMark;
        final int originalPadding = getPaddingLeft();

        int checkmarkWidth = checkMarkDrawable == null ? 0 : checkMarkDrawable.getIntrinsicWidth();
        setCheckMarkDrawable(null);
        int additionalPadding = originalPadding + checkmarkWidth + originalPadding;
        setPadding(additionalPadding, getPaddingTop(), getPaddingRight(), getPaddingBottom()); // TODO Does this request redraw?
        super.onDraw(canvas);
        setPadding(originalPadding, getPaddingTop(), getPaddingRight(), getPaddingBottom()); // TODO Does this request redrawing?
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
            final int left = originalPadding;
            final int right = originalPadding + checkmarkWidth;
            checkMarkDrawable.setBounds(getScrollX() + left, top, getScrollX() + right, bottom);
            checkMarkDrawable.draw(canvas);
        }
    }

    private Drawable mSurveyChoiceCheckMark; // getCheckMarkDrawable() is only available in newer APIs
}
