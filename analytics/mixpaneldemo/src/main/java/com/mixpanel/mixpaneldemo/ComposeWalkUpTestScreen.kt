package com.mixpanel.mixpaneldemo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

/**
 * Walk-Up-to-Clickable-Parent Test Screen (Compose)
 *
 * Validates that when a non-interactive leaf (e.g., Text inside a Button)
 * is tapped, the SDK walks up to the nearest clickable ancestor for $el_id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeWalkUpTestScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compose Walk-Up Test") },
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
            // 1. Basic walk-up
            item { SectionHeader("Basic Walk-Up") }

            item {
                TestDescription("Tap the text inside the button. \$el_id should be \"add_to_cart\" (from the Button's contentDescription), not a hash of the Text.")
            }
            item {
                Button(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "add_to_cart" }
                ) {
                    Text("Add to Cart")
                }
            }

            // 2. Nested clickables
            item { SectionHeader("Nested Clickables") }

            item {
                TestDescription("Tap \"Delete\" text. Walk-up should stop at the inner button (\"delete_item\"), NOT continue to the outer card (\"product_card\").")
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { }
                        .semantics { contentDescription = "product_card" }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Product Name", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { },
                            modifier = Modifier.semantics { contentDescription = "delete_item" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                }
            }

            // 3. Clickable with Image + Text
            item { SectionHeader("Clickable Container with Icon + Text") }

            item {
                TestDescription("Tap the icon or text. \$el_id should be \"checkout_action\" from the clickable Row.")
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { }
                        .semantics { contentDescription = "checkout_action" }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Proceed to Checkout", fontSize = 16.sp)
                }
            }

            // 4. Non-interactive text (no clickable ancestor)
            item { SectionHeader("No Clickable Ancestor") }

            item {
                TestDescription("Tap the text below. No clickable ancestor exists. \$el_id should be a hash fallback.")
            }
            item {
                Text(
                    "Terms and Conditions apply.",
                    modifier = Modifier.padding(8.dp),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            // 5. Leaf with its own identity
            item { SectionHeader("Leaf Has Own Identity") }

            item {
                TestDescription("Tap the text inside the button. Even though the Text has its own contentDescription (\"inner_label\"), walk-up still activates and takes the clickable parent's identity. \$el_id should be \"outer_button\".")
            }
            item {
                Button(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "outer_button" }
                ) {
                    Text(
                        "I have my own identity",
                        modifier = Modifier.semantics { contentDescription = "inner_label" }
                    )
                }
            }
        }
    }
}

@Composable
private fun TestDescription(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = Color.Gray,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
