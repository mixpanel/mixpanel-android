package com.mixpanel.android.mpmetrics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Compose test activity for walk-up-to-clickable-parent instrumentation tests.
 *
 * <p>Tests Compose's implicit walk-up behavior where findNodeAtPosition prefers
 * clickable parents over non-clickable children in the accessibility/semantics tree.
 */
class WalkUpComposeTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WalkUpTestContent() }
    }
}

@Composable
private fun WalkUpTestContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Basic walk-up: non-interactive Text inside clickable Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { }
                .semantics { contentDescription = "compose_card" }
                .padding(16.dp)
        ) {
            Text("Plain text inside clickable row")
        }

        // 2. Non-interactive Text with contentDescription inside clickable Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { }
                .semantics { contentDescription = "compose_parent_of_labeled" }
                .padding(16.dp)
        ) {
            Text(
                "Labeled text inside clickable row",
                modifier = Modifier.semantics { contentDescription = "compose_leaf_label" }
            )
        }

        // 3. Clickable leaf (Button) with identity inside clickable Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { }
                .semantics { contentDescription = "compose_parent_of_btn" }
        ) {
            Button(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "compose_inner_btn" }
            ) {
                Text("Inner button with identity")
            }
        }

        // 4. Non-interactive text with no clickable ancestor (hash fallback)
        Text(
            "Orphan text, no clickable ancestor",
            modifier = Modifier.padding(8.dp)
        )

        // 5. Nested clickables
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { }
                .semantics { contentDescription = "compose_outer" }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { }
                    .semantics { contentDescription = "compose_inner" }
                    .padding(8.dp)
            ) {
                Text("Leaf inside nested clickables")
            }
        }

        // 6. Clickable element with no identity inside clickable parent (hash fallback)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { }
                .semantics { contentDescription = "compose_parent_of_anon" }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { }
                    .padding(16.dp)
            ) {
                Text("Clickable row, no identity")
            }
        }

        // 7. Non-interactive text with contentDescription, no clickable ancestor
        Text(
            "Labeled orphan text",
            modifier = Modifier
                .padding(8.dp)
                .semantics { contentDescription = "compose_orphan_label" }
        )

        // 8. Deep nesting: clickable at top, 8 non-clickable wrapper Boxes, leaf Text
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { }
                .semantics { contentDescription = "compose_deep_parent" }
                .padding(16.dp)
        ) {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth()) {
                            Box(Modifier.fillMaxWidth()) {
                                Box(Modifier.fillMaxWidth()) {
                                    Box(Modifier.fillMaxWidth()) {
                                        Box(Modifier.fillMaxWidth()) {
                                            Text("Deep leaf in compose")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
