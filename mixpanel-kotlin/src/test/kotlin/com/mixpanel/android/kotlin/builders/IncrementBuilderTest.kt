package com.mixpanel.android.kotlin.builders

import org.junit.Assert.assertEquals
import org.junit.Test

class IncrementBuilderTest {
    @Test
    fun `build creates map with integer increment`() {
        val result =
            IncrementBuilder().apply {
                "count" by 1
            }.build()

        assertEquals(1, result["count"])
    }

    @Test
    fun `build creates map with double increment`() {
        val result =
            IncrementBuilder().apply {
                "balance" by 29.99
            }.build()

        assertEquals(29.99, result["balance"])
    }

    @Test
    fun `build creates map with multiple increments`() {
        val result =
            IncrementBuilder().apply {
                "loginCount" by 1
                "totalSpent" by 29.99
                "points" by 100L
            }.build()

        assertEquals(1, result["loginCount"])
        assertEquals(29.99, result["totalSpent"])
        assertEquals(100L, result["points"])
    }

    @Test
    fun `build with negative values works`() {
        val result =
            IncrementBuilder().apply {
                "credits" by -10
            }.build()

        assertEquals(-10, result["credits"])
    }
}
