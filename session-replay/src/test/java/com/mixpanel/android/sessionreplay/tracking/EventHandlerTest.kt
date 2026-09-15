package com.mixpanel.android.sessionreplay.tracking

import android.graphics.Point
import com.mixpanel.android.sessionreplay.models.RawScreenshotEvent
import com.mixpanel.android.sessionreplay.models.RawTouchEvent
import com.mixpanel.android.sessionreplay.models.SessionEvent
import com.mixpanel.android.sessionreplay.models.SessionEventData
import com.mixpanel.android.sessionreplay.models.TouchSample
import com.mixpanel.android.sessionreplay.services.EventService
import com.mixpanel.android.sessionreplay.utils.EventType
import com.mixpanel.android.sessionreplay.utils.IncrementalSource
import com.mixpanel.android.sessionreplay.utils.MouseInteraction
import com.mixpanel.android.sessionreplay.utils.PayloadObjectId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the rrweb wire encoding of touch events — see
 * https://github.com/rrweb-io/rrweb/blob/master/packages/types/src/index.ts
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EventHandlerTest {
    private lateinit var eventService: EventService
    private lateinit var eventHandler: EventHandler

    @Before
    fun setup() {
        eventService = EventService()
        eventHandler = EventHandler(eventService)
        eventHandler.initialize()
    }

    @After
    fun tearDown() {
        eventHandler.deinitialize()
    }

    /** The handler encodes on a serial executor, so wait for the queue to catch up. */
    private fun awaitEvents(count: Int): List<SessionEvent> {
        val deadline = System.currentTimeMillis() + 2_000
        while (eventService.eventsCount < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(5)
        }
        assertEquals(count, eventService.eventsCount)
        return eventService.dequeueEvents(count)
    }

    @Test
    fun touchStart_encodesAsMouseInteraction() {
        EventPublisher.shared.publishTouchEvent(
            RawTouchEvent.Interaction(
                type = MouseInteraction.TOUCH_START,
                point = Point(12, 34),
                timestamp = 1_700_000_000_000L
            )
        )

        val event = awaitEvents(1).single()
        assertEquals(EventType.INCREMENTAL_SNAPSHOT, event.type)
        assertEquals(1_700_000_000_000L, event.timestamp)

        val data = event.data as SessionEventData.DetailedData
        assertEquals(IncrementalSource.MOUSE_INTERACTION, data.source)
        assertEquals(MouseInteraction.TOUCH_START, data.type)
        assertEquals(PayloadObjectId.MAIN_SNAPSHOT, data.id)
        assertEquals(12, data.x)
        assertEquals(34, data.y)
    }

    @Test
    fun touchEnd_carriesItsOwnInteractionType() {
        EventPublisher.shared.publishTouchEvent(
            RawTouchEvent.Interaction(
                type = MouseInteraction.TOUCH_END,
                point = Point(1, 2),
                timestamp = 5_000L
            )
        )

        val data = awaitEvents(1).single().data as SessionEventData.DetailedData
        assertEquals(MouseInteraction.TOUCH_END, data.type)
    }

    @Test
    fun touchCancel_carriesItsOwnInteractionType() {
        EventPublisher.shared.publishTouchEvent(
            RawTouchEvent.Interaction(
                type = MouseInteraction.TOUCH_CANCEL,
                point = Point(1, 2),
                timestamp = 5_000L
            )
        )

        val data = awaitEvents(1).single().data as SessionEventData.DetailedData
        assertEquals(MouseInteraction.TOUCH_CANCEL, data.type)
    }

    @Test
    fun moveBatch_encodesAsTouchMovePositionsWithNegativeOffsets() {
        val batchEnd = 1_700_000_000_500L
        EventPublisher.shared.publishTouchEvent(
            RawTouchEvent.Move(
                samples = listOf(
                    TouchSample(Point(10, 20), batchEnd - 150),
                    TouchSample(Point(30, 40), batchEnd - 50),
                    TouchSample(Point(50, 60), batchEnd)
                )
            )
        )

        val event = awaitEvents(1).single()
        assertEquals(EventType.INCREMENTAL_SNAPSHOT, event.type)
        // The batch is stamped at its final sample, so the player schedules each position at
        // `event.timestamp + timeOffset`.
        assertEquals(batchEnd, event.timestamp)

        val data = event.data as SessionEventData.PositionData
        assertEquals(IncrementalSource.TOUCH_MOVE, data.source)
        assertEquals(listOf(-150, -50, 0), data.positions.map { it.timeOffset })
        assertTrue(data.positions.all { it.timeOffset <= 0 })
        assertTrue(data.positions.all { it.id == PayloadObjectId.MAIN_SNAPSHOT })
        assertEquals(listOf(10.0, 30.0, 50.0), data.positions.map { it.x })
        assertEquals(listOf(20.0, 40.0, 60.0), data.positions.map { it.y })
    }

    /**
     * Screenshots are encoded on this same serial queue, arbitrarily long after the pixels
     * were read — and after JPEG compression. They must report the frame's capture instant,
     * or a pre-tap screen can sort after the tap that acted on it.
     */
    @Test
    fun incrementalScreenshot_isStampedAtCaptureTimeNotEncodeTime() {
        val capturedAtMs = 1_700_000_000_000L
        EventPublisher.shared.publishSessionEvent(
            RawScreenshotEvent(data = ByteArray(4), isInitial = false, timestamp = capturedAtMs)
        )

        val event = awaitEvents(1).single()
        assertEquals(EventType.INCREMENTAL_SNAPSHOT, event.type)
        assertEquals(capturedAtMs, event.timestamp)
    }

    @Test
    fun initialScreenshot_isStampedAtCaptureTimeNotEncodeTime() {
        val capturedAtMs = 1_700_000_000_000L
        EventPublisher.shared.publishSessionEvent(
            RawScreenshotEvent(data = ByteArray(4), isInitial = true, timestamp = capturedAtMs)
        )

        assertEquals(capturedAtMs, awaitEvents(1).single().timestamp)
    }
}
