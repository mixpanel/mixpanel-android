package com.mixpanel.android.sessionreplay

import com.mixpanel.android.sessionreplay.utils.EndPoints
import org.junit.Assert.assertEquals
import org.junit.Test

class EndPointsTest {

    @Test
    fun testRecordEndpointWithDefaultUrl() {
        val endpoint = EndPoints.record(EndPoints.DEFAULT_BASE_URL)
        assertEquals("https://api.mixpanel.com/record", endpoint)
    }

    @Test
    fun testRecordEndpointWithCustomUrl() {
        val endpoint = EndPoints.record("http://localhost:8080")
        assertEquals("http://localhost:8080/record", endpoint)
    }

    @Test
    fun testRecordEndpointWithTrailingSlash() {
        val endpoint = EndPoints.record("https://api-eu.mixpanel.com/")
        assertEquals("https://api-eu.mixpanel.com/record", endpoint)
    }

    @Test
    fun testSettingsEndpointWithDefaultUrl() {
        val endpoint = EndPoints.settings(EndPoints.DEFAULT_BASE_URL)
        assertEquals("https://api.mixpanel.com/settings", endpoint)
    }

    @Test
    fun testSettingsEndpointWithCustomUrl() {
        val endpoint = EndPoints.settings("http://localhost:8080")
        assertEquals("http://localhost:8080/settings", endpoint)
    }

    @Test
    fun testSettingsEndpointWithTrailingSlash() {
        val endpoint = EndPoints.settings("https://api-eu.mixpanel.com/")
        assertEquals("https://api-eu.mixpanel.com/settings", endpoint)
    }

    @Test
    fun testSessionReplayRedirectConstant() {
        assertEquals("https://mixpanel.com/projects/replay-redirect", EndPoints.SESSION_REPLAY_REDIRECT)
    }
}
