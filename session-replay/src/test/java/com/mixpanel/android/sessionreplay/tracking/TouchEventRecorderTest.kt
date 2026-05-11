package com.mixpanel.android.sessionreplay.tracking

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import com.mixpanel.android.sessionreplay.models.RawScreenshotEvent
import com.mixpanel.android.sessionreplay.models.RawTouchEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TouchEventRecorderTest {
    private lateinit var context: Context
    private var capturedEvent: RawTouchEvent? = null
    private val eventListener = object : EventListener {
        override fun receivedTouchEvent(rawEvent: RawTouchEvent) {
            capturedEvent = rawEvent
        }

        override fun receivedScreenshotEvent(rawEvent: RawScreenshotEvent) {}
    }
    private val touchListener = object : TouchEventListener {
        override fun onTouchStart() {}
        override fun onTouchEnd() {}
    }

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        capturedEvent = null
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
            windowOffsetProvider = { intArrayOf(offsetX, offsetY) }
        )
    }

    private fun simulateTap(recorder: TouchEventRecorder, rawX: Float, rawY: Float) {
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, rawX, rawY, 0)
        val up = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, rawX, rawY, 0)
        recorder.onTouchEvent(down)
        recorder.onTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    @Test
    fun testTap_noOffset_density1x() {
        val recorder = createRecorder(density = 1.0f)
        simulateTap(recorder, 100f, 200f)

        assertNotNull(capturedEvent)
        assertEquals(100, capturedEvent!!.start.x)
        assertEquals(200, capturedEvent!!.start.y)
    }

    @Test
    fun testTap_noOffset_density3x() {
        val recorder = createRecorder(density = 3.0f)
        simulateTap(recorder, 540f, 960f)

        assertNotNull(capturedEvent)
        assertEquals(180, capturedEvent!!.start.x)
        assertEquals(320, capturedEvent!!.start.y)
    }

    @Test
    fun testTap_withTopOffset_statusBar() {
        val recorder = createRecorder(density = 3.0f, offsetY = 84)
        simulateTap(recorder, 540f, 960f)

        assertNotNull(capturedEvent)
        assertEquals(180, capturedEvent!!.start.x)
        assertEquals(292, capturedEvent!!.start.y)
    }

    @Test
    fun testTap_withTopOffset_cameraCutout() {
        val recorder = createRecorder(density = 3.0f, offsetY = 145)
        simulateTap(recorder, 540f, 960f)

        assertNotNull(capturedEvent)
        assertEquals(180, capturedEvent!!.start.x)
        assertEquals(((960f - 145) / 3f).toInt(), capturedEvent!!.start.y)
    }

    @Test
    fun testTap_withLeftOffset_landscapeCutout() {
        val recorder = createRecorder(density = 2.0f, offsetX = 100)
        simulateTap(recorder, 500f, 400f)

        assertNotNull(capturedEvent)
        assertEquals(200, capturedEvent!!.start.x)
        assertEquals(200, capturedEvent!!.start.y)
    }

    @Test
    fun testTap_withBothOffsets() {
        val recorder = createRecorder(density = 2.0f, offsetX = 50, offsetY = 100)
        simulateTap(recorder, 600f, 800f)

        assertNotNull(capturedEvent)
        assertEquals(275, capturedEvent!!.start.x)
        assertEquals(350, capturedEvent!!.start.y)
    }

    @Test
    fun testTap_atWindowOrigin_yieldsZero() {
        val recorder = createRecorder(density = 2.0f, offsetX = 100, offsetY = 200)
        simulateTap(recorder, 100f, 200f)

        assertNotNull(capturedEvent)
        assertEquals(0, capturedEvent!!.start.x)
        assertEquals(0, capturedEvent!!.start.y)
    }

    @Test
    fun testTap_clickEventHasSameStartAndEnd() {
        val recorder = createRecorder(density = 3.0f, offsetY = 84)
        simulateTap(recorder, 540f, 960f)

        assertNotNull(capturedEvent)
        assertEquals(capturedEvent!!.start, capturedEvent!!.end)
        assertEquals(false, capturedEvent!!.isSwipe)
    }

    @Test
    fun testTap_density2x_noOffset() {
        val recorder = createRecorder(density = 2.0f)
        simulateTap(recorder, 200f, 400f)

        assertNotNull(capturedEvent)
        assertEquals(100, capturedEvent!!.start.x)
        assertEquals(200, capturedEvent!!.start.y)
    }

    @Test
    fun testTap_density4x_withOffset() {
        val recorder = createRecorder(density = 4.0f, offsetX = 40, offsetY = 160)
        simulateTap(recorder, 720f, 1280f)

        assertNotNull(capturedEvent)
        assertEquals(170, capturedEvent!!.start.x)
        assertEquals(280, capturedEvent!!.start.y)
    }

    @Test
    fun testTap_density1x_withOffset() {
        val recorder = createRecorder(density = 1.0f, offsetX = 50, offsetY = 100)
        simulateTap(recorder, 300f, 500f)

        assertNotNull(capturedEvent)
        assertEquals(250, capturedEvent!!.start.x)
        assertEquals(400, capturedEvent!!.start.y)
    }

    @Test
    fun testTap_fractionalResults_truncated() {
        val recorder = createRecorder(density = 1.5f)
        simulateTap(recorder, 101f, 202f)

        assertNotNull(capturedEvent)
        assertEquals(67, capturedEvent!!.start.x)
        assertEquals(134, capturedEvent!!.start.y)
    }

    @Test
    fun testTap_zeroCoordinates() {
        val recorder = createRecorder(density = 3.0f)
        simulateTap(recorder, 0f, 0f)

        assertNotNull(capturedEvent)
        assertEquals(0, capturedEvent!!.start.x)
        assertEquals(0, capturedEvent!!.start.y)
    }

    @Test
    fun testSwipe_coordinatesScaledWithOffset() {
        val recorder = createRecorder(density = 3.0f, offsetY = 84)
        val downTime = SystemClock.uptimeMillis()

        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 300f, 600f, 0)
        recorder.onTouchEvent(down)
        down.recycle()

        val move = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, 900f, 1200f, 0)
        recorder.onTouchEvent(move)
        move.recycle()

        val up = MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_UP, 900f, 1200f, 0)
        recorder.onTouchEvent(up)
        up.recycle()

        // Trigger the end-of-scroll runnable (posted with 200ms delay)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertNotNull(capturedEvent)
        assertEquals(true, capturedEvent!!.isSwipe)
        assertEquals(100, capturedEvent!!.start.x)
        assertEquals(((600f - 84) / 3f).toInt(), capturedEvent!!.start.y)
        assertEquals(300, capturedEvent!!.end.x)
        assertEquals(((1200f - 84) / 3f).toInt(), capturedEvent!!.end.y)
    }
}
