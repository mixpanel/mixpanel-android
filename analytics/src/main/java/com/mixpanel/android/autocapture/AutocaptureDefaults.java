package com.mixpanel.android.autocapture;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

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
     * Builds a stable, PII-free description of where a view sits in the hierarchy, used as the input
     * to the anonymous {@code $el_id} hash.
     *
     * <p>Format: the view's own class, then each ancestor with the child index that leads back down
     * to the previous element, up to {@link #MAX_HIERARCHY_DEPTH} levels:
     *
     * <pre>{@code Button@FrameLayout[0]/ScrollView[2]/LinearLayout[1]}</pre>
     *
     * <p>Only class names and sibling indices — never text — so it cannot carry user data, and it is
     * identical across launches for the same layout, which an identity hash is not.
     */
    @NonNull
    static String structuralPath(@NonNull View view) {
        StringBuilder path = new StringBuilder(view.getClass().getSimpleName()).append('@');

        View current = view;
        ViewParent parent = view.getParent();
        int depth = 0;
        while (parent instanceof ViewGroup && depth < MAX_HIERARCHY_DEPTH) {
            ViewGroup group = (ViewGroup) parent;
            if (depth > 0) {
                path.append('/');
            }
            path.append(group.getClass().getSimpleName())
                    .append('[')
                    .append(group.indexOfChild(current))
                    .append(']');
            current = group;
            parent = group.getParent();
            depth++;
        }

        if (depth == 0) {
            // Detached view, or a root with no ViewGroup parent: nothing structural to describe.
            path.append("detached");
        }
        return path.toString();
    }

    private AutocaptureDefaults() {
        // Prevent instantiation
    }
}
