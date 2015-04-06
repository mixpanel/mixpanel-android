package com.mixpanel.android.viewcrawler;

import android.view.View;
import android.widget.RelativeLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class LayoutCaller {
    public LayoutCaller(JSONArray args) throws JSONException {
        JSONObject layout_info = args.optJSONObject(0);
        int rule_id = layout_info.getInt("rule_id");
        if (layout_info.getString("operation").equals("remove")) {
            mArgs = new int[]{rule_id, 0};
        } else if (layout_info.has("anchor_id")) {
            mArgs = new int[]{rule_id, layout_info.getInt("anchor_id")};
        } else {
            mArgs = new int[]{rule_id, RelativeLayout.TRUE};
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
