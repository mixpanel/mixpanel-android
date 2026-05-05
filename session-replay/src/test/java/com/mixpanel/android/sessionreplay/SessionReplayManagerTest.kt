package com.mixpanel.android.sessionreplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReplayManagerTest {

    @Test
    fun testValidateServerUrlWithValidHttpsUrl() {
        val result = SessionReplayManager.validateServerUrl("https://api.mixpanel.com")
        assertTrue(result.isSuccess)
        assertEquals("https://api.mixpanel.com", result.getOrNull())
    }

    @Test
    fun testValidateServerUrlWithBlankUrl() {
        val result = SessionReplayManager.validateServerUrl("")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception!!.message!!.contains("must start with https://"))
    }

    @Test
    fun testValidateServerUrlWithMissingProtocol() {
        val result = SessionReplayManager.validateServerUrl("api.mixpanel.com")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception!!.message!!.contains("must start with https://"))
    }

    @Test
    fun testValidateServerUrlWithHttpUrl() {
        val result = SessionReplayManager.validateServerUrl("http://api.mixpanel.com")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception!!.message!!.contains("must start with https://"))
    }

    @Test
    fun testValidateServerUrlWithMalformedUrl() {
        val result = SessionReplayManager.validateServerUrl("https://[invalid")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception!!.message!!.contains("malformed"))
    }
}
