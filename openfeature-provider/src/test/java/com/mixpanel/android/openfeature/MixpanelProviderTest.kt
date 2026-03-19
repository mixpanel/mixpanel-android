package com.mixpanel.android.openfeature

import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.android.mpmetrics.MixpanelFlagVariant
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.Reason
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.exceptions.ErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.eq

class MixpanelProviderTest {

    private lateinit var mockFlags: MixpanelAPI.Flags
    private lateinit var provider: MixpanelProvider

    @Before
    fun setUp() {
        mockFlags = mock(MixpanelAPI.Flags::class.java)
        `when`(mockFlags.areFlagsReady()).thenReturn(true)
        provider = MixpanelProvider(mockFlags)
    }

    // --- Metadata ---

    @Test
    fun `metadata name is mixpanel-provider`() {
        assertEquals("mixpanel-provider", provider.metadata.name)
    }

    // --- Boolean evaluation ---

    @Test
    fun `resolves boolean flag to true`() {
        setupFlag("bool-flag", true)
        val result = provider.getBooleanEvaluation("bool-flag", false, ImmutableContext())
        assertEquals(true, result.value)
        assertEquals(Reason.STATIC.toString(), result.reason)
        assertNull(result.errorCode)
    }

    @Test
    fun `resolves boolean flag to false`() {
        setupFlag("bool-flag", false)
        val result = provider.getBooleanEvaluation("bool-flag", true, ImmutableContext())
        assertEquals(false, result.value)
        assertEquals(Reason.STATIC.toString(), result.reason)
    }

    // --- String evaluation ---

    @Test
    fun `resolves string flag`() {
        setupFlag("string-flag", "hello")
        val result = provider.getStringEvaluation("string-flag", "default", ImmutableContext())
        assertEquals("hello", result.value)
        assertEquals(Reason.STATIC.toString(), result.reason)
        assertNull(result.errorCode)
    }

    // --- Integer evaluation ---

    @Test
    fun `resolves integer flag`() {
        setupFlag("int-flag", 42)
        val result = provider.getIntegerEvaluation("int-flag", 0, ImmutableContext())
        assertEquals(42, result.value)
        assertEquals(Reason.STATIC.toString(), result.reason)
        assertNull(result.errorCode)
    }

    @Test
    fun `resolves integer flag from long value`() {
        setupFlag("int-flag", 42L)
        val result = provider.getIntegerEvaluation("int-flag", 0, ImmutableContext())
        assertEquals(42, result.value)
        assertEquals(Reason.STATIC.toString(), result.reason)
    }

    @Test
    fun `resolves integer flag from double with no fractional part`() {
        setupFlag("int-flag", 42.0)
        val result = provider.getIntegerEvaluation("int-flag", 0, ImmutableContext())
        assertEquals(42, result.value)
        assertEquals(Reason.STATIC.toString(), result.reason)
    }

    // --- Double evaluation ---

    @Test
    fun `resolves double flag`() {
        setupFlag("double-flag", 3.14)
        val result = provider.getDoubleEvaluation("double-flag", 0.0, ImmutableContext())
        assertEquals(3.14, result.value!!, 0.001)
        assertEquals(Reason.STATIC.toString(), result.reason)
        assertNull(result.errorCode)
    }

    @Test
    fun `resolves double flag from integer value`() {
        setupFlag("double-flag", 42)
        val result = provider.getDoubleEvaluation("double-flag", 0.0, ImmutableContext())
        assertEquals(42.0, result.value!!, 0.001)
        assertEquals(Reason.STATIC.toString(), result.reason)
    }

    // --- Object evaluation ---

    @Test
    fun `resolves object flag with string value`() {
        setupFlag("obj-flag", "hello")
        val defaultValue = Value.Null
        val result = provider.getObjectEvaluation("obj-flag", defaultValue, ImmutableContext())
        assertEquals(Value.String("hello"), result.value)
        assertEquals(Reason.STATIC.toString(), result.reason)
        assertNull(result.errorCode)
    }

    @Test
    fun `resolves object flag with boolean value`() {
        setupFlag("obj-flag", true)
        val defaultValue = Value.Null
        val result = provider.getObjectEvaluation("obj-flag", defaultValue, ImmutableContext())
        assertEquals(Value.Boolean(true), result.value)
        assertEquals(Reason.STATIC.toString(), result.reason)
    }

    @Test
    fun `resolves object flag with integer value`() {
        setupFlag("obj-flag", 42)
        val defaultValue = Value.Null
        val result = provider.getObjectEvaluation("obj-flag", defaultValue, ImmutableContext())
        assertEquals(Value.Integer(42), result.value)
        assertEquals(Reason.STATIC.toString(), result.reason)
    }

    @Test
    fun `resolves object flag with double value`() {
        setupFlag("obj-flag", 3.14)
        val defaultValue = Value.Null
        val result = provider.getObjectEvaluation("obj-flag", defaultValue, ImmutableContext())
        assertEquals(Value.Double(3.14), result.value)
        assertEquals(Reason.STATIC.toString(), result.reason)
    }

    @Test
    fun `resolves object flag with null value`() {
        setupFlag("obj-flag", null)
        val defaultValue = Value.String("default")
        val result = provider.getObjectEvaluation("obj-flag", defaultValue, ImmutableContext())
        assertEquals(Value.Null, result.value)
        assertEquals(Reason.STATIC.toString(), result.reason)
    }

    // --- Error: FLAG_NOT_FOUND ---

    @Test
    fun `returns FLAG_NOT_FOUND error when flag does not exist for boolean`() {
        setupFlagNotFound("missing-flag")
        val result = provider.getBooleanEvaluation("missing-flag", true, ImmutableContext())
        assertEquals(true, result.value)
        assertEquals(ErrorCode.FLAG_NOT_FOUND, result.errorCode)
        assertEquals(Reason.ERROR.toString(), result.reason)
    }

    @Test
    fun `returns FLAG_NOT_FOUND error when flag does not exist for string`() {
        setupFlagNotFound("missing-flag")
        val result = provider.getStringEvaluation("missing-flag", "fallback", ImmutableContext())
        assertEquals("fallback", result.value)
        assertEquals(ErrorCode.FLAG_NOT_FOUND, result.errorCode)
        assertEquals(Reason.ERROR.toString(), result.reason)
    }

    @Test
    fun `returns FLAG_NOT_FOUND error when flag does not exist for integer`() {
        setupFlagNotFound("missing-flag")
        val result = provider.getIntegerEvaluation("missing-flag", 99, ImmutableContext())
        assertEquals(99, result.value)
        assertEquals(ErrorCode.FLAG_NOT_FOUND, result.errorCode)
        assertEquals(Reason.ERROR.toString(), result.reason)
    }

    @Test
    fun `returns FLAG_NOT_FOUND error when flag does not exist for double`() {
        setupFlagNotFound("missing-flag")
        val result = provider.getDoubleEvaluation("missing-flag", 1.5, ImmutableContext())
        assertEquals(1.5, result.value!!, 0.001)
        assertEquals(ErrorCode.FLAG_NOT_FOUND, result.errorCode)
        assertEquals(Reason.ERROR.toString(), result.reason)
    }

    @Test
    fun `returns FLAG_NOT_FOUND error when flag does not exist for object`() {
        setupFlagNotFound("missing-flag")
        val defaultValue = Value.String("fallback")
        val result = provider.getObjectEvaluation("missing-flag", defaultValue, ImmutableContext())
        assertEquals(defaultValue, result.value)
        assertEquals(ErrorCode.FLAG_NOT_FOUND, result.errorCode)
        assertEquals(Reason.ERROR.toString(), result.reason)
    }

    // --- Error: TYPE_MISMATCH ---

    @Test
    fun `returns TYPE_MISMATCH when boolean requested but string returned`() {
        setupFlag("string-flag", "not-a-bool")
        val result = provider.getBooleanEvaluation("string-flag", false, ImmutableContext())
        assertEquals(false, result.value)
        assertEquals(ErrorCode.TYPE_MISMATCH, result.errorCode)
        assertEquals(Reason.ERROR.toString(), result.reason)
    }

    @Test
    fun `returns TYPE_MISMATCH when string requested but boolean returned`() {
        setupFlag("bool-flag", true)
        val result = provider.getStringEvaluation("bool-flag", "default", ImmutableContext())
        assertEquals("default", result.value)
        assertEquals(ErrorCode.TYPE_MISMATCH, result.errorCode)
        assertEquals(Reason.ERROR.toString(), result.reason)
    }

    @Test
    fun `returns TYPE_MISMATCH when integer requested but string returned`() {
        setupFlag("string-flag", "not-a-number")
        val result = provider.getIntegerEvaluation("string-flag", 0, ImmutableContext())
        assertEquals(0, result.value)
        assertEquals(ErrorCode.TYPE_MISMATCH, result.errorCode)
        assertEquals(Reason.ERROR.toString(), result.reason)
    }

    @Test
    fun `returns TYPE_MISMATCH when double requested but string returned`() {
        setupFlag("string-flag", "not-a-number")
        val result = provider.getDoubleEvaluation("string-flag", 0.0, ImmutableContext())
        assertEquals(0.0, result.value!!, 0.001)
        assertEquals(ErrorCode.TYPE_MISMATCH, result.errorCode)
        assertEquals(Reason.ERROR.toString(), result.reason)
    }

    @Test
    fun `returns TYPE_MISMATCH when integer requested but double with fractional part returned`() {
        setupFlag("double-flag", 3.14)
        val result = provider.getIntegerEvaluation("double-flag", 0, ImmutableContext())
        assertEquals(0, result.value)
        assertEquals(ErrorCode.TYPE_MISMATCH, result.errorCode)
        assertEquals(Reason.ERROR.toString(), result.reason)
    }

    // --- Error: PROVIDER_NOT_READY ---

    @Test
    fun `returns PROVIDER_NOT_READY when flags are not ready for boolean`() {
        `when`(mockFlags.areFlagsReady()).thenReturn(false)
        val result = provider.getBooleanEvaluation("any-flag", true, ImmutableContext())
        assertEquals(true, result.value)
        assertEquals(ErrorCode.PROVIDER_NOT_READY, result.errorCode)
        assertEquals(Reason.ERROR.toString(), result.reason)
    }

    @Test
    fun `returns PROVIDER_NOT_READY when flags are not ready for string`() {
        `when`(mockFlags.areFlagsReady()).thenReturn(false)
        val result = provider.getStringEvaluation("any-flag", "default", ImmutableContext())
        assertEquals("default", result.value)
        assertEquals(ErrorCode.PROVIDER_NOT_READY, result.errorCode)
        assertEquals(Reason.ERROR.toString(), result.reason)
    }

    @Test
    fun `returns PROVIDER_NOT_READY when flags are not ready for integer`() {
        `when`(mockFlags.areFlagsReady()).thenReturn(false)
        val result = provider.getIntegerEvaluation("any-flag", 5, ImmutableContext())
        assertEquals(5, result.value)
        assertEquals(ErrorCode.PROVIDER_NOT_READY, result.errorCode)
        assertEquals(Reason.ERROR.toString(), result.reason)
    }

    @Test
    fun `returns PROVIDER_NOT_READY when flags are not ready for double`() {
        `when`(mockFlags.areFlagsReady()).thenReturn(false)
        val result = provider.getDoubleEvaluation("any-flag", 2.5, ImmutableContext())
        assertEquals(2.5, result.value!!, 0.001)
        assertEquals(ErrorCode.PROVIDER_NOT_READY, result.errorCode)
        assertEquals(Reason.ERROR.toString(), result.reason)
    }

    @Test
    fun `returns PROVIDER_NOT_READY when flags are not ready for object`() {
        `when`(mockFlags.areFlagsReady()).thenReturn(false)
        val defaultValue = Value.String("default")
        val result = provider.getObjectEvaluation("any-flag", defaultValue, ImmutableContext())
        assertEquals(defaultValue, result.value)
        assertEquals(ErrorCode.PROVIDER_NOT_READY, result.errorCode)
        assertEquals(Reason.ERROR.toString(), result.reason)
    }

    // --- Lifecycle ---

    @Test
    fun `initialize completes without error`() = kotlinx.coroutines.test.runTest {
        provider.initialize(ImmutableContext())
    }

    @Test
    fun `onContextSet completes without error`() = kotlinx.coroutines.test.runTest {
        provider.onContextSet(ImmutableContext(), ImmutableContext())
    }

    @Test
    fun `shutdown is a no-op`() {
        // Should not throw
        provider.shutdown()
    }

    @Test
    fun `hooks list is empty`() {
        assertEquals(emptyList<Any>(), provider.hooks)
    }

    // --- Variant key passthrough ---

    @Test
    fun `variant key from flag is included in evaluation result`() {
        setupFlag("flag", "value")
        val result = provider.getStringEvaluation("flag", "default", ImmutableContext())
        assertEquals("variant-key", result.variant)
    }

    // --- Per-evaluation context is ignored ---

    @Test
    fun `per-evaluation context is ignored and does not affect resolution`() {
        setupFlag("flag", "resolved-value")
        val context = ImmutableContext(
            targetingKey = "user-123",
            attributes = mapOf("attr" to Value.String("val"))
        )
        val result = provider.getStringEvaluation("flag", "default", context)
        assertEquals("resolved-value", result.value)
        assertEquals(Reason.STATIC.toString(), result.reason)
        assertNull(result.errorCode)
    }

    // --- Helpers ---

    /**
     * Sets up the mock so that getVariantSync returns a variant with the given value.
     * Uses Mockito's Answer to check the fallback argument and return a DIFFERENT object,
     * ensuring identity check detects the flag was found.
     */
    private fun setupFlag(flagName: String, value: Any?) {
        `when`(mockFlags.getVariantSync(eq(flagName), any(MixpanelFlagVariant::class.java)))
            .thenReturn(MixpanelFlagVariant("variant-key", value))
    }

    /**
     * Sets up the mock so that getVariantSync returns the same fallback object that was passed in,
     * simulating a flag-not-found scenario (identity check).
     */
    private fun setupFlagNotFound(flagName: String) {
        `when`(mockFlags.getVariantSync(eq(flagName), any(MixpanelFlagVariant::class.java)))
            .thenAnswer { invocation -> invocation.getArgument(1) }
    }
}
