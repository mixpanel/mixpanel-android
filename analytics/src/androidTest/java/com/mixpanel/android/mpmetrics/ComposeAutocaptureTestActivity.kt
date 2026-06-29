package com.mixpanel.android.mpmetrics

import android.os.Bundle
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Compose test activity for autocapture instrumentation tests.
 * All composables are created programmatically with explicit semantics.
 */
class ComposeAutocaptureTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TestContent()
        }
    }
}

@Composable
private fun TestContent() {
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

        // Sensitive button - privacy test
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "mp-sensitive" }
        ) {
            Text("Sensitive (mp-sensitive)")
        }
    }
}
