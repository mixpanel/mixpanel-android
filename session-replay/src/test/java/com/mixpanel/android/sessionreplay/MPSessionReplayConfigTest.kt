package com.mixpanel.android.sessionreplay

import com.mixpanel.android.sessionreplay.models.MPSessionReplayConfig
import com.mixpanel.android.sessionreplay.models.RemoteSettingsMode
import com.mixpanel.android.sessionreplay.sensitive_views.AutoMaskedView
import com.mixpanel.android.sessionreplay.utils.ReplaySettings
import org.junit.Assert.assertEquals
import org.junit.Test

class MPSessionReplayConfigTest {
    @Test
    fun testDefaultInitialization() {
        val config = MPSessionReplayConfig()

        val expectedConfig = MPSessionReplayConfig(
            wifiOnly = true,
            flushInterval = ReplaySettings.FLUSH_INTERVAL,
            autoStartRecording = true,
            recordingSessionsPercent = 100.0,
            autoMaskedViews = setOf(AutoMaskedView.Text, AutoMaskedView.Image, AutoMaskedView.Web),
            enableLogging = false,
            remoteSettingsMode = RemoteSettingsMode.DISABLED
        )
        assertEquals(expectedConfig, config)
    }

    @Test
    fun testCustomInitialization() {
        val config = MPSessionReplayConfig(
            wifiOnly = false,
            autoStartRecording = false,
            recordingSessionsPercent = 50.0,
            autoMaskedViews = setOf(AutoMaskedView.Image),
            flushInterval = 20,
            enableLogging = true,
            remoteSettingsMode = RemoteSettingsMode.STRICT
        )

        val expectedConfig = MPSessionReplayConfig(
            wifiOnly = false,
            autoStartRecording = false,
            recordingSessionsPercent = 50.0,
            autoMaskedViews = setOf(AutoMaskedView.Image),
            flushInterval = 20,
            enableLogging = true,
            remoteSettingsMode = RemoteSettingsMode.STRICT
        )
        assertEquals(expectedConfig, config)
    }

    @Test
    fun testToJson() {
        val config = MPSessionReplayConfig(
            wifiOnly = false,
            recordingSessionsPercent = 75.0
        )
        val json = config.toJson()

        @Suppress("ktlint:standard:max-line-length")
        val expectedJson = """{"wifiOnly":false,"flushInterval":10,"autoStartRecording":true,"recordingSessionsPercent":75.0,"autoMaskedViews":["Text","Image","Web"],"enableLogging":false,"remoteSettingsMode":"DISABLED","debugOptions":null,"serverUrl":"https://api.mixpanel.com"}"""
        assertEquals(expectedJson, json)
    }

    @Test
    fun testFromJson() {
        @Suppress("ktlint:standard:max-line-length")
        val json = """{"wifiOnly":true,"flushInterval":20,"autoStartRecording":false,"recordingSessionsPercent":25.0,"autoMaskedViews":["Text","Web"],"enableLogging":true,"remoteSettingsMode":"STRICT"}"""
        val config = MPSessionReplayConfig.fromJson(json)

        val expectedConfig = MPSessionReplayConfig(
            wifiOnly = true,
            flushInterval = 20,
            autoStartRecording = false,
            recordingSessionsPercent = 25.0,
            autoMaskedViews = setOf(AutoMaskedView.Text, AutoMaskedView.Web),
            enableLogging = true,
            remoteSettingsMode = RemoteSettingsMode.STRICT
        )
        assertEquals(expectedConfig, config)
    }
}
