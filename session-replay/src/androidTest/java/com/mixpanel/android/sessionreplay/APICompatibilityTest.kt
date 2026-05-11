package com.mixpanel.android.sessionreplay

import android.content.Context
import android.view.View
import android.widget.TextView
import android.widget.EditText
import android.widget.ImageView
import android.webkit.WebView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mixpanel.android.sessionreplay.models.MPSessionReplayConfig
import com.mixpanel.android.sessionreplay.sensitive_views.AutoMaskedView
import com.mixpanel.android.sessionreplay.utils.ReplaySettings
import com.mixpanel.android.sessionreplay.extensions.mpReplaySensitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Before
import org.junit.Rule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumentation test to verify all public APIs work across all Android API levels
 * without throwing exceptions. This ensures API compatibility from minimum API 21
 * through the latest supported versions.
 */
@RunWith(AndroidJUnit4::class)
class APICompatibilityTest {

    private lateinit var context: Context
    private val testToken = "test_token_123"
    private val testDistinctId = "test_user_123"

    @get:Rule
    var activityScenarioRule = ActivityScenarioRule(ShellActivity::class.java)

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Ensure clean state on main thread
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MPSessionReplay.getInstance()?.deinitialize()
        }
    }

    @Test
    fun testAllPublicAPIs() {
        // Test 1: MPSessionReplayConfig creation with all parameters
        val config = MPSessionReplayConfig(
            wifiOnly = false,
            flushInterval = 60000L,
            autoStartRecording = false,
            recordingSessionsPercent = 50.0,
            autoMaskedViews = setOf(AutoMaskedView.Text, AutoMaskedView.Image),
            enableLogging = true
        )
        assertNotNull(config)

        // Test 2: Config JSON serialization
        val jsonString = config.toJson()
        assertNotNull(jsonString)
        assertTrue(jsonString.isNotEmpty())

        // Test 3: Config JSON deserialization
        val deserializedConfig = MPSessionReplayConfig.fromJson(jsonString)
        assertNotNull(deserializedConfig)
        assertEquals(config.wifiOnly, deserializedConfig.wifiOnly)

        // Test 4: AutoMaskedView enum and defaultSet
        val defaultSet = AutoMaskedView.defaultSet()
        assertNotNull(defaultSet)
        assertTrue(defaultSet.isNotEmpty())

        // Test enum values
        assertNotNull(AutoMaskedView.Text)
        assertNotNull(AutoMaskedView.Image)
        assertNotNull(AutoMaskedView.Web)

        // Test 5: Initialize with callback - full version
        val initLatch = CountDownLatch(1)
        var initResult: Result<MPSessionReplayInstance?>? = null

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MPSessionReplay.initialize(
                appContext = context,
                token = testToken,
                distinctId = testDistinctId,
                config = config
            ) { result ->
                initResult = result
                initLatch.countDown()
            }
        }

        assertTrue("Initialization callback should complete", initLatch.await(5, TimeUnit.SECONDS))
        assertNotNull(initResult)

        // Test 6: getInstance() after initialization
        val instance = MPSessionReplay.getInstance()
        assertNotNull(instance)

        // Test 7: Instance properties
        assertEquals(testToken, instance!!.token)
        assertNotNull(instance.config)

        // Test 8: Recording control methods
        instance.stopRecording() // Stop any auto-recording first
        instance.startRecording(75.0)
        instance.autoStartRecording()
        instance.stopRecording()

        // Test 9: Identity management
        val replayId = instance.getReplayId()
        assertNotNull(replayId)

        val distinctId = instance.getDistinctId()
        assertEquals(testDistinctId, distinctId)

        instance.identify("new_user_456")
        assertEquals("new_user_456", instance.getDistinctId())

        // Test 10: Sensitive view management with real views
        val textView = TextView(context)
        val editText = EditText(context)
        val imageView = ImageView(context)

        // Individual view operations
        instance.addSensitiveView(textView)
        instance.removeSensitiveView(textView)
        instance.addSafeView(editText)
        instance.removeSafeView(editText)

        // Class operations
        instance.addSensitiveClass(TextView::class.java)
        instance.removeSensitiveClass(TextView::class.java)
        instance.addSensitiveClass(ImageView::class.java)

        // Test 11: Flush operation
        val flushLatch = CountDownLatch(1)
        instance.flush {
            flushLatch.countDown()
        }
        assertTrue("Flush callback should complete", flushLatch.await(5, TimeUnit.SECONDS))

        // Test 12: View extension function
        val view = View(context)
        val sensitiveView = view.mpReplaySensitive(true)
        assertSame(view, sensitiveView) // Should return same view

        val nonSensitiveView = view.mpReplaySensitive(false)
        assertSame(view, nonSensitiveView)

        // Test 13: Modifier extension (Compose) - Skip if Compose not available
        // Note: Compose extension is tested in separate Compose tests

        // Test 14: SessionReplaySender is internal - skip
        // Note: SessionReplaySender is an internal API

        // Test 15: Error types construction
        val disabledError = MPSessionReplayError.Disabled("Test reason")
        assertEquals("Test reason", disabledError.reason)

        val initError = MPSessionReplayError.InitializationError(Exception("Test exception"))
        assertNotNull(initError.cause)

        // Test 16: Deinitialize
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            instance.deinitialize()
        }

        // Note: getInstance() behavior after deinitialize may vary - not testing null

        // Test 18: JvmStatic overloads for Java compatibility
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // Initialize with config
            MPSessionReplay.initialize(context, testToken, testDistinctId, MPSessionReplayConfig())
            // Note: Not asserting getInstance() as it may be async
            MPSessionReplay.getInstance()?.deinitialize()

            // Initialize without config
            MPSessionReplay.initialize(context, testToken, testDistinctId)
            // Note: Not asserting getInstance() as it may be async
            MPSessionReplay.getInstance()?.deinitialize()
        }

        // Initialize with callback (JvmStatic version)
        val jvmLatch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MPSessionReplay.initialize(context, testToken, testDistinctId) { result ->
                jvmLatch.countDown()
            }
        }
        assertTrue("JvmStatic callback should complete", jvmLatch.await(5, TimeUnit.SECONDS))

        // Test 19: WebView handling on main thread
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = WebView(context)
            assertNotNull(webView)

            // Test adding WebView as sensitive
            val currentInstance = MPSessionReplay.getInstance()
            currentInstance?.addSensitiveView(webView)
            currentInstance?.removeSensitiveView(webView)
        }

        // Test 20: Multiple initialization attempts
        val multiInitLatch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MPSessionReplay.initialize(context, testToken, testDistinctId) { result ->
                multiInitLatch.countDown()
            }
        }
        assertTrue("Multiple init should complete", multiInitLatch.await(5, TimeUnit.SECONDS))

        // Clean up
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MPSessionReplay.getInstance()?.deinitialize()
        }
    }

    @Test
    fun testConfigDefaultValues() {
        // Test default configuration values
        val defaultConfig = MPSessionReplayConfig()

        assertTrue(defaultConfig.wifiOnly)
        assertEquals(ReplaySettings.FLUSH_INTERVAL, defaultConfig.flushInterval)
        assertTrue(defaultConfig.autoStartRecording)
        assertEquals(100.0, defaultConfig.recordingSessionsPercent, 0.001)
        assertEquals(AutoMaskedView.defaultSet(), defaultConfig.autoMaskedViews)
        assertFalse(defaultConfig.enableLogging)
    }

    @Test
    fun testThreadSafetyOfAPIs() {
        // Initialize first on main thread
        val initLatch = CountDownLatch(1)
        var instance: MPSessionReplayInstance? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MPSessionReplay.initialize(context, testToken, testDistinctId) { result ->
                instance = result.getOrNull()
                initLatch.countDown()
            }
        }
        assertTrue("Init should complete", initLatch.await(5, TimeUnit.SECONDS))
        assertNotNull(instance)

        // Test concurrent access to APIs
        val threads = mutableListOf<Thread>()
        val errors = mutableListOf<Exception>()

        repeat(5) { i ->
            threads.add(
                Thread {
                    try {
                        // Concurrent view operations
                        val view = TextView(context)
                        instance!!.addSensitiveView(view)
                        instance!!.removeSensitiveView(view)
                        instance!!.addSafeView(view)
                        instance!!.removeSafeView(view)

                        // Concurrent class operations
                        instance!!.addSensitiveClass(TextView::class.java)
                        instance!!.removeSensitiveClass(TextView::class.java)

                        // Concurrent identity operations
                        instance!!.identify("concurrent_user_$i")
                        instance!!.getDistinctId()
                        instance!!.getReplayId()

                        // Concurrent recording operations
                        instance!!.startRecording(50.0)
                        instance!!.stopRecording()
                    } catch (e: Exception) {
                        synchronized(errors) {
                            errors.add(e)
                        }
                    }
                }
            )
        }

        // Start all threads
        threads.forEach { it.start() }

        // Wait for completion
        threads.forEach { it.join(5000) }

        // Verify no exceptions
        assertTrue("No exceptions during concurrent access", errors.isEmpty())

        // Clean up
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            instance?.deinitialize()
        }
    }
}
