package com.mixpanel.sessionreplaydemo.ui.safecontainer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixpanel.android.sessionreplay.extensions.mpReplaySensitive

@Composable
fun SafeContainerTestScreen() {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Test Case 4a: Compose TextField in Safe Container (Always Masked)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF3E0), shape = RoundedCornerShape(8.dp))
                .padding(16.dp)
                .mpReplaySensitive(isSensitive = false) // Mark as safe container
        ) {
            Text(
                text = "✓ ALWAYS MASKED: TextField in safe container",
                fontSize = 12.sp,
                color = Color(0xFFE65100),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            var textFieldValue by remember { mutableStateOf("Sensitive input text") }
            TextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                label = { Text("Password or sensitive data") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFFFE0B2),
                    unfocusedContainerColor = Color(0xFFFFE0B2)
                )
            )

            Text(
                text = "TextField should be masked despite parent being safe",
                fontSize = 11.sp,
                color = Color(0xFFBF360C),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Spacer
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))

        // Test Case 4b: Compose Text in Safe Container (Protected)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp))
                .padding(16.dp)
                .mpReplaySensitive(isSensitive = false) // Mark as safe container
        ) {
            Text(
                text = "✓ NOT MASKED: Regular Text in safe container",
                fontSize = 12.sp,
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "This is regular text content that should NOT be masked because it's inside a safe container.",
                fontSize = 14.sp,
                color = Color.Black,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFC8E6C9), shape = RoundedCornerShape(4.dp))
                    .padding(12.dp)
            )

            Text(
                text = "Regular Text should be protected by safe container",
                fontSize = 11.sp,
                color = Color(0xFF1B5E20),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Spacer
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))

        // Test Case 4c: Mixed Content in Compose Safe Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF3E5F5), shape = RoundedCornerShape(8.dp))
                .padding(16.dp)
                .mpReplaySensitive(isSensitive = false) // Mark as safe container
        ) {
            Text(
                text = "✓ MIXED: TextField masked, Text protected",
                fontSize = 12.sp,
                color = Color(0xFF4A148C),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Safe label text",
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE1BEE7), shape = RoundedCornerShape(4.dp))
                    .padding(8.dp)
            )

            var mixedTextFieldValue by remember { mutableStateOf("Always masked") }
            TextField(
                value = mixedTextFieldValue,
                onValueChange = { mixedTextFieldValue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFCE93D8),
                    unfocusedContainerColor = Color(0xFFCE93D8)
                )
            )

            Text(
                text = "Text is protected, but TextField is always masked",
                fontSize = 11.sp,
                color = Color(0xFF6A1B9A),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
