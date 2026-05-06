package com.mixpanel.android.sessionreplay

import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.mixpanel.android.sessionreplay.models.MPSessionReplayConfig
import com.mixpanel.android.sessionreplay.sensitive_views.AutoMaskedView
import com.mixpanel.android.sessionreplay.sensitive_views.SensitiveViewManager
import com.mixpanel.android.sessionreplay.services.FlushService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MPSessionReplayInstanceTest {
    private lateinit var mockContext: Context
    private lateinit var mockApplicationContext: Context
    private lateinit var mockConnectivityManager: ConnectivityManager
    private lateinit var mockLifecycleObserver: LifecycleObserver
    private lateinit var mockResources: Resources
    private lateinit var testScope: TestScope

    @Before
    fun setUp() {
        // Initialize mocks
        mockContext = mockk()
        mockApplicationContext = mockk()
        mockConnectivityManager = mockk()
        mockLifecycleObserver = mockk()
        mockResources = mockk(relaxed = true)

        // Mock Looper.getMainLooper()
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk()

        // Create test scope
        testScope = TestScope(StandardTestDispatcher())

        // Mock ProcessLifecycleOwner
        mockkObject(ProcessLifecycleOwner)
        val mockLifecycle = mockk<Lifecycle>()

        every { ProcessLifecycleOwner.get().lifecycle } returns mockLifecycle
        every { mockLifecycle.addObserver(any()) } just Runs
        every { mockLifecycle.removeObserver(any()) } just Runs

        // Mock methods on mockContext
        every { mockContext.applicationContext } returns mockApplicationContext
        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockConnectivityManager
        every { mockContext.getResources() } returns mockResources

        // Mock methods on mockApplicationContext
        every { mockApplicationContext.getResources() } returns mockResources
    }

    @Test
    fun testInitializationWithDefaultConfig() {
        val defaultConfig = MPSessionReplayConfig(autoStartRecording = false)
        val instance =
            MPSessionReplayInstance(
                mockContext,
                "token",
                "distinctId",
                defaultConfig,
                lifecycleScope = testScope
            )

        // Assertions
        assertEquals(instance.flushService.wifiOnly, defaultConfig.wifiOnly)
        assertTrue(SensitiveViewManager.autoMaskedViews.contains(AutoMaskedView.Text))
        assertTrue(SensitiveViewManager.autoMaskedViews.contains(AutoMaskedView.Image))
        assertTrue(SensitiveViewManager.autoMaskedViews.contains(AutoMaskedView.Web))
    }

    @Test
    fun testInitializationWithCustomConfig() {
        val customConfig =
            MPSessionReplayConfig(
                autoStartRecording = false,
                wifiOnly = false,
                autoMaskedViews = mutableSetOf()
            )
        val instance =
            MPSessionReplayInstance(
                mockContext,
                "token",
                "distinctId",
                customConfig,
                lifecycleScope = testScope
            )

        // Assertions
        assertEquals(instance.flushService.wifiOnly, customConfig.wifiOnly)
        assertFalse(SensitiveViewManager.autoMaskedViews.contains(AutoMaskedView.Text))
        assertFalse(SensitiveViewManager.autoMaskedViews.contains(AutoMaskedView.Image))
        assertFalse(SensitiveViewManager.autoMaskedViews.contains(AutoMaskedView.Web))
    }

    @Test
    fun `startRecording with no arguments`() {
        // recordSessionsPercent is not provided, so it defaults to 100.0.

        val mockFlushService = mockk<FlushService>()
        every { mockFlushService.start() } just Runs
        every { mockFlushService.replayId } returns "mockReplayId"

        val mockSessionReplaySender = mockk<SessionReplaySender>(relaxed = true)
        every { mockSessionReplaySender.registerSessionReplay(mockContext) } just Runs

        val instance =
            MPSessionReplayInstance(
                mockContext,
                "token",
                "distinctId",
                config = MPSessionReplayConfig(autoStartRecording = false),
                sessionReplaySender = mockSessionReplaySender,
                lifecycleScope = testScope,
                flushService = mockFlushService
            )
        instance.startRecording()

        // verify that flushService starts (which is a side effect of startRecording)
        verify { mockFlushService.start() } // Ensure flushService.start() is called
    }

    @Test
    fun `startRecording with 0 percent`() {
        val mockFlushService = mockk<FlushService>()
        every { mockFlushService.start() } just Runs
        every { mockFlushService.replayId } returns "mockReplayId"

        val mockSessionReplaySender = mockk<SessionReplaySender>(relaxed = true)
        every { mockSessionReplaySender.registerSessionReplay(mockContext) } just Runs

        val instance =
            MPSessionReplayInstance(
                mockContext,
                "token",
                "distinctId",
                config = MPSessionReplayConfig(autoStartRecording = false),
                sessionReplaySender = mockSessionReplaySender,
                lifecycleScope = testScope,
                flushService = mockFlushService
            )
        instance.startRecording(0.0)

        // verify that he flushService does not start
        verify(exactly = 0) { mockFlushService.start() } // Ensure flushService.start() is NOT called
    }

    @Test
    fun testIdentifyUpdatesDistinctId() {
        // Create a mock FlushService
        val mockFlushService = mockk<FlushService>()

        // Track the distinctId state
        var currentDistinctId = "originalDistinctId"

        // Mock the methods
        every { mockFlushService.updateDistinctId(any()) } answers {
            currentDistinctId = firstArg<String>()
        }
        every { mockFlushService.getDistinctId() } answers { currentDistinctId }
        every { mockFlushService.replayId } returns "test-replay-id"

        // Create instance with mocked FlushService and lifecycleScope
        val instance =
            MPSessionReplayInstance(
                mockContext,
                "token",
                "originalDistinctId",
                MPSessionReplayConfig(),
                flushService = mockFlushService,
                lifecycleScope = testScope
            )

        // Test the identify method
        instance.identify("newDistinctId")

        // Verify that updateDistinctId was called
        verify { mockFlushService.updateDistinctId("newDistinctId") }

        // Verify that the distinctId was updated
        assertEquals("newDistinctId", instance.getDistinctId())
    }
}
