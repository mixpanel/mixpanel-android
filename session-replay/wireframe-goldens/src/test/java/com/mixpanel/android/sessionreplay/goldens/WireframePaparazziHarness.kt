package com.mixpanel.android.sessionreplay.goldens

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import app.cash.paparazzi.Paparazzi
import com.mixpanel.android.sessionreplay.models.SensitiveRule
import com.mixpanel.android.sessionreplay.sensitive_views.SensitiveViewManager
import com.mixpanel.android.sessionreplay.wireframe.WireframeElement
import com.mixpanel.android.sessionreplay.wireframe.WireframeEmitter

/**
 * Runs the real masking pipeline — the Layer-1/3 walk
 * ([SensitiveViewManager.processSubviews]) followed by the Layer-2/4 emitter seam
 * ([WireframeEmitter.processForTesting]) — over content laid out by layoutlib.
 *
 * **The walk must happen during the render.** Paparazzi detaches the view tree (and disposes the
 * composition) once `snapshot()` returns, which leaves the root's parent chain terminating at
 * `null` and the semantics tree empty. While rendering, layoutlib's own `Layout` root is properly
 * parented, so `View.isShown` — the gate at the top of `processSubviews` — behaves exactly as it
 * does on a device, and `GONE`/`INVISIBLE` subtrees are skipped for the real reason rather than
 * because nothing was attached. Both entry points below therefore hook a layout callback rather
 * than inspecting the tree afterwards.
 */
internal class WireframePaparazziHarness(private val paparazzi: Paparazzi) {

    /** A finished capture: the processed element list plus the viewport it was measured in. */
    data class Capture(
        val elements: List<WireframeElement>,
        val viewport: List<Int>
    )

    /** Invokes [onLaidOut] exactly once, from inside a real layout pass. */
    private class WalkHost(
        context: Context,
        private val onLaidOut: (View) -> Unit
    ) : FrameLayout(context) {
        private var walked = false

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            if (!walked) {
                walked = true
                onLaidOut(this)
            }
        }
    }

    /** Captures an Android [View] hierarchy built by [content]. */
    fun capture(
        rules: List<SensitiveRule> = emptyList(),
        content: (Context) -> View
    ): Capture {
        var capture: Capture? = null
        val host = WalkHost(paparazzi.context) { root -> capture = walk(root, rules) }
        host.addView(
            content(paparazzi.context),
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        paparazzi.snapshot(host)
        return requireNotNull(capture) { "Layout pass never ran — content produced no layout." }
    }

    /** Captures Compose [content] through the same pipeline. */
    fun captureCompose(
        rules: List<SensitiveRule> = emptyList(),
        content: @Composable () -> Unit
    ): Capture {
        var capture: Capture? = null
        paparazzi.snapshot {
            val view = LocalView.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        if (capture == null) capture = walk(view.rootView, rules)
                    }
            ) {
                content()
            }
        }
        return requireNotNull(capture) { "Composition never laid out — content produced no layout." }
    }

    companion object {
        /**
         * Absolute placement inside a [FrameLayout], so a golden's coordinates are dictated by
         * the case rather than by intrinsic text measurement. Real layout still runs — this only
         * fixes where it runs.
         */
        fun at(x: Int, y: Int, w: Int, h: Int): FrameLayout.LayoutParams =
            FrameLayout.LayoutParams(w, h).apply {
                leftMargin = x
                topMargin = y
            }

        /** Resets every piece of [SensitiveViewManager] global state a case can touch. */
        fun resetMaskingState() {
            SensitiveViewManager.deinitialize()
            // deinitialize() deliberately keeps registered *classes* (masking a class is a
            // standing instruction that outlives a session), so they have to be cleared by hand
            // or they leak into the next case.
            SensitiveViewManager.autoMaskedViews = emptySet()
            // Deliberately *not* the shipped default (off): most goldens exercise the label tier,
            // and the `*_fallbackOff_*` cases turn it back off per-case.
            SensitiveViewManager.useAccessibilityLabelFallback = true
        }
    }

    private fun walk(root: View, rules: List<SensitiveRule>): Capture {
        val collected = mutableListOf<WireframeElement>()
        val summary = SensitiveViewManager.processSubviews(root, collected)
        val processed = WireframeEmitter(sensitiveRules = rules)
            .processForTesting(collected, summary.boundsSnapshot)
        return Capture(processed, listOf(root.width, root.height))
    }
}
