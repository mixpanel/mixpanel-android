package com.mixpanel.android.mpmetrics;

import androidx.annotation.NonNull;

import org.json.JSONObject;

class FlagsConfig {
    public final boolean enabled;
    @NonNull public final JSONObject context;
    @NonNull public final VariantLookupPolicy variantLookupPolicy;

    public FlagsConfig() {
        this.enabled = false;
        this.context = new JSONObject();
        this.variantLookupPolicy = VariantLookupPolicy.networkOnly();
    }

    public FlagsConfig(boolean enabled) {
        this.enabled = enabled;
        this.context = new JSONObject();
        this.variantLookupPolicy = VariantLookupPolicy.networkOnly();
    }

    public FlagsConfig(boolean enabled, @NonNull JSONObject context) {
        this.enabled = enabled;
        this.context = context;
        this.variantLookupPolicy = VariantLookupPolicy.networkOnly();
    }

    public FlagsConfig(boolean enabled, @NonNull JSONObject context, @NonNull VariantLookupPolicy variantLookupPolicy) {
        this.enabled = enabled;
        this.context = context;
        this.variantLookupPolicy = variantLookupPolicy;
    }
}
