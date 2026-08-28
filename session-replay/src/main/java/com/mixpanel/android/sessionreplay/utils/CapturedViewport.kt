package com.mixpanel.android.sessionreplay.utils

import androidx.annotation.VisibleForTesting

/**
 * Dimensions of the most recently captured frame, in the logical pixels the screenshot is
 * encoded at.
 *
 * The replay player sizes its viewport from the meta event and scales each frame to fit, so the
 * meta event has to describe the frame that was actually captured. Written by the screen
 * recorder, read when a payload's meta event is built.
 *
 * Captures are tagged with the replay they belong to. The recorder outlives any one replay, so
 * an untagged value would let a replay that has not captured a frame yet describe itself with
 * the previous replay's dimensions.
 */
internal object CapturedViewport {
    data class Size(
        val width: Int,
        val height: Int
    )

    private data class Captured(
        val replayId: String,
        val size: Size
    )

    @Volatile
    private var currentReplayId: String? = null

    @Volatile
    private var lastCaptured: Captured? = null

    /** Names the replay that subsequent captures belong to. */
    fun setCurrentReplay(replayId: String) {
        currentReplayId = replayId
    }

    /** Ignores non-positive dimensions so a failed capture cannot describe the viewport. */
    fun record(
        width: Int,
        height: Int
    ) {
        val replayId = currentReplayId ?: return
        if (width > 0 && height > 0) {
            lastCaptured = Captured(replayId, Size(width, height))
        }
    }

    /** Null until [replayId] itself has captured a frame, so a payload flushed before its
     * replay's first capture falls back to the system metrics. */
    fun current(replayId: String): Size? = lastCaptured?.takeIf { it.replayId == replayId }?.size

    @VisibleForTesting
    internal fun reset() {
        currentReplayId = null
        lastCaptured = null
    }
}
