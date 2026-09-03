package com.mixpanel.android.sessionreplay.models

import android.graphics.Point

/**
 * One sampled point of a drag, in 1x logical pixels, paired with the wall-clock
 * millisecond at which the finger was actually there (derived from
 * `MotionEvent.eventTime`, not from when the SDK got around to processing it).
 */
data class TouchSample(
    val point: Point,
    val timestamp: Long
)

/**
 * A touch captured off the window's `MotionEvent` stream, before it is encoded into an
 * rrweb incremental snapshot by
 * [com.mixpanel.android.sessionreplay.tracking.EventHandler].
 *
 * Timestamps are wall-clock milliseconds taken from the originating `MotionEvent`, so no
 * pipeline-lag fudge factor is applied downstream.
 */
sealed class RawTouchEvent {
    abstract val timestamp: Long

    /**
     * A discrete gesture boundary — down, lift, or cancel — carrying a single point.
     * Encoded as `source = MOUSE_INTERACTION`, `type` = an rrweb `MouseInteractions` value.
     */
    data class Interaction(
        val type: Int,
        val point: Point,
        override val timestamp: Long
    ) : RawTouchEvent()

    /**
     * A batch of sampled drag positions between a down and a lift. Encoded as
     * `source = TOUCH_MOVE`. [samples] is never empty — [com.mixpanel.android.sessionreplay.tracking.TouchEventRecorder]
     * is the only producer and drops empty batches — and is ordered oldest to newest, so
     * the batch's timestamp is that of its final sample.
     */
    data class Move(
        val samples: List<TouchSample>
    ) : RawTouchEvent() {
        override val timestamp: Long get() = samples.last().timestamp
    }
}

/**
 * [timestamp] is when the frame's pixels were captured, not when this event was published.
 * The encoder stamps the rrweb event with it so a screen reports when it was on screen —
 * compression and the event serial queue both sit between capture and publish, and touches
 * are timestamped accurately at the source, so a publish-time stamp mis-orders the two.
 */
data class RawScreenshotEvent(
    val data: ByteArray,
    val isInitial: Boolean,
    val timestamp: Long
)
