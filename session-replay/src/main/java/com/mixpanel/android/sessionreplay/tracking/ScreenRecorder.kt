package com.mixpanel.android.sessionreplay.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.View
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.mixpanel.android.sessionreplay.logging.Logger
import com.mixpanel.android.sessionreplay.sensitive_views.SensitiveViewManager
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
     * Info needed to composite a sub-window onto a full-screen bitmap.
     * Captured on the main thread (view access), consumed on a background thread.
     */
    @VisibleForTesting
    internal data class SubWindowInfo(
        val screenX: Float,
        val screenY: Float,
        val fullWidth: Int,
        val fullHeight: Int
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

        return SubWindowInfo(
            screenX = (subWindowLocation[0] - mainWindowLocation[0]) * fullScale.scale,
            screenY = (subWindowLocation[1] - mainWindowLocation[1]) * fullScale.scale,
            fullWidth = fullScale.width,
            fullHeight = fullScale.height
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

    private suspend fun renderViewHierarchyAsImage(
        view: View,
        pool: BitmapPool
    ): Bitmap? {
        // Reset content change state. If this value is true after creating the bitmap
        // we will need to re-validate the view content for sensitivity changes
        contentMayHaveChanged = false

        // Process subviews to detect sensitive content before capture
        val initialSummary = SensitiveViewManager.processSubviews(view)

        // Skip capture during screen transitions to prevent sensitive content leaks.
        // Transitioning views are rendered but not in the hierarchy for masking detection.
        if (initialSummary.hasActiveTransition) {
            Logger.info("Skipping capture during screen transition")
            return null
        }

        // Create the bitmap at 1x scale (may return null if both canvas.draw and PixelCopy fail)
        val bitmapWithScale = createBitmapFromView(view, pool)
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

            return bitmap
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
     * @return A [ByteArray] containing the compressed JPEG image, or `null` if capture fails.
     */
    suspend fun captureScreenshot(rootView: View, fullScreenView: View? = null): ByteArray? {
        // Initialize bitmap pool if not already done
        val pool = acquireBitmapPool(rootView.context)

        // Process subviews and render bitmap (must be called from main thread)
        val image = try {
            renderViewHierarchyAsImage(rootView, pool)
        } catch (e: Exception) {
            Logger.warn("Failed to capture screenshot: ${e.message}")
            null
        } ?: return null

        // Gather sub-window info on main thread (view access required)
        val subWindowInfo = getSubWindowInfo(rootView, fullScreenView)

        // Compositing and compression on background thread (CPU-intensive operations)
        return withContext(Dispatchers.Default) {
            val bitmap = if (subWindowInfo != null) {
                // This method handles the recycling of the image bitmap passed into it
                compositeOntoFullScreen(image, subWindowInfo, pool)
            } else {
                image
            } ?: return@withContext null

            try {
                bitmap.compressToByteArray().also {
                    Logger.debug { "Compressed screenshot size: %.2f KB".format(it.size / 1024.0) }
//                     saveToLocalFilesystem(
//                         rootView.context.applicationContext,
//                         it,
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
