package com.mixpanel.android.autocapture

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import com.mixpanel.android.util.MPLog
import curtains.OnTouchEventListener
import curtains.phoneWindow
import curtains.touchEventInterceptors

/**
 * Listener interface for processed tap events.
 */
internal fun interface TapListener {
    /**
     * Called when a valid tap is detected (single-pointer ACTION_UP within touch slop
     * and duration threshold).
     *
     * @param x         Screen X coordinate.
     * @param y         Screen Y coordinate.
     * @param decorView The window's decor view.
     */
    fun onTap(x: Float, y: Float, decorView: View)
}

/**
 * Bridge between Curtains' Kotlin extensions and the Java-based autocapture code.
 *
 * Provides window resolution via [phoneWindow] and touch interception via
 * [touchEventInterceptors], replacing custom reflection and Window.Callback wrapping.
 */
internal object CurtainsHelper {

    private const val TAG = "MP.CurtainsHelper"

    /** Maximum duration (ms) for a touch to be considered a tap, not a long press. */
    private const val MAX_TAP_DURATION_MS = 800L

    /**
     * Gets the [Window] associated with a root [View] using Curtains' phoneWindow extension.
     * Returns null for views not attached to a PhoneWindow (e.g., PopupWindow, Toast).
     */
    @JvmStatic
    fun getWindow(view: View): Window? {
        return try {
            view.phoneWindow
        } catch (e: Exception) {
            MPLog.d(TAG, "Could not get window from view", e)
            null
        }
    }

    /**
     * Installs a tap listener on a [Window] using Curtains' touchEventInterceptors.
     *
     * The listener filters for taps (single-pointer ACTION_UP within touch slop
     * and duration threshold) and notifies [onTap] with screen coordinates
     * and the window's decor view.
     *
     * @return The installed [OnTouchEventListener], to be kept for later removal.
     */
    @JvmStatic
    fun installTouchListener(window: Window, onTap: TapListener): OnTouchEventListener {
        val context = window.context
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val touchSlopSq = touchSlop * touchSlop

        var downX = 0f
        var downY = 0f
        var downTime = 0L

        val listener = OnTouchEventListener { event ->
            try {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        downTime = event.eventTime
                    }
                    MotionEvent.ACTION_UP -> {
                        if (event.pointerCount == 1) {
                            val duration = event.eventTime - downTime
                            val dx = event.rawX - downX
                            val dy = event.rawY - downY
                            if (downTime > 0 && duration <= MAX_TAP_DURATION_MS
                                && (dx * dx + dy * dy) <= touchSlopSq
                            ) {
                                val decorView = window.decorView
                                onTap.onTap(event.rawX, event.rawY, decorView)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                MPLog.e(TAG, "Error in touch listener", e)
            }
        }

        window.touchEventInterceptors += listener
        return listener
    }

    /**
     * Removes a previously installed touch listener from a [Window].
     */
    @JvmStatic
    fun removeTouchListener(window: Window, listener: OnTouchEventListener) {
        try {
            window.touchEventInterceptors -= listener
        } catch (e: Exception) {
            MPLog.d(TAG, "Error removing touch listener", e)
        }
    }
}
