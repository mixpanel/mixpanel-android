package com.mixpanel.android.sessionreplay

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import com.mixpanel.android.mpmetrics.MixpanelAPI
import org.json.JSONObject

/**
 * Test activity for event trigger instrumentation tests.
 * Creates buttons programmatically to fire test events via MixpanelAPI.
 */
class EventTriggerTestActivity : Activity() {

    lateinit var eventTriggerTestButton: Button
    lateinit var productViewTestButton: Button
    lateinit var productViewFailTestButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val mixpanel = MixpanelAPI.getInstance(this, TEST_TOKEN, true)

        // Test 1: Simple event trigger (no property filter)
        eventTriggerTestButton = Button(this).apply {
            text = "Test Event Trigger"
            setOnClickListener {
                mixpanel.track("Test Event Trigger Clicked")
            }
        }
        layout.addView(eventTriggerTestButton)

        // Test 2: Event with property filter - passes (age=20 > 18)
        productViewTestButton = Button(this).apply {
            text = "Product View (age=20)"
            setOnClickListener {
                val properties = JSONObject().apply {
                    put("age", 20)
                }
                mixpanel.track("Product View", properties)
            }
        }
        layout.addView(productViewTestButton)

        // Test 3: Event with property filter - fails (age=15 < 18)
        productViewFailTestButton = Button(this).apply {
            text = "Product View (age=15)"
            setOnClickListener {
                val properties = JSONObject().apply {
                    put("age", 15)
                }
                mixpanel.track("Product View", properties)
            }
        }
        layout.addView(productViewFailTestButton)

        setContentView(layout)
    }

    companion object {
        const val TEST_TOKEN = "test_token_event_trigger"
    }
}
