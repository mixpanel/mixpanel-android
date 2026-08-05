package com.mixpanel.android.autocapture;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mixpanel.android.mpmetrics.AutocaptureOptions;
import com.mixpanel.android.util.MPLog;


/**
 * Extracts semantic information from Views and AccessibilityNodeInfo for autocapture.
 *
 * <p>Handles both traditional XML Views and Jetpack Compose views (via AccessibilityNodeProvider).
 */
final class SemanticExtractor {

    private static final String TAG = "MP.SemanticExtractor";

    // Flag to track if Compose is available (checked once at runtime)
    private static volatile Boolean composeAvailable = null;

    /**
     * Result of the single-pass hit-test descent through the view tree.
     * Captures the target view, compose root, and ancestor hierarchy in one traversal,
     * eliminating the need for separate post-hoc parent walks.
     */
    private static final class HitResult {
        /** The deepest view at the tap coordinates. */
        @NonNull final View target;
        /** The nearest Compose root (AndroidComposeView) encountered during descent, or null. */
        @Nullable final View composeRoot;
        /** Pre-built ancestor hierarchy string (top-down order), captured during descent. */
        @NonNull final String hierarchy;

        HitResult(@NonNull View target, @Nullable View composeRoot, @NonNull String hierarchy) {
            this.target = target;
            this.composeRoot = composeRoot;
            this.hierarchy = hierarchy;
        }
    }

    /**
     * Extracts semantic information from a view at the given coordinates.
     *
     * @param rootView           The root view to search within.
     * @param x                  Screen X coordinate.
     * @param y                  Screen Y coordinate.
     * @return A ClickEvent with extracted semantics, or null if no view found.
     */
    @Nullable
    static ClickEvent extract(@NonNull View rootView, float x, float y) {
        try {
            // Single-pass descent: finds target view, compose root, and hierarchy in one traversal
            HitResult hit = findTargetView(rootView, (int) x, (int) y);
            if (hit == null) {
                return null;
            }

            MPLog.d(TAG, "findTargetView result: target=" + hit.target.getClass().getSimpleName() +
                    ", composeRoot=" + (hit.composeRoot != null ? hit.composeRoot.getClass().getSimpleName() : "null"));

            if (hit.composeRoot != null) {
                ClickEvent.Builder composeResult = extractFromCompose(hit.composeRoot, x, y);
                MPLog.d(TAG, "extractFromCompose result: " + (composeResult != null ? "found" : "not found"));

                if (composeResult != null) {
                    // Build the ClickEvent and wrap in ComposeClickEvent for dead click detection
                    ClickEvent baseEvent = composeResult.build();
                    return new ComposeClickEvent(
                            baseEvent.x, baseEvent.y, baseEvent.elementId,
                            baseEvent.tagName, baseEvent.accessibleLabel,
                            baseEvent.role, baseEvent.elements,
                            baseEvent.isInteractive, hit.composeRoot);
                }

                // Only fall back to accessibility if Compose didn't find a node
                MPLog.d(TAG, "Compose node not found, falling back to accessibility");
                ClickEvent.Builder accessibilityResult = extractFromAccessibility(hit.composeRoot, x, y);
                if (accessibilityResult != null) {
                    return accessibilityResult.build();
                }
            }

            // Fall back to direct view extraction (XML views)
            ClickEvent.Builder viewResult = extractFromView(hit.target, hit.hierarchy, x, y);

            // Walk up to clickable parent if the tapped view is not interactive
            if (viewResult != null) {
                viewResult = walkUpToClickableParent(hit.target, viewResult, hit.hierarchy, x, y);
            }

            return viewResult != null ? viewResult.build() : null;
        } catch (Exception e) {
            MPLog.e(TAG, "Error extracting semantics", e);
        }

        return null;
    }

    /**
     * Extracts semantics from a Compose root using Compose's SemanticsNode API.
     */
    @Nullable
    private static ClickEvent.Builder extractFromCompose(@NonNull View composeRoot, float x, float y) {
        try {
            return ComposeSemanticHelper.extract(composeRoot, x, y);
        } catch (NoClassDefFoundError e) {
            // Compose not available at runtime
            composeAvailable = false;
            MPLog.d(TAG, "Compose semantics not available: " + e.getMessage());
            return null;
        } catch (Exception e) {
            MPLog.e(TAG, "Error extracting Compose semantics", e);
            return null;
        }
    }

    /**
     * Checks if Compose UI library is available at runtime.
     */
    private static boolean isComposeAvailable() {
        if (composeAvailable != null) {
            return composeAvailable;
        }

        try {
            Class.forName("androidx.compose.ui.node.RootForTest");
            composeAvailable = true;
        } catch (ClassNotFoundException e) {
            composeAvailable = false;
        }
        return composeAvailable;
    }

    /**
     * Extracts semantics using AccessibilityNodeProvider (for Compose views).
     */
    @Nullable
    private static ClickEvent.Builder extractFromAccessibility(@NonNull View viewWithProvider, float x, float y) {
        AccessibilityNodeProvider provider = viewWithProvider.getAccessibilityNodeProvider();
        if (provider == null) {
            return null;
        }

        MPLog.d(TAG, "Using AccessibilityNodeProvider from: " + viewWithProvider.getClass().getSimpleName());

        AccessibilityNodeInfo rootNode = null;
        AccessibilityNodeInfo targetNode = null;
        try {
            rootNode = provider.createAccessibilityNodeInfo(View.NO_ID);
            if (rootNode == null) {
                MPLog.d(TAG, "No root accessibility node from provider");
                return null;
            }

            targetNode = findNodeAtPosition(
                    rootNode, (int) x, (int) y, 0, new int[]{0});
            if (targetNode != null) {
                // Log what we found for debugging
                CharSequence className = targetNode.getClassName();
                CharSequence contentDesc = targetNode.getContentDescription();
                CharSequence text = targetNode.getText();
                String viewId = targetNode.getViewIdResourceName();
                MPLog.d(TAG, "Found accessibility node - class: " + className +
                        ", contentDesc: " + contentDesc +
                        ", text: " + text +
                        ", viewId: " + viewId +
                        ", clickable: " + targetNode.isClickable());

                return extractFromNode(targetNode, x, y);
            }

            MPLog.d(TAG, "No accessibility node found at position");
        } catch (Exception e) {
            MPLog.d(TAG, "Error extracting from accessibility", e);
        } finally {
            if (targetNode != null) {
                targetNode.recycle();
            }
            if (rootNode != null) {
                rootNode.recycle();
            }
        }

        return null;
    }

    /**
     * Recursively finds the best AccessibilityNodeInfo at the given position.
     * Prefers clickable/interactive nodes over non-interactive leaf nodes.
     *
     * @param nodeCount Mutable counter tracking total nodes visited across the
     *                  entire traversal. When {@code nodeCount[0]} exceeds
     *                  {@link AutocaptureDefaults#MAX_ACCESSIBILITY_NODES}, the
     *                  traversal stops early and returns the best match found so far.
     */
    @Nullable
    private static AccessibilityNodeInfo findNodeAtPosition(
            @NonNull AccessibilityNodeInfo node, int x, int y, int depth,
            @NonNull int[] nodeCount) {

        if (depth > AutocaptureDefaults.MAX_RECURSION_DEPTH) {
            return null;
        }

        nodeCount[0]++;
        if (nodeCount[0] > AutocaptureDefaults.MAX_ACCESSIBILITY_NODES) {
            if (nodeCount[0] == AutocaptureDefaults.MAX_ACCESSIBILITY_NODES + 1) {
                MPLog.w(TAG, "Accessibility node limit ("
                        + AutocaptureDefaults.MAX_ACCESSIBILITY_NODES
                        + ") reached during hit-test. This screen has a very complex view "
                        + "hierarchy (e.g., large list, dense dashboard). Autocapture will "
                        + "use the best match found so far. This limit prevents excessive "
                        + "main-thread work per tap.");
            }
            return null;
        }

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        if (!bounds.contains(x, y)) {
            return null;
        }

        // Check children for a more specific match
        AccessibilityNodeInfo deepestChild = null;
        int childCount = node.getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
                if (child != null) {
                    AccessibilityNodeInfo result = findNodeAtPosition(
                            child, x, y, depth + 1, nodeCount);
                    if (result != null) {
                        child.recycle();
                        child = null;
                        if (deepestChild != null) {
                            deepestChild.recycle();
                        }
                        deepestChild = result;
                        // Continue looking - we'll prefer clickable nodes
                        break;
                    }
                    child.recycle();
                    child = null;
                }
            } catch (Exception e) {
                if (child != null) {
                    child.recycle();
                }
            }
        }

        // If we found a child and it's clickable, return it
        if (deepestChild != null) {
            if (deepestChild.isClickable() || deepestChild.isLongClickable() || deepestChild.isCheckable()) {
                return deepestChild;
            }
            // Child is not interactive - check if current node is interactive
            // If so, prefer the current (parent) node for better semantics
            if (node.isClickable() || node.isLongClickable() || node.isCheckable()) {
                deepestChild.recycle();
                return AccessibilityNodeInfo.obtain(node);
            }
            // Neither is interactive, return the deepest
            return deepestChild;
        }

        // No children matched - return this node
        return AccessibilityNodeInfo.obtain(node);
    }

    /**
     * Extracts semantics from an AccessibilityNodeInfo.
     */
    @Nullable
    private static ClickEvent.Builder extractFromNode(@NonNull AccessibilityNodeInfo node, float x, float y) {
        CharSequence contentDesc = node.getContentDescription();

        // Element ID resolution for Compose:
        // 1. viewIdResourceName (from Modifier.testTag)
        // 2. contentDescription (from Modifier.semantics { contentDescription = ... })
        // 3. text content (from Text composable)
        // 4. Class name fallback
        String elementId = null;
        String accessibleLabel = null;

        // Try viewIdResourceName first (Compose testTag)
        String viewId = node.getViewIdResourceName();
        if (viewId != null && !viewId.isEmpty()) {
            // viewIdResourceName format: "package:id/name" - extract just the name
            int slashIndex = viewId.lastIndexOf('/');
            elementId = slashIndex >= 0 ? viewId.substring(slashIndex + 1) : viewId;
        }

        // Try contentDescription
        if (elementId == null && contentDesc != null && contentDesc.length() > 0) {
            elementId = contentDesc.toString();
            accessibleLabel = contentDesc.toString();
        }

        // Fallback to class name + hash
        if (elementId == null) {
            CharSequence className = node.getClassName();
            if (className != null) {
                String simpleName = getSimpleClassName(className.toString());
                elementId = simpleName + "_" + Integer.toHexString(node.hashCode());
            }
        }

        if (elementId == null) {
            elementId = "unknown_" + Integer.toHexString(node.hashCode());
        }

        ClickEvent.Builder builder = new ClickEvent.Builder(x, y, elementId);

        if (accessibleLabel != null) {
            builder.accessibleLabel(accessibleLabel);
        }

        // Tag name - for Compose, try to get a meaningful name
        CharSequence className = node.getClassName();
        if (className != null) {
            String simpleName = getSimpleClassName(className.toString());
            // Map generic Compose class names to more meaningful ones
            String tagName = mapComposeClassName(simpleName, node);
            builder.tagName(tagName);
        }

        // Role
        builder.role(inferRoleFromNode(node));

        // Build hierarchy from ancestors
        builder.elements(buildHierarchyFromNode(node));

        // Interactive check - for Compose, also check if it's a known interactive type
        builder.isInteractive(isInteractiveNode(node));

        return builder;
    }

    /**
     * Maps generic Compose class names to more meaningful tag names.
     */
    @NonNull
    private static String mapComposeClassName(@NonNull String className, @NonNull AccessibilityNodeInfo node) {
        // Compose often reports "View" as the class name
        // Try to infer a better name from the node's properties
        if ("View".equals(className)) {
            if (node.isClickable()) {
                if (node.isCheckable()) {
                    return node.isChecked() ? "Switch" : "Checkbox";
                }
                return "Button";
            }
            if (node.isEditable()) {
                return "TextField";
            }
            if (node.getText() != null && node.getText().length() > 0) {
                return "Text";
            }
        }
        return className;
    }

    /**
     * Builds hierarchy string from AccessibilityNodeInfo ancestors.
     */
    @NonNull
    private static String buildHierarchyFromNode(@NonNull AccessibilityNodeInfo node) {
        StringBuilder sb = new StringBuilder();
        AccessibilityNodeInfo current = node;
        int depth = 0;

        while (current != null && depth < AutocaptureDefaults.MAX_HIERARCHY_DEPTH) {
            CharSequence className = current.getClassName();
            if (className != null) {
                if (sb.length() > 0) {
                    sb.insert(0, AutocaptureDefaults.HIERARCHY_SEPARATOR);
                }
                sb.insert(0, getSimpleClassName(className.toString()));
            }

            AccessibilityNodeInfo parent;
            try {
                parent = current.getParent();
            } catch (IllegalStateException e) {
                // Nodes from AccessibilityNodeProvider.createAccessibilityNodeInfo()
                // are not sealed and cannot call getParent(). Return what we have.
                break;
            }
            if (current != node) {
                current.recycle();
            }
            current = parent;
            depth++;
        }

        if (current != null && current != node) {
            current.recycle();
        }

        return sb.toString();
    }

    /**
     * Checks if an AccessibilityNodeInfo represents an interactive element.
     * Excludes controls with inherent visual feedback from dead click monitoring.
     */
    private static boolean isInteractiveNode(@NonNull AccessibilityNodeInfo node) {
        // Exclude controls with inherent visual feedback
        if (node.isEditable()) {
            // TextField - keyboard appears
            return false;
        }
        if (node.isCheckable()) {
            // Switch, Checkbox - toggle animation
            return false;
        }
        CharSequence className = node.getClassName();
        if (className != null) {
            String name = className.toString();
            if (name.contains("Slider") || name.contains("SeekBar") ||
                name.contains("ProgressBar")) {
                return false;
            }
        }

        // Check if clickable/longClickable
        return node.isClickable() || node.isLongClickable();
    }

    /**
     * Extracts semantics from a traditional View.
     *
     * @param view      The target view to extract from.
     * @param hierarchy Pre-built hierarchy string from {@link #findTargetView}, or null
     *                  to build on demand (fallback for callers that don't have it).
     */
    @Nullable
    private static ClickEvent.Builder extractFromView(@NonNull View view, @Nullable String hierarchy,
                                                      float x, float y) {
        // Element ID resolution: contentDescription > resource ID > fallback
        String elementId = resolveElementId(view);
        if (elementId == null) {
            elementId = "unknown_" + Integer.toHexString(view.hashCode());
        }

        ClickEvent.Builder builder = new ClickEvent.Builder(x, y, elementId);

        // Tag name
        builder.tagName(view.getClass().getSimpleName());

        // Content description (aria-label)
        CharSequence contentDesc = view.getContentDescription();
        if (contentDesc != null && contentDesc.length() > 0) {
            builder.accessibleLabel(contentDesc.toString());
        }

        // Role
        builder.role(inferRoleFromView(view));

        // View hierarchy — use pre-built string from descent when available
        builder.elements(hierarchy != null ? hierarchy : buildHierarchyString(view));

        // Interactive check
        builder.isInteractive(isInteractive(view));

        return builder;
    }

    /**
     * Single-pass descent through the view tree that finds the deepest view at the given
     * screen coordinates while simultaneously detecting the Compose root and building
     * the ancestor hierarchy string.
     *
     * <p>This replaces three separate traversals:
     * <ol>
     *   <li>{@code findViewAtPosition} — downward hit-test (O(n), each node calls getLocationOnScreen)</li>
     *   <li>{@code findComposeRoot} — upward walk from target to find AndroidComposeView</li>
     *   <li>{@code buildHierarchyString} — upward walk to collect ancestor names</li>
     * </ol>
     *
     * <p>The {@code int[2]} array for {@link View#getLocationOnScreen} is allocated once and
     * reused across all recursive calls.
     */
    @Nullable
    private static HitResult findTargetView(@NonNull View rootView, int x, int y) {
        int[] locationBuf = new int[2];
        return findTargetView(rootView, x, y, 0, null, locationBuf);
    }

    @Nullable
    private static HitResult findTargetView(@NonNull View view, int x, int y, int depth,
                                            @Nullable View composeRoot, @NonNull int[] locationBuf) {
        if (depth >= AutocaptureDefaults.MAX_RECURSION_DEPTH) {
            return null;
        }

        if (!isViewVisible(view)) {
            return null;
        }

        view.getLocationOnScreen(locationBuf);
        int left = locationBuf[0];
        int top = locationBuf[1];
        int right = left + view.getWidth();
        int bottom = top + view.getHeight();

        if (x < left || x > right || y < top || y > bottom) {
            return null;
        }

        // Detect Compose root during descent — no separate upward walk needed
        View currentComposeRoot = composeRoot;
        if (currentComposeRoot == null && isComposeAvailable()) {
            try {
                if (ComposeSemanticHelper.isComposeRoot(view)) {
                    currentComposeRoot = view;
                }
            } catch (NoClassDefFoundError e) {
                composeAvailable = false;
            }
        }

        // Check children in reverse order (top-most first)
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                HitResult result = findTargetView(child, x, y, depth + 1,
                        currentComposeRoot, locationBuf);
                if (result != null) {
                    return result;
                }
            }
        }

        // This view is the deepest match — build hierarchy by walking up (only once, at the leaf)
        String hierarchy = buildHierarchyString(view);
        return new HitResult(view, currentComposeRoot, hierarchy);
    }

    /**
     * Returns the view's meaningful identity (contentDescription or resource ID name),
     * or null if the view has no identity and would fall back to a hash.
     *
     * <p>Resolution order:
     * 1. contentDescription (if non-empty)
     * 2. Resource ID name (R.id.xxx)
     */
    @Nullable
    private static String resolveIdentity(@NonNull View view) {
        CharSequence contentDesc = view.getContentDescription();
        if (contentDesc != null && contentDesc.length() > 0) {
            return contentDesc.toString();
        }
        int id = view.getId();
        if (id != View.NO_ID) {
            try {
                String resourceName = view.getResources().getResourceEntryName(id);
                if (resourceName != null && !resourceName.isEmpty()) {
                    return resourceName;
                }
            } catch (Exception ignored) {
                // Resource not found
            }
        }
        return null;
    }

    /**
     * Resolves the element ID according to the priority:
     * 1. contentDescription (if non-empty)
     * 2. Resource ID name (R.id.xxx)
     * 3. ClassName_<hashCode>
     */
    @NonNull
    private static String resolveElementId(@NonNull View view) {
        String identity = resolveIdentity(view);
        if (identity != null) {
            return identity;
        }
        return view.getClass().getSimpleName() + "_" + Integer.toHexString(view.hashCode());
    }

    /**
     * Builds a hierarchy string of ancestor views (max depth).
     */
    @NonNull
    private static String buildHierarchyString(@NonNull View view) {
        StringBuilder sb = new StringBuilder();
        View current = view;
        int depth = 0;

        while (current != null && depth < AutocaptureDefaults.MAX_HIERARCHY_DEPTH) {
            if (sb.length() > 0) {
                sb.insert(0, AutocaptureDefaults.HIERARCHY_SEPARATOR);
            }
            sb.insert(0, current.getClass().getSimpleName());

            if (current.getParent() instanceof View) {
                current = (View) current.getParent();
            } else {
                break;
            }
            depth++;
        }

        return sb.toString();
    }

    /**
     * Infers the semantic role from a View's class.
     */
    @Nullable
    private static String inferRoleFromView(@NonNull View view) {
        if (view instanceof Button || view instanceof ImageButton) {
            return "Button";
        }
        if (view instanceof Switch || view instanceof ToggleButton) {
            return "Switch";
        }
        if (view instanceof CheckBox) {
            return "Checkbox";
        }
        if (view instanceof RadioButton) {
            return "Radio";
        }
        if (view instanceof SeekBar) {
            return "Slider";
        }
        if (view instanceof Spinner) {
            return "ComboBox";
        }
        if (view instanceof EditText) {
            return "TextField";
        }
        if (view instanceof ImageView) {
            return "Image";
        }
        if (view instanceof TextView) {
            return "Text";
        }
        if (view.isClickable() || view.isLongClickable()) {
            return "Button";
        }
        return null;
    }

    /**
     * Infers the semantic role from an AccessibilityNodeInfo.
     */
    @Nullable
    private static String inferRoleFromNode(@NonNull AccessibilityNodeInfo node) {
        CharSequence className = node.getClassName();
        if (className == null) {
            return null;
        }

        String name = className.toString();
        if (name.contains("Button")) {
            return "Button";
        }
        if (name.contains("Switch") || name.contains("Toggle")) {
            return "Switch";
        }
        if (name.contains("CheckBox")) {
            return "Checkbox";
        }
        if (name.contains("RadioButton")) {
            return "Radio";
        }
        if (name.contains("SeekBar") || name.contains("Slider")) {
            return "Slider";
        }
        if (name.contains("Spinner")) {
            return "ComboBox";
        }
        if (name.contains("EditText")) {
            return "TextField";
        }
        if (name.contains("Image")) {
            return "Image";
        }
        if (name.contains("Text")) {
            return "Text";
        }
        if (node.isClickable() || node.isLongClickable()) {
            return "Button";
        }
        return null;
    }

    /**
     * Checks if a view is interactive for dead click detection purposes.
     *
     * <p>Note: Controls with inherent visual feedback (EditText, Switch, SeekBar)
     * are excluded because they always produce a UI response:
     * - EditText: Shows cursor, keyboard appears
     * - Switch/CompoundButton: Toggle animation and state change
     * - SeekBar: Thumb moves with drag
     */
    private static boolean isInteractive(@NonNull View view) {
        // Exclude controls with inherent visual feedback from dead click monitoring
        if (view instanceof EditText ||
            view instanceof CompoundButton ||  // Switch, CheckBox, RadioButton, ToggleButton
            view instanceof SeekBar) {
            return false;
        }

        if (view.hasOnClickListeners()) {
            return true;
        }
        if (view.isClickable() || view.isLongClickable()) {
            return true;
        }
        // Known interactive types
        return view instanceof Button ||
               view instanceof Spinner;
    }

    /**
     * Checks if a view is visible (not gone/invisible and has positive dimensions).
     */
    private static boolean isViewVisible(@NonNull View view) {
        return view.getVisibility() == View.VISIBLE &&
               view.getWidth() > 0 &&
               view.getHeight() > 0;
    }

    /**
     * Extracts the simple class name from a fully qualified class name.
     */
    @NonNull
    private static String getSimpleClassName(@NonNull String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
    }

    /**
     * When the tapped view is not interactive (not clickable/long-clickable/checkable),
     * walks up the view hierarchy to the nearest interactive ancestor and extracts
     * semantics from that ancestor instead.
     * Does not walk past the first interactive ancestor.
     *
     * <p>This matches the iOS behavior where walk-up is based on interactivity,
     * not identity. A non-interactive leaf (e.g., TextView inside a clickable
     * LinearLayout) will always resolve to its clickable parent's identity,
     * even if the leaf has its own contentDescription.
     *
     * <p>Checkable views (e.g., CheckBox, RadioButton) are treated as interactive
     * and will not trigger a walk-up, consistent with the accessibility node path.
     */
    @NonNull
    private static ClickEvent.Builder walkUpToClickableParent(
            @NonNull View tappedView, @NonNull ClickEvent.Builder original,
            @Nullable String hierarchy, float x, float y) {
        // Only walk up if the tapped view is not interactive
        if (tappedView.isClickable() || tappedView.isLongClickable() || tappedView instanceof Checkable) {
            return original;
        }

        // Walk up to nearest interactive ancestor
        ViewParent parent = tappedView.getParent();
        int depth = 0;
        while (parent instanceof View && depth < AutocaptureDefaults.MAX_ANCESTOR_SEARCH_DEPTH) {
            View ancestor = (View) parent;
            if (ancestor.isClickable() || ancestor.isLongClickable() || ancestor instanceof Checkable) {
                // Found an interactive ancestor — rebuild hierarchy from its perspective
                return extractFromView(ancestor, buildHierarchyString(ancestor), x, y);
            }
            parent = ancestor.getParent();
            depth++;
        }

        // No interactive ancestor found, return original
        return original;
    }

    private SemanticExtractor() {
        // Prevent instantiation
    }
}
