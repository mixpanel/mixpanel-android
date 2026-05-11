package com.mixpanel.android.sessionreplay

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.mixpanel.android.sessionreplay.models.RecordingEventTrigger
import com.mixpanel.android.sessionreplay.network.SdkConfig
import com.mixpanel.android.sessionreplay.services.RemoteSettingsResult
import com.mixpanel.android.sessionreplay.services.RemoteSettingsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Test helper for configuring Session Replay behavior in instrumentation tests.
 * This class provides methods to inject mock remote settings for testing purposes.
 *
 * **Note:** This class is intended for testing only and should not be used in production code.
 */
@VisibleForTesting
internal object SessionReplayTestHelper {

    /**
     * Configures mock event triggers that will be returned by the remote settings service.
     * Call this before initializing Session Replay in your test.
     *
     * This also sets up a fresh coroutine scope to avoid interference from MainApplication's
     * initialization.
     *
     * @param triggers Map of event names to their trigger configurations
     */
    @JvmStatic
    fun configureMockEventTriggers(triggers: Map<String, RecordingEventTrigger>) {
        // Set up a fresh coroutine scope to avoid race conditions with MainApplication
        SessionReplayManager.coroutineScopeFactory = {
            CoroutineScope(SupervisorJob() + Dispatchers.Main)
        }

        SessionReplayManager.remoteSettingsServiceFactory = { context, version, mpLib, serverUrl ->
            MockRemoteSettingsService(context, version, mpLib, serverUrl, triggers)
        }
    }

    /**
     * Resets the test configuration back to default (real remote settings).
     * Call this in your test's tearDown/cleanup.
     */
    @JvmStatic
    fun reset() {
        SessionReplayManager.remoteSettingsServiceFactory = null
        SessionReplayManager.coroutineScopeFactory = null
    }
}

/**
 * Mock implementation of RemoteSettingsService for testing.
 */
internal class MockRemoteSettingsService(
    context: Context,
    version: String,
    mpLib: String,
    serverUrl: String,
    private val mockTriggers: Map<String, RecordingEventTrigger>
) : RemoteSettingsService(context, version = version, mpLib = mpLib, serverUrl = serverUrl) {

    override suspend fun fetchRemoteSettings(token: String): RemoteSettingsResult = RemoteSettingsResult(
        isRecordingEnabled = true,
        sdkConfig = SdkConfig(
            recordSessionsPercent = 100.0,
            recordingEventTriggers = mockTriggers
        ),
        isFromCache = false
    )
}
