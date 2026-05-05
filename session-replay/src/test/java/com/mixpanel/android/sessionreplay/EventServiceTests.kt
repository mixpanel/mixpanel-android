package com.mixpanel.android.sessionreplay

import com.mixpanel.android.sessionreplay.services.EventService
import com.mixpanel.android.sessionreplay.utils.EventType
import com.mixpanel.android.sessionreplay.utils.IncrementalSource
import com.mixpanel.android.sessionreplay.utils.PayloadObjectId
import com.mixpanel.android.sessionreplay.utils.SessionReplayEncoder
import com.mixpanel.android.sessionreplay.utils.TimingAdjustment
import com.mixpanel.android.sessionreplay.utils.TouchInteraction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import android.util.Base64
import com.mixpanel.android.sessionreplay.models.SessionEvent
import com.mixpanel.android.sessionreplay.models.SessionEventData
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventServiceTests {
    private lateinit var eventService: EventService
    private val testDispatcher = StandardTestDispatcher()

    // Sample data for tests (adjust as needed)
    private val touchEvent = SessionEvent(
        type = EventType.INCREMENTAL_SNAPSHOT,
        data = SessionEventData.DetailedData(
            source = IncrementalSource.TOUCH_INTERACTION,
            type = TouchInteraction.START,
            id = PayloadObjectId.MAIN_SNAPSHOT,
            x = 0,
            y = 0
        ),
        timestamp = System.currentTimeMillis() + TimingAdjustment.TOUCH_INTERACTION
    )

    private lateinit var mainScreenshotEvent: SessionEvent
    private lateinit var screenshotEvent: SessionEvent

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        eventService = EventService()

        // Mock Base64 for unit tests
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } returns "dGVzdEltYWdlRGF0YQ=="

        // Initialize screenshot events after mocking
        runBlocking {
            val imageData = ByteArray(0) // Empty byte array for example
            mainScreenshotEvent = SessionReplayEncoder.mainSessionEvent(imageData)!!

            // Introduce a slight delay to ensure different timestamps
            delay(1000)
            screenshotEvent = SessionReplayEncoder.incrementalSessionEvent(imageData)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Base64::class)
    }

    @Test
    fun testEnqueueScreenshotEvent() =
        runTest {
            eventService.enqueueEvent(screenshotEvent)

            delay(500)

            assertEquals(1, eventService.eventsCount)
            assertFalse(eventService.isEventsEmpty)
        }

    @Test
    fun testEnqueueTouchEvent() =
        runTest {
            eventService.enqueueEvent(touchEvent)

            delay(500)

            assertEquals(1, eventService.eventsCount)
            assertFalse(eventService.isEventsEmpty)
        }

    @Test
    fun testEnqueueOverTheQueueSizeLimit() =
        runTest {
            val eventService = EventService(queueSizeLimit = 5)
            repeat(6) { eventService.enqueueEvent(touchEvent) }

            delay(500)

            assertEquals(5, eventService.eventsCount)
            assertFalse(eventService.isEventsEmpty)
        }

    @Test
    fun testDequeueOneEvent() =
        runTest {
            eventService.enqueueEvent(screenshotEvent)
            eventService.enqueueEvent(touchEvent)

            delay(500)

            val dequeuedEvents = eventService.dequeueEvents(1)
            assertEquals(1, dequeuedEvents.size)
            assertEquals(1, eventService.eventsCount)
        }

    @Test
    fun testDequeueEvents() =
        runTest {
            eventService.enqueueEvent(mainScreenshotEvent)
            eventService.enqueueEvent(screenshotEvent)
            eventService.enqueueEvent(touchEvent)

            delay(500)

            val dequeuedEvents = eventService.dequeueEvents(2)
            delay(500)
            assertEquals(2, dequeuedEvents.size)
            assertEquals(1, eventService.eventsCount)
        }

    @Test
    fun testDequeueEventsLargerThanQueueSize() =
        runTest {
            eventService.enqueueEvent(mainScreenshotEvent)
            eventService.enqueueEvent(screenshotEvent)
            eventService.enqueueEvent(touchEvent)

            delay(500)

            val dequeuedEvents = eventService.dequeueEvents(5) // Request more than the queue size
            assertEquals(3, dequeuedEvents.size)
            assertEquals(0, eventService.eventsCount)
        }

    @Test
    fun testPrependEvents() =
        runTest {
            eventService.enqueueEvent(mainScreenshotEvent)
            eventService.enqueueEvent(screenshotEvent)
            eventService.enqueueEvent(touchEvent)

            delay(500)

            val dequeuedEvents = eventService.dequeueEvents(3)
            assertEquals(3, dequeuedEvents.size)
            assertEquals(0, eventService.eventsCount)

            eventService.prependEvents(dequeuedEvents)
            assertEquals(3, eventService.eventsCount)
        }

    @Test
    fun testClearEvents() =
        runTest {
            eventService.enqueueEvent(mainScreenshotEvent)
            eventService.enqueueEvent(screenshotEvent)
            eventService.enqueueEvent(touchEvent)

            delay(500)

            eventService.clearEvents()
            assertEquals(0, eventService.eventsCount)
            assertTrue(eventService.isEventsEmpty)
        }

    @Test
    fun testEvictionWithoutFullSnapshot() =
        runTest {
            val eventService = EventService(queueSizeLimit = 5)

            repeat(5) { eventService.enqueueEvent(screenshotEvent) }

            delay(500)

            assertEquals(5, eventService.eventsCount)

            eventService.enqueueEvent(screenshotEvent)

            delay(500)

            assertEquals(5, eventService.eventsCount)
        }

    @Test
    fun testEvictionWithFullSnapshotWithEnqueue() =
        runTest {
            val eventService = EventService(queueSizeLimit = 5)
            eventService.enqueueEvent(mainScreenshotEvent)
            repeat(3) { eventService.enqueueEvent(screenshotEvent) }

            eventService.enqueueEvent(mainScreenshotEvent) // Another full snapshot

            delay(500)
            assertEquals(5, eventService.eventsCount)

            eventService.enqueueEvent(screenshotEvent)

            delay(500)

            assertEquals(2, eventService.eventsCount)
        }

    @Test
    fun testEvictionWithFullSnapshotWithPrepend() =
        runTest {
            val eventService = EventService(queueSizeLimit = 5)
            eventService.enqueueEvent(mainScreenshotEvent)
            repeat(2) { eventService.enqueueEvent(screenshotEvent) }

            delay(500)
            assertEquals(3, eventService.eventsCount)

            val eventsToFlush = eventService.dequeueEvents(3)

            delay(500)
            assertEquals(0, eventService.eventsCount)

            // More events coming in
            eventService.enqueueEvent(mainScreenshotEvent)
            repeat(3) { eventService.enqueueEvent(screenshotEvent) }
            eventService.enqueueEvent(mainScreenshotEvent)

            delay(500)
            assertEquals(5, eventService.eventsCount)

            eventService.prependEvents(eventsToFlush) // Simulate flush failure

            delay(500)

            assertEquals(4, eventService.eventsCount)
        }
}
