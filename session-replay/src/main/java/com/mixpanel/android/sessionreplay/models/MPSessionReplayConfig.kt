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
 * @property debugOptions Debug feature configuration. When not null, debug features are enabled.
 *   Its two members differ in where they take effect: [DebugOptions.overlayColors] draws on the
 *   user's screen and is restricted to debuggable builds, while [DebugOptions.wireframeEmitter]
 *   renders nothing and hands your own code a description of a frame the SDK already built, so
 *   it is delivered in any build.
 * @property serverUrl The server URL for your Mixpanel data residency. Use constants from
 *   [DataResidency]: [DataResidency.US], [DataResidency.EU], or [DataResidency.IN].
 *   Defaults to US.
 * @property wireframesOptions **Beta.** Wireframe capture configuration. When set, the SDK
 *   records a text outline of each captured screen — the visible elements, what they say,
 *   and where they sit — alongside the replay, so sessions can be summarized without
 *   watching them. Your masking settings apply to it. Before shipping to production,
 *   inspect the wireframes your app produces and confirm that no sensitive information is
 *   captured; see [WireframesOptions].
 *   Default: `null` (wireframe capture disabled).
 *
 *   Serialized, so React Native can turn wireframes on through the config JSON it already
 *   sends. [SensitiveRuleSerializer] flattens the sealed rule hierarchy (and its `Regex`
 *   instances) into the same JSON shape iOS decodes.
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
    var serverUrl: String = EndPoints.DEFAULT_BASE_URL,
    var wireframesOptions: WireframesOptions? = null
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
