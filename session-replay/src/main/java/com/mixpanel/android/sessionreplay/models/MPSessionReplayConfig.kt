package com.mixpanel.android.sessionreplay.models

import com.mixpanel.android.sessionreplay.debug.DebugOptions
import com.mixpanel.android.sessionreplay.sensitive_views.AutoMaskedView
import com.mixpanel.android.sessionreplay.utils.DataResidency
import com.mixpanel.android.sessionreplay.utils.EndPoints
import com.mixpanel.android.sessionreplay.utils.ReplaySettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Configuration options for Mixpanel Session Replay.
 *
 * @property wifiOnly If true, session replays will only be flushed when connected to Wi-Fi. Default: `true`.
 * @property flushInterval Interval in seconds at which session replay events are flushed to the server.
 * @property autoStartRecording **Deprecated.** Use [recordingSessionsPercent] instead. This property will be removed in a future release. Default: `true`.
 * @property recordingSessionsPercent Sampling rate for recording sessions (0.0 to 100.0). Default: `100.0`.
 * @property autoMaskedViews Set of view types to automatically mask during recording.
 * @property enableLogging If true, enables debug logging for session replay. Default: `false`.
 * @property remoteSettingsMode Controls how remote SDK config settings are fetched and applied. Default: `DISABLED`.
 * @property debugOptions Debug feature configuration. When not null, debug features are enabled. Only works in debuggable builds.
 * @property serverUrl The server URL for your Mixpanel data residency. Use constants from
 *   [DataResidency]: [DataResidency.US], [DataResidency.EU], or [DataResidency.IN].
 *   Defaults to US.
 */
@Serializable
data class MPSessionReplayConfig(
    var wifiOnly: Boolean = true,
    var flushInterval: Long = ReplaySettings.FLUSH_INTERVAL,
    @Deprecated("Use recordingSessionsPercent instead. Set to 0 to disable auto-start recording. Any value > 0 and <= 100 enables it.")
    var autoStartRecording: Boolean = true,
    var recordingSessionsPercent: Double = 100.0,
    var autoMaskedViews: Set<AutoMaskedView> = AutoMaskedView.defaultSet(),
    var enableLogging: Boolean = false,
    var remoteSettingsMode: RemoteSettingsMode = RemoteSettingsMode.DISABLED,
    var debugOptions: DebugOptions? = null,
    var serverUrl: String = EndPoints.DEFAULT_BASE_URL
) {
    // IMPORTANT:
    // This class is serializable and used in React Native. When adding new parameters,
    // ensure they are serializable or marked with `@Transient`, and add corresponding
    // support in the React Native bridge.

    // Initialize from JSON
    companion object {
        private val json =
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            }

        fun fromJson(jsonString: String): MPSessionReplayConfig = json.decodeFromString<MPSessionReplayConfig>(jsonString)
    }

    // Convert to JSON
    fun toJson(): String = json.encodeToString(this)
}
