package com.mixpanel.android.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MixpanelIdentityTest {

    @Test
    fun namedArgumentsMatchPositional() {
        assertEquals(
            MixpanelIdentity("user_123", "acme", "device_abc"),
            MixpanelIdentity(deviceId = "device_abc", companyId = "acme", userId = "user_123")
        )
    }

    @Test
    fun companyAndDeviceDefaultToNull() {
        val identity = MixpanelIdentity(userId = "user_123")
        assertEquals("user_123", identity.userId)
        assertNull(identity.companyId)
        assertNull(identity.deviceId)
    }

    @Test
    fun equalityCoversEveryValue() {
        val identity = MixpanelIdentity("user_123", "acme", "device_abc")
        assertEquals(MixpanelIdentity("user_123", "acme", "device_abc").hashCode(), identity.hashCode())
        assertNotEquals(MixpanelIdentity("user_123", "acme"), identity)
        assertNotEquals(MixpanelIdentity("user_123", "globex", "device_abc"), identity)
    }

    @Test
    fun toStringOmitsUnsetValues() {
        assertEquals(
            "MixpanelIdentity(userId=user_123, companyId=acme, deviceId=device_abc)",
            MixpanelIdentity("user_123", "acme", "device_abc").toString()
        )
        assertEquals("MixpanelIdentity(userId=user_123)", MixpanelIdentity("user_123").toString())
        assertEquals("MixpanelIdentity(deviceId=device_abc)", MixpanelIdentity(null, deviceId = "device_abc").toString())
    }
}
