package com.mixpanel.android.sessionreplay

import android.content.Context
import android.net.ConnectivityManager
import com.mixpanel.android.sessionreplay.models.MPSessionReplayConfig
import com.mixpanel.android.sessionreplay.services.FlushService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for [MPSessionReplayInstance.getSessionReplayUrl].
 * Uses Robolectric to support Android's Uri.Builder.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GetSessionReplayUrlTest {
    private lateinit var context: Context
    private lateinit var testScope: TestScope

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        testScope = TestScope(StandardTestDispatcher())

        mockkObject(SessionReplayManager)
        every { SessionReplayManager.cleanupEventCollection() } just Runs
    }

    @Test
    fun `getSessionReplayUrl returns null when not recording`() {
        val mockFlushService = mockk<FlushService>()
        every { mockFlushService.replayId } returns "test-replay-id"
        every { mockFlushService.getDistinctId() } returns "test-distinct-id"

        val instance = MPSessionReplayInstance(
            context,
            "test-token",
            "test-distinct-id",
            config = MPSessionReplayConfig(autoStartRecording = false),
            lifecycleScope = testScope,
            flushService = mockFlushService
        )

        assertNull(instance.getSessionReplayUrl())
    }

    @Test
    fun `getSessionReplayUrl returns correct URL when recording`() {
        val mockFlushService = mockk<FlushService>()
        every { mockFlushService.start() } just Runs
        every { mockFlushService.replayId } returns "test-replay-id"
        every { mockFlushService.getDistinctId() } returns "test-distinct-id"

        val mockSessionReplaySender = mockk<SessionReplaySender>(relaxed = true)

        val instance = MPSessionReplayInstance(
            context,
            "test-token",
            "test-distinct-id",
            config = MPSessionReplayConfig(autoStartRecording = false),
            sessionReplaySender = mockSessionReplaySender,
            lifecycleScope = testScope,
            flushService = mockFlushService
        )

        instance.startRecording()

        val url = instance.getSessionReplayUrl()
        assertEquals(
            "https://mixpanel.com/projects/replay-redirect?replay_id=test-replay-id&distinct_id=test-distinct-id&token=test-token",
            url
        )
    }

    @Test
    fun `getSessionReplayUrl URL encodes distinctId`() {
        val mockFlushService = mockk<FlushService>()
        every { mockFlushService.start() } just Runs
        every { mockFlushService.replayId } returns "550e8400-e29b-41d4-a716-446655440000"
        every { mockFlushService.getDistinctId() } returns "user@example.com"

        val mockSessionReplaySender = mockk<SessionReplaySender>(relaxed = true)

        val instance = MPSessionReplayInstance(
            context,
            "abc123def456",
            "user@example.com",
            config = MPSessionReplayConfig(autoStartRecording = false),
            sessionReplaySender = mockSessionReplaySender,
            lifecycleScope = testScope,
            flushService = mockFlushService
        )

        instance.startRecording()

        val url = instance.getSessionReplayUrl()
        assertEquals(
            "https://mixpanel.com/projects/replay-redirect?replay_id=550e8400-e29b-41d4-a716-446655440000&distinct_id=user%40example.com&token=abc123def456",
            url
        )
    }
}
