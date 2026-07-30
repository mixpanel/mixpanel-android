package com.mixpanel.android.mpmetrics;

/**
 * Configuration options for click event autocapture.
 *
 * <p>When enabled, a {@code $mp_click} event is tracked for every tap on any element.
 * Each event includes the touch coordinates, element identifier, class name,
 * accessibility label, semantic role, and view hierarchy path.
 *
 * <p>Use this class to enable or disable click event tracking when autocapture is enabled.
 *
 * <pre>{@code
 * ClickOptions clickOptions = new ClickOptions.Builder()
 *     .enabled(true)
 *     .build();
 * }</pre>
 *
 * @see AutocaptureOptions.Builder#clickOptions(ClickOptions)
 */
public class ClickOptions {

    private final boolean mEnabled;
    private final boolean mWalkUpToClickableParent;

    private ClickOptions(Builder builder) {
        this.mEnabled = builder.mEnabled;
        this.mWalkUpToClickableParent = builder.mWalkUpToClickableParent;
    }

    /**
     * Returns whether click event tracking is enabled.
     *
     * @return {@code true} if click events are tracked, {@code false} otherwise.
     *         Defaults to {@code true} when autocapture is enabled.
     */
    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * Returns whether walk-up-to-clickable-parent is enabled.
     *
     * @return {@code true} if the SDK should walk up to the nearest clickable ancestor
     *         when the tapped view has no meaningful identity.
     */
    public boolean isWalkUpToClickableParent() {
        return mWalkUpToClickableParent;
    }

    /**
     * Builder for creating {@link ClickOptions} instances.
     *
     * <p>Default values:
     * <ul>
     *   <li>{@code enabled} = {@code true}</li>
     * </ul>
     */
    public static class Builder {
        private boolean mEnabled = true;
        private boolean mWalkUpToClickableParent = false;

        public Builder() {
        }

        /**
         * Creates a Builder pre-populated with values from an existing {@link ClickOptions}.
         *
         * @param source The ClickOptions to copy values from.
         */
        public Builder(ClickOptions source) {
            this.mEnabled = source.mEnabled;
            this.mWalkUpToClickableParent = source.mWalkUpToClickableParent;
        }

        /**
         * Enables or disables click event tracking.
         *
         * @param enabled {@code true} to track click events, {@code false} to disable.
         * @return This Builder instance for chaining.
         */
        public Builder enabled(boolean enabled) {
            this.mEnabled = enabled;
            return this;
        }

        /**
         * When enabled, if the tapped view has no meaningful identifier
         * (contentDescription or valid resource ID), the SDK walks up the
         * view hierarchy to the nearest clickable ancestor and uses its
         * identity instead.
         *
         * <p>This is intended for cross-platform frameworks (e.g., React Native,
         * Flutter) where interactive components are wrappers around leaf views
         * that don't carry their own identity. It only affects the
         * {@code android.view.View} extraction path — Compose semantics
         * extraction is not affected.
         *
         * <p>Defaults to {@code false}. Native Android apps should generally
         * leave this disabled.
         *
         * @param walkUp {@code true} to enable ancestor walk-up
         * @return This Builder instance for chaining.
         */
        public Builder walkUpToClickableParent(boolean walkUp) {
            this.mWalkUpToClickableParent = walkUp;
            return this;
        }

        /**
         * Builds and returns a {@link ClickOptions} instance with the configured settings.
         *
         * @return A new {@link ClickOptions} instance.
         */
        public ClickOptions build() {
            return new ClickOptions(this);
        }
    }
}
