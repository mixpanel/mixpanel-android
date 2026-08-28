package com.mixpanel.android.sessionreplay.utils

import androidx.annotation.VisibleForTesting

/**
 * Dimensions of the most recently captured frame, in the logical pixels the screenshot is encoded at.
 *
 * The replay player sizes its viewport from the meta event and scales each frame to fit, so the meta
 * event has to describe the frame that was actually captured.
 */
internal object CapturedViewport {
    data class Size(
        val width: Int,
        val height: Int
    )

    @Volatile
    private var lastCaptured: Size? = null

    /** Ignores non-positive dimensions so a failed capture cannot describe the viewport. */
    fun record(
        width: Int,
        height: Int
    ) {
        if (width > 0 && height > 0) {
            lastCaptured = Size(width, height)
        }
    }

    fun current(): Size? = lastCaptured

    @VisibleForTesting
    internal fun reset() {
        lastCaptured = null
    }
}
