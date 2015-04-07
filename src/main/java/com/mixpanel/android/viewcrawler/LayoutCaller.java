package com.mixpanel.android.viewcrawler;

import android.view.View;
import android.widget.RelativeLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class LayoutCaller {
    public LayoutCaller(int[] args) throws JSONException {
        mArgs = args;
    }

    public int[] getArgs() {
        return mArgs;
    }

    public void applyMethod(View target) {
        applyMethodWithArguments(target, mArgs);
    }

    public void applyMethodWithArguments(View target, int[] args) {
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)target.getLayoutParams();
        params.addRule(args[RULE_INDEX], args[ANCHOR_ID]);
        target.setLayoutParams(params);
    }

    private final int[] mArgs;
    public static final int RULE_INDEX = 0;
    public static final int ANCHOR_ID = 1;
}
