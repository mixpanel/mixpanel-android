package com.mixpanel.android.sessionreplay.debug

import com.mixpanel.android.sessionreplay.wireframe.WireframeDebugSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Configuration for debug features in Session Replay.
 *
 * @property overlayColors When not null, enables a visual overlay showing which views
 *   are being masked. Only works in debuggable builds.
 * @property emitWireframes Whether to build a debug snapshot of each captured wireframe.
 *   Serializable, and therefore the switch a cross-platform host can set: React Native's whole
 *   configuration crosses the bridge as JSON, so it cannot pass [wireframeEmitter] and instead
 *   sets this, leaving its bridge to attach the callback. Default: `false`.
 *
 *   A native caller has no use for it — providing [wireframeEmitter] is both the switch and the
 *   destination, and setting this without a callback delivers nowhere.
 *
 * @property wireframeEmitter When not null, hands you each wireframe as it is captured so
 *   you can check your masking while you develop. You get exactly the elements that are
 *   sent to Mixpanel, plus the reason each one's text was kept or removed.
 *
 *   This observes wireframe capture; it does not enable it. Wireframes are only captured
 *   when
 *   [com.mixpanel.android.sessionreplay.models.MPSessionReplayConfig.wireframesOptions]
 *   is set, so setting this on its own is harmless but never calls you back.
 *
 *   It runs in the background and never holds up recording, so it may arrive slightly out
 *   of step with the rest of the replay; make your callback thread-safe. Nothing given to
 *   it is ever sent to Mixpanel.
 */
@Serializable
data class DebugOptions(
    val overlayColors: DebugOverlayColors? = DebugOverlayColors(),
    val emitWireframes: Boolean = false,
    // @Transient because a callback has no JSON representation; the React Native bridge
    // decodes this class from JSON and native callers set it directly.
    @Transient
    val wireframeEmitter: ((WireframeDebugSnapshot) -> Unit)? = null
)
