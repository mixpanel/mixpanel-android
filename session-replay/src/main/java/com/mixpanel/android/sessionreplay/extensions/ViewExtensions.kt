package com.mixpanel.android.sessionreplay.extensions

import android.view.View
import com.mixpanel.android.sessionreplay.sensitive_views.SensitiveViewManager

/**
 * Marks a View as sensitive or safe for masking in session replay screenshots.
 *
 * Use this to indicate whether the given [View] contains sensitive information
 * that should be hidden when a screenshot is captured during session replay.
 *
 * @param isSensitive
 * - `true` marks the view as sensitive — it will be masked in screenshots.
 * - `false` marks the view as safe — it will not be masked.
 *
 * If this method is not called on a view, the library will fall back to its default behavior,
 * which masks views based on the configuration provided during initialization.
 *
 * This controls **pixels only**. To declare the text recorded for the view in the
 * `mp_wireframe` event, use [mpWireframeText] — the two concerns are orthogonal and compose.
 *
 * @return The same [View], allowing method chaining.
 *
 * Example usage:
 * ```kotlin
 * val editText = findViewById<EditText>(R.id.cardNumberInput)
 * editText.mpReplaySensitive(true)
 * ```
 */
fun View.mpReplaySensitive(isSensitive: Boolean): View {
    if (isSensitive) {
        SensitiveViewManager.addSensitiveView(this)
        SensitiveViewManager.removeSafeView(this)
    } else {
        SensitiveViewManager.removeSensitiveView(this)
        SensitiveViewManager.addSafeView(this)
    }

    return this
}

/**
 * Declares the text recorded for this [View] in the `mp_wireframe` event.
 *
 * **Beta.** Wireframes are in beta; see
 * [com.mixpanel.android.sessionreplay.models.WireframesOptions] for what to check before
 * shipping to production.
 *
 * Use it to describe content the walker can't read (custom-drawn views, `ImageView`s) or to
 * attach an analytical label. Declared text takes precedence over the view's own visible text
 * and over its `contentDescription`.
 *
 * Masking and declared text are **orthogonal** — this call has no bearing on which pixels are
 * captured, and [mpReplaySensitive] has no bearing on the declared text. Combine them freely:
 *
 * ```kotlin
 * avatarImageView.mpReplaySensitive(true).mpWireframeText("profile photo")
 * ```
 *
 * @param text The text to record for this view. Blank strings are ignored. Pass `null` to clear
 * any previously declared text.
 *
 * **This text is sent even when the view is masked** — masking hides the pixels while the
 * declared text still describes the view for the AI summary. Because it is authored by you
 * (not scraped from the screen), it is your responsibility to ensure [text] is not itself
 * sensitive; if it could be, omit it.
 *
 * @return The same [View], allowing method chaining.
 *
 * Example usage:
 * ```kotlin
 * chartView.mpWireframeText("monthly spend")
 * ```
 */
fun View.mpWireframeText(text: String?): View {
    SensitiveViewManager.setWireframeText(this, text)
    return this
}
