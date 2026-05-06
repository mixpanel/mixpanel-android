package com.mixpanel.android.sessionreplay

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mixpanel.android.sessionreplay.models.MPSessionReplayConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MPSessionReplayTest {

    private lateinit var context: Context
    private val testToken = "test_token_123"
    private val testDistinctId = "test_user_123"

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun initializeDoesNotReturnWithoutForeground() {
        // Test 1: MPSessionReplayConfig creation with all parameters
        val config = MPSessionReplayConfig(
            autoStartRecording = false,
            recordingSessionsPercent = 0.0,
            enableLogging = true
        )

        val initLatch = CountDownLatch(1)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MPSessionReplay.initialize(
                appContext = context,
                token = testToken,
                distinctId = testDistinctId,
                config = config
            ) {
                initLatch.countDown()
            }
        }

        assertFalse(
            "Initialization callback should not complete",
            initLatch.await(3, TimeUnit.SECONDS)
        )
    }

    @Test
    fun initializeSucceedsOnceForegrounded() {
        // Test 1: MPSessionReplayConfig creation with all parameters
        val config = MPSessionReplayConfig(
            autoStartRecording = false,
            recordingSessionsPercent = 0.0,
            enableLogging = true
        )

        val initLatch = CountDownLatch(1)
        var initResult: Result<MPSessionReplayInstance?>? = null

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MPSessionReplay.initialize(
                appContext = context,
                token = testToken,
                distinctId = testDistinctId,
                config = config
            ) {
                initResult = it
                initLatch.countDown()
            }
        }

        // Start Foreground
        ActivityScenario.launch(ShellActivity::class.java)

        assertTrue(
            "Initialization callback should complete",
            initLatch.await(3, TimeUnit.SECONDS)
        )
        assertNotNull(initResult)
    }

    @Test
    fun testSessionReplayReinitialization() {
        // First initialization
        val firstLatch = CountDownLatch(1)
        var firstInstance: MPSessionReplayInstance? = null
        var firstError: Throwable? = null

        // Start Foreground
        ActivityScenario.launch(ShellActivity::class.java)

        MPSessionReplay.initialize(
            context,
            "test-token-1",
            "test-distinct-id-1",
            MPSessionReplayConfig(autoStartRecording = false)
        ) { result ->
            result.fold(
                onSuccess = { instance ->
                    firstInstance = instance
                    firstLatch.countDown()
                },
                onFailure = { error ->
                    firstError = error
                    firstLatch.countDown()
                }
            )
        }

        // Wait for first initialization
        assertTrue("First initialization timed out", firstLatch.await(5, TimeUnit.SECONDS))

        // Verify first instance is created
        assertNull("First initialization should not have error", firstError)
        assertNotNull("First instance should not be null", firstInstance)
        assertEquals(
            "getInstance should return the first instance",
            firstInstance,
            MPSessionReplay.getInstance()
        )

        // Second initialization (re-initialization)
        val secondLatch = CountDownLatch(1)
        var secondInstance: MPSessionReplayInstance? = null
        var secondError: Throwable? = null

        MPSessionReplay.initialize(
            context,
            "test-token-2",
            "test-distinct-id-2",
            MPSessionReplayConfig(autoStartRecording = false)
        ) { result ->
            result.fold(
                onSuccess = { instance ->
                    secondInstance = instance
                    secondLatch.countDown()
                },
                onFailure = { error ->
                    secondError = error
                    secondLatch.countDown()
                }
            )
        }

        // Wait for second initialization
        assertTrue("Second initialization timed out", secondLatch.await(5, TimeUnit.SECONDS))

        // Verify second instance is created and is different from first
        assertNull("Second initialization should not have error", secondError)
        assertNotNull("Second instance should not be null", secondInstance)
        assertNotEquals(
            "Second instance should be different from first instance",
            firstInstance,
            secondInstance
        )

        // Verify getInstance returns the second instance (only one instance exists)
        assertEquals(
            "getInstance should return the second instance after re-initialization",
            secondInstance,
            MPSessionReplay.getInstance()
        )

        // Verify only one instance exists by checking multiple calls to getInstance
        val currentInstance1 = MPSessionReplay.getInstance()
        val currentInstance2 = MPSessionReplay.getInstance()

        assertEquals(
            "Multiple calls to getInstance should return the same instance",
            currentInstance1,
            currentInstance2
        )
        assertEquals(
            "Current instance should be the second instance",
            secondInstance,
            currentInstance1
        )
    }

    @Test
    fun testMultipleReinitializations() {
        var previousInstance: MPSessionReplayInstance? = null

        // Start Foreground
        ActivityScenario.launch(ShellActivity::class.java)

        // Perform multiple re-initializations
        for (i in 1..5) {
            val latch = CountDownLatch(1)
            var instance: MPSessionReplayInstance? = null
            var error: Throwable? = null

            MPSessionReplay.initialize(
                context,
                "test-token-$i",
                "test-distinct-id-$i",
                MPSessionReplayConfig(autoStartRecording = false)
            ) { result ->
                result.fold(
                    onSuccess = { inst ->
                        instance = inst
                        latch.countDown()
                    },
                    onFailure = { err ->
                        error = err
                        latch.countDown()
                    }
                )
            }

            // Wait for initialization
            assertTrue("Initialization $i timed out", latch.await(5, TimeUnit.SECONDS))

            // Verify instance is created
            assertNull("Initialization $i should not have error", error)
            assertNotNull("Instance $i should not be null", instance)

            // Verify it's different from the previous instance
            if (previousInstance != null) {
                assertNotEquals(
                    "Instance $i should be different from previous instance",
                    previousInstance,
                    instance
                )
            }

            // Verify getInstance returns the current instance
            assertEquals(
                "getInstance should return instance $i",
                instance,
                MPSessionReplay.getInstance()
            )

            previousInstance = instance
        }

        // Final verification - only one instance should exist
        val finalInstance = MPSessionReplay.getInstance()
        assertNotNull("Final instance should not be null", finalInstance)
        assertEquals(
            "Final instance should be the last created instance",
            previousInstance,
            finalInstance
        )
    }
}
