package com.mixpanel.android.mpmetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * Configuration options for Mixpanel feature flags.
 *
 * <p>Use this class to consolidate all feature flag settings into a single
 * configuration object when initializing a {@link MixpanelAPI} instance via
 * {@link MixpanelOptions.Builder#flagOptions(FlagOptions)}.
 *
 * <pre>{@code
 * FlagOptions flagOptions = new FlagOptions.Builder()
 *     .enabled(true)
 *     .context(new JSONObject().put("plan", "enterprise"))
 *     .loadOnFirstForeground(true)
 *     .build();
 *
 * MixpanelOptions options = new MixpanelOptions.Builder()
 *     .flagOptions(flagOptions)
 *     .build();
 * }</pre>
 *
 * @see MixpanelOptions.Builder#flagOptions(FlagOptions)
 */
public class FlagOptions {

    private final boolean mEnabled;
    private final JSONObject mContext;
    private final boolean mLoadOnFirstForeground;

    private FlagOptions(Builder builder) {
        this.mEnabled = builder.mEnabled;
        this.mLoadOnFirstForeground = builder.mLoadOnFirstForeground;
        this.mContext = builder.mContext != null ? builder.mContext : new JSONObject();
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
     * this {@code FlagOptions} instance.
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
     * @return {@code true} if flags should auto-load on first foreground,
     *         {@code false} otherwise. Defaults to {@code true}.
     */
    public boolean shouldLoadOnFirstForeground() {
        return mLoadOnFirstForeground;
    }

    /**
     * Builder for creating {@link FlagOptions} instances.
     *
     * <p>Default values:
     * <ul>
     *   <li>{@code enabled} = {@code false}</li>
     *   <li>{@code context} = empty {@link JSONObject}</li>
     *   <li>{@code loadOnFirstForeground} = {@code true}</li>
     * </ul>
     */
    public static class Builder {
        private boolean mEnabled = false;
        private JSONObject mContext = new JSONObject();
        private boolean mLoadOnFirstForeground = true;

        public Builder() {
        }

        /**
         * Creates a Builder pre-populated with values from an existing {@link FlagOptions}.
         *
         * @param source The FlagOptions to copy values from.
         */
        public Builder(FlagOptions source) {
            this.mEnabled = source.mEnabled;
            this.mContext = source.mContext; // same ref is fine, builder will copy on context()
            this.mLoadOnFirstForeground = source.mLoadOnFirstForeground;
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
            // Defensive copy: prevents caller's later mutations from affecting built FlagOptions
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
         * @param loadOnFirstForeground {@code true} to auto-load on first foreground,
         *                              {@code false} to disable auto-loading.
         * @return This Builder instance for chaining.
         */
        public Builder loadOnFirstForeground(boolean loadOnFirstForeground) {
            this.mLoadOnFirstForeground = loadOnFirstForeground;
            return this;
        }

        /**
         * Builds and returns a {@link FlagOptions} instance with the configured settings.
         *
         * @return A new {@link FlagOptions} instance.
         */
        public FlagOptions build() {
            return new FlagOptions(this);
        }
    }
}
