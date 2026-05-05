package com.mixpanel.android.mpmetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * Configuration options for Mixpanel feature flags.
 *
 * <p>Use this class to consolidate all feature flag settings into a single
 * configuration object when initializing a {@link MixpanelAPI} instance via
 * {@link MixpanelOptions.Builder#featureFlagOptions(FeatureFlagOptions)}.
 *
 * <pre>{@code
 * FeatureFlagOptions featureFlagOptions = new FeatureFlagOptions.Builder()
 *     .enabled(true)
 *     .context(new JSONObject().put("plan", "enterprise"))
 *     .prefetchFlags(true)
 *     .variantLookupPolicy(VariantLookupPolicy.persistenceUntilNetworkSuccess())
 *     .build();
 *
 * MixpanelOptions options = new MixpanelOptions.Builder()
 *     .featureFlagOptions(featureFlagOptions)
 *     .build();
 * }</pre>
 *
 * @see MixpanelOptions.Builder#featureFlagOptions(FeatureFlagOptions)
 */
public class FeatureFlagOptions {

    private final boolean mEnabled;
    private final JSONObject mContext;
    private final boolean mPrefetchFlags;
    private final VariantLookupPolicy mVariantLookupPolicy;

    private FeatureFlagOptions(Builder builder) {
        this.mEnabled = builder.mEnabled;
        this.mPrefetchFlags = builder.mPrefetchFlags;
        this.mContext = builder.mContext != null ? builder.mContext : new JSONObject();
        this.mVariantLookupPolicy = builder.mVariantLookupPolicy != null ? builder.mVariantLookupPolicy : VariantLookupPolicy.networkOnly();
    }

    /**
     * Returns whether feature flags are enabled.
     *
     * @return {@code true} if feature flags are enabled, {@code false} otherwise.
     *         Defaults to {@code false}.
     */
    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * Returns a defensive copy of the context used for evaluating feature flags.
     *
     * <p>The returned {@link JSONObject} is a copy; mutating it will not affect
     * this {@code FeatureFlagOptions} instance.
     *
     * @return A non-null JSONObject containing the feature flags context.
     *         Defaults to an empty JSONObject.
     */
    @NonNull
    public JSONObject getContext() {
        // Defensive copy: JSONObject is mutable, so return a copy to preserve immutability
        try {
            return new JSONObject(mContext.toString());
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /**
     * Returns whether feature flags should be automatically loaded on the first
     * app foreground event.
     *
     * @return {@code true} if flags should be prefetched on first foreground,
     *         {@code false} otherwise. Defaults to {@code true}.
     */
    public boolean shouldPrefetchFlags() {
        return mPrefetchFlags;
    }

    /**
     * Returns the strategy used to resolve flag variants relative to the
     * on-disk persistence layer and the network. Determines whether the SDK reads and writes
     * persisted variants: {@link VariantLookupPolicy#networkOnly()} disables both;
     * {@link VariantLookupPolicy#persistenceUntilNetworkSuccess()} and
     * {@link VariantLookupPolicy#networkFirst()} enable both.
     *
     * @return the configured {@link VariantLookupPolicy}; defaults to
     *         {@link VariantLookupPolicy#networkOnly()}.
     */
    @NonNull
    public VariantLookupPolicy getVariantLookupPolicy() {
        return mVariantLookupPolicy;
    }

    /**
     * Builder for creating {@link FeatureFlagOptions} instances.
     *
     * <p>Default values:
     * <ul>
     *   <li>{@code enabled} = {@code false}</li>
     *   <li>{@code context} = empty {@link JSONObject}</li>
     *   <li>{@code prefetchFlags} = {@code true}</li>
     *   <li>{@code variantLookupPolicy} = {@link VariantLookupPolicy#networkOnly()}</li>
     * </ul>
     */
    public static class Builder {
        private boolean mEnabled = false;
        private JSONObject mContext = new JSONObject();
        private boolean mPrefetchFlags = true;
        private VariantLookupPolicy mVariantLookupPolicy = VariantLookupPolicy.networkOnly();

        public Builder() {
        }

        /**
         * Creates a Builder pre-populated with values from an existing {@link FeatureFlagOptions}.
         *
         * @param source The FeatureFlagOptions to copy values from.
         */
        public Builder(FeatureFlagOptions source) {
            this.mEnabled = source.mEnabled;
            this.mContext = source.mContext; // same ref is fine, builder will copy on context()
            this.mPrefetchFlags = source.mPrefetchFlags;
            this.mVariantLookupPolicy = source.mVariantLookupPolicy;
        }

        /**
         * Enables or disables feature flags.
         *
         * @param enabled {@code true} to enable feature flags, {@code false} to disable.
         * @return This Builder instance for chaining.
         */
        public Builder enabled(boolean enabled) {
            this.mEnabled = enabled;
            return this;
        }

        /**
         * Sets the context used for evaluating feature flags.
         *
         * <p>The provided JSONObject is defensively copied to prevent external mutation.
         *
         * @param context A JSONObject containing key-value pairs for the feature flags context,
         *                or {@code null} for an empty context.
         * @return This Builder instance for chaining.
         */
        public Builder context(@Nullable JSONObject context) {
            // Defensive copy: prevents caller's later mutations from affecting built FeatureFlagOptions
            if (context == null) {
                this.mContext = new JSONObject();
            } else {
                try {
                    this.mContext = new JSONObject(context.toString());
                } catch (Exception e) {
                    this.mContext = new JSONObject();
                }
            }
            return this;
        }

        /**
         * Controls whether feature flags are automatically fetched on the first app
         * foreground event.
         *
         * <p>Set to {@code false} if you want to manually control when flags are loaded
         * (e.g., by calling {@code getFlags().loadFlags()} yourself).
         *
         * @param prefetchFlags {@code true} to prefetch on first foreground,
         *                      {@code false} to disable prefetching.
         * @return This Builder instance for chaining.
         */
        public Builder prefetchFlags(boolean prefetchFlags) {
            this.mPrefetchFlags = prefetchFlags;
            return this;
        }

        /**
         * Sets the strategy used to resolve flag variants relative to the
         * on-disk persistence layer and the network.
         *
         * @param variantLookupPolicy the lookup strategy to use.
         * @return This Builder instance for chaining.
         * @see VariantLookupPolicy
         */
        public Builder variantLookupPolicy(@NonNull VariantLookupPolicy variantLookupPolicy) {
            this.mVariantLookupPolicy = variantLookupPolicy;
            return this;
        }

        /**
         * Builds and returns a {@link FeatureFlagOptions} instance with the configured settings.
         *
         * @return A new {@link FeatureFlagOptions} instance.
         */
        public FeatureFlagOptions build() {
            return new FeatureFlagOptions(this);
        }
    }
}
