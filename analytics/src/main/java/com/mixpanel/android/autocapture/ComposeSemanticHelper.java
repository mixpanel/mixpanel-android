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
     * Extracts semantics from a Compose view at the given screen coordinates.
     *
     * <p>Converts screen coordinates to window-relative coordinates before matching
     * against Compose's {@code getBoundsInWindow()}, which is necessary for correct
     * hit testing in split-screen, multi-window, or any mode where the window origin
     * is offset from the screen origin.
     *
     * @param view    The view (must implement RootForTest)
     * @param screenX Screen X coordinate (from MotionEvent.getRawX)
     * @param screenY Screen Y coordinate (from MotionEvent.getRawY)
     * @return A ClickEvent.Builder with extracted semantics, or null if no node found
     */
    @Nullable
    static ClickEvent.Builder extract(@NonNull View view, float screenX, float screenY) {
        if (!(view instanceof RootForTest)) {
            MPLog.d(TAG, "View is not RootForTest, returning null");
            return null;
        }

        // Convert screen coordinates to window-relative coordinates.
        // Compose's getBoundsInWindow() returns bounds relative to the window, not the
        // screen. In split-screen or multi-window the window origin is offset from (0,0).
        int[] windowOffset = new int[2];
        view.getLocationInWindow(windowOffset);
        int[] screenOffset = new int[2];
        view.getLocationOnScreen(screenOffset);
        float windowX = screenX - (screenOffset[0] - windowOffset[0]);
        float windowY = screenY - (screenOffset[1] - windowOffset[1]);

        RootForTest root = (RootForTest) view;
        SemanticsNode rootNode = root.getSemanticsOwner().getRootSemanticsNode();

        // Find the node using window-relative coordinates
        SemanticsNode node = findNodeAtPosition(rootNode, windowX, windowY);
        if (node == null) {
            MPLog.d(TAG, "No Compose semantics node at position (" + windowX + ", " + windowY + ")");
            return null;
        }

        // Pass original screen coordinates to extractFromNode for the ClickEvent
        return extractFromNode(node, screenX, screenY);
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
        final int contentHash;

        SemanticSnapshot(int contentHash) {
            this.contentHash = contentHash;
        }

        /**
         * Checks if the tree has meaningfully changed.
         * The content hash is structurally sensitive — computeTreeHash folds
         * child hashes sequentially, so any node addition/removal (even
         * text-less nodes) produces a different hash.
         */
        boolean hasChanged(SemanticSnapshot current) {
            if (current == null) return true;

            if (this.contentHash != current.contentHash) {
                MPLog.d(TAG, "Snapshot: contentHash changed");
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
            int hash = computeTreeHash(rootNode, 0);
            MPLog.d(TAG, "Captured snapshot - hash: " + hash);
            return new SemanticSnapshot(hash);
        } catch (Exception e) {
            MPLog.e(TAG, "Error capturing semantic snapshot", e);
            return null;
        }
    }

    /**
     * Computes a content hash for the semantic tree.
     * The hash is structurally sensitive — child hashes are folded sequentially,
     * so any node addition/removal produces a different hash.
     */
    private static int computeTreeHash(@NonNull SemanticsNode node, int depth) {
        if (depth >= AutocaptureDefaults.MAX_RECURSION_DEPTH) {
            return 0;
        }

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
            hash = 31 * hash + computeTreeHash(child, depth + 1);
        }

        return hash;
    }

    /**
     * Finds the best SemanticsNode at the given position.
     * Prefers clickable nodes over non-interactive leaf nodes.
     */
    @Nullable
    private static SemanticsNode findNodeAtPosition(@NonNull SemanticsNode node, float x, float y) {
        return findNodeAtPositionRecursive(node, x, y, 0);
    }

    @Nullable
    private static SemanticsNode findNodeAtPositionRecursive(
            @NonNull SemanticsNode node, float x, float y, int depth) {
        if (depth >= AutocaptureDefaults.MAX_RECURSION_DEPTH) {
            return null;
        }

        Rect bounds = node.getBoundsInWindow();

        // Check if point is within bounds
        if (x < bounds.getLeft() || x > bounds.getRight() ||
            y < bounds.getTop() || y > bounds.getBottom()) {
            return null;
        }

        // Check children (iterate in reverse for top-most first)
        List<SemanticsNode> children = node.getChildren();
        SemanticsNode bestMatch = null;

        for (int i = children.size() - 1; i >= 0; i--) {
            SemanticsNode child = children.get(i);
            SemanticsNode childMatch = findNodeAtPositionRecursive(child, x, y, depth + 1);
            if (childMatch != null) {
                // Prefer clickable nodes
                if (isClickable(childMatch)) {
                    return childMatch;
                }
                if (bestMatch == null) {
                    bestMatch = childMatch;
                }
            }
        }

        // If we found a non-clickable child but current node is clickable, prefer current
        if (bestMatch != null && !isClickable(bestMatch) && isClickable(node)) {
            return node;
        }

        if (bestMatch != null) {
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
     */
    @Nullable
    private static ClickEvent.Builder extractFromNode(@NonNull SemanticsNode node, float x, float y) {
        SemanticsConfiguration config = node.getConfig();

        String contentDesc = getStringProperty(config, SemanticsProperties.INSTANCE.getContentDescription());
        String testTag = getStringProperty(config, SemanticsProperties.INSTANCE.getTestTag());

        // Element ID resolution (matching Android plan):
        // 1. contentDescription (from semantics { contentDescription = ... })
        // 2. testTag (from Modifier.testTag(...)) - equivalent to resource ID
        // 3. ClassName_view_<hashCode>
        String elementId = null;
        String ariaLabel = null;

        if (contentDesc != null && !contentDesc.isEmpty()) {
            elementId = contentDesc;
            ariaLabel = contentDesc;
        }

        if (elementId == null && testTag != null && !testTag.isEmpty()) {
            elementId = testTag;
        }

        if (elementId == null) {
            String tagName = getTagName(config);
            elementId = tagName + "_view_" + Integer.toHexString(node.hashCode());
        }

        ClickEvent.Builder builder = new ClickEvent.Builder(x, y, elementId);

        if (ariaLabel != null) {
            builder.ariaLabel(ariaLabel);
        }

        // Tag name from role
        String tagName = getTagName(config);
        builder.tagName(tagName);

        // Role
        String role = getRoleString(config);
        builder.role(role);

        // Interactive check
        builder.isInteractive(isInteractiveElement(config));

        MPLog.d(TAG, "Extracted Compose semantics - id: " + elementId +
                ", tag: " + tagName + ", role: " + role);

        return builder;
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
