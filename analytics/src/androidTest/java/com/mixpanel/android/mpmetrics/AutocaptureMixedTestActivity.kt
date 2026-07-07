package com.mixpanel.android.mpmetrics

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Mixed XML/Compose test activity for verifying cross-framework dead click detection.
 *
 * Layout: XML LinearLayout containing:
 *   - An XML TextView (ID_XML_LABEL) that displays status text
 *   - A ComposeView with a Compose Button whose onClick modifies the XML TextView
 *
 * This tests that ComposeUiChangeMonitor detects XML view changes caused by
 * a Compose button click (cross-framework dead click detection).
 */
class AutocaptureMixedTestActivity : ComponentActivity() {

    companion object {
        const val ID_XML_LABEL = 20001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        fun dpToPx(dp: Int) = Math.round(dp * density)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            val pad = dpToPx(16)
            setPadding(pad, pad, pad, pad)
        }

        val xmlLabel = TextView(this).apply {
            id = ID_XML_LABEL
            text = "Initial"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(xmlLabel)

        val composeView = ComposeView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(16)
            }
            setContent {
                ComposeUpdatesXmlButton(onClickUpdateXml = {
                    xmlLabel.text = "Updated by Compose"
                })
            }
        }
        layout.addView(composeView)

        setContentView(layout)
    }
}

@Composable
private fun ComposeUpdatesXmlButton(onClickUpdateXml: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClickUpdateXml,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics { contentDescription = "compose_updates_xml_btn" }
    ) {
        Text("Compose \u2192 XML update")
    }
}
