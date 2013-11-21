package com.mixpanel.android.surveys;

import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;

public class AlwaysSubmittableEditText extends EditText {

    public AlwaysSubmittableEditText(Context context) {
        super(context);
    }

    public AlwaysSubmittableEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AlwaysSubmittableEditText(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        final InputConnection ret = super.onCreateInputConnection(outAttrs);
        // The stock EditText forces MultiLine inputs to have the enter key,
        // but we want the "Done" key.
        outAttrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        return ret;
    }

}
