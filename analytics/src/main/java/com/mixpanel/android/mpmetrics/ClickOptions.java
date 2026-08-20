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
 * <p><b>Experimental (beta).</b> Autocapture may contain issues, and its API and the properties it
 * captures may change in a future release before general availability. Pin your SDK version if you
 * build reports on autocaptured events.
 *
 * @see AutocaptureOptions.Builder#clickOptions(ClickOptions)
 */
public class ClickOptions {

    private final boolean mEnabled;

    private ClickOptions(Builder builder) {
        this.mEnabled = builder.mEnabled;
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
     * Builder for creating {@link ClickOptions} instances.
     *
     * <p>Default values:
     * <ul>
     *   <li>{@code enabled} = {@code true}</li>
     * </ul>
     */
    public static class Builder {
        private boolean mEnabled = true;

        public Builder() {
        }

        /**
         * Creates a Builder pre-populated with values from an existing {@link ClickOptions}.
         *
         * @param source The ClickOptions to copy values from.
         */
        public Builder(ClickOptions source) {
            this.mEnabled = source.mEnabled;
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
         * Builds and returns a {@link ClickOptions} instance with the configured settings.
         *
         * @return A new {@link ClickOptions} instance.
         */
        public ClickOptions build() {
            return new ClickOptions(this);
        }
    }
}
