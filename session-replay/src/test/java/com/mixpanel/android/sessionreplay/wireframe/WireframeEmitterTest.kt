package com.mixpanel.android.sessionreplay.wireframe

import android.graphics.Rect
import com.mixpanel.android.sessionreplay.models.RawScreenshotEvent
import com.mixpanel.android.sessionreplay.models.RawTouchEvent
import com.mixpanel.android.sessionreplay.models.SensitiveRule
import com.mixpanel.android.sessionreplay.models.SessionEvent
import com.mixpanel.android.sessionreplay.models.SessionEventData
import com.mixpanel.android.sessionreplay.tracking.EventListener
import com.mixpanel.android.sessionreplay.tracking.EventPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WireframeEmitterTest {
    private val capturedEvents = mutableListOf<SessionEvent>()
    private val capturedSnapshots = mutableListOf<WireframeDebugSnapshot>()

    private val listener = object : EventListener {
        override fun receivedTouchEvent(rawEvent: RawTouchEvent) = Unit
        override fun receivedScreenshotEvent(rawEvent: RawScreenshotEvent) = Unit
        override fun receivedCustomEvent(event: SessionEvent) {
            capturedEvents += event
        }
    }

    @Before
    fun setUp() {
        EventPublisher.shared.subscribe(listener)
    }

    @After
    fun tearDown() {
        EventPublisher.shared.unsubscribe(listener)
        capturedEvents.clear()
        capturedSnapshots.clear()
    }

    private fun emitter(
        rules: List<SensitiveRule> = emptyList(),
        withDebug: Boolean = true
    ) = WireframeEmitter(
        sensitiveRules = rules,
        debugEmitter = if (withDebug) { snap -> capturedSnapshots += snap } else null,
        dispatcher = Dispatchers.Unconfined
    )

    private fun element(
        text: String? = "hello",
        x: Int = 0,
        y: Int = 0,
        w: Int = 100,
        h: Int = 20,
        maskDecision: MaskDecision = MaskDecision.NONE
    ) = WireframeElement(WireframeType.Text, text, x, y, w, h, maskDecision)

    private fun emittedText(index: Int = 0): String? {
        val payload = capturedEvents.first().data as SessionEventData.WireframeCustomData
        return payload.payload.elements[index].text
    }

    private fun emittedDecision(index: Int = 0): MaskDecision =
        capturedSnapshots.first().elements[index].maskDecision

    private fun emittedBounds(index: Int = 0): List<Int> {
        val payload = capturedEvents.first().data as SessionEventData.WireframeCustomData
        return payload.payload.elements[index].bounds
    }

    private fun emittedViewport(): List<Int> {
        val payload = capturedEvents.first().data as SessionEventData.WireframeCustomData
        return payload.payload.viewport!!
    }

    @Test
    fun `passes text through when no rules and no mask`() = runTest {
        emitter().emit(listOf(element("hello")), 1080, 1920)
        assertEquals("hello", emittedText())
        assertEquals(MaskDecision.NONE, emittedDecision())
    }

    @Test
    fun `Redact rewrites text in place with default replacement`() = runTest {
        val e = emitter(rules = listOf(SensitiveRule.Redact("secret")))
        e.emit(listOf(element("this is secret info")), 1080, 1920)
        assertEquals("this is [REDACTED] info", emittedText())
        assertEquals(MaskDecision.RULE_REDACT, emittedDecision())
    }

    @Test
    fun `Redact honors a custom replacement`() = runTest {
        val e = emitter(rules = listOf(SensitiveRule.Redact("SSN", replacement = "***")))
        e.emit(listOf(element("SSN is 123")), 1080, 1920)
        assertEquals("*** is 123", emittedText())
    }

    @Test
    fun `Strip nulls text on substring match`() = runTest {
        val e = emitter(rules = listOf(SensitiveRule.Strip("password")))
        e.emit(listOf(element("my password field")), 1080, 1920)
        assertNull(emittedText())
        assertEquals(MaskDecision.RULE_STRIP, emittedDecision())
    }

    @Test
    fun `rules run in declared order — Strip first short-circuits Redact`() = runTest {
        val e = emitter(
            rules = listOf(
                SensitiveRule.Strip("secret"),
                SensitiveRule.Redact("secret", replacement = "XXX")
            )
        )
        e.emit(listOf(element("this is secret info")), 1080, 1920)
        assertNull(emittedText())
        assertEquals(MaskDecision.RULE_STRIP, emittedDecision())
    }

    @Test
    fun `rules run in declared order — Redact first runs before the later Strip sees text`() = runTest {
        val e = emitter(
            rules = listOf(
                SensitiveRule.Redact("secret", replacement = "XXX"),
                SensitiveRule.Strip("secret") // "secret" is already gone by the time this runs
            )
        )
        e.emit(listOf(element("this is secret info")), 1080, 1920)
        assertEquals("this is XXX info", emittedText())
        assertEquals(MaskDecision.RULE_REDACT, emittedDecision())
    }

    @Test
    fun `later rules see the output of earlier redacts`() = runTest {
        val e = emitter(
            rules = listOf(
                SensitiveRule.Redact("foo", replacement = "bar"),
                SensitiveRule.Redact("bar", replacement = "baz")
            )
        )
        e.emit(listOf(element("foo")), 1080, 1920)
        assertEquals("baz", emittedText())
    }

    @Test
    fun `RedactRegex rewrites without interpreting special chars in replacement`() = runTest {
        // If Regex.replace(String, String) were used, "$1" would try to reference group 1.
        // The lambda form in the emitter must escape that so it lands literally.
        val e = emitter(
            rules = listOf(SensitiveRule.RedactRegex(Regex("""\d{3}-\d{2}-\d{4}"""), replacement = "\$1"))
        )
        e.emit(listOf(element("SSN 123-45-6789 end")), 1080, 1920)
        assertEquals("SSN \$1 end", emittedText())
    }

    @Test
    fun `StripRegex nulls text on regex match`() = runTest {
        val e = emitter(rules = listOf(SensitiveRule.StripRegex(Regex("""\d+"""))))
        e.emit(listOf(element("call 555")), 1080, 1920)
        assertNull(emittedText())
        assertEquals(MaskDecision.RULE_STRIP, emittedDecision())
    }

    @Test
    fun `Redact matches case-insensitively`() = runTest {
        val e = emitter(rules = listOf(SensitiveRule.Redact("SSN", replacement = "***")))
        e.emit(listOf(element("my ssn is 1234")), 1080, 1920)
        assertEquals("my *** is 1234", emittedText())
        assertEquals(MaskDecision.RULE_REDACT, emittedDecision())
    }

    @Test
    fun `Strip matches case-insensitively`() = runTest {
        val e = emitter(rules = listOf(SensitiveRule.Strip("PASSWORD")))
        e.emit(listOf(element("my password field")), 1080, 1920)
        assertNull(emittedText())
        assertEquals(MaskDecision.RULE_STRIP, emittedDecision())
    }

    @Test
    fun `RedactRegex honors caller's RegexOption`() = runTest {
        val e = emitter(
            rules = listOf(
                SensitiveRule.RedactRegex(Regex("email", RegexOption.IGNORE_CASE), replacement = "[E]")
            )
        )
        e.emit(listOf(element("EMAIL address")), 1080, 1920)
        assertEquals("[E] address", emittedText())
    }

    @Test
    fun `StripRegex honors caller's RegexOption`() = runTest {
        val e = emitter(
            rules = listOf(SensitiveRule.StripRegex(Regex("bearer", RegexOption.IGNORE_CASE)))
        )
        e.emit(listOf(element("Bearer XYZ")), 1080, 1920)
        assertNull(emittedText())
    }

    @Test
    fun `geometric strip nulls text when bounds intersect a mask rect`() = runTest {
        val e = emitter()
        val leaked = element("account: alice", x = 10, y = 10, w = 200, h = 40)
        val maskRect = Rect(0, 0, 500, 500) // covers the element
        e.emit(listOf(leaked), 1080, 1920, maskBounds = setOf(maskRect))
        assertNull(emittedText())
        assertEquals(MaskDecision.GEOMETRIC, emittedDecision())
    }

    @Test
    fun `geometric strip leaves non-intersecting elements alone`() = runTest {
        val e = emitter()
        val safe = element("far away", x = 1000, y = 1000, w = 50, h = 50)
        val maskRect = Rect(0, 0, 100, 100)
        e.emit(listOf(safe), 1080, 1920, maskBounds = setOf(maskRect))
        assertEquals("far away", emittedText())
        assertEquals(MaskDecision.NONE, emittedDecision())
    }

    @Test
    fun `Layer 1 decision is respected — rules and geometric strip skipped`() = runTest {
        val e = emitter(rules = listOf(SensitiveRule.Redact("hello", replacement = "XXX")))
        val alreadyMasked = element(text = null, maskDecision = MaskDecision.EXPLICIT)
        e.emit(listOf(alreadyMasked), 1080, 1920, maskBounds = setOf(Rect(0, 0, 500, 500)))
        assertNull(emittedText())
        // Decision preserved as EXPLICIT; not overwritten by GEOMETRIC or RULE_*.
        assertEquals(MaskDecision.EXPLICIT, emittedDecision())
    }

    @Test
    fun `debug snapshot is not produced when debugEmitter is null`() = runTest {
        val e = emitter(withDebug = false)
        e.emit(listOf(element("hello")), 1080, 1920)
        assertTrue(capturedEvents.isNotEmpty()) // wire event still emitted
        assertTrue(capturedSnapshots.isEmpty())
    }

    @Test
    fun `throwing debug callback does not prevent wire event publish`() = runTest {
        // Debug delivery is launched on an internal SupervisorJob scope. Its failure must
        // not cascade back into the emit path or drop the wire event.
        val e = WireframeEmitter(
            debugEmitter = { throw RuntimeException("boom") },
            dispatcher = Dispatchers.Unconfined
        )
        e.emit(listOf(element("hello")), 1080, 1920)
        assertEquals(1, capturedEvents.size)
    }

    // ---- Empty screen ----------------------------------------------------------------
    //
    // A screen with nothing to describe still has to be transmitted: "the content is
    // gone" is information the summarizer needs, or a replay keeps showing the last
    // populated wireframe after a logout, a navigation, or a modal dismissing. The
    // emitter deliberately does not short-circuit on an empty element list, and these
    // pin that — an `if (elements.isEmpty()) return` looks like an obvious optimization
    // and every other test in this file would still pass with it in place.
    // Mirrors the Flutter suite's "Empty screen" group.

    @Test
    fun `empty screen still emits a payload`() = runTest {
        val e = emitter()
        e.emit(emptyList(), 1080, 1920)

        assertEquals(1, capturedEvents.size)
        val payload = (capturedEvents.first().data as SessionEventData.WireframeCustomData).payload
        assertTrue("an empty screen ships zero elements", payload.elements.isEmpty())
        assertEquals("but still reports its viewport", listOf(1080, 1920), payload.viewport)
    }

    @Test
    fun `empty screen reaches the debug callback`() = runTest {
        val e = emitter()
        e.emit(emptyList(), 1080, 1920)

        assertEquals(1, capturedSnapshots.size)
        assertTrue(capturedSnapshots.first().elements.isEmpty())
    }

    @Test
    fun `a static empty screen dedups after the first emit`() = runTest {
        val e = emitter()
        e.emit(emptyList(), 1080, 1920)
        e.emit(emptyList(), 1080, 1920)

        assertEquals("a blank screen must not re-emit every frame", 1, capturedEvents.size)
    }

    @Test
    fun `an empty frame after a populated one emits`() = runTest {
        val e = emitter()
        e.emit(listOf(element("Account balance")), 1080, 1920)
        e.emit(emptyList(), 1080, 1920)

        assertEquals("clearing the screen is a change and must be sent", 2, capturedEvents.size)
        val second = (capturedEvents[1].data as SessionEventData.WireframeCustomData).payload
        assertTrue(second.elements.isEmpty())
    }

    @Test
    fun `dedup skips identical consecutive emits`() = runTest {
        val e = emitter()
        val els = listOf(element("hello"))
        e.emit(els, 1080, 1920)
        e.emit(els, 1080, 1920) // identical — should be dropped by dedup
        assertEquals(1, capturedEvents.size)
    }

    /**
     * Dedup state is per *session*, not per SDK lifetime.
     *
     * The emitter is built in `MPSessionReplayInstance.init` and survives a stop/start
     * cycle, so a new replay's first frame used to be compared against the previous
     * replay's last one. Backgrounding and foregrounding onto an unchanged screen then
     * dedups the opening `mp_wireframe` away, leaving a screenshot with nothing to
     * describe it — the one frame an AI summary most needs. `startRecording` calls
     * [WireframeEmitter.resetDedup] at the session boundary.
     */
    @Test
    fun `resetDedup re-emits an identical payload after a session restart`() = runTest {
        val e = emitter()
        val els = listOf(element("unchanged"))
        e.emit(els, 1080, 1920)
        e.resetDedup() // same screen, new session
        e.emit(els, 1080, 1920)
        assertEquals(2, capturedEvents.size)
    }

    @Test
    fun `dedup re-emits when a mask bound starts stripping text`() = runTest {
        val e = emitter()
        val els = listOf(element("hello"))
        e.emit(els, 1080, 1920, maskBounds = emptySet())
        e.emit(els, 1080, 1920, maskBounds = setOf(Rect(0, 0, 100, 100)))
        assertEquals(2, capturedEvents.size)
        assertEquals("hello", emittedText())
        assertNull(
            (capturedEvents[1].data as SessionEventData.WireframeCustomData)
                .payload.elements[0].text
        )
    }

    @Test
    fun `dedup skips a mask bound that moves without changing the wire`() = runTest {
        // Dedup keys off the finished payload, not the mask set. Mask rects are not on the
        // wire; they matter only through the text they strip, so a mask that misses every
        // element produces an identical render.
        val e = emitter()
        val els = listOf(element("hello", x = 1000, y = 1000)) // outside the mask below
        e.emit(els, 1080, 1920, maskBounds = emptySet())
        e.emit(els, 1080, 1920, maskBounds = setOf(Rect(0, 0, 100, 100)))
        assertEquals(1, capturedEvents.size)
    }

    @Test
    fun `dedup re-emits when only the viewport changes`() = runTest {
        // A rotation on a screen whose element list is unchanged still changes the render,
        // so the viewport is part of the dedup key.
        val e = emitter()
        e.emit(emptyList(), 1080, 1920)
        e.emit(emptyList(), 1920, 1080)
        assertEquals(2, capturedEvents.size)
    }

    @Test
    fun `dedup skips a frame whose only change is its mask decision`() = runTest {
        // maskDecision is debug-only metadata; the wire payload never carries it, so two
        // frames differing only there render the same.
        val e = emitter()
        e.emit(listOf(element(null, maskDecision = MaskDecision.EXPLICIT)), 1080, 1920)
        e.emit(listOf(element(null, maskDecision = MaskDecision.AUTO)), 1080, 1920)
        assertEquals(1, capturedEvents.size)
    }

    @Test
    fun `text longer than max is truncated with ellipsis`() = runTest {
        val long = "a".repeat(WireframeEmitter.MAX_TEXT_LEN + 10)
        emitter().emit(listOf(element(long)), 1080, 1920)
        val emitted = emittedText()!!
        // The ellipsis is paid for out of the budget, not added on top of it: the wire value
        // never exceeds MAX_TEXT_LEN, matching the ERD's 50-character cap.
        assertEquals(WireframeEmitter.MAX_TEXT_LEN, emitted.length)
        assertTrue(emitted.endsWith("…"))
        assertNotEquals(long, emitted)
    }

    @Test
    fun `text exactly at max length is not truncated`() = runTest {
        val exact = "a".repeat(WireframeEmitter.MAX_TEXT_LEN)
        emitter().emit(listOf(element(exact)), 1080, 1920)
        val emitted = emittedText()!!
        assertEquals(exact, emitted)
        assertFalse(emitted.endsWith("…"))
    }

    @Test
    fun `event is stamped with the frame's capture instant, not now`() = runTest {
        // The analyzer orders wireframes against touches (which carry MotionEvent.eventTime),
        // so a screen must report when it was on screen — not when the pipeline got to it.
        val capturedAtMs = 1_700_000_000_000L
        emitter().emit(listOf(element()), 1080, 1920, capturedAtMs = capturedAtMs)
        assertEquals(capturedAtMs, capturedEvents.first().timestamp)
        assertEquals(capturedAtMs, capturedSnapshots.first().timestamp)
    }

    @Test
    fun `bounds and viewport are unchanged at density 1`() = runTest {
        emitter().emit(listOf(element(x = 10, y = 20, w = 100, h = 40)), 1080, 1920, density = 1f)
        assertEquals(listOf(10, 20, 100, 40), emittedBounds())
        assertEquals(listOf(1080, 1920), emittedViewport())
    }

    @Test
    fun `bounds and viewport are scaled from raw px to logical px by density`() = runTest {
        // Raw device px in, 1x logical px out — matching the screenshot (captured at
        // 1/density) and the touch path ((rawX - offset) / density). 2.75 divides evenly here.
        emitter().emit(listOf(element(x = 275, y = 550, w = 550, h = 110)), 1080, 1920, density = 2.75f)
        // left 275/2.75=100, top 550/2.75=200; right 825/2.75=300 -> w=200; bottom 660/2.75=240 -> h=40
        assertEquals(listOf(100, 200, 200, 40), emittedBounds())
        assertEquals(listOf((1080 / 2.75f).toInt(), (1920 / 2.75f).toInt()), emittedViewport())
    }

    @Test
    fun `scaled bounds top-left matches the touch scalePoint formula for the same raw point`() = runTest {
        // A click at raw (x,y) is recorded as ((raw - windowOffset) / density).toInt(). Element
        // bounds are already window-relative (offset 0), so the wireframe's top-left must equal
        // the click's scaled point — otherwise clicks wouldn't overlay their target element.
        val density = 3f
        val rawX = 300
        val rawY = 600
        emitter().emit(listOf(element(x = rawX, y = rawY, w = 90, h = 30)), 1080, 1920, density = density)
        assertEquals((rawX / density).toInt(), emittedBounds()[0])
        assertEquals((rawY / density).toInt(), emittedBounds()[1])
    }

    @Test
    fun `non-positive density falls back to 1x scaling`() = runTest {
        emitter().emit(listOf(element(x = 10, y = 20, w = 100, h = 40)), 1080, 1920, density = 0f)
        assertEquals(listOf(10, 20, 100, 40), emittedBounds())
        assertEquals(listOf(1080, 1920), emittedViewport())
    }

    @Test
    fun `each element in a multi-element emit is processed independently`() = runTest {
        val e = emitter(rules = listOf(SensitiveRule.Redact("SSN", replacement = "***")))
        e.emit(
            listOf(
                element("SSN entry", x = 0, y = 0),
                element("safe label", x = 200, y = 0),
                element("SSN again", x = 400, y = 0)
            ),
            1080,
            1920
        )
        assertEquals("*** entry", emittedText(0))
        assertEquals("safe label", emittedText(1))
        assertEquals("*** again", emittedText(2))
        assertEquals(MaskDecision.RULE_REDACT, emittedDecision(0))
        assertEquals(MaskDecision.NONE, emittedDecision(1))
        assertEquals(MaskDecision.RULE_REDACT, emittedDecision(2))
    }

    @Test
    fun `geometric strip evaluates each mask rect independently`() = runTest {
        val e = emitter()
        val hit = element("covered", x = 50, y = 50, w = 20, h = 20)
        val miss = element("clear", x = 900, y = 900, w = 20, h = 20)
        val masks = setOf(
            Rect(0, 0, 100, 100), // covers `hit`
            Rect(500, 500, 600, 600) // covers neither
        )
        e.emit(listOf(hit, miss), 1080, 1920, maskBounds = masks)
        assertNull(emittedText(0))
        assertEquals("clear", emittedText(1))
        assertEquals(MaskDecision.GEOMETRIC, emittedDecision(0))
        assertEquals(MaskDecision.NONE, emittedDecision(1))
    }

    // MARK: - Declared text (mpWireframeText(...))

    @Test
    fun `declared text survives the geometric strip even when a mask fully overlaps`() = runTest {
        // Customer-authored text is intentionally sent even when the view is masked — masking
        // grays the pixels while the declared text still describes the view for the AI summary.
        val e = emitter()
        val declared = element("monthly spend", x = 10, y = 10, w = 200, h = 40, maskDecision = MaskDecision.DECLARED)
        val maskRect = Rect(0, 0, 500, 500) // fully covers the element
        e.emit(listOf(declared), 1080, 1920, maskBounds = setOf(maskRect))
        assertEquals("monthly spend", emittedText())
        assertEquals(MaskDecision.DECLARED, emittedDecision())
    }

    @Test
    fun `declared text is still scrubbed by sensitive rules`() = runTest {
        // Layer 2 (geometric) is skipped for declared text, but Layer 4 (user rules) still runs
        // as a safety net.
        val e = emitter(rules = listOf(SensitiveRule.Strip("password")))
        val declared = element("my password field", maskDecision = MaskDecision.DECLARED)
        e.emit(listOf(declared), 1080, 1920)
        assertNull(emittedText())
        assertEquals(MaskDecision.RULE_STRIP, emittedDecision())
    }

    @Test
    fun `declared text is still redacted by sensitive rules`() = runTest {
        val e = emitter(rules = listOf(SensitiveRule.Redact("secret", replacement = "XXX")))
        val declared = element("this is secret info", maskDecision = MaskDecision.DECLARED)
        e.emit(listOf(declared), 1080, 1920)
        assertEquals("this is XXX info", emittedText())
        assertEquals(MaskDecision.RULE_REDACT, emittedDecision())
    }

    @Test
    fun `debug snapshot toJson produces valid JSON containing maskDecision and text`() = runTest {
        val e = emitter(rules = listOf(SensitiveRule.Redact("SSN", replacement = "***")))
        e.emit(listOf(element("SSN entry")), 1080, 1920)
        val json = capturedSnapshots.first().toJson()
        // Not asserting the whole structure — just that the debug-only field is present
        // and the transformed text made it through. If serialization silently breaks
        // (e.g. MaskDecision loses @Serializable), one of these fails.
        assertTrue("expected maskDecision in $json", json.contains("\"maskDecision\""))
        assertTrue("expected RULE_REDACT in $json", json.contains("RULE_REDACT"))
        assertTrue("expected redacted text in $json", json.contains("*** entry"))
        assertTrue("expected viewport in $json", json.contains("\"viewport\""))
    }
}
