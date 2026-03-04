package com.mixpanel.android.mpmetrics;

import org.junit.Test;

import static org.junit.Assert.*;

public class MixpanelFlagVariantTest {

    @Test
    public void testDefaultConstructor() {
        MixpanelFlagVariant variant = new MixpanelFlagVariant();
        assertEquals("", variant.key);
        assertNull(variant.value);
        assertNull(variant.experimentID);
        assertNull(variant.isExperimentActive);
        assertNull(variant.isQATester);
    }

    @Test
    public void testKeyValueConstructor() {
        MixpanelFlagVariant variant = new MixpanelFlagVariant("control", "blue");
        assertEquals("control", variant.key);
        assertEquals("blue", variant.value);
        assertNull(variant.experimentID);
        assertNull(variant.isExperimentActive);
        assertNull(variant.isQATester);
    }

    @Test
    public void testKeyValueWithNullValue() {
        MixpanelFlagVariant variant = new MixpanelFlagVariant("control", null);
        assertEquals("control", variant.key);
        assertNull(variant.value);
    }

    @Test
    public void testKeyValueWithBooleanValue() {
        MixpanelFlagVariant variant = new MixpanelFlagVariant("enabled", true);
        assertEquals("enabled", variant.key);
        assertEquals(true, variant.value);
    }

    @Test
    public void testKeyValueWithIntegerValue() {
        MixpanelFlagVariant variant = new MixpanelFlagVariant("count", 42);
        assertEquals("count", variant.key);
        assertEquals(42, variant.value);
    }

    @Test
    public void testKeyValueWithDoubleValue() {
        MixpanelFlagVariant variant = new MixpanelFlagVariant("ratio", 3.14);
        assertEquals("ratio", variant.key);
        assertEquals(3.14, variant.value);
    }

    @Test
    public void testFallbackStringConstructor() {
        MixpanelFlagVariant variant = new MixpanelFlagVariant("default_value");
        assertEquals("default_value", variant.key);
        assertEquals("default_value", variant.value);
        assertNull(variant.experimentID);
        assertNull(variant.isExperimentActive);
        assertNull(variant.isQATester);
    }

    @Test
    public void testFallbackObjectConstructor() {
        MixpanelFlagVariant variant = new MixpanelFlagVariant((Object) 42);
        assertEquals("", variant.key);
        assertEquals(42, variant.value);
        assertNull(variant.experimentID);
    }

    @Test
    public void testFallbackObjectConstructorWithBoolean() {
        MixpanelFlagVariant variant = new MixpanelFlagVariant((Object) true);
        assertEquals("", variant.key);
        assertEquals(true, variant.value);
    }

    @Test
    public void testFullConstructor() {
        MixpanelFlagVariant variant = new MixpanelFlagVariant(
                "variant_a", "value_a", "exp_123", true, false);
        assertEquals("variant_a", variant.key);
        assertEquals("value_a", variant.value);
        assertEquals("exp_123", variant.experimentID);
        assertEquals(true, variant.isExperimentActive);
        assertEquals(false, variant.isQATester);
    }

    @Test
    public void testFullConstructorWithNulls() {
        MixpanelFlagVariant variant = new MixpanelFlagVariant(
                "variant_b", null, null, null, null);
        assertEquals("variant_b", variant.key);
        assertNull(variant.value);
        assertNull(variant.experimentID);
        assertNull(variant.isExperimentActive);
        assertNull(variant.isQATester);
    }

    @Test
    public void testFieldsArePublicFinal() {
        MixpanelFlagVariant variant = new MixpanelFlagVariant("key", "value");
        // Fields should be accessible directly (public final)
        assertNotNull(variant.key);
        assertNotNull(variant.value);
    }
}
