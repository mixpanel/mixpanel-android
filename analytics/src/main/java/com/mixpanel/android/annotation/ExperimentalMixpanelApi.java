package com.mixpanel.android.annotation;

import androidx.annotation.RequiresOptIn;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an API as <b>experimental (beta)</b>: usable and supported, but not yet at general
 * availability.
 *
 * <p>An experimental API works and is safe to try, with three caveats:
 *
 * <ul>
 *   <li><b>It may contain issues.</b> Behaviour can be imperfect in cases the feature has not been
 *       hardened against yet.</li>
 *   <li><b>The API may change.</b> Types, method signatures and default behaviour can change in a
 *       future release, in ways that are not source compatible.</li>
 *   <li><b>The data it produces may change.</b> Event and property shapes can be refined before GA,
 *       so pin your SDK version before building long-lived reports on them.</li>
 * </ul>
 *
 * <p>Calling an annotated API produces a build <b>warning</b>, not an error — nothing breaks if you
 * ignore it. To acknowledge it and silence the warning, annotate the calling class or method:
 *
 * <pre>{@code
 * @OptIn(markerClass = ExperimentalMixpanelApi.class)
 * public void setUpAnalytics() {
 *     // ... experimental APIs here
 * }
 * }</pre>
 *
 * <p>Opting in applies only to the scope you annotate.
 *
 * <p><b>Currently experimental:</b> autocapture — {@code $mp_click}, {@code $mp_rage_click} and
 * {@code $mp_dead_click} capture, their configuration in
 * {@code MixpanelOptions.Builder#autocaptureOptions}, and the element id extractor interfaces. When
 * a feature reaches GA the annotation is removed from its declarations; this annotation type itself
 * stays, for whatever is experimental next.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({
    ElementType.TYPE,
    ElementType.METHOD,
    ElementType.CONSTRUCTOR,
    ElementType.FIELD,
    ElementType.PARAMETER
})
@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
public @interface ExperimentalMixpanelApi {
}
