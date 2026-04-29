package com.mixpanel.android.openfeature;

import com.mixpanel.android.mpmetrics.MixpanelFlagVariant;

import java.lang.reflect.Field;

/**
 * Test helper that creates a MixpanelFlagVariant with a null key field.
 * This bypasses the @NonNull annotation to simulate a runtime edge case.
 */
class NullKeyVariantFactory {

    static MixpanelFlagVariant create(Object value) {
        MixpanelFlagVariant variant = new MixpanelFlagVariant("placeholder", value);
        try {
            Field keyField = MixpanelFlagVariant.class.getDeclaredField("key");
            keyField.setAccessible(true);
            keyField.set(variant, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create null-key variant", e);
        }
        return variant;
    }
}
