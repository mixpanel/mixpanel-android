package com.mixpanel.android.sessionreplay.models

import android.graphics.Point

data class RawTouchEvent(
    val start: Point,
    val end: Point,
    val isSwipe: Boolean,
    val direction: String? = null
)

data class RawScreenshotEvent(
    val data: ByteArray,
    val isInitial: Boolean
)
