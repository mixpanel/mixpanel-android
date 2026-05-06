package com.mixpanel.android.sessionreplay.sensitive_views

import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21]) // Test at API 21 to ensure compatibility
class SensitiveViewManagerTest {

    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        context = RuntimeEnvironment.getApplication()

        // Reset SensitiveViewManager state before each test
        clearAllMocks()
        // Reset to default state - need to clear the sensitive classes first
        resetSensitiveViewManager()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun resetSensitiveViewManager() {
        // Reset autoMaskedViews
        SensitiveViewManager.autoMaskedViews = emptySet()
        // Clear all custom sensitive classes by resetting to just EditText
        // We'll do this by setting autoMaskedViews to all, then to none to clear TextView, ImageView, WebView
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text, AutoMaskedView.Image, AutoMaskedView.Web)
        SensitiveViewManager.autoMaskedViews = emptySet()
    }

    // ===== Basic Sensitive Class Operations Tests =====

    @Test
    fun `test addSensitiveClass adds custom class`() {
        // Arrange
        class CustomView(context: android.content.Context) : View(context)

        // Act
        SensitiveViewManager.addSensitiveClass(CustomView::class.java)

        // Assert - verify by setting autoMaskedViews and checking if custom class is preserved
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text)

        // Create a real view instance
        val customView = CustomView(context)
        val parent = android.widget.LinearLayout(context)
        parent.addView(customView)

        // Mock the visibility checks
        val mockedView = spyk(customView)
        every { mockedView.isShown } returns true
        every { mockedView.getGlobalVisibleRect(any()) } returns true

        val mockedParent = spyk(parent)
        every { mockedParent.isShown } returns true
        every { mockedParent.getChildAt(0) } returns mockedView

        // Process and verify it's marked as sensitive
        val result = SensitiveViewManager.processSubviews(mockedParent)
        assertTrue(result.needsMasking)
    }

    @Test
    fun `test addSensitiveClass handles null gracefully`() {
        // Act & Assert - should not throw
        SensitiveViewManager.addSensitiveClass(null)
    }

    @Test
    fun `test removeSensitiveClass removes custom class`() {
        // Arrange
        class CustomView(context: android.content.Context) : View(context)
        SensitiveViewManager.addSensitiveClass(CustomView::class.java)

        // Act
        SensitiveViewManager.removeSensitiveClass(CustomView::class.java)

        // Assert - custom view should no longer be sensitive
        val customView = CustomView(context)

        val result = SensitiveViewManager.processSubviews(customView)
        assertFalse(result.needsMasking)
    }

    @Test
    fun `test removeSensitiveClass cannot remove EditText`() {
        // Act - try to remove EditText
        SensitiveViewManager.removeSensitiveClass(EditText::class.java)

        // Assert - EditText should still be sensitive
        val editText = EditText(context)
        val mockedEditText = spyk(editText)
        every { mockedEditText.isShown } returns true
        every { mockedEditText.getGlobalVisibleRect(any()) } returns true

        val result = SensitiveViewManager.processSubviews(mockedEditText)
        assertTrue(result.needsMasking)
    }

    @Test
    fun `test removeSensitiveClass handles null gracefully`() {
        // Act & Assert - should not throw
        SensitiveViewManager.removeSensitiveClass(null)
    }

    // ===== AutoMaskedViews Integration Tests =====

    @Test
    fun `test autoMaskedViews adds TextView when Text is enabled`() {
        // Act
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text)

        // Assert
        val textView = TextView(context)
        val mockedTextView = spyk(textView)
        every { mockedTextView.isShown } returns true
        every { mockedTextView.getGlobalVisibleRect(any()) } returns true

        val result = SensitiveViewManager.processSubviews(mockedTextView)
        assertTrue(result.needsMasking)
    }

    @Test
    fun `test autoMaskedViews removes TextView when Text is disabled`() {
        // Arrange - first enable Text
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text)

        // Act - disable Text
        SensitiveViewManager.autoMaskedViews = emptySet()

        // Assert
        val textView = TextView(context)

        val result = SensitiveViewManager.processSubviews(textView)
        assertFalse(result.needsMasking)
    }

    @Test
    fun `test autoMaskedViews adds ImageView when Image is enabled`() {
        // Act
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Image)

        // Assert
        val imageView = ImageView(context)
        val mockedImageView = spyk(imageView)
        every { mockedImageView.isShown } returns true
        every { mockedImageView.getGlobalVisibleRect(any()) } returns true

        val result = SensitiveViewManager.processSubviews(mockedImageView)
        assertTrue(result.needsMasking)
    }

    @Test
    fun `test autoMaskedViews removes ImageView when Image is disabled`() {
        // Arrange - first enable Image
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Image)

        // Act - disable Image
        SensitiveViewManager.autoMaskedViews = emptySet()

        // Assert
        val imageView = ImageView(context)

        val result = SensitiveViewManager.processSubviews(imageView)
        assertFalse(result.needsMasking)
    }

    @Test
    fun `test autoMaskedViews adds WebView when Web is enabled`() {
        // Act
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Web)

        // Assert
        val webView = WebView(context)
        val mockedWebView = spyk(webView)
        every { mockedWebView.isShown } returns true
        every { mockedWebView.getGlobalVisibleRect(any()) } returns true

        val result = SensitiveViewManager.processSubviews(mockedWebView)
        assertTrue(result.needsMasking)
    }

    @Test
    fun `test autoMaskedViews removes WebView when Web is disabled`() {
        // Arrange - first enable Web
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Web)

        // Act - disable Web
        SensitiveViewManager.autoMaskedViews = emptySet()

        // Assert
        val webView = WebView(context)

        val result = SensitiveViewManager.processSubviews(webView)
        assertFalse(result.needsMasking)
    }

    @Test
    fun `test autoMaskedViews handles multiple types`() {
        // Act
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text, AutoMaskedView.Image, AutoMaskedView.Web)

        // Assert all types are sensitive
        val textView = TextView(context)
        val mockedTextView = spyk(textView)
        every { mockedTextView.isShown } returns true
        every { mockedTextView.getGlobalVisibleRect(any()) } returns true

        val imageView = ImageView(context)
        val mockedImageView = spyk(imageView)
        every { mockedImageView.isShown } returns true
        every { mockedImageView.getGlobalVisibleRect(any()) } returns true

        val webView = WebView(context)
        val mockedWebView = spyk(webView)
        every { mockedWebView.isShown } returns true
        every { mockedWebView.getGlobalVisibleRect(any()) } returns true

        assertTrue(SensitiveViewManager.processSubviews(mockedTextView).needsMasking)
        assertTrue(SensitiveViewManager.processSubviews(mockedImageView).needsMasking)
        assertTrue(SensitiveViewManager.processSubviews(mockedWebView).needsMasking)
    }

    @Test
    fun `test EditText always remains sensitive regardless of autoMaskedViews`() {
        // Act - set empty autoMaskedViews
        SensitiveViewManager.autoMaskedViews = emptySet()

        // Assert - EditText should still be sensitive
        val editText = EditText(context)
        val mockedEditText = spyk(editText)
        every { mockedEditText.isShown } returns true
        every { mockedEditText.getGlobalVisibleRect(any()) } returns true

        val result = SensitiveViewManager.processSubviews(mockedEditText)
        assertTrue(result.needsMasking)
    }

    // ===== ProcessSubviews Integration Tests =====

    @Test
    fun `test processSubviews detects subclasses of sensitive classes`() {
        // Arrange - create a custom EditText subclass
        class CustomEditText(context: android.content.Context) : EditText(context)

        val customEditText = CustomEditText(context)
        val mockedCustomEditText = spyk(customEditText)
        every { mockedCustomEditText.isShown } returns true
        every { mockedCustomEditText.getGlobalVisibleRect(any()) } returns true

        // Act
        val result = SensitiveViewManager.processSubviews(mockedCustomEditText)

        // Assert - should be detected as sensitive because it extends EditText
        assertTrue(result.needsMasking)
    }

    @Test
    fun `test processSubviews respects safe views over sensitive classes`() {
        // Arrange
        val textView = TextView(context)

        // Enable Text masking
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text)

        // Mark the view as safe
        SensitiveViewManager.addSafeView(textView)

        // Act
        val result = SensitiveViewManager.processSubviews(textView)

        // Assert - should not be masked because it's marked as safe
        assertFalse(result.needsMasking)
    }

    @Test
    fun `test processSubviews handles ViewGroup with mixed sensitive and non-sensitive children`() {
        // Arrange
        val viewGroup = android.widget.LinearLayout(context)
        val editText = EditText(context)
        val regularView = View(context)

        viewGroup.addView(editText)
        viewGroup.addView(regularView)

        val mockedEditText = spyk(editText)
        every { mockedEditText.isShown } returns true
        every { mockedEditText.getGlobalVisibleRect(any()) } returns true

        val mockedRegularView = spyk(regularView)
        every { mockedRegularView.isShown } returns true

        val mockedViewGroup = spyk(viewGroup)
        every { mockedViewGroup.isShown } returns true
        every { mockedViewGroup.getChildAt(0) } returns mockedEditText
        every { mockedViewGroup.getChildAt(1) } returns mockedRegularView

        // Act
        val result = SensitiveViewManager.processSubviews(mockedViewGroup)

        // Assert - should need masking because of EditText child
        assertTrue(result.needsMasking)
    }

    @Test
    fun `test processSubviews handles null view`() {
        // Act
        val result = SensitiveViewManager.processSubviews(null)

        // Assert
        assertFalse(result.needsMasking)
    }

    // ===== Thread Safety Tests =====

    @Test
    fun `test concurrent add and remove operations on sensitive classes`() {
        // Arrange
        class TestView1(context: android.content.Context) : View(context)
        class TestView2(context: android.content.Context) : View(context)
        class TestView3(context: android.content.Context) : View(context)

        val latch = CountDownLatch(3)
        var exception: Exception? = null

        // Act - perform concurrent operations
        thread {
            try {
                repeat(100) {
                    SensitiveViewManager.addSensitiveClass(TestView1::class.java)
                    SensitiveViewManager.removeSensitiveClass(TestView1::class.java)
                }
            } catch (e: Exception) {
                exception = e
            } finally {
                latch.countDown()
            }
        }

        thread {
            try {
                repeat(100) {
                    SensitiveViewManager.addSensitiveClass(TestView2::class.java)
                    SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text, AutoMaskedView.Image)
                }
            } catch (e: Exception) {
                exception = e
            } finally {
                latch.countDown()
            }
        }

        thread {
            try {
                repeat(100) {
                    SensitiveViewManager.addSensitiveClass(TestView3::class.java)
                    SensitiveViewManager.autoMaskedViews = emptySet()
                }
            } catch (e: Exception) {
                exception = e
            } finally {
                latch.countDown()
            }
        }

        // Wait for all threads to complete
        assertTrue(latch.await(5, TimeUnit.SECONDS))

        // Assert - no exceptions should have occurred
        assertNull(exception)
    }

    @Test
    fun `test maskSensitiveViews draws rectangles for sensitive views`() {
        // Arrange
        val mockCanvas = mockk<Canvas>(relaxed = true)
        val view = View(context)
        val editText = EditText(context)

        val mockedEditText = spyk(editText)
        every { mockedEditText.isShown } returns true
        every { mockedEditText.getGlobalVisibleRect(any()) } answers {
            val rect = firstArg<Rect>()
            rect.set(10, 10, 100, 100)
            true
        }

        // First process the view to collect bounds
        val summary = SensitiveViewManager.processSubviews(mockedEditText)

        // Act - pass bounds from processSubviews
        SensitiveViewManager.maskSensitiveViews(view, mockCanvas, summary.boundsSnapshot)

        // Assert - verify canvas draw was called
        verify { mockCanvas.drawRect(any<Rect>(), any()) }
    }

    @Test
    fun `test hidden views are not masked`() {
        // Act
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text)

        // Assert
        val textViewVisible = spyk(TextView(context)) {
            every { isShown } returns true
            every { getGlobalVisibleRect(any()) } returns true
        }
        val textViewInvisible = spyk(TextView(context)) {
            every { isShown } returns false
            every { getGlobalVisibleRect(any()) } returns false
        }

        val resultVisible = SensitiveViewManager.processSubviews(textViewVisible)
        val resultInvisible = SensitiveViewManager.processSubviews(textViewInvisible)

        assertTrue(resultVisible.needsMasking)
        assertFalse(resultInvisible.needsMasking)
    }

    // ===== MaskRegionsListener Tests =====

    @Test
    fun `test MaskRegionsListener is notified when mask regions are detected`() {
        // Arrange
        val capturedEntries = mutableListOf<Map<Rect, MaskDecision>>()
        val listener = MaskRegionsListener { entries ->
            capturedEntries.add(entries)
        }
        SensitiveViewManager.setMaskRegionsListener(listener)

        val editText = EditText(context)
        val mockedEditText = spyk(editText)
        every { mockedEditText.isShown } returns true
        every { mockedEditText.getGlobalVisibleRect(any()) } answers {
            val rect = firstArg<Rect>()
            rect.set(10, 20, 100, 80)
            true
        }

        // Act
        SensitiveViewManager.processSubviews(mockedEditText)

        // Assert
        assertTrue("Listener should have been called", capturedEntries.isNotEmpty())
        val entries = capturedEntries.first()
        // EditText is a text entry mask (security-enforced)
        val expectedRect = Rect(10, 20, 100, 80)
        assertTrue(
            "Text entry mask entries should contain the correct rect",
            entries[expectedRect] == MaskDecision.TEXT_ENTRY
        )

        // Cleanup
        SensitiveViewManager.setMaskRegionsListener(null)
    }

    @Test
    fun `test MaskRegionsListener receives empty entries when no sensitive views`() {
        // Arrange
        val capturedEntries = mutableListOf<Map<Rect, MaskDecision>>()
        val listener = MaskRegionsListener { entries ->
            capturedEntries.add(entries)
        }
        SensitiveViewManager.setMaskRegionsListener(listener)

        val regularView = View(context)
        val mockedView = spyk(regularView)
        every { mockedView.isShown } returns true

        // Act
        SensitiveViewManager.processSubviews(mockedView)

        // Assert
        assertTrue("Listener should have been called", capturedEntries.isNotEmpty())
        val entries = capturedEntries.first()
        assertTrue("All mask entries should be empty for non-sensitive view", entries.isEmpty())

        // Cleanup
        SensitiveViewManager.setMaskRegionsListener(null)
    }

    @Test
    fun `test MaskRegionsListener receives multiple entries for multiple sensitive views`() {
        // Arrange
        val capturedEntries = mutableListOf<Map<Rect, MaskDecision>>()
        val listener = MaskRegionsListener { entries ->
            capturedEntries.add(entries)
        }
        SensitiveViewManager.setMaskRegionsListener(listener)

        val viewGroup = android.widget.LinearLayout(context)
        val editText1 = EditText(context)
        val editText2 = EditText(context)

        viewGroup.addView(editText1)
        viewGroup.addView(editText2)

        val mockedEditText1 = spyk(editText1)
        every { mockedEditText1.isShown } returns true
        every { mockedEditText1.getGlobalVisibleRect(any()) } answers {
            val rect = firstArg<Rect>()
            rect.set(0, 0, 100, 50)
            true
        }

        val mockedEditText2 = spyk(editText2)
        every { mockedEditText2.isShown } returns true
        every { mockedEditText2.getGlobalVisibleRect(any()) } answers {
            val rect = firstArg<Rect>()
            rect.set(0, 60, 100, 110)
            true
        }

        val mockedViewGroup = spyk(viewGroup)
        every { mockedViewGroup.isShown } returns true
        every { mockedViewGroup.getChildAt(0) } returns mockedEditText1
        every { mockedViewGroup.getChildAt(1) } returns mockedEditText2

        // Act
        SensitiveViewManager.processSubviews(mockedViewGroup)

        // Assert
        assertTrue("Listener should have been called", capturedEntries.isNotEmpty())
        val entries = capturedEntries.first()
        // EditTexts are text entry masks (security-enforced)
        val textEntryEntries = entries.filter { it.value == MaskDecision.TEXT_ENTRY }
        assertTrue("Should have at least 2 text entry mask entries", textEntryEntries.size >= 2)

        // Cleanup
        SensitiveViewManager.setMaskRegionsListener(null)
    }

    @Test
    fun `test safe container still masks sensitive children`() {
        // Arrange - a safe parent container with an EditText child
        val viewGroup = android.widget.LinearLayout(context)
        val editText = EditText(context)
        viewGroup.addView(editText)

        // Mark the parent as safe
        SensitiveViewManager.addSafeView(viewGroup)

        val mockedEditText = spyk(editText)
        every { mockedEditText.isShown } returns true
        every { mockedEditText.getGlobalVisibleRect(any()) } answers {
            val rect = firstArg<Rect>()
            rect.set(0, 0, 100, 50)
            true
        }

        val mockedViewGroup = spyk(viewGroup)
        every { mockedViewGroup.isShown } returns true
        every { mockedViewGroup.getChildAt(0) } returns mockedEditText

        // Act
        val result = SensitiveViewManager.processSubviews(mockedViewGroup)

        // Assert - EditText inside safe container must still be masked
        assertTrue("Sensitive children inside safe containers must still be masked", result.needsMasking)

        // Cleanup
        SensitiveViewManager.removeSafeView(viewGroup)
    }

    @Test
    fun `test MaskRegionsListener categorizes auto and text entry masks correctly`() {
        // Arrange
        val capturedEntries = mutableListOf<Map<Rect, MaskDecision>>()
        val listener = MaskRegionsListener { entries ->
            capturedEntries.add(entries)
        }
        SensitiveViewManager.setMaskRegionsListener(listener)

        // Enable auto-masking for Text
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text)

        val viewGroup = android.widget.LinearLayout(context)
        val editText = EditText(context) // Should be text entry mask (security-enforced)
        val textView = TextView(context) // Should be auto mask (Text auto-mask enabled)

        viewGroup.addView(editText)
        viewGroup.addView(textView)

        val mockedEditText = spyk(editText)
        every { mockedEditText.isShown } returns true
        every { mockedEditText.getGlobalVisibleRect(any()) } answers {
            val rect = firstArg<Rect>()
            rect.set(0, 0, 100, 50)
            true
        }

        val mockedTextView = spyk(textView)
        every { mockedTextView.isShown } returns true
        every { mockedTextView.getGlobalVisibleRect(any()) } answers {
            val rect = firstArg<Rect>()
            rect.set(0, 60, 100, 110)
            true
        }

        val mockedViewGroup = spyk(viewGroup)
        every { mockedViewGroup.isShown } returns true
        every { mockedViewGroup.getChildAt(0) } returns mockedEditText
        every { mockedViewGroup.getChildAt(1) } returns mockedTextView

        // Act
        SensitiveViewManager.processSubviews(mockedViewGroup)

        // Assert
        assertTrue("Listener should have been called", capturedEntries.isNotEmpty())
        val entries = capturedEntries.first()

        // EditText should be text entry mask
        val editTextRect = Rect(0, 0, 100, 50)
        assertTrue(
            "EditText should be in text entry mask entries",
            entries[editTextRect] == MaskDecision.TEXT_ENTRY
        )

        // TextView should be auto mask
        val textViewRect = Rect(0, 60, 100, 110)
        assertTrue(
            "TextView should be in auto mask entries",
            entries[textViewRect] == MaskDecision.AUTO
        )

        // Cleanup
        SensitiveViewManager.setMaskRegionsListener(null)
    }
}
