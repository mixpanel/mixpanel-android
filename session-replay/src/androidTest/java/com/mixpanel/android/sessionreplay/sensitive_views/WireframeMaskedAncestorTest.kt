package com.mixpanel.android.sessionreplay.sensitive_views

import android.graphics.Rect
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.mixpanel.android.sessionreplay.ShellActivity
import com.mixpanel.android.sessionreplay.extensions.mpReplaySensitive
import com.mixpanel.android.sessionreplay.wireframe.MaskDecision
import com.mixpanel.android.sessionreplay.wireframe.WireframeElement
import com.mixpanel.android.sessionreplay.wireframe.WireframeEmitter
import com.mixpanel.android.sessionreplay.wireframe.WireframeType
import com.mixpanel.android.sessionreplay.withAttachedView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real-framework coverage for mask provenance that cannot be represented by layoutlib. */
@RunWith(AndroidJUnit4::class)
class WireframeMaskedAncestorTest {
    private lateinit var scenario: ActivityScenario<ShellActivity>

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(ShellActivity::class.java)
        SensitiveViewManager.autoMaskedViews = emptySet()
    }

    @After
    fun tearDown() {
        SensitiveViewManager.autoMaskedViews = emptySet()
        scenario.close()
    }

    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun offsetChildOutsideMaskedView_stripsTextByProvenance() {
        scenario.withAttachedView({ activity ->
            FrameLayout(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                val masked = FrameLayout(activity).apply {
                    clipChildren = false
                    mpReplaySensitive(true)
                    addView(
                        Button(activity).apply { text = "Transfer 12345 to Chase" },
                        FrameLayout.LayoutParams(400, 80).apply {
                            leftMargin = 200
                            topMargin = 200
                        }
                    )
                }
                addView(
                    masked,
                    FrameLayout.LayoutParams(100, 100).apply {
                        leftMargin = 16
                        topMargin = 16
                    }
                )
            }
        }) { root ->
            val elements = mutableListOf<WireframeElement>()
            val summary = SensitiveViewManager.processSubviews(root, elements)
            val button = elements.singleOrNull { it.type == WireframeType.Button }
            assertNotNull("the out-of-bounds child must still be visible", button)

            val buttonRect = Rect(button!!.x, button.y, button.x + button.w, button.y + button.h)
            assertFalse(
                "the regression fixture must not depend on geometric stripping",
                summary.boundsSnapshot.any { Rect.intersects(it, buttonRect) }
            )

            val processed = WireframeEmitter().processForTesting(elements, summary.boundsSnapshot)
            val emittedButton = processed.single { it.type == WireframeType.Button }
            assertEquals(null, emittedButton.text)
            assertEquals(MaskDecision.EXPLICIT, emittedButton.maskDecision)
        }
    }
}
