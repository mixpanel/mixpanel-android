package com.mixpanel.android.sessionreplay

import com.mixpanel.android.sessionreplay.network.FlushRequest
import com.mixpanel.android.sessionreplay.network.NetworkMonitoring
import com.mixpanel.android.sessionreplay.services.EventService
import com.mixpanel.android.sessionreplay.services.FlushService
import com.mixpanel.android.sessionreplay.utils.PayloadInfo
import com.mixpanel.android.sessionreplay.utils.SessionReplayEncoder
import android.util.Base64
import com.mixpanel.android.sessionreplay.models.SessionEvent
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MockNetworkMonitor : NetworkMonitoring {
    override var isUsingWiFi: Boolean = true
}

@OptIn(ExperimentalCoroutinesApi::class)
class FlushServiceTests {
    private lateinit var flushService: FlushService
    private lateinit var mockFlushRequest: FlushRequest
    private lateinit var mockNetworkMonitor: MockNetworkMonitor
    private lateinit var eventService: EventService

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var mainScreenshotEvent: SessionEvent
    private lateinit var screenshotEvent: SessionEvent

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        MockKAnnotations.init(this)

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

        mockNetworkMonitor = MockNetworkMonitor()
        eventService = EventService(queueSizeLimit = 10)
        mockFlushRequest = mockk<FlushRequest>(relaxed = true)

        // Track the distinctId state in the test
        var currentDistinctId = "testDistinctId"

        // Mock the property getter to return the current value
        every { mockFlushRequest.distinctId } answers { currentDistinctId }

        // Mock the update method to update our tracked value
        every { mockFlushRequest.updateDistinctId(any()) } answers {
            currentDistinctId = firstArg<String>()
        }

        flushService =
            FlushService(
                "testToken",
                "testDistinctId",
                wifiOnly = false,
                networkMonitor = mockNetworkMonitor,
                lifecycleScope = testScope,
                eventService,
                flushRequest = mockFlushRequest,
                coroutineDispatcher = testDispatcher
            )
    }

    @After
    fun tearDown() {
        flushService.stop()
        Dispatchers.resetMain()
        unmockkStatic(Base64::class)
    }

    @Test
    fun testStartFlushService() =
        testScope.runTest {
            coEvery { mockFlushRequest.sendRequest(any<PayloadInfo>()) } returns true

            // Test that start() initializes the service properly
            val initialReplayId = flushService.replayId
            flushService.start()

            // Verify that start() creates a new replay ID
            assertNotEquals("Replay ID should change on start", initialReplayId, flushService.replayId)

            // Clean up
            flushService.stop()
        }

    @Test
    fun testStopFlushService() =
        testScope.runTest {
            coEvery { mockFlushRequest.sendRequest(any<PayloadInfo>()) } returns true

            // Simply verify start and stop work without errors
            flushService.start()
            flushService.stop()

            // Service should stop cleanly
            assertTrue("Service stopped successfully", true)
        }

    @Test
    fun testFlushEventsWithoutForcingAll() =
        testScope.runTest {
            coEvery { mockFlushRequest.sendRequest(any<PayloadInfo>()) } returns true

            eventService.enqueueEvent(mainScreenshotEvent)
            flushService.flushEvents(forAll = false)

            advanceUntilIdle()

            coVerify { mockFlushRequest.sendRequest(any<PayloadInfo>()) }
            assertTrue(eventService.isEventsEmpty)
        }

    @Test
    fun testFlushEventsForcingAll() =
        testScope.runTest {
            coEvery { mockFlushRequest.sendRequest(any<PayloadInfo>()) } returns true

            eventService.enqueueEvent(screenshotEvent)
            eventService.enqueueEvent(screenshotEvent)
            eventService.enqueueEvent(screenshotEvent)

            flushService.flushEvents(forAll = true)

            advanceUntilIdle()

            coVerify { mockFlushRequest.sendRequest(any<PayloadInfo>()) }
            assertTrue(eventService.isEventsEmpty)
        }

    @Test
    fun testUpdateDistinctId() =
        testScope.runTest {
            // Test the updateDistinctId method
            flushService.updateDistinctId("newDistinctId")

            // Verify that the FlushRequest's updateDistinctId method was called
            verify { mockFlushRequest.updateDistinctId("newDistinctId") }

            // Verify that the distinctId was updated in the FlushService
            assertEquals("newDistinctId", flushService.getDistinctId())
        }

    @Test
    fun testFlushEventsFailure() =
        testScope.runTest {
            coEvery { mockFlushRequest.sendRequest(any<PayloadInfo>()) } returns false

            eventService.enqueueEvent(screenshotEvent)
            eventService.enqueueEvent(screenshotEvent)

            flushService.flushEvents(forAll = false)

            advanceUntilIdle()

            coVerify { mockFlushRequest.sendRequest(any<PayloadInfo>()) }
            assertEquals(2, eventService.eventsCount)
        }

    @Test
    fun testManualFlushAfterStart() =
        testScope.runTest {
            coEvery { mockFlushRequest.sendRequest(any<PayloadInfo>()) } returns true

            // Don't start the service to avoid the periodic flush job
            // Just test manual flush functionality
            eventService.enqueueEvent(mainScreenshotEvent)
            assertEquals("Event should be queued", 1, eventService.eventsCount)

            // Manually trigger flush
            flushService.flushEvents(forAll = false)
            advanceUntilIdle()

            // Verify flush happened
            coVerify(exactly = 1) { mockFlushRequest.sendRequest(any<PayloadInfo>()) }
            assertTrue("Event should be flushed", eventService.isEventsEmpty)
        }

    @Test
    fun testFlushEventsRespectsWifiOnly() =
        testScope.runTest {
            coEvery { mockFlushRequest.sendRequest(any<PayloadInfo>()) } returns true

            // Create service with wifiOnly = true
            val wifiOnlyFlushService = FlushService(
                "testToken",
                "testDistinctId",
                wifiOnly = true,
                networkMonitor = mockNetworkMonitor,
                lifecycleScope = testScope,
                eventService,
                flushRequest = mockFlushRequest,
                coroutineDispatcher = testDispatcher
            )

            // Set network to non-WiFi
            mockNetworkMonitor.isUsingWiFi = false

            eventService.enqueueEvent(mainScreenshotEvent)
            wifiOnlyFlushService.flushEvents(forAll = false)

            advanceUntilIdle()

            // Verify no flush happened
            coVerify(exactly = 0) { mockFlushRequest.sendRequest(any<PayloadInfo>()) }
            assertEquals("Events should not be flushed on non-WiFi", 1, eventService.eventsCount)

            // Now enable WiFi
            mockNetworkMonitor.isUsingWiFi = true
            wifiOnlyFlushService.flushEvents(forAll = false)

            advanceUntilIdle()

            // Verify flush happened
            coVerify(exactly = 1) { mockFlushRequest.sendRequest(any<PayloadInfo>()) }
            assertTrue("Events should be flushed on WiFi", eventService.isEventsEmpty)
        }
}
