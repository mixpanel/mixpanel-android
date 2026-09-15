package com.mixpanel.android.sessionreplay.wireframe

import android.graphics.Rect
import androidx.annotation.RestrictTo
import com.mixpanel.android.sessionreplay.logging.Logger
import com.mixpanel.android.sessionreplay.models.SensitiveRule
import com.mixpanel.android.sessionreplay.models.SessionEvent
import com.mixpanel.android.sessionreplay.models.SessionEventData
import com.mixpanel.android.sessionreplay.tracking.EventPublisher
import com.mixpanel.android.sessionreplay.utils.EventType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Builds a wireframe `mp_wireframe` rrweb Custom event and publishes it onto the existing
 * session-replay event stream alongside screenshots and touches.
 *
 * Wire format (per client spec):
 *  ```
 *  { "type": 5, "timestamp": <ms>, "data": {
 *      "tag": "mp_wireframe",
 *      "payload": { "viewport": [w,h], "elements": [...] }
 *  }}
 *  ```
 *
 * Identical consecutive frames are deduped so static screens don't spam events. The dedup
 * key is the hash of the finished [WireframePayload] — see [lastPayloadHash].
 *
 * **Element order is preserved end to end.** Every stage here is an order-preserving
 * `map`; nothing sorts, filters, or reorders, so the list reaches the wire in the order
 * the view walk produced it (breadth-first on the View path — see
 * [com.mixpanel.android.sessionreplay.sensitive_views.SensitiveViewManager.processSubviews]).
 * The order carries no contract of its own; it is preserved so goldens stay deterministic.
 *
 * Threading: [emit] is `suspend` and moves *all* work — dedup hashing, element processing,
 * JSON serialization, event publish, and the debug callback — off the caller thread onto
 * [dispatcher]. Defaults to [Dispatchers.Default] since the workload is CPU-bound (regex
 * matching, JSON, rect intersection) and matches the pattern the screenshot compression
 * path already uses. The wireframe pipeline never touches views once emit is invoked.
 *
 * **Not public API despite the `public` modifier.** This is `@RestrictTo(LIBRARY_GROUP)` rather
 * than `internal` so the off-device coordinate goldens in `:session-replay:wireframe-goldens` can
 * drive [processForTesting] from their own Gradle module — they have to live outside this one
 * because Paparazzi's layoutlib `android.jar` cannot share a unit-test classpath with the
 * Robolectric/mockk suites here. Same convention `:common` uses for its cross-module surface.
 * Nothing outside the `com.mixpanel.android` group should touch this class.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class WireframeEmitter(
    sensitiveRules: List<SensitiveRule> = emptyList(),
    private val debugEmitter: ((WireframeDebugSnapshot) -> Unit)? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    // SupervisorJob so one failing debug callback can't kill sibling deliveries; scope is
    // cancelled from [cancel] when the emitter is torn down.
    private val debugScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
) {
    // Defensive copy: if the caller passed a mutableListOf and mutates it later, we don't
    // want the emitter's ordering guarantees to shift out from under it.
    private val sensitiveRules: List<SensitiveRule> = sensitiveRules.toList()

    /**
     * Hash of the last published [WireframePayload] — i.e. of exactly the bytes that went
     * on the wire, after geometric masking, sensitive rules, glyph/blank normalization,
     * truncation, and density scaling.
     *
     * Hashing the *finished* payload rather than the raw walker output is deliberate. The
     * ERD defines dedup as "identical renders collapse to one", and only the payload
     * determines the render. Raw elements are a strictly worse key in both directions:
     * text that differs upstream but processes to the same wire value (a masked field
     * being typed into, a redacted value matching the same rule each frame) re-emits a
     * byte-identical event every capture, and bounds that differ sub-pixel but round to
     * the same ints do the same.
     *
     * This subsumes the old separate mask-bounds hash. Mask rects are not on the wire;
     * they matter only through the text they strip, which is already baked into the
     * payload. A mask that moves without changing any element's text produces an
     * identical render and should dedup. Matches iOS `lastPayloadHash` and Flutter
     * `_lastPayloadHash`.
     *
     * @Volatile provides a memory barrier so concurrent emits (running on the shared
     * Default dispatcher pool) at least see fresh reads. The check-then-set is still
     * racy — worst case is one duplicate emit, never corrupt state.
     */
    @Volatile
    private var lastPayloadHash: Int? = null

    /**
     * Builds and publishes a wireframe Custom event from the elements collected during the
     * view walk.
     *
     * Element bounds and [viewportWidthPx]/[viewportHeightPx] arrive as raw device pixels
     * (from `getGlobalVisibleRect`/`boundsInWindow`). They are converted here to 1x logical
     * pixels by dividing by [density], so the wireframe shares the exact coordinate space as
     * the screenshot bitmap (captured at `1/density`) and the scaled touch points
     * (`(rawX - windowOffset) / density`). Without this, bounds would be off by the device
     * density factor and wouldn't overlay clicks or the screenshot in the player.
     *
     * Geometric masking still runs in raw pixels (see [process]) because [maskBounds] are
     * raw too; only the final wire/debug bounds are scaled.
     *
     * @param density Display density used to convert raw px to 1x logical px. Falls back to
     *   1f if non-positive.
     * @param maskBounds Rects that were (or will be) painted gray on the screenshot. Any
     *   wireframe element whose bounds intersect one of these has its text stripped so the
     *   wireframe can't leak content the screenshot has visually redacted.
     * @param capturedAtMs Wall-clock instant the frame this wireframe describes was captured.
     *   Production passes the screenshot's capture time so both events report when the screen
     *   was shown rather than when they were encoded — the analyzer orders wireframes against
     *   touches (which carry `MotionEvent.eventTime`) to decide which screen a tap acted on.
     *   Defaults to now for tests and any caller with no frame to align to.
     */
    suspend fun emit(
        elements: List<WireframeElement>,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        density: Float = 1f,
        maskBounds: Set<Rect> = emptySet(),
        capturedAtMs: Long = Date().time
    ) {
        // Move everything — processing and dedup hashing included — off the caller thread.
        // `emit` is typically invoked from the main-thread view-walk site; nothing here
        // needs main, and processing a 200-element list is CPU work we shouldn't spend
        // there. Dedup runs after processing (see [lastPayloadHash]), so a deduped frame
        // costs one pipeline pass — rect intersection and regex over a few hundred
        // elements, negligible against the JPEG compression happening alongside it.
        withContext(dispatcher) {
            val safeDensity = density.takeIf { it > 0f } ?: 1f
            val viewportW = scaleToLogical(viewportWidthPx, safeDensity)
            val viewportH = scaleToLogical(viewportHeightPx, safeDensity)

            val processed: List<WireframeElement>
            val payload: WireframePayload
            try {
                processed = elements.map { it.process(maskBounds) }
                payload = WireframePayload(
                    viewport = listOf(viewportW, viewportH),
                    elements = processed.map { it.toJson(safeDensity) }
                )
            } catch (e: Exception) {
                Logger.error { "Failed to build wireframe payload: ${e.message}" }
                return@withContext
            }

            val payloadHash = payload.hashCode()
            if (lastPayloadHash == payloadHash) return@withContext

            val event = SessionEvent(
                type = EventType.CUSTOM,
                data = SessionEventData.WireframeCustomData(tag = TAG, payload = payload),
                timestamp = capturedAtMs
            )

            // Deliberately content-free: element text is customer data, and the SDK log is
            // a channel they did not opt into and cannot filter. Anyone who wants to see
            // what a wireframe says installs DebugOptions.wireframeEmitter, which is
            // opt-in, stays on device, and reports each element's maskDecision alongside
            // its text. Counts and viewport are enough to confirm capture is running.
            Logger.debug {
                "Emitting wireframe (elements=${elements.size}, viewport=${viewportW}x$viewportH)"
            }

            lastPayloadHash = payloadHash
            EventPublisher.shared.publishCustomEvent(event)

            // Debug snapshot: only built when a debugEmitter is installed. Fired on
            // [debugScope] so a slow user callback can't stall the screenshot pipeline —
            // the wire event above is already published by the time we launch. Isolated
            // so a failing callback (or serialization hiccup) can't take down emit either.
            debugEmitter?.let { deliver ->
                debugScope.launch {
                    val snapshot = try {
                        WireframeDebugSnapshot(
                            timestamp = event.timestamp,
                            viewport = listOf(viewportW, viewportH),
                            elements = processed.map { it.toDebugElement(safeDensity) }
                        )
                    } catch (e: Exception) {
                        Logger.warn("Failed to build debug wireframe snapshot: ${e.message}")
                        return@launch
                    }
                    try {
                        deliver(snapshot)
                    } catch (e: Exception) {
                        Logger.warn("DebugOptions.wireframeEmitter callback threw: ${e.message}")
                    }
                }
            }
        }
    }

    /** Cancels the internal debug scope. Call from teardown (e.g. `deinitialize`). */
    fun cancel() {
        debugScope.cancel()
    }

    /**
     * Clears [lastPayloadHash] so the next emit publishes even if the render is identical.
     *
     * Dedup is scoped to a *recording session*, but this emitter is built once in
     * `MPSessionReplayInstance.init` and survives a stop/start cycle — so the boundary has
     * to be announced. [com.mixpanel.android.sessionreplay.MPSessionReplayInstance.startRecording]
     * calls this alongside `flushService.start()`, which is where the new replay id is minted.
     *
     * Without it, a background/foreground onto an unchanged screen compares the new
     * replay's first frame against the *previous* replay's last one and dedups it away,
     * shipping an opening screenshot with no `mp_wireframe` to describe it. Android is the
     * guaranteed case of the three: `stopRecording` resets `initialScreenshotCaptured`, so
     * `startRecording` always forces an initial capture. Mirrors iOS `resetDedup()` and
     * Flutter `resetDedup()`.
     */
    fun resetDedup() {
        lastPayloadHash = null
    }

    /**
     * Test seam mirroring the element-processing half of [emit] without the coroutine
     * dispatch, dedup hashing, event publish, or debug callback. Runs Layer 2 (geometric
     * strip) and Layer 4 (sensitive rules) via [process], then applies the same blank
     * null-out and [MAX_TEXT_LEN]-char truncation the wire path uses in [toJson], so the returned
     * [WireframeElement.text] is byte-identical to what would ship.
     *
     * Unlike [emit] it does **not** scale by density — golden tests operate in the raw
     * pixel space of the incoming bounds, matching iOS's manual frames. Those bounds are
     * mockk-stubbed in the Robolectric suite and come from a real layoutlib measure/layout pass
     * in `:session-replay:wireframe-goldens`. [WireframeElement.maskDecision] is preserved so
     * golden output can assert the full post-masking shape.
     *
     * Test-only; production goes through [emit]. Mirrors iOS
     * `WireframeEmitter.processedElementsForTesting(elements:maskBounds:)`.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    fun processForTesting(
        elements: List<WireframeElement>,
        maskBounds: Set<Rect> = emptySet()
    ): List<WireframeElement> = elements.map { element ->
        val processed = element.process(maskBounds)
        processed.copy(text = processed.displayText()?.let(::truncate))
    }

    /**
     * Runs Layer 2 (geometric strip) and Layer 4 (sensitive rules) on an element whose
     * text survived the view-walk masking (Layer 1) or was substituted by Layer 3.
     * Returns the element as-is when no transformation applies.
     */
    private fun WireframeElement.process(maskBounds: Set<Rect>): WireframeElement {
        // Layer 1 already decided — trust it. DECLARED is the one decision that continues
        // through the pipeline: Layer 3 substituted the text, but Layer 4 still runs.
        if (maskDecision != MaskDecision.NONE && maskDecision != MaskDecision.DECLARED) return this
        val original = displayText() ?: return this

        // Customer-declared text (`mpWireframeText(...)`) is authored, not
        // scraped, and is intentionally sent even when the view is masked. Skip the
        // geometric strip — otherwise the view's own mask region would null it — but
        // still run the configured sensitive rules below as a safety net.
        if (maskDecision != MaskDecision.DECLARED) {
            applyGeometricStrip(original, maskBounds)?.let { return it }
        }
        return applyRules(original)
    }

    /**
     * Layer 2: if the element's bounds overlap any mask rect, drop its text. This closes
     * the leak where a sensitive ancestor covers a child on the screenshot but the child's
     * text would otherwise pass through the wireframe.
     * Returns a copy with text nulled and decision tagged when triggered, or null if the
     * element is unaffected.
     */
    private fun WireframeElement.applyGeometricStrip(
        text: String,
        maskBounds: Set<Rect>
    ): WireframeElement? {
        if (maskBounds.isEmpty()) return null
        val elementRect = Rect(x, y, x + w, y + h)
        return if (maskBounds.any { Rect.intersects(it, elementRect) }) {
            copy(text = null, maskDecision = MaskDecision.GEOMETRIC)
        } else {
            null
        }
    }

    /**
     * Layer 4: apply user-configured [SensitiveRule]s in declared order. A strip
     * short-circuits and returns null; a redact rewrites the running text and the next
     * rule sees the update. Uses [text] rather than `this.text` so callers can pass the
     * value they already null-checked without another `!!`.
     */
    private fun WireframeElement.applyRules(text: String): WireframeElement {
        var current = text
        var redacted = false
        for (rule in sensitiveRules) {
            when (rule) {
                is SensitiveRule.StripRegex ->
                    if (rule.regex.containsMatchIn(current)) {
                        return copy(text = null, maskDecision = MaskDecision.RULE_STRIP)
                    }
                is SensitiveRule.Strip ->
                    if (rule.text.isNotEmpty() && current.contains(rule.text, ignoreCase = true)) {
                        return copy(text = null, maskDecision = MaskDecision.RULE_STRIP)
                    }
                is SensitiveRule.RedactRegex ->
                    if (rule.regex.containsMatchIn(current)) {
                        // Lambda form avoids `$1`/`\` interpretation in the replacement.
                        current = rule.regex.replace(current) { rule.replacement }
                        redacted = true
                    }
                is SensitiveRule.Redact ->
                    if (rule.text.isNotEmpty() && current.contains(rule.text, ignoreCase = true)) {
                        current = current.replace(rule.text, rule.replacement, ignoreCase = true)
                        redacted = true
                    }
            }
        }
        return if (redacted) copy(text = current, maskDecision = MaskDecision.RULE_REDACT) else this
    }

    private fun WireframeElement.toJson(density: Float): WireframeElementJson {
        val cleanText = displayText()?.let(::truncate)
        return WireframeElementJson(
            role = type.wireName(),
            text = cleanText,
            bounds = scaledBounds(density)
        )
    }

    private fun WireframeElement.toDebugElement(density: Float): WireframeDebugSnapshot.DebugElement {
        val cleanText = displayText()?.let(::truncate)
        return WireframeDebugSnapshot.DebugElement(
            role = type.wireName(),
            text = cleanText,
            bounds = scaledBounds(density),
            maskDecision = maskDecision
        )
    }

    /**
     * Converts this element's raw-device-pixel bounds to 1x logical pixels. Each edge uses the
     * same `/ density` + truncation as the screenshot ([ScreenRecorder] `calculateBitmapScale`)
     * and the touch path ([TouchEventRecorder] `scalePoint`), so a click at a raw point lands
     * inside the matching element's scaled bounds. Width/height are derived from the scaled
     * edges (not scaled independently) to keep right/bottom edges aligned.
     */
    private fun WireframeElement.scaledBounds(density: Float): List<Int> {
        val left = scaleToLogical(x, density)
        val top = scaleToLogical(y, density)
        val right = scaleToLogical(x + w, density)
        val bottom = scaleToLogical(y + h, density)
        return listOf(left, top, right - left, bottom - top)
    }

    /**
     * Text worth including in a payload, or null. Centralizes the null/blank/glyph check.
     *
     * Blank text and bare icon-font glyphs are nulled, but the element itself is always
     * kept — a textless `text`/`button`/`image` is still meaningful structure (role +
     * bounds) per the Wireframe Capture Contract.
     *
     * Declared text is exempt: it is authored by the developer rather than scraped, so it
     * is taken verbatim rather than second-guessed for blankness or glyph content. (It is
     * still truncated, and `SensitiveViewManager.wireframeTextFor` already rejects a blank
     * declared string at the source.)
     *
     * Mirrors iOS `cleanTextForWire` and Flutter `_cleanText`.
     */
    private fun WireframeElement.displayText(): String? {
        val raw = text ?: return null
        if (maskDecision == MaskDecision.DECLARED) return raw
        if (raw.isBlank()) return null
        return raw.takeIf { isHumanReadable(it) }
    }

    /**
     * Caps text at [MAX_TEXT_LEN] characters *including* the ellipsis, so the wire value never
     * exceeds the ERD's 50-character limit (the service's `MAX_ELEMENT_TEXT` budget is the same
     * number). The ellipsis is kept — it tells the summarizer the label was cut rather than
     * ending mid-word — but it is paid for out of the budget, not added on top of it.
     */
    private fun truncate(text: String): String =
        if (text.length <= MAX_TEXT_LEN) text else text.substring(0, MAX_TEXT_LEN - 1) + "…"

    /** Raw device px → 1x logical px, matching the screenshot/touch `/ density` + `toInt()`. */
    private fun scaleToLogical(px: Int, density: Float): Int = (px / density).toInt()

    companion object {
        const val TAG: String = "mp_wireframe"
        const val MAX_TEXT_LEN: Int = 50

        /**
         * True if [text] contains at least one character outside the Unicode private-use
         * area (U+E000–U+F8FF), where icon fonts place their glyphs. A string that fails
         * this check is a bare icon glyph (a Material Icons codepoint, say) rather than
         * human-readable content, and shipping it would hand the summarizer garbage.
         *
         * Text carrying *any* readable character is kept verbatim, so a label like
         * "Settings ⚙" survives intact.
         *
         * Scanning `Char`s rather than code points is equivalent for this range: both
         * halves of a surrogate pair fall in U+D800–U+DFFF, below the PUA, so any
         * supplementary character short-circuits to `true` exactly as a code-point scan
         * would — and it avoids `String.codePoints()`, which is API 24+.
         *
         * Mirrors iOS `WireframeEmitter.isHumanReadable` and Flutter
         * `wireframeTextIsHumanReadable`.
         */
        internal fun isHumanReadable(text: String): Boolean =
            text.any { it.code < 0xE000 || it.code > 0xF8FF }
    }
}
