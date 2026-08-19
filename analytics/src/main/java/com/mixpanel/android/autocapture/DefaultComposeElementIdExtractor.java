package com.mixpanel.android.autocapture;

import androidx.annotation.NonNull;

/**
 * Default {@link ComposeElementIdExtractor} used when the host app configures neither element id
 * extractor.
 *
 * <p>Resolution priority, mirroring {@link DefaultViewElementIdExtractor} with {@code testTag}
 * playing the role of the resource id:
 * <ol>
 *   <li><b>{@code Modifier.testTag("...")}</b> — developer-assigned and never user-visible</li>
 *   <li><b>contentDescription</b> — from {@code semantics { contentDescription = ... }}</li>
 *   <li><b>Anonymous fallback</b> — {@code <TagName>_<hash>}</li>
 * </ol>
 *
 * <p>There is no React Native {@code nativeID} step: React Native renders through the legacy View
 * hierarchy, so a Compose element can never carry one.
 */
final class DefaultComposeElementIdExtractor implements ComposeElementIdExtractor {

    static final DefaultComposeElementIdExtractor INSTANCE = new DefaultComposeElementIdExtractor();

    private DefaultComposeElementIdExtractor() {
    }

    @Override
    @NonNull
    public String extractElementId(@NonNull ComposeElementInfo element) {
        String testTag = element.getTestTag();
        if (testTag != null && !testTag.isEmpty()) {
            return testTag;
        }

        String contentDescription = element.getContentDescription();
        if (contentDescription != null && !contentDescription.isEmpty()) {
            return contentDescription;
        }

        return element.getAnonymousId();
    }
}
