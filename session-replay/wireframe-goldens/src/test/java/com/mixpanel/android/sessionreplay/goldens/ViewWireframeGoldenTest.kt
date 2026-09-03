package com.mixpanel.android.sessionreplay.goldens

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.mixpanel.android.sessionreplay.extensions.mpReplaySensitive
import com.mixpanel.android.sessionreplay.extensions.mpWireframeText
import com.mixpanel.android.sessionreplay.goldens.WireframePaparazziHarness.Companion.at
import com.mixpanel.android.sessionreplay.models.SensitiveRule
import com.mixpanel.android.sessionreplay.sensitive_views.AutoMaskedView
import com.mixpanel.android.sessionreplay.sensitive_views.SensitiveViewManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Coordinate goldens for the **Android View / XML** wireframe path, laid out by layoutlib.
 *
 * Section headings mirror the Flutter reference suite ("Wireframe Masking Suite") so the two can
 * be diffed case-for-case; the Flutter fixture name is noted where one exists. Cases with no
 * Flutter counterpart are Android-specific mechanisms (class registration, breadth-first walk
 * order, elevation) that the platform contract still has to pin.
 *
 * Bounds come from a real measure/layout pass with real font metrics on a declared device
 * ([DeviceConfig.PIXEL_5], density 2.75, 1080x2340), so output is identical on every machine and
 * in CI with no emulator. Views are placed absolutely via [at] so a golden's numbers are dictated
 * by the case rather than by intrinsic text measurement.
 */
class ViewWireframeGoldenTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private val harness by lazy { WireframePaparazziHarness(paparazzi) }

    /** A registrable type for the `addSensitiveClass` cases. */
    private class CardNumberView(context: Context) : TextView(context)

    @Before
    fun setUp() {
        WireframePaparazziHarness.resetMaskingState()
    }

    @After
    fun tearDown() {
        SensitiveViewManager.removeSensitiveClass(CardNumberView::class.java)
        WireframePaparazziHarness.resetMaskingState()
    }

    private fun frame(context: Context, build: FrameLayout.() -> Unit): View =
        FrameLayout(context).apply(build)

    // ---- Text mask decisions (Flutter 1-4) -----------------------------------------------------

    @Test
    fun textPlain_emittedVerbatim() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(TextView(ctx).apply { text = "Public Information" }, at(16, 16, 400, 60))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "text_plain.json")
    }

    @Test
    fun textAutoMasked_nulled() {
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text)
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(TextView(ctx).apply { text = "Account 4111 1111" }, at(16, 16, 400, 60))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "text_auto_masked.json")
    }

    @Test
    fun textExplicitlyMasked_nulled() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    TextView(ctx).apply {
                        text = "Balance $1,234.56"
                        mpReplaySensitive(true)
                    },
                    at(16, 16, 400, 60)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "text_explicit_masked.json")
    }

    /** An unmask overrides auto-masking — that is exactly what auto-masking yields to. */
    @Test
    fun textUnmask_overridesAutoMask() {
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text)
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    TextView(ctx).apply {
                        text = "Public Override"
                        mpReplaySensitive(false)
                    },
                    at(16, 16, 400, 60)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "text_unmask_overrides_auto.json")
    }

    // ---- Text-entry fields (Flutter 5-6) -------------------------------------------------------

    @Test
    fun input_alwaysTextEntry() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(EditText(ctx).apply { setText("user@example.com") }, at(16, 16, 400, 80))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "input_always_masked.json")
    }

    /** An unmask cannot override the security decision on an editable field. */
    @Test
    fun inputInsideUnmaskedContainer_stillTextEntry() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                val container = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    mpReplaySensitive(false)
                    addView(
                        EditText(ctx).apply { setText("password123") },
                        LinearLayout.LayoutParams(400, 80)
                    )
                }
                addView(container, at(16, 16, 440, 120))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "input_in_unmask_still_masked.json")
    }

    // ---- Buttons (Flutter 8-13) ---------------------------------------------------------------

    @Test
    fun buttonWithTitle_carriesLabel() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(Button(ctx).apply { text = "Submit" }, at(16, 16, 300, 100))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "button_title.json")
    }

    @Test
    fun buttonUnlabeled_isTextlessShell() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(Button(ctx), at(16, 16, 120, 120))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "button_unlabeled.json")
    }

    /** Third text tier: no visible text, so the accessibility label supplies the name. */
    @Test
    fun buttonFallsBackToContentDescription() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    Button(ctx).apply { contentDescription = "Open settings" },
                    at(16, 16, 120, 120)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "button_content_description.json")
    }

    @Test
    fun buttonMasked_dropsLabel() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    Button(ctx).apply {
                        text = "Pay"
                        mpReplaySensitive(true)
                    },
                    at(16, 16, 300, 100)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "button_masked_drops_label.json")
    }

    // ---- Images (Flutter 16-19) ---------------------------------------------------------------

    @Test
    fun imageUnlabeled_isTextlessShell() {
        val capture = harness.capture { ctx ->
            frame(ctx) { addView(ImageView(ctx), at(16, 16, 220, 220)) }
        }
        WireframeGoldenFormat.assertGolden(capture, "image_unlabeled.json")
    }

    @Test
    fun imageWithContentDescription_carriesLabel() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    ImageView(ctx).apply { contentDescription = "Company logo" },
                    at(16, 16, 220, 220)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "image_content_description.json")
    }

    @Test
    fun imageAutoMasked_dropsLabel() {
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Image)
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    ImageView(ctx).apply { contentDescription = "Company logo" },
                    at(16, 16, 220, 220)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "image_auto_masked.json")
    }

    @Test
    fun imageMasked_dropsLabel() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    ImageView(ctx).apply {
                        contentDescription = "Company logo"
                        mpReplaySensitive(true)
                    },
                    at(16, 16, 220, 220)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "image_masked_drops_label.json")
    }

    // ---- Nested directives (Flutter 20-22) ----------------------------------------------------

    /**
     * An inner unmask is honored by Layer 1, then the text is stripped by Layer 2 anyway because
     * the outer mask's rect still covers it and the painter never punches unmask regions out.
     */
    @Test
    fun nestedUnmaskInMask_stripsGeometrically() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                val masked = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    mpReplaySensitive(true)
                    addView(
                        TextView(ctx).apply {
                            text = "Inner unmasked"
                            mpReplaySensitive(false)
                        },
                        LinearLayout.LayoutParams(400, 60)
                    )
                }
                addView(masked, at(16, 16, 440, 120))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "nested_unmask_in_mask_geometric.json")
    }

    /** Same contract with an intervening layout node — the two tree shapes must agree. */
    @Test
    fun nestedUnmaskUnderLayoutInMask_stripsGeometrically() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                val masked = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    mpReplaySensitive(true)
                    val inner = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(
                            TextView(ctx).apply {
                                text = "Inner unmasked"
                                mpReplaySensitive(false)
                            },
                            LinearLayout.LayoutParams(400, 60)
                        )
                        addView(
                            TextView(ctx).apply { text = "Inner plain" },
                            LinearLayout.LayoutParams(400, 60)
                        )
                    }
                    addView(inner, LinearLayout.LayoutParams(400, 120))
                }
                addView(masked, at(16, 16, 440, 160))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "nested_unmask_under_layout_geometric.json")
    }

    @Test
    fun nestedMaskInUnmask_innerMaskWins() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                val safe = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    mpReplaySensitive(false)
                    addView(
                        TextView(ctx).apply {
                            text = "Still secret"
                            mpReplaySensitive(true)
                        },
                        LinearLayout.LayoutParams(400, 60)
                    )
                }
                addView(safe, at(16, 16, 440, 120))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "nested_mask_in_unmask.json")
    }

    // ---- Geometric leak prevention (Flutter 23-24) ---------------------------------------------

    /** The overlapped node is a sibling of the mask, not a descendant — Layer 2's whole point. */
    @Test
    fun geometricOverlap_nullsSiblingText() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    View(ctx).apply { mpReplaySensitive(true) },
                    at(0, 0, 600, 400)
                )
                addView(TextView(ctx).apply { text = "Account balance" }, at(40, 80, 400, 60))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "geometric_overlap_nulled.json")
    }

    /** Layer 2 is role-agnostic — it strips button and image labels too. */
    @Test
    fun geometricOverlap_stripsButtonAndImageLabels() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(View(ctx).apply { mpReplaySensitive(true) }, at(0, 0, 600, 400))
                addView(Button(ctx).apply { text = "Checkout" }, at(40, 40, 300, 100))
                addView(
                    ImageView(ctx).apply { contentDescription = "Company logo" },
                    at(40, 180, 200, 200)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "geometric_overlap_button_and_image.json")
    }

    // ---- Sensitive rules (Flutter 25-30) -------------------------------------------------------

    @Test
    fun ruleStrip_nullsMatchingText() {
        val capture = harness.capture(rules = listOf(SensitiveRule.Strip("Bearer "))) { ctx ->
            frame(ctx) {
                addView(TextView(ctx).apply { text = "Bearer eyJhbGciOi" }, at(16, 16, 400, 60))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "rule_strip.json")
    }

    @Test
    fun ruleRedact_rewritesInPlace() {
        val capture = harness.capture(
            rules = listOf(SensitiveRule.Redact("alice@example.com", "[EMAIL]"))
        ) { ctx ->
            frame(ctx) {
                addView(
                    TextView(ctx).apply { text = "email: alice@example.com" },
                    at(16, 16, 400, 60)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "rule_redact.json")
    }

    @Test
    fun ruleStripRegex_nullsMatchingText() {
        val capture = harness.capture(
            rules = listOf(SensitiveRule.StripRegex(Regex("^token-")))
        ) { ctx ->
            frame(ctx) {
                addView(TextView(ctx).apply { text = "token-abc123" }, at(16, 16, 400, 60))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "rule_strip_regex.json")
    }

    @Test
    fun ruleRedactRegex_rewritesMatchingText() {
        val capture = harness.capture(
            rules = listOf(SensitiveRule.RedactRegex(Regex("\\d{3}-\\d{2}-\\d{4}"), "[SSN]"))
        ) { ctx ->
            frame(ctx) {
                addView(TextView(ctx).apply { text = "SSN: 123-45-6789" }, at(16, 16, 400, 60))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "rule_redact_regex.json")
    }

    /** Rules run over label-derived text too, not just visible text. */
    @Test
    fun ruleStrip_reachesContentDescriptionLabel() {
        val capture = harness.capture(rules = listOf(SensitiveRule.Strip("Bearer "))) { ctx ->
            frame(ctx) {
                addView(
                    Button(ctx).apply { contentDescription = "Bearer eyJhbGciOi" },
                    at(16, 16, 120, 120)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "rule_strip_button_label.json")
    }

    @Test
    fun ruleRedact_reachesImageLabel() {
        val capture = harness.capture(
            rules = listOf(SensitiveRule.Redact("alice@example.com", "[EMAIL]"))
        ) { ctx ->
            frame(ctx) {
                addView(
                    ImageView(ctx).apply { contentDescription = "avatar of alice@example.com" },
                    at(16, 16, 220, 220)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "rule_redact_image_label.json")
    }

    // ---- Declared wireframe text (Flutter 32-38) ----------------------------------------------

    @Test
    fun declaredText_survivesMaskOnImage() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    ImageView(ctx).apply {
                        mpReplaySensitive(true).mpWireframeText("profile photo")
                    },
                    at(16, 16, 220, 220)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "declared_mask_image.json")
    }

    @Test
    fun declaredText_adoptsButtonRole() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    Button(ctx).apply {
                        text = "Submit"
                        mpReplaySensitive(true).mpWireframeText("checkout action")
                    },
                    at(16, 16, 300, 100)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "declared_button.json")
    }

    /** Labels the field without ever leaking the typed value. */
    @Test
    fun declaredText_labelsInputWithoutLeakingValue() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    EditText(ctx).apply {
                        setText("4111 1111 1111 1111")
                        mpWireframeText("Card number")
                    },
                    at(16, 16, 400, 80)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "declared_input.json")
    }

    /** A labeled container does not absorb the field inside it. */
    @Test
    fun declaredContainer_keepsInputSeparate() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                val container = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    mpReplaySensitive(false).mpWireframeText("payment form")
                    addView(
                        TextView(ctx).apply { text = "Pay now" },
                        LinearLayout.LayoutParams(400, 60)
                    )
                    addView(EditText(ctx), LinearLayout.LayoutParams(400, 80))
                }
                addView(container, at(16, 16, 440, 180))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "declared_container_keeps_input.json")
    }

    @Test
    fun declaredText_onPlainViewFallsBackToTextRole() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    View(ctx).apply { mpWireframeText("monthly spend") },
                    at(16, 16, 300, 200)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "declared_plain_view.json")
    }

    /** Declared text is exempt from Layer 2 but not from Layer 4. */
    @Test
    fun declaredText_stillStrippedByRule() {
        val capture = harness.capture(rules = listOf(SensitiveRule.Strip("secret"))) { ctx ->
            frame(ctx) {
                addView(
                    View(ctx).apply { mpWireframeText("card 4111 secret") },
                    at(16, 16, 300, 100)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "declared_rule_stripped.json")
    }

    @Test
    fun declaredText_survivesGeometricStrip() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(View(ctx).apply { mpReplaySensitive(true) }, at(0, 0, 600, 400))
                addView(
                    TextView(ctx).apply {
                        text = "Scraped"
                        mpWireframeText("Declared label")
                    },
                    at(40, 80, 400, 60)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "declared_survives_geometric.json")
    }

    // ---- Accessibility fallback disabled (Flutter 39-42) ---------------------------------------

    @Test
    fun buttonLabel_fallbackOff_dropsLabelKeepsShell() {
        SensitiveViewManager.useAccessibilityLabelFallback = false
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    Button(ctx).apply { contentDescription = "Open settings" },
                    at(16, 16, 120, 120)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "button_label_fallback_off.json")
    }

    /** The flag gates the fallback position only; visible text is untouched. */
    @Test
    fun visibleText_fallbackOff_unaffected() {
        SensitiveViewManager.useAccessibilityLabelFallback = false
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    Button(ctx).apply {
                        text = "Continue"
                        contentDescription = "Continue to checkout"
                    },
                    at(16, 16, 300, 100)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "button_label_fallback_off_with_text.json")
    }

    @Test
    fun imageLabel_fallbackOff_dropsLabel() {
        SensitiveViewManager.useAccessibilityLabelFallback = false
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    ImageView(ctx).apply { contentDescription = "Company logo" },
                    at(16, 16, 220, 220)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "image_label_fallback_off.json")
    }

    /** Declared text is authored, not scraped, so the flag must not gate it. */
    @Test
    fun declaredText_fallbackOff_stillEmitted() {
        SensitiveViewManager.useAccessibilityLabelFallback = false
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    Button(ctx).apply {
                        contentDescription = "scraped"
                        mpWireframeText("Open settings")
                    },
                    at(16, 16, 120, 120)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "declared_beats_label_fallback_off.json")
    }

    // ---- Hidden and off-screen content (Flutter 43-44) -----------------------------------------

    /**
     * Three filters at once. `View.isShown` covers GONE and INVISIBLE; the explicit `alpha <= 0f`
     * check covers the third, since a fully transparent view paints nothing and so must not hand
     * its text to the summarizer either. Counterpart to Flutter fixture 44.
     */
    @Test
    fun hiddenSubtrees_notEmitted() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(TextView(ctx).apply { text = "Visible" }, at(16, 0, 400, 60))
                addView(
                    TextView(ctx).apply {
                        text = "Gone secret"
                        visibility = View.GONE
                    },
                    at(16, 70, 400, 60)
                )
                addView(
                    TextView(ctx).apply {
                        text = "Invisible secret"
                        visibility = View.INVISIBLE
                    },
                    at(16, 140, 400, 60)
                )
                addView(
                    TextView(ctx).apply {
                        text = "Transparent secret"
                        alpha = 0f
                    },
                    at(16, 210, 400, 60)
                )
            }
        }
        assertEquals(
            "only the visible view is described",
            listOf("Visible"),
            capture.elements.map { it.text }
        )
        WireframeGoldenFormat.assertGolden(capture, "hidden_views_not_emitted.json")
    }

    /** Alpha is multiplicative, so a transparent parent takes its whole subtree with it. */
    @Test
    fun transparentParent_dropsSubtree() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(TextView(ctx).apply { text = "Visible" }, at(16, 0, 400, 60))
                val faded = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    alpha = 0f
                    addView(
                        TextView(ctx).apply { text = "Child of transparent" },
                        LinearLayout.LayoutParams(400, 60)
                    )
                }
                addView(faded, at(16, 70, 400, 60))
            }
        }
        assertEquals(
            "a transparent parent hides its children too",
            listOf("Visible"),
            capture.elements.map { it.text }
        )
        WireframeGoldenFormat.assertGolden(capture, "transparent_parent_drops_subtree.json")
    }

    /** Bounds are clipped to the enclosing scroll viewport; a fully scrolled-out row is absent. */
    @Test
    fun scrollableOffscreen_clippedAndDropped() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                val scroller = ScrollView(ctx)
                val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                repeat(5) { i ->
                    column.addView(
                        TextView(ctx).apply { text = "Row $i" },
                        LinearLayout.LayoutParams(400, 80)
                    )
                }
                scroller.addView(column, FrameLayout.LayoutParams(400, 400))
                addView(scroller, at(16, 16, 400, 250))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "scrollable_offscreen_clipped.json")
    }

    // ---- Text cleaning and truncation (Flutter 45) ---------------------------------------------

    @Test
    fun overlongText_truncatedAtCap() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    TextView(ctx).apply {
                        text = "This label is far too long to ship intact and must " +
                            "therefore exceed the fifty character wireframe cap"
                    },
                    at(16, 16, 900, 60)
                )
            }
        }
        val text = capture.elements.single().text
        assertEquals("truncated text is capped at 50 chars", 50, text?.length)
        WireframeGoldenFormat.assertGolden(capture, "text_truncated.json")
    }

    /** An icon-font glyph is not human-readable text — nulled, shell kept. */
    @Test
    fun iconGlyphOnlyText_nulled() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(TextView(ctx).apply { text = "" }, at(16, 16, 120, 120))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "icon_glyph_nulled.json")
    }

    /** Any human-readable character keeps the whole string. */
    @Test
    fun mixedGlyphAndReadableText_kept() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(TextView(ctx).apply { text = "Settings " }, at(16, 16, 400, 60))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "icon_glyph_mixed_kept.json")
    }

    /** Declared text is authored, so the SDK does not second-guess its codepoints. */
    @Test
    fun declaredGlyphText_keptVerbatim() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    TextView(ctx).apply { mpWireframeText("") },
                    at(16, 16, 120, 120)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "declared_glyph_kept.json")
    }

    @Test
    fun emptyScreen_emitsZeroElements() {
        val capture = harness.capture { ctx -> frame(ctx) {} }
        assertEquals("an empty screen ships no elements", emptyList<Any>(), capture.elements)
        WireframeGoldenFormat.assertGolden(capture, "empty_screen.json")
    }

    // ---- Android-specific: class registration --------------------------------------------------

    /** A class registered by the developer is an opt-in, so it reports EXPLICIT, not AUTO. */
    @Test
    fun registeredClass_reportsExplicit() {
        SensitiveViewManager.addSensitiveClass(CardNumberView::class.java)
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    CardNumberView(ctx).apply { text = "4111 1111 1111 1111" },
                    at(16, 16, 400, 60)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "class_explicit_masked.json")
    }

    /** An unmask overrides a class match, which `addSensitiveView` does not allow. */
    @Test
    fun safeView_overridesRegisteredClass() {
        SensitiveViewManager.addSensitiveClass(CardNumberView::class.java)
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    CardNumberView(ctx).apply {
                        text = "Not actually sensitive"
                        mpReplaySensitive(false)
                    },
                    at(16, 16, 400, 60)
                )
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "class_safe_kept.json")
    }

    // ---- Android-specific: walk order ----------------------------------------------------------

    /** The walk is breadth-first; a flat tree cannot tell BFS from DFS, so this one nests. */
    @Test
    fun nestedChildren_emittedBreadthFirst() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                val card = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        TextView(ctx).apply { text = "Card title" },
                        LinearLayout.LayoutParams(400, 60)
                    )
                    addView(
                        TextView(ctx).apply { text = "Card body" },
                        LinearLayout.LayoutParams(400, 60)
                    )
                }
                addView(card, at(0, 16, 400, 120))
                addView(TextView(ctx).apply { text = "Footer" }, at(16, 150, 400, 60))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "order_nested_breadth_first.json")
    }

    /** Emission order follows child index, not elevation/Z. */
    @Test
    fun elevation_doesNotReorderSiblings() {
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(
                    TextView(ctx).apply {
                        text = "Elevated overlay"
                        elevation = 8f
                    },
                    at(40, 40, 400, 60)
                )
                addView(TextView(ctx).apply { text = "Backdrop" }, at(0, 120, 400, 60))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "order_elevation_ignored.json")
    }

    // ---- Complex mixed masking (Flutter 31) ----------------------------------------------------

    /**
     * Realistic multi-widget layout exercising several decisions at once, the counterpart to the
     * Flutter `complex_mixed_masking` fixture. This is the case worth pairing with an image
     * golden of the same tree — comparing bounds against painted pixels is what validates that
     * the coordinates line up.
     */
    @Test
    fun complexMixedMasking() {
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text)
        val capture = harness.capture { ctx ->
            frame(ctx) {
                addView(TextView(ctx).apply { text = "Auto masked header" }, at(0, 0, 600, 60))
                addView(ImageView(ctx).apply { contentDescription = "Hero" }, at(0, 70, 200, 200))
                addView(
                    TextView(ctx).apply {
                        text = "Explicitly unmasked"
                        mpReplaySensitive(false)
                    },
                    at(220, 70, 380, 60)
                )
                addView(
                    ImageView(ctx).apply {
                        contentDescription = "Secret chart"
                        mpReplaySensitive(true)
                    },
                    at(220, 140, 380, 130)
                )
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(
                        TextView(ctx).apply { text = "Row auto" },
                        LinearLayout.LayoutParams(200, 60)
                    )
                    addView(
                        TextView(ctx).apply {
                            text = "Middle"
                            mpReplaySensitive(false)
                        },
                        LinearLayout.LayoutParams(200, 60)
                    )
                    addView(EditText(ctx), LinearLayout.LayoutParams(200, 60))
                }
                addView(row, at(0, 290, 600, 60))
            }
        }
        WireframeGoldenFormat.assertGolden(capture, "complex_mixed_masking.json")
    }
}
