package com.mixpanel.android.mpmetrics;

/**
 * Configuration options for rage click detection in autocapture.
 *
 * <p>Rage clicks are detected when a user rapidly clicks multiple times in the same area,
 * indicating frustration with an unresponsive UI element.
 *
 * <pre>{@code
 * RageClickOptions rageClickOptions = new RageClickOptions.Builder()
 *     .enabled(true)
 *     .clickThreshold(5)      // Require 5 clicks instead of default 4
 *     .timeWindowMs(800)      // Shorter time window
 *     .radius(50f)            // Larger click radius
 *     .build();
 * }</pre>
 *
 * @see AutocaptureOptions.Builder#rageClickOptions(RageClickOptions)
 */
public class RageClickOptions {

    private final boolean mEnabled;
    private final int mClickThreshold;
    private final long mTimeWindowMs;
    private final float mRadius;

    private RageClickOptions(Builder builder) {
        this.mEnabled = builder.mEnabled;
        this.mClickThreshold = builder.mClickThreshold;
        this.mTimeWindowMs = builder.mTimeWindowMs;
        this.mRadius = builder.mRadius;
    }

    /**
     * Returns whether rage click detection is enabled.
     *
     * @return {@code true} if rage clicks are tracked, {@code false} otherwise.
     *         Defaults to {@code true} when autocapture is enabled.
     */
    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * Returns the number of clicks required to trigger a rage click event.
     *
     * @return The click threshold. Defaults to 4.
     */
    public int getClickThreshold() {
        return mClickThreshold;
    }

    /**
     * Returns the time window in milliseconds within which clicks must occur
     * to be considered a rage click.
     *
     * @return The time window in milliseconds. Defaults to 1000.
     */
    public long getTimeWindowMs() {
        return mTimeWindowMs;
    }

    /**
     * Returns the spatial threshold for rage click detection.
     *
     * <p>Clicks within this radius (in dp) are considered to be in the "same location".
     *
     * @return The radius in density-independent pixels (dp). Defaults to 44.
     */
    public float getRadius() {
        return mRadius;
    }

    /**
     * Builder for creating {@link RageClickOptions} instances.
     *
     * <p>Default values:
     * <ul>
     *   <li>{@code enabled} = {@code true}</li>
     *   <li>{@code clickThreshold} = 4</li>
     *   <li>{@code timeWindowMs} = 1000</li>
     *   <li>{@code radius} = 44 (dp)</li>
     * </ul>
     */
    public static class Builder {
        private boolean mEnabled = true;
        private int mClickThreshold = 4;
        private long mTimeWindowMs = 1000;
        private float mRadius = 44f;

        public Builder() {
        }

        /**
         * Creates a Builder pre-populated with values from an existing {@link RageClickOptions}.
         *
         * @param source The RageClickOptions to copy values from.
         */
        public Builder(RageClickOptions source) {
            this.mEnabled = source.mEnabled;
            this.mClickThreshold = source.mClickThreshold;
            this.mTimeWindowMs = source.mTimeWindowMs;
            this.mRadius = source.mRadius;
        }

        /**
         * Enables or disables rage click detection.
         *
         * @param enabled {@code true} to detect rage clicks, {@code false} to disable.
         * @return This Builder instance for chaining.
         */
        public Builder enabled(boolean enabled) {
            this.mEnabled = enabled;
            return this;
        }

        /**
         * Sets the number of clicks required to trigger a rage click event.
         *
         * @param clickThreshold The number of clicks required. Must be at least 2.
         * @return This Builder instance for chaining.
         */
        public Builder clickThreshold(int clickThreshold) {
            this.mClickThreshold = Math.max(2, clickThreshold);
            return this;
        }

        /**
         * Sets the time window in milliseconds within which clicks must occur.
         *
         * @param timeWindowMs The time window in milliseconds. Must be positive.
         * @return This Builder instance for chaining.
         */
        public Builder timeWindowMs(long timeWindowMs) {
            this.mTimeWindowMs = Math.max(1, timeWindowMs);
            return this;
        }

        /**
         * Sets the spatial threshold for rage click detection.
         *
         * <p>Clicks within this radius are considered to be in the "same location".
         * Unit: dp (density-independent pixels) on Android.
         *
         * @param radius The radius in dp within which clicks are considered "same location".
         * @return This Builder instance for chaining.
         */
        public Builder radius(float radius) {
            this.mRadius = Math.max(0, radius);
            return this;
        }

        /**
         * Builds and returns a {@link RageClickOptions} instance with the configured settings.
         *
         * @return A new {@link RageClickOptions} instance.
         */
        public RageClickOptions build() {
            return new RageClickOptions(this);
        }
    }
}
