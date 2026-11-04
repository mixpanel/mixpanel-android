package com.mixpanel.android.sessionreplay.models

import com.mixpanel.android.sessionreplay.wireframe.MaskDecision
import com.mixpanel.android.sessionreplay.wireframe.WireframeDebugSnapshot
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Options and serialization coverage, the counterpart to the Flutter suite's
 * `wireframes_options_test.dart`.
 *
 * Two things are pinned here that no other Android test covers:
 *
 *  - **The defaults are API.** [WireframesOptions] is what a customer constructs, so a
 *    changed default silently changes what every integration ships. `sensitiveRules`
 *    defaulting to empty and `useAccessibilityLabelFallback` defaulting to `false` are
 *    decisions recorded in `session-replay/CLAUDE.md`, not incidental.
 *  - **The JSON shape is a cross-platform contract.** React Native enables wireframes by
 *    putting `wireframesOptions` into the one config JSON it sends to both SDKs, so the
 *    field names and `type` tokens here must stay identical to iOS's
 *    `MPWireframesOptions` decoder. The round-trip and per-variant cases below are what
 *    stop a rename on one platform from silently disabling rules on the other.
 *  - **`maskDecision` spells SCREAMING_SNAKE on the wire.** Golden fixtures are compared
 *    across Android, iOS and Flutter by eye, and that only works while all three print
 *    the same token for the same decision. Kotlin's enum name happens to produce it
 *    today; [debugSnapshotSerializesEveryDecisionVariant] is what stops a rename or a
 *    `@SerialName` from quietly breaking cross-platform comparability.
 */
class WireframesOptionsTest {

    // ---- Defaults ----------------------------------------------------------------------

    @Test
    fun `defaults are no rules and label fallback off`() {
        val options = WireframesOptions()

        assertTrue("no rules by default", options.sensitiveRules.isEmpty())
        assertFalse("label fallback is opt-in, not opt-out", options.useAccessibilityLabelFallback)
    }

    @Test
    fun `rules are carried through verbatim and in declared order`() {
        val rules = listOf(
            SensitiveRule.Strip("password"),
            SensitiveRule.Redact("SSN", "[SSN]")
        )
        val options = WireframesOptions(sensitiveRules = rules)

        // Order is load-bearing: the emitter applies rules in sequence and a Strip
        // short-circuits the ones after it.
        assertEquals(rules, options.sensitiveRules)
    }

    @Test
    fun `label fallback can be turned on`() {
        assertEquals(true, WireframesOptions(useAccessibilityLabelFallback = true).useAccessibilityLabelFallback)
    }

    // ---- Rule variants: default and custom replacement ----------------------------------

    @Test
    fun `Redact defaults to the shared replacement token`() {
        assertEquals("[REDACTED]", SensitiveRule.Redact("SSN").replacement)
    }

    @Test
    fun `Redact honors a custom replacement`() {
        assertEquals("[SSN]", SensitiveRule.Redact("SSN", "[SSN]").replacement)
    }

    @Test
    fun `RedactRegex defaults to the shared replacement token`() {
        assertEquals("[REDACTED]", SensitiveRule.RedactRegex(Regex("\\d+")).replacement)
    }

    @Test
    fun `RedactRegex honors a custom replacement`() {
        assertEquals("[CARD]", SensitiveRule.RedactRegex(Regex("\\d+"), "[CARD]").replacement)
    }

    /** Strip has no replacement by design — it nulls the text rather than rewriting it. */
    @Test
    fun `Strip carries only its match text`() {
        assertEquals("password", SensitiveRule.Strip("password").text)
    }

    @Test
    fun `StripRegex carries its regex and its options`() {
        val regex = Regex("token-\\d+", RegexOption.IGNORE_CASE)
        val rule = SensitiveRule.StripRegex(regex)

        assertEquals(regex.pattern, rule.regex.pattern)
        assertTrue(
            "a caller's RegexOptions must survive into the rule",
            rule.regex.options.contains(RegexOption.IGNORE_CASE)
        )
    }

    // ---- Debug snapshot serialization ---------------------------------------------------

    @Test
    fun `debug snapshot JSON carries role text bounds and decision`() {
        val snapshot = WireframeDebugSnapshot(
            timestamp = 1_234L,
            viewport = listOf(400, 800),
            elements = listOf(
                WireframeDebugSnapshot.DebugElement(
                    role = "text",
                    text = "Account balance",
                    bounds = listOf(10, 20, 200, 40),
                    maskDecision = MaskDecision.NONE
                )
            )
        )

        val json = snapshot.toJson()

        assertTrue(json.contains("\"timestamp\":1234"))
        assertTrue(json.contains("\"viewport\":[400,800]"))
        assertTrue(json.contains("\"role\":\"text\""))
        assertTrue(json.contains("\"text\":\"Account balance\""))
        assertTrue(json.contains("\"bounds\":[10,20,200,40]"))
        assertTrue(json.contains("\"maskDecision\":\"NONE\""))
    }

    /**
     * Every decision variant, spelled the way the cross-platform golden format expects.
     *
     * This is the test that makes fixtures comparable between Android, iOS and Flutter:
     * all three print the same token for the same decision, so a reviewer can diff two
     * platforms' goldens by eye. A rename here, or a `@SerialName` on the enum, breaks
     * that silently — the SDK keeps working and only the cross-platform comparison rots.
     */
    @Test
    fun `debug snapshot serializes every decision variant in SCREAMING_SNAKE`() {
        val expected = mapOf(
            MaskDecision.NONE to "NONE",
            MaskDecision.DECLARED to "DECLARED",
            MaskDecision.EXPLICIT to "EXPLICIT",
            MaskDecision.AUTO to "AUTO",
            MaskDecision.TEXT_ENTRY to "TEXT_ENTRY",
            MaskDecision.GEOMETRIC to "GEOMETRIC",
            MaskDecision.RULE_STRIP to "RULE_STRIP",
            MaskDecision.RULE_REDACT to "RULE_REDACT"
        )

        assertEquals(
            "every MaskDecision variant must be covered here",
            MaskDecision.entries.toSet(),
            expected.keys
        )

        for ((decision, token) in expected) {
            val json = WireframeDebugSnapshot(
                timestamp = 0L,
                viewport = listOf(1, 1),
                elements = listOf(
                    WireframeDebugSnapshot.DebugElement(
                        role = "text",
                        text = null,
                        bounds = listOf(0, 0, 1, 1),
                        maskDecision = decision
                    )
                )
            ).toJson()

            assertTrue(
                "$decision must serialize as \"$token\"",
                json.contains("\"maskDecision\":\"$token\"")
            )
        }
    }

    // ---- JSON: the React Native contract --------------------------------------------------

    /**
     * The exact bytes React Native sends. Written out in full rather than round-tripped so
     * the field names and `type` tokens are pinned as text — a round trip alone would keep
     * passing after a rename that breaks iOS.
     */
    @Test
    fun `decodes the React Native payload`() {
        val options = json.decodeFromString<WireframesOptions>(
            """
            {
              "sensitiveRules": [
                { "type": "strip", "text": "password" },
                { "type": "redact", "text": "SSN", "replacement": "[SSN]" },
                { "type": "stripRegex", "pattern": "\\d{16}" },
                { "type": "redactRegex", "pattern": "[^@]+@[^@]+", "replacement": "[EMAIL]" }
              ],
              "useAccessibilityLabelFallback": true
            }
            """.trimIndent()
        )

        assertTrue(options.useAccessibilityLabelFallback)
        assertEquals(4, options.sensitiveRules.size)
        assertEquals(SensitiveRule.Strip("password"), options.sensitiveRules[0])
        assertEquals(SensitiveRule.Redact("SSN", "[SSN]"), options.sensitiveRules[1])
        // Regex has no useful equals(), so compare what the emitter actually uses.
        assertEquals("""\d{16}""", (options.sensitiveRules[2] as SensitiveRule.StripRegex).regex.pattern)
        val redactRegex = options.sensitiveRules[3] as SensitiveRule.RedactRegex
        assertEquals("[^@]+@[^@]+", redactRegex.regex.pattern)
        assertEquals("[EMAIL]", redactRegex.replacement)
    }

    /**
     * Both fields are optional. A bridge that only wants wireframes turned on sends `{}`,
     * and must get the same defaults a Kotlin caller writing `WireframesOptions()` gets.
     */
    @Test
    fun `an empty object decodes to the Kotlin defaults`() {
        assertEquals(WireframesOptions(), json.decodeFromString<WireframesOptions>("{}"))
    }

    /**
     * `replacement` is optional in JSON and must fall through to the same token the Kotlin
     * default parameter produces — otherwise a rule redacts to `null` or to a second,
     * divergent literal.
     */
    @Test
    fun `an omitted replacement decodes to the shared default`() {
        val options = json.decodeFromString<WireframesOptions>(
            """{"sensitiveRules":[{"type":"redact","text":"SSN"},{"type":"redactRegex","pattern":"\\d+"}]}"""
        )

        assertEquals(
            SensitiveRule.DEFAULT_REPLACEMENT,
            (options.sensitiveRules[0] as SensitiveRule.Redact).replacement
        )
        assertEquals(
            SensitiveRule.DEFAULT_REPLACEMENT,
            (options.sensitiveRules[1] as SensitiveRule.RedactRegex).replacement
        )
    }

    /**
     * The three flags that mean the same thing in JavaScript, Kotlin and
     * `NSRegularExpression`. A bridge hands us a JS `RegExp`'s `i`/`m`/`s` flags and expects
     * the compiled Kotlin `Regex` to behave the same way.
     */
    @Test
    fun `regex flags map onto RegexOptions`() {
        val options = json.decodeFromString<WireframesOptions>(
            """
            {"sensitiveRules":[{
              "type": "stripRegex",
              "pattern": "a.b",
              "caseInsensitive": true,
              "multiline": true,
              "dotMatchesAll": true
            }]}
            """.trimIndent()
        )

        val regex = (options.sensitiveRules[0] as SensitiveRule.StripRegex).regex
        assertEquals(
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL),
            regex.options
        )
        // Behavioral proof, not just option bookkeeping: dotMatchesAll lets `.` cross the
        // newline and IGNORE_CASE lets it match the capital.
        assertTrue(regex.containsMatchIn("A\nB"))
    }

    @Test
    fun `omitted regex flags decode to a bare pattern`() {
        val options = json.decodeFromString<WireframesOptions>(
            """{"sensitiveRules":[{"type":"stripRegex","pattern":"a.b"}]}"""
        )

        assertTrue((options.sensitiveRules[0] as SensitiveRule.StripRegex).regex.options.isEmpty())
    }

    /**
     * Encoding is the inverse of decoding, so the JSON a native Kotlin caller's config
     * produces is a payload the bridge could have sent. Compared as re-decoded values
     * because `Regex` has no `equals`.
     */
    @Test
    fun `rules survive an encode-decode round trip`() {
        val original = WireframesOptions(
            sensitiveRules = listOf(
                SensitiveRule.Strip("password"),
                SensitiveRule.Redact("SSN", "[SSN]"),
                SensitiveRule.StripRegex(Regex("\\d{16}", RegexOption.IGNORE_CASE)),
                SensitiveRule.RedactRegex(Regex("a.b", setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)), "[X]")
            ),
            useAccessibilityLabelFallback = true
        )

        val decoded = json.decodeFromString<WireframesOptions>(json.encodeToString(original))

        assertEquals(original.useAccessibilityLabelFallback, decoded.useAccessibilityLabelFallback)
        assertEquals(original.sensitiveRules[0], decoded.sensitiveRules[0])
        assertEquals(original.sensitiveRules[1], decoded.sensitiveRules[1])
        val strip = decoded.sensitiveRules[2] as SensitiveRule.StripRegex
        assertEquals("""\d{16}""", strip.regex.pattern)
        assertEquals(setOf(RegexOption.IGNORE_CASE), strip.regex.options)
        val redact = decoded.sensitiveRules[3] as SensitiveRule.RedactRegex
        assertEquals("a.b", redact.regex.pattern)
        assertEquals(setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL), redact.regex.options)
        assertEquals("[X]", redact.replacement)
    }

    /**
     * A rule the caller wrote to remove sensitive text must never fail open. Every
     * malformed shape throws, which the bridge turns into a rejected `initialize` — loud,
     * at integration time, instead of an SDK that quietly redacts nothing.
     */
    @Test
    fun `malformed rules fail the decode rather than being dropped`() {
        val malformed = mapOf(
            "unknown type" to """{"sensitiveRules":[{"type":"obliterate","text":"x"}]}""",
            "redact without text" to """{"sensitiveRules":[{"type":"redact"}]}""",
            "strip without text" to """{"sensitiveRules":[{"type":"strip"}]}""",
            "stripRegex without pattern" to """{"sensitiveRules":[{"type":"stripRegex"}]}""",
            "redactRegex without pattern" to """{"sensitiveRules":[{"type":"redactRegex"}]}""",
            "uncompilable pattern" to """{"sensitiveRules":[{"type":"stripRegex","pattern":"a(b"}]}"""
        )

        for ((label, payload) in malformed) {
            assertThrows(label, SerializationException::class.java) {
                json.decodeFromString<WireframesOptions>(payload)
            }
        }
    }

    /**
     * Wireframes reach React Native through the one config JSON the bridge already sends,
     * so `wireframesOptions` has to survive `MPSessionReplayConfig` decoding — it was
     * `@Transient` until RN needed it.
     */
    @Test
    fun `config JSON carries wireframesOptions end to end`() {
        val config = MPSessionReplayConfig.fromJson(
            """{"wireframesOptions":{"sensitiveRules":[{"type":"strip","text":"password"}]}}"""
        )

        assertEquals(
            listOf(SensitiveRule.Strip("password")),
            config.wireframesOptions?.sensitiveRules
        )
        assertFalse(config.wireframesOptions!!.useAccessibilityLabelFallback)
    }

    /**
     * Wireframes stay opt-in for every integration that does not ask for them: an existing
     * React Native config JSON, which has no `wireframesOptions` key at all, must leave
     * capture off.
     */
    @Test
    fun `a config JSON without wireframesOptions leaves capture off`() {
        assertEquals(null, MPSessionReplayConfig.fromJson("""{"wifiOnly":true}""").wireframesOptions)
    }

    private val json = Json { ignoreUnknownKeys = true }
}
