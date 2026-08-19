package com.mixpanel.android.mpmetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mixpanel.android.autocapture.ComposeElementIdExtractor;
import com.mixpanel.android.autocapture.ViewElementIdExtractor;

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
    private final ViewElementIdExtractor mViewElementIdExtractor;
    private final ComposeElementIdExtractor mComposeElementIdExtractor;

    private AutocaptureOptions(Builder builder) {
        this.mClickOptions = builder.mClickOptions;
        this.mRageClickOptions = builder.mRageClickOptions;
        this.mDeadClickOptions = builder.mDeadClickOptions;
        this.mViewElementIdExtractor = builder.mViewElementIdExtractor;
        this.mComposeElementIdExtractor = builder.mComposeElementIdExtractor;
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
     * Returns the custom element id extractor for the View path, if one was provided.
     *
     * <p>When {@code null}, the SDK resolves {@code $el_id} for Views with its internal default
     * implementation (React Native {@code nativeID}, then Android resource id, then content
     * description, then an anonymous {@code <SimpleClassName>_<hash>} identifier).
     *
     * @return The {@link ViewElementIdExtractor} supplied by the host app, or {@code null}.
     */
    @Nullable
    public ViewElementIdExtractor getViewElementIdExtractor() {
        return mViewElementIdExtractor;
    }

    /**
     * Returns the custom element id extractor for the Jetpack Compose path, if one was provided.
     *
     * <p>When {@code null} <b>and</b> no {@link ViewElementIdExtractor} is configured either, the SDK
     * resolves Compose {@code $el_id} with its internal default implementation
     * ({@code Modifier.testTag(...)}, then contentDescription, then an anonymous
     * {@code <TagName>_<hash>} identifier).
     *
     * <p>When this is {@code null} but a {@link ViewElementIdExtractor} <i>is</i> configured, Compose
     * interactions report the anonymous identifier: an app that took control of identifiers on one
     * path should never have semantics-derived text — which can contain user data — reported on the
     * other.
     *
     * @return The {@link ComposeElementIdExtractor} supplied by the host app, or {@code null}.
     */
    @Nullable
    public ComposeElementIdExtractor getComposeElementIdExtractor() {
        return mComposeElementIdExtractor;
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
        private ViewElementIdExtractor mViewElementIdExtractor = null;
        private ComposeElementIdExtractor mComposeElementIdExtractor = null;

        /**
         * Creates a Builder with all event types enabled by default.
         *
         * <p>{@code new AutocaptureOptions.Builder().build()} enables click, rage click,
         * and dead click tracking with default settings. Use the individual setters
         * only to customize or disable specific event types.
         */
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
            this.mViewElementIdExtractor = source.mViewElementIdExtractor;
            this.mComposeElementIdExtractor = source.mComposeElementIdExtractor;
        }

        /**
         * Sets the click options for autocapture.
         *
         * @param clickOptions The {@link ClickOptions} configuration.
         * @return This Builder instance for chaining.
         */
        public Builder clickOptions(@NonNull ClickOptions clickOptions) {
            if (clickOptions == null) return this;
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
            if (rageClickOptions == null) return this;
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
            if (deadClickOptions == null) return this;
            this.mDeadClickOptions = deadClickOptions;
            return this;
        }

        /**
         * Sets a custom extractor that resolves the {@code $el_id} reported for a tapped
         * <b>View</b> — XML layouts, and everything React Native renders.
         *
         * <p>Use this to control exactly which identifier autocapture reports and to keep
         * personally identifiable information out of the payload. Pass {@code null} (the default)
         * to use the SDK's internal default resolution.
         *
         * <p>If the app also renders Jetpack Compose, set
         * {@link #composeElementIdExtractor(ComposeElementIdExtractor)} as well: Compose elements
         * are semantics nodes rather than Views, and while this extractor is set on its own the SDK
         * reports anonymous identifiers for them rather than semantics-derived text.
         *
         * @param viewElementIdExtractor The {@link ViewElementIdExtractor} to use, or {@code null}
         *                               for the SDK default.
         * @return This Builder instance for chaining.
         */
        public Builder viewElementIdExtractor(
                @Nullable ViewElementIdExtractor viewElementIdExtractor) {
            this.mViewElementIdExtractor = viewElementIdExtractor;
            return this;
        }

        /**
         * Sets a custom extractor that resolves the {@code $el_id} reported for a tapped
         * <b>Jetpack Compose</b> element.
         *
         * <p>The Compose counterpart of
         * {@link #viewElementIdExtractor(ViewElementIdExtractor)}. Pass {@code null} (the default)
         * to use the SDK's internal default resolution — {@code Modifier.testTag(...)}, then
         * contentDescription, then an anonymous identifier.
         *
         * @param composeElementIdExtractor The {@link ComposeElementIdExtractor} to use, or
         *                                  {@code null} for the SDK default.
         * @return This Builder instance for chaining.
         */
        public Builder composeElementIdExtractor(
                @Nullable ComposeElementIdExtractor composeElementIdExtractor) {
            this.mComposeElementIdExtractor = composeElementIdExtractor;
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
