package com.mixpanel.android.sessionreplay.debug

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.View
import android.view.WindowManager
import com.mixpanel.android.sessionreplay.logging.Logger
import com.mixpanel.android.sessionreplay.sensitive_views.MaskDecision
import curtains.Curtains
import curtains.OnRootViewsChangedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Manages the debug mask overlay that displays which views are being masked.
 *
 * The overlay is rendered in a separate [TYPE_APPLICATION_PANEL][WindowManager.LayoutParams.TYPE_APPLICATION_PANEL]
 * window so that it is visible on-device for debugging but **not captured** by
 * [android.view.PixelCopy] or software-canvas screenshots (which only read the
 * activity window's surface).
 *
 * **Important**: This overlay only works in debuggable builds to prevent
 * accidental exposure in production. Use [create] to obtain an instance.
 */
internal class DebugMaskOverlayManager private constructor(
    private val colors: DebugOverlayColors
) {

    companion object {
        /**
         * Creates a DebugMaskOverlayManager if the app is debuggable.
         * Returns null for non-debuggable (release) builds to prevent accidental exposure.
         *
         * @param context Application context
         * @param colors The colors to use for different mask types
         * @return DebugMaskOverlayManager instance if debuggable, null otherwise
         */
        fun create(context: Context, colors: DebugOverlayColors): DebugMaskOverlayManager? {
            val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            return if (isDebuggable) {
                DebugMaskOverlayManager(colors)
            } else {
                Logger.warn("Debug mask overlay is disabled in release builds")
                null
            }
        }
    }

    private var isEnabled = false
    private val overlayViews = mutableMapOf<Int, WeakReference<DebugMaskOverlayView>>()
    private var currentMaskEntries: Map<Rect, MaskDecision> = emptyMap()

    private val mainScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val onRootViewsChangedListener = OnRootViewsChangedListener { view, added ->
        // Skip overlay views to prevent infinite recursion: adding an overlay via
        // WindowManager creates a new root view, which would trigger this listener again.
        if (isEnabled && view !is DebugMaskOverlayView) {
            if (added) {
                attachOverlayToView(view)
            } else {
                removeOverlayFromView(view)
            }
        }
    }

    /**
     * Enables the debug mask overlay.
     * Attaches overlay views to all existing root views.
     */
    fun enable() {
        mainScope.launch {
            if (isEnabled) return@launch
            isEnabled = true

            Logger.info("Debug mask overlay enabled")

            // Listen for root view changes
            Curtains.onRootViewsChangedListeners += onRootViewsChangedListener

            // Attach overlay to all existing root views
            Curtains.rootViews.forEach { rootView ->
                if (rootView !is DebugMaskOverlayView) {
                    attachOverlayToView(rootView)
                }
            }
        }
    }

    /**
     * Disables the debug mask overlay.
     * Removes overlay views from all root views.
     */
    fun disable() {
        mainScope.launch {
            if (!isEnabled) return@launch
            isEnabled = false

            Logger.info("Debug mask overlay disabled")

            // Stop listening for root view changes
            Curtains.onRootViewsChangedListeners -= onRootViewsChangedListener

            // Remove overlays for all tracked root views
            overlayViews.forEach { (_, weakRef) ->
                weakRef.get()?.let { overlayView ->
                    try {
                        val wm = overlayView.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                        wm.removeViewImmediate(overlayView)
                    } catch (e: Exception) {
                        Logger.warn("Failed to remove debug overlay: ${e.message}")
                    }
                }
            }

            overlayViews.clear()
            currentMaskEntries = emptyMap()
        }
    }

    /**
     * Updates the displayed mask regions on the topmost overlay view only.
     *
     * Mask rects use screen coordinates, and every overlay is full-screen
     * (dialog windows are full-screen with transparent areas around the card).
     * Sending entries to all overlays would cause duplicate masks to render
     * on every layer. Instead, only the topmost overlay draws the masks and
     * all others are cleared.
     *
     * @param entries Map of bounds to mask decision type
     */
    fun updateMaskEntries(entries: Map<Rect, MaskDecision>) {
        // Skip if entries haven't changed
        if (currentMaskEntries == entries) return
        currentMaskEntries = entries
        refreshOverlayEntries()
    }

    /**
     * Attaches a debug overlay to the given root view by creating a separate
     * [TYPE_APPLICATION_PANEL] window. This window is visually on top of the
     * parent window but has its own surface, so PixelCopy on the parent window
     * will not capture the overlay.
     */
    private fun attachOverlayToView(rootView: View) {
        val viewHash = System.identityHashCode(rootView)

        // Skip if already attached
        if (overlayViews[viewHash]?.get() != null) {
            return
        }

        val windowToken = rootView.windowToken
        if (windowToken == null) {
            // Token not available yet — retry once the view is attached
            rootView.post { if (isEnabled) attachOverlayToView(rootView) }
            return
        }

        try {
            val overlayView = DebugMaskOverlayView(rootView.context, colors, rootView)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                token = windowToken
            }

            val wm = rootView.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.addView(overlayView, params)

            // Register BEFORE updating entries so the topmost-only logic
            // in updateMaskEntries can find this overlay.
            overlayViews[viewHash] = WeakReference(overlayView)

            // Refresh which overlay is topmost and update entries accordingly
            refreshOverlayEntries()

            // Invalidate the root view so the onDrawListener fires and triggers
            // a screenshot capture. Without this, the overlay stays empty until
            // the next user-driven draw (touch/scroll), because adding a view to
            // a separate window doesn't invalidate the parent window's DecorView.
            rootView.postInvalidateOnAnimation()
        } catch (e: Exception) {
            Logger.warn("Failed to attach debug overlay: [${e.javaClass.simpleName}] ${e.message}")
        }
    }

    private fun removeOverlayFromView(rootView: View) {
        val viewHash = System.identityHashCode(rootView)
        val overlayRef = overlayViews.remove(viewHash) ?: return
        val overlayView = overlayRef.get() ?: return

        try {
            val wm = overlayView.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeViewImmediate(overlayView)
        } catch (e: Exception) {
            Logger.warn("Failed to remove debug overlay: ${e.message}")
        }

        // The topmost window may have changed, refresh which overlay shows entries
        refreshOverlayEntries()
    }

    /**
     * Sends current mask entries to the topmost overlay and clears all others.
     * Called when overlays are added/removed to keep the correct one active.
     */
    private fun refreshOverlayEntries() {
        val allRootViews = Curtains.rootViews
        val topmostRootView = allRootViews.lastOrNull { it !is DebugMaskOverlayView }
        val topmostHash = topmostRootView?.let { System.identityHashCode(it) }

        overlayViews.forEach { (viewHash, weakRef) ->
            val entries = if (viewHash == topmostHash) currentMaskEntries else emptyMap()
            weakRef.get()?.updateMaskEntries(entries)
        }
    }
}
