package com.mixpanel.android.autocapture;

import android.graphics.Rect;
import android.os.Build;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.Button;
import android.widget.CheckBox;
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

import com.mixpanel.android.util.MPLog;

import java.util.regex.Pattern;

/**
 * Extracts semantic information from Views and AccessibilityNodeInfo for autocapture.
 *
 * <p>Handles both traditional XML Views and Jetpack Compose views (via AccessibilityNodeProvider).
 * Includes privacy protection by filtering sensitive fields and redacting PII patterns.
 */
final class SemanticExtractor {

    private static final String TAG = "MP.SemanticExtractor";

    // Regex patterns for sensitive data detection
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:4[0-9]{12}(?:[0-9]{3})?|" +           // Visa
            "5[1-5][0-9]{14}|" +                          // Mastercard
            "3[47][0-9]{13}|" +                           // Amex
            "6(?:011|5[0-9]{2})[0-9]{12}|" +              // Discover
            "(?:2131|1800|35\\d{3})\\d{11})\\b"           // JCB
    );

    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b"
    );

    // Sensitive input types that should not have text captured
    private static final int SENSITIVE_INPUT_MASK =
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD |
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD |
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD |
            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD;

    private static final int PASSWORD_MASK =
            InputType.TYPE_TEXT_VARIATION_PASSWORD |
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD |
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD |
            InputType.TYPE_NUMBER_VARIATION_PASSWORD;

    // Flag to track if Compose is available (checked once at runtime)
    private static volatile Boolean composeAvailable = null;

    /**
     * Extracts semantic information from a view at the given coordinates.
     *
     * @param rootView           The root view to search within.
     * @param x                  Screen X coordinate.
     * @param y                  Screen Y coordinate.
     * @param captureTextContent Whether to capture the text content of the element as {@code $el_text}.
     * @return A ClickEvent.Builder with extracted semantics, or null if no view found.
     */
    @Nullable
    static ClickEvent.Builder extract(@NonNull View rootView, float x, float y, boolean captureTextContent) {
        try {
            // Find the view at the tap position
            View targetView = findViewAtPosition(rootView, (int) x, (int) y);
            if (targetView == null) {
                return null;
            }

            // Check if target view (or an ancestor) is a Compose root
            // This handles Compose views by using Compose's SemanticsNode API directly
            View composeRoot = findComposeRoot(targetView);
            MPLog.d(TAG, "findComposeRoot result: " + (composeRoot != null ? composeRoot.getClass().getSimpleName() : "null") +
                    ", targetView: " + targetView.getClass().getSimpleName());
            if (composeRoot != null) {
                ComposeSemanticHelper.ExtractResult composeResult = extractFromCompose(composeRoot, x, y, captureTextContent);
                MPLog.d(TAG, "extractFromCompose result: " + composeResult.result);

                if (composeResult.result == ComposeSemanticHelper.ExtractionResult.SUCCESS) {
                    // Set Compose root for dead click detection using semantic comparison
                    composeResult.builder.composeRoot(composeRoot);
                    return composeResult.builder;
                }

                if (composeResult.result == ComposeSemanticHelper.ExtractionResult.SENSITIVE_BLOCKED) {
                    // Element is sensitive - do NOT fall back, block all events
                    MPLog.d(TAG, "Sensitive element detected, blocking all events (no fallback)");
                    return null;
                }

                // Only fall back to accessibility if Compose didn't find a node (NOT_FOUND)
                MPLog.d(TAG, "Compose node not found, falling back to accessibility");
                ClickEvent.Builder accessibilityResult = extractFromAccessibility(composeRoot, x, y, captureTextContent);
                if (accessibilityResult != null) {
                    return accessibilityResult;
                }
            }

            // Fall back to direct view extraction (XML views)
            return extractFromView(targetView, x, y, captureTextContent);
        } catch (Exception e) {
            MPLog.e(TAG, "Error extracting semantics", e);
        }

        return null;
    }

    /**
     * Finds a Compose root view (AndroidComposeView) in the view hierarchy.
     */
    @Nullable
    private static View findComposeRoot(@NonNull View view) {
        if (!isComposeAvailable()) {
            return null;
        }

        View current = view;
        while (current != null) {
            try {
                if (ComposeSemanticHelper.isComposeRoot(current)) {
                    return current;
                }
            } catch (NoClassDefFoundError e) {
                // Compose not available, mark it and stop checking
                composeAvailable = false;
                return null;
            }
            if (current.getParent() instanceof View) {
                current = (View) current.getParent();
            } else {
                break;
            }
        }
        return null;
    }

    /**
     * Extracts semantics from a Compose root using Compose's SemanticsNode API.
     */
    @NonNull
    private static ComposeSemanticHelper.ExtractResult extractFromCompose(@NonNull View composeRoot, float x, float y, boolean captureTextContent) {
        try {
            return ComposeSemanticHelper.extract(composeRoot, x, y, captureTextContent);
        } catch (NoClassDefFoundError e) {
            // Compose not available at runtime
            composeAvailable = false;
            MPLog.d(TAG, "Compose semantics not available: " + e.getMessage());
            return ComposeSemanticHelper.ExtractResult.notFound();
        } catch (Exception e) {
            MPLog.e(TAG, "Error extracting Compose semantics", e);
            return ComposeSemanticHelper.ExtractResult.notFound();
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
    private static ClickEvent.Builder extractFromAccessibility(@NonNull View viewWithProvider, float x, float y, boolean captureTextContent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return null;
        }

        AccessibilityNodeProvider provider = viewWithProvider.getAccessibilityNodeProvider();
        if (provider == null) {
            return null;
        }

        MPLog.d(TAG, "Using AccessibilityNodeProvider from: " + viewWithProvider.getClass().getSimpleName());

        try {
            AccessibilityNodeInfo rootNode = provider.createAccessibilityNodeInfo(View.NO_ID);
            if (rootNode == null) {
                MPLog.d(TAG, "No root accessibility node from provider");
                return null;
            }

            AccessibilityNodeInfo targetNode = findNodeAtPosition(rootNode, (int) x, (int) y, 0);
            if (targetNode != null) {
                // Log what we found for debugging
                CharSequence className = targetNode.getClassName();
                CharSequence contentDesc = targetNode.getContentDescription();
                CharSequence text = targetNode.getText();
                String viewId = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    viewId = targetNode.getViewIdResourceName();
                }
                MPLog.d(TAG, "Found accessibility node - class: " + className +
                        ", contentDesc: " + contentDesc +
                        ", text: " + text +
                        ", viewId: " + viewId +
                        ", clickable: " + targetNode.isClickable());

                ClickEvent.Builder builder = extractFromNode(targetNode, x, y, captureTextContent);
                targetNode.recycle();
                rootNode.recycle();
                return builder;
            }

            MPLog.d(TAG, "No accessibility node found at position");
            rootNode.recycle();
        } catch (Exception e) {
            MPLog.d(TAG, "Error extracting from accessibility", e);
        }

        return null;
    }

    /**
     * Recursively finds the best AccessibilityNodeInfo at the given position.
     * Prefers clickable/interactive nodes over non-interactive leaf nodes.
     */
    @Nullable
    private static AccessibilityNodeInfo findNodeAtPosition(
            @NonNull AccessibilityNodeInfo node, int x, int y, int depth) {

        if (depth > AutocaptureDefaults.MAX_ACCESSIBILITY_NODES) {
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
                    AccessibilityNodeInfo result = findNodeAtPosition(child, x, y, depth + 1);
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
     * Returns null if the node is marked with mp-no-track.
     */
    @Nullable
    private static ClickEvent.Builder extractFromNode(@NonNull AccessibilityNodeInfo node, float x, float y, boolean captureTextContent) {
        // Check if node is marked as sensitive - block ALL events
        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null) {
            String desc = contentDesc.toString();
            if (desc.contains(AutocaptureDefaults.NO_TRACK_TAG)) {
                MPLog.d(TAG, "Skipping autocapture for sensitive element (accessibility node)");
                return null;
            }
        }

        // Also check viewIdResourceName for sensitive markers (testTag in Compose)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            String viewId = node.getViewIdResourceName();
            if (viewId != null && viewId.contains(AutocaptureDefaults.NO_TRACK_TAG)) {
                MPLog.d(TAG, "Skipping autocapture for sensitive element (testTag): " + viewId);
                return null;
            }
        }

        ClickEvent.Builder builder = new ClickEvent.Builder()
                .x(x)
                .y(y);

        // Element ID resolution for Compose:
        // 1. viewIdResourceName (from Modifier.testTag)
        // 2. contentDescription (from Modifier.semantics { contentDescription = ... })
        // 3. text content (from Text composable)
        // 4. Class name fallback
        String elementId = null;

        // Try viewIdResourceName first (Compose testTag)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            String viewId = node.getViewIdResourceName();
            if (viewId != null && !viewId.isEmpty()) {
                // viewIdResourceName format: "package:id/name" - extract just the name
                int slashIndex = viewId.lastIndexOf('/');
                elementId = slashIndex >= 0 ? viewId.substring(slashIndex + 1) : viewId;
            }
        }

        // Try contentDescription
        if (elementId == null && contentDesc != null && contentDesc.length() > 0) {
            elementId = contentDesc.toString();
            builder.ariaLabel(contentDesc.toString());
        }

        // Try text content for buttons/clickable nodes
        CharSequence text = node.getText();
        if (elementId == null && text != null && text.length() > 0 &&
            (node.isClickable() || node.isCheckable())) {
            elementId = text.toString();
        }

        // Fallback to class name + hash
        if (elementId == null) {
            CharSequence className = node.getClassName();
            if (className != null) {
                String simpleName = getSimpleClassName(className.toString());
                elementId = simpleName + "_view_" + Integer.toHexString(node.hashCode());
            }
        }

        builder.elementId(elementId);

        // Tag name - for Compose, try to get a meaningful name
        CharSequence className = node.getClassName();
        if (className != null) {
            String simpleName = getSimpleClassName(className.toString());
            // Map generic Compose class names to more meaningful ones
            String tagName = mapComposeClassName(simpleName, node);
            builder.tagName(tagName);
        }

        // Text content (only when captureTextContent is enabled, with privacy filtering)
        if (captureTextContent && text != null && text.length() > 0 && !isPasswordNode(node)) {
            builder.text(sanitizeText(text.toString()));
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

            AccessibilityNodeInfo parent = current.getParent();
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
     * Returns null if the view is marked with mp-no-track.
     */
    @Nullable
    private static ClickEvent.Builder extractFromView(@NonNull View view, float x, float y, boolean captureTextContent) {
        // Check if view or ancestors are marked as sensitive - block ALL events
        if (isSensitiveView(view)) {
            MPLog.d(TAG, "Skipping autocapture for sensitive element: " + view.getClass().getSimpleName());
            return null;
        }

        ClickEvent.Builder builder = new ClickEvent.Builder()
                .x(x)
                .y(y);

        // Element ID resolution: contentDescription > resource ID > fallback
        String elementId = resolveElementId(view);
        builder.elementId(elementId);

        // Tag name
        builder.tagName(view.getClass().getSimpleName());

        // Content description (aria-label)
        CharSequence contentDesc = view.getContentDescription();
        if (contentDesc != null && contentDesc.length() > 0) {
            builder.ariaLabel(contentDesc.toString());
        }

        // Text content (only when captureTextContent is enabled, with privacy filtering)
        if (captureTextContent) {
            String text = extractText(view);
            if (text != null && !isSensitiveInput(view)) {
                builder.text(sanitizeText(text));
            }
        }

        // Role
        builder.role(inferRoleFromView(view));

        // View hierarchy
        builder.elements(buildHierarchyString(view));

        // Interactive check
        builder.isInteractive(isInteractive(view));

        return builder;
    }

    /**
     * Finds the deepest view at the given screen coordinates.
     */
    @Nullable
    private static View findViewAtPosition(@NonNull View view, int x, int y) {
        if (!isViewVisible(view)) {
            return null;
        }

        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int left = location[0];
        int top = location[1];
        int right = left + view.getWidth();
        int bottom = top + view.getHeight();

        if (x < left || x > right || y < top || y > bottom) {
            return null;
        }

        // Check children in reverse order (top-most first)
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                View result = findViewAtPosition(child, x, y);
                if (result != null) {
                    return result;
                }
            }
        }

        return view;
    }

    /**
     * Resolves the element ID according to the priority:
     * 1. contentDescription (if non-empty)
     * 2. Resource ID name (R.id.xxx)
     * 3. ClassName_view_<hashCode>
     */
    @NonNull
    private static String resolveElementId(@NonNull View view) {
        // 1. Try contentDescription
        CharSequence contentDesc = view.getContentDescription();
        if (contentDesc != null && contentDesc.length() > 0) {
            return contentDesc.toString();
        }

        // 2. Try resource ID name
        int id = view.getId();
        if (id != View.NO_ID) {
            try {
                String resourceName = view.getResources().getResourceEntryName(id);
                if (resourceName != null && !resourceName.isEmpty()) {
                    return resourceName;
                }
            } catch (Exception ignored) {
                // Resource not found, use fallback
            }
        }

        // 3. Fallback: ClassName_view_<hashCode>
        return view.getClass().getSimpleName() + "_view_" + Integer.toHexString(view.hashCode());
    }

    /**
     * Extracts visible text from a view, walking children if needed.
     */
    @Nullable
    private static String extractText(@NonNull View view) {
        // Direct text from TextView
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.length() > 0) {
                return text.toString();
            }
        }

        // For containers (e.g., Button with child TextView), walk children
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            StringBuilder sb = new StringBuilder();
            collectTextFromChildren(group, sb, 0);
            if (sb.length() > 0) {
                return sb.toString();
            }
        }

        return null;
    }

    /**
     * Recursively collects text from child TextViews.
     */
    private static void collectTextFromChildren(@NonNull ViewGroup group, @NonNull StringBuilder sb, int depth) {
        if (depth >= AutocaptureDefaults.MAX_RECURSION_DEPTH) {
            return;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) {
                CharSequence text = ((TextView) child).getText();
                if (text != null && text.length() > 0) {
                    if (sb.length() > 0) {
                        sb.append(" ");
                    }
                    sb.append(text);
                }
            } else if (child instanceof ViewGroup) {
                collectTextFromChildren((ViewGroup) child, sb, depth + 1);
            }
        }
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
    @NonNull
    private static String inferRoleFromView(@NonNull View view) {
        if (view instanceof Button || view instanceof ImageButton) {
            return "button";
        }
        if (view instanceof Switch || view instanceof ToggleButton) {
            return "switch";
        }
        if (view instanceof CheckBox) {
            return "checkbox";
        }
        if (view instanceof RadioButton) {
            return "radio";
        }
        if (view instanceof SeekBar) {
            return "slider";
        }
        if (view instanceof Spinner) {
            return "combobox";
        }
        if (view instanceof EditText) {
            return "textbox";
        }
        if (view instanceof ImageView) {
            return "img";
        }
        if (view instanceof TextView) {
            return "text";
        }
        if (view.isClickable() || view.isLongClickable()) {
            return "button";
        }
        return "none";
    }

    /**
     * Infers the semantic role from an AccessibilityNodeInfo.
     */
    @NonNull
    private static String inferRoleFromNode(@NonNull AccessibilityNodeInfo node) {
        CharSequence className = node.getClassName();
        if (className == null) {
            return "none";
        }

        String name = className.toString();
        if (name.contains("Button")) {
            return "button";
        }
        if (name.contains("Switch") || name.contains("Toggle")) {
            return "switch";
        }
        if (name.contains("CheckBox")) {
            return "checkbox";
        }
        if (name.contains("RadioButton")) {
            return "radio";
        }
        if (name.contains("SeekBar") || name.contains("Slider")) {
            return "slider";
        }
        if (name.contains("Spinner")) {
            return "combobox";
        }
        if (name.contains("EditText")) {
            return "textbox";
        }
        if (name.contains("Image")) {
            return "img";
        }
        if (name.contains("Text")) {
            return "text";
        }
        if (node.isClickable() || node.isLongClickable()) {
            return "button";
        }
        return "none";
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
     * Checks if a view or its ancestors are marked as sensitive.
     */
    private static boolean isSensitiveView(@Nullable View view) {
        View current = view;
        while (current != null) {
            // Check tag
            Object tag = current.getTag();
            if (tag instanceof String) {
                String tagStr = (String) tag;
                if (tagStr.contains(AutocaptureDefaults.NO_TRACK_TAG)) {
                    return true;
                }
            }

            // Check contentDescription
            CharSequence contentDesc = current.getContentDescription();
            if (contentDesc != null) {
                String desc = contentDesc.toString();
                if (desc.contains(AutocaptureDefaults.NO_TRACK_TAG)) {
                    return true;
                }
            }

            // Walk up the hierarchy
            if (current.getParent() instanceof View) {
                current = (View) current.getParent();
            } else {
                break;
            }
        }
        return false;
    }

    /**
     * Checks if an EditText has a sensitive input type (password, email, phone).
     */
    private static boolean isSensitiveInput(@NonNull View view) {
        if (!(view instanceof EditText)) {
            return false;
        }

        EditText editText = (EditText) view;
        int inputType = editText.getInputType();

        // Check for password types
        if ((inputType & PASSWORD_MASK) != 0) {
            return true;
        }

        // Check for email and phone (optionally sensitive)
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        return variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
               variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
               (inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_PHONE;
    }

    /**
     * Checks if an AccessibilityNodeInfo represents a password field.
     */
    private static boolean isPasswordNode(@NonNull AccessibilityNodeInfo node) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return node.isPassword();
        }
        return false;
    }

    /**
     * Sanitizes text by truncating and redacting sensitive patterns.
     */
    @NonNull
    private static String sanitizeText(@NonNull String text) {
        // Truncate to max length
        String result = text.length() > AutocaptureDefaults.MAX_TEXT_LENGTH
                ? text.substring(0, AutocaptureDefaults.MAX_TEXT_LENGTH)
                : text;

        // Redact credit card numbers
        result = CREDIT_CARD_PATTERN.matcher(result).replaceAll(AutocaptureDefaults.REDACTED_PLACEHOLDER);

        // Redact SSN patterns
        result = SSN_PATTERN.matcher(result).replaceAll(AutocaptureDefaults.REDACTED_PLACEHOLDER);

        return result;
    }

    /**
     * Extracts the simple class name from a fully qualified class name.
     */
    @NonNull
    private static String getSimpleClassName(@NonNull String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
    }

    private SemanticExtractor() {
        // Prevent instantiation
    }
}
