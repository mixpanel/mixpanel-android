package com.mixpanel.android.sessionreplay.tracking

import android.content.Context
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import com.mixpanel.android.sessionreplay.debug.DebugMaskOverlayView
import com.mixpanel.android.sessionreplay.models.RawTouchEvent
import curtains.Curtains
import kotlin.math.abs

interface TouchEventListener {
    fun onTouchStart()

    fun onTouchEnd()
}

class TouchEventRecorder(
    private val context: Context,
    private val touchEventListener: TouchEventListener,
    internal val windowOffsetProvider: () -> IntArray = {
        val location = IntArray(2)
        Curtains.rootViews.firstOrNull { it !is DebugMaskOverlayView }?.getLocationOnScreen(location)
        location
    }
) {
    private val gestureDetector: GestureDetector
    private val handler = Handler(Looper.getMainLooper())

    // Density for scaling coordinates to logical pixels (1x scale)
    // Falls back to 1f if density is invalid to prevent division by zero
    private val density: Float = context.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f

    private var initialTouchPoint: Point? = null

    // Variables to track scrolling state
    private var isScrolling = false
    private var lastX: Float = 0f
    private var lastY: Float = 0f

    private val endOfScrollRunnable = Runnable {
        if (isScrolling) {
            isScrolling = false

            initialTouchPoint?.let { startPoint ->
                val endPoint = scalePoint(lastX, lastY)
                val swipeEvent = RawTouchEvent(
                    start = startPoint,
                    end = endPoint,
                    isSwipe = true,
                    direction = detectSwipeDirection(from = startPoint, to = endPoint)
                )
                EventPublisher.shared.publishTouchEvent(swipeEvent)
            }
            touchEventListener.onTouchEnd()
        }
    }

    init {
        gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val point = scalePoint(e.rawX, e.rawY)
                    val clickEvent = RawTouchEvent(
                        start = point,
                        end = point,
                        isSwipe = false,
                        direction = null
                    )
                    EventPublisher.shared.publishTouchEvent(clickEvent)
                    touchEventListener.onTouchEnd()
                    return true
                }

                override fun onDown(e: MotionEvent): Boolean {
                    initialTouchPoint = scalePoint(e.rawX, e.rawY)
                    touchEventListener.onTouchStart()
                    return true
                }

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean {
                    if (!isScrolling) {
                        isScrolling = true
                    }

                    lastX = e2.rawX
                    lastY = e2.rawY

                    handler.removeCallbacks(endOfScrollRunnable)
                    handler.postDelayed(endOfScrollRunnable, 200) // 200ms delay before considering scroll ended
                    return true
                }
            }
        )
    }

    fun onTouchEvent(event: MotionEvent): Boolean = gestureDetector.onTouchEvent(event)

    private fun detectSwipeDirection(
        from: Point,
        to: Point
    ): String {
        val deltaX = to.x - from.x
        val deltaY = to.y - from.y

        return if (abs(deltaX) > abs(deltaY)) {
            if (deltaX > 0) "right" else "left"
        } else if (abs(deltaY) > abs(deltaX)) {
            if (deltaY > 0) "down" else "up"
        } else {
            "right" // Default to right if no clear direction
        }
    }

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
