package com.mixpanel.android.sessionreplay

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mixpanel.android.sessionreplay.models.MPSessionReplayConfig
import com.mixpanel.android.sessionreplay.services.RemoteSettingsResult
import com.mixpanel.android.sessionreplay.services.RemoteSettingsService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionReplayCallbackTest {
    private lateinit var mockContext: Context
    private lateinit var mockRemoteSettingsService: RemoteSettingsService
    private lateinit var mockLifecycle: Lifecycle

    private val testToken = "testToken123"
    private val testDistinctId = "testUser123"
    private val testConfig = MPSessionReplayConfig(autoStartRecording = false)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        mockContext = mockk()
        mockRemoteSettingsService = mockk()
        mockLifecycle = mockk(relaxed = true)
        Dispatchers.setMain(testDispatcher)

        // Mock ProcessLifecycleOwner
        mockkObject(ProcessLifecycleOwner)
        every { ProcessLifecycleOwner.get().lifecycle } returns mockLifecycle

        // Clear any existing instance via reflection
        val instanceField = SessionReplayManager::class.java.getDeclaredField("instance")
        instanceField.isAccessible = true
        instanceField.set(SessionReplayManager, null)

        // Clear the initialization scope
        val scopeField = SessionReplayManager::class.java.getDeclaredField("initializationScope")
        scopeField.isAccessible = true
        scopeField.set(SessionReplayManager, null)

        // Set the IO dispatcher to use test dispatcher
        SessionReplayManager.ioDispatcher = testDispatcher
        // Set up the remote settings service factory to return our mock
        SessionReplayManager.remoteSettingsServiceFactory = { _, _, _, _ -> mockRemoteSettingsService }

        // Set up basic context mocking
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.packageName } returns "com.test.package"
        every { mockContext.getSharedPreferences(any(), any()) } returns mockk(relaxed = true)
        every { mockContext.registerReceiver(any(), any()) } returns null
        every { mockContext.getSystemService(any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        // Clear the instance again
        val instanceField = SessionReplayManager::class.java.getDeclaredField("instance")
        instanceField.isAccessible = true
        instanceField.set(SessionReplayManager, null)

        // Clear the initialization scope
        val scopeField = SessionReplayManager::class.java.getDeclaredField("initializationScope")
        scopeField.isAccessible = true
        scopeField.set(SessionReplayManager, null)

        SessionReplayManager.remoteSettingsServiceFactory = null
        SessionReplayManager.coroutineScopeFactory = null
        SessionReplayManager.ioDispatcher = Dispatchers.IO // Reset to default
        Dispatchers.resetMain()
        unmockkAll()
    }

    /**
     * Helper function to simulate app entering foreground and trigger deferred initialization
     */
    private fun simulateAppForeground() {
        val observer = slot<DefaultLifecycleObserver>()
        verify { mockLifecycle.addObserver(capture(observer)) }

        val mockOwner = mockk<LifecycleOwner>()
        observer.captured.onStart(mockOwner)
    }

    @Test
    fun testInitializeCallbackCalledWhenDisabled() = runTest {
        // Inject the current test scope into SessionReplayManager
        SessionReplayManager.coroutineScopeFactory = { this }
        // Setup settings to return false (disabled)
        coEvery { mockRemoteSettingsService.fetchRemoteSettings(testToken) } returns RemoteSettingsResult(isRecordingEnabled = false)

        var result: Result<MPSessionReplayInstance?>? = null
        var callbackCalled = false

        // Initialize SessionReplayManager
        SessionReplayManager.initialize(mockContext, testToken, testDistinctId, testConfig) { res ->
            result = res
            callbackCalled = true
        }

        // Simulate app entering foreground
        simulateAppForeground()
        // Advance until all coroutines complete
        advanceUntilIdle()

        // Verify disabled callback was called
        assertTrue("Callback was not called", callbackCalled)
        assertNotNull(result)
        assertTrue("Expected failure result", result?.isFailure == true)
        val error = result?.exceptionOrNull()
        assertTrue("Expected MPSessionReplayError.Disabled", error is MPSessionReplayError.Disabled)

        // Verify instance is null
        assertNull(SessionReplayManager.getInstance())
        // Verify settings were checked
        coVerify { mockRemoteSettingsService.fetchRemoteSettings(testToken) }
    }

    @Test
    fun testInitializeCallbackCalledOnError() = runTest {
        // Inject the current test scope into SessionReplayManager
        SessionReplayManager.coroutineScopeFactory = { this }
        // Setup settings to throw an exception
        coEvery { mockRemoteSettingsService.fetchRemoteSettings(testToken) } throws RuntimeException("Network error")

        var result: Result<MPSessionReplayInstance?>? = null
        var callbackCalled = false

        // Initialize SessionReplayManager
        SessionReplayManager.initialize(mockContext, testToken, testDistinctId, testConfig) { res ->
            result = res
            callbackCalled = true
        }

        // Simulate app entering foreground
        simulateAppForeground()
        // Advance until all coroutines complete
        advanceUntilIdle()

        // Verify error callback was called
        assertTrue("Callback was not called", callbackCalled)
        assertNotNull(result)
        assertTrue("Expected failure result", result?.isFailure == true)
        val error = result?.exceptionOrNull()
        assertTrue("Expected MPSessionReplayError.InitializationError", error is MPSessionReplayError.InitializationError)

        // Verify instance is null
        assertNull(SessionReplayManager.getInstance())
        // Verify settings were checked
        coVerify { mockRemoteSettingsService.fetchRemoteSettings(testToken) }
    }

    @Test
    fun testGetInstanceReturnsNullBeforeInitialization() {
        assertNull(SessionReplayManager.getInstance())
    }

    @Test
    fun testInitializeWithoutCallbackWhenDisabled() = runTest {
        // Inject the current test scope into SessionReplayManager
        SessionReplayManager.coroutineScopeFactory = { this }
        // Setup settings to return false (disabled)
        coEvery { mockRemoteSettingsService.fetchRemoteSettings(testToken) } returns RemoteSettingsResult(isRecordingEnabled = false)

        // Initialize SessionReplayManager without callback
        SessionReplayManager.initialize(mockContext, testToken, testDistinctId, testConfig)

        // Simulate app entering foreground
        simulateAppForeground()
        // Advance until all coroutines complete
        advanceUntilIdle()

        // Verify instance is null
        assertNull(SessionReplayManager.getInstance())
        // Verify settings were checked
        coVerify { mockRemoteSettingsService.fetchRemoteSettings(testToken) }
    }

    @Test
    fun testInitializeDoesNotMakeNetworkCallUntilForeground() = runTest {
        // Inject the current test scope into SessionReplayManager
        SessionReplayManager.coroutineScopeFactory = { this }
        // Setup settings to return true (enabled)
        coEvery { mockRemoteSettingsService.fetchRemoteSettings(testToken) } returns RemoteSettingsResult(isRecordingEnabled = true)

        // Initialize SessionReplayManager
        SessionReplayManager.initialize(mockContext, testToken, testDistinctId, testConfig)

        // Let the coroutine advance until it suspends waiting for foreground
        testScheduler.advanceUntilIdle()

        // Verify that lifecycle observer was registered
        verify { mockLifecycle.addObserver(any()) }
        // Settings should NOT be checked yet - deferred until app enters foreground
        coVerify(exactly = 0) { mockRemoteSettingsService.fetchRemoteSettings(any()) }
        // Instance should not be created yet
        assertNull(SessionReplayManager.getInstance())

        simulateAppForeground()

        // Verify settings were checked
        coVerify { mockRemoteSettingsService.fetchRemoteSettings(testToken) }
    }
}
