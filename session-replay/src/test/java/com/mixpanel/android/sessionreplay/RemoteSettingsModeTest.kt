package com.mixpanel.android.sessionreplay

import com.mixpanel.android.sessionreplay.models.MPSessionReplayConfig
import com.mixpanel.android.sessionreplay.models.RecordingEventTrigger
import com.mixpanel.android.sessionreplay.models.RemoteSettingsMode
import com.mixpanel.android.sessionreplay.network.SdkConfig
import com.mixpanel.android.sessionreplay.services.RemoteSettingsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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

        val resolvedSettings =SessionReplayManager.applyRemoteSettings(config, result)

        // Should keep original config, ignoring remote config
        assertEquals(config, resolvedSettings?.config)
        // Event triggers should be null in DISABLED mode
        assertNull(resolvedSettings?.recordingEventTriggers)
    }

    @Test
    fun testDisabledModePreservesIsRecordingEnabled() {
        val config = MPSessionReplayConfig(remoteSettingsMode = RemoteSettingsMode.DISABLED)

        val enabledResult = RemoteSettingsResult(isRecordingEnabled = true, sdkConfig = null)
        val disabledResult = RemoteSettingsResult(isRecordingEnabled = false, sdkConfig = null)

        assertTrue(SessionReplayManager.applyRemoteSettings(config, enabledResult)?.isRecordingEnabled == true)
        assertFalse(SessionReplayManager.applyRemoteSettings(config, disabledResult)?.isRecordingEnabled == true)
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

        val resolvedSettings =SessionReplayManager.applyRemoteSettings(config, result)

        // Should apply remote value
        assertEquals(config.copy(recordingSessionsPercent = 10.0), resolvedSettings?.config)
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

        val resolvedSettings =SessionReplayManager.applyRemoteSettings(config, result)

        // STRICT mode should return null when API fails, disabling SDK initialization
        assertNull(resolvedSettings)
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

        val resolvedSettings =SessionReplayManager.applyRemoteSettings(config, result)

        // STRICT mode should return null when sdk_config is missing, disabling SDK initialization
        assertNull(resolvedSettings)
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

        val resolvedSettings =SessionReplayManager.applyRemoteSettings(config, result)

        // Should use init value when remote value is null
        assertEquals(80.0, resolvedSettings?.config?.recordingSessionsPercent)
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

        val resolvedSettings =SessionReplayManager.applyRemoteSettings(config, result)

        // Should use init value when remote value is invalid
        assertEquals(50.0, resolvedSettings?.config?.recordingSessionsPercent)
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
        assertEquals(0.0, SessionReplayManager.applyRemoteSettings(config, result0)?.config?.recordingSessionsPercent)

        // Test 100.0 (valid boundary)
        val result100 = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = SdkConfig(recordSessionsPercent = 100.0)
        )
        assertEquals(100.0, SessionReplayManager.applyRemoteSettings(config, result100)?.config?.recordingSessionsPercent)
    }

    @Test
    fun testStrictModeIncludesEventTriggers() {
        val config = MPSessionReplayConfig(remoteSettingsMode = RemoteSettingsMode.STRICT)
        val triggers = mapOf("Purchase" to RecordingEventTrigger(percentage = 50.0))
        val result = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = SdkConfig(recordingEventTriggers = triggers)
        )

        val resolvedSettings =SessionReplayManager.applyRemoteSettings(config, result)

        assertEquals(triggers, resolvedSettings?.recordingEventTriggers)
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

        val resolvedSettings =SessionReplayManager.applyRemoteSettings(config, result)

        // Should apply remote value
        assertEquals(config.copy(recordingSessionsPercent = 25.0), resolvedSettings?.config)
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

        val resolvedSettings =SessionReplayManager.applyRemoteSettings(config, result)

        // FALLBACK mode should keep original config
        assertEquals(config, resolvedSettings?.config)
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

        val resolvedSettings =SessionReplayManager.applyRemoteSettings(config, result)

        // Should use cached value
        assertEquals(config.copy(recordingSessionsPercent = 50.0), resolvedSettings?.config)
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

        val resolvedSettings =SessionReplayManager.applyRemoteSettings(config, result)

        // Should keep original config when sdk_config is missing
        assertEquals(config, resolvedSettings?.config)
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

        val resolvedSettings =SessionReplayManager.applyRemoteSettings(config, result)

        // Should use init value when remote value is null
        assertEquals(60.0, resolvedSettings?.config?.recordingSessionsPercent)
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

        val resolvedSettings =SessionReplayManager.applyRemoteSettings(config, result)

        // Should use init value when remote value is invalid
        assertEquals(40.0, resolvedSettings?.config?.recordingSessionsPercent)
    }

    @Test
    fun testFallbackModeIncludesEventTriggers() {
        val config = MPSessionReplayConfig(remoteSettingsMode = RemoteSettingsMode.FALLBACK)
        val triggers = mapOf("Checkout" to RecordingEventTrigger(percentage = 100.0))
        val result = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = SdkConfig(recordingEventTriggers = triggers)
        )

        val resolvedSettings =SessionReplayManager.applyRemoteSettings(config, result)

        assertEquals(triggers, resolvedSettings?.recordingEventTriggers)
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

        val resolvedSettings =SessionReplayManager.applyRemoteSettings(config, result)

        // DISABLED mode should ignore even cached config
        assertEquals(config, resolvedSettings?.config)
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

        val resolvedSettings =SessionReplayManager.applyRemoteSettings(config, result)

        // DISABLED mode should return resolved (not null like STRICT)
        assertNotNull(resolvedSettings)
        assertEquals(config, resolvedSettings?.config)
    }

    @Test
    fun testDisabledModeIgnoresEventTriggers() {
        val config = MPSessionReplayConfig(remoteSettingsMode = RemoteSettingsMode.DISABLED)
        val triggers = mapOf("Test Event" to RecordingEventTrigger(percentage = 100.0))
        val result = RemoteSettingsResult(
            isRecordingEnabled = true,
            sdkConfig = SdkConfig(recordingEventTriggers = triggers)
        )

        val resolvedSettings =SessionReplayManager.applyRemoteSettings(config, result)

        // DISABLED mode should return null for event triggers
        assertNull(resolvedSettings?.recordingEventTriggers)
    }

    // --- Edge Case Tests ---

    @Test
    fun testAllModesHandleEmptySdkConfig() {
        val initConfig = MPSessionReplayConfig(recordingSessionsPercent = 75.0)
        val emptySdkConfig = SdkConfig() // sdk_config : { config : {} }

        // STRICT: sdkConfig object exists, so SDK initializes with init value
        val strictConfig = initConfig.copy(remoteSettingsMode = RemoteSettingsMode.STRICT)
        val strictResult = RemoteSettingsResult(isRecordingEnabled = true, sdkConfig = emptySdkConfig)
        assertEquals(75.0, SessionReplayManager.applyRemoteSettings(strictConfig, strictResult)?.config?.recordingSessionsPercent)

        // FALLBACK: uses init value
        val fallbackConfig = initConfig.copy(remoteSettingsMode = RemoteSettingsMode.FALLBACK)
        val fallbackResult = RemoteSettingsResult(isRecordingEnabled = true, sdkConfig = emptySdkConfig)
        assertEquals(75.0, SessionReplayManager.applyRemoteSettings(fallbackConfig, fallbackResult)?.config?.recordingSessionsPercent)

        // DISABLED: always uses init value
        val disabledConfig = initConfig.copy(remoteSettingsMode = RemoteSettingsMode.DISABLED)
        val disabledResult = RemoteSettingsResult(isRecordingEnabled = true, sdkConfig = emptySdkConfig)
        assertEquals(75.0, SessionReplayManager.applyRemoteSettings(disabledConfig, disabledResult)?.config?.recordingSessionsPercent)
    }

    @Test
    fun testIsRecordingEnabledAlwaysPreserved() {
        // isRecordingEnabled should be preserved in the resolved result regardless of mode
        val config = MPSessionReplayConfig()

        val enabledResult = RemoteSettingsResult(isRecordingEnabled = true, sdkConfig = SdkConfig())
        val disabledResult = RemoteSettingsResult(isRecordingEnabled = false, sdkConfig = SdkConfig())

        // STRICT mode
        val strictConfig = config.copy(remoteSettingsMode = RemoteSettingsMode.STRICT)
        assertTrue(SessionReplayManager.applyRemoteSettings(strictConfig, enabledResult)?.isRecordingEnabled == true)
        assertFalse(SessionReplayManager.applyRemoteSettings(strictConfig, disabledResult)?.isRecordingEnabled == true)

        // FALLBACK mode
        val fallbackConfig = config.copy(remoteSettingsMode = RemoteSettingsMode.FALLBACK)
        assertTrue(SessionReplayManager.applyRemoteSettings(fallbackConfig, enabledResult)?.isRecordingEnabled == true)
        assertFalse(SessionReplayManager.applyRemoteSettings(fallbackConfig, disabledResult)?.isRecordingEnabled == true)

        // DISABLED mode
        val disabledConfig = config.copy(remoteSettingsMode = RemoteSettingsMode.DISABLED)
        assertTrue(SessionReplayManager.applyRemoteSettings(disabledConfig, enabledResult)?.isRecordingEnabled == true)
        assertFalse(SessionReplayManager.applyRemoteSettings(disabledConfig, disabledResult)?.isRecordingEnabled == true)
    }
}
