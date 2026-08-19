package com.mixpanel.android.autocapture;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mixpanel.android.util.MPLog;

/**
 * Default {@link ElementIdExtractor} used when the host app does not supply its own.
 *
 * <p>Resolution priority:
 * <ol>
 *   <li><b>React Native {@code nativeID}</b> — React Native stores the JS-side {@code nativeID}
 *       prop as a view tag keyed by the {@code view_tag_native_id} resource id. The id is looked up
 *       dynamically so the SDK does not need a compile-time dependency on React Native.</li>
 *   <li><b>Android resource id</b> — the entry name of {@code view.getId()}
 *       (e.g. {@code checkout_button} for {@code @+id/checkout_button}).</li>
 *   <li><b>Content description</b> — only when the view is important for accessibility, so
 *       framework-derived descriptions (which may contain user data) are not reported.</li>
 *   <li><b>Anonymous fallback</b> — {@code <SimpleClassName>_<hashCode>}.</li>
 * </ol>
 *
 * <p>Every step is defensive: any exception thrown while resolving an identifier is swallowed and
 * resolution continues with the next step, so this class can never crash the host app.
 */
final class DefaultElementIdExtractor implements ElementIdExtractor {

    private static final String TAG = "MP.DefaultElementIdExtractor";

    /** Resource entry name React Native uses to store the {@code nativeID} prop as a view tag. */
    private static final String RN_NATIVE_ID_TAG = "view_tag_native_id";

    /** Cached resolved resource id for {@link #RN_NATIVE_ID_TAG}: 0 means "absent". */
    private static volatile int sNativeIdTagRes = -1;

    static final DefaultElementIdExtractor INSTANCE = new DefaultElementIdExtractor();

    private DefaultElementIdExtractor() {
    }

    @Override
    @NonNull
    public String extractElementId(@NonNull View view) {
        String reactNativeId = resolveReactNativeId(view);
        if (reactNativeId != null) {
            return reactNativeId;
        }

        String resourceName = resolveResourceEntryName(view);
        if (resourceName != null) {
            return resourceName;
        }

        String contentDescription = resolveContentDescription(view);
        if (contentDescription != null) {
            return contentDescription;
        }

        return anonymousId(view);
    }

    /**
     * Returns the React Native {@code nativeID} prop for the view, or null when absent.
     *
     * <p>React Native writes the prop into the view's tag map under the
     * {@code R.id.view_tag_native_id} resource declared by the React Native library. The resource
     * id is resolved at runtime (and cached) so apps without React Native pay only a single lookup.
     */
    @Nullable
    private static String resolveReactNativeId(@NonNull View view) {
        try {
            int tagId = sNativeIdTagRes;
            if (tagId == -1) {
                tagId = view.getResources().getIdentifier(
                        RN_NATIVE_ID_TAG, "id", view.getContext().getPackageName());
                sNativeIdTagRes = tagId;
            }
            if (tagId != 0) {
                Object tag = view.getTag(tagId);
                if (tag instanceof String) {
                    String nativeId = (String) tag;
                    if (!nativeId.isEmpty()) {
                        return nativeId;
                    }
                }
            }
        } catch (Exception e) {
            MPLog.d(TAG, "Unable to resolve React Native nativeID", e);
        }
        return null;
    }

    /**
     * Returns the entry name of the view's resource id (e.g. {@code checkout_button}), or null when
     * the view has no id or the id cannot be resolved to a name (common for generated ids).
     */
    @Nullable
    private static String resolveResourceEntryName(@NonNull View view) {
        try {
            int id = view.getId();
            if (id != View.NO_ID) {
                String resourceName = view.getResources().getResourceEntryName(id);
                if (resourceName != null && !resourceName.isEmpty()) {
                    return resourceName;
                }
            }
        } catch (Exception e) {
            // Resources#getResourceEntryName throws NotFoundException for runtime-generated ids.
            MPLog.d(TAG, "Unable to resolve resource entry name", e);
        }
        return null;
    }

    /**
     * Returns the view's content description when the developer intentionally exposed it.
     *
     * <p>Frameworks such as React Native auto-derive a content description from child text even for
     * views marked {@code accessible={false}}, and that text may contain sensitive information.
     * Requiring {@link View#isImportantForAccessibility()} keeps derived text out of the payload.
     */
    @Nullable
    private static String resolveContentDescription(@NonNull View view) {
        try {
            if (view.isImportantForAccessibility()) {
                CharSequence contentDescription = view.getContentDescription();
                if (contentDescription != null && contentDescription.length() > 0) {
                    return contentDescription.toString();
                }
            }
        } catch (Exception e) {
            MPLog.d(TAG, "Unable to resolve content description", e);
        }
        return null;
    }

    /**
     * Returns the anonymous, PII-free identifier used when nothing else resolves:
     * {@code <SimpleClassName>_<hashCode>} (e.g. {@code AppCompatButton_3f2a1b}).
     */
    @NonNull
    static String anonymousId(@NonNull View view) {
        return view.getClass().getSimpleName() + "_" + Integer.toHexString(view.hashCode());
    }
}
