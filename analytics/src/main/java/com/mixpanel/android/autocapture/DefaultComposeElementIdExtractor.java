package com.mixpanel.android.autocapture;

import androidx.annotation.NonNull;

/**
 * Resolves the {@code $el_id} reported for a tapped <b>Jetpack Compose</b> element.
 *
 * <p>Resolution priority, mirroring {@link DefaultViewElementIdExtractor} with {@code testTag}
 * playing the role of the resource id:
 * <ol>
 *   <li><b>{@code Modifier.testTag("...")}</b> — developer-assigned and never user-visible</li>
 *   <li><b>Anonymous fallback</b> — {@code <TagName>_<hash>}, hashed from the node's position in
 *       the semantics tree</li>
 * </ol>
 *
 * <p>{@code contentDescription} from {@code semantics { }} is deliberately not a source: it is
 * localized, so the same element would report a different identifier per language, and it can carry
 * user data.
 *
 * <p>There is no React Native {@code nativeID} step: React Native renders through the legacy View
 * hierarchy, so a Compose element can never carry one.
 *
 * <p>This resolution is internal to the SDK and not configurable by the host app.
 */
final class DefaultComposeElementIdExtractor {

    static final DefaultComposeElementIdExtractor INSTANCE = new DefaultComposeElementIdExtractor();

    private DefaultComposeElementIdExtractor() {
    }

    /**
     * Returns the {@code $el_id} to report for the given Compose element. Never null.
     */
    @NonNull
    String extractElementId(@NonNull ComposeElementInfo element) {
        String testTag = element.getTestTag();
        if (testTag != null && !testTag.isEmpty()) {
            return testTag;
        }

        return element.getAnonymousId();
    }
}
