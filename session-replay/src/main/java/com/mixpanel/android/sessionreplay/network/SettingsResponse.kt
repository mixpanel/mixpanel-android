package com.mixpanel.android.sessionreplay.network

import com.mixpanel.android.sessionreplay.models.RecordingEventTrigger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SettingsResponse(
    val recording: RecordingSettings,
    @SerialName("sdk_config")
    val sdkConfig: SdkConfigWrapper? = null
)

@Serializable
internal data class RecordingSettings(
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
