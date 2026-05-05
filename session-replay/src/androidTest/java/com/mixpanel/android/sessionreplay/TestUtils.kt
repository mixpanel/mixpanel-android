package com.mixpanel.android.sessionreplay

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario

/**
 * Helper to run test code with a view attached to the activity's window.
 * This ensures getGlobalVisibleRect() returns true for visible views.
 */
fun ActivityScenario<ShellActivity>.withAttachedView(
    viewBuilder: (activity: ShellActivity) -> View,
    test: (view: View) -> Unit
) {
    onActivity { activity ->
        val view = viewBuilder(activity)
        val container = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(view)
        activity.setContentView(container)

        // Force layout pass to ensure views have valid bounds
        container.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, 1080, 1920)

        test(view)
    }
}
