package com.mixpanel.android.sessionreplay

import com.mixpanel.android.sessionreplay.network.APIRequest
import com.mixpanel.android.sessionreplay.network.FlushRequest
import com.mixpanel.android.sessionreplay.network.Network
import com.mixpanel.android.sessionreplay.utils.EndPoints
import com.mixpanel.android.sessionreplay.utils.PayloadInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

// Mock classes for testing
class MockNetwork : Network() {
    var performRequestResult: Result<Unit>? = null

    override suspend fun performAPIRequest(apiRequest: APIRequest): Result<Unit> =
        performRequestResult ?: Result.failure(Exception("No mock result set"))
}

@OptIn(ExperimentalCoroutinesApi::class)
class FlushRequestTests {
    private lateinit var flushRequest: FlushRequest
    private lateinit var mockNetwork: MockNetwork
    private val testDispatcher = StandardTestDispatcher()

    @Before // Set up before each test
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockNetwork = MockNetwork()
        flushRequest = FlushRequest("testToken", "testDistinctId", EndPoints.DEFAULT_BASE_URL, mockNetwork)
    }

    @After // Clean up after each test
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSendRequestNotAllowedDueToExponentialBackoff() =
        runBlocking {
            flushRequest.networkRequestsAllowedAfterTime =
                Date().time / 1000.0 + 1000 // Convert to seconds
            val payloadInfo = PayloadInfo(emptyList(), 1.0, 1, "test", 1, 1.0)
            val result = flushRequest.sendRequest(payloadInfo)

            assertFalse(result)
        }

    @Test
    fun testSendRequestSuccess() =
        runBlocking {
            mockNetwork.performRequestResult = Result.success(Unit)
            val payloadInfo = PayloadInfo(emptyList(), 1.0, 1, "test", 1, 1.0)

            val result = flushRequest.sendRequest(payloadInfo)

            assertTrue(result)
            assertEquals(0, flushRequest.networkConsecutiveFailures)
        }

    @Test
    fun testSendRequestFailure() =
        runBlocking {
            mockNetwork.performRequestResult = Result.failure(Exception("Test error"))
            val payloadInfo = PayloadInfo(emptyList(), 1.0, 1, "test", 1, 1.0)

            val result = flushRequest.sendRequest(payloadInfo)

            assertFalse(result)
            assertEquals(1, flushRequest.networkConsecutiveFailures)
        }

    @Test
    fun testUpdateDistinctId() {
        // Test the updateDistinctId method - it should execute without throwing an exception
        flushRequest.updateDistinctId("newDistinctId")

        // Verify that the distinctId was updated correctly
        assertEquals("newDistinctId", flushRequest.distinctId)
    }
}
