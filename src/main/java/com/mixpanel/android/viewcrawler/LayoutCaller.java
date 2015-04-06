package com.mixpanel.android.viewcrawler;

import android.view.View;
import android.widget.RelativeLayout;

import org.json.JSONArray;

public class LayoutCaller {
    public LayoutCaller(JSONArray args) {
        if (args.length() == 1) {
            mArgs = new int[]{args.optInt(RULE_INDEX), RelativeLayout.TRUE};
        } else {
            mArgs = new int[]{args.optInt(RULE_INDEX), args.optInt(ANCHOR_ID)};
        }
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
