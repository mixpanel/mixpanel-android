package com.mixpanel.android.mpmetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Represents the data associated with a feature flag variant from the Mixpanel API.
 * This class stores the key and value of a specific variant for a feature flag.
 * It can be instantiated either by parsing an API response or by creating a fallback instance.
 */
public class FeatureFlagData {

    /**
     * The key of the feature flag variant. This corresponds to the 'variant_key'
     * field in the Mixpanel API response. It cannot be null.
     */
    @NonNull
    public final String key;

    /**
     * The value of the feature flag variant. This corresponds to the 'variant_value'
     * field in the Mixpanel API response. The value can be of type Boolean, String,
     * Number (Integer, Double, Float, Long), JSONArray, JSONObject, or it can be null.
     */
    @Nullable
    public final Object value;

    /**
     * Constructs a {@code FeatureFlagData} object when parsing an API response.
     *
     * @param key The key of the feature flag variant. Corresponds to 'variant_key' from the API. Cannot be null.
     * @param value The value of the feature flag variant. Corresponds to 'variant_value' from the API.
     * Can be Boolean, String, Number, JSONArray, JSONObject, or null.
     */
    public FeatureFlagData(@NonNull String key, @Nullable Object value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Constructs a {@code FeatureFlagData} object for creating fallback instances.
     * In this case, the provided {@code keyAndValue} is used as both the key and the value
     * for the feature flag data. This is typically used when a flag is not found
     * and a default string value needs to be returned.
     *
     * @param keyAndValue The string value to be used as both the key and the value for this fallback. Cannot be null.
     */
    public FeatureFlagData(@NonNull String keyAndValue) {
        this.key = keyAndValue; // Default key to the value itself
        this.value = keyAndValue;
    }

    /**
     * Constructs a {@code FeatureFlagData} object for creating fallback instances.
     * In this version, the key is set to an empty string (""), and the provided {@code value}
     * is used as the value for the feature flag data. This is typically used when a
     * flag is not found or an error occurs, and a default value needs to be provided.
     *
     * @param value The object value to be used for this fallback. Cannot be null.
     * This can be of type Boolean, String, Number, JSONArray, or JSONObject.
     */
    public FeatureFlagData(@NonNull Object value) {
        this.key = "";
        this.value = value;
    }

    /**
     * Default constructor that initializes an empty {@code FeatureFlagData} object.
     * The key is set to an empty string ("") and the value is set to null.
     * This constructor might be used internally or for specific default cases.
     */
    FeatureFlagData() {
        this.key = "";
        this.value = null;
    }
}