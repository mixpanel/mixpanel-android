package com.mixpanel.android.kotlin.builders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PropertyBuilderTest {
    @Test
    fun `build creates JSONObject with string property`() {
        val result =
            PropertyBuilder().apply {
                "name" to "John"
            }.build()

        assertEquals("John", result.getString("name"))
    }

    @Test
    fun `build creates JSONObject with multiple properties`() {
        val result =
            PropertyBuilder().apply {
                "name" to "John"
                "age" to 30
                "premium" to true
                "balance" to 99.99
            }.build()

        assertEquals("John", result.getString("name"))
        assertEquals(30, result.getInt("age"))
        assertEquals(true, result.getBoolean("premium"))
        assertEquals(99.99, result.getDouble("balance"), 0.001)
    }

    @Test
    fun `build handles null values`() {
        val result =
            PropertyBuilder().apply {
                "name" to "John"
                "nickname" to null
            }.build()

        assertEquals("John", result.getString("name"))
        assertEquals(true, result.isNull("nickname"))
    }

    @Test
    fun `toMap returns map with correct properties`() {
        val result =
            PropertyBuilder().apply {
                "key1" to "value1"
                "key2" to 42
            }.toMap()

        assertEquals("value1", result["key1"])
        assertEquals(42, result["key2"])
    }

    @Test
    fun `toMap handles null values`() {
        val result =
            PropertyBuilder().apply {
                "key" to null
            }.toMap()

        assertNull(result["key"])
        assertEquals(true, result.containsKey("key"))
    }

    @Test
    fun `properties function creates JSONObject`() {
        val result =
            properties {
                "item" to "Premium"
                "price" to 9.99
            }

        assertEquals("Premium", result.getString("item"))
        assertEquals(9.99, result.getDouble("price"), 0.001)
    }
}
