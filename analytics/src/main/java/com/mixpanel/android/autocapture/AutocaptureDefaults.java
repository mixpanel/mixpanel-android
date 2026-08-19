package com.mixpanel.android.autocapture;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
     * Maximum number of accessibility nodes to visit during hit-test traversal.
     * Limits total work per tap on complex screens (e.g., large RecyclerViews,
     * dashboards with many cards). When exceeded, the traversal returns the best
     * match found so far rather than continuing to search.
     */
    static final int MAX_ACCESSIBILITY_NODES = 200;

    /**
     * Maximum recursion depth for tree traversal operations.
     * Prevents {@code StackOverflowError} in deeply nested view/semantic trees.
     */
    static final int MAX_RECURSION_DEPTH = 20;

    /**
     * Maximum number of ancestors to walk up when searching for a clickable parent.
     * Used by walkUpToClickableParent in SemanticExtractor.
     *
     * Set to 10: covers all practical view depths in both native and React Native
     * layouts (typically 1–5 levels from leaf to Pressable), with safety margin
     * for unusual component libraries. Keeps the bound tight enough to avoid
     * walking past the intended interactive element into navigation-level containers.
     */
    static final int MAX_ANCESTOR_SEARCH_DEPTH = 10;

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

    /**
     * Class-name prefix of every view React Native manages.
     *
     * <p>React Native's {@code BaseViewManager} applies accessibility props to the views it creates,
     * and the view a tap resolves to in a React Native app is the Pressable/Touchable's own
     * {@code ReactViewGroup}, so this prefix identifies the views whose accessibility metadata
     * follows React Native's conventions rather than the platform's.
     */
    private static final String REACT_NATIVE_VIEW_PACKAGE = "com.facebook.react.";

    /**
     * Returns the view's content description only when the developer intentionally exposed it, or
     * null otherwise. Shared by {@code $el_id} resolution and the {@code $attr-aria-label} property
     * so the two can never disagree about whether a label is safe to report.
     *
     * <p>Two guards, because the two UI stacks signal intent differently:
     *
     * <ul>
     *   <li><b>{@link View#isImportantForAccessibility()}</b> — the platform's own signal. Android
     *       frameworks auto-derive a container's content description from child text; that text may
     *       contain sensitive information (account numbers, personal details), and a view the
     *       developer marked unimportant for accessibility is not an intentional label.</li>
     *   <li><b>{@link View#isFocusable()}, for React Native views only</b> — React Native expresses
     *       {@code accessible={false}} by clearing focusability and leaves the view important for
     *       accessibility with its content description intact, so importance alone does not reflect
     *       intent there. {@code accessible={true}}, and a label with no {@code accessible} prop at
     *       all, both leave the view focusable.</li>
     * </ul>
     *
     * <p>The focusability guard is deliberately scoped to React Native views: a native Android view
     * can be clickable without being focusable while still carrying a perfectly intentional
     * {@code android:contentDescription}.
     */
    @Nullable
    static String intentionalContentDescription(@NonNull View view) {
        if (!view.isImportantForAccessibility()) {
            return null;
        }
        if (isReactNativeView(view) && !view.isFocusable()) {
            return null;
        }
        CharSequence contentDescription = view.getContentDescription();
        if (contentDescription != null && contentDescription.length() > 0) {
            return contentDescription.toString();
        }
        return null;
    }

    /** Returns whether the view is one React Native created and manages. */
    static boolean isReactNativeView(@NonNull View view) {
        return view.getClass().getName().startsWith(REACT_NATIVE_VIEW_PACKAGE);
    }

    private AutocaptureDefaults() {
        // Prevent instantiation
    }
}
