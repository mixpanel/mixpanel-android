package com.mixpanel.android.mpmetrics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Minimal Compose activity for `$el_id` resolution tests.
 *
 * Deliberately separate from [AutocaptureComposeTestActivity]: that activity's elements are laid out
 * in one long scrolling column and several tests tap them by coordinate, so inserting anything into
 * it shifts later elements off-screen and their injected touches start landing outside the app's
 * window.
 *
 * A single button, above the fold, carrying both a `testTag` and a `contentDescription` — the pair
 * that pins the Compose priority order.
 */
class ElementIdComposeTestActivity : ComponentActivity() {

    companion object {
        const val TEST_TAG = "compose_both_btn"
        const val CONTENT_DESCRIPTION = "Compose Both Label"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TestContent() }
    }
}

@Composable
private fun TestContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = ElementIdComposeTestActivity.CONTENT_DESCRIPTION
                }
                .testTag(ElementIdComposeTestActivity.TEST_TAG)
        ) {
            Text("Both - testTag + contentDescription")
        }
    }
}
