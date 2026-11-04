package com.mixpanel.android.sessionreplay.sensitive_views

import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp as composeDp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.mixpanel.android.sessionreplay.BuildConfig
import com.mixpanel.android.sessionreplay.ShellActivity
import com.mixpanel.android.sessionreplay.withAttachedView
import com.mixpanel.android.sessionreplay.wireframe.WireframeElement
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil

/**
 * Paired, on-device proof of the incremental main-thread cost of wireframe collection.
 *
 * This manual benchmark is skipped in every standard instrumentation run. Opt in explicitly with
 * `-Pandroid.testInstrumentationRunnerArguments.runWireframeBenchmark=true` and run against
 * release bytecode; debug results are not evidence about production performance. See
 * `session-replay/benchmark/README.md`.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class WireframeTraversalPerformanceTest {
    private lateinit var scenario: ActivityScenario<ShellActivity>

    @Before
    fun setUp() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "manual benchmark requires runWireframeBenchmark=true",
            arguments.getString("runWireframeBenchmark").toBoolean()
        )
        assumeTrue("benchmark requires -PmixpanelTestBuildType=release", BuildConfig.BUILD_TYPE == "release")
        SensitiveViewManager.deinitialize()
        SensitiveViewManager.autoMaskedViews = AutoMaskedView.defaultSet()
        scenario = ActivityScenario.launch(ShellActivity::class.java)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        SensitiveViewManager.deinitialize()
    }

    @Test
    fun shouldKeepWireframeTraversalWithinFrameBudget() {
        val arguments = InstrumentationRegistry.getArguments()
        val rows = arguments.intValue("wireframeRows", 250)
        val warmupPairs = arguments.intValue("wireframeWarmups", 20)
        val measuredPairs = arguments.intValue("wireframeIterations", 100)
        val maximumP95DeltaUs = arguments.longValue("wireframeMaxP95DeltaUs", 2_000L)
        val frameBudgetUs = arguments.longValue("wireframeFrameBudgetUs", 16_667L)

        scenario.withAttachedView(
            viewBuilder = { activity ->
                ScrollView(activity).apply {
                    addView(
                        LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            repeat(rows) { index -> addView(benchmarkRow(index)) }
                        },
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
            }
        ) { root ->
            repeat(warmupPairs) {
                SensitiveViewManager.processSubviews(root)
                SensitiveViewManager.processSubviews(root, mutableListOf())
            }

            val disabledSamples = LongArray(measuredPairs)
            val enabledSamples = LongArray(measuredPairs)
            val pairedDeltas = LongArray(measuredPairs)
            var disabledSummary: SubviewSummary? = null
            var enabledSummary: SubviewSummary? = null
            var lastWireframes: List<WireframeElement> = emptyList()

            repeat(measuredPairs) { index ->
                val wireframes = mutableListOf<WireframeElement>()
                val disabled: Timed<SubviewSummary>
                val enabled: Timed<SubviewSummary>
                if (index % 2 == 0) {
                    disabled = measureMicros { SensitiveViewManager.processSubviews(root) }
                    enabled = measureMicros { SensitiveViewManager.processSubviews(root, wireframes) }
                } else {
                    enabled = measureMicros { SensitiveViewManager.processSubviews(root, wireframes) }
                    disabled = measureMicros { SensitiveViewManager.processSubviews(root) }
                }
                disabledSamples[index] = disabled.micros
                enabledSamples[index] = enabled.micros
                pairedDeltas[index] = enabled.micros - disabled.micros
                disabledSummary = disabled.value
                enabledSummary = enabled.value
                lastWireframes = wireframes
            }

            assertFalse("enabled traversal must produce wireframes", lastWireframes.isEmpty())
            assertEquals(disabledSummary!!.boundsSnapshot, enabledSummary!!.boundsSnapshot)
            assertEquals(disabledSummary!!.hasActiveTransition, enabledSummary!!.hasActiveTransition)

            val disabled = Stats(disabledSamples)
            val enabled = Stats(enabledSamples)
            val delta = Stats(pairedDeltas)
            val result = JSONObject().apply {
                put("platform", "android")
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("sdk", Build.VERSION.SDK_INT)
                put("buildType", BuildConfig.BUILD_TYPE)
                put("rows", rows)
                put("warmupPairs", warmupPairs)
                put("measuredPairs", measuredPairs)
                put("disabled", disabled.toJson())
                put("enabled", enabled.toJson())
                put("pairedDelta", delta.toJson())
                put("maximumP95DeltaMicros", maximumP95DeltaUs)
                put("frameBudgetMicros", frameBudgetUs)
            }
            val report = buildString {
                appendLine("Wireframe traversal benchmark (Android release, $rows rows)")
                appendLine("  disabled: ${disabled.summary}")
                appendLine("  enabled:  ${enabled.summary}")
                append("  paired delta: ${delta.summary}")
            }
            Log.i(TAG, report)
            Log.i(TAG, "WIREFRAME_TRAVERSAL_BENCHMARK_JSON=$result")

            assertTrue(
                "wireframes added ${delta.p95Micros} us at p95; limit is $maximumP95DeltaUs us",
                delta.p95Micros <= maximumP95DeltaUs
            )
            assertTrue(
                "enabled traversal used ${enabled.p95Micros} us at p95; budget is $frameBudgetUs us",
                enabled.p95Micros < frameBudgetUs
            )
        }
    }

    @Test
    fun shouldKeepComposeWireframeTraversalWithinFrameBudget() {
        val arguments = InstrumentationRegistry.getArguments()
        val rows = arguments.intValue("wireframeRows", 250)
        val warmupPairs = arguments.intValue("wireframeWarmups", 20)
        val measuredPairs = arguments.intValue("wireframeIterations", 100)
        val maximumP95DeltaUs = arguments.longValue("wireframeMaxP95DeltaUs", 2_000L)
        val frameBudgetUs = arguments.longValue("wireframeFrameBudgetUs", 16_667L)

        scenario.withAttachedView(
            viewBuilder = { activity ->
                ComposeView(activity).apply {
                    setContent { ComposeBenchmarkHierarchy(rows) }
                }
            }
        ) { root ->
            val semanticsNodeCount = root.firstComposeRoot()
                ?.semanticsOwner
                ?.rootSemanticsNode
                ?.descendantCount()
                ?: 0
            assertTrue(
                "Compose benchmark must contain the full semantics workload; found $semanticsNodeCount nodes",
                semanticsNodeCount >= rows * 2
            )

            repeat(warmupPairs) {
                SensitiveViewManager.processSubviews(root)
                SensitiveViewManager.processSubviews(root, mutableListOf())
            }

            val disabledSamples = LongArray(measuredPairs)
            val enabledSamples = LongArray(measuredPairs)
            val pairedDeltas = LongArray(measuredPairs)
            var disabledSummary: SubviewSummary? = null
            var enabledSummary: SubviewSummary? = null
            var lastWireframes: List<WireframeElement> = emptyList()

            repeat(measuredPairs) { index ->
                val wireframes = mutableListOf<WireframeElement>()
                val disabled: Timed<SubviewSummary>
                val enabled: Timed<SubviewSummary>
                if (index % 2 == 0) {
                    disabled = measureMicros { SensitiveViewManager.processSubviews(root) }
                    enabled = measureMicros { SensitiveViewManager.processSubviews(root, wireframes) }
                } else {
                    enabled = measureMicros { SensitiveViewManager.processSubviews(root, wireframes) }
                    disabled = measureMicros { SensitiveViewManager.processSubviews(root) }
                }
                disabledSamples[index] = disabled.micros
                enabledSamples[index] = enabled.micros
                pairedDeltas[index] = enabled.micros - disabled.micros
                disabledSummary = disabled.value
                enabledSummary = enabled.value
                lastWireframes = wireframes
            }

            assertFalse("Compose semantics traversal must emit visible elements", lastWireframes.isEmpty())
            assertEquals(disabledSummary!!.boundsSnapshot, enabledSummary!!.boundsSnapshot)
            assertEquals(disabledSummary!!.hasActiveTransition, enabledSummary!!.hasActiveTransition)

            val disabled = Stats(disabledSamples)
            val enabled = Stats(enabledSamples)
            val delta = Stats(pairedDeltas)
            val result = JSONObject().apply {
                put("platform", "android-compose")
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("sdk", Build.VERSION.SDK_INT)
                put("buildType", BuildConfig.BUILD_TYPE)
                put("rows", rows)
                put("semanticsNodeCount", semanticsNodeCount)
                put("wireframeCount", lastWireframes.size)
                put("warmupPairs", warmupPairs)
                put("measuredPairs", measuredPairs)
                put("disabled", disabled.toJson())
                put("enabled", enabled.toJson())
                put("pairedDelta", delta.toJson())
                put("maximumP95DeltaMicros", maximumP95DeltaUs)
                put("frameBudgetMicros", frameBudgetUs)
            }
            val report = buildString {
                appendLine("Wireframe traversal benchmark (Android Compose release, $rows rows)")
                appendLine("  disabled: ${disabled.summary}")
                appendLine("  enabled:  ${enabled.summary}")
                append("  paired delta: ${delta.summary}")
            }
            Log.i(TAG, report)
            Log.i(TAG, "WIREFRAME_TRAVERSAL_BENCHMARK_JSON=$result")

            assertTrue(
                "Compose wireframes added ${delta.p95Micros} us at p95; limit is $maximumP95DeltaUs us",
                delta.p95Micros <= maximumP95DeltaUs
            )
            assertTrue(
                "Compose wireframe-enabled traversal used ${enabled.p95Micros} us at p95; budget is $frameBudgetUs us",
                enabled.p95Micros < frameBudgetUs
            )
        }
    }

    @Composable
    private fun ComposeBenchmarkHierarchy(rows: Int) {
        Column(modifier = Modifier.fillMaxWidth()) {
            repeat(rows) { index ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.composeDp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.composeDp)
                            .semantics {
                                role = Role.Image
                                contentDescription = "Product image $index"
                            }
                    )
                    BasicText("Product $index", modifier = Modifier.weight(1f))
                    BasicText("\$${index + 1}.99")
                }
            }
        }
    }

    private fun LinearLayout.benchmarkRow(index: Int): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 48.dp
            addView(ImageView(context), LinearLayout.LayoutParams(32.dp, 32.dp))
            addView(
                TextView(context).apply { text = "Product $index" },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(TextView(context).apply { text = "\$${index + 1}.99" })
        }

    private val Int.dp: Int
        get() = (this * InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density).toInt()

    private fun View.firstComposeRoot(): RootForTest? {
        if (this is RootForTest) return this
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                getChildAt(index).firstComposeRoot()?.let { return it }
            }
        }
        return null
    }

    private fun SemanticsNode.descendantCount(): Int =
        1 + children.sumOf { it.descendantCount() }

    private data class Timed<T>(val micros: Long, val value: T)

    private fun <T> measureMicros(block: () -> T): Timed<T> {
        val start = SystemClock.elapsedRealtimeNanos()
        val value = block()
        return Timed((SystemClock.elapsedRealtimeNanos() - start) / 1_000L, value)
    }

    private class Stats(samples: LongArray) {
        private val sorted = samples.sortedArray()
        val meanMicros = samples.average()
        val medianMicros = percentile(0.50)
        val p95Micros = percentile(0.95)
        val minimumMicros = sorted.first()
        val maximumMicros = sorted.last()

        val summary: String
            get() = "median=${formatMillis(medianMicros)}, p95=${formatMillis(p95Micros)}, " +
                "mean=${formatMillis(meanMicros)}, min=${formatMillis(minimumMicros)}, " +
                "max=${formatMillis(maximumMicros)}"

        fun toJson(): JSONObject = JSONObject().apply {
            put("meanMicros", meanMicros)
            put("medianMicros", medianMicros)
            put("p95Micros", p95Micros)
            put("minimumMicros", minimumMicros)
            put("maximumMicros", maximumMicros)
        }

        private fun percentile(value: Double): Long =
            sorted[(ceil(sorted.size * value).toInt() - 1).coerceIn(0, sorted.lastIndex)]

        private fun formatMillis(micros: Number): String =
            "%.3fms".format(micros.toDouble() / 1_000.0)
    }

    private fun android.os.Bundle.intValue(name: String, default: Int): Int =
        getString(name)?.toIntOrNull() ?: default

    private fun android.os.Bundle.longValue(name: String, default: Long): Long =
        getString(name)?.toLongOrNull() ?: default

    private companion object {
        const val TAG = "MixpanelWireframePerf"
    }
}
