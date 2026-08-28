package com.mixpanel.android.sessionreplay.tracking

import android.content.Context
import android.view.MotionEvent
import com.mixpanel.android.sessionreplay.models.RawScreenshotEvent
import com.mixpanel.android.sessionreplay.models.RawTouchEvent
import com.mixpanel.android.sessionreplay.utils.MouseInteraction
import com.mixpanel.android.sessionreplay.utils.TouchSampling
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TouchEventRecorderTest {
    private lateinit var context: Context
    private val capturedEvents = mutableListOf<RawTouchEvent>()
    private val eventListener = object : EventListener {
        override fun receivedTouchEvent(rawEvent: RawTouchEvent) {
            capturedEvents += rawEvent
        }

        override fun receivedScreenshotEvent(rawEvent: RawScreenshotEvent) {}
    }

    private var touchStartCount = 0
    private var touchEndCount = 0
    private val touchListener = object : TouchEventListener {
        override fun onTouchStart() {
            touchStartCount++
        }

        override fun onTouchEnd() {
            touchEndCount++
        }
    }

    /** Fixed uptime -> wall clock delta, so emitted timestamps are exactly predictable. */
    private val epochOffset = 1_700_000_000_000L

    /** Arbitrary uptime base for the synthetic MotionEvents. */
    private val downTime = 10_000L

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        capturedEvents.clear()
        touchStartCount = 0
        touchEndCount = 0
        EventPublisher.shared.subscribe(eventListener)
    }

    @After
    fun tearDown() {
        EventPublisher.shared.unsubscribe(eventListener)
    }

    private fun createRecorder(
        density: Float = 1.0f,
        offsetX: Int = 0,
        offsetY: Int = 0
    ): TouchEventRecorder {
        context.resources.displayMetrics.density = density
        return TouchEventRecorder(
            context = context,
            touchEventListener = touchListener,
            windowOffsetProvider = { intArrayOf(offsetX, offsetY) },
            epochOffsetProvider = { epochOffset }
        )
    }

    private fun TouchEventRecorder.send(
        action: Int,
        rawX: Float,
        rawY: Float,
        eventTimeOffset: Long = 0L
    ) {
        val event = MotionEvent.obtain(
            downTime,
            downTime + eventTimeOffset,
            action,
            rawX,
            rawY,
            0
        )
        onTouchEvent(event)
        event.recycle()
    }

    private fun simulateTap(recorder: TouchEventRecorder, rawX: Float, rawY: Float) {
        recorder.send(MotionEvent.ACTION_DOWN, rawX, rawY)
        recorder.send(MotionEvent.ACTION_UP, rawX, rawY, eventTimeOffset = 50L)
    }

    private val interactions: List<RawTouchEvent.Interaction>
        get() = capturedEvents.filterIsInstance<RawTouchEvent.Interaction>()

    private val moves: List<RawTouchEvent.Move>
        get() = capturedEvents.filterIsInstance<RawTouchEvent.Move>()

    private fun firstStart(): RawTouchEvent.Interaction {
        val start = interactions.firstOrNull { it.type == MouseInteraction.TOUCH_START }
        assertNotNull("expected a TOUCH_START event", start)
        return start!!
    }

    // region coordinate scaling — asserted on the TOUCH_START point

    @Test
    fun testTap_noOffset_density1x() {
        val recorder = createRecorder(density = 1.0f)
        simulateTap(recorder, 100f, 200f)

        assertEquals(100, firstStart().point.x)
        assertEquals(200, firstStart().point.y)
    }

    @Test
    fun testTap_noOffset_density3x() {
        val recorder = createRecorder(density = 3.0f)
        simulateTap(recorder, 540f, 960f)

        assertEquals(180, firstStart().point.x)
        assertEquals(320, firstStart().point.y)
    }

    @Test
    fun testTap_withTopOffset_statusBar() {
        val recorder = createRecorder(density = 3.0f, offsetY = 84)
        simulateTap(recorder, 540f, 960f)

        assertEquals(180, firstStart().point.x)
        assertEquals(292, firstStart().point.y)
    }

    @Test
    fun testTap_withTopOffset_cameraCutout() {
        val recorder = createRecorder(density = 3.0f, offsetY = 145)
        simulateTap(recorder, 540f, 960f)

        assertEquals(180, firstStart().point.x)
        assertEquals(((960f - 145) / 3f).toInt(), firstStart().point.y)
    }

    @Test
    fun testTap_withLeftOffset_landscapeCutout() {
        val recorder = createRecorder(density = 2.0f, offsetX = 100)
        simulateTap(recorder, 500f, 400f)

        assertEquals(200, firstStart().point.x)
        assertEquals(200, firstStart().point.y)
    }

    @Test
    fun testTap_withBothOffsets() {
        val recorder = createRecorder(density = 2.0f, offsetX = 50, offsetY = 100)
        simulateTap(recorder, 600f, 800f)

        assertEquals(275, firstStart().point.x)
        assertEquals(350, firstStart().point.y)
    }

    @Test
    fun testTap_atWindowOrigin_yieldsZero() {
        val recorder = createRecorder(density = 2.0f, offsetX = 100, offsetY = 200)
        simulateTap(recorder, 100f, 200f)

        assertEquals(0, firstStart().point.x)
        assertEquals(0, firstStart().point.y)
    }

    @Test
    fun testTap_density2x_noOffset() {
        val recorder = createRecorder(density = 2.0f)
        simulateTap(recorder, 200f, 400f)

        assertEquals(100, firstStart().point.x)
        assertEquals(200, firstStart().point.y)
    }

    @Test
    fun testTap_density4x_withOffset() {
        val recorder = createRecorder(density = 4.0f, offsetX = 40, offsetY = 160)
        simulateTap(recorder, 720f, 1280f)

        assertEquals(170, firstStart().point.x)
        assertEquals(280, firstStart().point.y)
    }

    @Test
    fun testTap_density1x_withOffset() {
        val recorder = createRecorder(density = 1.0f, offsetX = 50, offsetY = 100)
        simulateTap(recorder, 300f, 500f)

        assertEquals(250, firstStart().point.x)
        assertEquals(400, firstStart().point.y)
    }

    @Test
    fun testTap_fractionalResults_truncated() {
        val recorder = createRecorder(density = 1.5f)
        simulateTap(recorder, 101f, 202f)

        assertEquals(67, firstStart().point.x)
        assertEquals(134, firstStart().point.y)
    }

    @Test
    fun testTap_zeroCoordinates() {
        val recorder = createRecorder(density = 3.0f)
        simulateTap(recorder, 0f, 0f)

        assertEquals(0, firstStart().point.x)
        assertEquals(0, firstStart().point.y)
    }

    // endregion

    // region gesture boundaries

    @Test
    fun testTap_emitsTouchStartThenTouchEnd() {
        val recorder = createRecorder(density = 1.0f)
        simulateTap(recorder, 100f, 200f)

        assertEquals(2, capturedEvents.size)
        assertEquals(
            listOf(MouseInteraction.TOUCH_START, MouseInteraction.TOUCH_END),
            interactions.map { it.type }
        )
        assertEquals(interactions[0].point, interactions[1].point)
    }

    @Test
    fun testTap_notifiesTouchStartAndTouchEndListener() {
        val recorder = createRecorder()
        simulateTap(recorder, 100f, 200f)

        assertEquals(1, touchStartCount)
        assertEquals(1, touchEndCount)
    }

    @Test
    fun testCancel_emitsTouchCancelAndEndsGesture() {
        val recorder = createRecorder()
        recorder.send(MotionEvent.ACTION_DOWN, 100f, 200f)
        recorder.send(MotionEvent.ACTION_CANCEL, 120f, 220f, eventTimeOffset = 40L)

        assertEquals(
            listOf(MouseInteraction.TOUCH_START, MouseInteraction.TOUCH_CANCEL),
            interactions.map { it.type }
        )
        assertEquals(1, touchEndCount)
    }

    /**
     * A long press used to leave the gesture open forever — GestureDetector's
     * `onSingleTapUp` never fires after a long press, so `onTouchEnd` was never called and
     * the screenshot timer it gates ran until the next tap.
     */
    @Test
    fun testLongPress_stillEndsGesture() {
        val recorder = createRecorder()
        recorder.send(MotionEvent.ACTION_DOWN, 100f, 200f)
        recorder.send(MotionEvent.ACTION_UP, 100f, 200f, eventTimeOffset = 2_000L)

        assertEquals(
            listOf(MouseInteraction.TOUCH_START, MouseInteraction.TOUCH_END),
            interactions.map { it.type }
        )
        assertEquals(1, touchEndCount)
    }

    @Test
    fun testMoveWithoutDown_isIgnored() {
        val recorder = createRecorder()
        recorder.send(MotionEvent.ACTION_MOVE, 100f, 200f, eventTimeOffset = 100L)
        recorder.send(MotionEvent.ACTION_UP, 100f, 200f, eventTimeOffset = 200L)

        assertTrue(capturedEvents.isEmpty())
        assertEquals(0, touchEndCount)
    }

    // endregion

    // region timestamps

    @Test
    fun testTimestamps_comeFromMotionEventTimeWithNoOffset() {
        val recorder = createRecorder()
        recorder.send(MotionEvent.ACTION_DOWN, 100f, 200f)
        recorder.send(MotionEvent.ACTION_UP, 100f, 200f, eventTimeOffset = 120L)

        assertEquals(epochOffset + downTime, interactions[0].timestamp)
        assertEquals(epochOffset + downTime + 120L, interactions[1].timestamp)
    }

    // endregion

    // region touch move batching

    @Test
    fun testSwipe_emitsMoveBatchBeforeTouchEnd() {
        val recorder = createRecorder(density = 3.0f, offsetY = 84)
        recorder.send(MotionEvent.ACTION_DOWN, 300f, 600f)
        recorder.send(MotionEvent.ACTION_MOVE, 600f, 900f, eventTimeOffset = 60L)
        recorder.send(MotionEvent.ACTION_MOVE, 900f, 1200f, eventTimeOffset = 120L)
        recorder.send(MotionEvent.ACTION_UP, 900f, 1200f, eventTimeOffset = 150L)

        assertEquals(3, capturedEvents.size)
        assertEquals(MouseInteraction.TOUCH_START, (capturedEvents[0] as RawTouchEvent.Interaction).type)
        assertTrue(capturedEvents[1] is RawTouchEvent.Move)
        assertEquals(MouseInteraction.TOUCH_END, (capturedEvents[2] as RawTouchEvent.Interaction).type)

        val samples = (capturedEvents[1] as RawTouchEvent.Move).samples
        assertEquals(2, samples.size)
        assertEquals(200, samples[0].point.x)
        assertEquals(((900f - 84) / 3f).toInt(), samples[0].point.y)
        assertEquals(epochOffset + downTime + 60L, samples[0].timestamp)
        assertEquals(300, samples[1].point.x)
        assertEquals(((1200f - 84) / 3f).toInt(), samples[1].point.y)
        assertEquals(epochOffset + downTime + 120L, samples[1].timestamp)
    }

    @Test
    fun testMoves_closerThanSampleIntervalAreDropped() {
        val recorder = createRecorder()
        recorder.send(MotionEvent.ACTION_DOWN, 0f, 0f)
        // 16ms apart (one frame) — only the samples that clear the 50ms budget survive.
        for (frame in 1..8) {
            recorder.send(MotionEvent.ACTION_MOVE, frame * 10f, 0f, eventTimeOffset = frame * 16L)
        }
        recorder.send(MotionEvent.ACTION_UP, 80f, 0f, eventTimeOffset = 150L)

        val samples = moves.single().samples
        assertEquals(2, samples.size)
        assertEquals(epochOffset + downTime + 64L, samples[0].timestamp)
        assertEquals(epochOffset + downTime + 128L, samples[1].timestamp)
    }

    @Test
    fun testLongDrag_flushesBatchOnceItSpansTheBatchInterval() {
        val recorder = createRecorder()
        recorder.send(MotionEvent.ACTION_DOWN, 0f, 0f)
        // 12 samples at 50ms — the batch spans 500ms at the 11th and flushes there.
        for (sample in 1..12) {
            recorder.send(MotionEvent.ACTION_MOVE, sample * 10f, 0f, eventTimeOffset = sample * 50L)
        }
        recorder.send(MotionEvent.ACTION_UP, 120f, 0f, eventTimeOffset = 650L)

        assertEquals(2, moves.size)
        assertEquals(11, moves[0].samples.size)
        assertEquals(1, moves[1].samples.size)
        assertEquals(
            TouchSampling.MOVE_BATCH_INTERVAL_MS,
            moves[0].samples.last().timestamp - moves[0].samples.first().timestamp
        )
        // Batch timestamp is its final sample, which is what timeOffsets are measured against.
        assertEquals(moves[0].samples.last().timestamp, moves[0].timestamp)
    }

    @Test
    fun testGesture_doesNotLeakSamplesIntoTheNextGesture() {
        val recorder = createRecorder()
        recorder.send(MotionEvent.ACTION_DOWN, 0f, 0f)
        recorder.send(MotionEvent.ACTION_MOVE, 100f, 0f, eventTimeOffset = 60L)
        recorder.send(MotionEvent.ACTION_UP, 100f, 0f, eventTimeOffset = 80L)
        capturedEvents.clear()

        recorder.send(MotionEvent.ACTION_DOWN, 0f, 0f, eventTimeOffset = 1_000L)
        recorder.send(MotionEvent.ACTION_UP, 0f, 0f, eventTimeOffset = 1_050L)

        assertEquals(0, moves.size)
        assertEquals(2, interactions.size)
    }

    // endregion
}
