package com.mixpanel.android.autocapture;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.node.RootForTest;
import androidx.compose.ui.semantics.Role;
import androidx.compose.ui.semantics.SemanticsActions;
import androidx.compose.ui.semantics.SemanticsConfiguration;
import androidx.compose.ui.semantics.SemanticsNode;
import androidx.compose.ui.semantics.SemanticsProperties;
import androidx.compose.ui.semantics.SemanticsPropertyKey;
import androidx.compose.ui.text.AnnotatedString;

import com.mixpanel.android.util.MPLog;

import java.util.List;

/**
 * Helper class for extracting semantics from Compose views.
 *
 * <p>This class directly uses Compose UI APIs. It requires the Compose UI library
 * at runtime. The caller (SemanticExtractor) must catch NoClassDefFoundError when
 * calling methods in this class to handle apps without Compose.
 *
 * <p>This class is only loaded when Compose is available, avoiding ClassNotFoundException
 * for apps that don't use Compose.
 */
final class ComposeSemanticHelper {

    private static final String TAG = "MP.ComposeHelper";

    /**
     * Result of Compose extraction indicating why it returned null.
     */
    enum ExtractionResult {
        /** No semantic node found at position - fallback may be appropriate */
        NOT_FOUND,
        /** Element is sensitive - do NOT fall back, block all events */
        SENSITIVE_BLOCKED,
        /** Extraction succeeded - builder is available */
        SUCCESS
    }

    /** Holds extraction result and optional builder */
    static class ExtractResult {
        final ExtractionResult result;
        final ClickEvent.Builder builder;

        private ExtractResult(ExtractionResult result, ClickEvent.Builder builder) {
            this.result = result;
            this.builder = builder;
        }

        static ExtractResult notFound() {
            return new ExtractResult(ExtractionResult.NOT_FOUND, null);
        }

        static ExtractResult blocked() {
            return new ExtractResult(ExtractionResult.SENSITIVE_BLOCKED, null);
        }

        static ExtractResult success(ClickEvent.Builder builder) {
            return new ExtractResult(ExtractionResult.SUCCESS, builder);
        }
    }

    /**
     * Extracts semantics from a Compose view at the given coordinates.
     *
     * @param view The view (must implement RootForTest)
     * @param x    Screen X coordinate
     * @param y    Screen Y coordinate
     * @return ExtractResult indicating success, not found, or blocked
     */
    @NonNull
    static ExtractResult extract(@NonNull View view, float x, float y) {
        MPLog.d(TAG, "extract() called - view: " + view.getClass().getSimpleName() +
                ", isRootForTest: " + (view instanceof RootForTest) + ", x: " + x + ", y: " + y);

        if (!(view instanceof RootForTest)) {
            MPLog.d(TAG, "View is not RootForTest, returning NOT_FOUND");
            return ExtractResult.notFound();
        }

        RootForTest root = (RootForTest) view;
        SemanticsNode rootNode = root.getSemanticsOwner().getRootSemanticsNode();
        Rect rootBounds = rootNode.getBoundsInWindow();
        MPLog.d(TAG, "Root semantics node bounds: " + rootBounds.getLeft() + "," + rootBounds.getTop() +
                " to " + rootBounds.getRight() + "," + rootBounds.getBottom());

        // Find the node at the tap position, also collecting the path for sensitivity check
        NodeSearchResult result = findNodeAtPositionWithPath(rootNode, x, y);
        if (result == null || result.node == null) {
            MPLog.d(TAG, "No Compose semantics node at position (" + x + ", " + y + ")");
            return ExtractResult.notFound();
        }

        MPLog.d(TAG, "Found node, path length: " + result.path.size());

        // Check if any node in the path (including target) is sensitive
        if (isAnySensitive(result.path)) {
            MPLog.d(TAG, "Skipping autocapture for sensitive Compose element (ancestor check)");
            return ExtractResult.blocked();
        }

        ClickEvent.Builder builder = extractFromNode(result.node, x, y);
        if (builder == null) {
            return ExtractResult.notFound();
        }
        return ExtractResult.success(builder);
    }

    /**
     * Result of node search, including the path from root to target.
     */
    private static class NodeSearchResult {
        final SemanticsNode node;
        final List<SemanticsNode> path;

        NodeSearchResult(SemanticsNode node, List<SemanticsNode> path) {
            this.node = node;
            this.path = path;
        }
    }

    /**
     * Checks if any node in the path has sensitive markers.
     */
    private static boolean isAnySensitive(@NonNull List<SemanticsNode> path) {
        for (SemanticsNode node : path) {
            SemanticsConfiguration config = node.getConfig();
            String contentDesc = getStringProperty(config, SemanticsProperties.INSTANCE.getContentDescription());
            String testTag = getStringProperty(config, SemanticsProperties.INSTANCE.getTestTag());

            if (isSensitive(contentDesc) || isSensitive(testTag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a view is a Compose root.
     */
    static boolean isComposeRoot(@NonNull View view) {
        return view instanceof RootForTest;
    }

    /**
     * Snapshot of semantic tree state for dead click detection.
     */
    static class SemanticSnapshot {
        final int nodeCount;
        final int contentHash;

        SemanticSnapshot(int nodeCount, int contentHash) {
            this.nodeCount = nodeCount;
            this.contentHash = contentHash;
        }

        /**
         * Checks if the tree has meaningfully changed.
         * Navigation is detected by significant node count increase.
         */
        boolean hasChanged(SemanticSnapshot current) {
            if (current == null) return true;

            // Content hash changed = state change (text, labels, etc.)
            if (this.contentHash != current.contentHash) {
                MPLog.d(TAG, "Snapshot: contentHash changed");
                return true;
            }

            // Node count increased by 5+ = likely navigation (new screen added)
            // During navigation animation, both screens may be present
            if (current.nodeCount >= this.nodeCount + 5) {
                MPLog.d(TAG, "Snapshot: nodeCount increased significantly (" +
                        this.nodeCount + " -> " + current.nodeCount + ")");
                return true;
            }

            // Node count decreased significantly = screen removed
            if (current.nodeCount <= this.nodeCount - 5) {
                MPLog.d(TAG, "Snapshot: nodeCount decreased significantly");
                return true;
            }

            return false;
        }
    }

    /**
     * Captures a snapshot of the semantic tree for change detection.
     */
    @Nullable
    static SemanticSnapshot captureSnapshot(@NonNull View view) {
        if (!(view instanceof RootForTest)) {
            return null;
        }

        try {
            RootForTest root = (RootForTest) view;
            SemanticsNode rootNode = root.getSemanticsOwner().getRootSemanticsNode();
            int[] result = computeTreeHash(rootNode, 0);
            MPLog.d(TAG, "Captured snapshot - nodes: " + result[0] + ", hash: " + result[1]);
            return new SemanticSnapshot(result[0], result[1]);
        } catch (Exception e) {
            MPLog.e(TAG, "Error capturing semantic snapshot", e);
            return null;
        }
    }

    /**
     * Computes node count and content hash for the semantic tree.
     * Returns [nodeCount, contentHash].
     */
    private static int[] computeTreeHash(@NonNull SemanticsNode node, int depth) {
        if (depth >= AutocaptureDefaults.MAX_RECURSION_DEPTH) {
            return new int[]{0, 0};
        }

        int nodeCount = 1;
        int hash = 17;

        SemanticsConfiguration config = node.getConfig();

        // Include text content in hash
        String text = getTextProperty(config);
        if (text != null) {
            hash = 31 * hash + text.hashCode();
        }

        // Include content description
        String contentDesc = getStringProperty(config, SemanticsProperties.INSTANCE.getContentDescription());
        if (contentDesc != null) {
            hash = 31 * hash + contentDesc.hashCode();
        }

        // Recurse into children
        List<SemanticsNode> children = node.getChildren();
        for (SemanticsNode child : children) {
            int[] childResult = computeTreeHash(child, depth + 1);
            nodeCount += childResult[0];
            hash = 31 * hash + childResult[1];
        }

        return new int[]{nodeCount, hash};
    }

    /**
     * Finds the best SemanticsNode at the given position, tracking the path from root.
     * Prefers clickable nodes over non-interactive leaf nodes.
     */
    @Nullable
    private static NodeSearchResult findNodeAtPositionWithPath(@NonNull SemanticsNode node, float x, float y) {
        List<SemanticsNode> path = new java.util.ArrayList<>();
        SemanticsNode result = findNodeAtPositionRecursive(node, x, y, path, 0);
        if (result == null) {
            return null;
        }
        return new NodeSearchResult(result, path);
    }

    @Nullable
    private static SemanticsNode findNodeAtPositionRecursive(
            @NonNull SemanticsNode node, float x, float y, @NonNull List<SemanticsNode> path, int depth) {
        if (depth >= AutocaptureDefaults.MAX_RECURSION_DEPTH) {
            return null;
        }

        Rect bounds = node.getBoundsInWindow();

        // Check if point is within bounds
        if (x < bounds.getLeft() || x > bounds.getRight() ||
            y < bounds.getTop() || y > bounds.getBottom()) {
            return null;
        }

        // Add current node to path
        path.add(node);

        // Check children (iterate in reverse for top-most first)
        List<SemanticsNode> children = node.getChildren();
        SemanticsNode bestMatch = null;
        List<SemanticsNode> bestPath = null;

        for (int i = children.size() - 1; i >= 0; i--) {
            SemanticsNode child = children.get(i);
            List<SemanticsNode> childPath = new java.util.ArrayList<>(path);
            SemanticsNode childMatch = findNodeAtPositionRecursive(child, x, y, childPath, depth + 1);
            if (childMatch != null) {
                // Prefer clickable nodes
                if (isClickable(childMatch)) {
                    path.clear();
                    path.addAll(childPath);
                    return childMatch;
                }
                if (bestMatch == null) {
                    bestMatch = childMatch;
                    bestPath = childPath;
                }
            }
        }

        // If we found a non-clickable child but current node is clickable, prefer current
        if (bestMatch != null && !isClickable(bestMatch) && isClickable(node)) {
            // Keep current path (don't update with child path)
            return node;
        }

        if (bestMatch != null && bestPath != null) {
            path.clear();
            path.addAll(bestPath);
            return bestMatch;
        }

        return node;
    }

    /**
     * Checks if a node is clickable.
     */
    private static boolean isClickable(@NonNull SemanticsNode node) {
        SemanticsConfiguration config = node.getConfig();
        return config.contains(SemanticsActions.INSTANCE.getOnClick());
    }

    /**
     * Extracts semantic information from a SemanticsNode.
     * Note: Sensitive check is done in extract() by checking the entire path.
     */
    @Nullable
    private static ClickEvent.Builder extractFromNode(@NonNull SemanticsNode node, float x, float y) {
        SemanticsConfiguration config = node.getConfig();

        String contentDesc = getStringProperty(config, SemanticsProperties.INSTANCE.getContentDescription());
        String testTag = getStringProperty(config, SemanticsProperties.INSTANCE.getTestTag());

        ClickEvent.Builder builder = new ClickEvent.Builder()
                .x(x)
                .y(y);

        // Element ID resolution (matching Android plan):
        // 1. contentDescription (from semantics { contentDescription = ... })
        // 2. testTag (from Modifier.testTag(...)) - equivalent to resource ID
        // 3. ClassName_view_<hashCode>
        String elementId = null;

        if (contentDesc != null && !contentDesc.isEmpty()) {
            elementId = contentDesc;
            builder.ariaLabel(contentDesc);
        }

        if (elementId == null && testTag != null && !testTag.isEmpty()) {
            elementId = testTag;
        }

        if (elementId == null) {
            String tagName = getTagName(config);
            elementId = tagName + "_view_" + Integer.toHexString(node.hashCode());
        }

        builder.elementId(elementId);

        String text = getTextProperty(config);

        // Tag name from role
        String tagName = getTagName(config);
        builder.tagName(tagName);

        // Text content (if not editable)
        if (text != null && !text.isEmpty() && !isEditable(config)) {
            String sanitizedText = text.length() > AutocaptureDefaults.MAX_TEXT_LENGTH
                    ? text.substring(0, AutocaptureDefaults.MAX_TEXT_LENGTH)
                    : text;
            builder.text(sanitizedText);
        }

        // Role
        String role = getRoleString(config);
        builder.role(role);

        // Interactive check
        builder.isInteractive(isInteractiveElement(config));

        MPLog.d(TAG, "Extracted Compose semantics - id: " + elementId +
                ", tag: " + tagName + ", role: " + role);

        return builder;
    }

    private static boolean isSensitive(@Nullable String value) {
        if (value == null) return false;
        return value.contains(AutocaptureDefaults.SENSITIVE_TAG) ||
               value.contains(AutocaptureDefaults.NO_TRACK_TAG);
    }

    /**
     * Gets a string property from config (handles List<AnnotatedString>).
     */
    @Nullable
    private static <T> String getStringProperty(
            @NonNull SemanticsConfiguration config,
            @NonNull SemanticsPropertyKey<T> key) {
        if (!config.contains(key)) {
            return null;
        }

        T value = config.get(key);
        if (value == null) {
            return null;
        }

        // Handle List<AnnotatedString> for ContentDescription
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (!list.isEmpty()) {
                Object first = list.get(0);
                return first != null ? first.toString() : null;
            }
            return null;
        }

        return value.toString();
    }

    /**
     * Gets text from Text property.
     */
    @Nullable
    private static String getTextProperty(@NonNull SemanticsConfiguration config) {
        SemanticsPropertyKey<List<AnnotatedString>> textKey = SemanticsProperties.INSTANCE.getText();
        if (!config.contains(textKey)) {
            return null;
        }

        List<AnnotatedString> texts = config.get(textKey);
        if (texts == null || texts.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (AnnotatedString text : texts) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(text.getText());
        }
        return sb.toString();
    }

    /**
     * Checks if element is editable (text field).
     */
    private static boolean isEditable(@NonNull SemanticsConfiguration config) {
        return config.contains(SemanticsProperties.INSTANCE.getEditableText());
    }

    /**
     * Gets tag name from role or infers from properties.
     */
    @NonNull
    private static String getTagName(@NonNull SemanticsConfiguration config) {
        SemanticsPropertyKey<Role> roleKey = SemanticsProperties.INSTANCE.getRole();
        if (config.contains(roleKey)) {
            Role role = config.get(roleKey);
            if (role != null) {
                String roleName = role.toString();
                if (roleName.contains("Button")) return "Button";
                if (roleName.contains("Checkbox")) return "Checkbox";
                if (roleName.contains("Switch")) return "Switch";
                if (roleName.contains("RadioButton")) return "RadioButton";
                if (roleName.contains("Tab")) return "Tab";
                if (roleName.contains("Slider")) return "Slider";
                if (roleName.contains("Image")) return "Image";
                if (roleName.contains("DropdownList")) return "DropdownList";
            }
        }

        // Infer from properties - check EditableText BEFORE OnClick
        // (TextFields are clickable to gain focus but should be identified as TextField)
        if (config.contains(SemanticsProperties.INSTANCE.getEditableText())) {
            return "TextField";
        }
        if (config.contains(SemanticsActions.INSTANCE.getOnClick())) {
            return "Button";
        }
        if (config.contains(SemanticsProperties.INSTANCE.getText())) {
            return "Text";
        }

        return "View";
    }

    /**
     * Gets role string for $attr-role.
     */
    @NonNull
    private static String getRoleString(@NonNull SemanticsConfiguration config) {
        SemanticsPropertyKey<Role> roleKey = SemanticsProperties.INSTANCE.getRole();
        if (config.contains(roleKey)) {
            Role role = config.get(roleKey);
            if (role != null) {
                String roleName = role.toString().toLowerCase();
                if (roleName.contains("button")) return "button";
                if (roleName.contains("checkbox")) return "checkbox";
                if (roleName.contains("switch")) return "switch";
                if (roleName.contains("radiobutton")) return "radio";
                if (roleName.contains("tab")) return "tab";
                if (roleName.contains("slider")) return "slider";
                if (roleName.contains("image")) return "img";
                if (roleName.contains("dropdownlist")) return "combobox";
            }
        }

        // Infer from properties - check EditableText BEFORE OnClick
        // (TextFields are clickable to gain focus but should be identified as textbox)
        if (config.contains(SemanticsProperties.INSTANCE.getEditableText())) {
            return "textbox";
        }
        if (config.contains(SemanticsActions.INSTANCE.getOnClick())) {
            return "button";
        }

        return "none";
    }

    /**
     * Checks if element is interactive for dead click detection.
     * Excludes elements with inherent visual feedback.
     */
    private static boolean isInteractiveElement(@NonNull SemanticsConfiguration config) {
        // Exclude editable (keyboard feedback)
        if (config.contains(SemanticsProperties.INSTANCE.getEditableText())) {
            return false;
        }

        // Exclude toggle controls (visual feedback)
        SemanticsPropertyKey<Role> roleKey = SemanticsProperties.INSTANCE.getRole();
        if (config.contains(roleKey)) {
            Role role = config.get(roleKey);
            if (role != null) {
                String roleName = role.toString().toLowerCase();
                if (roleName.contains("checkbox") ||
                    roleName.contains("switch") ||
                    roleName.contains("radiobutton") ||
                    roleName.contains("slider")) {
                    return false;
                }
            }
        }

        // Interactive if clickable
        return config.contains(SemanticsActions.INSTANCE.getOnClick());
    }

    private ComposeSemanticHelper() {
        // Prevent instantiation
    }
}
