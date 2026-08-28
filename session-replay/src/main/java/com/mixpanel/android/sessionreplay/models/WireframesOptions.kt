package com.mixpanel.android.sessionreplay.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Enables wireframe capture for Session Replay.
 *
 * **Beta.** Wireframes are in beta. Before shipping to production, inspect the wireframes
 * your app produces with
 * [com.mixpanel.android.sessionreplay.debug.DebugOptions.wireframeEmitter] and confirm that
 * no sensitive information is captured.
 *
 * A wireframe is a lightweight text outline of a screen: the visible elements, what kind
 * each one is (text, button, input, image), what it says, and where it sits. One is
 * captured alongside each screenshot, which lets Mixpanel summarize what your users saw
 * and did without anyone having to watch the replay.
 *
 * Set this on [MPSessionReplayConfig.wireframesOptions] to turn wireframes on. Leave it
 * `null` — the default — and none are captured.
 *
 * Mixpanel can also turn wireframe capture off for a project from the server. When it
 * does, the rest of session replay keeps recording — only the wireframe payload is
 * dropped — and the reason is logged at init.
 *
 * ### Your masking settings are respected
 *
 * Anything hidden in the replay video is also removed from the wireframe. The element is
 * still listed so the shape of the screen is preserved, but its text is dropped:
 *
 * - Views you marked sensitive with `addSensitiveView`, `addSensitiveClass`, or
 *   `mpReplaySensitive(true)` on a `View` or a Compose `Modifier`.
 * - Views covered by automatic masking (`AutoMaskedView`), and text input fields.
 * - Anything sitting underneath a masked area, even if that view was never marked
 *   sensitive itself. If it is painted over in the video, its text is not sent.
 *
 * ### Describing an element yourself
 *
 * `mpWireframeText("...")` lets you supply the text for an element — useful for
 * custom views, canvas-drawn content, or anywhere the text picked up automatically isn't
 * meaningful. Because you authored it rather than the SDK scraping it off the screen, it
 * is sent even for an element you've masked in the video. Use it to say what a screen is
 * for ("Checkout summary") without revealing what's on it.
 *
 * ### Catching sensitive content by pattern
 *
 * Some text is sensitive because of what it contains rather than which view it came from
 * — an account number inside a custom view the SDK has no way to recognize, for example.
 * [sensitiveRules] match on the text itself and run last, over everything above,
 * including text you supplied with `wireframeText`.
 *
 * ### Checking what you're sending
 *
 * While wireframes are on, the debug callback
 * [com.mixpanel.android.sessionreplay.debug.DebugOptions.wireframeEmitter] hands each one
 * back to you as it is captured, along with the reason every element's text was kept or
 * removed. It shows exactly what is sent, so you can confirm your masking as you build.
 *
 * @property sensitiveRules Rules applied to each element's text before it is sent, in the
 *   order you declare them. Elements are always kept; only their text is affected.
 *   - A matching `Strip` rule drops the text and stops — no later rule runs.
 *   - `Redact` rules build on one another: a rule declared later sees the result of the
 *     ones before it, not the original text.
 *
 *   Empty by default.
 *
 * @property useAccessibilityLabelFallback Whether an element with no text of its own may
 *   fall back to its accessibility label (`contentDescription` on a `View`,
 *   `ContentDescription` in Compose). Off by default: a label is not drawn on screen, so
 *   unlike visible text you cannot confirm what it contains by watching the replay, and
 *   labels sometimes hold more than what is shown.
 *
 *   Turn it on if you want icons and image buttons named. For an icon-only control the
 *   label is usually the only description of what the element is for, and with the fallback
 *   off it is sent as a bare shell instead — `mpWireframeText(...)` is then the only way to
 *   describe it.
 *
 *   The label is only ever a fallback. Text you set with `mpWireframeText(...)`
 *   wins over it, an element's own visible text wins over it, and a masked element stays
 *   textless either way.
 *
 * ### JSON
 *
 * This type serializes so cross-platform bridges (React Native) can enable wireframes
 * through the config JSON they already send. The shape is deliberately identical on
 * Android and iOS so one bridge payload decodes on both:
 *
 * ```json
 * {
 *   "sensitiveRules": [
 *     { "type": "strip", "text": "password" },
 *     { "type": "redact", "text": "SSN", "replacement": "[SSN]" },
 *     { "type": "stripRegex", "pattern": "\\d{16}", "caseInsensitive": true },
 *     { "type": "redactRegex", "pattern": "[^@]+@[^@]+", "replacement": "[EMAIL]" }
 *   ],
 *   "useAccessibilityLabelFallback": true
 * }
 * ```
 *
 * See [SensitiveRuleSerializer] for the per-rule field list and the regex-flag mapping.
 */
@Serializable
data class WireframesOptions(
    val sensitiveRules: List<SensitiveRule> = emptyList(),
    val useAccessibilityLabelFallback: Boolean = false
)

/**
 * A rule that rewrites or drops the text of a wireframe element when it matches.
 *
 * Rules run after the SDK's own masking, on whatever text is left — including text you
 * supplied with `wireframeText`. See [WireframesOptions] for the full picture.
 *
 * They run in the order you list them in [WireframesOptions.sensitiveRules]. A matching
 * `Strip` rule drops the text and stops immediately; a matching `Redact` rule rewrites the
 * text, and any rule after it sees the rewritten value.
 *
 * Put a strip first if you want it to win no matter what the redacts do — a strip placed
 * later only fires if the text still matches once the earlier redacts have run.
 *
 * Serializes through [SensitiveRuleSerializer] so React Native can declare rules in the
 * config JSON; see [WireframesOptions] for the payload shape.
 */
@Serializable(with = SensitiveRuleSerializer::class)
sealed class SensitiveRule {
    /**
     * Replaces case-insensitive substring matches of [text] with [replacement], leaving
     * the surrounding text intact.
     */
    data class Redact(
        val text: String,
        val replacement: String = DEFAULT_REPLACEMENT
    ) : SensitiveRule()

    /** Drops the element's text entirely if it contains [text] (case-insensitive). */
    data class Strip(val text: String) : SensitiveRule()

    /** Replaces regex matches of [regex] with [replacement], leaving surrounding text intact. */
    data class RedactRegex(
        val regex: Regex,
        val replacement: String = DEFAULT_REPLACEMENT
    ) : SensitiveRule()

    /** Drops the element's text entirely if [regex] finds a match anywhere in the text. */
    data class StripRegex(val regex: Regex) : SensitiveRule()

    companion object {
        /**
         * Replacement used by [Redact] and [RedactRegex] when the caller does not supply one.
         *
         * Shared with [SensitiveRuleSerializer] so a rule that omits `replacement` in JSON
         * decodes to exactly what the Kotlin default would have produced, and with iOS's
         * `MPSensitiveRule.defaultReplacement` so both platforms redact to the same token.
         */
        const val DEFAULT_REPLACEMENT: String = "[REDACTED]"
    }
}

/**
 * JSON form of [SensitiveRule], for the React Native bridge.
 *
 * A sealed hierarchy holding [Regex] instances has no automatic representation, so each
 * variant maps to one flat object tagged by `type`:
 *
 * | `type`         | fields                                                     |
 * |----------------|------------------------------------------------------------|
 * | `redact`       | `text`, optional `replacement`                             |
 * | `strip`        | `text`                                                     |
 * | `redactRegex`  | `pattern`, optional `replacement`, optional flags          |
 * | `stripRegex`   | `pattern`, optional flags                                  |
 *
 * The three optional flags — `caseInsensitive`, `multiline`, `dotMatchesAll` — are the
 * subset of regex options that mean the same thing in JavaScript (`i`, `m`, `s`), Kotlin
 * ([RegexOption.IGNORE_CASE], [RegexOption.MULTILINE], [RegexOption.DOT_MATCHES_ALL]) and
 * `NSRegularExpression`, so a bridge can pass a JS `RegExp` through unchanged. They
 * default to `false`, matching a bare `RegExp` with no flags.
 *
 * Encoding is the exact inverse, so a decode/encode round trip is lossless.
 *
 * Malformed input throws [SerializationException] rather than dropping the rule: a rule
 * the caller wrote to remove sensitive text must never fail open. On the bridge that
 * surfaces as a rejected `initialize`, which is the loud failure we want.
 */
object SensitiveRuleSerializer : KSerializer<SensitiveRule> {
    private const val TYPE_REDACT = "redact"
    private const val TYPE_STRIP = "strip"
    private const val TYPE_REDACT_REGEX = "redactRegex"
    private const val TYPE_STRIP_REGEX = "stripRegex"

    @Serializable
    private data class Surrogate(
        val type: String,
        val text: String? = null,
        val pattern: String? = null,
        val replacement: String? = null,
        val caseInsensitive: Boolean = false,
        val multiline: Boolean = false,
        val dotMatchesAll: Boolean = false
    )

    override val descriptor: SerialDescriptor = Surrogate.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: SensitiveRule
    ) {
        val surrogate = when (value) {
            is SensitiveRule.Redact ->
                Surrogate(type = TYPE_REDACT, text = value.text, replacement = value.replacement)

            is SensitiveRule.Strip ->
                Surrogate(type = TYPE_STRIP, text = value.text)

            is SensitiveRule.RedactRegex ->
                value.regex.toSurrogate(TYPE_REDACT_REGEX).copy(replacement = value.replacement)

            is SensitiveRule.StripRegex ->
                value.regex.toSurrogate(TYPE_STRIP_REGEX)
        }
        encoder.encodeSerializableValue(Surrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): SensitiveRule {
        val surrogate = decoder.decodeSerializableValue(Surrogate.serializer())
        return when (surrogate.type) {
            TYPE_REDACT -> SensitiveRule.Redact(
                text = surrogate.requireText(),
                replacement = surrogate.replacement ?: SensitiveRule.DEFAULT_REPLACEMENT
            )

            TYPE_STRIP -> SensitiveRule.Strip(text = surrogate.requireText())

            TYPE_REDACT_REGEX -> SensitiveRule.RedactRegex(
                regex = surrogate.toRegex(),
                replacement = surrogate.replacement ?: SensitiveRule.DEFAULT_REPLACEMENT
            )

            TYPE_STRIP_REGEX -> SensitiveRule.StripRegex(regex = surrogate.toRegex())

            else -> throw SerializationException(
                "Unknown wireframe sensitiveRules type '${surrogate.type}'. " +
                    "Expected one of $TYPE_REDACT, $TYPE_STRIP, $TYPE_REDACT_REGEX, $TYPE_STRIP_REGEX."
            )
        }
    }

    private fun Surrogate.requireText(): String =
        text ?: throw SerializationException(
            "Wireframe sensitiveRules type '$type' requires a non-null 'text' field."
        )

    private fun Surrogate.toRegex(): Regex {
        val pattern = pattern ?: throw SerializationException(
            "Wireframe sensitiveRules type '$type' requires a non-null 'pattern' field."
        )
        val options = mutableSetOf<RegexOption>()
        if (caseInsensitive) options += RegexOption.IGNORE_CASE
        if (multiline) options += RegexOption.MULTILINE
        if (dotMatchesAll) options += RegexOption.DOT_MATCHES_ALL
        return try {
            Regex(pattern, options)
        } catch (e: IllegalArgumentException) {
            // Covers PatternSyntaxException. A pattern that compiles in JavaScript can still
            // be rejected by java.util.regex, so this is reachable from a well-formed bridge
            // payload — fail the decode rather than ship an integration whose redaction
            // silently never fires.
            throw SerializationException(
                "Wireframe sensitiveRules pattern '$pattern' is not a valid regular expression on Android: ${e.message}",
                e
            )
        }
    }

    private fun Regex.toSurrogate(type: String) = Surrogate(
        type = type,
        pattern = pattern,
        caseInsensitive = RegexOption.IGNORE_CASE in options,
        multiline = RegexOption.MULTILINE in options,
        dotMatchesAll = RegexOption.DOT_MATCHES_ALL in options
    )
}
