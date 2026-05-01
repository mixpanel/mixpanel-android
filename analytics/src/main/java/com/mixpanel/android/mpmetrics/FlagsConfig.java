package com.mixpanel.android.mpmetrics;

import androidx.annotation.NonNull;

import org.json.JSONObject;

class FlagsConfig {
    public final boolean enabled;
    @NonNull public final JSONObject context;
    @NonNull public final VariantLookupPolicy variantLookupPolicy;
    public final boolean cacheVariants;

    FlagsConfig(
        boolean enabled,
        @NonNull JSONObject context,
        @NonNull VariantLookupPolicy variantLookupPolicy,
        boolean cacheVariants
    ) {
        this.enabled = enabled;
        this.context = context;
        this.variantLookupPolicy = variantLookupPolicy;
        this.cacheVariants = cacheVariants;
    }
}
