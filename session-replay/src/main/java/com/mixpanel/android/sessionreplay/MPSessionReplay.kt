package com.mixpanel.android.sessionreplay

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.mixpanel.android.eventbridge.MixpanelEventBridge
import com.mixpanel.android.sessionreplay.logging.LogLevel
import com.mixpanel.android.sessionreplay.logging.Logger
import com.mixpanel.android.sessionreplay.logging.PrintDebugLogging
import com.mixpanel.android.sessionreplay.models.MPSessionReplayConfig
import com.mixpanel.android.sessionreplay.models.RemoteSettingsMode
import com.mixpanel.android.sessionreplay.services.RemoteSettingsResult
import com.mixpanel.android.sessionreplay.services.RemoteSettingsService
import com.mixpanel.android.sessionreplay.models.RecordingEventTrigger
import com.mixpanel.android.sessionreplay.utils.APIConstants
import com.mixpanel.android.sessionreplay.utils.LogMessages.AUTO_START_RECORDING_DEPRECATED
import com.mixpanel.android.sessionreplay.utils.LogMessages.WIREFRAMES_BETA_NOTICE
import com.mixpanel.android.sessionreplay.utils.RecordingEventTriggerEvaluator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.MalformedURLException
import java.net.URL

sealed class MPSessionReplayError : Exception() {
    data class Disabled(val reason: String) : MPSessionReplayError()
    data class InitializationError(override val cause: Throwable) : MPSessionReplayError()
}

/**
 * Consolidated result from applying remote settings.
 * Contains all values needed for SDK initialization in one place.
 */
internal data class ResolvedRemoteSettings(
    val isRecordingEnabled: Boolean,
    val config: MPSessionReplayConfig,
    val recordingEventTriggers: Map<String, RecordingEventTrigger>?
)

open class MPSessionReplay {
    // Class-level properties and other methods can go here if needed

    companion object { // Use companion object to define static functions
        fun initialize(
            appContext: Context,
            token: String,
            distinctId: String,
            config: MPSessionReplayConfig = MPSessionReplayConfig(),
            completion: (Result<MPSessionReplayInstance?>) -> Unit = {}
        ) = SessionReplayManager.initialize(appContext, token, distinctId, config, completion)

        @JvmStatic
        fun initialize(
            appContext: Context,
            token: String,
            distinctId: String,
            completion: (Result<MPSessionReplayInstance?>) -> Unit
        ) = initialize(appContext, token, distinctId, MPSessionReplayConfig(), completion)

        @JvmStatic
        fun initialize(
            appContext: Context,
            token: String,
            distinctId: String,
            config: MPSessionReplayConfig
        ) = initialize(appContext, token, distinctId, config, {})

        @JvmStatic
        fun initialize(
            appContext: Context,
            token: String,
            distinctId: String
        ) = initialize(appContext, token, distinctId, MPSessionReplayConfig(), {})

        fun getInstance(): MPSessionReplayInstance? = SessionReplayManager.getInstance()
    }
}

object SessionReplayManager {
    @Volatile
    private var instance: MPSessionReplayInstance? = null
    private val logger = PrintDebugLogging()

    // Job for event collection from MixpanelEventBridge
    @Volatile
    private var eventCollectionJob: Job? = null

    // Coroutine scope for initialization operations
    @Volatile
    private var initializationScope: CoroutineScope? = null

    // For testing purposes - allows injection of custom scope
    internal var coroutineScopeFactory: (() -> CoroutineScope)? = null

    // For testing purposes - allows injection of IO dispatcher
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    // For testing purposes
    internal var remoteSettingsServiceFactory: ((Context, String, String, String) -> RemoteSettingsService)? = null

    @Synchronized
    private fun getOrCreateScope(): CoroutineScope = initializationScope ?: run {
        val scope = coroutineScopeFactory?.invoke()
            ?: CoroutineScope(SupervisorJob() + Dispatchers.Main)
        initializationScope = scope
        scope
    }

    @Synchronized
    fun initialize(
        appContext: Context,
        token: String,
        // TODO: distinctId is not used, remove it
        distinctId: String,
        config: MPSessionReplayConfig,
        completion: (Result<MPSessionReplayInstance?>) -> Unit = {}
    ) {
        // Configure logging first
        configureLogging(config)

        logWireframesBetaNotice(appContext, config)

        // Validate serverUrl early to avoid unnecessary work
        validateServerUrl(config.serverUrl).onFailure { error ->
            Logger.warn("Invalid serverUrl, Session Replay is disabled: ${error.message}")
            completion(Result.failure(MPSessionReplayError.Disabled(error.message ?: "Invalid serverUrl")))
            return
        }

        @Suppress("Deprecation")
        if (!config.autoStartRecording) {
            Logger.warn(AUTO_START_RECORDING_DEPRECATED)
        }

        // Store previous instance for cleanup
        val previousInstance = instance
        instance = null

        // Create settings service
        val remoteSettingsService = remoteSettingsServiceFactory?.invoke(
            appContext,
            APIConstants.currentLibVersion,
            APIConstants.currentMpLib,
            config.serverUrl
        ) ?: RemoteSettingsService(
            appContext,
            version = APIConstants.currentLibVersion,
            mpLib = APIConstants.currentMpLib,
            serverUrl = config.serverUrl
        )

        // Cancel any existing initialization operations
        initializationScope?.cancel()
        initializationScope = null

        // Get or create a new scope for this initialization
        val scope = getOrCreateScope()

        // Launch async initialization
        scope.launch(ioDispatcher) {
            try {
                // Clean up previous instance on main thread if needed
                previousInstance?.let { withContext(Dispatchers.Main) { it.deinitialize() } }

                // Wait for app to enter foreground before making network call
                ForegroundAwaiter().waitForForeground()

                // Now we're in foreground, safe to make network call
                val settingsResult = remoteSettingsService.fetchRemoteSettings(
                    token,
                    wireframesRequested = config.wireframesOptions != null
                )
                val resolvedSettings = resolveRemoteSettings(config, settingsResult)

                withContext(Dispatchers.Main) {
                    when {
                        resolvedSettings == null -> {
                            val error = "Session Replay is disabled (STRICT mode: settings unavailable)"
                            Logger.warn(error)
                            completion(Result.failure(MPSessionReplayError.Disabled(error)))
                        }
                        !resolvedSettings.isRecordingEnabled -> {
                            val error = "Session Replay is disabled via remote settings"
                            Logger.warn(error)
                            completion(Result.failure(MPSessionReplayError.Disabled(error)))
                        }
                        else -> {
                            instance = MPSessionReplayInstance(appContext, token, distinctId, resolvedSettings.config)
                            setupEventTriggers(resolvedSettings.recordingEventTriggers)
                            completion(Result.success(instance))
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.error("Failed to initialize Session Replay: ${e.message}")
                withContext(Dispatchers.Main) {
                    completion(Result.failure(MPSessionReplayError.InitializationError(e)))
                }
            }
        }
    }

    /**
     * Applies remote settings to the config based on remoteSettingsMode.
     * Returns a consolidated ResolvedRemoteSettings containing all values needed for initialization.
     * Returns null if SDK initialization should be disabled (STRICT mode with API failure or missing sdk_config).
     */
    @VisibleForTesting
    internal fun resolveRemoteSettings(
        config: MPSessionReplayConfig,
        remoteSettings: RemoteSettingsResult
    ): ResolvedRemoteSettings? = when (config.remoteSettingsMode) {
        // The wireframe kill switch is an enablement switch, not remote config, so it is honored
        // even here where remote config values are ignored.
        RemoteSettingsMode.DISABLED -> ResolvedRemoteSettings(
            isRecordingEnabled = remoteSettings.isRecordingEnabled,
            config = applyWireframeKillSwitch(config, remoteSettings),
            recordingEventTriggers = null // Ignored in DISABLED mode
        )

        RemoteSettingsMode.STRICT -> if (remoteSettings.isFromCache || remoteSettings.sdkConfig == null) {
            // The API call failed or the response did not include sdk_config.config
            null
        } else {
            ResolvedRemoteSettings(
                isRecordingEnabled = remoteSettings.isRecordingEnabled,
                config = applyRemoteConfigValues(config, remoteSettings),
                recordingEventTriggers = remoteSettings.sdkConfig.recordingEventTriggers
            )
        }

        RemoteSettingsMode.FALLBACK -> ResolvedRemoteSettings(
            isRecordingEnabled = remoteSettings.isRecordingEnabled,
            config = applyRemoteConfigValues(config, remoteSettings),
            recordingEventTriggers = remoteSettings.sdkConfig?.recordingEventTriggers
        )
    }

    /**
     * Applies remote config values to the local config.
     * Returns the original config if no valid remote values are available.
     */
    private fun applyRemoteConfigValues(config: MPSessionReplayConfig, remoteSettings: RemoteSettingsResult): MPSessionReplayConfig {
        var updatedConfig = config

        // Apply recordSessionsPercent if available from remote
        remoteSettings.sdkConfig?.recordSessionsPercent?.let { percent ->
            if (percent in 0.0..100.0) {
                Logger.info("Applying remote recordSessionsPercent: $percent")
                updatedConfig = updatedConfig.copy(recordingSessionsPercent = percent)
            } else {
                Logger.warn("Invalid remote recordSessionsPercent value: $percent. Must be between 0.00 and 100.00.")
            }
        }

        return applyWireframeKillSwitch(updatedConfig, remoteSettings)
    }

    /**
     * Honors the server-side wireframe kill switch by clearing
     * [MPSessionReplayConfig.wireframesOptions].
     *
     * `wireframesOptions` is the only switch the wireframe pipeline reads, so clearing it before the
     * instance is built turns wireframe capture off wholesale while leaving replay recording
     * untouched.
     */
    private fun applyWireframeKillSwitch(
        config: MPSessionReplayConfig,
        remoteSettings: RemoteSettingsResult
    ): MPSessionReplayConfig {
        if (remoteSettings.isWireframeEnabled || config.wireframesOptions == null) return config

        Logger.warn("Wireframe capture is disabled via remote settings")
        return config.copy(wireframesOptions = null)
    }

    /**
     * Tells an app that just turned wireframes on that the feature is in beta.
     *
     * Deliberately bypasses [Logger] and its [MPSessionReplayConfig.enableLogging] gate: the
     * developer who needs to read this is the one wiring wireframes up for the first time, and
     * has no particular reason to have opted into verbose logging. Restricted to debuggable
     * builds instead, so a shipped app prints nothing.
     *
     * Fires on local opt-in, before the remote kill switch is consulted. If the server then turns
     * capture off, [applyWireframeKillSwitch] says so in its own log line.
     */
    private fun logWireframesBetaNotice(appContext: Context, config: MPSessionReplayConfig) {
        if (config.wireframesOptions == null) return
        if ((appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return

        Log.i("MixpanelSessionReplay", WIREFRAMES_BETA_NOTICE)
    }

    private fun configureLogging(config: MPSessionReplayConfig) {
        Logger.removeLogging(logger)

        if (config.enableLogging) {
            Logger.addLogging(logger)
            LogLevel.entries.forEach { level ->
                Logger.enableLevel(level)
            }
            Logger.info("Logging Enabled")
            Logger.warn("Initializing Session Replay")
        } else {
            LogLevel.entries.forEach { level ->
                Logger.disableLevel(level)
            }
        }
    }

    /**
     * Sets up event triggers from remote settings.
     * Collects events from MixpanelEventBridge and evaluates triggers.
     */
    @Synchronized
    private fun setupEventTriggers(triggers: Map<String, RecordingEventTrigger>?) {
        // Clean up previous event collection if it exists
        cleanupEventCollection()

        // If no triggers configured, skip setup
        if (triggers.isNullOrEmpty()) {
            Logger.info("[SessionReplay] No event triggers configured. Event-based recording disabled.")
            return
        }

        // Reset event triggers flag to enabled when setting up new triggers
        instance?.enableEventTriggers()

        Logger.info("[SessionReplay] Configuring ${triggers.size} event trigger(s)")

        val evaluator = RecordingEventTriggerEvaluator(triggers)
        val scope = getOrCreateScope()

        // Collect events from MixpanelEventBridge
        eventCollectionJob = scope.launch(Dispatchers.Default) {
            try {
                MixpanelEventBridge.events().collect { event ->
                    processEvent(event.eventName, event.properties, evaluator)
                }
            } catch (e: Exception) {
                Logger.error("[SessionReplay] Error collecting events: ${e.message}")
            }
        }

        Logger.info("[SessionReplay] Event triggers configured successfully")
    }

    /**
     * Processes an event and starts recording if trigger conditions are met.
     */
    private suspend fun processEvent(
        eventName: String,
        properties: org.json.JSONObject?,
        evaluator: RecordingEventTriggerEvaluator
    ) {
        // Check if event triggers are enabled (early exit when paused)
        val currentInstance = instance
        if (currentInstance?.isEventTriggersEnabled() != true) {
            Logger.debug("[SessionReplay] Event triggers paused, ignoring event: $eventName")
            return
        }

        // Properties must be non-null for evaluation
        val props = properties ?: org.json.JSONObject()

        // Evaluate trigger conditions (returns sampling percentage or null)
        val samplingPercent = evaluator.shouldStartRecording(
            eventName = eventName,
            properties = props
        )

        if (samplingPercent != null) {
            Logger.info("[SessionReplay] Event trigger matched: $eventName. Starting recording with $samplingPercent% sampling.")

            // Start recording on main thread with trigger's sampling percentage
            withContext(Dispatchers.Main) {
                // Only start if not already recording
                if (!currentInstance.isRecording()) {
                    currentInstance.startRecording(sessionsPercent = samplingPercent)
                } else {
                    Logger.debug("[SessionReplay] Recording already active, skipping start for event: $eventName")
                }
            }
        }
    }

    @Synchronized
    internal fun cleanupEventCollection() {
        eventCollectionJob?.cancel()
        eventCollectionJob = null
    }

    fun getInstance(): MPSessionReplayInstance? = instance

    internal fun validateServerUrl(url: String): Result<String> {
        val trimmedUrl = url.trim()

        if (!trimmedUrl.startsWith("https://")) {
            return Result.failure(
                IllegalArgumentException("MPSessionReplayConfig.serverUrl must start with https://")
            )
        }

        return try {
            URL(trimmedUrl)
            Result.success(trimmedUrl)
        } catch (e: MalformedURLException) {
            Result.failure(IllegalArgumentException("MPSessionReplayConfig.serverUrl '$trimmedUrl' is malformed: ${e.message}"))
        }
    }
}
