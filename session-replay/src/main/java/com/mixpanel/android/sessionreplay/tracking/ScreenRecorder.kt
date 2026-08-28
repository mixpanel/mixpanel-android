package com.mixpanel.android.sessionreplay.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.View
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.mixpanel.android.sessionreplay.logging.Logger
import com.mixpanel.android.sessionreplay.sensitive_views.SensitiveViewManager
import com.mixpanel.android.sessionreplay.wireframe.WireframeElement
import com.mixpanel.android.sessionreplay.wireframe.WireframeEmitter
import curtains.phoneWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

internal class ScreenRecorder {
    companion object {
        val shared = ScreenRecorder()

        // Lazy-init pixel copy thread to avoid starting if never used
        private val pixelCopyThread by lazy {
            HandlerThread("PixelCopyHelper").apply { start() }
        }

        private val pixelCopyHandler by lazy {
            Handler(pixelCopyThread.looper)
        }
    }

    /**
     * Data class representing bitmap dimensions and scale for 1x density capture.
     * @param width The width of the bitmap in logical pixels
     * @param height The height of the bitmap in logical pixels
     * @param scale The scale factor to apply to the canvas (1/density)
     */
    private data class BitmapScale(
        val width: Int,
        val height: Int,
        val scale: Float
    )

    /**
     * Data class holding a bitmap along with its scale information.
     * @param bitmap The created bitmap
     * @param scale The scale information used to create the bitmap
     */
    private data class BitmapWithScale(
        val bitmap: Bitmap,
        val scale: BitmapScale
    )

    // Bitmap pool for reusing bitmaps across screenshot captures
    // Initialized lazily when first screenshot is captured
    private var bitmapPool: BitmapPool? = null

    // When non-null, every successful capture emits a structural wireframe as an rrweb
    // Custom event via the existing replay event stream. Installed by
    // [MPSessionReplayInstance] when [MPSessionReplayConfig.wireframesOptions] is non-null.
    @Volatile
    var wireframeEmitter: WireframeEmitter? = null

    // Flag to track if content may have changed since last screenshot
    // Used to potentially skip revalidation when content is static
    @Volatile
    var contentMayHaveChanged: Boolean = true

    /**
     * Initializes the bitmap pool if not already initialized.
     * Should be called with a valid context before capturing screenshots.
     * @return The initialized [BitmapPool] instance
     */
    @Synchronized
    private fun acquireBitmapPool(context: Context): BitmapPool = bitmapPool ?: BitmapPool(context).also { bitmapPool = it }

    /**
     * Calculates the bitmap dimensions and scale factor for capturing at 1x density.
     * If density is invalid or would result in invalid dimensions, returns original view dimensions.
     */
    private fun calculateBitmapScale(view: View): BitmapScale {
        val density = view.context.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        val scaledWidth = (view.width / density).toInt()
        val scaledHeight = (view.height / density).toInt()

        // Validate scaled dimensions
        return if (scaledWidth <= 0 || scaledHeight <= 0) {
            Logger.warn(
                "Invalid scaled dimensions: scaledWidth=$scaledWidth, scaledHeight=$scaledHeight (view: ${view.width}x${view.height}, " +
                    "density=$density), using original dimensions"
            )
            // Use original view dimensions without scaling
            BitmapScale(view.width, view.height, 1f)
        } else {
            BitmapScale(scaledWidth, scaledHeight, 1f / density)
        }
    }

    // Safe method to create a bitmap snapshot from a view at 1x scale
    // Returns both the bitmap and scale information to avoid redundant calculations
    private suspend fun createBitmapFromView(
        view: View,
        pool: BitmapPool
    ): BitmapWithScale? {
        if (view.width <= 0 || view.height <= 0) {
            Logger.warn("Invalid view dimensions: ${view.width}x${view.height}")
            return null
        }

        if (!view.isAttachedToWindow) {
            Logger.warn("View is not attached to window — cannot capture")
            return null
        }

        // Calculate bitmap dimensions and scale factor for 1x density capture
        val bitmapScale = calculateBitmapScale(view)

        // Use PixelCopy on API 26+ to capture the window surface directly.
        // This handles hardware bitmaps, RenderEffects, and GPU effects correctly.
        // Software canvas fallback is only used on older APIs or if PixelCopy fails.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            captureUsingPixelCopy(view, bitmapScale, pool)
        } else {
            captureUsingSoftwareCanvas(view, bitmapScale, pool)
        }
    }

    /**
     * A compressed frame and the wall-clock instant its pixels were read off the surface.
     *
     * The timestamp travels with the bytes so the screenshot event reports when the frame was
     * on screen rather than when it reached the event queue — compression and queueing add an
     * unbounded, load-dependent lag, and touches are timestamped accurately at the source
     * (`MotionEvent.eventTime`), so a late stamp mis-orders a screen against the tap that
     * produced it.
     */
    data class CapturedScreenshot(
        val data: ByteArray,
        val capturedAtMs: Long
    ) {
        // ByteArray identity: data class equals() would compare the array by reference.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CapturedScreenshot) return false
            return capturedAtMs == other.capturedAtMs && data.contentEquals(other.data)
        }

        override fun hashCode(): Int = 31 * data.contentHashCode() + capturedAtMs.hashCode()
    }

    /**
     * Info needed to composite a sub-window onto a full-screen bitmap.
     * Captured on the main thread (view access), consumed on a background thread.
     */
    @VisibleForTesting
    internal data class SubWindowInfo(
        val screenX: Float,
        val screenY: Float,
        val fullWidth: Int,
        val fullHeight: Int,
        // Raw-device-pixel counterparts of the four values above, for the wireframe path.
        // The bitmap fields are pre-scaled to 1x logical px because the composite draws
        // straight onto the scaled bitmap; wireframe bounds arrive raw and are scaled by
        // density later in WireframeEmitter, so they need the offset in raw px.
        val offsetXPx: Int = 0,
        val offsetYPx: Int = 0,
        val fullWidthPx: Int = 0,
        val fullHeightPx: Int = 0
    )

    /**
     * Checks if the captured view is a sub-window and returns the info needed
     * for compositing. Must be called on the main thread (accesses view properties).
     */
    @VisibleForTesting
    internal fun getSubWindowInfo(rootView: View, fullScreenView: View?): SubWindowInfo? {
        if (fullScreenView == null || fullScreenView === rootView) return null

        val fullScale = calculateBitmapScale(fullScreenView)
        val viewScale = calculateBitmapScale(rootView)

        if (fullScale.width <= viewScale.width && fullScale.height <= viewScale.height) return null

        // Compute position relative to main window (not absolute screen position)
        // This accounts for device notches/cutouts that offset the main window
        val mainWindowLocation = IntArray(2)
        fullScreenView.getLocationOnScreen(mainWindowLocation)

        val subWindowLocation = IntArray(2)
        rootView.getLocationOnScreen(subWindowLocation)

        val offsetXPx = subWindowLocation[0] - mainWindowLocation[0]
        val offsetYPx = subWindowLocation[1] - mainWindowLocation[1]

        return SubWindowInfo(
            screenX = offsetXPx * fullScale.scale,
            screenY = offsetYPx * fullScale.scale,
            fullWidth = fullScale.width,
            fullHeight = fullScale.height,
            offsetXPx = offsetXPx,
            offsetYPx = offsetYPx,
            fullWidthPx = fullScreenView.width,
            fullHeightPx = fullScreenView.height
        )
    }

    /**
     * Composites a captured sub-window bitmap onto a full-screen bitmap
     * at its screen position, with a black background.
     * Can be called from any thread (no view access).
     */
    private fun compositeOntoFullScreen(
        windowBitmap: Bitmap,
        info: SubWindowInfo,
        pool: BitmapPool
    ): Bitmap? {
        val fullBitmap = pool.acquire(info.fullWidth, info.fullHeight)
        if (fullBitmap == null) {
            pool.release(windowBitmap)
            return null
        }

        try {
            // Draw the captured window content onto the full-screen bitmap
            // (pool clears the bitmap to black, so the background is already set)
            val canvas = Canvas(fullBitmap)
            canvas.drawBitmap(windowBitmap, info.screenX, info.screenY, null)

            return fullBitmap
        } catch (e: Exception) {
            Logger.warn("Failed to composite sub-window: [${e.javaClass.simpleName}] ${e.message}")
            pool.release(fullBitmap)
            return null
        } finally {
            pool.release(windowBitmap)
        }
    }

    /**
     * Captures the view using software canvas drawing.
     * Acquires a bitmap from the pool and releases it on failure.
     * @return BitmapWithScale on success, null on failure.
     */
    private fun captureUsingSoftwareCanvas(
        view: View,
        bitmapScale: BitmapScale,
        pool: BitmapPool
    ): BitmapWithScale? {
        val bitmap = pool.acquire(bitmapScale.width, bitmapScale.height) ?: return null

        return try {
            val canvas = Canvas(bitmap)
            // Scale the canvas down to fit the view into the smaller bitmap
            canvas.scale(bitmapScale.scale, bitmapScale.scale)
            view.draw(canvas)
            BitmapWithScale(bitmap, bitmapScale)
        } catch (e: Exception) {
            Logger.warn("Software capture failed: [${e.javaClass.simpleName}] ${e.message}")
            pool.release(bitmap)
            null
        }
    }

    /**
     * Captures the view's window content using PixelCopy.
     * Acquires a bitmap from the pool and releases it on failure.
     * @return BitmapWithScale on success, null on failure.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun captureUsingPixelCopy(
        view: View,
        bitmapScale: BitmapScale,
        pool: BitmapPool
    ): BitmapWithScale? {
        val window = view.phoneWindow ?: run {
            Logger.warn("PixelCopy failed: view has no associated Window")
            return null
        }

        val bitmap = pool.acquire(bitmapScale.width, bitmapScale.height) ?: return null

        return withTimeoutOrNull(300L) {
            suspendCancellableCoroutine { continuation ->
                try {
                    PixelCopy.request(
                        window,
                        bitmap,
                        { copyResult ->
                            if (copyResult == PixelCopy.SUCCESS) {
                                continuation.resume(BitmapWithScale(bitmap, bitmapScale))
                            } else {
                                Logger.warn("PixelCopy failed with result: $copyResult")
                                continuation.resume(null)
                            }
                        },
                        pixelCopyHandler
                    )
                } catch (e: Exception) {
                    Logger.warn("PixelCopy.request threw exception: [${e.javaClass.simpleName}] ${e.message}")
                    continuation.resume(null)
                }
            }
        } ?: run {
            Logger.warn("PixelCopy failed or timed out")
            pool.release(bitmap)
            null
        }
    }

    /**
     * A wireframe gathered during the view walk, held until the screenshot it describes is
     * known to ship. See [renderViewHierarchyAsImage] for why emission is deferred.
     */
    private class PendingWireframe(
        val elements: List<WireframeElement>,
        val viewportWidthPx: Int,
        val viewportHeightPx: Int,
        val density: Float,
        val maskBounds: Set<Rect>
    )

    /**
     * A rendered frame plus the wireframe describing it, if wireframe capture is enabled.
     *
     * [capturedAtMs] is wall-clock time read the moment the pixels came off the surface —
     * the closest available proxy for when this frame was on screen. Both the screenshot
     * event and the wireframe event are stamped with it, so the two describe the same instant
     * and sort correctly against touches (which carry `MotionEvent.eventTime`, accurate at the
     * source). Stamping later — after JPEG compression, or on the event serial queue — puts an
     * unbounded, load-dependent lag between when a screen was shown and when it claims to have
     * been shown, which is enough to sort a pre-tap screen after the tap it preceded.
     */
    private class RenderedFrame(
        val bitmap: Bitmap,
        val wireframe: PendingWireframe?,
        val capturedAtMs: Long
    )

    private suspend fun renderViewHierarchyAsImage(
        view: View,
        pool: BitmapPool,
        subWindowInfo: SubWindowInfo?
    ): RenderedFrame? {
        // Reset content change state. If this value is true after creating the bitmap
        // we will need to re-validate the view content for sensitivity changes
        contentMayHaveChanged = false

        // Process subviews to detect sensitive content before capture. If wireframe capture is
        // enabled, also accumulate a structural element list during the same traversal so the
        // wireframe stays time-aligned with the screenshot without requiring a second walk.
        val emitter = wireframeEmitter
        val wireframeBuffer: MutableList<WireframeElement>? = if (emitter != null) mutableListOf() else null
        val initialSummary = SensitiveViewManager.processSubviews(view, wireframeBuffer)

        // Skip capture during screen transitions to prevent sensitive content leaks.
        // Transitioning views are rendered but not in the hierarchy for masking detection.
        if (initialSummary.hasActiveTransition) {
            Logger.info("Skipping capture during screen transition")
            return null
        }

        // Hold the wireframe rather than emitting it here. A wireframe is only meaningful
        // paired with the screenshot it describes, so it must not ship on any path that
        // discards the frame — bitmap failure below, or the bounds-changed discard, which
        // fires precisely because the mask rects this wireframe was stripped against are no
        // longer accurate. [captureScreenshot] emits it once the image is confirmed.
        //
        // Mask bounds are captured here so the emitter can geometrically strip element text
        // that overlaps any rect the screenshot is about to paint over. Density is captured
        // so emit can convert raw px bounds/viewport to 1x logical px — the same space as the
        // screenshot (captured at 1/density) and scaled touches.
        val pendingWireframe = if (emitter != null && wireframeBuffer != null) {
            // A sub-window (dialog, popup, separate-window bottom sheet) is captured on its own
            // and then composited onto a full-screen bitmap at its offset from the main window.
            // The wireframe has to describe *that* image, per the wire contract ("bounds — the
            // bounding box of the element within the screenshot image"), so the viewport becomes
            // the full screen and every element shifts by the same offset. Touch points already
            // live in this space — scalePoint subtracts the main window's origin — so all three
            // signals agree. Without the shift, a dialog's elements land at the wrong place in
            // the image and the viewport claims the dialog's size.
            val dx = subWindowInfo?.offsetXPx ?: 0
            val dy = subWindowInfo?.offsetYPx ?: 0
            PendingWireframe(
                elements = if (dx == 0 && dy == 0) {
                    wireframeBuffer
                } else {
                    wireframeBuffer.map { it.copy(x = it.x + dx, y = it.y + dy) }
                },
                viewportWidthPx = subWindowInfo?.fullWidthPx ?: view.width,
                viewportHeightPx = subWindowInfo?.fullHeightPx ?: view.height,
                density = view.context.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f,
                // Shifted to match the elements so the Layer 2 geometric strip still lines up.
                // The unshifted set stays in initialSummary for maskSensitiveViews, which paints
                // the sub-window's own bitmap before compositing.
                maskBounds = if (dx == 0 && dy == 0) {
                    initialSummary.boundsSnapshot
                } else {
                    initialSummary.boundsSnapshot.mapTo(mutableSetOf()) { rect ->
                        Rect(rect).apply { offset(dx, dy) }
                    }
                }
            )
        } else {
            null
        }

        // Create the bitmap at 1x scale (may return null if both canvas.draw and PixelCopy fail)
        val bitmapWithScale = createBitmapFromView(view, pool)
        // Stamped here, right after the pixels are read off the surface and before any
        // compression or queueing, so both events carry when the frame was on screen rather
        // than when the SDK got around to encoding it. See [RenderedFrame.capturedAtMs].
        val capturedAtMs = System.currentTimeMillis()
        val bitmap = bitmapWithScale?.bitmap
        try {
            if (bitmap == null) {
                throw IllegalStateException("createBitmapFromView returned null")
            }
            // Apply masking if needed, using the same scale transformation from bitmap creation
            if (initialSummary.needsMasking) {
                // Only revalidate if content may have changed since initial processing
                if (contentMayHaveChanged) {
                    // Re-process to get fresh bounds for comparison
                    val revalidatedSummary = SensitiveViewManager.processSubviews(view)

                    // If bounds changed, discard screenshot (views may have moved/changed)
                    if (initialSummary.boundsSnapshot != revalidatedSummary.boundsSnapshot) {
                        Logger.warn("Bounds changed during capture, discarding screenshot")
                        pool.release(bitmap)
                        return null
                    }
                }

                // Bounds validated (or no revalidation needed) - apply masking
                val canvas = Canvas(bitmap)
                canvas.scale(bitmapWithScale.scale.scale, bitmapWithScale.scale.scale)
                SensitiveViewManager.maskSensitiveViews(view, canvas, initialSummary.boundsSnapshot)
            }

            return RenderedFrame(bitmap, pendingWireframe, capturedAtMs)
        } catch (e: Exception) {
            bitmap?.let { pool.release(it) }
            Logger.warn("Failed to render view as image: ${e.message}")
            return null
        }
    }

    /**
     * Captures a screenshot of the provided [rootView] at 1x scale (logical pixels).
     *
     * This method renders the view hierarchy into a [Bitmap] at 1x density (similar to iOS scale = 1.0),
     * applies masking if necessary, compresses the image to a JPEG byte array, and then recycles
     * the [Bitmap] to free memory.
     *
     * The output bitmap is always full-screen size. If the view is a sub-window
     * (dialog, popup), it is composited onto a black background at its screen position.
     *
     * **Threading:** This function MUST be called from the main thread as it performs view operations.
     * CPU-intensive compression happens on a background thread.
     *
     * @param rootView The root [View] to capture.
     * @param fullScreenView Optional view (e.g., activity root) used to determine full-screen
     *   dimensions when capturing sub-windows (dialogs, popups). If provided and larger than
     *   rootView, the sub-window is composited onto a full-screen bitmap.
     * @return The compressed JPEG image paired with the instant it was captured, or `null` if
     *   capture fails. The caller must stamp the screenshot event with
     *   [CapturedScreenshot.capturedAtMs] rather than the time it happens to publish, so the
     *   event reports when the frame was on screen.
     */
    suspend fun captureScreenshot(rootView: View, fullScreenView: View? = null): CapturedScreenshot? {
        // Initialize bitmap pool if not already done
        val pool = acquireBitmapPool(rootView.context)

        // Gather sub-window info on main thread (view access required). Resolved before the
        // walk so the wireframe built during it can be expressed in the same coordinate space
        // as the composited screenshot.
        val subWindowInfo = getSubWindowInfo(rootView, fullScreenView)

        // Process subviews and render bitmap (must be called from main thread)
        val rendered = try {
            renderViewHierarchyAsImage(rootView, pool, subWindowInfo)
        } catch (e: Exception) {
            Logger.warn("Failed to capture screenshot: ${e.message}")
            null
        } ?: return null
        val image = rendered.bitmap

        // Compositing and compression on background thread (CPU-intensive operations)
        return withContext(Dispatchers.Default) {
            val bitmap = if (subWindowInfo != null) {
                // This method handles the recycling of the image bitmap passed into it
                compositeOntoFullScreen(image, subWindowInfo, pool)
            } else {
                image
            } ?: return@withContext null

            try {
                CapturedScreenshot(bitmap.compressToByteArray(), rendered.capturedAtMs).also {
                    // The image is now guaranteed to ship (a non-null return is always
                    // published as a screenshot event), so the wireframe describing it can
                    // go out. Both events carry the frame's capture instant, so they stay
                    // aligned no matter how long compression or the event queue takes.
                    // Failures are swallowed: a wireframe problem must never cost us the
                    // screenshot.
                    rendered.wireframe?.let { pending ->
                        try {
                            wireframeEmitter?.emit(
                                elements = pending.elements,
                                viewportWidthPx = pending.viewportWidthPx,
                                viewportHeightPx = pending.viewportHeightPx,
                                density = pending.density,
                                maskBounds = pending.maskBounds,
                                capturedAtMs = rendered.capturedAtMs
                            )
                        } catch (e: Exception) {
                            Logger.warn("Failed to emit wireframe: ${e.message}")
                        }
                    }
                    Logger.debug { "Compressed screenshot size: %.2f KB".format(it.data.size / 1024.0) }
//                     saveToLocalFilesystem(
//                         rootView.context.applicationContext,
//                         it.data,
//                         "screenshot-${System.currentTimeMillis()}.jpg"
//                     )
                }
            } catch (e: Exception) {
                Logger.warn("Failed to process screenshot: ${e.message}")
                null
            } finally {
                pool.release(bitmap)
            }
        }
    }

    private fun Bitmap.compressToByteArray(
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 80 // Adjust as needed
    ): ByteArray =
        try {
            val stream = ByteArrayOutputStream()
            compress(format, quality, stream)
            stream.toByteArray()
        } catch (e: Exception) {
            Logger.warn("Failed to compress image: ${e.message}")
            byteArrayOf() // Return an empty array on failure
        }

    private fun saveToLocalFilesystem(
        context: Context,
        imageData: ByteArray,
        filename: String
    ) {
        val file = File(context.filesDir, filename)
        FileOutputStream(file).use { out ->
            out.write(imageData)
        }
    }

    /**
     * Clears the bitmap pool and releases all pooled bitmaps.
     *
     * This should be called when screen recording stops to free memory.
     * The pool will be re-initialized automatically on the next screenshot capture.
     *
     * Thread-safe: Uses synchronization to prevent race conditions with
     * concurrent screenshot captures or pool initialization.
     */
    fun clearBitmapPool() {
        synchronized(this) {
            bitmapPool?.clear()
            bitmapPool = null
        }
        Logger.info("Bitmap pool cleared")
    }
}
