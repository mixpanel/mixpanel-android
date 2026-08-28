package com.mixpanel.android.sessionreplay.wireframe

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Debug-time snapshot of a single wireframe emission. Mirrors the on-the-wire
 * [WireframePayload] shape and adds a `maskDecision` field per element so users can
 * inspect *why* each element's text was (or wasn't) transformed.
 *
 * Delivered to [com.mixpanel.android.sessionreplay.debug.DebugOptions.wireframeEmitter]
 * on a background scope, out-of-band from the wire event publish so a slow callback
 * cannot stall screenshot capture. Callbacks must be thread-safe. Never sent to Mixpanel.
 *
 * **Not a stable contract.** This shape (and the [MaskDecision] enum names it embeds)
 * is meant for interactive debugging, not machine consumption. Enum values may be
 * added or renamed in future SDK versions; do not build tooling that treats [toJson]
 * output as a versioned schema.
 */
@Serializable
data class WireframeDebugSnapshot(
    val timestamp: Long,
    val viewport: List<Int>,
    val elements: List<DebugElement>
) {
    @Serializable
    data class DebugElement(
        val role: String,
        val text: String?,
        val bounds: List<Int>,
        val maskDecision: MaskDecision
    )

    /** Serializes this snapshot to a JSON string. */
    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json { encodeDefaults = true }
    }
}
