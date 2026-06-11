package com.mixpanel.android.mpmetrics;

import androidx.annotation.NonNull;

/**
 * Configuration options for Mixpanel autocapture.
 *
 * <p>Autocapture automatically tracks user interactions without requiring manual instrumentation.
 * Phase 1 supports click events, rage clicks, and dead clicks.
 *
 * <p>Autocapture is <b>disabled by default</b>. To enable, create an AutocaptureOptions instance
 * and pass it to {@link MixpanelOptions.Builder#autocaptureOptions(AutocaptureOptions)}.
 *
 * <pre>{@code
 * // Enable all autocapture with defaults
 * AutocaptureOptions autocaptureOptions = new AutocaptureOptions.Builder().build();
 *
 * // Or customize individual event types
 * AutocaptureOptions autocaptureOptions = new AutocaptureOptions.Builder()
 *     .clickOptions(new ClickOptions.Builder().enabled(true).build())
 *     .rageClickOptions(new RageClickOptions.Builder()
 *         .enabled(true)
 *         .clickThreshold(5)
 *         .build())
 *     .deadClickOptions(new DeadClickOptions.Builder()
 *         .enabled(false)  // Disable dead click detection
 *         .build())
 *     .build();
 *
 * MixpanelOptions options = new MixpanelOptions.Builder()
 *     .autocaptureOptions(autocaptureOptions)
 *     .build();
 *
 * MixpanelAPI mixpanel = MixpanelAPI.getInstance(context, "YOUR_TOKEN", true, options);
 * }</pre>
 *
 * @see MixpanelOptions.Builder#autocaptureOptions(AutocaptureOptions)
 * @see ClickOptions
 * @see RageClickOptions
 * @see DeadClickOptions
 */
public class AutocaptureOptions {

    private final ClickOptions mClickOptions;
    private final RageClickOptions mRageClickOptions;
    private final DeadClickOptions mDeadClickOptions;

    private AutocaptureOptions(Builder builder) {
        this.mClickOptions = builder.mClickOptions;
        this.mRageClickOptions = builder.mRageClickOptions;
        this.mDeadClickOptions = builder.mDeadClickOptions;
    }

    /**
     * Returns whether any autocapture feature is enabled.
     *
     * <p>This returns {@code true} if at least one of click, rage click, or dead click
     * detection is enabled.
     *
     * @return {@code true} if any autocapture feature is enabled, {@code false} otherwise.
     */
    public boolean isEnabled() {
        return mClickOptions.isEnabled() ||
               mRageClickOptions.isEnabled() ||
               mDeadClickOptions.isEnabled();
    }

    /**
     * Returns the click options configuration.
     *
     * @return The {@link ClickOptions} for click event tracking.
     */
    @NonNull
    public ClickOptions getClickOptions() {
        return mClickOptions;
    }

    /**
     * Returns the rage click options configuration.
     *
     * @return The {@link RageClickOptions} for rage click detection.
     */
    @NonNull
    public RageClickOptions getRageClickOptions() {
        return mRageClickOptions;
    }

    /**
     * Returns the dead click options configuration.
     *
     * @return The {@link DeadClickOptions} for dead click detection.
     */
    @NonNull
    public DeadClickOptions getDeadClickOptions() {
        return mDeadClickOptions;
    }

    /**
     * Builder for creating {@link AutocaptureOptions} instances.
     *
     * <p>When built, all event types are enabled by default with their respective default settings.
     * Use the individual options builders to customize or disable specific event types.
     */
    public static class Builder {
        private ClickOptions mClickOptions = new ClickOptions.Builder().build();
        private RageClickOptions mRageClickOptions = new RageClickOptions.Builder().build();
        private DeadClickOptions mDeadClickOptions = new DeadClickOptions.Builder().build();

        public Builder() {
        }

        /**
         * Creates a Builder pre-populated with values from an existing {@link AutocaptureOptions}.
         *
         * @param source The AutocaptureOptions to copy values from.
         */
        public Builder(AutocaptureOptions source) {
            this.mClickOptions = source.mClickOptions;
            this.mRageClickOptions = source.mRageClickOptions;
            this.mDeadClickOptions = source.mDeadClickOptions;
        }

        /**
         * Sets the click options for autocapture.
         *
         * @param clickOptions The {@link ClickOptions} configuration.
         * @return This Builder instance for chaining.
         */
        public Builder clickOptions(@NonNull ClickOptions clickOptions) {
            this.mClickOptions = clickOptions;
            return this;
        }

        /**
         * Sets the rage click options for autocapture.
         *
         * @param rageClickOptions The {@link RageClickOptions} configuration.
         * @return This Builder instance for chaining.
         */
        public Builder rageClickOptions(@NonNull RageClickOptions rageClickOptions) {
            this.mRageClickOptions = rageClickOptions;
            return this;
        }

        /**
         * Sets the dead click options for autocapture.
         *
         * @param deadClickOptions The {@link DeadClickOptions} configuration.
         * @return This Builder instance for chaining.
         */
        public Builder deadClickOptions(@NonNull DeadClickOptions deadClickOptions) {
            this.mDeadClickOptions = deadClickOptions;
            return this;
        }

        /**
         * Builds and returns an {@link AutocaptureOptions} instance with the configured settings.
         *
         * @return A new {@link AutocaptureOptions} instance.
         */
        public AutocaptureOptions build() {
            return new AutocaptureOptions(this);
        }
    }
}
