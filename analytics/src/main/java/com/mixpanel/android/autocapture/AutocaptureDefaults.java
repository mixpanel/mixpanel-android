package com.mixpanel.android.autocapture;

/**
 * Internal constants for autocapture functionality.
 *
 * <p>These values are not exposed in the public API but can be adjusted internally if needed.
 */
final class AutocaptureDefaults {

    /**
     * Maximum depth of the view hierarchy captured in {@code $elements}.
     * Limits the number of ancestor elements included in the hierarchy string.
     */
    static final int MAX_HIERARCHY_DEPTH = 5;

    /**
     * Maximum number of accessibility nodes to probe when searching for an element.
     * Prevents excessive traversal in complex UI hierarchies.
     */
    static final int MAX_ACCESSIBILITY_NODES = 500;

    /**
     * Maximum recursion depth for tree traversal operations.
     * Prevents {@code StackOverflowError} in deeply nested view/semantic trees.
     */
    static final int MAX_RECURSION_DEPTH = 20;

    /**
     * Event name for click events.
     */
    static final String EVENT_CLICK = "$mp_click";

    /**
     * Event name for rage click events.
     */
    static final String EVENT_RAGE_CLICK = "$mp_rage_click";

    /**
     * Event name for dead click events.
     */
    static final String EVENT_DEAD_CLICK = "$mp_dead_click";

    /**
     * Property name for X coordinate.
     */
    static final String PROP_X = "$x";

    /**
     * Property name for Y coordinate.
     */
    static final String PROP_Y = "$y";

    /**
     * Property name for element ID.
     */
    static final String PROP_EL_ID = "$el_id";

    /**
     * Property name for element tag name (class name).
     */
    static final String PROP_EL_TAG_NAME = "$el_tag_name";

    /**
     * Property name for accessibility label (contentDescription).
     */
    static final String PROP_ARIA_LABEL = "$attr-aria-label";

    /**
     * Property name for element role.
     */
    static final String PROP_ROLE = "$attr-role";

    /**
     * Property name for view hierarchy.
     */
    static final String PROP_ELEMENTS = "$elements";

    /**
     * Separator used in view hierarchy string.
     */
    static final String HIERARCHY_SEPARATOR = " > ";

    private AutocaptureDefaults() {
        // Prevent instantiation
    }
}
