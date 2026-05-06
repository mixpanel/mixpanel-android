package com.mixpanel.android.sessionreplay.debug

import kotlinx.serialization.Serializable

/**
 * Configuration for debug features in Session Replay.
 *
 * @property overlayColors When not null, enables a visual overlay showing which views
 *   are being masked. Only works in debuggable builds.
 */
@Serializable
data class DebugOptions(
    val overlayColors: DebugOverlayColors? = DebugOverlayColors()
)
