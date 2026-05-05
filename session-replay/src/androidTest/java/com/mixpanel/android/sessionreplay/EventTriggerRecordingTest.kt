package com.mixpanel.android.sessionreplay

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mixpanel.android.sessionreplay.models.MPSessionReplayConfig
import com.mixpanel.android.sessionreplay.models.RecordingEventTrigger
import com.mixpanel.android.sessionreplay.models.RemoteSettingsMode
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EventTriggerRecordingTest {

    private lateinit var scenario: ActivityScenario<EventTriggerTestActivity>
    private var sessionReplayInstance: MPSessionReplayInstance? = null

    companion object {
        private const val TEST_TOKEN = "test_token_event_trigger"
        private const val TEST_DISTINCT_ID = "test_user_event_trigger"
        private const val INIT_TIMEOUT_SECONDS = 10L
        private const val RECORDING_START_TIMEOUT_MS = 3000L
        private const val POLL_INTERVAL_MS = 100L
    }

    @Before
    fun setup() {
        // Stop any existing recording to ensure clean state
        MPSessionReplay.getInstance()?.stopRecording()
    }

    @After
    fun tearDown() {
        // Cleanup
        MPSessionReplay.getInstance()?.stopRecording()
        SessionReplayTestHelper.reset()
        if (::scenario.isInitialized) {
            scenario.close()
        }
    }

    @Test
    fun testEventTriggerStartsRecording() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Configure mock event triggers via remote settings
        val eventTriggers = mapOf(
            "Test Event Trigger Clicked" to RecordingEventTrigger(percentage = 100.0)
        )
        SessionReplayTestHelper.configureMockEventTriggers(eventTriggers)

        initializeAndLaunch(context)

        // Verify not recording before clicking button
        assertFalse(
            "Should not be recording before event trigger",
            sessionReplayInstance?.isRecording() ?: true
        )

        // Click the event trigger test button
        scenario.onActivity { activity ->
            activity.eventTriggerTestButton.performClick()
        }

        // Wait for event processing and recording to start
        val recordingStarted = waitForRecordingToStart(RECORDING_START_TIMEOUT_MS)

        assertTrue(
            "Recording should start after event trigger",
            recordingStarted
        )
    }

    @Test
    fun testEventTriggerWithPropertyFilterStartsRecording() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Configure mock event trigger with property filter: age > 18
        // JSONLogic: {">": [{"var": "age"}, 18]}
        val eventTriggers = mapOf(
            "Product View" to RecordingEventTrigger(
                percentage = 100.0,
                propertyFilters = Json.parseToJsonElement(
                    """{">":[{"var":"age"},18]}"""
                )
            )
        )
        SessionReplayTestHelper.configureMockEventTriggers(eventTriggers)

        initializeAndLaunch(context)

        // Verify not recording before clicking button
        assertFalse(
            "Should not be recording before event trigger",
            sessionReplayInstance?.isRecording() ?: true
        )

        // Click the Product View test button (fires event with age=20, which is > 18)
        scenario.onActivity { activity ->
            activity.productViewTestButton.performClick()
        }

        // Wait for event processing and recording to start
        val recordingStarted = waitForRecordingToStart(RECORDING_START_TIMEOUT_MS)

        assertTrue(
            "Recording should start when event property filter matches (age=20 > 18)",
            recordingStarted
        )
    }

    @Test
    fun testEventTriggerWithPropertyFilterDoesNotStartRecordingWhenFilterFails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Configure mock event trigger with property filter: age > 18
        // JSONLogic: {">": [{"var": "age"}, 18]}
        val eventTriggers = mapOf(
            "Product View" to RecordingEventTrigger(
                percentage = 100.0,
                propertyFilters = Json.parseToJsonElement(
                    """{">":[{"var":"age"},18]}"""
                )
            )
        )
        SessionReplayTestHelper.configureMockEventTriggers(eventTriggers)

        initializeAndLaunch(context)

        // Verify not recording before clicking button
        assertFalse(
            "Should not be recording before event trigger",
            sessionReplayInstance?.isRecording() ?: true
        )

        // Click the Product View fail test button (fires event with age=15, which is < 18)
        scenario.onActivity { activity ->
            activity.productViewFailTestButton.performClick()
        }

        // Wait to ensure event has time to process
        Thread.sleep(RECORDING_START_TIMEOUT_MS)

        // Verify recording did NOT start since property filter failed (15 is not > 18)
        assertFalse(
            "Recording should NOT start when event property filter fails (age=15 is not > 18)",
            sessionReplayInstance?.isRecording() ?: true
        )
    }

    @Test
    fun testEventTriggersEnabledByDefault() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Configure mock event triggers
        val eventTriggers = mapOf(
            "Test Event Trigger Clicked" to RecordingEventTrigger(percentage = 100.0)
        )
        SessionReplayTestHelper.configureMockEventTriggers(eventTriggers)

        initializeAndLaunch(context)

        // Verify event triggers are enabled by default
        assertTrue(
            "Event triggers should be enabled by default after initialization",
            sessionReplayInstance?.isEventTriggersEnabled() ?: false
        )
    }

    @Test
    fun testDisableEventTriggersPreventsRecording() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Configure mock event triggers
        val eventTriggers = mapOf(
            "Test Event Trigger Clicked" to RecordingEventTrigger(percentage = 100.0)
        )
        SessionReplayTestHelper.configureMockEventTriggers(eventTriggers)

        initializeAndLaunch(context)

        // Disable event triggers
        sessionReplayInstance?.disableEventTriggers()

        // Verify event triggers are disabled
        assertFalse(
            "Event triggers should be disabled after calling disableEventTriggers()",
            sessionReplayInstance?.isEventTriggersEnabled() ?: true
        )

        // Click the event trigger test button
        scenario.onActivity { activity ->
            activity.eventTriggerTestButton.performClick()
        }

        // Wait to ensure event has time to process
        Thread.sleep(RECORDING_START_TIMEOUT_MS)

        // Verify recording did NOT start because event triggers are disabled
        assertFalse(
            "Recording should NOT start when event triggers are disabled",
            sessionReplayInstance?.isRecording() ?: true
        )
    }

    @Test
    fun testEnableEventTriggersAfterDisable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Configure mock event triggers
        val eventTriggers = mapOf(
            "Test Event Trigger Clicked" to RecordingEventTrigger(percentage = 100.0)
        )
        SessionReplayTestHelper.configureMockEventTriggers(eventTriggers)

        initializeAndLaunch(context)

        // Disable then re-enable event triggers
        sessionReplayInstance?.disableEventTriggers()
        sessionReplayInstance?.enableEventTriggers()

        // Verify event triggers are enabled
        assertTrue(
            "Event triggers should be enabled after calling enableEventTriggers()",
            sessionReplayInstance?.isEventTriggersEnabled() ?: false
        )

        // Click the event trigger test button
        scenario.onActivity { activity ->
            activity.eventTriggerTestButton.performClick()
        }

        // Wait for event processing and recording to start
        val recordingStarted = waitForRecordingToStart(RECORDING_START_TIMEOUT_MS)

        assertTrue(
            "Recording should start after re-enabling event triggers",
            recordingStarted
        )
    }

    @Test
    fun testDisableEventTriggersDoesNotStopActiveRecording() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Configure mock event triggers
        val eventTriggers = mapOf(
            "Test Event Trigger Clicked" to RecordingEventTrigger(percentage = 100.0)
        )
        SessionReplayTestHelper.configureMockEventTriggers(eventTriggers)

        initializeAndLaunch(context)

        // Click the event trigger test button to start recording
        scenario.onActivity { activity ->
            activity.eventTriggerTestButton.performClick()
        }

        // Wait for recording to start
        val recordingStarted = waitForRecordingToStart(RECORDING_START_TIMEOUT_MS)
        assertTrue("Recording should start from event trigger", recordingStarted)

        // Disable event triggers while recording is active
        sessionReplayInstance?.disableEventTriggers()

        // Verify recording is still active
        assertTrue(
            "Disabling event triggers should NOT stop an already-active recording",
            sessionReplayInstance?.isRecording() ?: false
        )
    }

    /**
     * Helper to initialize SDK and launch activity.
     */
    private fun initializeAndLaunch(context: android.content.Context) {
        val config = MPSessionReplayConfig(
            autoStartRecording = false,
            recordingSessionsPercent = 0.0,
            enableLogging = true,
            remoteSettingsMode = RemoteSettingsMode.FALLBACK
        )

        val initLatch = CountDownLatch(1)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MPSessionReplay.initialize(
                appContext = context.applicationContext,
                token = TEST_TOKEN,
                distinctId = TEST_DISTINCT_ID,
                config = config
            ) { result ->
                result.fold(
                    onSuccess = { instance ->
                        sessionReplayInstance = instance
                        initLatch.countDown()
                    },
                    onFailure = {
                        initLatch.countDown()
                    }
                )
            }
        }

        // Launch activity to bring app to foreground
        scenario = ActivityScenario.launch(EventTriggerTestActivity::class.java)

        // Wait for activity to be in RESUMED state (fully ready)
        scenario.moveToState(Lifecycle.State.RESUMED)

        // Wait for SDK initialization
        assertTrue(
            "SDK initialization should complete",
            initLatch.await(INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        )

        assertNotNull("Session Replay instance should be initialized", sessionReplayInstance)
    }

    /**
     * Polls isRecording() until it returns true or timeout is reached.
     */
    private fun waitForRecordingToStart(timeoutMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (sessionReplayInstance?.isRecording() == true) {
                return true
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return sessionReplayInstance?.isRecording() == true
    }
}
