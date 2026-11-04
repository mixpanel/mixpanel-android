package com.mixpanel.android.autocapture;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

/**
 * A {@link ClickEvent} that originated from a Jetpack Compose element.
 *
 * <p>Adds a weak reference to the Compose root view, used internally by
 * {@link DeadClickDetector} to capture semantic snapshots for UI-change comparison.
 *
 * <p>This class is package-private — app developers always work with the
 * {@link ClickEvent} base class and its public {@link ClickEvent.Builder}.
 */
final class ComposeClickEvent extends ClickEvent {

    @NonNull
    private final WeakReference<View> composeRootRef;

    ComposeClickEvent(
            float x,
            float y,
            @NonNull String elementId,
            @Nullable String tagName,
            @Nullable String role,
            @Nullable String elements,
            boolean isInteractive,
            @NonNull View composeRoot) {
        super(x, y, elementId, tagName, role, elements, isInteractive);
        this.composeRootRef = new WeakReference<>(composeRoot);
    }

    /**
     * Returns the Compose root view, or null if the view was garbage collected.
     */
    @Nullable
    View getComposeRoot() {
        return composeRootRef.get();
    }
}
