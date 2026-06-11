package com.mixpanel.android.autocapture;

import android.content.Context;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.mixpanel.android.mpmetrics.RageClickOptions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tracks click patterns to detect rage clicks.
 *
 * <p>A rage click is detected when a user rapidly clicks multiple times in the same area,
 * indicating frustration with an unresponsive UI element.
 *
 * <p>Detection criteria (all configurable):
 * <ul>
 *   <li>N clicks within time window (default: 4 clicks within 1000ms)</li>
 *   <li>All clicks within spatial radius (default: 44dp)</li>
 * </ul>
 *
 * <p>Thread safety: This class is NOT thread-safe and should only be called from the main thread.
 */
final class RageClickTracker {

    /**
     * Interface for providing current time (for testability).
     */
    interface TimeProvider {
        long currentTimeMillis();
    }

    private final int mClickThreshold;
    private final long mTimeWindowMs;
    private final float mRadiusPx;
    private final TimeProvider mTimeProvider;

    private final List<ClickRecord> mRecentClicks = new ArrayList<>();

    /**
     * Creates a RageClickTracker with the given options and screen density.
     *
     * @param options The rage click configuration options.
     * @param context The context to get screen density.
     */
    RageClickTracker(@NonNull RageClickOptions options, @NonNull Context context) {
        this(options, context, System::currentTimeMillis);
    }

    /**
     * Creates a RageClickTracker with injectable time provider (for testing).
     */
    @VisibleForTesting
    RageClickTracker(@NonNull RageClickOptions options, @NonNull Context context, @NonNull TimeProvider timeProvider) {
        mClickThreshold = options.getClickThreshold();
        mTimeWindowMs = options.getTimeWindowMs();
        mTimeProvider = timeProvider;

        // Convert dp to pixels
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        mRadiusPx = options.getRadius() * metrics.density;
    }

    /**
     * Records a click and checks if it triggers a rage click.
     *
     * @param clickEvent The click event to record.
     * @return The ClickEvent that triggered the rage click (same as input), or null if no rage click detected.
     */
    @Nullable
    ClickEvent recordClick(@NonNull ClickEvent clickEvent) {
        long now = mTimeProvider.currentTimeMillis();
        float x = clickEvent.x;
        float y = clickEvent.y;

        // Remove expired clicks
        pruneExpiredClicks(now);

        // Add new click
        mRecentClicks.add(new ClickRecord(x, y, now));

        // Count clicks within radius of the current click
        int clicksInRadius = countClicksInRadius(x, y);

        // Check if threshold reached
        if (clicksInRadius >= mClickThreshold) {
            // Clear tracked clicks to avoid multiple rage click events for the same sequence
            mRecentClicks.clear();
            return clickEvent;
        }

        return null;
    }

    /**
     * Clears all tracked clicks.
     * Call this on activity changes or when the user navigates away.
     */
    void clear() {
        mRecentClicks.clear();
    }

    /**
     * Removes clicks that are outside the time window.
     */
    private void pruneExpiredClicks(long now) {
        long cutoff = now - mTimeWindowMs;
        Iterator<ClickRecord> iterator = mRecentClicks.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().timestamp < cutoff) {
                iterator.remove();
            }
        }
    }

    /**
     * Counts clicks within the spatial radius of the given position.
     */
    private int countClicksInRadius(float x, float y) {
        int count = 0;
        float radiusSquared = mRadiusPx * mRadiusPx;

        for (ClickRecord record : mRecentClicks) {
            float dx = record.x - x;
            float dy = record.y - y;
            float distanceSquared = dx * dx + dy * dy;

            if (distanceSquared <= radiusSquared) {
                count++;
            }
        }

        return count;
    }

    /**
     * Simple record of a click's position and time.
     */
    private static class ClickRecord {
        final float x;
        final float y;
        final long timestamp;

        ClickRecord(float x, float y, long timestamp) {
            this.x = x;
            this.y = y;
            this.timestamp = timestamp;
        }
    }
}
