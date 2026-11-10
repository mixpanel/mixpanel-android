package com.mixpanel.android.sessionreplay

import com.mixpanel.android.identity.MixpanelIdentity
import com.mixpanel.android.sessionreplay.network.APIRequest
import com.mixpanel.android.sessionreplay.network.FlushRequest
import com.mixpanel.android.sessionreplay.network.Network
import com.mixpanel.android.sessionreplay.utils.EndPoints
import com.mixpanel.android.sessionreplay.utils.PayloadInfo
import com.mixpanel.android.sessionreplay.utils.ReplayIdentifiers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayIdentifiersTest {

    private class CapturingNetwork : Network() {
        var lastRequest: APIRequest? = null

        override suspend fun performAPIRequest(apiRequest: APIRequest): Result<Unit> {
            lastRequest = apiRequest
            return Result.success(Unit)
        }
    }

    private fun sentQueryItems(configure: (FlushRequest) -> Unit): Map<String, String> = runBlocking {
        val network = CapturingNetwork()
        val flushRequest = FlushRequest("token", "initialDistinctId", EndPoints.DEFAULT_BASE_URL, network)
        configure(flushRequest)
        flushRequest.sendRequest(PayloadInfo(emptyList(), 1.0, 1, "replay", 1, 1.0))
        network.lastRequest!!.queryItems!!.toMap()
    }

    // Distinct id mirrors what analytics sends on events

    @Test
    fun distinctIdIsTheUserOnceIdentified() {
        assertEquals("user_123", ReplayIdentifiers.Identity.of(MixpanelIdentity("user_123", "acme", "device_abc"))?.distinctId)
    }

    @Test
    fun distinctIdIsTheDeviceBeforeIdentify() {
        assertEquals("\$device:device_abc", ReplayIdentifiers.Identity.of(MixpanelIdentity(null, null, "device_abc"))?.distinctId)
    }

    @Test
    fun distinctIdNeedsUserOrDevice() {
        assertNull(ReplayIdentifiers.Identity.of(MixpanelIdentity(null, "acme"))?.distinctId)
        assertNull(ReplayIdentifiers.Identity.of(MixpanelIdentity("", null, ""))?.distinctId)
    }

    // What reaches the record endpoint

    @Test
    fun flushSendsUserAndCompanyInsteadOfDistinctId() {
        val identity = MixpanelIdentity("user_123", "acme", "device_abc")
        val sent = sentQueryItems { it.updateIdentifiers(ReplayIdentifiers.Identity.of(identity)!!) }

        assertFalse(sent.containsKey("distinct_id"))
        assertEquals("user_123", sent["\$user_id"])
        assertEquals("acme", sent["\$company_id"])
        assertEquals("device_abc", sent["\$device_id"])
    }

    @Test
    fun flushSendsDistinctIdWithoutACompany() {
        val identity = MixpanelIdentity("user_123", null, "device_abc")
        val sent = sentQueryItems { it.updateIdentifiers(ReplayIdentifiers.Identity.of(identity)!!) }

        assertEquals("user_123", sent["distinct_id"])
        assertEquals("user_123", sent["\$user_id"])
        assertEquals("device_abc", sent["\$device_id"])
        assertFalse(sent.containsKey("\$company_id"))
    }

    @Test
    fun flushSendsDistinctIdWithoutAUser() {
        val identity = MixpanelIdentity(null, "acme", "device_abc")
        val sent = sentQueryItems { it.updateIdentifiers(ReplayIdentifiers.Identity.of(identity)!!) }

        assertEquals("\$device:device_abc", sent["distinct_id"])
    }

    @Test
    fun flushOmitsUnsetAndEmptyKeys() {
        val identity = MixpanelIdentity(null, "", "device_abc")
        val sent = sentQueryItems { it.updateIdentifiers(ReplayIdentifiers.Identity.of(identity)!!) }

        assertEquals("\$device:device_abc", sent["distinct_id"])
        assertEquals("device_abc", sent["\$device_id"])
        assertFalse(sent.containsKey("\$user_id"))
        assertFalse(sent.containsKey("\$company_id"))
    }

    @Test
    fun distinctIdOnlyCallersSendNoIdentityKeys() {
        val sent = sentQueryItems { }

        assertEquals("initialDistinctId", sent["distinct_id"])
        assertTrue(sent.keys.none { it in setOf("\$user_id", "\$company_id", "\$device_id") })
    }

    @Test
    fun anIdentityPassedAtConstructionIsSentWithTheFirstFlush() = runBlocking {
        val network = CapturingNetwork()
        val identity = MixpanelIdentity("user_123", null, "device_abc")
        val flushRequest = FlushRequest(
            "token", ReplayIdentifiers.Identity.of(identity)!!, EndPoints.DEFAULT_BASE_URL, network
        )
        flushRequest.sendRequest(PayloadInfo(emptyList(), 1.0, 1, "replay", 1, 1.0))
        val sent = network.lastRequest!!.queryItems!!.toMap()

        assertEquals("user_123", sent["distinct_id"])
        assertEquals("user_123", sent["\$user_id"])
        assertEquals("device_abc", sent["\$device_id"])
    }

    @Test
    fun identifyingByDistinctIdClearsTheIdentity() {
        val identity = MixpanelIdentity("user_123", "acme", "device_abc")
        val sent = sentQueryItems {
            it.updateIdentifiers(ReplayIdentifiers.Identity.of(identity)!!)
            it.updateDistinctId("someone_else")
        }

        assertEquals("someone_else", sent["distinct_id"])
        assertFalse(sent.containsKey("\$company_id"))
    }
}
