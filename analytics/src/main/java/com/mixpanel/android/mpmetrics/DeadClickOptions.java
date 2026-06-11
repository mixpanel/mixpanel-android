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
 *     .timeoutMs(600)         // Wait 600ms for UI response
 *     .baselineDelayMs(200)   // Wait 200ms before taking baseline snapshot
 *     .build();
 * }</pre>
 *
 * @see AutocaptureOptions.Builder#deadClickOptions(DeadClickOptions)
 */
public class DeadClickOptions {

    private final boolean mEnabled;
    private final long mTimeoutMs;
    private final long mBaselineDelayMs;

    private DeadClickOptions(Builder builder) {
        this.mEnabled = builder.mEnabled;
        this.mTimeoutMs = builder.mTimeoutMs;
        this.mBaselineDelayMs = builder.mBaselineDelayMs;
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
     * Returns the timeout in milliseconds to wait for a UI response after a click.
     *
     * <p>If no UI change is detected within this time, a dead click event is emitted.
     *
     * @return The timeout in milliseconds. Defaults to 500.
     */
    public long getTimeoutMs() {
        return mTimeoutMs;
    }

    /**
     * Returns the delay in milliseconds before capturing the baseline UI snapshot.
     *
     * <p>This allows initial UI settling (e.g., ripple effects) before establishing
     * the baseline state against which changes are detected.
     *
     * @return The baseline delay in milliseconds. Defaults to 150.
     */
    public long getBaselineDelayMs() {
        return mBaselineDelayMs;
    }

    /**
     * Builder for creating {@link DeadClickOptions} instances.
     *
     * <p>Default values:
     * <ul>
     *   <li>{@code enabled} = {@code true}</li>
     *   <li>{@code timeoutMs} = 500</li>
     *   <li>{@code baselineDelayMs} = 150</li>
     * </ul>
     */
    public static class Builder {
        private boolean mEnabled = true;
        private long mTimeoutMs = 500;
        private long mBaselineDelayMs = 150;

        public Builder() {
        }

        /**
         * Creates a Builder pre-populated with values from an existing {@link DeadClickOptions}.
         *
         * @param source The DeadClickOptions to copy values from.
         */
        public Builder(DeadClickOptions source) {
            this.mEnabled = source.mEnabled;
            this.mTimeoutMs = source.mTimeoutMs;
            this.mBaselineDelayMs = source.mBaselineDelayMs;
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
         * Sets the timeout in milliseconds to wait for a UI response.
         *
         * @param timeoutMs The timeout in milliseconds. Must be positive.
         * @return This Builder instance for chaining.
         */
        public Builder timeoutMs(long timeoutMs) {
            this.mTimeoutMs = Math.max(1, timeoutMs);
            return this;
        }

        /**
         * Sets the delay in milliseconds before capturing the baseline UI snapshot.
         *
         * @param baselineDelayMs The baseline delay in milliseconds. Must be non-negative.
         * @return This Builder instance for chaining.
         */
        public Builder baselineDelayMs(long baselineDelayMs) {
            this.mBaselineDelayMs = Math.max(0, baselineDelayMs);
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
