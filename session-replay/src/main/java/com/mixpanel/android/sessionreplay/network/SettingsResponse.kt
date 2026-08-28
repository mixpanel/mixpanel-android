package com.mixpanel.android.sessionreplay.network

import com.mixpanel.android.sessionreplay.models.RecordingEventTrigger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SettingsResponse(
    val recording: RecordingSettings,
    @SerialName("sdk_config")
    val sdkConfig: SdkConfigWrapper? = null,
    /**
     * Server-side kill switch for wireframe capture. Only present in the response when the
     * SDK asked for it (`wireframe=1`), which it only does when
     * [com.mixpanel.android.sessionreplay.models.MPSessionReplayConfig.wireframesOptions] is set.
     */
    val wireframe: WireframeSettings? = null
)

@Serializable
internal data class RecordingSettings(
    @SerialName("is_enabled")
    val isEnabled: Boolean,
    val error: String? = null
)

/**
 * Wireframe kill switch, independent of [RecordingSettings]: replay keeps recording, only the
 * wireframe payload is dropped.
 */
@Serializable
internal data class WireframeSettings(
    @SerialName("is_enabled")
    val isEnabled: Boolean,
    val error: String? = null
)

@Serializable
internal data class SdkConfigWrapper(
    val config: SdkConfig? = null,
    val error: String? = null
)

@Serializable
internal data class SdkConfig(
    @SerialName("record_sessions_percent")
    val recordSessionsPercent: Double? = null,
    @SerialName("recording_event_triggers")
    val recordingEventTriggers: Map<String, RecordingEventTrigger>? = null
)
