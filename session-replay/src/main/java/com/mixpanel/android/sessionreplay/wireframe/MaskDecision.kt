package com.mixpanel.android.sessionreplay.wireframe

import kotlinx.serialization.Serializable

/**
 * Why an element's text was transformed (or not) when the wireframe was built.
 *
 * Carried on [WireframeElement] for local inspection via
 * [com.mixpanel.android.sessionreplay.debug.DebugOptions.wireframeEmitter]. Never
 * emitted on the on-the-wire format.
 */
@Serializable
enum class MaskDecision {
    /** Text emitted as-is. */
    NONE,

    /**
     * Developer-provided `mpWireframeText(...)`. Emitted verbatim, even on a masked
     * or editable view, because the text is authored rather than scraped from the screen.
     * Exempt from the Layer 2 geometric strip; Layer 4 sensitive rules still run over it, so
     * an element that started as [DECLARED] can still end up [RULE_STRIP] or [RULE_REDACT].
     */
    DECLARED,

    /**
     * View was directly marked sensitive via `addSensitiveView`,
     * `mpReplaySensitive(true)`, or a match against a class registered with
     * `addSensitiveClass`. A class match is still overridable by `addSafeView`, unlike the
     * other two — the developer opted the class in, so the decision is reported as explicit,
     * but a safe container remains the narrower, later-stated intent.
     */
    EXPLICIT,

    /**
     * Auto-masked by class (TextView / ImageView / WebView per
     * [com.mixpanel.android.sessionreplay.sensitive_views.AutoMaskedView]) — i.e. masked
     * because the SDK's defaults said so, not because the developer named this view or its
     * class. Developer-registered classes report [EXPLICIT].
     */
    AUTO,

    /** `EditText` — always masked, cannot be overridden. */
    TEXT_ENTRY,

    /**
     * Element bounds intersected a mask rect drawn by another view — typically a
     * sensitive ancestor or sibling. Prevents wireframe text from leaking content that
     * the screenshot has visually redacted.
     */
    GEOMETRIC,

    /**
     * Matched a [com.mixpanel.android.sessionreplay.models.SensitiveRule.Strip] or
     * [com.mixpanel.android.sessionreplay.models.SensitiveRule.StripRegex]; text was
     * dropped entirely.
     */
    RULE_STRIP,

    /**
     * Modified by [com.mixpanel.android.sessionreplay.models.SensitiveRule.Redact] or
     * [com.mixpanel.android.sessionreplay.models.SensitiveRule.RedactRegex]; text is
     * present but rewritten.
     */
    RULE_REDACT
}
