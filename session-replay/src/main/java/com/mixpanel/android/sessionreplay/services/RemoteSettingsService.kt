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
 */
internal data class RemoteSettingsResult(
    val isRecordingEnabled: Boolean,
    val sdkConfig: SdkConfig? = null,
    val isFromCache: Boolean = false
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

    /**
     * Checks settings from the Mixpanel endpoint and returns both recording status and SDK config.
     */
    open suspend fun fetchRemoteSettings(token: String): RemoteSettingsResult = try {
        performRemoteSettingsFetch(token)
    } catch (e: Exception) {
        Logger.warn("Settings check failed: ${e.message}")
        getCachedSettingsResult(token)
    }

    private suspend fun performRemoteSettingsFetch(token: String): RemoteSettingsResult {
        Logger.info("Checking settings for project")

        val apiRequest = APIRequest(
            endPoint = EndPoints.settings(serverUrl),
            method = RequestMethod.GET,
            requestBody = null,
            queryItems = listOf(
                "recording" to "1",
                "sdk_config" to "1",
                "\$os" to "Android",
                "mp_lib" to mpLib,
                "\$lib_version" to version
            ),
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
            isFromCache = false
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
            isFromCache = true
        )
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
