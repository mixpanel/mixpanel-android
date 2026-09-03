package com.mixpanel.android.sessionreplay.tracking

import android.content.Context
import android.graphics.Point
import android.os.SystemClock
import android.view.MotionEvent
import com.mixpanel.android.sessionreplay.debug.DebugMaskOverlayView
import com.mixpanel.android.sessionreplay.models.RawTouchEvent
import com.mixpanel.android.sessionreplay.models.TouchSample
import com.mixpanel.android.sessionreplay.utils.MouseInteraction
import com.mixpanel.android.sessionreplay.utils.TouchSampling
import curtains.Curtains

interface TouchEventListener {
    fun onTouchStart()

    fun onTouchEnd()
}

/**
 * Translates the window's raw `MotionEvent` stream into rrweb touch events.
 *
 * A gesture becomes `TOUCH_START` → zero or more `TOUCH_MOVE` position batches →
 * `TOUCH_END` (or `TOUCH_CANCEL`). Only the primary pointer is tracked, matching
 * rrweb-web; secondary pointers going down or up mid-gesture are ignored.
 *
 * Nothing here is deferred: batches drain on the next sampled move or when the gesture
 * ends, so no position is held behind a timer and every event carries the `eventTime` of
 * the `MotionEvent` that produced it.
 */
class TouchEventRecorder(
    context: Context,
    private val touchEventListener: TouchEventListener,
    internal val windowOffsetProvider: () -> IntArray = {
        val location = IntArray(2)
        Curtains.rootViews.firstOrNull { it !is DebugMaskOverlayView }?.getLocationOnScreen(location)
        location
    },
    /**
     * Converts the uptime base of `MotionEvent.eventTime` to wall clock. Read per event so
     * a device sleeping mid-session can't skew later gestures.
     */
    private val epochOffsetProvider: () -> Long = {
        System.currentTimeMillis() - SystemClock.uptimeMillis()
    }
) {
    // Density for scaling coordinates to logical pixels (1x scale)
    // Falls back to 1f if density is invalid to prevent division by zero
    private val density: Float = context.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f

    private val pendingSamples = mutableListOf<TouchSample>()
    private var isTracking = false
    private var lastSampledEventTime = 0L

    fun onTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onGestureStart(event)
            MotionEvent.ACTION_MOVE -> onGestureMove(event)
            MotionEvent.ACTION_UP -> onGestureEnd(event, MouseInteraction.TOUCH_END)
            MotionEvent.ACTION_CANCEL -> onGestureEnd(event, MouseInteraction.TOUCH_CANCEL)
        }
    }

    private fun onGestureStart(event: MotionEvent) {
        resetGesture()
        isTracking = true
        lastSampledEventTime = event.eventTime
        publishInteraction(MouseInteraction.TOUCH_START, event)
        touchEventListener.onTouchStart()
    }

    private fun onGestureMove(event: MotionEvent) {
        // A move without a preceding down means recording started mid-gesture; wait for the
        // next clean gesture rather than emitting a path with no start.
        if (!isTracking) return
        if (event.eventTime - lastSampledEventTime < TouchSampling.MOVE_SAMPLE_INTERVAL_MS) return

        lastSampledEventTime = event.eventTime
        pendingSamples += TouchSample(
            point = scalePoint(event.rawX, event.rawY),
            timestamp = toWallClock(event.eventTime)
        )

        val batchSpan = pendingSamples.last().timestamp - pendingSamples.first().timestamp
        if (batchSpan >= TouchSampling.MOVE_BATCH_INTERVAL_MS ||
            pendingSamples.size >= TouchSampling.MAX_POSITIONS_PER_BATCH
        ) {
            flushSamples()
        }
    }

    private fun onGestureEnd(event: MotionEvent, interactionType: Int) {
        if (!isTracking) return

        // Drain the path before the boundary event so the stream stays chronological.
        flushSamples()
        publishInteraction(interactionType, event)
        resetGesture()
        touchEventListener.onTouchEnd()
    }

    private fun flushSamples() {
        if (pendingSamples.isEmpty()) return
        EventPublisher.shared.publishTouchEvent(RawTouchEvent.Move(pendingSamples.toList()))
        pendingSamples.clear()
    }

    private fun resetGesture() {
        pendingSamples.clear()
        isTracking = false
        lastSampledEventTime = 0L
    }

    private fun publishInteraction(interactionType: Int, event: MotionEvent) {
        EventPublisher.shared.publishTouchEvent(
            RawTouchEvent.Interaction(
                type = interactionType,
                point = scalePoint(event.rawX, event.rawY),
                timestamp = toWallClock(event.eventTime)
            )
        )
    }

    private fun toWallClock(eventTime: Long): Long = eventTime + epochOffsetProvider()

    /**
     * Converts raw screen coordinates to logical coordinates relative to the screenshot.
     * Subtracts window offset (for notch/cutout) and scales by density to match 1x screenshot scale.
     */
    private fun scalePoint(rawX: Float, rawY: Float): Point {
        val offset = windowOffsetProvider()
        return Point(
            ((rawX - offset[0]) / density).toInt(),
            ((rawY - offset[1]) / density).toInt()
        )
    }
}
