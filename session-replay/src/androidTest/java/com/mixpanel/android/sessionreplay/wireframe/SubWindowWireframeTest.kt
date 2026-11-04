package com.mixpanel.android.sessionreplay.wireframe

import android.app.Dialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mixpanel.android.sessionreplay.ShellActivity
import com.mixpanel.android.sessionreplay.extensions.mpReplaySensitive
import com.mixpanel.android.sessionreplay.sensitive_views.SensitiveViewManager
import com.mixpanel.android.sessionreplay.tracking.ScreenRecorder
import curtains.Curtains
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Instrumented coverage for wireframe capture of a **sub-window** (dialog, popup,
 * separate-window bottom sheet).
 *
 * A sub-window is captured on its own surface and then composited onto a full-screen bitmap at
 * its offset from the main window. The wireframe has to describe *that composited image*, per the
 * wire contract ("bounds — the bounding box of the element within the screenshot image"), so
 * `renderViewHierarchyAsImage` shifts every element and every mask rect by the same offset and
 * swaps the viewport for the full screen.
 *
 * This runs on a device because the parts that can go wrong are the parts a JVM test has to fake:
 * whether a real dialog window reports the offsets `getSubWindowInfo` expects, whether
 * `Curtains.rootViews` orders the windows the way [com.mixpanel.android.sessionreplay.MPSessionReplayInstance]
 * assumes, and — the one that actually matters — whether the shifted bounds land on the element's
 * pixels in the image that ships. The last is asserted against the decoded screenshot rather than
 * against a recomputed formula, so the test does not have to take a position on what coordinate
 * space `getGlobalVisibleRect` returns for a view in a sub-window. If the shift is missing,
 * doubled, or has its axes swapped, the probe misses.
 *
 * Geometry is derived from the device's own screen size, so there is no golden to record and
 * nothing here is density- or resolution-dependent.
 *
 * Run: `./gradlew :session-replay:connectedDebugAndroidTest` (needs a connected device/emulator).
 */
@RunWith(AndroidJUnit4::class)
class SubWindowWireframeTest {

    // A Rule rather than ActivityScenario.use {}: JUnit rules wrap @After, so the activity (and
    // with it the dialog's parent window) is still alive when tearDown dismisses.
    @get:Rule
    val activityRule = ActivityScenarioRule(ShellActivity::class.java)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** Solid fill on the probed element. Far from black so the composite background can't alias. */
    private val avatarColor = Color.rgb(255, 0, 255)

    private var dialog: Dialog? = null

    @Before
    fun setUp() {
        SensitiveViewManager.deinitialize()
        // Text/Image auto-masking off so the probed elements keep their text and the only mask in
        // play is the one a test installs deliberately.
        SensitiveViewManager.autoMaskedViews = emptySet()
        // Opt in explicitly: the shipped default is off, and the probed ImageView is identified by
        // its contentDescription.
        SensitiveViewManager.useAccessibilityLabelFallback = true
    }

    @After
    fun tearDown() {
        // Tolerant: if the activity window went away first, the dialog is already detached.
        dialog?.let { d -> instrumentation.runOnMainSync { runCatching { d.dismiss() } } }
        dialog = null
        ScreenRecorder.shared.wireframeEmitter = null
        SensitiveViewManager.deinitialize()
    }

    /**
     * The load-bearing assertion: the bounds the wireframe reports for an element must contain
     * that element's pixels *in the composited screenshot*. Also pins the viewport against the
     * image's real dimensions — the contract is "within the screenshot image", so the two are the
     * same statement.
     */
    @Test
    fun dialogElementBoundsLandOnTheElementInTheCompositedImage() {
        run {
            val activity = activityOf(activityRule.scenario)
            setWhiteContent(activity)

            val screen = mainWindowSize(activity)
            val offsetX = screen.first / 8
            val offsetY = screen.second / 2
            lateinit var avatar: ImageView
            showDialog(activity, offsetX, offsetY, screen) { content ->
                avatar = ImageView(activity).apply {
                    setBackgroundColor(avatarColor)
                    contentDescription = "Avatar"
                    layoutParams = FrameLayout.LayoutParams(content.width / 2, content.height / 2)
                }
                content.addView(avatar)
            }

            val (snapshot, jpeg) = captureDialog(activity)
            val image = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            assertNotNull("screenshot did not decode", image)

            // The composited image is full-screen, so the viewport must be its exact dimensions —
            // not the dialog's.
            assertEquals(listOf(image.width, image.height), snapshot.viewport)

            val element = snapshot.elements.single { it.role == "image" }
            assertEquals("Avatar", element.text)
            val (x, y, w, h) = element.bounds
            assertTrue(
                "reported bounds [$x,$y,$w,$h] fall outside the ${image.width}x${image.height} image",
                x >= 0 && y >= 0 && x + w <= image.width && y + h <= image.height
            )

            // Probe the middle of the reported box. This is the whole test: if the elements were
            // never shifted into the composited image's space, this lands on the black background
            // above the dialog instead of on the avatar.
            val cx = x + w / 2
            val cy = y + h / 2
            assertColorNear(
                "wireframe bounds do not cover the element in the screenshot",
                avatarColor,
                image.getPixel(cx, cy)
            )

            // Guard against a vacuous pass: confirm the un-shifted position is *not* also the
            // avatar colour, i.e. the shift is what moved the probe onto the element.
            val density = activity.resources.displayMetrics.density
            val unshiftedX = cx - (offsetX / density).toInt()
            val unshiftedY = cy - (offsetY / density).toInt()
            assertTrue("test setup is degenerate — dialog is not offset", unshiftedX in 0 until image.width && unshiftedY in 0 until image.height)
            assertColorFar(
                "un-shifted probe also hits the element, so this test cannot detect a missing shift",
                avatarColor,
                image.getPixel(unshiftedX, unshiftedY)
            )
        }
    }

    /**
     * The Layer 2 geometric strip intersects element bounds against the rects painted on the
     * screenshot. Shifting the elements into composited space without shifting the masks with them
     * leaves the two in different coordinate systems, they stop overlapping, and text the
     * screenshot has visually covered leaks through the wireframe. Nothing else fails when that
     * happens, which is what makes it worth an on-device test.
     */
    @Test
    fun geometricStripStillCoversDialogTextAfterTheShift() {
        run {
            val activity = activityOf(activityRule.scenario)
            setWhiteContent(activity)

            val screen = mainWindowSize(activity)
            showDialog(activity, screen.first / 8, screen.second / 2, screen) { content ->
                val box = FrameLayout.LayoutParams(content.width / 2, content.height / 3)
                content.addView(
                    TextView(activity).apply {
                        text = "Balance \$4,200"
                        layoutParams = box
                    }
                )
                // Sits directly on top of the label and is masked, so the screenshot paints over
                // the label's pixels. The label itself is never marked sensitive — only geometry
                // connects the two.
                content.addView(
                    View(activity).apply {
                        setBackgroundColor(Color.DKGRAY)
                        layoutParams = FrameLayout.LayoutParams(box.width, box.height)
                        mpReplaySensitive(true)
                    }
                )
            }

            val (snapshot, _) = captureDialog(activity)

            val label = snapshot.elements.single { it.role == "text" }
            assertNull(
                "text covered by a mask on the screenshot must not survive in the wireframe",
                label.text
            )
            assertEquals(MaskDecision.GEOMETRIC, label.maskDecision)
        }
    }

    // ---- Harness -------------------------------------------------------------------------------

    private fun activityOf(scenario: ActivityScenario<ShellActivity>): ShellActivity {
        lateinit var activity: ShellActivity
        scenario.onActivity { activity = it }
        return activity
    }

    private fun setWhiteContent(activity: ShellActivity) {
        instrumentation.runOnMainSync {
            activity.setContentView(
                FrameLayout(activity).apply {
                    setBackgroundColor(Color.WHITE)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            )
        }
        instrumentation.waitForIdleSync()
    }

    private fun mainWindowSize(activity: ShellActivity): Pair<Int, Int> {
        val decor = activity.window.decorView
        awaitLaidOut(decor)
        return decor.width to decor.height
    }

    /**
     * Shows a dialog pinned to an exact offset and size in raw pixels, so the sub-window path is
     * exercised with a known, non-zero offset on both axes.
     */
    private fun showDialog(
        activity: ShellActivity,
        offsetXPx: Int,
        offsetYPx: Int,
        screen: Pair<Int, Int>,
        buildContent: (FrameLayout) -> Unit
    ) {
        val width = screen.first / 2
        val height = screen.second / 4
        lateinit var content: FrameLayout
        instrumentation.runOnMainSync {
            val d = Dialog(activity).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                content = FrameLayout(activity)
                setContentView(content)
            }
            d.window!!.apply {
                setBackgroundDrawable(ColorDrawable(Color.WHITE))
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                attributes = attributes.also {
                    it.gravity = Gravity.TOP or Gravity.START
                    it.x = offsetXPx
                    it.y = offsetYPx
                    it.width = width
                    it.height = height
                }
            }
            d.show()
            dialog = d
        }
        instrumentation.waitForIdleSync()
        awaitLaidOut(dialog!!.window!!.decorView)

        // Content is measured now, so children can be sized relative to it.
        instrumentation.runOnMainSync { buildContent(content) }
        instrumentation.waitForIdleSync()
        awaitLaidOut(content.getChildAt(0))
    }

    /**
     * Drives the production capture path and returns the wireframe it emitted alongside the JPEG
     * that shipped. View selection mirrors `MPSessionReplayInstance.captureScreenshot` — including
     * the `Curtains.rootViews` ordering it relies on, asserted here because nothing else pins it.
     */
    private fun captureDialog(activity: ShellActivity): Pair<WireframeDebugSnapshot, ByteArray> {
        val latch = CountDownLatch(1)
        val snapshots = mutableListOf<WireframeDebugSnapshot>()
        ScreenRecorder.shared.wireframeEmitter = WireframeEmitter(
            debugEmitter = { snap ->
                synchronized(snapshots) { snapshots += snap }
                latch.countDown()
            }
        )

        val captured = runBlocking(Dispatchers.Main) {
            val roots = Curtains.rootViews
            assertSame(
                "production picks the last root view as the capture target; expected the dialog",
                dialog!!.window!!.decorView,
                roots.last()
            )
            assertSame(
                "production picks the first root view as the full-screen reference; expected the activity",
                activity.window.decorView,
                roots.first()
            )
            ScreenRecorder.shared.captureScreenshot(roots.last(), roots.first())
        }

        assertNotNull("captureScreenshot returned null — no frame to assert against", captured)
        assertTrue(
            "wireframe was never emitted for the captured frame",
            latch.await(5, TimeUnit.SECONDS)
        )
        return synchronized(snapshots) { snapshots.single() } to captured!!.data
    }

    private fun awaitLaidOut(view: View) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            var ready = false
            instrumentation.runOnMainSync {
                ready = view.isAttachedToWindow && view.width > 0 && view.height > 0
            }
            if (ready) return
            Thread.sleep(20)
        }
        throw AssertionError("view never laid out: $view")
    }

    // JPEG is lossy, so exact equality is not available even on a flat fill. The tolerance is wide
    // enough to absorb compression and narrow enough that black background never passes.
    private fun assertColorNear(message: String, expected: Int, actual: Int) {
        assertTrue(
            "$message (expected ~#${hex(expected)}, got #${hex(actual)})",
            channelDistance(expected, actual) <= COLOR_TOLERANCE
        )
    }

    private fun assertColorFar(message: String, expected: Int, actual: Int) {
        assertTrue(
            "$message (both ~#${hex(actual)})",
            channelDistance(expected, actual) > COLOR_TOLERANCE
        )
    }

    private fun channelDistance(a: Int, b: Int): Int = maxOf(
        abs(Color.red(a) - Color.red(b)),
        abs(Color.green(a) - Color.green(b)),
        abs(Color.blue(a) - Color.blue(b))
    )

    private fun hex(color: Int): String = Integer.toHexString(color)

    private companion object {
        const val COLOR_TOLERANCE = 48
    }
}
