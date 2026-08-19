package com.mixpanel.android.autocapture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a captured click event with element metadata.
 *
 * <p>Contains all semantic information about the clicked element and its context.
 * Create a {@code ClickEvent} using the {@link Builder} and pass it to
 * {@code mixpanel.getAutocapture().trackClick()} to track click events with full element metadata.
 *
 * @see ComposeClickEvent
 */
public class ClickEvent {

    /**
     * Touch X coordinate in absolute screen pixels.
     *
     * <p>The SDK captures this as {@code event.getRawX()}.
     * When tracking manually, use {@code motionEvent.getRawX()}.
     */
    public final float x;

    /**
     * Touch Y coordinate in absolute screen pixels.
     *
     * <p>The SDK captures this as {@code event.getRawY()}.
     * When tracking manually, use {@code motionEvent.getRawY()}.
     */
    public final float y;

    /**
     * A stable identifier for the tapped element, used to group clicks in analytics.
     *
     * <p>Autocapture resolves this from the View hierarchy in the following order (see
     * {@code DefaultViewElementIdExtractor}), and the same preferences apply when tracking manually:
     * <ul>
     *   <li>React Native {@code nativeID} — the JS-side prop, stored as a view tag</li>
     *   <li>Resource ID name — stable and not user-visible
     *       ({@code view.getResources().getResourceEntryName(view.getId())})</li>
     *   <li>{@code contentDescription} — only when the view is important for accessibility</li>
     *   <li>{@code <SimpleClassName>_<hashCode>} as a last resort</li>
     * </ul>
     *
     * <p>For Jetpack Compose the order is {@code Modifier.testTag("...")}, then
     * {@code contentDescription}, then {@code TagName_<hashCode>}.
     *
     * <p>Supply a
     * {@link com.mixpanel.android.mpmetrics.AutocaptureOptions.Builder#viewElementIdExtractor(ViewElementIdExtractor)}
     * (or {@code composeElementIdExtractor} for Compose)
     * to override the View-path resolution entirely — for example a custom string like
     * {@code "buy_button"} or {@code "settings_row_notifications"}.
     *
     * <p>Avoid dynamic values (e.g., adapter position, timestamp) — they prevent meaningful grouping.
     */
    @NonNull
    public final String elementId;

    /**
     * The class name or component type of the tapped element.
     *
     * <p>Examples: {@code "Button"}, {@code "MaterialCardView"}, {@code "ComposeView"}.
     * Use {@code view.getClass().getSimpleName()} to get the class name.
     * Defaults to {@code null} if not provided.
     */
    @Nullable
    public final String tagName;

    /**
     * The human-readable accessibility label of the element.
     *
     * <p>This is the text read aloud by TalkBack — typically the view's {@code contentDescription}.
     * Examples: {@code "Add to cart"}, {@code "Play video"}, {@code "Close"}.
     * Set to {@code null} if the element has no accessibility label.
     */
    @Nullable
    public final String accessibleLabel;

    /**
     * The semantic role describing what the element does.
     *
     * <p>Common values: {@code "button"}, {@code "link"}, {@code "switch"}, {@code "checkbox"},
     * {@code "slider"}, {@code "tab"}, {@code "textfield"}, {@code "image"}.
     * Set to {@code null} if the element has no specific role.
     */
    @Nullable
    public final String role;

    /**
     * View hierarchy path from the tapped element up to 5 ancestor levels, {@code ">"} separated.
     *
     * <p>Example: {@code "Button > LinearLayout > CardView > RecyclerView > FrameLayout"}.
     * Useful for identifying where in the view tree the click occurred.
     * Defaults to {@code null} if not provided.
     */
    @Nullable
    public final String elements;

    /** Whether the clicked view is considered interactive (clickable/longClickable). */
    public final boolean isInteractive;

    /**
     * Creates a new ClickEvent.
     */
    ClickEvent(
            float x,
            float y,
            @NonNull String elementId,
            @Nullable String tagName,
            @Nullable String accessibleLabel,
            @Nullable String role,
            @Nullable String elements,
            boolean isInteractive) {
        this.x = x;
        this.y = y;
        this.elementId = elementId;
        this.tagName = tagName;
        this.accessibleLabel = accessibleLabel;
        this.role = role;
        this.elements = elements;
        this.isInteractive = isInteractive;
    }

    /**
     * Converts this ClickEvent to a JSONObject for event tracking.
     *
     * @return A JSONObject containing all non-null properties.
     */
    @NonNull
    public JSONObject toProperties() {
        JSONObject props = new JSONObject();
        try {
            props.put(AutocaptureDefaults.PROP_X, (int) x);
            props.put(AutocaptureDefaults.PROP_Y, (int) y);
            props.put(AutocaptureDefaults.PROP_EL_ID, elementId);
            if (tagName != null) {
                props.put(AutocaptureDefaults.PROP_EL_TAG_NAME, tagName);
            }
            if (accessibleLabel != null) {
                props.put(AutocaptureDefaults.PROP_ARIA_LABEL, accessibleLabel);
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
     *
     * <p>Only {@code x}, {@code y}, and {@code elementId} are required.
     * All other fields default to {@code null}/{@code false}.
     *
     * <p><b>Minimal usage:</b>
     * <pre>{@code
     * ClickEvent click = new ClickEvent.Builder(150, 300, "buy_button").build();
     * mixpanel.getAutocapture().trackClick(click);
     * }</pre>
     *
     * <p><b>Full usage:</b>
     * <pre>{@code
     * ClickEvent click = new ClickEvent.Builder(motionEvent.getRawX(), motionEvent.getRawY(), "buy_button")
     *     .tagName(view.getClass().getSimpleName())
     *     .accessibleLabel(view.getContentDescription().toString())
     *     .role("button")
     *     .elements("MaterialButton > LinearLayout > CardView")
     *     .build();
     * mixpanel.getAutocapture().trackClick(click);
     * }</pre>
     */
    public static class Builder {
        private final float x;
        private final float y;
        private final String elementId;
        private String tagName;
        private String accessibleLabel;
        private String role;
        private String elements;
        private boolean isInteractive;

        /**
         * @param x         Touch X coordinate in absolute screen pixels
         * @param y         Touch Y coordinate in absolute screen pixels
         * @param elementId Stable identifier for the tapped element
         */
        public Builder(float x, float y, @NonNull String elementId) {
            this.x = x;
            this.y = y;
            this.elementId = elementId;
        }

        /**
         * @param tagName Class name of the tapped element, e.g. {@code "Button"}, {@code "MaterialCardView"}
         */
        public Builder tagName(@Nullable String tagName) {
            this.tagName = tagName;
            return this;
        }

        /**
         * @param accessibleLabel The element's accessibility label (contentDescription)
         */
        public Builder accessibleLabel(@Nullable String accessibleLabel) {
            this.accessibleLabel = accessibleLabel;
            return this;
        }

        /**
         * @param role Semantic role like {@code "button"}, {@code "switch"}, {@code "link"}
         */
        public Builder role(@Nullable String role) {
            this.role = role;
            return this;
        }

        /**
         * @param elements View hierarchy path, {@code ">"} separated (max 5 levels)
         */
        public Builder elements(@Nullable String elements) {
            this.elements = elements;
            return this;
        }

        Builder isInteractive(boolean isInteractive) {
            this.isInteractive = isInteractive;
            return this;
        }

        public ClickEvent build() {
            return new ClickEvent(x, y, elementId, tagName, accessibleLabel, role, elements, isInteractive);
        }
    }
}
