package com.mixpanel.android.sessionreplay.sensitive_views

import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mixpanel.android.sessionreplay.ShellActivity
import com.mixpanel.android.sessionreplay.withAttachedView
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for safe container masking logic.
 *
 * These tests verify that:
 * 1. Safe containers protect non-input children from global masking rules
 * 2. EditText and subclasses are ALWAYS masked regardless of safe container status
 * 3. Safe status properly propagates through view hierarchies
 */
@RunWith(AndroidJUnit4::class)
class SafeContainerMaskingTest {

    private lateinit var scenario: ActivityScenario<ShellActivity>

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(ShellActivity::class.java)
        // Reset SensitiveViewManager state before each test
        SensitiveViewManager.autoMaskedViews = emptySet()
    }

    @After
    fun tearDown() {
        // Clean up after each test
        SensitiveViewManager.autoMaskedViews = emptySet()
        scenario.close()
    }

    @Test
    fun testEditTextMaskedInSafeViewGroup() {
        scenario.withAttachedView({ activity ->
            LinearLayout(activity).apply {
                val editText = EditText(activity)
                addView(editText)
                SensitiveViewManager.addSafeView(this)
            }
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            assertTrue(
                "EditText must always be masked even in safe container",
                result.needsMasking
            )
        }
    }

    @Test
    fun testTextViewNotMaskedInSafeViewGroup() {
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text)

        scenario.withAttachedView({ activity ->
            LinearLayout(activity).apply {
                val textView = TextView(activity).apply { text = "Safe content" }
                addView(textView)
                SensitiveViewManager.addSafeView(this)
            }
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            assertFalse(
                "TextView should be protected by safe container",
                result.needsMasking
            )
        }
    }

    @Test
    fun testImageViewNotMaskedInSafeViewGroup() {
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Image)

        scenario.withAttachedView({ activity ->
            FrameLayout(activity).apply {
                val imageView = ImageView(activity)
                addView(imageView)
                SensitiveViewManager.addSafeView(this)
            }
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            assertFalse(
                "ImageView should be protected by safe container",
                result.needsMasking
            )
        }
    }

    @Test
    fun testNestedSafeContainersProtectTextView() {
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text)

        scenario.withAttachedView({ activity ->
            LinearLayout(activity).apply {
                val parent = LinearLayout(activity)
                val textView = TextView(activity).apply { text = "Protected content" }
                parent.addView(textView)
                addView(parent)
                SensitiveViewManager.addSafeView(this) // Only grandparent marked safe
            }
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            assertFalse(
                "TextView should be protected by ancestor safe container",
                result.needsMasking
            )
        }
    }

    @Test
    fun testEditTextMaskedInNestedSafeContainers() {
        scenario.withAttachedView({ activity ->
            LinearLayout(activity).apply {
                val parent = LinearLayout(activity)
                val editText = EditText(activity)
                parent.addView(editText)
                addView(parent)
                SensitiveViewManager.addSafeView(this)
                SensitiveViewManager.addSafeView(parent)
            }
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            assertTrue(
                "EditText must be masked even in nested safe containers",
                result.needsMasking
            )
        }
    }

    @Test
    fun testMixedContentInSafeContainer() {
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text, AutoMaskedView.Image)

        scenario.withAttachedView({ activity ->
            LinearLayout(activity).apply {
                val textView = TextView(activity).apply { text = "Safe text" }
                val imageView = ImageView(activity)
                val editText = EditText(activity)
                addView(textView)
                addView(imageView)
                addView(editText)
                SensitiveViewManager.addSafeView(this)
            }
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            assertTrue(
                "Container should need masking due to EditText",
                result.needsMasking
            )
        }
    }

    @Test
    fun testAutoCompleteTextViewMaskedInSafeContainer() {
        scenario.withAttachedView({ activity ->
            LinearLayout(activity).apply {
                val autoComplete = AutoCompleteTextView(activity)
                addView(autoComplete)
                SensitiveViewManager.addSafeView(this)
            }
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            assertTrue(
                "AutoCompleteTextView (EditText subclass) must be masked in safe container",
                result.needsMasking
            )
        }
    }

    @Test
    fun testSafeViewExplicitlyMarkedSensitiveOverrides() {
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text)

        scenario.withAttachedView({ activity ->
            TextView(activity).apply {
                text = "Conflicted status"
                SensitiveViewManager.addSafeView(this)
                SensitiveViewManager.addSensitiveView(this)
            }
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            assertTrue(
                "Explicit sensitive marking should override safe status",
                result.needsMasking
            )
        }
    }

    @Test
    fun testSafeContainerWithNoAutoMasking() {
        SensitiveViewManager.autoMaskedViews = emptySet()

        scenario.withAttachedView({ activity ->
            LinearLayout(activity).apply {
                val textView = TextView(activity)
                val imageView = ImageView(activity)
                addView(textView)
                addView(imageView)
                SensitiveViewManager.addSafeView(this)
            }
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            assertFalse(
                "Safe container with no auto-masking should not mask",
                result.needsMasking
            )
        }
    }

    @Test
    fun testEditTextDirectlyMarkedSafeStillMasked() {
        scenario.withAttachedView({ activity ->
            EditText(activity).apply {
                SensitiveViewManager.addSafeView(this)
            }
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            assertTrue(
                "EditText must be masked even when directly marked as safe",
                result.needsMasking
            )
        }
    }

    @Test
    fun testComplexNestedHierarchyWithMixedSafeStatus() {
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text, AutoMaskedView.Image)

        scenario.withAttachedView({ activity ->
            // Complex hierarchy:
            // root (safe) -> branch1 (not safe) -> textView1, editText1
            //            -> branch2 (safe) -> textView2, imageView2, editText2
            LinearLayout(activity).apply {
                val branch1 = LinearLayout(activity)
                val branch2 = LinearLayout(activity)

                val textView1 = TextView(activity).apply { text = "Branch 1 text" }
                val editText1 = EditText(activity)

                val textView2 = TextView(activity).apply { text = "Branch 2 text" }
                val imageView2 = ImageView(activity)
                val editText2 = EditText(activity)

                branch1.addView(textView1)
                branch1.addView(editText1)
                branch2.addView(textView2)
                branch2.addView(imageView2)
                branch2.addView(editText2)
                addView(branch1)
                addView(branch2)

                SensitiveViewManager.addSafeView(this)
                SensitiveViewManager.addSafeView(branch2)
            }
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            assertTrue(
                "Complex hierarchy should mask due to EditText fields",
                result.needsMasking
            )
        }
    }

    @Test
    fun testChildExplicitlyMarkedSensitiveInSafeContainer() {
        SensitiveViewManager.autoMaskedViews = setOf(AutoMaskedView.Text)

        scenario.withAttachedView({ activity ->
            LinearLayout(activity).apply {
                val textView = TextView(activity).apply { text = "Explicitly sensitive" }
                addView(textView)
                SensitiveViewManager.addSafeView(this)
                SensitiveViewManager.addSensitiveView(textView) // Explicit override
            }
        }) { view ->
            val result = SensitiveViewManager.processSubviews(view)
            assertTrue(
                "Child explicitly marked sensitive should be masked in safe container",
                result.needsMasking
            )
        }
    }
}
