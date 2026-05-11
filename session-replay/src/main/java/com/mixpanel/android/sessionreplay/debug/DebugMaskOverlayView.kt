package com.mixpanel.android.sessionreplay.debug

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import com.mixpanel.android.sessionreplay.sensitive_views.MaskDecision

/**
 * Debug overlay view that displays mask regions as colored rectangles.
 *
 * All masks are drawn with solid colors onto a single layer, then the
 * entire layer is rendered with transparency. This avoids stacking
 * artifacts when mask regions overlap.
 *
 * Draw order (lowest to highest priority):
 * - Unmask regions (addSafeView): Green by default
 * - Auto masks (text, images, web views): Orange by default
 * - Mask/Text entry (explicitly sensitive, input fields): Red by default
 *
 * Higher-priority colors paint over lower-priority ones.
 */
@SuppressLint("ViewConstructor")
internal class DebugMaskOverlayView(
    context: Context,
    colors: DebugOverlayColors,
    private val parentRootView: View
) : View(context) {

    private var maskEntries: Map<Rect, MaskDecision> = emptyMap()

    // Reusable arrays for getting view locations on screen
    private val overlayScreenLocation = IntArray(2)
    private val parentScreenLocation = IntArray(2)

    // Solid fill paints — transparency is applied at the layer level
    private val maskPaint = colors.maskColor?.let { createSolidPaint(it) }
    private val autoMaskPaint = colors.autoMaskColor?.let { createSolidPaint(it) }
    private val unmaskPaint = colors.unmaskColor?.let { createSolidPaint(it) }

    private val layerAlpha = (colors.alpha.coerceIn(0f, 1f) * 255).toInt()

    init {
        // Make this view non-interactive
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun createSolidPaint(color: Int): Paint = Paint().apply {
        this.color = color
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    /**
     * Updates the mask entries to display.
     * @param entries Map of bounds to mask decision type
     */
    fun updateMaskEntries(entries: Map<Rect, MaskDecision>) {
        if (maskEntries != entries) {
            maskEntries = entries
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            invalidate()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // Always return false to let touch events pass through
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawMaskRegions(canvas)
    }

    private fun drawMaskRegions(canvas: Canvas) {
        // Mask rects use getGlobalVisibleRect which returns coordinates relative
        // to the parent window's DecorView. The overlay panel window may not start
        // at the same origin (e.g. it may be inset below the status bar). Compute
        // the delta between this overlay's screen position and the parent root
        // view's screen position to correctly translate mask coordinates into the
        // overlay's canvas coordinate space.
        getLocationOnScreen(overlayScreenLocation)
        parentRootView.getLocationOnScreen(parentScreenLocation)
        val offsetX = overlayScreenLocation[0] - parentScreenLocation[0]
        val offsetY = overlayScreenLocation[1] - parentScreenLocation[1]

        if (maskEntries.isEmpty()) return

        // Draw all masks with solid colors on a single layer, then apply
        // transparency to the whole layer. This prevents stacking artifacts
        // when regions overlap. Draw order is lowest to highest priority
        // so higher-priority colors paint over lower-priority ones.
        val count = canvas.saveLayerAlpha(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            layerAlpha
        )

        // Draw sorted by priority (lowest first) so higher-priority colors
        // paint over lower-priority ones in visually overlapping regions.
        maskEntries.entries.sortedBy { it.value.ordinal }.forEach { (rect, type) ->
            val paint = when (type) {
                MaskDecision.UNMASK -> unmaskPaint
                MaskDecision.AUTO -> autoMaskPaint
                MaskDecision.MASK, MaskDecision.TEXT_ENTRY -> maskPaint
                MaskDecision.NONE -> null
            } ?: return@forEach

            canvas.drawRect(
                (rect.left - offsetX).toFloat(),
                (rect.top - offsetY).toFloat(),
                (rect.right - offsetX).toFloat(),
                (rect.bottom - offsetY).toFloat(),
                paint
            )
        }

        canvas.restoreToCount(count)
    }
}
