package com.mixpanel.android.sessionreplay.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import com.mixpanel.android.sessionreplay.logging.Logger
import com.mixpanel.android.sessionreplay.network.APIRequest
import com.mixpanel.android.sessionreplay.network.Network
import com.mixpanel.android.sessionreplay.network.RequestMethod
import com.mixpanel.android.sessionreplay.network.SdkConfig
import com.mixpanel.android.sessionreplay.network.SettingsResponse
import com.mixpanel.android.sessionreplay.utils.EndPoints
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Result from checking settings endpoint, containing recording status, SDK config, and event triggers.
 *
 * [isWireframeEnabled] is the server-side wireframe kill switch. It defaults to enabled: the
 * `wireframe` field is only requested (and only returned) when the app opted in to wireframes,
 * and anything short of an explicit `false` leaves capture alone.
 */
internal data class RemoteSettingsResult(
    val isRecordingEnabled: Boolean,
    val sdkConfig: SdkConfig? = null,
    val isFromCache: Boolean = false,
    val isWireframeEnabled: Boolean = true
)

internal open class RemoteSettingsService(
    private val context: Context,
    private val network: Network = Network(),
    private val version: String,
    private val mpLib: String,
    private val serverUrl: String = EndPoints.DEFAULT_BASE_URL
) {
    companion object {
        private const val SETTINGS_TIMEOUT_MS = 5000L
        private const val PREFS_NAME = "mp_session_replay_prefs"
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun recordingEnabledKey(token: String) = "mp_sr_recording_${token}_enabled"
    private fun recordingTimestampKey(token: String) = "mp_sr_recording_${token}_timestamp"
    private fun sdkConfigKey(token: String) = "mp_sr_recording_${token}_sdk_config"
    private fun wireframeEnabledKey(token: String) = "mp_sr_wireframe_${token}_enabled"

    /**
     * Checks settings from the Mixpanel endpoint and returns both recording status and SDK config.
     *
     * @param wireframesRequested whether this app opted in to wireframes. When true the request asks
     *   for the wireframe kill switch (`wireframe=1`) so the server can turn capture off remotely.
     */
    open suspend fun fetchRemoteSettings(token: String, wireframesRequested: Boolean = false): RemoteSettingsResult = try {
        performRemoteSettingsFetch(token, wireframesRequested)
    } catch (e: Exception) {
        Logger.warn("Settings check failed: ${e.message}")
        getCachedSettingsResult(token)
    }

    private suspend fun performRemoteSettingsFetch(token: String, wireframesRequested: Boolean): RemoteSettingsResult {
        Logger.info("Checking settings for project")

        val apiRequest = APIRequest(
            endPoint = EndPoints.settings(serverUrl),
            method = RequestMethod.GET,
            requestBody = null,
            queryItems = buildList {
                add("recording" to "1")
                add("sdk_config" to "1")
                // Only ask for the wireframe kill switch when this app opted in to wireframes.
                if (wireframesRequested) add("wireframe" to "1")
                add("\$os" to "Android")
                add("mp_lib" to mpLib)
                add("\$lib_version" to version)
            },
            headers = mapOf(
                "Authorization" to "Basic ${Base64.encodeToString("$token:".toByteArray(), Base64.NO_WRAP)}"
            ),
            timeout = SETTINGS_TIMEOUT_MS
        )

        val result = network.performAPIRequestWithResponse(apiRequest)
        return if (result.isSuccess) {
            handleSuccessResponse(result.getOrNull() ?: "", token)
        } else {
            handleErrorResponse(result.exceptionOrNull(), token)
        }
    }

    private fun handleSuccessResponse(response: String, token: String): RemoteSettingsResult = try {
        Logger.debug("Parsing settings response: $response")
        val settingsResponse = json.decodeFromString<SettingsResponse>(response)
        val isEnabled = settingsResponse.recording.isEnabled
        val sdkConfig = settingsResponse.sdkConfig?.config

        Logger.debug("Recording is_enabled value: $isEnabled")

        if (isEnabled) {
            Logger.info("Recording settings check complete: enabled")
            clearRecordingCache(token)
        } else {
            Logger.warn("Recording settings check complete: disabled")
            settingsResponse.recording.error?.let { error ->
                Logger.warn("Recording settings error message: $error")
            }
            cacheRecordingDisabled(token)
        }

        val isWireframeEnabled = resolveWireframeEnabled(settingsResponse, token)

        val finalSdkConfig = sdkConfig?.also {
            // Cache SDK config if present (includes event triggers)
            cacheSdkConfig(token, it)
            Logger.info("Remote SDK config: $it")
        } ?: run {
            Logger.warn("Remote SDK config not found${settingsResponse.sdkConfig?.error?.let { ". Error: $it" } ?: ""}")
            // API succeeded but sdk_config missing - clear cache
            clearCachedSdkConfig(token)
            null
        }

        RemoteSettingsResult(
            isRecordingEnabled = isEnabled,
            sdkConfig = finalSdkConfig,
            isFromCache = false,
            isWireframeEnabled = isWireframeEnabled
        )
    } catch (e: Exception) {
        Logger.error("Failed to parse settings response: ${e.message}")
        getCachedSettingsResult(token)
    }

    private fun clearCachedSdkConfig(token: String) {
        try {
            prefs.edit { remove(sdkConfigKey(token)) }
            Logger.info("Cleared cached SDK config")
        } catch (e: Exception) {
            Logger.error("Failed to clear cached SDK config: ${e.message}")
        }
    }

    private fun handleErrorResponse(exception: Throwable?, token: String): RemoteSettingsResult {
        val errorMessage = exception?.message ?: "Unknown error"
        Logger.warn("Settings API error: $errorMessage -- checking cache...")
        return getCachedSettingsResult(token)
    }

    /**
     * Returns cached settings result when API call fails.
     */
    private fun getCachedSettingsResult(token: String): RemoteSettingsResult {
        val cachedSdkConfig = getCachedSdkConfig(token)
        return RemoteSettingsResult(
            isRecordingEnabled = checkCachedRecordingState(token),
            sdkConfig = cachedSdkConfig,
            isFromCache = true,
            isWireframeEnabled = checkCachedWireframeState(token)
        )
    }

    // --- Wireframe Kill Switch ---

    /**
     * Reads the wireframe kill switch off a fresh response and refreshes its cache.
     *
     * An absent `wireframe` field means the switch was never asked for (wireframes off locally) or
     * the server had nothing to say, so the previously cached verdict is left untouched and capture
     * stays on.
     */
    private fun resolveWireframeEnabled(settingsResponse: SettingsResponse, token: String): Boolean {
        val wireframe = settingsResponse.wireframe ?: return true

        return if (wireframe.isEnabled) {
            Logger.info("Wireframe settings check complete: enabled")
            clearWireframeCache(token)
            true
        } else {
            Logger.warn("Wireframe capture is disabled via remote settings")
            wireframe.error?.let { error -> Logger.warn("Wireframe settings error message: $error") }
            cacheWireframeDisabled(token)
            false
        }
    }

    private fun cacheWireframeDisabled(token: String) {
        try {
            prefs.edit { putBoolean(wireframeEnabledKey(token), false) }
        } catch (e: Exception) {
            Logger.error("Failed to cache wireframe state: ${e.message}")
        }
    }

    private fun clearWireframeCache(token: String) {
        try {
            prefs.edit { remove(wireframeEnabledKey(token)) }
        } catch (e: Exception) {
            Logger.error("Failed to clear wireframe cache: ${e.message}")
        }
    }

    private fun checkCachedWireframeState(token: String): Boolean = try {
        val key = wireframeEnabledKey(token)
        if (prefs.contains(key)) {
            prefs.getBoolean(key, true).also { isEnabled ->
                if (!isEnabled) Logger.info("Using cached wireframe state: disabled")
            }
        } else {
            true
        }
    } catch (e: Exception) {
        Logger.error("Failed to check cached wireframe state: ${e.message}")
        true // Default to enabled on error
    }

    // --- Recording Enabled Cache ---

    private fun cacheRecordingDisabled(token: String) {
        try {
            prefs.edit {
                putBoolean(recordingEnabledKey(token), false)
                putLong(recordingTimestampKey(token), System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Logger.error("Failed to cache recording state: ${e.message}")
        }
    }

    private fun clearRecordingCache(token: String) {
        try {
            prefs.edit {
                remove(recordingEnabledKey(token))
                remove(recordingTimestampKey(token))
            }
        } catch (e: Exception) {
            Logger.error("Failed to clear recording cache: ${e.message}")
        }
    }

    private fun checkCachedRecordingState(token: String): Boolean = try {
        val key = recordingEnabledKey(token)
        if (prefs.contains(key)) {
            prefs.getBoolean(key, true).also { isEnabled ->
                if (!isEnabled) Logger.info("Using cached recording state: disabled")
            }
        } else {
            Logger.info("No cached recording state, defaulting to enabled")
            true
        }
    } catch (e: Exception) {
        Logger.error("Failed to check cached recording state: ${e.message}")
        true // Default to enabled on error
    }

    // --- SDK Config Cache ---

    private fun cacheSdkConfig(token: String, sdkConfig: SdkConfig) {
        try {
            prefs.edit { putString(sdkConfigKey(token), json.encodeToString(sdkConfig)) }
        } catch (e: Exception) {
            Logger.error("Failed to cache SDK config: ${e.message}")
        }
    }

    private fun getCachedSdkConfig(token: String): SdkConfig? = try {
        prefs.getString(sdkConfigKey(token), null)?.let { jsonString ->
            json.decodeFromString<SdkConfig>(jsonString).also {
                Logger.info("Using cached SDK config: $it")
            }
        }
    } catch (e: Exception) {
        Logger.error("Failed to get cached SDK config: ${e.message}")
        null
    }
}
