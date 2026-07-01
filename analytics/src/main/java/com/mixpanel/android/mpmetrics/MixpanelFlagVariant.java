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
     * /flags/ response; {@link Persistence} for one loaded from the on-disk
     * persistence layer; {@link Fallback} for the developer-supplied fallback the
     * SDK returned because no real variant was available.
     *
     * <p>Variants the SDK returns from {@link MixpanelAPI.Flags#getVariant} or
     * {@link MixpanelAPI.Flags#getVariantSync} always carry a non-null {@code source}.
     * Variants the developer constructs themselves (to pass in as a fallback) have a
     * {@code null} source until the SDK stamps them on return.
     *
     * <p>This is a Java 8-compatible discriminated union (no {@code sealed}
     * keyword) so that variant-specific data — namely the {@code persistedAtMillis}
     * timestamp on {@link Persistence} — is bundled with the variant rather than
     * floating as a separate nullable field on {@link MixpanelFlagVariant}.
     * Construct via {@link #network()} / {@link #persistence(long)} / {@link #fallback()}.
     */
    public abstract static class Source {
        Source() {}

        /** Singleton {@link Network} instance — every call returns the same one. */
        @NonNull
        public static Network network() {
            return Network.INSTANCE;
        }

        /**
         * Returns a {@link Persistence} source stamped with the given write timestamp.
         *
         * @param persistedAtMillis absolute timestamp (millis since epoch) at which
         *                          this variant set was originally written to disk.
         */
        @NonNull
        public static Persistence persistence(long persistedAtMillis) {
            return new Persistence(persistedAtMillis);
        }

        /**
         * Returns a {@link Fallback} source tagged with the given reason.
         * The SDK uses this to explain why a fallback was returned (flag missing
         * from the cache, evaluation error, etc.) so callers — especially the
         * OpenFeature wrapper — can map to the right user-facing error code.
         */
        @NonNull
        public static Fallback fallback(@NonNull Fallback.Reason reason) {
            return new Fallback(reason);
        }

        /** Variant assigned by the most recent successful /flags/ network call. */
        public static final class Network extends Source {
            // Held inside the subclass so the outer class's <clinit> does not reference it,
            // sidestepping the "subclass referenced from superclass initializer" deadlock pattern.
            static final Network INSTANCE = new Network();

            Network() {}

            @Override public String toString() { return "Network"; }
        }

        /** Variant loaded from the on-disk persistence layer. */
        public static final class Persistence extends Source {
            /** Absolute timestamp (millis since epoch) at which this variant set was persisted. */
            public final long persistedAtMillis;

            Persistence(long persistedAtMillis) {
                this.persistedAtMillis = persistedAtMillis;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof Persistence)) return false;
                return persistedAtMillis == ((Persistence) o).persistedAtMillis;
            }

            @Override
            public int hashCode() {
                return (int) (persistedAtMillis ^ (persistedAtMillis >>> 32));
            }

            @Override public String toString() { return "Persistence(persistedAtMillis=" + persistedAtMillis + ")"; }
        }

        /**
         * Developer-supplied fallback returned by the SDK because no flag was found, the
         * lookup happened before flags were ready, or a fetch failed without a usable
         * persisted blob to fall back to.
         *
         * <p>Carries a {@link Reason} so callers can tell <em>why</em> the SDK fell back —
         * previously a Fallback meant only "not a real variant," which collapsed several
         * distinct outcomes into one (SDK-79).
         */
        public static final class Fallback extends Source {
            /**
             * Why the SDK returned the developer fallback.
             *
             * <p>Network/cache SDKs (like Android) cannot distinguish "flag does not exist"
             * from "user is not in any rollout" without server-side cooperation — both
             * surface as {@link #FLAG_NOT_FOUND} for now. Future server changes can add
             * a more specific reason without breaking callers that already handle this enum.
             */
            public enum Reason {
                /** Flag key was not present in the cache or network response. */
                FLAG_NOT_FOUND,
                /** Flags were not ready when the sync lookup happened. */
                NOT_READY,
            }

            /** Reason the SDK returned this fallback. */
            @NonNull
            public final Reason reason;

            Fallback(@NonNull Reason reason) {
                this.reason = reason;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof Fallback)) return false;
                return reason == ((Fallback) o).reason;
            }

            @Override
            public int hashCode() {
                return reason.hashCode();
            }

            @Override public String toString() { return "Fallback(" + reason + ")"; }
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
     * Where this variant came from.
     * <ul>
     *   <li>{@link Source.Network} — assigned by the most recent successful /flags/ response.</li>
     *   <li>{@link Source.Persistence} — loaded from the on-disk persistence layer
     *       (the persistence timestamp lives on the {@link Source.Persistence} instance).</li>
     *   <li>{@link Source.Fallback} — the developer-supplied fallback. This is the default
     *       on every instance the developer constructs, since the only reason to build one
     *       directly is to pass it as a fallback to {@link MixpanelAPI.Flags#getVariant} or
     *       {@link MixpanelAPI.Flags#getVariantSync}.</li>
     * </ul>
     */
    @NonNull
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
        this.source = Source.fallback(Source.Fallback.Reason.FLAG_NOT_FOUND);
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
        this.source = Source.fallback(Source.Fallback.Reason.FLAG_NOT_FOUND);
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
            @NonNull Source source) {
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
     *
     * <p>Public so callers (and tests in adjacent packages) can construct
     * variants with explicit source metadata — the SDK uses this internally
     * to stamp the reason a developer-supplied fallback was returned.
     */
    @NonNull
    public MixpanelFlagVariant withSource(@NonNull Source source) {
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
        this.source = Source.fallback(Source.Fallback.Reason.FLAG_NOT_FOUND);
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
        this.source = Source.fallback(Source.Fallback.Reason.FLAG_NOT_FOUND);
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
        this.source = Source.fallback(Source.Fallback.Reason.FLAG_NOT_FOUND);
    }
}