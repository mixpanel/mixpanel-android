package com.mixpanel.android.sessionreplay

import com.mixpanel.android.identity.MixpanelIdentity
import com.mixpanel.android.sessionreplay.network.APIRequest
import com.mixpanel.android.sessionreplay.network.FlushRequest
import com.mixpanel.android.sessionreplay.network.Network
import com.mixpanel.android.sessionreplay.utils.EndPoints
import com.mixpanel.android.sessionreplay.utils.PayloadInfo
import com.mixpanel.android.sessionreplay.utils.ReplayIdentity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayIdentityTest {

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
        assertEquals("user_123", ReplayIdentity.distinctId(MixpanelIdentity("user_123", "acme", "device_abc")))
    }

    @Test
    fun distinctIdIsTheDeviceBeforeIdentify() {
        assertEquals("\$device:device_abc", ReplayIdentity.distinctId(MixpanelIdentity(null, null, "device_abc")))
    }

    @Test
    fun distinctIdNeedsUserOrDevice() {
        assertNull(ReplayIdentity.distinctId(MixpanelIdentity(null, "acme")))
        assertNull(ReplayIdentity.distinctId(MixpanelIdentity("", null, "")))
    }

    // What reaches the record endpoint

    @Test
    fun flushSendsUserAndCompanyInsteadOfDistinctId() {
        val identity = MixpanelIdentity("user_123", "acme", "device_abc")
        val sent = sentQueryItems { it.updateIdentity(identity, ReplayIdentity.distinctId(identity)!!) }

        assertFalse(sent.containsKey("distinct_id"))
        assertEquals("user_123", sent["\$user_id"])
        assertEquals("acme", sent["\$company_id"])
        assertEquals("device_abc", sent["\$device_id"])
    }

    @Test
    fun flushSendsDistinctIdWithoutACompany() {
        val identity = MixpanelIdentity("user_123", null, "device_abc")
        val sent = sentQueryItems { it.updateIdentity(identity, ReplayIdentity.distinctId(identity)!!) }

        assertEquals("user_123", sent["distinct_id"])
        assertEquals("user_123", sent["\$user_id"])
        assertEquals("device_abc", sent["\$device_id"])
        assertFalse(sent.containsKey("\$company_id"))
    }

    @Test
    fun flushSendsDistinctIdWithoutAUser() {
        val identity = MixpanelIdentity(null, "acme", "device_abc")
        val sent = sentQueryItems { it.updateIdentity(identity, ReplayIdentity.distinctId(identity)!!) }

        assertEquals("\$device:device_abc", sent["distinct_id"])
    }

    @Test
    fun flushOmitsUnsetAndEmptyKeys() {
        val identity = MixpanelIdentity(null, "", "device_abc")
        val sent = sentQueryItems { it.updateIdentity(identity, ReplayIdentity.distinctId(identity)!!) }

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
    fun identifyingByDistinctIdClearsTheIdentity() {
        val identity = MixpanelIdentity("user_123", "acme", "device_abc")
        val sent = sentQueryItems {
            it.updateIdentity(identity, "user_123")
            it.updateDistinctId("someone_else")
        }

        assertEquals("someone_else", sent["distinct_id"])
        assertFalse(sent.containsKey("\$company_id"))
    }
}
