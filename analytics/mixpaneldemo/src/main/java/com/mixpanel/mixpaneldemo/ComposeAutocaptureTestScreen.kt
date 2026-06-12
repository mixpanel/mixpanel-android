package com.mixpanel.mixpaneldemo

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

/**
 * Jetpack Compose Autocapture Test Screen
 *
 * Tests $mp_click, $mp_rage_click, and $mp_dead_click on Compose elements,
 * and verifies $el_id resolution via Compose's accessibility semantics:
 * 1. semantics { contentDescription } (if non-empty)
 * 2. testTag / resource ID mapping
 * 3. ClassName_view_<hashCode>
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeAutocaptureTestScreen(navController: NavHostController) {
    val context = LocalContext.current
    var tapCount by remember { mutableIntStateOf(0) }
    var switchOn by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf("") }
    var passwordValue by remember { mutableStateOf("") }
    var sliderValue by remember { mutableFloatStateOf(0.5f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compose Autocapture Test") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // $el_id Resolution
            item { SectionHeader("\$el_id Resolution") }

            item {
                Button(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "compose_rule1" }
                ) {
                    Text("Rule 1 - semantics contentDescription")
                }
            }

            item {
                Button(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compose_rule2_btn")
                    // No contentDescription
                ) {
                    Text("Rule 2 - testTag (no contentDescription)")
                }
            }

            item {
                Button(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth()
                    // No semantics at all
                ) {
                    Text("Rule 3 - No semantics (hash fallback)")
                }
            }

            item {
                Button(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "compose_desc_wins" }
                        .testTag("ignored_tag")
                ) {
                    Text("Rule 1 Wins - contentDescription + testTag")
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Color.Blue.copy(alpha = 0.1f))
                        .clickable { }
                        .semantics { contentDescription = "compose_box" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Clickable Box (non-Button)")
                }
            }

            // Dead Click
            item { SectionHeader("Dead Click") }

            item {
                Button(
                    onClick = { }, // Empty - no UI change, only ripple
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "compose_dead_btn" }
                ) {
                    Text("Dead Button (ripple only) -> \$mp_dead_click")
                }
            }

            // Rage Click
            item { SectionHeader("Rage Click - tap 4+ times") }

            item {
                Button(
                    onClick = { }, // Does nothing visible
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .semantics { contentDescription = "compose_rage_btn" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red.copy(alpha = 0.3f)
                    )
                ) {
                    Text("Rage Zone - tap rapidly")
                }
            }

            item {
                Button(
                    onClick = { tapCount++ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "compose_rage_click_btn" }
                ) {
                    Text("Click + Rage - tap 4x (both events): ${tapCount}x")
                }
            }

            // Excluded Controls
            item { SectionHeader("Excluded Controls (no dead click)") }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Switch - no dead click")
                    Switch(checked = switchOn, onCheckedChange = { switchOn = it })
                }
            }

            item {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text("TextField - no dead click, text not captured") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = passwordValue,
                    onValueChange = { passwordValue = it },
                    label = { Text("Password - \$el_text absent") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(value = sliderValue, onValueChange = { sliderValue = it })
                    Text(
                        "Slider - no dead click (value: ${(sliderValue * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Privacy
            item { SectionHeader("Privacy - zero events captured") }

            item {
                Button(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "mp-sensitive" }
                ) {
                    Text("mp-sensitive (no events)")
                }
            }

            item {
                Button(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "mp-no-track" }
                ) {
                    Text("mp-no-track (no events)")
                }
            }

            // Navigate to XML Test
            item { SectionHeader("XML Views Test") }

            item {
                Button(
                    onClick = {
                        context.startActivity(Intent(context, XmlAutocaptureTestActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("Open XML Test Activity")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}
