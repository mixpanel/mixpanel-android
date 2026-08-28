# Mixpanel Android Session Replay — Internal Notes

User-facing docs live in [README.md](README.md). This file captures internal
design decisions for the wireframe/AI-summary work so they aren't re-litigated.

## Wireframe Capture Notes (2026-07-29)

**The `mp_wireframe` event is a cross-platform contract — Android, Flutter, and
iOS must all follow it.** This section records how the Android implementation
realizes that contract and where the reasoning isn't obvious from the code; the
same section exists in the Flutter session-replay `CLAUDE.md`. If the platforms
disagree, that is a bug in one of them, not a local Android choice.

- **Only four semantic roles are emitted:** `text`, `button`, `input`, `image`
  (`WireframeType`). Layout/containers (`ViewGroup`/`LinearLayout`/
  `ConstraintLayout`, Compose `Box`/`Row`/`Column`) are never emitted —
  `classifyAndroidView` returns null for them and `collectWireframeForNode` hits
  `else -> return`. The payload is a flat list of `{role, text?, bounds}`, not a
  view hierarchy.
- **Every collected element is emitted, even when textless.** A textless
  `button`/`input`/`image` is meaningful structure — e.g. two textless `input`
  shells + a `Log in` button reads as a login form. Existence + position + role
  is not customer content, so an element is never dropped merely for lacking
  text. (Input fields are always textless by security design — `TEXT_ENTRY`.)
- **Text population depends on screenshot masking, not element type:**
  - *Not screenshot-masked* → `text` = visible text; if absent, fall back to the
    platform accessibility label (Android `contentDescription` / Compose
    `ContentDescription`; Flutter `semanticLabel` / tooltip), unless
    `WireframesOptions.useAccessibilityLabelFallback` is off. Then run user
    `SensitiveRule`s over the result. Rationale: if it's visible it's already in
    the unmasked screenshot; the customer's lever is masking the view.
  - *Screenshot-masked* (explicit mask, auto text/image mask, geometric overlap,
    input `TEXT_ENTRY`) → keep the `role + bounds` shell with `text = null`.
    Nothing hidden on the screenshot leaves the device.
- **Masking vs. rules are distinct.** A *screenshot mask* grays pixels → drop
  text (shell kept). `SensitiveRule`s are a wireframe-only text filter (pixels
  still visible) → element kept; strip nulls text, redact rewrites it.
- **Accessibility labels are opt-in (reversed 2026-08-24; was opt-out, decided
  2026-08-14).** `WireframesOptions.useAccessibilityLabelFallback`, default
  **`false`** on all three platforms. The residual risk is what decided it: a
  label can describe more than what's visible (an icon whose
  `contentDescription` holds PII), and because it is never drawn, the "mask what
  you can see in the replay" workflow can't reach it — so the customer has no
  way to audit it, and a default that ships un-auditable text is the wrong
  default. The cost is understood and accepted: with the fallback off every
  icon-only control (nav bars, `IconButton`, FABs, toolbar actions) is a bare
  shell, and naming those is much of what an AI summary wants from a toolbar.
  Customers who want them named set it `true`, or describe elements with
  `mpWireframeText(...)`, which the flag deliberately does not gate — declared
  text is authored, not scraped. `SensitiveRule`s run over label text either
  way, and screenshot-masked views never expose labels.
  - **The internal mirrors default off too**, so "nobody set it" and "the
    customer took the default" behave the same: `SensitiveViewManager
    .useAccessibilityLabelFallback` and what `deinitialize()` resets it to
    (Android), `SensitiveViewManager.shared` (iOS), and the `MaskDetector` /
    `ScreenshotCapturer` parameters (Flutter).
  - **The golden harnesses force it on** (Android `resetMaskingState`, iOS
    `WireframeGoldenTestUtils`, Flutter `golden_test_utils`) and the
    `*_fallbackOff_*` cases turn it back off per-case, so the fixtures still
    cover both tiers and none of them had to be re-recorded.
  - The label is only ever the *third* tier: declared `wireframeText` → visible
    text → label. That ordering is what makes "fallback" accurate even for
    images, which have no visible text of their own.
  - With the fallback off, a **Compose** node whose only content is a label is
    dropped rather than emitted as an empty text shell — the label was the sole
    evidence it was content at all, so keeping a shell would emit the labeled
    *containers* the four-role rule above excludes. Nodes carrying a role
    (`Role.Image`, `Role.Button`) still emit textless, as does every Android
    view, since `classifyAndroidView` never consults the label.

## Icon-glyph normalization (added 2026-08-17)

`WireframeEmitter.isHumanReadable` nulls text made *entirely* of Unicode
private-use-area codepoints (U+E000–U+F8FF), where icon fonts live — a Material
icon `TextView` would otherwise ship its glyph as element text and hand the
summarizer garbage. The element keeps its role + bounds shell; only the text is
dropped. Any single readable character keeps the whole string, so "Settings ⚙"
survives.

- **Declared text is exempt**, matching iOS `cleanTextForWire` and Flutter
  `_cleanText`: it is authored, not scraped, so the SDK does not second-guess
  the codepoints a developer chose.
- The check lives in the emitter (`displayText`), not in text extraction, so a
  glyph does *not* fall through to the `contentDescription` tier. That matches
  iOS, where `extractWireframeText` has no glyph guard either. Flutter's extra
  guard in `mask_detector.dart` is narrower than it looks — it only stops
  descendant glyph paragraphs from being aggregated into a synthesized button
  label, a mechanism neither native platform has.

**Platform alignment status (2026-08-17):**
- Android — aligned: emits textless shells (adds the element regardless of null
  text), reads `contentDescription` as a fallback on both the View and Compose
  paths gated by `useAccessibilityLabelFallback`, and normalizes icon-font
  glyphs. Coordinate goldens now exist for both UI toolkits — see "Wireframe
  goldens run off-device" below.
- Flutter — aligned (status as of 2026-08-24): `WireframesOptions
  .useAccessibilityLabelFallback` gates the `semanticLabel` tier and the
  `Tooltip` message fallback in `mask_detector.dart`, which is invisible in
  normal use for the same reason.
- iOS — aligned (status as of 2026-08-24): `MPWireframesOptions
  .useAccessibilityLabelFallback` gates `extractWireframeText`'s
  `accessibilityLabel` tier.

## Declared wireframe text vs. masking (decided 2026-07-30)

Masking and developer-declared text are **orthogonal**. This mirrors the iOS
`.mpReplaySensitive(_:)` / `.mpWireframeText(_:)` split and must stay in parity.

- Entry points, **one concern each** (revised 2026-08-20): `View.mpReplaySensitive(Boolean)`
  / `Modifier.mpReplaySensitive(Boolean)` for pixels, and `View.mpWireframeText(String?)`
  / `Modifier.mpWireframeText(String)` for declared text. Chain them to do both:
  `avatar.mpReplaySensitive(true).mpWireframeText("profile photo")`.
  - **The combined `mpReplay(sensitive:wireframeText:)` was removed** from both
    platforms that had it (Android and iOS). It duplicated `mpReplaySensitive` — two
    public ways to mask —
    which is the thing the split exists to stop; and its two nullable parameters made
    "declare text without touching masking" read as an accident of defaulting rather than
    a first-class call. Do not reintroduce a combined entry point. iOS mirrors this
    exactly (`View+MPReplay.swift` keeps only the shared `MPReplayWrapper` plumbing and
    `.mpWireframeText(_:)`); on the `View` API `mpWireframeText` takes `String?` because a
    `View` is long-lived and `null` must be able to clear a prior declaration, while the
    two declarative APIs (Compose `Modifier`, SwiftUI `View`) take a non-null `String`
    because removal is expressed by dropping the modifier.
  - **Flutter deliberately did not follow.** Its declared text is a `wireframeText:`
    parameter on `MixpanelMask`/`MixpanelUnmask`, not a second masking API, so it never
    had the duplication being cleaned up. The accepted consequence is that Flutter has no
    way to declare text *without* also choosing mask or unmask — there is no
    `MixpanelWireframeText` widget. Tyler's call, 2026-08-20; reopen before adding one.
- Declared View text is stored in a weak-keyed identity map in `SensitiveViewManager`
  (not `View.setTag`, matching the existing sensitive-view idiom); Compose text rides a
  `SemanticsPropertyKey<String>` (`mpReplayWireframeText`).
- `MaskDecision.DECLARED` marks text authored by the developer rather than
  scraped from the view (Layer 3 substitution). Declared text is **exempt from the
  Layer 2 geometric strip** (including the view's own mask region) in
  `WireframeEmitter.process`, so it survives even when the view is masked — masking
  still grays the pixels via the mask region added during the walk; the declared text
  still describes the view for the AI summary. **Layer 4 sensitive rules still run**
  over declared text as a safety net, and may replace the decision with `RULE_STRIP` /
  `RULE_REDACT`.
- **Input fields are labeled too.** An `EditText`/`TextField` carrying `wireframeText`
  emits that label (per the ERD's `TextField(wireframeText = "Card number")` example);
  the typed value is never emitted. Note the older SDK Design & API Surface appendix
  says the opposite ("input fields are never labeled") — the ERD wins, and the appendix
  needs correcting for iOS/Flutter parity.
- Rationale: masking is an opaque rectangle over the pixels, not a blur — the pixels
  are never captured. Declared text is not read from those pixels, so masking has no
  bearing on it. It is the developer's responsibility to ensure `wireframeText` is
  not itself sensitive; if it could be, omit it.
- Declared text is emitted even for views that don't classify into one of the four
  roles (fall back to `text`), because the developer explicitly opted the element in.

## `addSensitiveClass` reports EXPLICIT (decided 2026-08-07)

Per the ERD's Layer 1 table, a class registered via `addSensitiveClass` is a developer
opt-in and reports `MaskDecision.EXPLICIT`, alongside `addSensitiveView` and
`mpReplaySensitive(true)`. Only the `AutoMaskedView` classes
(`TextView`/`ImageView`/`WebView`) report `AUTO`. Previously both reported `AUTO`,
because `SensitiveViewManager._sensitiveClasses` is one bag holding both; customer
registrations are now mirrored into `_customerSensitiveClasses` so the walk can tell
them apart.

- **Reporting only.** The pixels masked are identical either way. What changes is
  `DebugOptions.wireframeEmitter`'s `maskDecision` and the debug overlay fill, which reads the same
  `InternalMaskDecision` — a registered class now paints red (mask) rather than orange
  (auto). That overlay shift is intentional: one taxonomy, two consumers.
- **`addSafeView` still overrides a class match** (`shouldMask` in `processSubviews`),
  which `addSensitiveView` does not allow. So EXPLICIT here does not mean
  "unconditionally masked"; the masking behavior was left untouched deliberately, since
  changing it would change which pixels ship. Both cases are pinned by goldens
  (`wireframe_class_explicit_masked.json`, `wireframe_class_safe_kept.json`).
- Side effect of the split: disabling `AutoMaskedView.Text` no longer un-registers a
  `TextView` the developer explicitly passed to `addSensitiveClass`.
- Compose has no class-based path at all (`config.isSensitiveView()` only), so this is
  View-hierarchy-only.
- **iOS aligned 2026-08-18** (was reporting `AUTO`). `SensitiveViewManager.swift` now reads
  `(view.mpReplaySensitive == true || sensitiveClasses.contains { view.isKind(of: $0) })
  ? .mask : .auto`. iOS needed no bag-splitting — `sensitiveClasses` is only ever populated
  by `addSensitiveClass`, and auto-detection caches into a separate `knownSensitiveViews`.
  The membership is tested directly rather than read off whichever cache won, because
  `isSensitiveView` checks auto-detection *before* `sensitiveClasses`, so a `UILabel`
  subclass that is both would otherwise short-circuit to `AUTO`. Same "an unmask still
  overrides a class match" rule as `addSafeView` here. Pinned by iOS goldens
  `wireframe_class_explicit_masked` / `_safe_kept` / `_explicit_beats_auto`, mirroring the
  Android pair, plus `testRegisteredClass_reportsExplicitButMasksIdentically`, which masks
  one view both ways and asserts equal mask rects.
- **Flutter does not participate** — it is widget-based with no class-registration API, so
  there is nothing to align there.

## rrweb-faithful touch tracking (decided 2026-08-10)

Touch capture now follows the rrweb spec
([`packages/types/src/index.ts`](https://github.com/rrweb-io/rrweb/blob/master/packages/types/src/index.ts))
rather than approximating it. `TouchEventRecorder` reads `MotionEvent` actions directly —
the old `GestureDetector` is gone — and a gesture emits:

| Phase | rrweb encoding |
| --- | --- |
| `ACTION_DOWN` | `source: 2` (MouseInteraction), `type: 7` (TouchStart) |
| `ACTION_MOVE` | `source: 6` (TouchMove), batched `positions[]` |
| `ACTION_UP` | `source: 2`, `type: 9` (TouchEnd) |
| `ACTION_CANCEL` | `source: 2`, `type: 10` (TouchCancel) |

- **Two enum values were wrong before.** `IncrementalSource.TOUCH_MOVE` was `1` (that's
  `MouseMove`; TouchMove is `6`) and `TOUCH_INTERACTION` is spelled `MouseInteraction` in
  the spec — renamed to `IncrementalSource.MOUSE_INTERACTION`. `TouchInteraction.START` is
  replaced by the full `MouseInteraction` enum. iOS still uses the old names
  (`IncrementalSource.touchInteraction`, `TouchInteraction.start`); Flutter has
  `RRWebMouseInteraction.touchStart` only. Both need the same treatment.
- **`SessionPosition.timeOffset` no longer serializes as `time_offset`.** The rrweb payload
  is camelCase throughout (`tagName`, `textContent`, `childNodes`); only the Mixpanel
  ingestion envelope (`batch_start_time`, `replay_id`) is snake_case. Nothing had ever
  emitted `PositionData` on any platform, so no shipped payload changes shape — but this
  is the first time the field goes over the wire, so it's worth confirming against
  ingestion. `timeOffset` is `<= 0`, measured against the batch's final sample, which is
  also the event's `timestamp`; the player schedules each point at
  `event.timestamp + timeOffset`.
- **No delay offsets anywhere in the touch path.** Two were removed:
  `TimingAdjustment.TOUCH_INTERACTION = -800` (subtracted from every touch timestamp) and
  the 200ms `Handler.postDelayed` scroll-end debounce. The -800 existed because the
  timestamp was taken in `EventHandler`'s background executor, long after the touch. Events
  now carry `MotionEvent.eventTime` converted to wall clock
  (`eventTime + (currentTimeMillis - uptimeMillis)`, recomputed per event so a sleeping
  device can't skew later gestures), which is accurate at the source and needs no fudge.
  The duplicate `ReplaySettings.TOUCH_INTERACTION_TIMING_ADJUSTMENT` and the unused
  `TOUCH_EVENT_DEBOUNCE_TIME` are gone too.
- **Move batches drain without a timer.** A batch flushes when it spans
  `TouchSampling.MOVE_BATCH_INTERVAL_MS` (500ms, rrweb's throttle) at the arrival of the
  next sample, when it hits `MAX_POSITIONS_PER_BATCH`, or when the gesture ends — always
  before the TouchEnd, so the stream stays chronological. Samples are taken at most every
  `MOVE_SAMPLE_INTERVAL_MS` (50ms, rrweb's `sampling.mousemove`). Nothing is held behind a
  delayed callback.
- **Long press now ends the gesture.** `GestureDetector.onSingleTapUp` never fires after a
  long press, so `onTouchEnd()` was never called and the 150ms screenshot `recordTimer` it
  gates ran until the next tap. Driving off `ACTION_UP`/`ACTION_CANCEL` fixes it.
- **Primary pointer only**, matching rrweb-web. `ACTION_POINTER_DOWN`/`_UP` are ignored;
  reading a secondary pointer's screen coordinates needs `getRawX(index)` (API 29), which
  minSdk 21 + AnimalSniffer would require gating.
- `RawTouchEvent` is now a sealed `Interaction`/`Move` pair carrying its own timestamp. Its
  `isSwipe`/`direction` fields were dropped — nothing consumed them, and a swipe is now
  fully described by start → positions → end. iOS's `RawTouchEvent` still has them.

## Frames are stamped at capture, not at encode (decided 2026-08-13)

Screenshots and wireframes now carry the wall-clock instant the pixels came off the
surface. `ScreenRecorder` reads `System.currentTimeMillis()` immediately after
`createBitmapFromView` and threads it through `RenderedFrame.capturedAtMs` →
`CapturedScreenshot.capturedAtMs` → `RawScreenshotEvent.timestamp` →
`SessionReplayEncoder.{main,incremental}SessionEvent(image, timestamp)`, and through
`WireframeEmitter.emit(..., capturedAtMs)` for the `mp_wireframe` event.

- **Why:** both were previously stamped downstream of the frame — the wireframe after
  JPEG compression, the screenshot later still, on `EventHandler`'s serial queue behind
  whatever was already enqueued. Touches are accurate at the source (`MotionEvent.eventTime`
  converted to wall clock), so the two clocks disagreed in one direction only: a pre-tap
  screen could sort *after* the tap. The service's wireframe sampler is touch-gated
  (before-frame plus up to two after-frames), so that mis-order picks the wrong before-frame
  and burns an after-frame slot.
- Both encoder entry points default `timestamp` to now, so callers with no frame to align to
  (tests, `EventServiceTests`/`FlushServiceTest`) are unchanged. `WireframeEmitter.emit`
  does the same.
- This is the same class of fix as removing `TimingAdjustment.TOUCH_INTERACTION = -800`
  from the touch path: timestamp at the source rather than compensating downstream.
- **All three platforms now stamp at the frame (2026-08-19).** Flutter reads
  `captureTimestamp` in `screenshot_capturer.dart` immediately before `boundary.toImage()`
  and uses it for both the screenshot and the wireframe. iOS was the last one: it stamped
  both events with `record(_ triggerTimestamp:)`'s value, computed at the *top* of `record`
  — so a timer-driven frame was dated before its render, and a touch-triggered frame was
  back-dated to the touch's own `UITouch.timestamp`. That tie mattered: the service's
  sampler is touch-gated, and a frame sharing a millisecond with the TOUCH_END that
  produced it left "which screen did this tap act on" to be decided by sort stability
  rather than by the timestamps. iOS now reads the clock right after its `renderer.image`
  block (the analogue of reading it after `createBitmapFromView`) and threads it out via
  `RenderedFrame.capturedAtMs` → `CapturedScreenshot.capturedAtMs` →
  `RawScreenshotEvent.timestamp`, deliberately named after this module's chain.
  `triggerTimestamp` survives for what it is actually for — the `recordInterval` rate-limit
  gate — because "has enough time passed since we last captured" and "this is what the
  screen looked like at T" are two different quantities.

## The Compose wireframe walk stays one pass (decided 2026-08-12)

Two accuracy features were built on top of the Compose walk and then removed. Both
were correct about the problem they described; both cost more fidelity than they
bought. The walk is again a single recursive pass over the **merged** semantics tree,
and `ComposeWireframeContext` is gone. Record here so neither is rebuilt by reflex.

**Merged-text index** (`buildMergedTextIndex`). Compose folds every descendant's text
into a `mergeDescendants` node's config, including descendants that are composed but
never shown — laid out with no area, or measured and then not placed (Material3's
`NavigationBarItemLayout` does the latter for a collapsed `alwaysShowLabel = false`
label). The index walked `SemanticsOwner.unmergedRootSemanticsNode` and re-attributed
each fragment to its own contributor, gated on `layoutInfo.isPlaced`. Bounds alone
cannot do this — an unplaced node reports the rect of the sibling that *was* placed.

It was removed because of an interaction with classification. Idiomatic Material
nav items pass `contentDescription = null` on the icon, since the label supplies the
accessibility name. Null the label and the item has no text at all; `Role.Tab` has no
textless-shell case in `collectWireframeForNode`, so it hits `else -> return` and the
element disappears. Measured on a `NavigationBarItem`-shaped tree: **four tabs became
one**. With icons that *do* carry a description, the index only deduped `"Home"` +
desc `"Home"` down to `"Home"` — no real gain. It also cost ~2.2ms per captured frame
(~800µs after optimization), the largest single item in the wireframe pass.

The phantom text is **not a privacy problem** — masking is geometric (`Rect.intersects`)
and covers the merged node's whole rect either way. Fidelity only.

*If it is ever rebuilt, it must land together with a textless-shell case for `Role.Tab`,
or it is a net regression.*

**Destination grouping + `dropOccludedElements`.** Grouped elements by nearest
full-screen (≥90% of root) layout ancestor, folded nested groups, and dropped all but
the topmost when two survived with text. Aimed at a `NavHost` mid-`fadeIn`/`fadeOut`,
where both destinations are composed at identical bounds and the wireframe would
describe two screens at once.

Removed because the false positive is more frequent and more damaging than the true
positive. Any full-screen text-bearing overlay — loading scrim, coach mark, "no
connection", in-window blocking progress — is the same shape: two full-screen siblings,
neither enclosing the other. The content *underneath* was the side discarded, leaving a
wireframe reading only `"Loading…"` with the screen deleted. And the frequencies run the
wrong way: a crossfade lasts ~300ms, so against the 500ms capture gate it is caught at
most once per navigation, while an overlay sits up for seconds and corrupts every
capture in that window. Emitting both is the safer failure — a summary reading two
screens can still tell what the user was doing; one reading a deleted screen cannot.

**Kept:** the `visibleBounds()` extraction, deduping the placed / `boundsInRoot` /
`boundsInWindow` / zero-area check that `markNodeForMasking` and
`collectWireframeForNode` each had inline. No behavior change.

**Both limitations are pinned by golden tests** in `ComposeWireframeGoldenTest`
(`composeMerged_includesTextFromNeverShownDescendants`,
`composeMerged_includesTextFromMeasuredButUnplacedDescendant`,
`composeStackedDestinations_keepsBoth`, `composeFullScreenOverlay_keepsContentUnderneath`)
so that reintroducing either is deliberate.

**Better next steps than either feature**, both cheaper and both needing platform parity:
emit `SemanticsProperties.Selected` so tab/selection context is explicit rather than
inferred from which label survived, and give `Role.Tab` a textless shell so nav
structure survives regardless of what happens to its text.

## Dedup keys off the finished wire payload (decided 2026-08-17)

All three SDKs now dedup on `hash(WireframePayload)` — the finished DTO, after
geometric masking, sensitive rules, glyph/blank normalization, truncation, and
density scaling. Android `lastPayloadHash`, iOS `lastPayloadHash`, Flutter
`_lastPayloadHash` / `WireframePayload.wireHash`.

Previously all three disagreed: Android and iOS hashed the **raw walker output**
plus a **separate mask-bounds hash**; Flutter hashed processed elements but not
the viewport.

- **Why the payload and not the raw elements.** Dedup means "identical renders
  collapse to one", and only the payload determines the render. Raw hashing
  produces false negatives in both directions: a masked field being typed into
  changes upstream text every frame but ships a byte-identical `text: null`
  payload, and bounds differing sub-pixel round to the same ints. Raw hashing
  also can't see truncation or density scaling, both of which are applied at
  serialization (`toJson(density)` / `wireText(for:)`).
- **The mask-bounds hash is gone, not merged.** Mask rects are not on the wire;
  they matter only through the text they strip, which the payload already
  reflects. A mask that moves without changing any element's text renders
  identically and now dedups. This is a deliberate behavior change — the old
  Android/iOS tests asserting "changing mask bounds always re-emits" were
  rewritten to assert the stripping case re-emits and the non-overlapping case
  dedups.
- **Viewport is part of the key on all three** (it wasn't anywhere before), so a
  rotation that leaves the element list untouched still emits.
- **`maskDecision` is not part of the key.** It's debug-only; no platform ships
  it on the wire. Two frames differing only in decision dedup. Flutter needed
  care here: `WireframePayload.toJson` *does* emit `maskDecision` but is not the
  wire serializer — `RRWebEvent._buildWireframeEvent` is, and it omits the field.
  `wireHash` deliberately mirrors the latter.
- Android's dedup check moved **after** processing as a result, so a deduped
  frame costs one pipeline pass (rect intersection + regex over a few hundred
  elements — negligible next to the JPEG compression running alongside it).

### Dedup is scoped to a recording session, not to the SDK lifetime (fixed 2026-08-20)

**The emitter outlives a stop/start cycle on all three platforms, so the session
boundary has to be announced explicitly.** Found by Greptile on the iOS PR
(#185); on inspection all three were broken the same way and all three are now
fixed together.

The emitter is built once (iOS/Android `init`, Flutter `initialize()`) and holds
the payload hash. `startRecording` mints a new replay id but used to leave that
hash in place, so the new replay's **first** frame was compared against the
*previous* replay's last one. Backgrounding and foregrounding onto an unchanged
screen therefore shipped an opening screenshot with **no `mp_wireframe`** — the
one frame an AI summary most needs, missing, until the UI happened to change.
Background → foreground is the ordinary session-restart path, not a corner case.

- **Reset site, one per platform**, immediately after the new session is
  generated: iOS `resetDedup()` after `SessionManager.generateNewSession()`,
  Android `resetDedup()` before `scheduleScreenshotCapture()`, Flutter
  `_screenshotCapturer.resetWireframeDedup()` after
  `_sessionManager.startNewSession()`. Each is the *only* site that mints a
  replay id, which is what makes one call sufficient — iOS's
  `generateNewSession()` has a single caller, and Android's id comes from
  `FlushService.start()`, also only reached from `startRecording`.
- **Android was the guaranteed case**, not the probabilistic one: `stopRecording`
  clears `initialScreenshotCaptured`, so `startRecording` → `scheduleScreenshotCapture()`
  *always* forces an initial capture. iOS depends on the swizzled `layoutSubviews`
  marking the screen dirty, which a foreground reliably does.
- **iOS already had `resetDedup()` with zero callers** — written for exactly this
  and never wired up. Android and Flutter had no such API at all; both gained one
  mirroring iOS's name. Flutter routes through
  `ScreenshotCapturer.resetWireframeDedup()` rather than threading the emitter
  into the coordinator: the capturer already owns the emitter and the coordinator
  already holds the capturer, so nothing new had to be plumbed.
- **Tested at the call site, not just on the reset method.** Each platform has
  two tests: one on the emitter (`resetDedup` re-emits an identical payload) and
  one driving `startRecording` and asserting the reset happened. Only the second
  catches the call site being deleted, and all three were confirmed to fail with
  the reset removed. iOS needed a `#if DEBUG hasDedupStateForTesting` seam to
  observe the private hash; Android asserts with a mockk `verify`; Flutter primes
  a real emitter and re-emits.
  - Android's call-site test installs the mock emitter **after** constructing the
    instance — `init` assigns `ScreenRecorder.shared.wireframeEmitter` from
    `wireframesOptions` (null in that test), so anything planted earlier is
    overwritten.

## An unmask never stops the wireframe walk, and never overrides an explicit mask (decided 2026-08-18, iOS completed 2026-08-19)

Cross-platform rule: **unmasking is a statement about pixels, not a reason to
stop describing a subtree** — and it overrides *auto*-masking only, never a
decision the developer made explicitly.

Android already worked the first way — `isSafe` is propagated to children
(`ViewContext(child, isSafe)`) and the walk never stops — and Flutter's unmask is
a directive on a render subtree that keeps traversing. iOS was the outlier: a
view with `mpReplaySensitive == false` returned early from
`traverseViewAndLayers`, so an explicitly-unmasked subtree emitted **zero**
wireframe elements. Fixed by threading `insideSafeSubtree` through the iOS walk.

What an unmask does and does not override, now consistent on all three:

| Nested under an unmask | Wireframe | Pixels |
| --- | --- | --- |
| Ordinary content | text emitted | shown |
| Auto-masked type (`maskAllText`/`maskAllImages`) | text emitted — auto-masking is what an unmask overrides | shown |
| Explicit `mpReplaySensitive = true` / `addSensitiveView` | textless shell, `EXPLICIT` | grayed on all three |
| Text input | textless shell, `TEXT_ENTRY` — never scraped, safe ancestor or not | grayed on all three |

- **The two right-hand cells were an accepted iOS divergence and are now
  closed** (2026-08-19). They previously read "Android grays; iOS still shows",
  recorded here as intended behavior. That call was reversed on the standing
  instruction that the platforms must not diverge. On iOS an unmask around a
  login form used to show the typed characters in the replay video; it now grays
  them.
- **iOS reached masking by two different routes and had to be fixed in both.**
  SwiftUI's `.mpReplaySensitive(_:)` plants a background *rect*, so masks were
  recorded and a containment sweep arbitrated — an unmask whose rect contained
  the mask's deleted it. UIKit's `mpReplaySensitive = false` marks a real
  *ancestor*, so `recordMask` no-op'd and no mask was ever recorded. The rule now
  lives in `recordMask` itself: an unmask suppresses `.auto`, never `.mask` or
  `.textInput`.
- **iOS's `.safe` branch descends whether or not wireframes are collected.** It
  used to return early with collection off, which was safe while a safe subtree
  could not produce masks at all. Now that it can, that shortcut would make
  masking depend on whether wireframes are enabled — turning them on would gray
  pixels that were previously shipped. Pinned by
  `SensitiveViewManagerWireframeTests.testUnmaskSubtree_maskDecisionsUnchanged`.
- **Content inside an explicit mask is still described** (iOS, 2026-08-19). The
  iOS walk used to `return` before a masked view's subviews, so a masked form
  emitted nothing at all and the summary lost the region's structure rather than
  just its text. It now descends to describe, with an `insideMaskedSubtree` flag
  suppressing every mask and unmask write below, so the frame set is unchanged —
  the container's rect already covers the subtree. Layer 2 does the redaction.
  Matches Android's `nested_unmask_in_mask_geometric` and Flutter fixtures 20/21.

## `wireframesOptions` crosses the React Native bridge as JSON (decided 2026-08-26)

React Native does not walk its own tree — it is a thin bridge over the two native SDKs, and
the one thing it sends is a config JSON string. So `wireframesOptions` was unreachable from
RN: it was `@Transient` here and absent from iOS's `CodingKeys`, both for the same reason,
that `SensitiveRule` is a sealed hierarchy holding `Regex`/`NSRegularExpression` values with
no automatic representation. It is now serialized on both platforms.

- **One shape, no per-platform transform.** `SensitiveRuleSerializer` (here) and
  `MPSensitiveRule`'s `Codable` conformance (iOS) were written against the same field names
  and `type` tokens, so RN's `toJSON()` emits `wireframesOptions` verbatim for both — unlike
  `autoMaskedViews` (`Text` vs `text`) and `remoteSettingsMode` (`STRICT` vs `strict`),
  which it still has to case-convert per platform. Each variant maps to one flat object
  tagged by `type`: `redact`/`strip` carry `text`, the regex pair carry `pattern`, and both
  redacts carry an optional `replacement`. All three suites pin the tokens as literal text,
  because a round-trip test stays green through a rename that silently disables every rule
  on the *other* platform.
- **Regex flags are the intersection of three engines.** Only `caseInsensitive`,
  `multiline`, `dotMatchesAll` cross, mapping to `RegexOption.IGNORE_CASE`/`MULTILINE`/
  `DOT_MATCHES_ALL` here, `.caseInsensitive`/`.anchorsMatchLines`/
  `.dotMatchesLineSeparators` on iOS, and JS's `i`/`m`/`s`. RN drops `g` silently (native
  always replaces every match, so a global regex means the same thing either way) and warns
  about anything else. The pattern itself is compiled by `java.util.regex` / ICU, not by
  JavaScript, so a JS-valid pattern can still behave differently or be rejected.
- **A malformed rule fails the decode; it is never skipped.** Unknown `type`, a missing
  `text`/`pattern`, or a pattern the platform engine rejects throws, which the bridge turns
  into a rejected `initialize`. A rule the customer wrote to remove sensitive text must not
  fail open — a silently-dropped rule is an integration that redacts nothing and looks fine.
- **The default replacement is now a named constant** on both platforms
  (`SensitiveRule.DEFAULT_REPLACEMENT`, `MPSensitiveRule.defaultReplacement`) so the decoder
  and the Kotlin/Swift default parameter cannot drift apart, and so the two platforms cannot
  redact to different tokens.
- **Consequence for the RN release:** the RN package now requires the SDK versions that
  carry wireframes. Both pins are marked `RELEASE:` in `android/build.gradle` and the
  podspec, and RN cannot ship before the SDKs do.

## React Native text on iOS needed its own classification (decided 2026-08-26)

Android needed nothing for React Native: `ReactTextView` is a `TextView`, `ReactEditText` an
`EditText`, `ReactImageView` an `ImageView`, so `classifyAndroidView` already covered every
RN screen. iOS covered almost none of one. React Native draws its own text — neither
architecture backs a `<Text>` with a `UILabel` — so `classifyForWireframe` returned `nil` and
an RN screen emitted a wireframe with **no text elements at all**, not even textless shells.
Masking was never affected: the RN bridge registers RN's classes through the public
`addSensitiveClass`, so the pixels were always grayed. Only the description was missing,
which is the one thing wireframes exist for.

`ReactNativeWireframeSupport.swift` adds the classification and the text read:

- **Fabric (`RCTParagraphComponentView`, default since RN 0.76)** exposes
  `attributedText`, a public property its own header documents as "to be only used by
  external introspection and debug tools". Read by KVC behind a `responds(to:)` guard.
- **Paper (`RCTTextView`)** keeps its `NSTextStorage` private and publishes the string only
  by overriding `accessibilityLabel`. That override prefers an explicitly-set label over the
  rendered text, and which one came back is not observable from outside RN. So Paper text is
  **the label tier, gated by `useAccessibilityLabelFallback`** — it is a label as far as we
  can tell, and the flag exists precisely to let the customer decide about labels. With the
  fallback off (the default) a legacy-arch `<Text>` ships as a `role + bounds` shell and
  `mpWireframeText` is the way to describe it. Reading the `_textStorage` ivar by KVC would
  resolve the ambiguity exactly and was rejected on the same principle that removed the
  SwiftUI reflection extractor.

  This was briefly the other way round — Paper read as tier 2, ungated, to avoid shipping
  legacy-arch apps textless. Tyler chose the gated split 2026-08-26: Fabric ungated (exact),
  Paper gated (a label). It also *removed* code, since Paper now simply returns `nil` from
  `renderedText(for:)` and falls through to the `accessibilityFallback` the `.text` case
  already ended with.
- **Paper `RCTImageView`** is an `RCTView`, not a `UIImageView`, so it needed classifying
  too. Fabric's is backed by `RCTUIImageViewAnimated : UIImageView` and needed nothing.
- **Fabric is tier 2, Paper is tier 3.** `attributedText` is the string the view draws, so
  gating it would have shipped every New-Architecture screen textless and defeated the
  point. Paper has no such exact source, so it sits in the tier its one available source
  actually belongs to. Both architectures are classified either way, so the *shape* of a
  legacy-arch screen is preserved regardless of the flag; only its text waits on it.
- **In the SDK, not behind a new public API.** A "register a text provider" extension point
  would exist on iOS only, since Android needs none; keeping the table next to the SwiftUI
  private-class table keeps the *public* surface identical across platforms.
- **No button role, deliberately.** RN's `Pressable`/`TouchableOpacity` is a plain view on
  both platforms, so a `<Text>` inside one is described as `text` by Android and iOS alike.
  Adding a trait-based button role on iOS only would have created a parity gap where there
  currently is none.

## A new initialization clears registered sensitive classes (decided 2026-08-26)

`SensitiveViewManager.deinitialize()` now clears `_sensitiveClasses` / `_customerSensitiveClasses`
(keeping only the seeded `EditText`). It used to keep them, on the reasoning that masking a class
is a standing instruction that outlives a session.

Tyler's ruling: **a new initialization is a new initialization** — the incoming config decides
what is masked, and nothing survives from the last one.

- **It was internally inconsistent.** `addSensitiveView` and `mpWireframeText` are equally
  standing developer instructions and were already cleared three lines above.
- **iOS already behaved this way**, by replacing the whole manager in `deinitializeInstance()`.
  So the two platforms disagreed about what re-initialize means. iOS's doc comment on
  `addSensitiveClass` now states the lifetime explicitly; it was previously undocumented.
- **The visible cost fell on React Native**, which is the only platform that implements
  `autoMaskedViews` *through* `addSensitiveClass`. `initialize` is documented as
  re-initializable, so a second call with a narrowed `autoMaskedViews` had no effect: the first
  call's registrations kept masking for the life of the process, and `syncAutoMaskedClass`
  refuses to drop a customer-registered class, so nothing could undo it. That is exactly the
  "narrow `autoMaskedViews` to get readable wireframes" flow.
- **`EditText` is deliberately retained.** It is not a developer registration but the
  always-masked text-entry guarantee, seeded at construction and refused by
  `removeSensitiveClass`. Clearing it would silently unmask every input until the next
  `autoMaskedViews` assignment re-seeded it — pinned by
  `test deinitialize keeps inputs always masked`.
- **Direction of the change matters.** The old behaviour failed *safe* (over-masking); the new
  one requires a caller who registered a class to register again after re-initializing, which
  fails *unsafe* if they do not. That is why the lifetime is now documented on the public API of
  both platforms rather than left implicit.

Pinned by three tests in `SensitiveViewManagerTest`, two of which were verified to fail with the
clearing removed. Version bumped to `1.4.0-wire10`; the React Native pins moved with it.

## Accessibility roles feed the wireframe role field (decided 2026-08-26)

`WireframeType`/`WireframeRole` gained `Link`, `Header`, `Checkbox`, `Switch`, `Radio`, `Tab`
alongside `Text`/`Input`/`Image`/`Button`, and the walk now consults a view's declared
accessibility role when view type says nothing. Motivated by React Native, where
`Pressable`/`TouchableOpacity` are plain views and previously produced no role at all.

**Why a closed set.** `role` is the one field the masking pipeline never touches — Layers 1–4
mask, strip and redact `text`, and nothing filters `role` — so a role sourced from
developer-supplied *text* would bypass masking entirely. Neither platform exposes a string:
iOS gives a `UIAccessibilityTraits` bitmask, Android an `AccessibilityRole` enum. Every value is
mapped through an allowlist, so an unmapped upstream value can never reach the payload. Tyler's
constraint; the structural argument is what satisfies it.

**Coverage differs by platform on purpose** (Tyler: "we can only track things we are capable of;
if one platform is better than another in a given area, that's fine"). iOS collapses most of
React Native's roles to `UIAccessibilityTraitNone`, so it sees button/link/header/switch;
Android reads its full enum and also reports checkbox/radio/tab. `rn_role_checkbox` is the case
that records the difference rather than hiding it.

**Two traps, both caught by goldens rather than reasoning:**
- **React Native's switch trait is `0x20000000000001`, whose low bit *is*
  `UIAccessibilityTraitButton`.** Testing `.button` first reported every switch as a button. The
  composite has to be tested first.
- **An accessibility-derived role must not close the wireframe subtree.** It lands on
  *containers*, so treating it as a leaf swallowed both the `<Text>` carrying the label and any
  nested control — `role_nested_control` reported one `button: "Cupcake Add"` and lost the inner
  button, which is exactly the failure mode that makes Flutter's `ListTile` lose a row's action.
  A *view-type* role still closes the subtree (a `UIButton`'s inner `UILabel` must not re-emit).

**Consequence, and a reversal.** I first added descendant-text absorption so a roled control
would not ship textless. It was wrong twice: it duplicated the label on Android (where a roled
container was never a leaf, so the child was already emitting), and on iOS it was the mechanism
doing the swallowing. Removed. A roled container now ships as a **textless shell with its label
beside it**, bounds nested inside, on both platforms — a summary can associate them by geometry,
and no element is lost. iOS additionally skips the label tier for these roles, because React
Native synthesizes an aggregated `accessibilityLabel` on an accessible container which
re-introduced the duplicate.

**Field note:** iOS only gets the trait when the view is `accessible`. `Pressable` and
`TouchableOpacity` default it to true, so real touchables work; a hand-written `<View>` must say
so, which is why the golden fixture does.

## Coordinate space

Wireframe bounds and viewport are converted to 1× logical pixels (`/ density`)
at the serialization boundary in `WireframeEmitter`, matching the screenshot
(captured at `1/density`) and scaled touch points
(`(rawX - windowOffset) / density`). Geometric masking runs in raw pixels
because `maskBounds` are raw; only the final wire/debug bounds are scaled.

## Wireframe goldens run off-device, in their own module (decided 2026-08-18)

Wireframe coordinate goldens live in **`:session-replay:wireframe-goldens`** and are
rendered by **Paparazzi (layoutlib)** on the JVM. This is now the standard for testing
"real" layout wireframes on Android; both predecessors were deleted.

- **What replaced what.** The Robolectric `WireframeGoldenTest` (35 goldens) stubbed
  `getGlobalVisibleRect` with mockk, so it pinned the walk's plumbing but never a
  coordinate. The instrumented `ComposeWireframeGoldenTest` used a real device and was
  `.gitignore`d "until CI has a pinned device/emulator" — a hold-out Paparazzi makes
  unnecessary, since the device is declared in code (`DeviceConfig.PIXEL_5`, density 2.75,
  1080x2340). 98 goldens now cover the View and Compose paths in ~9s with no emulator.
- **Separate Gradle module, and it has to be.** Paparazzi puts layoutlib's *real*
  `android.jar` on the unit-test classpath, displacing AGP's mockable one that this
  module's `unitTests.isReturnDefaultValues` tests rely on. Sharing a source set breaks
  both directions: the Robolectric/mockk tests fail with `UnsatisfiedLinkError` on
  `SystemProperties.native_get_int`, and Paparazzi's Bridge fails to init when it is not
  first in the JVM (cashapp/paparazzi#1979).
- **The walk must run *during* the render.** Paparazzi detaches the view tree and disposes
  the composition once `snapshot()` returns, leaving `isShown` false and the semantics tree
  empty. The harness hooks `onLayout` (View) and `onGloballyPositioned` (Compose) instead,
  where layoutlib's root is properly parented and the visibility gate behaves as on device.
  `viewGoneSubtree_isNotDescribed` exists so that simplifying this back breaks visibly.
- **Version pin.** Paparazzi **1.3.5**, not 2.x: alpha03+ ship Kotlin 2.3.0 metadata, which
  the deliberately capped Kotlin 2.1.0 compiler cannot read. 1.3.5's custom HTML reporter
  calls a Gradle internal removed in Gradle 9, so the module sets
  `reports.html.required = false`; the failure is cosmetic and post-test. Root
  `gradle.properties` needs `android.jetifier.ignorelist=common-.*\.jar`.
- **Seams, not friend-paths.** `WireframeEmitter`, `processForTesting`,
  `SensitiveViewManager.deinitialize` and `useAccessibilityLabelFallback` are
  `@RestrictTo(LIBRARY_GROUP)` rather than `internal`, matching the convention `:common`
  already uses. This is lint-enforced, not compiler-enforced — a real loosening accepted so
  the goldens do not depend on an AGP intermediate jar path.
- **Kept on-device:** `SubWindowWireframeTest`, which checks dialog wireframe bounds against
  the composited screenshot. It is not a golden, and Paparazzi cannot host it (no second
  window). The Flutter suite calls this wireframe/pixel pairing its most valuable check.

### Alpha (added 2026-08-18)

`processSubviews` now skips `alpha <= 0f` in addition to `!isShown` (GONE/INVISIBLE),
matching Flutter's `Opacity(0)` filter — a fully transparent view paints nothing, so its
text must not reach the summarizer either. Alpha is multiplicative down the hierarchy and
children are enqueued at the bottom of the loop, so this drops the whole subtree. It also
means such a view contributes no mask region, which is right: nothing is painted to cover.

**Compose has no equivalent and this is a known gap.** Transparency lives on the graphics
layer, and the only thing that reads it — `SemanticsNode.isTransparent` — is `internal` to
compose-ui. A composable behind `Modifier.alpha(0f)` is still described, text and all.
Pinned by `compose_transparent_node_known_gap` so a fix shows up as a diff. Closing it needs
an upstream request to open `isTransparent`, or an `InvisibleToUser`-style opt-in.

### Known parity gaps vs. the Flutter reference suite

The decision x role coverage matrix has no holes, and every Flutter golden scenario is
covered except the platform-variant ones (Cupertino), which have no Android analogue. Open:

- **Tooltip is never consulted.** Flutter's text tiers are declared -> visible -> *tooltip*
  -> icon label (fixtures 11, 29). Android reads `contentDescription` only, so an icon-only
  action carrying `View.tooltipText` (API 26+) but no `contentDescription` ships as a bare
  shell. Needs a cross-platform ruling before fixtures can be written.
- **Options & serialization: 0 of Flutter's 10 tests.** No `WireframesOptions` test exists,
  including the one that locks the SCREAMING_SNAKE decision spelling that makes fixtures
  comparable across platforms.
- **Emitter unit tests are 36 vs 36 but not the same 36.** Missing Flutter's *Empty screen*
  group (4) entirely; partial on text cleaning (0/5, covered as goldens instead), declared
  (3/5), dedup (5/6), debug callback (2/3). Android is ahead on density scaling and rule
  ordering.
- **"Composite button emits once"** (Flutter 15) has no Android case.
- **Accepted divergence:** Flutter fixture 21 expects `[GEOMETRIC, EXPLICIT]` for
  `Mask > Layout > [Unmask > Text, Text]`; Android reports `[GEOMETRIC, GEOMETRIC]` because
  descendants of a mask are not individually marked EXPLICIT by Layer 1. Text output is
  identical and `maskDecision` is debug-only, never on the wire. Tyler's call, 2026-08-18 —
  do not "fix" without reopening.
