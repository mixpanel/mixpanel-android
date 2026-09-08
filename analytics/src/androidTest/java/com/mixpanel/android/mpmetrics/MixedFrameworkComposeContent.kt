package com.mixpanel.android.mpmetrics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Sets up Compose content inside a ComposeView for mixed-framework dead click testing
 * in [AutocaptureXmlTestActivity].
 *
 * Contains:
 * - A Compose button that updates the XML TextView counter
 * - A Compose button that updates the Compose text counter
 * - A Compose text displaying the counter value
 */
object MixedFrameworkComposeContent {

    @JvmStatic
    fun setContent(composeView: ComposeView, activity: AutocaptureXmlTestActivity) {
        val composeTextCounter = mutableIntStateOf(0)
        activity.composeTextCounter = composeTextCounter

        composeView.setContent {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Compose button that changes XML text
                Button(
                    onClick = {
                        val xmlCounter = activity.xmlTextCounter
                        val currentText = xmlCounter.text.toString()
                        val currentCount = currentText.substringAfter(": ").toIntOrNull() ?: 0
                        xmlCounter.text = "XML counter: ${currentCount + 1}"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "compose_btn_xml_text" }
                ) {
                    Text("Compose Btn -> XML Text")
                }

                // Compose button that changes Compose text
                Button(
                    onClick = {
                        composeTextCounter.intValue++
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .semantics { contentDescription = "compose_btn_compose_text" }
                ) {
                    Text("Compose Btn -> Compose Text")
                }

                // Compose text counter
                Text(
                    text = "Compose counter: ${composeTextCounter.intValue}",
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { contentDescription = "compose_text_counter" }
                )
            }
        }
    }
}
