package com.mixpanel.android.mpmetrics;

/**
 * Configuration options for dead click detection in autocapture.
 *
 * <p>Dead clicks are detected when a user clicks on an element that appears to be interactive
 * but produces no UI response within a configured timeout period.
 *
 * <pre>{@code
 * DeadClickOptions deadClickOptions = new DeadClickOptions.Builder()
 *     .enabled(true)
 *     .timeWindowMs(1200)     // Wait 1200ms for UI response
 *     .build();
 * }</pre>
 *
 * @see AutocaptureOptions.Builder#deadClickOptions(DeadClickOptions)
 */
public class DeadClickOptions {

    private final boolean mEnabled;
    private final long mTimeWindowMs;

    private DeadClickOptions(Builder builder) {
        this.mEnabled = builder.mEnabled;
        this.mTimeWindowMs = builder.mTimeWindowMs;
    }

    /**
     * Returns whether dead click detection is enabled.
     *
     * @return {@code true} if dead clicks are tracked, {@code false} otherwise.
     *         Defaults to {@code true} when autocapture is enabled.
     */
    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * Returns the time window in milliseconds to wait for a UI response after a click.
     *
     * <p>If no UI change is detected within this window, a dead click event is emitted.
     *
     * @return The time window in milliseconds. Defaults to 500.
     */
    public long getTimeWindowMs() {
        return mTimeWindowMs;
    }

    /**
     * Builder for creating {@link DeadClickOptions} instances.
     *
     * <p>Default values:
     * <ul>
     *   <li>{@code enabled} = {@code true}</li>
     *   <li>{@code timeWindowMs} = 500</li>
     * </ul>
     */
    public static class Builder {
        private boolean mEnabled = true;
        private long mTimeWindowMs = 500;

        public Builder() {
        }

        /**
         * Creates a Builder pre-populated with values from an existing {@link DeadClickOptions}.
         *
         * @param source The DeadClickOptions to copy values from.
         */
        public Builder(DeadClickOptions source) {
            this.mEnabled = source.mEnabled;
            this.mTimeWindowMs = source.mTimeWindowMs;
        }

        /**
         * Enables or disables dead click detection.
         *
         * @param enabled {@code true} to detect dead clicks, {@code false} to disable.
         * @return This Builder instance for chaining.
         */
        public Builder enabled(boolean enabled) {
            this.mEnabled = enabled;
            return this;
        }

        /**
         * Sets the time window in milliseconds to wait for a UI response.
         *
         * @param timeWindowMs The time window in milliseconds. Must be positive.
         * @return This Builder instance for chaining.
         */
        public Builder timeWindowMs(long timeWindowMs) {
            this.mTimeWindowMs = Math.max(1, timeWindowMs);
            return this;
        }

        /**
         * Builds and returns a {@link DeadClickOptions} instance with the configured settings.
         *
         * @return A new {@link DeadClickOptions} instance.
         */
        public DeadClickOptions build() {
            return new DeadClickOptions(this);
        }
    }
}
