package com.mixpanel.android.mpmetrics

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Compose test activity for autocapture instrumentation tests.
 * All composables are created programmatically with explicit semantics.
 *
 * Includes mixed-framework elements (AndroidView inside Compose) for cross-framework
 * dead click testing.
 */
class AutocaptureComposeTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TestContent()
        }
    }
}

@Composable
private fun TestContent() {
    val composeTextCounter = remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Rule 1 button - contentDescription priority
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "compose_rule1_btn" }
        ) {
            Text("Rule 1 - contentDescription")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Rule 2 button - testTag fallback (no contentDescription)
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .testTag("compose_rule2_btn")
        ) {
            Text("Rule 2 - testTag")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Rule 3 button - hash fallback (no contentDescription, no testTag)
        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Rule 3 - hash fallback")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dead click button - onClick does nothing (no UI change)
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "compose_dead_btn" }
        ) {
            Text("Dead Button (no effect)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Rage zone - clickable area
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clickable {}
                .semantics { contentDescription = "compose_rage_zone" }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ============ Mixed-Framework Dead Click Test Elements ============

        // XML button + XML text counter embedded via AndroidView
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    val dp8 = (8 * ctx.resources.displayMetrics.density).toInt()

                    // XML button that changes XML text
                    val xmlTextCounter = TextView(ctx).apply {
                        text = "XML counter: 0"
                        this.contentDescription = "xml_text_counter_in_compose"
                        setPadding(0, dp8, 0, 0)
                    }

                    val xmlBtnXmlText = android.widget.Button(ctx).apply {
                        text = "XML Btn -> XML Text"
                        this.contentDescription = "xml_btn_xml_text_in_compose"
                        setOnClickListener {
                            val current = xmlTextCounter.text.toString()
                                .substringAfter(": ").toIntOrNull() ?: 0
                            xmlTextCounter.text = "XML counter: ${current + 1}"
                        }
                    }
                    addView(xmlBtnXmlText, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ))
                    addView(xmlTextCounter, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ))

                    // XML button that changes Compose text
                    val xmlBtnComposeText = android.widget.Button(ctx).apply {
                        text = "XML Btn -> Compose Text"
                        this.contentDescription = "xml_btn_compose_text_in_compose"
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp8 }
                        setOnClickListener {
                            composeTextCounter.intValue++
                        }
                    }
                    addView(xmlBtnComposeText)
                }
            }
        )

        // Compose button that changes XML text (via AndroidView update)
        // We use a separate AndroidView to hold the XML counter that this button updates
        val xmlCounterFromCompose = remember { mutableIntStateOf(0) }

        Button(
            onClick = {
                xmlCounterFromCompose.intValue++
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .semantics { contentDescription = "compose_btn_xml_text_in_compose" }
        ) {
            Text("Compose Btn -> XML Text")
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            factory = { ctx ->
                TextView(ctx).apply {
                    this.contentDescription = "xml_text_from_compose_btn"
                }
            },
            update = { textView ->
                textView.text = "XML counter (from Compose): ${xmlCounterFromCompose.intValue}"
            }
        )

        // Compose button that changes Compose text
        Button(
            onClick = {
                composeTextCounter.intValue++
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .semantics { contentDescription = "compose_btn_compose_text_in_compose" }
        ) {
            Text("Compose Btn -> Compose Text")
        }

        // Compose text counter
        Text(
            text = "Compose counter: ${composeTextCounter.intValue}",
            modifier = Modifier
                .padding(top = 4.dp)
                .semantics { contentDescription = "compose_text_counter_in_compose" }
        )
    }
}
