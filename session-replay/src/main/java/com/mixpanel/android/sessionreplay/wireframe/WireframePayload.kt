package com.mixpanel.android.sessionreplay.wireframe

import kotlinx.serialization.Serializable

/**
 * Payload of a `mp_wireframe` rrweb Custom event. Matches the client spec.
 *
 *  - `viewport`: optional `[width, height]` in px.
 *  - `elements`: list of visible UI elements (see [WireframeElementJson]).
 */
@Serializable
data class WireframePayload(
    val viewport: List<Int>? = null,
    val elements: List<WireframeElementJson>
)

/**
 * On-the-wire shape for a single wireframe element.
 *
 *  - `role`: one of "text", "button", "input", "image".
 *  - `text`: visible label or content description. `null` for input fields, masked elements,
 *    and elements with no text. Truncated to 49 chars + "…" when longer.
 *  - `bounds`: `[x, y, w, h]` in window-relative pixels.
 */
@Serializable
data class WireframeElementJson(
    val role: String,
    val text: String?,
    val bounds: List<Int>
)
