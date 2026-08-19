package com.mixpanel.android.autocapture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Resolves the {@code $el_id} reported for an autocaptured interaction with a <b>Jetpack Compose</b>
 * element.
 *
 * <p>The Compose counterpart of {@link ViewElementIdExtractor}. Compose elements are semantics nodes
 * rather than Views, so they cannot be handed to a {@code View}-based extractor; this interface
 * receives the node's semantics as a {@link ComposeElementInfo} instead.
 *
 * <p>Implement this interface and pass it to
 * {@link com.mixpanel.android.mpmetrics.AutocaptureOptions.Builder#composeElementIdExtractor(ComposeElementIdExtractor)}
 * to take full control over which identifier the SDK reports for Compose interactions.
 *
 * <pre>{@code
 * AutocaptureOptions options = new AutocaptureOptions.Builder()
 *     // Report only test tags the team opted into; anything else stays anonymous.
 *     .composeElementIdExtractor(element -> {
 *         String tag = element.getTestTag();
 *         return tag != null && tag.startsWith("track_") ? tag : null;
 *     })
 *     .build();
 * }</pre>
 *
 * <p>Resolution when nothing is configured: {@code Modifier.testTag(...)}, then
 * {@code contentDescription}, then an anonymous {@code <TagName>_<hash>} identifier.
 *
 * <p><b>Setting only a {@link ViewElementIdExtractor} makes Compose identifiers anonymous.</b> The
 * SDK treats a configured identifier policy as deliberate, so it will not fall back to
 * semantics-derived text — which can contain user data — on the path the app did not configure.
 * Supply both extractors to control both paths.
 *
 * <p><b>Threading:</b> {@link #extractElementId(ComposeElementInfo)} is invoked on the main (UI)
 * thread immediately after the hit test that resolved the tapped element. Keep the implementation
 * fast and side-effect free. Exceptions thrown from the implementation are caught and logged by the
 * SDK — the event is still reported, using the anonymous identifier.
 *
 * @see ViewElementIdExtractor
 * @see com.mixpanel.android.mpmetrics.AutocaptureOptions
 */
public interface ComposeElementIdExtractor {

    /**
     * Returns the {@code $el_id} to report for the given Compose element.
     *
     * @param element The semantics of the element resolved by the autocapture hit test. Never null.
     * @return The identifier to report, or {@code null} to let the SDK report
     *         {@link ComposeElementInfo#getAnonymousId()} instead. Empty strings are treated as
     *         {@code null}.
     */
    @Nullable
    String extractElementId(@NonNull ComposeElementInfo element);
}
