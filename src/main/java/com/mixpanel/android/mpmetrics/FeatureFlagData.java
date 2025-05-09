package com.mixpanel.android.mpmetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable; /**
 * Represents the data associated with a feature flag variant from the Mixpanel API.
 */
public class FeatureFlagData {
    @NonNull public final String key; // Corresponds to 'variant_key'
    @Nullable public final Object value; // Corresponds to 'variant_value', can be Boolean, String, Number, JSONArray, JSONObject, or null

    // Constructor used when parsing the API response
    public FeatureFlagData(@NonNull String key, @Nullable Object value) {
        this.key = key;
        this.value = value;
    }

    // Constructor for creating fallback instances
    public FeatureFlagData(@NonNull String key) {
        this.key = key;
        this.value = key; // Defaulting value to null if not provided
    }

    FeatureFlagData() {
        this.key = "";
        this.value = null;
    }

    // TODO: Add equals() and hashCode() if storing these in sets/maps directly
}
