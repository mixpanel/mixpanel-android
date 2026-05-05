package com.mixpanel.android.sessionreplay

import com.mixpanel.android.sessionreplay.models.MPSessionReplayConfig
import com.mixpanel.android.sessionreplay.utils.EndPoints
import org.junit.Assert.assertEquals
import org.junit.Test

class MPSessionReplayConfigServerUrlTest {

    @Test
    fun testDefaultServerUrl() {
        val config = MPSessionReplayConfig()
        assertEquals(EndPoints.DEFAULT_BASE_URL, config.serverUrl)
    }

    @Test
    fun testCustomServerUrl() {
        val config = MPSessionReplayConfig(
            serverUrl = "https://api-eu.mixpanel.com"
        )
        assertEquals("https://api-eu.mixpanel.com", config.serverUrl)
    }

    @Test
    fun testServerUrlWithTrailingSlash() {
        val config = MPSessionReplayConfig(
            serverUrl = "https://api-eu.mixpanel.com/"
        )
        // Config stores URL as-is; trailing slash is handled by recordEndpoint()
        assertEquals("https://api-eu.mixpanel.com/", config.serverUrl)
    }

    @Test
    fun testServerUrlWithWhitespace() {
        val config = MPSessionReplayConfig(
            serverUrl = "  https://api-eu.mixpanel.com  "
        )
        // Config stores URL as-is; whitespace is handled by validateServerUrl()
        assertEquals("  https://api-eu.mixpanel.com  ", config.serverUrl)
    }
}
