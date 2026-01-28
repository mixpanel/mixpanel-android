package com.mixpanel.android.kotlin

import com.mixpanel.android.mpmetrics.MixpanelAPI
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PeopleTest {
    private lateinit var mockPeople: MixpanelAPI.People
    private lateinit var people: People

    @Before
    fun setUp() {
        mockPeople = mockk(relaxed = true)
        people = People(mockPeople)
    }

    // ========================================
    // SET PROPERTIES
    // ========================================

    @Test
    fun `setProperties DSL calls api set with correct properties`() {
        val propsSlot = slot<JSONObject>()

        people.setProperties {
            "name" to "John Doe"
            "email" to "john@example.com"
            "age" to 30
        }

        verify { mockPeople.set(capture(propsSlot)) }
        assertEquals("John Doe", propsSlot.captured.getString("name"))
        assertEquals("john@example.com", propsSlot.captured.getString("email"))
        assertEquals(30, propsSlot.captured.getInt("age"))
    }

    @Test
    fun `set single property calls api set`() {
        people.set("name", "Jane")

        verify { mockPeople.set("name", "Jane") }
    }

    @Test
    fun `setOnceProperties DSL calls api setOnce with correct properties`() {
        val propsSlot = slot<JSONObject>()

        people.setOnceProperties {
            "createdAt" to 1234567890L
        }

        verify { mockPeople.setOnce(capture(propsSlot)) }
        assertEquals(1234567890L, propsSlot.captured.getLong("createdAt"))
    }

    @Test
    fun `setOnce single property calls api setOnce`() {
        people.setOnce("firstLogin", 12345L)

        verify { mockPeople.setOnce("firstLogin", 12345L) }
    }

    // ========================================
    // INCREMENT
    // ========================================

    @Test
    fun `increment DSL calls api increment with correct values`() {
        val mapSlot = slot<Map<String, Number>>()

        people.increment {
            "loginCount" by 1
            "totalSpent" by 29.99
        }

        verify { mockPeople.increment(capture(mapSlot)) }
        assertEquals(1, mapSlot.captured["loginCount"])
        assertEquals(29.99, mapSlot.captured["totalSpent"])
    }

    @Test
    fun `increment single property calls api increment`() {
        people.increment("visits", 1.0)

        verify { mockPeople.increment("visits", 1.0) }
    }

    // ========================================
    // APPEND / REMOVE / UNION
    // ========================================

    @Test
    fun `append calls api append`() {
        people.append("tags", "premium")

        verify { mockPeople.append("tags", "premium") }
    }

    @Test
    fun `remove calls api remove`() {
        people.remove("tags", "free")

        verify { mockPeople.remove("tags", "free") }
    }

    @Test
    fun `union calls api union with JSONArray`() {
        val arraySlot = slot<JSONArray>()

        people.union("interests", listOf("kotlin", "android"))

        verify { mockPeople.union(eq("interests"), capture(arraySlot)) }
        assertEquals("kotlin", arraySlot.captured.getString(0))
        assertEquals("android", arraySlot.captured.getString(1))
    }

    @Test
    fun `unset calls api unset`() {
        people.unset("oldProperty")

        verify { mockPeople.unset("oldProperty") }
    }

    // ========================================
    // TRACK CHARGE
    // ========================================

    @Test
    fun `trackCharge with properties DSL calls api trackCharge`() {
        val propsSlot = slot<JSONObject>()

        people.trackCharge(amount = 29.99) {
            "sku" to "PREMIUM"
            "currency" to "USD"
        }

        verify { mockPeople.trackCharge(eq(29.99), capture(propsSlot)) }
        assertEquals("PREMIUM", propsSlot.captured.getString("sku"))
        assertEquals("USD", propsSlot.captured.getString("currency"))
    }

    @Test
    fun `trackCharge without properties calls api trackCharge with empty JSONObject`() {
        val propsSlot = slot<JSONObject>()

        people.trackCharge(amount = 9.99)

        verify { mockPeople.trackCharge(eq(9.99), capture(propsSlot)) }
        assertEquals(0, propsSlot.captured.length())
    }

    @Test
    fun `clearCharges calls api clearCharges`() {
        people.clearCharges()

        verify { mockPeople.clearCharges() }
    }

    // ========================================
    // MERGE
    // ========================================

    @Test
    fun `merge DSL calls api merge with correct properties`() {
        val propsSlot = slot<JSONObject>()

        people.merge("preferences") {
            "theme" to "dark"
        }

        verify { mockPeople.merge(eq("preferences"), capture(propsSlot)) }
        assertEquals("dark", propsSlot.captured.getString("theme"))
    }

    @Test
    fun `merge with JSONObject calls api merge`() {
        val value = JSONObject().put("key", "value")

        people.merge("settings", value)

        verify { mockPeople.merge("settings", value) }
    }

    // ========================================
    // DELETE / IDENTITY
    // ========================================

    @Test
    fun `deleteUser calls api deleteUser`() {
        people.deleteUser()

        verify { mockPeople.deleteUser() }
    }

    @Test
    fun `isIdentified returns api value`() {
        every { mockPeople.isIdentified } returns true

        assertEquals(true, people.isIdentified)
    }

    @Test
    fun `java returns underlying api`() {
        assertEquals(mockPeople, people.java)
    }
}
