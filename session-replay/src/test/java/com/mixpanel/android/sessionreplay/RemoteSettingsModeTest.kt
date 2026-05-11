package com.mixpanel.android.sessionreplay

import com.mixpanel.android.sessionreplay.models.MPSessionReplayConfig
import com.mixpanel.android.sessionreplay.models.RemoteSettingsMode
import com.mixpanel.android.sessionreplay.network.SdkConfig
import com.mixpanel.android.sessionreplay.services.RemoteSettingsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for remote settings mode functionality.
 * These tests verify that the applyRemoteSettings logic works correctly for all modes.
 */
class RemoteSettingsModeTest {

    // --- DISABLED Mode Tests ---

    @Test
    fun testDisabledModeIgnoresRemoteConfig() {
        val config = MPSessionReplayConfig(
            recordingSessionsPercent = 100.0,
            remoteSettingsMode = RemoteSettingsMode.DISABLED
        )
        val result = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = SdkConfig(recordSessionsPercent = 25.0)
        )

        val finalConfig = SessionReplayManager.applyRemoteSettings(config, result)

        // Should keep original config, ignoring remote config
        assertEquals(config, finalConfig)
    }

    // --- STRICT Mode Tests ---

    @Test
    fun testStrictModeAppliesRemoteConfig() {
        val config = MPSessionReplayConfig(
            recordingSessionsPercent = 100.0,
            remoteSettingsMode = RemoteSettingsMode.STRICT
        )
        val result = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = SdkConfig(recordSessionsPercent = 10.0)
        )

        val finalConfig = SessionReplayManager.applyRemoteSettings(config, result)

        // Should apply remote value
        assertEquals(config.copy(recordingSessionsPercent = 10.0), finalConfig)
    }

    @Test
    fun testStrictModeReturnsNullOnApiFailure() {
        val config = MPSessionReplayConfig(
            autoStartRecording = true,
            recordingSessionsPercent = 100.0,
            remoteSettingsMode = RemoteSettingsMode.STRICT
        )
        val result = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = null,
            isFromCache = true // API failed, using cache
        )

        val finalConfig = SessionReplayManager.applyRemoteSettings(config, result)

        // STRICT mode should return null when API fails, disabling SDK initialization
        assertNull(finalConfig)
    }

    @Test
    fun testStrictModeReturnsNullWhenApiSucceedsWithNullSdkConfig() {
        val config = MPSessionReplayConfig(
            autoStartRecording = true,
            recordingSessionsPercent = 80.0,
            remoteSettingsMode = RemoteSettingsMode.STRICT
        )
        val result = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = null,
            isFromCache = false // API succeeded but no sdk_config
        )

        val finalConfig = SessionReplayManager.applyRemoteSettings(config, result)

        // STRICT mode should return null when sdk_config is missing, disabling SDK initialization
        assertNull(finalConfig)
    }

    @Test
    fun testStrictModeUsesInitConfigWhenRecordSessionsPercentIsNull() {
        val config = MPSessionReplayConfig(
            recordingSessionsPercent = 80.0,
            remoteSettingsMode = RemoteSettingsMode.STRICT
        )
        val result = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = SdkConfig(recordSessionsPercent = null)
        )

        val finalConfig = SessionReplayManager.applyRemoteSettings(config, result)

        // Should use init value when remote value is null
        assertEquals(80.0, finalConfig?.recordingSessionsPercent)
    }

    @Test
    fun testStrictModeUsesInitConfigWhenRecordSessionsPercentIsInvalid() {
        val config = MPSessionReplayConfig(
            recordingSessionsPercent = 50.0,
            remoteSettingsMode = RemoteSettingsMode.STRICT
        )
        // Test with invalid value (negative)
        val result = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = SdkConfig(recordSessionsPercent = -10.0)
        )

        val finalConfig = SessionReplayManager.applyRemoteSettings(config, result)

        // Should use init value when remote value is invalid
        assertEquals(50.0, finalConfig?.recordingSessionsPercent)
    }

    @Test
    fun testStrictModeAppliesValidBoundaryValues() {
        val config = MPSessionReplayConfig(
            recordingSessionsPercent = 50.0,
            remoteSettingsMode = RemoteSettingsMode.STRICT
        )

        // Test 0.0 (valid boundary)
        val result0 = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = SdkConfig(recordSessionsPercent = 0.0)
        )
        assertEquals(0.0, SessionReplayManager.applyRemoteSettings(config, result0)?.recordingSessionsPercent)

        // Test 100.0 (valid boundary)
        val result100 = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = SdkConfig(recordSessionsPercent = 100.0)
        )
        assertEquals(100.0, SessionReplayManager.applyRemoteSettings(config, result100)?.recordingSessionsPercent)
    }

    // --- FALLBACK Mode Tests ---

    @Test
    fun testFallbackModeAppliesRemoteConfig() {
        val config = MPSessionReplayConfig(
            recordingSessionsPercent = 100.0,
            remoteSettingsMode = RemoteSettingsMode.FALLBACK
        )
        val result = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = SdkConfig(recordSessionsPercent = 25.0)
        )

        val finalConfig = SessionReplayManager.applyRemoteSettings(config, result)

        // Should apply remote value
        assertEquals(config.copy(recordingSessionsPercent = 25.0), finalConfig)
    }

    @Test
    fun testFallbackModeKeepsAutoRecordingOnApiFailure() {
        val config = MPSessionReplayConfig(
            autoStartRecording = true,
            recordingSessionsPercent = 100.0,
            remoteSettingsMode = RemoteSettingsMode.FALLBACK
        )
        val result = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = null,
            isFromCache = true // API failed, using cache
        )

        val finalConfig = SessionReplayManager.applyRemoteSettings(config, result)

        // FALLBACK mode should keep original config
        assertEquals(config, finalConfig)
    }

    @Test
    fun testFallbackModeUsesCachedValueWhenAvailable() {
        val config = MPSessionReplayConfig(
            recordingSessionsPercent = 100.0,
            remoteSettingsMode = RemoteSettingsMode.FALLBACK
        )
        val result = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = SdkConfig(recordSessionsPercent = 50.0), // Cached value
            isFromCache = true
        )

        val finalConfig = SessionReplayManager.applyRemoteSettings(config, result)

        // Should use cached value
        assertEquals(config.copy(recordingSessionsPercent = 50.0), finalConfig)
    }

    @Test
    fun testFallbackModeKeepsOriginalValueWhenSdkConfigMissing() {
        val config = MPSessionReplayConfig(
            recordingSessionsPercent = 75.0,
            remoteSettingsMode = RemoteSettingsMode.FALLBACK
        )
        val result = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = null,
            isFromCache = false // API succeeded but no sdk_config
        )

        val finalConfig = SessionReplayManager.applyRemoteSettings(config, result)

        // Should keep original config when sdk_config is missing
        assertEquals(config, finalConfig)
    }

    @Test
    fun testFallbackModeUsesInitConfigWhenRecordSessionsPercentIsNull() {
        val config = MPSessionReplayConfig(
            recordingSessionsPercent = 60.0,
            remoteSettingsMode = RemoteSettingsMode.FALLBACK
        )
        val result = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = SdkConfig(recordSessionsPercent = null)
        )

        val finalConfig = SessionReplayManager.applyRemoteSettings(config, result)

        // Should use init value when remote value is null
        assertEquals(60.0, finalConfig?.recordingSessionsPercent)
    }

    @Test
    fun testFallbackModeUsesInitConfigWhenRecordSessionsPercentIsInvalid() {
        val config = MPSessionReplayConfig(
            recordingSessionsPercent = 40.0,
            remoteSettingsMode = RemoteSettingsMode.FALLBACK
        )
        val result = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = SdkConfig(recordSessionsPercent = 150.0) // Invalid: > 100
        )

        val finalConfig = SessionReplayManager.applyRemoteSettings(config, result)

        // Should use init value when remote value is invalid
        assertEquals(40.0, finalConfig?.recordingSessionsPercent)
    }

    // --- DISABLED Mode Additional Tests ---

    @Test
    fun testDisabledModeIgnoresCachedConfig() {
        val config = MPSessionReplayConfig(
            recordingSessionsPercent = 100.0,
            remoteSettingsMode = RemoteSettingsMode.DISABLED
        )
        val result = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = SdkConfig(recordSessionsPercent = 10.0),
            isFromCache = true
        )

        val finalConfig = SessionReplayManager.applyRemoteSettings(config, result)

        // DISABLED mode should ignore even cached config
        assertEquals(config, finalConfig)
    }

    @Test
    fun testDisabledModeIgnoresApiFailure() {
        val config = MPSessionReplayConfig(
            recordingSessionsPercent = 100.0,
            remoteSettingsMode = RemoteSettingsMode.DISABLED
        )
        val result = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = null,
            isFromCache = true // Simulating API failure
        )

        val finalConfig = SessionReplayManager.applyRemoteSettings(config, result)

        // DISABLED mode should return config (not null like STRICT)
        assertEquals(config, finalConfig)
    }

    // --- Edge Case Tests ---

    @Test
    fun testAllModesHandleEmptySdkConfig() {
        val initConfig = MPSessionReplayConfig(recordingSessionsPercent = 75.0)
        val emptySdkConfig = SdkConfig() // sdk_config : { config : {} }

        // STRICT: sdkConfig object exists, so SDK initializes with init value
        val strictConfig = initConfig.copy(remoteSettingsMode = RemoteSettingsMode.STRICT)
        val strictResult = RemoteSettingsResult(isRecordingEnabled = true, sdkConfig = emptySdkConfig)
        assertEquals(75.0, SessionReplayManager.applyRemoteSettings(strictConfig, strictResult)?.recordingSessionsPercent)

        // FALLBACK: uses init value
        val fallbackConfig = initConfig.copy(remoteSettingsMode = RemoteSettingsMode.FALLBACK)
        val fallbackResult = RemoteSettingsResult(isRecordingEnabled = true, sdkConfig = emptySdkConfig)
        assertEquals(75.0, SessionReplayManager.applyRemoteSettings(fallbackConfig, fallbackResult)?.recordingSessionsPercent)

        // DISABLED: always uses init value
        val disabledConfig = initConfig.copy(remoteSettingsMode = RemoteSettingsMode.DISABLED)
        val disabledResult = RemoteSettingsResult(isRecordingEnabled = true, sdkConfig = emptySdkConfig)
        assertEquals(75.0, SessionReplayManager.applyRemoteSettings(disabledConfig, disabledResult)?.recordingSessionsPercent)
    }
}
