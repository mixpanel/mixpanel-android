package com.mixpanel.android.autocapture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

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

    /** Visible text content of the element (max 100 chars, sensitive data redacted). */
    @Nullable
    public final String text;

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

    /**
     * Creates a new ClickEvent.
     */
    ClickEvent(
            float x,
            float y,
            @Nullable String elementId,
            @Nullable String tagName,
            @Nullable String text,
            @Nullable String ariaLabel,
            @Nullable String role,
            @Nullable String elements,
            long timestamp,
            boolean isInteractive) {
        this.x = x;
        this.y = y;
        this.elementId = elementId;
        this.tagName = tagName;
        this.text = text;
        this.ariaLabel = ariaLabel;
        this.role = role;
        this.elements = elements;
        this.timestamp = timestamp;
        this.isInteractive = isInteractive;
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
            if (text != null) {
                props.put(AutocaptureDefaults.PROP_EL_TEXT, text);
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
        private String text;
        private String ariaLabel;
        private String role;
        private String elements;
        private long timestamp;
        private boolean isInteractive;

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

        Builder text(@Nullable String text) {
            this.text = text;
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

        ClickEvent build() {
            return new ClickEvent(x, y, elementId, tagName, text, ariaLabel, role, elements, timestamp, isInteractive);
        }
    }
}
