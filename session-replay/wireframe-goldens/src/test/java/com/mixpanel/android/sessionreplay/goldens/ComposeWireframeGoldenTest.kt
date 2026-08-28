package com.mixpanel.android.sessionreplay.goldens

import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.mixpanel.android.sessionreplay.extensions.mpReplaySensitive
import com.mixpanel.android.sessionreplay.extensions.mpWireframeText
import com.mixpanel.android.sessionreplay.models.SensitiveRule
import com.mixpanel.android.sessionreplay.sensitive_views.AutoMaskedView
import com.mixpanel.android.sessionreplay.sensitive_views.SensitiveViewManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Coordinate goldens for the **Compose** wireframe path, laid out by layoutlib.
 *
 * Mirrors [ViewWireframeGoldenTest] section for section so the two Android paths can be diffed
 * against each other and against the Flutter reference suite. The final section has no counterpart
 * on either — it pins behavior specific to Compose's *merged* semantics tree, including two
 * limitations that are deliberate (see `session-replay/CLAUDE.md`, "The Compose wireframe walk
 * stays one pass") and must not be "fixed" by reflex.
 */
class ComposeWireframeGoldenTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private val harness by lazy { WireframePaparazziHarness(paparazzi) }

    @Before
    fun setUp() {
        WireframePaparazziHarness.resetMaskingState()
    }

    @After
    fun tearDown() {
        WireframePaparazziHarness.resetMaskingState()
    }

    @Composable
    private fun EditableField(initial: String = "", modifier: Modifier = Modifier) {
        var value by remember { mutableStateOf(initial) }
        BasicTextField(value = value, onValueChange = { value = it }, modifier = modifier)
    }

    /** A sized, role-carrying stand-in for an image — Compose classifies on `Role.Image`. */
    @Composable
    private fun ImageNode(label: String? = null, size: Int = 80, modifier: Modifier = Modifier) {
        Box(
            modifier = modifier
                .size(size.dp)
                .semantics {
                    role = Role.Image
                    if (label != null) contentDescription = label
                }
        )
    }

    // ---- Text mask decisions -------------------------------------------------------------------

    @Test
    fun textPlain_emittedVerbatim() {
        val capture = harness.captureCompose { BasicText("Public Information") }
        WireframeGoldenFormat.assertGolden(capture, "compose_text_plain.json")
    }

    @Test
    fun textAutoMasked_nulled() {
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text)
        val capture = harness.captureCompose { BasicText("Account 4111 1111") }
        WireframeGoldenFormat.assertGolden(capture, "compose_text_auto_masked.json")
    }

    @Test
    fun textExplicitlyMasked_nulled() {
        val capture = harness.captureCompose {
            BasicText("Balance $1,234.56", modifier = Modifier.mpReplaySensitive(true))
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_text_explicit_masked.json")
    }

    @Test
    fun textUnmask_overridesAutoMask() {
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text)
        val capture = harness.captureCompose {
            BasicText("Public Override", modifier = Modifier.mpReplaySensitive(false))
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_text_unmask_overrides_auto.json")
    }

    // ---- Text-entry fields ---------------------------------------------------------------------

    @Test
    fun input_alwaysTextEntry() {
        val capture = harness.captureCompose { EditableField("user@example.com") }
        WireframeGoldenFormat.assertGolden(capture, "compose_input_always_masked.json")
    }

    @Test
    fun inputInsideUnmaskedContainer_stillTextEntry() {
        val capture = harness.captureCompose {
            Column(modifier = Modifier.mpReplaySensitive(false)) {
                EditableField("password123")
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_input_in_unmask_still_masked.json")
    }

    // ---- Buttons -------------------------------------------------------------------------------

    @Test
    fun buttonWithTitle_carriesLabel() {
        val capture = harness.captureCompose {
            BasicText("Submit", modifier = Modifier.semantics { role = Role.Button })
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_button_title.json")
    }

    @Test
    fun buttonUnlabeled_isTextlessShell() {
        val capture = harness.captureCompose {
            Box(modifier = Modifier.size(48.dp).semantics { role = Role.Button })
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_button_unlabeled.json")
    }

    @Test
    fun buttonFallsBackToContentDescription() {
        val capture = harness.captureCompose {
            Box(
                modifier = Modifier.size(48.dp).semantics {
                    role = Role.Button
                    contentDescription = "Open settings"
                }
            )
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_button_content_description.json")
    }

    @Test
    fun buttonMasked_dropsLabel() {
        val capture = harness.captureCompose {
            BasicText(
                "Pay",
                modifier = Modifier
                    .semantics { role = Role.Button }
                    .mpReplaySensitive(true)
            )
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_button_masked_drops_label.json")
    }

    // ---- Images --------------------------------------------------------------------------------

    @Test
    fun imageUnlabeled_isTextlessShell() {
        val capture = harness.captureCompose { ImageNode() }
        WireframeGoldenFormat.assertGolden(capture, "compose_image_unlabeled.json")
    }

    @Test
    fun imageWithContentDescription_carriesLabel() {
        val capture = harness.captureCompose { ImageNode("Company logo") }
        WireframeGoldenFormat.assertGolden(capture, "compose_image_content_description.json")
    }

    @Test
    fun imageAutoMasked_dropsLabel() {
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Image)
        val capture = harness.captureCompose { ImageNode("Company logo") }
        WireframeGoldenFormat.assertGolden(capture, "compose_image_auto_masked.json")
    }

    @Test
    fun imageMasked_dropsLabel() {
        val capture = harness.captureCompose {
            ImageNode("Company logo", modifier = Modifier.mpReplaySensitive(true))
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_image_masked_drops_label.json")
    }

    // ---- Nested directives ---------------------------------------------------------------------

    @Test
    fun nestedUnmaskInMask_stripsGeometrically() {
        val capture = harness.captureCompose {
            Column(modifier = Modifier.mpReplaySensitive(true)) {
                BasicText("Inner unmasked", modifier = Modifier.mpReplaySensitive(false))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_nested_unmask_in_mask_geometric.json")
    }

    @Test
    fun nestedUnmaskUnderLayoutInMask_stripsGeometrically() {
        val capture = harness.captureCompose {
            Column(modifier = Modifier.mpReplaySensitive(true)) {
                Column {
                    BasicText("Inner unmasked", modifier = Modifier.mpReplaySensitive(false))
                    BasicText("Inner plain")
                }
            }
        }
        WireframeGoldenFormat.assertGolden(
            capture,
            "compose_nested_unmask_under_layout_geometric.json"
        )
    }

    @Test
    fun nestedMaskInUnmask_innerMaskWins() {
        val capture = harness.captureCompose {
            Column(modifier = Modifier.mpReplaySensitive(false)) {
                BasicText("Still secret", modifier = Modifier.mpReplaySensitive(true))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_nested_mask_in_unmask.json")
    }

    // ---- Geometric leak prevention -------------------------------------------------------------

    @Test
    fun geometricOverlap_nullsSiblingText() {
        val capture = harness.captureCompose {
            Box(Modifier.fillMaxSize()) {
                Box(modifier = Modifier.requiredSize(300.dp, 200.dp).mpReplaySensitive(true))
                BasicText("Account balance")
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_geometric_overlap_nulled.json")
    }

    @Test
    fun geometricOverlap_stripsButtonAndImageLabels() {
        val capture = harness.captureCompose {
            Box(Modifier.fillMaxSize()) {
                Box(modifier = Modifier.requiredSize(300.dp, 200.dp).mpReplaySensitive(true))
                Column {
                    BasicText("Checkout", modifier = Modifier.semantics { role = Role.Button })
                    ImageNode("Company logo")
                }
            }
        }
        WireframeGoldenFormat.assertGolden(
            capture,
            "compose_geometric_overlap_button_and_image.json"
        )
    }

    // ---- Sensitive rules -----------------------------------------------------------------------

    @Test
    fun ruleStrip_nullsMatchingText() {
        val capture = harness.captureCompose(rules = listOf(SensitiveRule.Strip("Bearer "))) {
            BasicText("Bearer eyJhbGciOi")
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_rule_strip.json")
    }

    @Test
    fun ruleRedact_rewritesInPlace() {
        val capture = harness.captureCompose(
            rules = listOf(SensitiveRule.Redact("alice@example.com", "[EMAIL]"))
        ) {
            BasicText("email: alice@example.com")
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_rule_redact.json")
    }

    @Test
    fun ruleStripRegex_nullsMatchingText() {
        val capture = harness.captureCompose(
            rules = listOf(SensitiveRule.StripRegex(Regex("^token-")))
        ) {
            BasicText("token-abc123")
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_rule_strip_regex.json")
    }

    @Test
    fun ruleRedactRegex_rewritesMatchingText() {
        val capture = harness.captureCompose(
            rules = listOf(SensitiveRule.RedactRegex(Regex("\\d{3}-\\d{2}-\\d{4}"), "[SSN]"))
        ) {
            BasicText("SSN: 123-45-6789")
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_rule_redact_regex.json")
    }

    @Test
    fun ruleRedact_reachesImageLabel() {
        val capture = harness.captureCompose(
            rules = listOf(SensitiveRule.Redact("alice@example.com", "[EMAIL]"))
        ) {
            ImageNode("avatar of alice@example.com")
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_rule_redact_image_label.json")
    }

    // ---- Declared wireframe text ---------------------------------------------------------------

    @Test
    fun declaredText_survivesMaskOnImage() {
        val capture = harness.captureCompose {
            ImageNode(
                "Avatar",
                modifier = Modifier.mpReplaySensitive(true).mpWireframeText("profile photo")
            )
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_declared_mask_image.json")
    }

    @Test
    fun declaredText_adoptsButtonRole() {
        val capture = harness.captureCompose {
            BasicText(
                "Submit",
                modifier = Modifier
                    .semantics { role = Role.Button }
                    .mpReplaySensitive(true).mpWireframeText("checkout action")
            )
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_declared_button.json")
    }

    /** The label rides the field itself, so it adopts the input role; the value never ships. */
    @Test
    fun declaredText_labelsInputWithoutLeakingValue() {
        val capture = harness.captureCompose {
            EditableField(
                "4111 1111 1111 1111",
                modifier = Modifier.mpWireframeText("Card number")
            )
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_declared_input.json")
    }

    /** A labeled container does not absorb the field inside it. */
    @Test
    fun declaredContainer_keepsInputSeparate() {
        val capture = harness.captureCompose {
            Column(modifier = Modifier.mpReplaySensitive(false).mpWireframeText("payment form")) {
                BasicText("Pay now")
                EditableField()
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_declared_container_keeps_input.json")
    }

    @Test
    fun declaredText_stillStrippedByRule() {
        val capture = harness.captureCompose(rules = listOf(SensitiveRule.Strip("secret"))) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .mpWireframeText("card 4111 secret")
            )
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_declared_rule_stripped.json")
    }

    @Test
    fun declaredText_survivesGeometricStrip() {
        val capture = harness.captureCompose {
            Box(Modifier.fillMaxSize()) {
                Box(modifier = Modifier.requiredSize(300.dp, 200.dp).mpReplaySensitive(true))
                BasicText("Scraped", modifier = Modifier.mpWireframeText("Declared label"))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_declared_survives_geometric.json")
    }

    // ---- Accessibility fallback disabled -------------------------------------------------------

    @Test
    fun buttonLabel_fallbackOff_dropsLabelKeepsShell() {
        SensitiveViewManager.useAccessibilityLabelFallback = false
        val capture = harness.captureCompose {
            Box(
                modifier = Modifier.size(48.dp).semantics {
                    role = Role.Button
                    contentDescription = "Open settings"
                }
            )
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_button_label_fallback_off.json")
    }

    @Test
    fun visibleText_fallbackOff_unaffected() {
        SensitiveViewManager.useAccessibilityLabelFallback = false
        val capture = harness.captureCompose {
            BasicText("Log in", modifier = Modifier.semantics { contentDescription = "Log in now" })
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_text_fallback_off_keeps_text.json")
    }

    @Test
    fun imageLabel_fallbackOff_dropsLabel() {
        SensitiveViewManager.useAccessibilityLabelFallback = false
        val capture = harness.captureCompose { ImageNode("Company logo") }
        WireframeGoldenFormat.assertGolden(capture, "compose_image_label_fallback_off.json")
    }

    /**
     * A label-only node is dropped rather than kept as an empty shell: the label was the only
     * evidence it was content at all, so keeping a shell would emit the labeled *containers* the
     * four-role rule excludes. Compose-only — `classifyAndroidView` never consults the label.
     */
    @Test
    fun labelOnlyNode_fallbackOff_dropsElement() {
        SensitiveViewManager.useAccessibilityLabelFallback = false
        val capture = harness.captureCompose {
            Box(modifier = Modifier.size(24.dp).semantics { contentDescription = "Cart" })
        }
        assertEquals("a label-only node is dropped entirely", emptyList<Any>(), capture.elements)
        WireframeGoldenFormat.assertGolden(capture, "compose_label_only_fallback_off.json")
    }

    @Test
    fun declaredText_fallbackOff_stillEmitted() {
        SensitiveViewManager.useAccessibilityLabelFallback = false
        val capture = harness.captureCompose {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .semantics { contentDescription = "Cart" }
                    .mpWireframeText("Cart")
            )
        }
        WireframeGoldenFormat.assertGolden(
            capture,
            "compose_declared_beats_label_fallback_off.json"
        )
    }

    // ---- Text cleaning and truncation ----------------------------------------------------------

    @Test
    fun overlongText_truncatedAtCap() {
        val capture = harness.captureCompose {
            BasicText(
                "This label is far too long to ship intact and must " +
                    "therefore exceed the fifty character wireframe cap"
            )
        }
        assertEquals("truncated text is capped at 50 chars", 50, capture.elements.single().text?.length)
        WireframeGoldenFormat.assertGolden(capture, "compose_text_truncated.json")
    }

    @Test
    fun iconGlyphOnlyText_nulled() {
        val capture = harness.captureCompose { BasicText("") }
        WireframeGoldenFormat.assertGolden(capture, "compose_icon_glyph_nulled.json")
    }

    @Test
    fun mixedGlyphAndReadableText_kept() {
        val capture = harness.captureCompose { BasicText("Settings ") }
        WireframeGoldenFormat.assertGolden(capture, "compose_icon_glyph_mixed_kept.json")
    }

    @Test
    fun emptyScreen_emitsZeroElements() {
        val capture = harness.captureCompose { }
        assertEquals("an empty screen ships no elements", emptyList<Any>(), capture.elements)
        WireframeGoldenFormat.assertGolden(capture, "compose_empty_screen.json")
    }

    // ---- Hidden and off-screen content ---------------------------------------------------------

    /**
     * **Known divergence from the View path — pinned deliberately, not endorsed.**
     *
     * `SensitiveViewManager.processSubviews` drops a `View` with `alpha <= 0f`, matching Flutter's
     * `Opacity(0)` filter. The Compose walk has no equivalent: transparency lives on the
     * graphics layer, and the only thing that reads it — `SemanticsNode.isTransparent` — is
     * `internal` to compose-ui, so the SDK cannot see it without reflection. A composable behind
     * `Modifier.alpha(0f)` is therefore still described, text and all.
     *
     * The golden records today's behavior so the gap is visible and a fix shows up as a diff.
     * Closing it needs either an upstream request to open `isTransparent` up, or an
     * `InvisibleToUser`-style semantics opt-in customers set themselves.
     */
    @Test
    fun transparentNode_stillEmitted_knownGap() {
        val capture = harness.captureCompose {
            Column {
                BasicText("Visible")
                BasicText("Transparent secret", modifier = Modifier.alpha(0f))
            }
        }
        assertEquals(
            "Compose cannot see graphics-layer alpha — both are still described",
            listOf("Visible", "Transparent secret"),
            capture.elements.map { it.text }
        )
        WireframeGoldenFormat.assertGolden(capture, "compose_transparent_node_known_gap.json")
    }

    // ---- Off-screen content --------------------------------------------------------------------

    /**
     * `boundsInRoot` has scroll clipping applied, so a row scrolled fully out of a scroll
     * container occupies no area and is dropped; a straddling row keeps its text with clipped
     * bounds. The Compose counterpart to [ViewWireframeGoldenTest.scrollableOffscreen_clippedAndDropped].
     */
    @Test
    fun scrollableOffscreen_clippedAndDropped() {
        val capture = harness.captureCompose {
            Column(
                modifier = Modifier
                    .requiredSize(300.dp, 100.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                repeat(5) { i -> BasicText("Row $i", modifier = Modifier.height(30.dp)) }
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_scrollable_offscreen_clipped.json")
    }

    // ---- Complex mixed masking -----------------------------------------------------------------

    @Test
    fun complexMixedMasking() {
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text)
        val capture = harness.captureCompose {
            Column {
                BasicText("Auto masked header")
                ImageNode("Hero")
                BasicText("Explicitly unmasked", modifier = Modifier.mpReplaySensitive(false))
                ImageNode("Secret chart", modifier = Modifier.mpReplaySensitive(true))
                Row {
                    BasicText("Row auto")
                    BasicText("Middle", modifier = Modifier.mpReplaySensitive(false))
                    EditableField()
                }
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_complex_mixed_masking.json")
    }

    // ---- Compose-only: merged semantics and paint order ----------------------------------------

    /** The merged node reports text folded up from its visible descendants. */
    @Test
    fun merged_keepsTextFromVisibleDescendants() {
        val capture = harness.captureCompose {
            Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                BasicText("Title")
                BasicText("Subtitle")
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_merged_visible_descendants.json")
    }

    /**
     * A known, deliberate limitation: Compose folds *every* descendant's text into a
     * `mergeDescendants` node, including descendants that are composed but never shown. The
     * merged-text index that would fix this was removed because it cost four nav tabs to save one
     * duplicate label — see `session-replay/CLAUDE.md`. Pinned so rebuilding it is deliberate.
     *
     * Not a privacy problem: masking is geometric and covers the merged node's whole rect either
     * way. Fidelity only.
     */
    @Test
    fun merged_includesTextFromNeverShownDescendant() {
        val capture = harness.captureCompose {
            Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                BasicText("Shown label")
                Box(modifier = Modifier.height(0.dp)) { BasicText("Never shown") }
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_merged_never_shown_descendant.json")
    }

    /** Same limitation via the measured-but-not-placed shape Material3 nav items produce. */
    @Test
    fun merged_includesTextFromMeasuredButUnplacedDescendant() {
        val capture = harness.captureCompose {
            Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                BasicText("Placed label")
                Layout(content = { BasicText("Measured but unplaced") }) { measurables, constraints ->
                    val placeables = measurables.map { it.measure(constraints) }
                    // Measure, then deliberately never place — the collapsed-label shape.
                    layout(placeables.maxOfOrNull { it.width } ?: 0, 0) { }
                }
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_merged_unplaced_descendant.json")
    }

    /** Emission follows the semantics walk, not paint order. */
    @Test
    fun overlappingSiblings_emitInDeclarationOrder() {
        val capture = harness.captureCompose {
            Box(Modifier.fillMaxSize()) {
                BasicText("Underneath")
                BasicText("On top")
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_overlapping_siblings.json")
    }

    /** `zIndex` changes paint order; the wireframe order follows it as Compose reports it. */
    @Test
    fun zIndex_emissionOrder() {
        val capture = harness.captureCompose {
            Box(Modifier.fillMaxSize()) {
                BasicText("Raised", modifier = Modifier.zIndex(2f))
                BasicText("Base", modifier = Modifier.zIndex(1f))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "compose_zindex_order.json")
    }

    /**
     * Two full-screen destinations mid-crossfade both survive. The grouping pass that would drop
     * one was removed because a full-screen scrim is the same shape and deleting the content
     * underneath is the more damaging failure — see `session-replay/CLAUDE.md`.
     */
    @Test
    fun stackedDestinations_keepBoth() {
        val capture = harness.captureCompose {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) { BasicText("Outgoing screen") }
                Column(Modifier.fillMaxSize()) { BasicText("Incoming screen") }
            }
        }
        assertEquals(
            listOf("Outgoing screen", "Incoming screen"),
            capture.elements.map { it.text }
        )
        WireframeGoldenFormat.assertGolden(capture, "compose_stacked_destinations.json")
    }

    /** The same rule from the other side: a scrim must not delete the content under it. */
    @Test
    fun fullScreenOverlay_keepsContentUnderneath() {
        val capture = harness.captureCompose {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) { BasicText("Checkout total $84.20") }
                Box(Modifier.fillMaxSize()) { BasicText("Loading") }
            }
        }
        assertEquals(
            listOf("Checkout total $84.20", "Loading"),
            capture.elements.map { it.text }
        )
        WireframeGoldenFormat.assertGolden(capture, "compose_full_screen_overlay.json")
    }
}
