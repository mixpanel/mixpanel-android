package com.mixpanel.android.openfeature;

import com.mixpanel.android.mpmetrics.MixpanelFlagVariant;

import java.lang.reflect.Field;

/**
 * Test helper that creates a MixpanelFlagVariant with a null key field.
 * This bypasses the @NonNull annotation to simulate a runtime edge case:
 * a real, network-sourced variant whose {@code key} happens to be null.
 * <p>
 * The two-arg {@link MixpanelFlagVariant#MixpanelFlagVariant(String, Object)}
 * constructor defaults {@code source} to
 * {@code Source.fallback(Reason.FLAG_NOT_FOUND)}. Since SDK-79 that source
 * value is the wrapper's authoritative fallback signal, so a
 * default-constructed variant would be dispatched as a fallback (not as a
 * real variant with a null key). We overwrite {@code source} to
 * {@code Source.network()} via reflection so the wrapper takes the
 * successful-resolution branch.
 */
class NullKeyVariantFactory {

    static MixpanelFlagVariant create(Object value) {
        MixpanelFlagVariant variant = new MixpanelFlagVariant("placeholder", value);
        try {
            Field keyField = MixpanelFlagVariant.class.getDeclaredField("key");
            keyField.setAccessible(true);
            keyField.set(variant, null);

            Field sourceField = MixpanelFlagVariant.class.getDeclaredField("source");
            sourceField.setAccessible(true);
            sourceField.set(variant, MixpanelFlagVariant.Source.network());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create null-key variant", e);
        }
        return variant;
    }
}
