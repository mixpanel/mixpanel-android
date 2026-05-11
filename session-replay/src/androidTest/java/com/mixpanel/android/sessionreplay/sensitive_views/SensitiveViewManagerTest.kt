package com.mixpanel.android.sessionreplay.sensitive_views

import android.content.Context
import android.view.View
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mixpanel.android.sessionreplay.ShellActivity
import com.mixpanel.android.sessionreplay.withAttachedView
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SensitiveViewManagerTest {
    private lateinit var context: Context
    private lateinit var scenario: ActivityScenario<ShellActivity>

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch(ShellActivity::class.java)
        // Reset autoMaskedViews to clear any sensitive classes
        SensitiveViewManager.autoMaskedViews = emptySet()
    }

    @After
    fun tearDown() {
        SensitiveViewManager.autoMaskedViews = emptySet()
        scenario.close()
    }

    @Test
    fun addSafeViewSuccessfully() {
        // Arrange
        val view = View(context)

        // Act
        val result = SensitiveViewManager.addSafeView(view)

        // Assert
        assertTrue(result)

        // Verify the view can be removed
        assertTrue(SensitiveViewManager.removeSafeView(view))
    }

    @Test
    fun addSafeViewPreventsDuplicates() {
        // Arrange
        val view = View(context)
        SensitiveViewManager.addSafeView(view)

        // Act
        val result = SensitiveViewManager.addSafeView(view)

        // Assert
        assertFalse(result) // Adding the same view again should return false
    }

    @Test
    fun removeSafeViewSuccessfully() {
        // Arrange
        val view = View(context)
        SensitiveViewManager.addSafeView(view)

        // Act
        val result = SensitiveViewManager.removeSafeView(view)

        // Assert
        assertTrue(result)
    }

    @Test
    fun removeSafeViewReturnsFalseForNonExistentView() {
        // Arrange
        val view = View(context)

        // Act
        val result = SensitiveViewManager.removeSafeView(view)

        // Assert
        assertFalse(result)
    }

    @Test
    fun processSubviewsRespectsSafeViews() {
        // Arrange
        val sensitiveTextView = TextView(context)

        // Mark the view as a safe view first
        SensitiveViewManager.addSafeView(sensitiveTextView)

        // Act
        val summary = SensitiveViewManager.processSubviews(sensitiveTextView)

        // Assert - safe view with no auto-masking should not need masking
        assertFalse(summary.needsMasking)
    }

    @Test
    fun canAddAndRemoveMultipleSafeViews() {
        // Arrange
        val view1 = View(context)
        val view2 = TextView(context)
        val views = listOf(view1, view2)

        // Act & Assert
        views.forEach { view ->
            assertTrue(SensitiveViewManager.addSafeView(view))
        }

        views.forEach { view ->
            assertTrue(SensitiveViewManager.removeSafeView(view))
        }
    }

    @Test
    fun safeViewsDoNotImpactSensitiveViewTracking() {
        scenario.withAttachedView({ activity ->
            EditText(activity).apply {
                SensitiveViewManager.addSensitiveView(this)
                SensitiveViewManager.addSafeView(this)
            }
        }) { view ->
            val summary = SensitiveViewManager.processSubviews(view)
            // The view being both sensitive and safe should still be considered for masking
            assertTrue(summary.needsMasking)
        }
    }

    // ===== Sensitive Class Tests - These would catch API incompatibility =====

    @Test
    fun testAddSensitiveClassWithCustomView() {
        // This test exercises the _sensitiveClasses collection which uses Collections.newSetFromMap
        // Running on API 21 emulator would have caught ConcurrentHashMap.newKeySet() incompatibility

        // Create a custom view class
        class CustomView(context: Context) : View(context)

        // Add the custom class as sensitive
        SensitiveViewManager.addSensitiveClass(CustomView::class.java)

        scenario.withAttachedView({ activity ->
            CustomView(activity)
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            // Should be marked as sensitive
            assertTrue("Custom view should be sensitive", result.needsMasking)
        }
    }

    @Test
    fun testAutoMaskedViewsUpdatesTextView() {
        // This test exercises updateSensitiveClasses() which modifies _sensitiveClasses

        // Enable Text masking
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text)

        scenario.withAttachedView({ activity ->
            TextView(activity)
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            // Should be marked as sensitive
            assertTrue("TextView should be sensitive when Text is enabled", result.needsMasking)

            // Disable Text masking
            SensitiveViewManager.autoMaskedViews = emptySet()

            // Process again
            val result2 = SensitiveViewManager.processSubviews(view)

            // Should no longer be sensitive
            assertFalse("TextView should not be sensitive when Text is disabled", result2.needsMasking)
        }
    }

    @Test
    fun testAutoMaskedViewsUpdatesImageView() {
        // Enable Image masking
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Image)

        scenario.withAttachedView({ activity ->
            ImageView(activity)
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            // Should be marked as sensitive
            assertTrue("ImageView should be sensitive when Image is enabled", result.needsMasking)

            // Disable Image masking
            SensitiveViewManager.autoMaskedViews = emptySet()

            // Process again
            val result2 = SensitiveViewManager.processSubviews(view)

            // Should no longer be sensitive
            assertFalse("ImageView should not be sensitive when Image is disabled", result2.needsMasking)
        }
    }

    @Test
    fun testAutoMaskedViewsUpdatesWebView() {
        // Enable Web masking
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Web)

        scenario.withAttachedView({ activity ->
            WebView(activity)
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            // Should be marked as sensitive
            assertTrue("WebView should be sensitive when Web is enabled", result.needsMasking)

            // Disable Web masking
            SensitiveViewManager.autoMaskedViews = emptySet()

            // Process again
            val result2 = SensitiveViewManager.processSubviews(view)

            // Should no longer be sensitive
            assertFalse("WebView should not be sensitive when Web is disabled", result2.needsMasking)
        }
    }

    @Test
    fun testEditTextAlwaysRemainsSensitive() {
        // EditText should always be sensitive regardless of autoMaskedViews

        // Clear all auto masked views
        SensitiveViewManager.autoMaskedViews = emptySet()

        scenario.withAttachedView({ activity ->
            EditText(activity)
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            // Should still be marked as sensitive
            assertTrue("EditText should always be sensitive", result.needsMasking)
        }
    }

    @Test
    fun testRemoveSensitiveClassCannotRemoveEditText() {
        // Try to remove EditText from sensitive classes
        SensitiveViewManager.removeSensitiveClass(EditText::class.java)

        scenario.withAttachedView({ activity ->
            EditText(activity)
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            // Should still be marked as sensitive
            assertTrue("EditText should remain sensitive even after removal attempt", result.needsMasking)
        }
    }

    @Test
    fun testConcurrentModificationOfSensitiveClasses() {
        // This test specifically exercises the thread-safe collection
        // The API 21 incompatibility would have manifested here

        val threads = mutableListOf<Thread>()
        var exception: Exception? = null

        // Create multiple threads that modify sensitive classes
        repeat(5) {
            threads.add(
                Thread {
                    try {
                        // Each thread adds/removes classes and changes autoMaskedViews
                        repeat(20) {
                            class TestView(context: Context) : View(context)
                            SensitiveViewManager.addSensitiveClass(TestView::class.java)
                            SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text, AutoMaskedView.Image)
                            SensitiveViewManager.removeSensitiveClass(TestView::class.java)
                            SensitiveViewManager.autoMaskedViews = emptySet()
                        }
                    } catch (e: Exception) {
                        exception = e
                    }
                }
            )
        }

        // Start all threads
        threads.forEach { it.start() }

        // Wait for completion
        threads.forEach { it.join(5000) }

        // Should complete without exceptions
        assertTrue("No exceptions should occur during concurrent modification", exception == null)
    }

    @Test
    fun testProcessSubviewsWithMixedSensitiveClasses() {
        // Enable Text masking
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text)

        scenario.withAttachedView({ activity ->
            LinearLayout(activity).apply {
                val editText = EditText(activity)
                val textView = TextView(activity)
                val regularView = View(activity)
                addView(editText)
                addView(textView)
                addView(regularView)
            }
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            // Should be marked as sensitive due to EditText and TextView
            assertTrue("Parent should be sensitive due to sensitive children", result.needsMasking)
        }
    }
}
