package com.mixpanel.android.sessionreplay.debug

import com.mixpanel.android.sessionreplay.wireframe.WireframeDebugSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Configuration for debug features in Session Replay.
 *
 * @property overlayColors When not null, enables a visual overlay showing which views
 *   are being masked. Only works in debuggable builds.
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
    // @Transient because a callback has no JSON representation. A host that configures the SDK
    // from JSON decodes this class and then attaches the callback itself.
    @Transient
    val wireframeEmitter: ((WireframeDebugSnapshot) -> Unit)? = null
)
