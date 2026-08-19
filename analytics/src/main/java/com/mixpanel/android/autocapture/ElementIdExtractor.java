package com.mixpanel.android.autocapture;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Resolves the {@code $el_id} property reported for an autocaptured interaction.
 *
 * <p>Implement this interface and pass it to
 * {@link com.mixpanel.android.mpmetrics.AutocaptureOptions.Builder#elementIdExtractor(ElementIdExtractor)}
 * to take full control over which identifier the SDK reports for a tapped view. This is the
 * recommended way to keep personally identifiable information out of autocapture events: the SDK
 * only ever reports what this method returns.
 *
 * <p>When no extractor is provided, the SDK falls back to an internal default implementation that
 * resolves the identifier from the React Native {@code nativeID}, the Android resource id, and the
 * view's content description, in that order.
 *
 * <pre>{@code
 * AutocaptureOptions options = new AutocaptureOptions.Builder()
 *     .elementIdExtractor(view -> {
 *         Object tag = view.getTag(R.id.mp_tracking_id);
 *         return tag instanceof String ? (String) tag : null;
 *     })
 *     .build();
 * }</pre>
 *
 * <p><b>Threading:</b> {@link #extractElementId(View)} is invoked on the main (UI) thread
 * immediately after the hit test that resolved the tapped view. Keep the implementation fast and
 * side-effect free. Exceptions thrown from the implementation are caught and logged by the SDK —
 * the event is still reported, using an anonymous {@code <SimpleClassName>_<hash>} identifier.
 *
 * @see com.mixpanel.android.mpmetrics.AutocaptureOptions
 */
public interface ElementIdExtractor {

    /**
     * Returns the {@code $el_id} to report for the given view.
     *
     * @param view The view resolved by the autocapture hit test. Never null.
     * @return The identifier to report, or {@code null} to let the SDK report an anonymous
     *         {@code <SimpleClassName>_<hash>} identifier instead. Empty strings are treated as
     *         {@code null}.
     */
    @Nullable
    String extractElementId(@NonNull View view);
}
