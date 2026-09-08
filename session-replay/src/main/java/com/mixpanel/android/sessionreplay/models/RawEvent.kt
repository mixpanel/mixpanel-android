package com.mixpanel.android.sessionreplay.models

import android.graphics.Point

data class RawTouchEvent(
    val start: Point,
    val end: Point,
    val isSwipe: Boolean,
    val direction: String? = null
)

/**
 * A compressed frame and the logical pixel dimensions it was captured at. The system display
 * metrics are not equivalent: they exclude the navigation bar on non-edge-to-edge activities.
 */
data class CapturedFrame(
    val data: ByteArray,
    val width: Int,
    val height: Int
)

data class RawScreenshotEvent(
    val data: ByteArray,
    val isInitial: Boolean,
    val width: Int,
    val height: Int
)
