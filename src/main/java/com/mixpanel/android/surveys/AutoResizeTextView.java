package com.mixpanel.android.surveys;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.Layout;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;


public class AutoResizeTextView extends TextView {
    private int mTextSizeSp = 18;
    private int mMaxLines = 4;
    private float mMinTextSizePx;

    public AutoResizeTextView(Context context) {
        super(context);
        init();
    }

    public AutoResizeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AutoResizeTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public AutoResizeTextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setMaxLines(mMaxLines);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp);
        mMinTextSizePx = getTextSize() / 1.3f;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final Layout layout = getLayout();
        if (layout != null) {
            final int lineCount = layout.getLineCount();
            if (lineCount == mMaxLines) {
                if (layout.getEllipsisCount(lineCount - 1) > 0 && getTextSize() > mMinTextSizePx) {
                    final float textSize = getTextSize();
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, (textSize - 2));
                    measure(widthMeasureSpec, heightMeasureSpec);
                }
            }
        }
    }
}
