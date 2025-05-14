package com.mixpanel.android.mpmetrics;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

class FlagsConfig {
    public final boolean enabled;
    @NonNull public final JSONObject context;

    public FlagsConfig() {
        this.enabled = false;
        this.context = new JSONObject();
    }

    public FlagsConfig(boolean enabled) {
        this.enabled = enabled;
        this.context = new JSONObject();
    }

    public FlagsConfig(boolean enabled, @NonNull JSONObject context) {
        this.enabled = enabled;
        this.context = context;
    }
}
