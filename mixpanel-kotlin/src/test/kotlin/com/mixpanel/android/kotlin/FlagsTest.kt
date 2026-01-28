package com.mixpanel.android.kotlin

import com.mixpanel.android.mpmetrics.FlagCompletionCallback
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.android.mpmetrics.MixpanelFlagVariant
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FlagsTest {
    private lateinit var mockFlags: MixpanelAPI.Flags
    private lateinit var flags: Flags

    @Before
    fun setUp() {
        mockFlags = mockk(relaxed = true)
        flags = Flags(mockFlags)
    }

    // ========================================
    // IS ENABLED
    // ========================================

    @Test
    fun `isEnabled calls api isEnabled and returns result`() =
        runTest {
            val callbackSlot = slot<FlagCompletionCallback<Boolean>>()

            every {
                mockFlags.isEnabled(eq("feature"), eq(false), capture(callbackSlot))
            } answers {
                callbackSlot.captured.onComplete(true)
            }

            val result = flags.isEnabled(name = "feature", fallback = false)

            assertEquals(true, result)
            verify { mockFlags.isEnabled("feature", false, any()) }
        }

    @Test
    fun `isEnabled with custom fallback passes fallback to api`() =
        runTest {
            val callbackSlot = slot<FlagCompletionCallback<Boolean>>()

            every {
                mockFlags.isEnabled(eq("feature"), eq(true), capture(callbackSlot))
            } answers {
                callbackSlot.captured.onComplete(true)
            }

            flags.isEnabled(name = "feature", fallback = true)

            verify { mockFlags.isEnabled("feature", true, any()) }
        }

    // ========================================
    // GET VARIANT
    // ========================================

    @Test
    fun `getVariant calls api getVariant and returns result`() =
        runTest {
            val callbackSlot = slot<FlagCompletionCallback<MixpanelFlagVariant>>()
            val expectedVariant = MixpanelFlagVariant("control", "value")

            every {
                mockFlags.getVariant(eq("experiment"), any(), capture(callbackSlot))
            } answers {
                callbackSlot.captured.onComplete(expectedVariant)
            }

            val result = flags.getVariant(name = "experiment")

            assertEquals(expectedVariant, result)
            verify { mockFlags.getVariant(eq("experiment"), any(), any()) }
        }

    @Test
    fun `getVariant with custom fallback passes fallback to api`() =
        runTest {
            val callbackSlot = slot<FlagCompletionCallback<MixpanelFlagVariant>>()
            val fallbackVariant = MixpanelFlagVariant("fallback")
            val expectedVariant = MixpanelFlagVariant("control", "value")

            every {
                mockFlags.getVariant(eq("experiment"), eq(fallbackVariant), capture(callbackSlot))
            } answers {
                callbackSlot.captured.onComplete(expectedVariant)
            }

            flags.getVariant(name = "experiment", fallback = fallbackVariant)

            verify { mockFlags.getVariant("experiment", fallbackVariant, any()) }
        }

    // ========================================
    // ARE READY / LOAD
    // ========================================

    @Test
    fun `areReady returns api areFlagsReady`() {
        every { mockFlags.areFlagsReady() } returns true

        assertEquals(true, flags.areReady)
    }

    @Test
    fun `load calls api loadFlags`() {
        flags.load()

        verify { mockFlags.loadFlags() }
    }

    @Test
    fun `java returns underlying api`() {
        assertEquals(mockFlags, flags.java)
    }
}
