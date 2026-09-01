package com.mixpanel.android.autocapture;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mixpanel.android.util.MPLog;

/**
 * Resolves the {@code $el_id} reported for a tapped <b>View</b> — traditional XML layouts, and
 * anything React Native renders, since React Native draws through the legacy View hierarchy.
 *
 * <p>Resolution priority:
 * <ol>
 *   <li><b>React Native {@code nativeID}</b> — React Native stores the JS-side {@code nativeID}
 *       prop as a view tag keyed by the {@code view_tag_native_id} resource id. The id is looked up
 *       dynamically so the SDK does not need a compile-time dependency on React Native.</li>
 *   <li><b>Android resource id</b> — the entry name of {@code view.getId()}
 *       (e.g. {@code checkout_button} for {@code @+id/checkout_button}).</li>
 *   <li><b>Anonymous fallback</b> — {@code <SimpleClassName>_<hash>}, hashed from the view's
 *       position in the hierarchy.</li>
 * </ol>
 *
 * <p>{@code contentDescription} is deliberately not a source: it is localized, so the same element
 * would report a different identifier per language, and it can carry user data.
 *
 * <p>Every step is defensive: any exception thrown while resolving an identifier is swallowed and
 * resolution continues with the next step, so this class can never crash the host app.
 *
 * <p>This resolution is internal to the SDK and not configurable by the host app.
 */
final class DefaultViewElementIdExtractor {

    private static final String TAG = "MP.DefaultViewElementIdExtractor";

    /** Resource entry name React Native uses to store the {@code nativeID} prop as a view tag. */
    private static final String RN_NATIVE_ID_TAG = "view_tag_native_id";

    /** Cached resolved resource id for {@link #RN_NATIVE_ID_TAG}: 0 means "absent". */
    private static volatile int sNativeIdTagRes = -1;

    static final DefaultViewElementIdExtractor INSTANCE = new DefaultViewElementIdExtractor();

    private DefaultViewElementIdExtractor() {
    }

    /**
     * Returns the {@code $el_id} to report for the given view. Never null.
     */
    @NonNull
    String extractElementId(@NonNull View view) {
        String reactNativeId = resolveReactNativeId(view);
        if (reactNativeId != null) {
            return reactNativeId;
        }

        String resourceName = resolveResourceEntryName(view);
        if (resourceName != null) {
            return resourceName;
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
     * Returns the anonymous, PII-free identifier used when nothing else resolves:
     * {@code <SimpleClassName>_<hash>} (e.g. {@code AppCompatButton_3f2a1b}).
     *
     * <p>The hash is derived from the view's <b>position in the hierarchy</b>, not from its identity.
     * {@code View.hashCode()} is an identity hash: it changes on every launch and as list rows are
     * recycled, so an element with no id would get a different {@code $el_id} every session and
     * could never be grouped. A structural hash is stable for the same layout across launches.
     *
     * <p>It identifies a <i>position</i> rather than a row: reordering siblings changes the id, and
     * two rows of the same list are distinguished by their index, not their content.
     */
    @NonNull
    static String anonymousId(@NonNull View view) {
        String path = AutocaptureDefaults.structuralPath(view);
        // String.hashCode() is specified by the JDK, so it is stable across processes and devices —
        // unlike Object.hashCode(), which is identity-based.
        return view.getClass().getSimpleName() + "_" + Integer.toHexString(path.hashCode());
    }
}
