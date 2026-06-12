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

    /**
     * Extracts semantic information from a view at the given coordinates.
     *
     * @param rootView The root view to search within.
     * @param x        Screen X coordinate.
     * @param y        Screen Y coordinate.
     * @return A ClickEvent.Builder with extracted semantics, or null if no view found.
     */
    @Nullable
    static ClickEvent.Builder extract(@NonNull View rootView, float x, float y) {
        try {
            // First try to find view via accessibility (works for Compose)
            ClickEvent.Builder result = extractFromAccessibility(rootView, x, y);
            if (result != null) {
                return result;
            }

            // Fall back to direct view traversal (XML views)
            View targetView = findViewAtPosition(rootView, (int) x, (int) y);
            if (targetView != null) {
                return extractFromView(targetView, x, y);
            }
        } catch (Exception e) {
            MPLog.e(TAG, "Error extracting semantics", e);
        }

        return null;
    }

    /**
     * Extracts semantics using AccessibilityNodeProvider (for Compose views).
     */
    @Nullable
    private static ClickEvent.Builder extractFromAccessibility(@NonNull View rootView, float x, float y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return null;
        }

        AccessibilityNodeProvider provider = rootView.getAccessibilityNodeProvider();
        if (provider == null) {
            return null;
        }

        try {
            AccessibilityNodeInfo rootNode = provider.createAccessibilityNodeInfo(View.NO_ID);
            if (rootNode == null) {
                return null;
            }

            AccessibilityNodeInfo targetNode = findNodeAtPosition(rootNode, (int) x, (int) y, 0);
            if (targetNode != null) {
                ClickEvent.Builder builder = extractFromNode(targetNode, x, y);
                targetNode.recycle();
                rootNode.recycle();
                return builder;
            }

            rootNode.recycle();
        } catch (Exception e) {
            MPLog.d(TAG, "Error extracting from accessibility", e);
        }

        return null;
    }

    /**
     * Recursively finds the deepest AccessibilityNodeInfo at the given position.
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
        int childCount = node.getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findNodeAtPosition(child, x, y, depth + 1);
                if (result != null) {
                    child.recycle();
                    return result;
                }
                child.recycle();
            }
        }

        // This node contains the point but no child does
        // Return a copy since we need to recycle the original during traversal
        return AccessibilityNodeInfo.obtain(node);
    }

    /**
     * Extracts semantics from an AccessibilityNodeInfo.
     * Returns null if the node is marked as sensitive (mp-sensitive/mp-no-track).
     */
    @Nullable
    private static ClickEvent.Builder extractFromNode(@NonNull AccessibilityNodeInfo node, float x, float y) {
        // Check if node is marked as sensitive - block ALL events
        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null) {
            String desc = contentDesc.toString();
            if (desc.contains(AutocaptureDefaults.SENSITIVE_TAG) ||
                desc.contains(AutocaptureDefaults.NO_TRACK_TAG)) {
                CharSequence className = node.getClassName();
                String simpleName = className != null ? getSimpleClassName(className.toString()) : "Unknown";
                MPLog.d(TAG, "Skipping autocapture for sensitive element: " + simpleName);
                return null;
            }
        }

        ClickEvent.Builder builder = new ClickEvent.Builder()
                .x(x)
                .y(y);

        // Element ID (contentDescription or class name fallback)
        if (contentDesc != null && contentDesc.length() > 0) {
            builder.elementId(contentDesc.toString());
            builder.ariaLabel(contentDesc.toString());
        } else {
            CharSequence className = node.getClassName();
            if (className != null) {
                String simpleName = getSimpleClassName(className.toString());
                builder.elementId(simpleName + "_" + node.hashCode());
            }
        }

        // Tag name (class simple name)
        CharSequence className = node.getClassName();
        if (className != null) {
            builder.tagName(getSimpleClassName(className.toString()));
        }

        // Text content (with privacy filtering)
        CharSequence text = node.getText();
        if (text != null && text.length() > 0 && !isPasswordNode(node)) {
            builder.text(sanitizeText(text.toString()));
        }

        // Role
        builder.role(inferRoleFromNode(node));

        // Interactive check
        builder.isInteractive(node.isClickable() || node.isLongClickable() || node.isCheckable());

        return builder;
    }

    /**
     * Extracts semantics from a traditional View.
     * Returns null if the view is marked as sensitive (mp-sensitive/mp-no-track).
     */
    @Nullable
    private static ClickEvent.Builder extractFromView(@NonNull View view, float x, float y) {
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

        // Text content (with privacy filtering)
        String text = extractText(view);
        if (text != null && !isSensitiveInput(view)) {
            builder.text(sanitizeText(text));
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
            collectTextFromChildren(group, sb);
            if (sb.length() > 0) {
                return sb.toString();
            }
        }

        return null;
    }

    /**
     * Recursively collects text from child TextViews.
     */
    private static void collectTextFromChildren(@NonNull ViewGroup group, @NonNull StringBuilder sb) {
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
                collectTextFromChildren((ViewGroup) child, sb);
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
                if (tagStr.contains(AutocaptureDefaults.SENSITIVE_TAG) ||
                    tagStr.contains(AutocaptureDefaults.NO_TRACK_TAG)) {
                    return true;
                }
            }

            // Check contentDescription
            CharSequence contentDesc = current.getContentDescription();
            if (contentDesc != null) {
                String desc = contentDesc.toString();
                if (desc.contains(AutocaptureDefaults.SENSITIVE_TAG) ||
                    desc.contains(AutocaptureDefaults.NO_TRACK_TAG)) {
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
