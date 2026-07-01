package com.mixpanel.android.autocapture;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;

/**
 * Immutable data class representing a captured click event.
 *
 * <p>Contains all semantic information about the clicked element and its context.
 * This snapshot is captured immediately on click to ensure accurate attribution
 * even when views are recycled (e.g., RecyclerView, LazyColumn).
 */
final class ClickEvent {

    /** Screen X coordinate of the click. */
    public final float x;

    /** Screen Y coordinate of the click. */
    public final float y;

    /** Primary element identifier (contentDescription, resource ID, or fallback). */
    @Nullable
    public final String elementId;

    /** Element class name (e.g., "Button", "TextView"). */
    @Nullable
    public final String tagName;

    /** Accessibility label (contentDescription). */
    @Nullable
    public final String ariaLabel;

    /** Semantic role of the element (e.g., "button", "switch", "checkbox"). */
    @Nullable
    public final String role;

    /** View hierarchy string (max 5 levels, ">" separated). */
    @Nullable
    public final String elements;

    /** Timestamp when the click was captured. */
    public final long timestamp;

    /** Whether the clicked view is considered interactive (clickable/longClickable). */
    public final boolean isInteractive;

    /** Whether this click originated from a Compose element (set at construction, not affected by GC). */
    final boolean isCompose;

    /**
     * Reference to Compose root view for dead click detection.
     * Only set for clicks on Compose elements. Weak reference to avoid memory leaks.
     */
    @Nullable
    final WeakReference<View> composeRootRef;

    /**
     * Creates a new ClickEvent.
     */
    ClickEvent(
            float x,
            float y,
            @Nullable String elementId,
            @Nullable String tagName,
            @Nullable String ariaLabel,
            @Nullable String role,
            @Nullable String elements,
            long timestamp,
            boolean isInteractive,
            @Nullable View composeRoot) {
        this.x = x;
        this.y = y;
        this.elementId = elementId;
        this.tagName = tagName;
        this.ariaLabel = ariaLabel;
        this.role = role;
        this.elements = elements;
        this.timestamp = timestamp;
        this.isInteractive = isInteractive;
        this.isCompose = composeRoot != null;
        this.composeRootRef = composeRoot != null ? new WeakReference<>(composeRoot) : null;
    }

    /**
     * Returns true if this click was on a Compose element.
     */
    boolean isComposeClick() {
        return isCompose;
    }

    /**
     * Returns the Compose root view, or null if not a Compose click or view was garbage collected.
     */
    @Nullable
    View getComposeRoot() {
        return composeRootRef != null ? composeRootRef.get() : null;
    }

    /**
     * Converts this ClickEvent to a JSONObject for event tracking.
     *
     * @return A JSONObject containing all non-null properties.
     */
    @NonNull
    JSONObject toProperties() {
        JSONObject props = new JSONObject();
        try {
            props.put(AutocaptureDefaults.PROP_X, (int) x);
            props.put(AutocaptureDefaults.PROP_Y, (int) y);

            if (elementId != null) {
                props.put(AutocaptureDefaults.PROP_EL_ID, elementId);
            }
            if (tagName != null) {
                props.put(AutocaptureDefaults.PROP_EL_TAG_NAME, tagName);
            }
            if (ariaLabel != null) {
                props.put(AutocaptureDefaults.PROP_ARIA_LABEL, ariaLabel);
            }
            if (role != null) {
                props.put(AutocaptureDefaults.PROP_ROLE, role);
            }
            if (elements != null) {
                props.put(AutocaptureDefaults.PROP_ELEMENTS, elements);
            }
        } catch (JSONException e) {
            // Should not happen with these simple types
        }
        return props;
    }

    /**
     * Builder for creating {@link ClickEvent} instances.
     */
    static class Builder {
        private float x;
        private float y;
        private String elementId;
        private String tagName;
        private String ariaLabel;
        private String role;
        private String elements;
        private long timestamp;
        private boolean isInteractive;
        private View composeRoot;

        Builder() {
            this.timestamp = System.currentTimeMillis();
        }

        Builder x(float x) {
            this.x = x;
            return this;
        }

        Builder y(float y) {
            this.y = y;
            return this;
        }

        Builder elementId(@Nullable String elementId) {
            this.elementId = elementId;
            return this;
        }

        Builder tagName(@Nullable String tagName) {
            this.tagName = tagName;
            return this;
        }

        Builder ariaLabel(@Nullable String ariaLabel) {
            this.ariaLabel = ariaLabel;
            return this;
        }

        Builder role(@Nullable String role) {
            this.role = role;
            return this;
        }

        Builder elements(@Nullable String elements) {
            this.elements = elements;
            return this;
        }

        Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        Builder isInteractive(boolean isInteractive) {
            this.isInteractive = isInteractive;
            return this;
        }

        Builder composeRoot(@Nullable View composeRoot) {
            this.composeRoot = composeRoot;
            return this;
        }

        ClickEvent build() {
            return new ClickEvent(x, y, elementId, tagName, ariaLabel, role, elements, timestamp, isInteractive, composeRoot);
        }
    }
}
