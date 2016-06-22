package com.mixpanel.android.surveys;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.Layout;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;


public class AutoResizeTextView extends TextView {
    private static final int TEXT_SIZE_SP = 18;
    private static final int MAX_LINES = 4;
    private static final float MIN_TEXT_SIZE_FACTOR = 1.3f;
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
        setMaxLines(MAX_LINES);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP);
        mMinTextSizePx = getTextSize() / MIN_TEXT_SIZE_FACTOR;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final Layout layout = getLayout();
        if (layout != null) {
            final int lineCount = layout.getLineCount();
            if (lineCount == MAX_LINES && layout.getEllipsisCount(lineCount - 1) > 0 && getTextSize() > mMinTextSizePx) {
                    final float textSize = getTextSize();
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, (textSize - 2));
                    measure(widthMeasureSpec, heightMeasureSpec);
            }
        }
    }
}
