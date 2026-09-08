package com.mixpanel.android.sessionreplay.tracking

import android.graphics.Point
import com.mixpanel.android.sessionreplay.models.RawScreenshotEvent
import com.mixpanel.android.sessionreplay.models.RawTouchEvent
import com.mixpanel.android.sessionreplay.models.SessionEvent
import com.mixpanel.android.sessionreplay.models.SessionEventData
import com.mixpanel.android.sessionreplay.services.EventService
import com.mixpanel.android.sessionreplay.utils.DeviceInfo
import com.mixpanel.android.sessionreplay.utils.EventType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the meta event that states the viewport the player scales each frame to. Taken from the
 * system display metrics it is shorter than the captured frame on a non-edge-to-edge activity,
 * and the player crops the bottom of the app UI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ViewportMetaEventTest {
    private lateinit var eventService: EventService
    private lateinit var eventHandler: EventHandler

    private companion object {
        val IMAGE = byteArrayOf(1, 2, 3)
        const val PORTRAIT_WIDTH = 411
        const val PORTRAIT_HEIGHT = 519
    }

    @Before
    fun setUp() {
        eventService = EventService()
        eventHandler = EventHandler(eventService)
        eventHandler.initialize()
    }

    @After
    fun tearDown() {
        eventHandler.deinitialize()
    }

    private fun capture(
        width: Int,
        height: Int,
        isInitial: Boolean = true
    ) {
        eventHandler.receivedScreenshotEvent(RawScreenshotEvent(IMAGE, isInitial, width, height))
    }

    /**
     * Drains everything queued so far. A touch event runs on the same serial executor as the
     * frames, so its arrival proves every frame queued before it has been processed.
     */
    private fun drain(): List<SessionEvent> {
        eventHandler.receivedTouchEvent(RawTouchEvent(Point(0, 0), Point(0, 0), isSwipe = false))
        val drained = mutableListOf<SessionEvent>()
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            drained += eventService.dequeueEvents(100)
            if (drained.any { it.data is SessionEventData.DetailedData }) {
                return drained.dropLast(1)
            }
            Thread.sleep(5)
        }
        throw AssertionError("Timed out waiting for queued events")
    }

    private fun dimensionsOf(event: SessionEvent): Pair<Int, Int> {
        assertEquals(EventType.META, event.type)
        val data = event.data as SessionEventData.DimensionData
        return data.width to data.height
    }

    @Test
    fun `the first frame of a replay emits a meta describing that frame`() {
        capture(PORTRAIT_WIDTH, PORTRAIT_HEIGHT)

        val events = drain()

        assertEquals(2, events.size)
        assertEquals(PORTRAIT_WIDTH to PORTRAIT_HEIGHT, dimensionsOf(events[0]))
        assertEquals(EventType.FULL_SNAPSHOT, events[1].type)
    }

    @Test
    fun `the meta describes the captured frame rather than the system metrics`() {
        // The reported case: a window taller than the metrics report, by a bottom tab bar's worth
        // of UI — the slice the player was cropping.
        val capturedHeight = DeviceInfo.screenHeight + 49

        capture(DeviceInfo.screenWidth, capturedHeight)

        val (_, height) = dimensionsOf(drain()[0])
        assertEquals(capturedHeight, height)
        assertNotEquals(DeviceInfo.screenHeight, height)
    }

    @Test
    fun `the meta shares the timestamp of the frame it describes`() {
        capture(PORTRAIT_WIDTH, PORTRAIT_HEIGHT)

        val events = drain()

        assertEquals(events[1].timestamp, events[0].timestamp)
    }

    @Test
    fun `a frame at unchanged dimensions does not repeat the meta`() {
        capture(PORTRAIT_WIDTH, PORTRAIT_HEIGHT)
        capture(PORTRAIT_WIDTH, PORTRAIT_HEIGHT)

        val events = drain()

        assertEquals(1, events.count { it.type == EventType.META })
        assertEquals(2, events.count { it.type == EventType.FULL_SNAPSHOT })
    }

    @Test
    fun `a rotation emits a second meta ahead of the frame it describes`() {
        capture(PORTRAIT_WIDTH, PORTRAIT_HEIGHT)
        capture(PORTRAIT_HEIGHT, PORTRAIT_WIDTH)

        val events = drain()

        // Both sizes are described within one batch, which a single meta per batch could not do.
        assertEquals(4, events.size)
        assertEquals(PORTRAIT_WIDTH to PORTRAIT_HEIGHT, dimensionsOf(events[0]))
        assertEquals(EventType.FULL_SNAPSHOT, events[1].type)
        assertEquals(PORTRAIT_HEIGHT to PORTRAIT_WIDTH, dimensionsOf(events[2]))
        assertEquals(EventType.FULL_SNAPSHOT, events[3].type)
    }

    @Test
    fun `a new replay re-emits the meta at unchanged dimensions`() {
        capture(PORTRAIT_WIDTH, PORTRAIT_HEIGHT)
        eventHandler.resetViewportTracking()
        capture(PORTRAIT_WIDTH, PORTRAIT_HEIGHT)

        val events = drain()

        assertEquals(2, events.count { it.type == EventType.META })
        assertEquals(PORTRAIT_WIDTH to PORTRAIT_HEIGHT, dimensionsOf(events[2]))
    }

    @Test
    fun `a failed capture does not describe the viewport`() {
        capture(0, 0)

        val events = drain()

        assertTrue(events.none { it.type == EventType.META })
    }

    @Test
    fun `an incremental frame is described the same way as a full one`() {
        capture(PORTRAIT_WIDTH, PORTRAIT_HEIGHT, isInitial = false)

        val events = drain()

        assertEquals(PORTRAIT_WIDTH to PORTRAIT_HEIGHT, dimensionsOf(events[0]))
        assertEquals(EventType.INCREMENTAL_SNAPSHOT, events[1].type)
    }
}
