package com.mixpanel.android.sessionreplay

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.mixpanel.android.sessionreplay.network.APIRequest
import com.mixpanel.android.sessionreplay.network.Network
import com.mixpanel.android.sessionreplay.network.RequestMethod
import com.mixpanel.android.sessionreplay.network.SdkConfig
import com.mixpanel.android.sessionreplay.services.RemoteSettingsResult
import com.mixpanel.android.sessionreplay.services.RemoteSettingsService
import com.mixpanel.android.sessionreplay.utils.APIConstants
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteSettingsServiceTest {
    private lateinit var mockContext: Context
    private lateinit var mockNetwork: Network
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var remoteSettingsService: RemoteSettingsService
    private val testToken = "testToken123"
    private val version = APIConstants.currentLibVersion
    private val mpLib = APIConstants.currentMpLib

    @Before
    fun setUp() {
        mockContext = mockk()
        mockNetwork = mockk()
        mockSharedPreferences = mockk()
        mockEditor = mockk()

        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } returns "dGVzdFRva2VuMTIzOg=="

        every { mockContext.getSharedPreferences(any(), any()) } returns mockSharedPreferences
        every { mockSharedPreferences.edit() } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.remove(any()) } returns mockEditor
        every { mockEditor.apply() } just Runs

        remoteSettingsService = RemoteSettingsService(mockContext, mockNetwork, version, mpLib)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testCheckSettingsSuccess() = runTest {
        val responseJson = """{"recording": {"is_enabled": true}}"""
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.success(responseJson)

        val result = remoteSettingsService.fetchRemoteSettings(testToken)

        assertEquals(RemoteSettingsResult(isRecordingEnabled = true), result)
    }

    @Test
    fun testCheckSettingsEnabledClearsRecordingCache() = runTest {
        val responseJson = """{"recording": {"is_enabled": true}}"""
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.success(responseJson)

        remoteSettingsService.fetchRemoteSettings(testToken)

        // Verify cache is cleared when recording is enabled
        verify { mockEditor.remove("mp_sr_recording_${testToken}_enabled") }
        verify { mockEditor.remove("mp_sr_recording_${testToken}_timestamp") }
    }

    @Test
    fun testCheckSettingsWithSdkConfig() = runTest {
        val responseJson = """{"recording": {"is_enabled": true}, "sdk_config": {"config": {"record_sessions_percent": 25.5}}}"""
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.success(responseJson)

        val result = remoteSettingsService.fetchRemoteSettings(testToken)

        assertEquals(
            RemoteSettingsResult(
                isRecordingEnabled = true,
                sdkConfig = SdkConfig(recordSessionsPercent = 25.5),
                isFromCache = false
            ),
            result
        )

        // Verify sdk_config is cached as JSON
        verify { mockEditor.putString("mp_sr_recording_${testToken}_sdk_config", """{"record_sessions_percent":25.5}""") }
    }

    @Test
    fun testCheckSettingsDisabled() = runTest {
        val responseJson = """{"recording": {"is_enabled": false}}"""
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.success(responseJson)

        val result = remoteSettingsService.fetchRemoteSettings(testToken)

        assertEquals(RemoteSettingsResult(isRecordingEnabled = false), result)
        verify { mockEditor.putBoolean("mp_sr_recording_${testToken}_enabled", false) }
    }

    @Test
    fun testCheckSettingsApiFailureFallsBackToCache() = runTest {
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.failure(Exception("Network error"))
        every { mockSharedPreferences.contains("mp_sr_recording_${testToken}_enabled") } returns true
        every { mockSharedPreferences.getBoolean("mp_sr_recording_${testToken}_enabled", true) } returns false

        val result = remoteSettingsService.fetchRemoteSettings(testToken)

        assertEquals(RemoteSettingsResult(isRecordingEnabled = false, isFromCache = true), result)
    }

    @Test
    fun testCheckSettingsApiFailureNoCacheDefaultsToEnabled() = runTest {
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.failure(Exception("Network error"))
        every { mockSharedPreferences.contains(any()) } returns false

        val result = remoteSettingsService.fetchRemoteSettings(testToken)

        assertEquals(RemoteSettingsResult(isRecordingEnabled = true, isFromCache = true), result)
    }

    @Test
    fun testCheckSettingsIncludesSdkConfigQueryParam() = runTest {
        val responseJson = """{"recording": {"is_enabled": true}}"""
        val capturedRequest = slot<APIRequest>()
        coEvery { mockNetwork.performAPIRequestWithResponse(capture(capturedRequest)) } returns Result.success(responseJson)

        remoteSettingsService.fetchRemoteSettings(testToken)

        val expectedRequest = APIRequest(
            endPoint = "https://api.mixpanel.com/settings",
            method = RequestMethod.GET,
            queryItems = listOf(
                "recording" to "1",
                "sdk_config" to "1",
                "\$os" to "Android",
                "mp_lib" to mpLib,
                "\$lib_version" to version
            ),
            headers = mapOf("Authorization" to "Basic dGVzdFRva2VuMTIzOg=="),
            timeout = 5000L
        )
        assertEquals(expectedRequest, capturedRequest.captured)
    }

    @Test
    fun testCheckSettingsCachesSdkConfigAsJsonOnSuccess() = runTest {
        val responseJson = """{"recording": {"is_enabled": true}, "sdk_config": {"config": {"record_sessions_percent": 50.0}}}"""
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.success(responseJson)

        remoteSettingsService.fetchRemoteSettings(testToken)

        verify { mockEditor.putString("mp_sr_recording_${testToken}_sdk_config", """{"record_sessions_percent":50.0}""") }
    }

    @Test
    fun testCheckSettingsReturnsCachedSdkConfigOnFailure() = runTest {
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.failure(Exception("Network error"))
        every { mockSharedPreferences.getString("mp_sr_recording_${testToken}_sdk_config", null) } returns
            """{"record_sessions_percent":75.0}"""

        val result = remoteSettingsService.fetchRemoteSettings(testToken)

        assertEquals(
            RemoteSettingsResult(
                isRecordingEnabled = true,
                sdkConfig = SdkConfig(recordSessionsPercent = 75.0),
                isFromCache = true
            ),
            result
        )
    }

    // --- Wireframe Kill Switch Tests ---

    @Test
    fun testCheckSettingsIncludesWireframeQueryParamWhenRequested() = runTest {
        val responseJson = """{"recording": {"is_enabled": true}, "wireframe": {"is_enabled": true}}"""
        val capturedRequest = slot<APIRequest>()
        coEvery { mockNetwork.performAPIRequestWithResponse(capture(capturedRequest)) } returns Result.success(responseJson)

        remoteSettingsService.fetchRemoteSettings(testToken, wireframesRequested = true)

        val expectedRequest = APIRequest(
            endPoint = "https://api.mixpanel.com/settings",
            method = RequestMethod.GET,
            queryItems = listOf(
                "recording" to "1",
                "sdk_config" to "1",
                "wireframe" to "1",
                "\$os" to "Android",
                "mp_lib" to mpLib,
                "\$lib_version" to version
            ),
            headers = mapOf("Authorization" to "Basic dGVzdFRva2VuMTIzOg=="),
            timeout = 5000L
        )
        assertEquals(expectedRequest, capturedRequest.captured)
    }

    @Test
    fun testCheckSettingsWireframeDisabled() = runTest {
        val responseJson = """
            {"recording": {"is_enabled": true},
             "wireframe": {"is_enabled": false, "error": "organization is blocked from wireframe capture."}}
        """.trimIndent()
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.success(responseJson)

        val result = remoteSettingsService.fetchRemoteSettings(testToken, wireframesRequested = true)

        // Replay keeps recording; only wireframes are killed
        assertEquals(
            RemoteSettingsResult(isRecordingEnabled = true, isWireframeEnabled = false),
            result
        )
        verify { mockEditor.putBoolean("mp_sr_wireframe_${testToken}_enabled", false) }
    }

    @Test
    fun testCheckSettingsWireframeEnabledClearsWireframeCache() = runTest {
        val responseJson = """{"recording": {"is_enabled": true}, "wireframe": {"is_enabled": true}}"""
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.success(responseJson)

        val result = remoteSettingsService.fetchRemoteSettings(testToken, wireframesRequested = true)

        assertEquals(RemoteSettingsResult(isRecordingEnabled = true), result)
        verify { mockEditor.remove("mp_sr_wireframe_${testToken}_enabled") }
    }

    @Test
    fun testCheckSettingsMissingWireframeFieldLeavesWireframesEnabled() = runTest {
        // The SDK never asked for the switch, so the server does not send it
        val responseJson = """{"recording": {"is_enabled": true}}"""
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.success(responseJson)

        val result = remoteSettingsService.fetchRemoteSettings(testToken)

        assertTrue(result.isWireframeEnabled)
        verify(exactly = 0) { mockEditor.putBoolean("mp_sr_wireframe_${testToken}_enabled", any()) }
        verify(exactly = 0) { mockEditor.remove("mp_sr_wireframe_${testToken}_enabled") }
    }

    @Test
    fun testCheckSettingsBothSwitchesDisabled() = runTest {
        val responseJson = """{"recording": {"is_enabled": false}, "wireframe": {"is_enabled": false}}"""
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.success(responseJson)

        val result = remoteSettingsService.fetchRemoteSettings(testToken, wireframesRequested = true)

        assertEquals(
            RemoteSettingsResult(isRecordingEnabled = false, isWireframeEnabled = false),
            result
        )
    }

    @Test
    fun testCheckSettingsApiFailureFallsBackToCachedWireframeState() = runTest {
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.failure(Exception("Network error"))
        every { mockSharedPreferences.contains("mp_sr_recording_${testToken}_enabled") } returns false
        every { mockSharedPreferences.contains("mp_sr_wireframe_${testToken}_enabled") } returns true
        every { mockSharedPreferences.getBoolean("mp_sr_wireframe_${testToken}_enabled", true) } returns false
        every { mockSharedPreferences.getString("mp_sr_recording_${testToken}_sdk_config", null) } returns null

        val result = remoteSettingsService.fetchRemoteSettings(testToken, wireframesRequested = true)

        // The kill switch survives a failed fetch
        assertEquals(
            RemoteSettingsResult(isRecordingEnabled = true, isFromCache = true, isWireframeEnabled = false),
            result
        )
    }

    // --- API Response Scenario Tests (from REMOTE_CONFIG_MODES.md) ---

    @Test
    fun testCheckSettingsWithEmptySdkConfigObject() = runTest {
        // sdk_config: { config: {} } - empty config object
        val responseJson = """{"recording": {"is_enabled": true}, "sdk_config": {"config": {}}}"""
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.success(responseJson)

        val result = remoteSettingsService.fetchRemoteSettings(testToken)

        assertEquals(
            RemoteSettingsResult(
                isRecordingEnabled = true,
                sdkConfig = SdkConfig(recordSessionsPercent = null),
                isFromCache = false
            ),
            result
        )
    }

    @Test
    fun testCheckSettingsWithNullRecordSessionsPercent() = runTest {
        // config: { record_sessions_percent: null }
        val responseJson = """{"recording": {"is_enabled": true}, "sdk_config": {"config": {"record_sessions_percent": null}}}"""
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.success(responseJson)

        val result = remoteSettingsService.fetchRemoteSettings(testToken)

        assertEquals(
            RemoteSettingsResult(
                isRecordingEnabled = true,
                sdkConfig = SdkConfig(recordSessionsPercent = null),
                isFromCache = false
            ),
            result
        )
    }

    @Test
    fun testCheckSettingsClearsSdkConfigCacheWhenSdkConfigMissing() = runTest {
        // Only recording, no sdk_config at all
        val responseJson = """{"recording": {"is_enabled": true}}"""
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.success(responseJson)

        remoteSettingsService.fetchRemoteSettings(testToken)

        // Verify sdk_config cache is cleared when sdk_config is missing
        verify { mockEditor.remove("mp_sr_recording_${testToken}_sdk_config") }
    }

    @Test
    fun testCheckSettingsWithSdkConfigButNullConfig() = runTest {
        // sdk_config exists but config is null: sdk_config: { config: null }
        val responseJson = """{"recording": {"is_enabled": true}, "sdk_config": {"config": null}}"""
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.success(responseJson)

        val result = remoteSettingsService.fetchRemoteSettings(testToken)

        // Should return null sdkConfig and clear cache
        assertEquals(
            RemoteSettingsResult(
                isRecordingEnabled = true,
                sdkConfig = null,
                isFromCache = false
            ),
            result
        )
        verify { mockEditor.remove("mp_sr_recording_${testToken}_sdk_config") }
    }

    @Test
    fun testCheckSettingsWithZeroPercent() = runTest {
        // 0 is a valid boundary value
        val responseJson = """{"recording": {"is_enabled": true}, "sdk_config": {"config": {"record_sessions_percent": 0}}}"""
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.success(responseJson)

        val result = remoteSettingsService.fetchRemoteSettings(testToken)

        assertEquals(
            RemoteSettingsResult(
                isRecordingEnabled = true,
                sdkConfig = SdkConfig(recordSessionsPercent = 0.0),
                isFromCache = false
            ),
            result
        )
    }

    @Test
    fun testCheckSettingsWithHundredPercent() = runTest {
        // 100 is a valid boundary value
        val responseJson = """{"recording": {"is_enabled": true}, "sdk_config": {"config": {"record_sessions_percent": 100}}}"""
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.success(responseJson)

        val result = remoteSettingsService.fetchRemoteSettings(testToken)

        assertEquals(
            RemoteSettingsResult(
                isRecordingEnabled = true,
                sdkConfig = SdkConfig(recordSessionsPercent = 100.0),
                isFromCache = false
            ),
            result
        )
    }

    @Test
    fun testCheckSettingsWithSdkConfigError() = runTest {
        // sdk_config has error field
        val responseJson = """{"recording": {"is_enabled": true}, "sdk_config": {"error": "config not available"}}"""
        coEvery { mockNetwork.performAPIRequestWithResponse(any()) } returns Result.success(responseJson)

        val result = remoteSettingsService.fetchRemoteSettings(testToken)

        // Should return null sdkConfig when there's an error
        assertEquals(
            RemoteSettingsResult(
                isRecordingEnabled = true,
                sdkConfig = null,
                isFromCache = false
            ),
            result
        )
    }
}
