package com.mixpanel.android.mpmetrics;

import org.json.JSONObject;

public class Survey {

    /* package */ Survey(JSONObject description) {
        mDescription = description;
    }

    private final JSONObject mDescription;
}
