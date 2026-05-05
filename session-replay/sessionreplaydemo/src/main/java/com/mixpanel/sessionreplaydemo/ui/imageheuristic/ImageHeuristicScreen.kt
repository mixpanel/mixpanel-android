package com.mixpanel.sessionreplaydemo.ui.imageheuristic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.mixpanel.android.sessionreplay.extensions.mpReplaySensitivePropKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageHeuristicScreen(onBackPressed: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image Heuristic Test") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Test Case 1: Explicit Image Role (Should be detected)
            TestCard(title = "1. Explicit Image Role", expectedResult = "SHOULD BE MASKED") {
                AsyncImage(
                    model = "https://picsum.photos/200/200?random=1",
                    contentDescription = "User Profile Image",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .semantics {
                            role = Role.Image
                        },
                    contentScale = ContentScale.Crop
                )
            }

            // Test Case 2: Content Description with Image Keywords (Should be detected)
            TestCard(
                title = "2. Image Keywords in Description",
                expectedResult = "SHOULD BE MASKED"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // "photo" keyword
                    AsyncImage(
                        model = "https://picsum.photos/200/200?random=2",
                        contentDescription = "User profile photo",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .clickable { /* Do nothing */ },
                        contentScale = ContentScale.Crop
                    )

                    // "avatar" keyword
                    AsyncImage(
                        model = "https://picsum.photos/200/200?random=3",
                        contentDescription = "User avatar",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .clickable { /* Do nothing */ },
                        contentScale = ContentScale.Crop
                    )

                    // "icon" keyword
                    AsyncImage(
                        model = "https://picsum.photos/200/200?random=4",
                        contentDescription = "Settings icon",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { /* Do nothing */ },
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Test Case 3: Heuristic Detection - Clickable without Text (Should be detected)
            TestCard(
                title = "3. Heuristic: Clickable No Text",
                expectedResult = "SHOULD BE MASKED"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Clickable image with no description
                    AsyncImage(
                        model = "https://picsum.photos/200/200?random=5",
                        contentDescription = null, // No description
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { /* Do nothing */ },
                        contentScale = ContentScale.Crop
                    )

                    // Another clickable image without text
                    SubcomposeAsyncImage(
                        model = "https://picsum.photos/200/200?random=6",
                        contentDescription = null,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { /* Do nothing */ },
                        loading = {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Test Case 4: Heuristic Detection - Has Content Description but No Text (Should be detected)
            TestCard(
                title = "4. Heuristic: Description but No Text",
                expectedResult = "SHOULD BE MASKED"
            ) {
                AsyncImage(
                    model = "https://picsum.photos/200/200?random=7",
                    contentDescription = "Generic clickable element",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            // Test Case 5: Should NOT be detected - Has Text
            TestCard(title = "5. Has Text Content", expectedResult = "SHOULD NOT BE MASKED") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Button with text
                    Button(onClick = { }) {
                        Text("Click Me")
                    }

                    // Clickable text
                    Text(
                        text = "Clickable Text",
                        modifier = Modifier
                            .clickable { /* Do nothing */ }
                            .padding(8.dp)
                    )
                }
            }

            // Test Case 6: Image with Text - Should be masked
            TestCard(title = "6. Image with Text Label", expectedResult = "SHOULD BE MASKED") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = "https://picsum.photos/200/200?random=8",
                        contentDescription = "Home",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Text("Home")
                }
            }

            // Test Case 7: Safe Container Test
            TestCard(
                title = "7. Safe Container with Image",
                expectedResult = "SHOULD NOT BE MASKED (if in safe container)"
            ) {
                Column(
                    modifier = Modifier.semantics {
                        set(mpReplaySensitivePropKey, false) // Mark as safe
                    }
                ) {
                    AsyncImage(
                        model = "https://picsum.photos/200/200?random=9",
                        contentDescription = "Safe account image",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .semantics {
                                role = Role.Image
                            },
                        contentScale = ContentScale.Crop
                    )
                    Text("This image is in a safe container")
                }
            }

            // Test Case 8: Complex Case - Multiple semantic properties
            TestCard(
                title = "8. Complex: Multiple Properties",
                expectedResult = "SHOULD BE MASKED"
            ) {
                AsyncImage(
                    model = "https://picsum.photos/200/200?random=10",
                    contentDescription = "User profile picture",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .semantics {
                            role = Role.Image
                            onClick(label = "View profile") { true }
                        },
                    contentScale = ContentScale.Crop
                )
            }

            // Test Case 9: Non-clickable with image description
            TestCard(
                title = "9. Non-Clickable with Image Description",
                expectedResult = "SHOULD BE MASKED"
            ) {
                AsyncImage(
                    model = "https://picsum.photos/200/200?random=11",
                    contentDescription = "Company logo",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            // Test Case 10: Gallery Image - Common UI element
            TestCard(title = "10. Gallery Image", expectedResult = "SHOULD BE MASKED") {
                AsyncImage(
                    model = "https://picsum.photos/200/200?random=12",
                    contentDescription = "Gallery thumbnail",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { /* Open gallery */ },
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun TestCard(
    title: String,
    expectedResult: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = expectedResult,
                fontSize = 12.sp,
                color = if (expectedResult.contains("SHOULD BE MASKED")) Color.Red else Color.Green
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
