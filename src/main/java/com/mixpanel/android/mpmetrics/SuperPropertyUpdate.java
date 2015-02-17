package com.mixpanel.android.mpmetrics;

import org.json.JSONObject;

public interface SuperPropertyUpdate {
    public JSONObject update(JSONObject oldValues);
}
