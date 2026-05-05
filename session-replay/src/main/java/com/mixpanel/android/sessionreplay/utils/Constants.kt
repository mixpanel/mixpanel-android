package com.mixpanel.android.sessionreplay.utils

import com.mixpanel.android.sessionreplay.logging.Logger

object APIConstants {
    const val MAX_BATCH_SIZE = 50
    const val FLUSH_SIZE = 1000
    const val MIN_RETRY_BACKOFF = 60.0
    const val MAX_RETRY_BACKOFF = 600.0
    const val FAILURES_TILL_BACKOFF = 2
    private const val LIB_VERSION = "1.2.0"
    private const val MP_LIB = "android-sr"

    // Override mechanism for library version and mp_lib
    @Volatile
    private var overriddenLibVersion: String? = null

    @Volatile
    private var overriddenMpLib: String? = null

    @JvmStatic
    val currentLibVersion: String
        get() = overriddenLibVersion ?: LIB_VERSION

    @JvmStatic
    fun setLibVersion(version: String) {
        if (version.isBlank()) {
            Logger.warn("Blank lib version provided, ignoring.")
            return
        }
        overriddenLibVersion = version
    }

    @JvmStatic
    val currentMpLib: String
        get() = overriddenMpLib ?: MP_LIB

    @JvmStatic
    fun setMpLib(lib: String) {
        if (lib.isBlank()) {
            Logger.warn("Blank mp_lib provided, ignoring.")
            return
        }
        overriddenMpLib = lib
    }
}

object BundleConstants {
    const val ID = "com.mixpanel.Mixpanel" // Assuming the package ID remains the same in Android
}

// Device specific checks can be done in Kotlin
// using methods like Build.MODEL, Build.MANUFACTURER etc.

object EventType {
    const val LOAD = 1
    const val FULL_SNAPSHOT = 2
    const val INCREMENTAL_SNAPSHOT = 3
    const val META = 4
    const val CUSTOM = 5
    const val PLUGIN = 6
}

object IncrementalSource {
    const val MUTATION = 0
    const val TOUCH_MOVE = 1
    const val TOUCH_INTERACTION = 2
}

object TouchInteraction {
    const val START = 7
    const val SWIPE_THRESHOLD = 10.0
}

object PayloadObjectId {
    const val MAIN_SNAPSHOT = 28
}

/**
 * Server URLs for different data residency regions.
 * Use these constants when configuring [com.mixpanel.android.sessionreplay.models.MPSessionReplayConfig.serverUrl].
 */
object DataResidency {
    /** Base URL for US data residency (default). */
    const val US = "https://api.mixpanel.com"

    /** Base URL for EU data residency. */
    const val EU = "https://api-eu.mixpanel.com"

    /** Base URL for India data residency. */
    const val IN = "https://api-in.mixpanel.com"
}

/**
 * Internal endpoint URL builders.
 */
internal object EndPoints {
    const val DEFAULT_BASE_URL = DataResidency.US

    fun record(baseUrl: String): String = "${baseUrl.trimEnd('/')}/record"

    fun settings(baseUrl: String): String = "${baseUrl.trimEnd('/')}/settings"
}

object TimingAdjustment {
    const val TOUCH_INTERACTION = -800
}

object ReplaySettings {
    const val TOUCH_INTERACTION_TIMING_ADJUSTMENT = -800
    const val TOUCH_EVENT_DEBOUNCE_TIME = 0.3 // seconds
    const val SNAPSHOT_TIMER_INTERVAL = 1.0 // seconds
    const val FLUSH_INTERVAL = 10L // seconds
    const val QUEUE_BATCH_SIZE = 10
    const val QUEUE_SIZE_LIMIT = 1000
}

object GzipSettings {
    const val GZIP_HEADER_OFFSET = 16
}

object ImageSettings {
    const val JPEG_COMPRESSION_RATE = 0.4
}

object NetworkError {
    const val DOMAIN = "com.mixpanel.sessionreplay"
    const val INVALID_REQUEST_CODE = 1001
    const val INVALID_RESPONSE_CODE = 1002
}

object Compose {
    const val MP_REPLAY_SENSITIVE = "mp-replay-sensitive"
}
