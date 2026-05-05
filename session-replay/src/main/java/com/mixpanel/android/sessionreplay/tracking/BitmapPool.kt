package com.mixpanel.android.sessionreplay.tracking

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.mixpanel.android.sessionreplay.logging.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A thread-safe pool of reusable [Bitmap] objects to reduce allocation overhead
 * during screen recording.
 *
 * The pool maintains a maximum number of bitmaps based on device memory availability:
 * - Standard devices: 3 bitmaps
 * - Low-RAM devices: 2 bitmaps
 *
 * This helps prevent excessive memory usage while still providing performance benefits
 * from bitmap reuse.
 *
 * **Thread Safety:** All methods are thread-safe and can be called from any thread.
 */
class BitmapPool(
    context: Context
) {
    private val pool = ArrayDeque<Bitmap>()
    private val maxPoolSize: Int
    private val lock = ReentrantLock()

    init {
        maxPoolSize = determinePoolSize(context)
        Logger.info("BitmapPool initialized with max size: $maxPoolSize")
    }

    /**
     * Determines the maximum pool size based on device memory.
     *
     * Uses ActivityManager to check available RAM:
     * - Low-RAM devices or devices with < 2GB RAM: pool size = 2
     * - Standard devices: pool size = 3
     */
    private fun determinePoolSize(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am == null) {
            Logger.warn("ActivityManager not available, using default pool size")
            return DEFAULT_POOL_SIZE
        }

        val isLowRam = am.isLowRamDevice
        val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)

        val poolSize = if (isLowRam || totalGB < 2.0) LOW_RAM_POOL_SIZE else DEFAULT_POOL_SIZE
        Logger.info("Device RAM: %.2fGB, lowRam=$isLowRam, poolSize=$poolSize".format(totalGB))
        return poolSize
    }

    /**
     * Acquires a [Bitmap] from the pool or creates a new one if no match is found.
     *
     * Searches the pool for a bitmap matching the requested dimensions.
     * Non-matching bitmaps remain in the pool for future use.
     *
     * @param width The desired width of the bitmap
     * @param height The desired height of the bitmap
     * @return A [Bitmap] ready for use, or null if allocation fails (e.g., OutOfMemoryError)
     */
    fun acquire(
        width: Int,
        height: Int
    ): Bitmap? =
        lock.withLock {
            // Search for a matching bitmap by dimensions
            val iterator = pool.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (candidate.isRecycled || !candidate.isMutable) {
                    iterator.remove()
                    continue
                }
                if (candidate.width == width && candidate.height == height) {
                    iterator.remove()
                    clearBitmap(candidate)
                    Logger.debug("Reusing bitmap from pool (size=${pool.size})")
                    return candidate
                }
            }

            // No match found — evict one non-matching bitmap if pool is full,
            // so the newly created bitmap can be pooled on release.
            if (pool.size >= maxPoolSize) {
                val evicted = pool.removeFirst()
                evicted.recycle()
                Logger.debug("Evicted stale bitmap to make room (${evicted.width}x${evicted.height})")
            }

            return try {
                Logger.debug("Creating new bitmap ($width x $height)")
                createBitmap(width, height, Bitmap.Config.RGB_565)
            } catch (e: OutOfMemoryError) {
                Logger.warn("OOM on bitmap alloc, clearing pool and retrying: ${e.message}")
                clear()
                try {
                    createBitmap(width, height, Bitmap.Config.RGB_565)
                } catch (e2: OutOfMemoryError) {
                    Logger.warn("Failed again after clear: ${e2.message}")
                    null
                }
            }
        }

    /**
     * Releases a [Bitmap] back to the pool for reuse.
     *
     * If the pool is at maximum capacity, the bitmap will be recycled instead.
     *
     * @param bitmap The bitmap to release
     */
    fun release(bitmap: Bitmap?) =
        lock.withLock {
            if (bitmap == null || bitmap.isRecycled) return

            if (pool.size >= maxPoolSize) {
                bitmap.recycle()
                return
            }

            pool.addLast(bitmap)
            Logger.debug("Bitmap released to pool (size=${pool.size})")
        }

    /**
     * Clears the pool, recycling all pooled bitmaps.
     *
     * This should be called when screen recording stops to free memory.
     */
    fun clear() =
        lock.withLock {
            Logger.info("Clearing bitmap pool (${pool.size})")
            pool.forEach { it.recycle() }
            pool.clear()
        }

    private fun clearBitmap(bitmap: Bitmap) =
        try {
            bitmap.eraseColor(0)
        } catch (e: Exception) {
            Logger.warn("Failed to clear bitmap: ${e.message}")
        }

    companion object {
        private const val DEFAULT_POOL_SIZE = 3
        private const val LOW_RAM_POOL_SIZE = 2
    }
}
