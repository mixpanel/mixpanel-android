package com.mixpanel.android.kotlin

import com.mixpanel.android.mpmetrics.MixpanelAPI
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GroupTest {
    private lateinit var mockGroup: MixpanelAPI.Group
    private lateinit var group: Group

    @Before
    fun setUp() {
        mockGroup = mockk(relaxed = true)
        group = Group(mockGroup)
    }

    // ========================================
    // SET PROPERTIES
    // ========================================

    @Test
    fun `setProperties DSL calls api set with correct properties`() {
        val propsSlot = slot<JSONObject>()

        group.setProperties {
            "name" to "Acme Inc"
            "industry" to "Technology"
            "employees" to 500
        }

        verify { mockGroup.set(capture(propsSlot)) }
        assertEquals("Acme Inc", propsSlot.captured.getString("name"))
        assertEquals("Technology", propsSlot.captured.getString("industry"))
        assertEquals(500, propsSlot.captured.getInt("employees"))
    }

    @Test
    fun `set single property calls api set`() {
        group.set("name", "Acme")

        verify { mockGroup.set("name", "Acme") }
    }

    @Test
    fun `setOnceProperties DSL calls api setOnce with correct properties`() {
        val propsSlot = slot<JSONObject>()

        group.setOnceProperties {
            "createdAt" to 1234567890L
        }

        verify { mockGroup.setOnce(capture(propsSlot)) }
        assertEquals(1234567890L, propsSlot.captured.getLong("createdAt"))
    }

    @Test
    fun `setOnce single property calls api setOnce`() {
        group.setOnce("foundedYear", 2020)

        verify { mockGroup.setOnce("foundedYear", 2020) }
    }

    // ========================================
    // REMOVE / UNION / UNSET
    // ========================================

    @Test
    fun `remove calls api remove`() {
        group.remove("tags", "startup")

        verify { mockGroup.remove("tags", "startup") }
    }

    @Test
    fun `union calls api union with JSONArray`() {
        val arraySlot = slot<JSONArray>()

        group.union("tags", listOf("enterprise", "saas"))

        verify { mockGroup.union(eq("tags"), capture(arraySlot)) }
        assertEquals("enterprise", arraySlot.captured.getString(0))
        assertEquals("saas", arraySlot.captured.getString(1))
    }

    @Test
    fun `unset calls api unset`() {
        group.unset("oldProperty")

        verify { mockGroup.unset("oldProperty") }
    }

    // ========================================
    // DELETE
    // ========================================

    @Test
    fun `deleteGroup calls api deleteGroup`() {
        group.deleteGroup()

        verify { mockGroup.deleteGroup() }
    }

    @Test
    fun `java returns underlying api`() {
        assertEquals(mockGroup, group.java)
    }
}
