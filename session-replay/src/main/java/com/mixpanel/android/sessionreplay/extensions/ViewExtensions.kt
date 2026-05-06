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
