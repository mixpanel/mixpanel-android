package com.mixpanel.android.mpmetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Represents the data associated with a feature flag variant from the Mixpanel API.
 * This class stores the key and value of a specific variant for a feature flag.
 * It can be instantiated either by parsing an API response or by creating a fallback instance.
 */
public class MixpanelFlagVariant {

    /**
     * Identifies where a served variant came from. {@link Network} for a fresh
     * /flags/ response; {@link Cache} for one loaded from the on-disk cache.
     * Populated only when the SDK actually serves a variant — developer-supplied
     * fallbacks have a {@code null} source.
     *
     * <p>This is a Java 8-compatible discriminated union (no {@code sealed}
     * keyword) so that variant-specific data — namely the {@code cachedAtMillis}
     * timestamp on {@link Cache} — is bundled with the variant rather than
     * floating as a separate nullable field on {@link MixpanelFlagVariant}.
     * Construct via {@link #network()} / {@link #cache(long)}.
     */
    public abstract static class Source {
        Source() {}

        /** Singleton {@link Network} instance — every call returns the same one. */
        @NonNull
        public static Network network() {
            return Network.INSTANCE;
        }

        /**
         * Returns a {@link Cache} source stamped with the given write timestamp.
         *
         * @param cachedAtMillis absolute timestamp (millis since epoch) at which
         *                       this variant set was originally written to the cache.
         */
        @NonNull
        public static Cache cache(long cachedAtMillis) {
            return new Cache(cachedAtMillis);
        }

        /** Variant assigned by the most recent successful /flags/ network call. */
        public static final class Network extends Source {
            // Held inside the subclass so the outer class's <clinit> does not reference it,
            // sidestepping the "subclass referenced from superclass initializer" deadlock pattern.
            static final Network INSTANCE = new Network();

            Network() {}
        }

        /** Variant loaded from the on-disk cache. */
        public static final class Cache extends Source {
            /** Absolute timestamp (millis since epoch) at which this variant set was cached. */
            public final long cachedAtMillis;

            Cache(long cachedAtMillis) {
                this.cachedAtMillis = cachedAtMillis;
            }
        }
    }

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
     * The value of experimentID. This corresponds to the optional 'experiment_id' field in the Mixpanel API response.
     */
    @Nullable
    public final String experimentID;

    /**
     * The value of isExperimentActive. This corresponds to the optional 'is_experiment_active' field in the Mixpanel API response.
     */
    @Nullable
    public final Boolean isExperimentActive;

    /**
     * The value of isQATester. This corresponds to the optional 'is_qa_tester' field in the Mixpanel API response.
     */
    @Nullable
    public final Boolean isQATester;

    /**
     * Where this variant was sourced from. {@code null} on developer-supplied
     * fallback instances; {@link Source.Network} or {@link Source.Cache} when
     * the SDK serves a variant. For cached variants, the cache timestamp lives
     * on the {@link Source.Cache} instance itself rather than as a separate
     * field — this makes invalid combinations like NETWORK + non-null timestamp
     * impossible to construct.
     */
    @Nullable
    public final Source source;

    /**
     * Constructs a {@code MixpanelFlagVariant} object when parsing an API response.
     *
     * @param key The key of the feature flag variant. Corresponds to 'variant_key' from the API. Cannot be null.
     * @param value The value of the feature flag variant. Corresponds to 'variant_value' from the API.
     * Can be Boolean, String, Number, JSONArray, JSONObject, or null.
     */
    public MixpanelFlagVariant(@NonNull String key, @Nullable Object value) {
        this.key = key;
        this.value = value;
        this.experimentID = null;
        this.isExperimentActive = null;
        this.isQATester = null;
        this.source = null;
    }

    /**
     * Constructs a {@code MixpanelFlagVariant} object when parsing an API response with optional experiment fields.
     *
     * @param key The key of the feature flag variant. Corresponds to 'variant_key' from the API. Cannot be null.
     * @param value The value of the feature flag variant. Corresponds to 'variant_value' from the API.
     * Can be Boolean, String, Number, JSONArray, JSONObject, or null.
     * @param experimentID The experiment ID. Corresponds to 'experiment_id' from the API. Can be null.
     * @param isExperimentActive Whether the experiment is active. Corresponds to 'is_experiment_active' from the API. Can be null.
     * @param isQATester Whether the user is a QA tester. Corresponds to 'is_qa_tester' from the API. Can be null.
     */
    public MixpanelFlagVariant(@NonNull String key, @Nullable Object value, @Nullable String experimentID, @Nullable Boolean isExperimentActive, @Nullable Boolean isQATester) {
        this.key = key;
        this.value = value;
        this.experimentID = experimentID;
        this.isExperimentActive = isExperimentActive;
        this.isQATester = isQATester;
        this.source = null;
    }

    /**
     * Full constructor including source metadata. Used internally by the SDK
     * when stamping a served variant with its origin.
     */
    MixpanelFlagVariant(
            @NonNull String key,
            @Nullable Object value,
            @Nullable String experimentID,
            @Nullable Boolean isExperimentActive,
            @Nullable Boolean isQATester,
            @Nullable Source source) {
        this.key = key;
        this.value = value;
        this.experimentID = experimentID;
        this.isExperimentActive = isExperimentActive;
        this.isQATester = isQATester;
        this.source = source;
    }

    /**
     * Returns a copy of this variant stamped with the given source metadata.
     * Other fields (key, value, experiment fields) are preserved by reference.
     */
    @NonNull
    MixpanelFlagVariant withSource(@NonNull Source source) {
        return new MixpanelFlagVariant(
                this.key,
                this.value,
                this.experimentID,
                this.isExperimentActive,
                this.isQATester,
                source);
    }

    /**
     * Constructs a {@code MixpanelFlagVariant} object for creating fallback instances.
     * In this case, the provided {@code keyAndValue} is used as both the key and the value
     * for the feature flag data. This is typically used when a flag is not found
     * and a default string value needs to be returned.
     *
     * @param keyAndValue The string value to be used as both the key and the value for this fallback. Cannot be null.
     */
    public MixpanelFlagVariant(@NonNull String keyAndValue) {
        this.key = keyAndValue; // Default key to the value itself
        this.value = keyAndValue;
        this.experimentID = null;
        this.isExperimentActive = null;
        this.isQATester = null;
        this.source = null;
    }

    /**
     * Constructs a {@code MixpanelFlagVariant} object for creating fallback instances.
     * In this version, the key is set to an empty string (""), and the provided {@code value}
     * is used as the value for the feature flag data. This is typically used when a
     * flag is not found or an error occurs, and a default value needs to be provided.
     *
     * @param value The object value to be used for this fallback. Cannot be null.
     * This can be of type Boolean, String, Number, JSONArray, or JSONObject.
     */
    public MixpanelFlagVariant(@NonNull Object value) {
        this.key = "";
        this.value = value;
        this.experimentID = null;
        this.isExperimentActive = null;
        this.isQATester = null;
        this.source = null;
    }

    /**
     * Default constructor that initializes an empty {@code MixpanelFlagVariant} object.
     * The key is set to an empty string ("") and the value is set to null.
     * This constructor might be used internally or for specific default cases.
     */
    MixpanelFlagVariant() {
        this.key = "";
        this.value = null;
        this.experimentID = null;
        this.isExperimentActive = null;
        this.isQATester = null;
        this.source = null;
    }
}