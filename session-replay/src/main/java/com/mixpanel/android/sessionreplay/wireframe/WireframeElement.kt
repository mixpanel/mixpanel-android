package com.mixpanel.android.sessionreplay.wireframe

import android.graphics.Rect

/**
 * Internal classification of a wireframe element. Maps to the role names defined in the
 * rrweb Custom event spec via [wireName].
 *
 * A **closed** set, deliberately. The role is the one field the masking pipeline never touches
 * — Layers 1–4 mask, strip and redact `text`, and nothing filters `role` — so a role sourced
 * from developer-supplied text would be a channel that bypasses masking entirely. Every value
 * here is produced by mapping a platform enum, never by forwarding a string, so that channel
 * does not exist.
 *
 * [Text], [Input], [Image] and [Button] come from view type. The rest come from accessibility
 * roles, which are developer-declared intent rather than inference, and are therefore only as
 * complete as the app's own accessibility annotations. Coverage differs by platform on purpose:
 * iOS collapses most of React Native's `accessibilityRole` values to
 * `UIAccessibilityTraitNone`, so it can only distinguish button/link/header, while Android reads
 * its full role enum. Reporting what a platform can actually see beats reporting the
 * intersection.
 */
enum class WireframeType {
    Text,
    Input,
    Image,
    Button,
    Link,
    Header,
    Checkbox,
    Switch,
    Radio,
    Tab
    ;

    fun wireName(): String = when (this) {
        Text -> "text"
        Input -> "input"
        Image -> "image"
        Button -> "button"
        Link -> "link"
        Header -> "header"
        Checkbox -> "checkbox"
        Switch -> "switch"
        Radio -> "radio"
        Tab -> "tab"
    }
}

/**
 * In-memory representation of a single wireframe element captured during the view walk.
 * Converted to [WireframeElementJson] at emit time.
 *
 * [maskDecision] records why (if at all) the element's text was transformed by the
 * masking pipeline. Not serialized to the wire format.
 *
 * [MaskDecision.DECLARED] marks text authored by the developer via
 * `mpWireframeText(...)` rather than scraped from the view. Declared text is
 * exempt from the Layer 2 geometric strip (including the view's own mask region) so it
 * survives even when the view is masked — masking hides the pixels while the declared text
 * still describes the view for the AI summary. Layer 4 sensitive rules still run over it as
 * a safety net, and may replace the decision with [MaskDecision.RULE_STRIP] /
 * [MaskDecision.RULE_REDACT].
 */
data class WireframeElement(
    val type: WireframeType,
    val text: String?,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val maskDecision: MaskDecision = MaskDecision.NONE
) {
    companion object {
        fun fromRect(
            type: WireframeType,
            text: String?,
            rect: Rect,
            maskDecision: MaskDecision = MaskDecision.NONE
        ): WireframeElement =
            WireframeElement(
                type = type,
                text = text,
                x = rect.left,
                y = rect.top,
                w = rect.width(),
                h = rect.height(),
                maskDecision = maskDecision
            )
    }
}
