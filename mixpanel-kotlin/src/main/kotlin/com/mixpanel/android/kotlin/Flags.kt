package com.mixpanel.android.kotlin

import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.android.mpmetrics.MixpanelFlagVariant
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Kotlin-friendly wrapper for [MixpanelAPI.Flags].
 *
 * Provides coroutine support for feature flag access.
 *
 * Example:
 * ```kotlin
 * lifecycleScope.launch {
 *     val isEnabled = mixpanel.flags.isEnabled(name = "new_feature")
 *     val variant = mixpanel.flags.getVariant(name = "theme")
 *     val theme = variant.value as? String ?: "light"
 * }
 * ```
 */
class Flags internal constructor(
    private val flags: MixpanelAPI.Flags,
) {
    /**
     * Access to the underlying [MixpanelAPI.Flags] for advanced use cases.
     */
    val java: MixpanelAPI.Flags get() = flags

    /**
     * Checks if a feature flag is enabled.
     *
     * Example:
     * ```kotlin
     * lifecycleScope.launch {
     *     val isEnabled = mixpanel.flags.isEnabled(name = "new_feature")
     *     if (isEnabled) { showNewFeature() }
     * }
     * ```
     *
     * @param name The feature flag name
     * @param fallback The fallback value if not found
     * @return True if enabled, false otherwise
     */
    suspend fun isEnabled(
        name: String,
        fallback: Boolean = false,
    ): Boolean =
        suspendCancellableCoroutine { continuation ->
            flags.isEnabled(
                name,
                fallback,
            ) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }

    /**
     * Gets the full variant for a feature flag.
     *
     * Example:
     * ```kotlin
     * lifecycleScope.launch {
     *     val variant = mixpanel.flags.getVariant(name = "theme")
     *     val theme = variant.value as? String ?: "light"
     * }
     * ```
     *
     * @param name The feature flag name
     * @param fallback The fallback variant if not found
     * @return The variant
     */
    suspend fun getVariant(
        name: String,
        fallback: MixpanelFlagVariant = MixpanelFlagVariant(""),
    ): MixpanelFlagVariant =
        suspendCancellableCoroutine { continuation ->
            flags.getVariant(
                name,
                fallback,
            ) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }

    /**
     * Ensures flags are loaded before proceeding.
     *
     * Example:
     * ```kotlin
     * lifecycleScope.launch {
     *     mixpanel.flags.ensureLoaded()
     *     // Flags are now loaded
     * }
     * ```
     */
    suspend fun ensureLoaded() {
        if (!areReady) {
            load()
            isEnabled(name = "__ensure_loaded__", fallback = false)
        }
    }

    /**
     * Returns whether flags have been loaded.
     */
    val areReady: Boolean
        get() = flags.areFlagsReady()

    /**
     * Triggers a flags load/refresh.
     */
    fun load() {
        flags.loadFlags()
    }
}
