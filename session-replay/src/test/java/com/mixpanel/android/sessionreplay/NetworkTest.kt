package com.mixpanel.android.sessionreplay

import com.mixpanel.android.sessionreplay.network.APIRequest
import com.mixpanel.android.sessionreplay.network.Network
import com.mixpanel.android.sessionreplay.network.RequestMethod
import com.mixpanel.android.sessionreplay.utils.EndPoints
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.MalformedURLException

// Mock extension for testing the new method
abstract class NetworkWithResponse : Network() {
    abstract override suspend fun performAPIRequestWithResponse(apiRequest: APIRequest): Result<String>
}

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkTests {
    private lateinit var mockNetwork: NetworkWithResponse

    @Before
    fun setUp() {
        mockNetwork = mockk()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testPerformAPIRequestSuccess() =
        runTest {
            val apiRequest =
                APIRequest(
                    endPoint = EndPoints.record(EndPoints.DEFAULT_BASE_URL),
                    method = RequestMethod.GET,
                    requestBody = null,
                    queryItems = null,
                    headers = emptyMap()
                )

            coEvery { mockNetwork.performAPIRequest(apiRequest) } returns Result.success(Unit)

            val result = mockNetwork.performAPIRequest(apiRequest)

            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }

    @Test
    fun testPerformAPIRequestFailureWithError() =
        runTest {
            val expectedErrorMessage = "Test error"

            val apiRequest =
                APIRequest(
                    endPoint = EndPoints.record(EndPoints.DEFAULT_BASE_URL),
                    method = RequestMethod.GET,
                    requestBody = null,
                    queryItems = null,
                    headers = emptyMap()
                )

            coEvery { mockNetwork.performAPIRequest(apiRequest) } returns Result.failure(Exception(expectedErrorMessage))

            val result = mockNetwork.performAPIRequest(apiRequest)

            assertFalse(result.isSuccess)
            val exception = result.exceptionOrNull()
            assertNotNull(exception)
            assertEquals(expectedErrorMessage, exception?.message)
        }

    @Test
    fun testPerformAPIRequestInvalidResponse() =
        runTest {
            val apiRequest =
                APIRequest(
                    endPoint = EndPoints.record(EndPoints.DEFAULT_BASE_URL),
                    method = RequestMethod.GET,
                    requestBody = null,
                    queryItems = null,
                    headers = emptyMap()
                )

            // Mock the network behavior to return a 500 status code or a specific error message
            coEvery { mockNetwork.performAPIRequest(apiRequest) } returns Result.failure(Exception("Error: 500 - Internal Server Error"))

            val result = mockNetwork.performAPIRequest(apiRequest)

            assertFalse(result.isSuccess)
            val exception = result.exceptionOrNull()
            assertNotNull(exception)
            assertEquals("Error: 500 - Internal Server Error", exception?.message)
        }

    @Test
    fun testPerformAPIRequestInvalidURL() =
        runTest {
            val apiRequest =
                APIRequest(
                    endPoint = "invalid-url",
                    method = RequestMethod.GET,
                    requestBody = null,
                    queryItems = null,
                    headers = emptyMap()
                )

            coEvery { mockNetwork.performAPIRequest(apiRequest) } returns
                Result.failure(
                    MalformedURLException("no protocol: invalid-url")
                )

            val result = mockNetwork.performAPIRequest(apiRequest)

            assertFalse(result.isSuccess)
            val exception = result.exceptionOrNull()
            assertNotNull(exception)
            assertEquals("no protocol: invalid-url", exception?.message)
        }

    @Test
    fun testPerformAPIRequestWithResponseSuccess() =
        runTest {
            val expectedResponse = """{"recording": {"is_enabled": true}}"""
            val apiRequest =
                APIRequest(
                    endPoint = "https://api.mixpanel.com/settings",
                    method = RequestMethod.GET,
                    requestBody = null,
                    queryItems = listOf("recording" to "1", "mp_lib" to "android-sr", "\$lib_version" to "0.1.1-SNAPSHOT"),
                    headers = mapOf("Authorization" to "Basic dGVzdFRva2VuOg==")
                )

            coEvery { mockNetwork.performAPIRequestWithResponse(apiRequest) } returns Result.success(expectedResponse)

            val result = mockNetwork.performAPIRequestWithResponse(apiRequest)

            assertTrue(result.isSuccess)
            assertEquals(expectedResponse, result.getOrNull())
        }

    @Test
    fun testPerformAPIRequestWithResponseErrorBody() =
        runTest {
            val errorResponse = """{"error": "Invalid token"}"""
            val apiRequest =
                APIRequest(
                    endPoint = "https://api.mixpanel.com/settings",
                    method = RequestMethod.GET,
                    requestBody = null,
                    queryItems = listOf("recording" to "1"),
                    headers = mapOf("Authorization" to "Basic aW52YWxpZDo=")
                )

            coEvery { mockNetwork.performAPIRequestWithResponse(apiRequest) } returns
                Result.failure(Exception("Error: 401 - $errorResponse"))

            val result = mockNetwork.performAPIRequestWithResponse(apiRequest)

            assertFalse(result.isSuccess)
            val exception = result.exceptionOrNull()
            assertNotNull(exception)
            assertTrue(exception?.message?.contains(errorResponse) == true)
        }

    @Test
    fun testPerformAPIRequestWithResponseNetworkFailure() =
        runTest {
            val apiRequest =
                APIRequest(
                    endPoint = "https://api.mixpanel.com/settings",
                    method = RequestMethod.GET,
                    requestBody = null,
                    queryItems = listOf("recording" to "1"),
                    headers = mapOf("Authorization" to "Basic dGVzdFRva2VuOg==")
                )

            coEvery { mockNetwork.performAPIRequestWithResponse(apiRequest) } returns
                Result.failure(Exception("Network error: Unable to resolve host"))

            val result = mockNetwork.performAPIRequestWithResponse(apiRequest)

            assertFalse(result.isSuccess)
            val exception = result.exceptionOrNull()
            assertNotNull(exception)
            assertEquals("Network error: Unable to resolve host", exception?.message)
        }

    @Test
    fun testPerformAPIRequestWithResponse404NotFound() =
        runTest {
            val apiRequest =
                APIRequest(
                    endPoint = "https://api.mixpanel.com/settings",
                    method = RequestMethod.GET,
                    requestBody = null,
                    queryItems = listOf("recording" to "1"),
                    headers = mapOf("Authorization" to "Basic dGVzdFRva2VuOg==")
                )

            coEvery { mockNetwork.performAPIRequestWithResponse(apiRequest) } returns
                Result.failure(Exception("Error: 404 - Not Found"))

            val result = mockNetwork.performAPIRequestWithResponse(apiRequest)

            assertFalse(result.isSuccess)
            val exception = result.exceptionOrNull()
            assertNotNull(exception)
            assertEquals("Error: 404 - Not Found", exception?.message)
        }
}
