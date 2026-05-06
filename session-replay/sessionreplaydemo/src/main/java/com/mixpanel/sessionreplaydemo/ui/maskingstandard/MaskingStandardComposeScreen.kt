package com.mixpanel.sessionreplaydemo.ui.maskingstandard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixpanel.android.sessionreplay.extensions.mpReplaySensitive
import com.mixpanel.sessionreplaydemo.R

@Composable
fun MaskingStandardComposeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Masking Standard Tests (Compose)",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "To see expected results, configure app with Image-only auto-masking.",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(20.dp))

        Scenario1SensitiveContainer()
        Scenario2InsensitiveContainer()
        Scenario3SensitiveContainerWithOverflow()
        Scenario4MaskWithInnerUnmask()
        Scenario5MaskWithInnerUnmaskOverflow()
        Scenario6UnmaskWithInnerMask()
        Scenario7UnmaskWithTextEntry()
        Scenario8AutoMasking()

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun ScenarioHeader(number: Int, title: String, description: String) {
    Column(modifier = Modifier.mpReplaySensitive(false)) {
        Text(
            text = "$number. $title",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = description,
            fontSize = 11.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(6.dp))
    }
}

// ============================================================
// Scenario 1: Sensitive Container
// MixpanelMask wrapping a container with text, image, and input
// Expected: All leaf children individually masked
// ============================================================
@Composable
private fun Scenario1SensitiveContainer() {
    ScenarioHeader(
        1,
        "Sensitive Container",
        "mpReplaySensitive(true) on container - All children should be masked"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .mpReplaySensitive(true)
            .background(Color(0xFFFFEBEE))
            .padding(12.dp)
    ) {
        Text(text = "Name: Tyler", fontSize = 14.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "Profile image",
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        var email by remember { mutableStateOf("tyler@example.com") }
        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}

// ============================================================
// Scenario 2: Insensitive Container
// MixpanelUnmask wrapping a container - overrides auto-masking
// Expected: Text+Image NOT masked, TextField ALWAYS masked
// ============================================================
@Composable
private fun Scenario2InsensitiveContainer() {
    ScenarioHeader(
        2,
        "Insensitive Container",
        "mpReplaySensitive(false) on container - Text/Image NOT masked, TextField ALWAYS masked"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .mpReplaySensitive(false)
            .background(Color(0xFFE8F5E9))
            .padding(12.dp)
    ) {
        Text(text = "Public label", fontSize = 14.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Profile image",
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Visible caption", fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        var password by remember { mutableStateOf("password123") }
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}

// ============================================================
// Scenario 3: Sensitive Container with Overflow
// MixpanelMask on container, child overflows outside bounds
// Overflow child is NOT currently masked — only auto-mask rules
// may apply. May change to align across platforms.
// ============================================================
@Composable
private fun Scenario3SensitiveContainerWithOverflow() {
    ScenarioHeader(
        3,
        "Sensitive Container with Overflow",
        "mpReplaySensitive(true) - Overflow Image IS masked (auto-mask), Text is NOT masked. May change to align across platforms."
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        Box(
            modifier = Modifier
                .size(150.dp, 80.dp)
                .mpReplaySensitive(true)
                .background(Color(0xFF2196F3))
        ) {
            Text(
                text = "Inside",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            Box(
                modifier = Modifier
                    .offset(x = 150.dp, y = 15.dp)
                    .size(130.dp, 50.dp)
                    .background(Color(0xFFF44336)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Outside",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

// ============================================================
// Scenario 4: MixpanelMask + MixpanelUnmask
// Outer: sensitive, Inner child: safe
// Expected: Header masked, Public note UNmasked, Footer masked
// ============================================================
@Composable
private fun Scenario4MaskWithInnerUnmask() {
    ScenarioHeader(
        4,
        "MixpanelMask + MixpanelUnmask",
        "Sensitive outer + Safe inner - Inner child should be UNmasked"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .mpReplaySensitive(true)
            .background(Color(0xFFFFEBEE))
            .padding(12.dp)
    ) {
        Text(
            text = "Header (masked)",
            fontSize = 14.sp,
            modifier = Modifier.padding(6.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .mpReplaySensitive(false)
                .background(Color(0xFFE8F5E9))
                .padding(6.dp)
        ) {
            Text(
                text = "Public note (unmask inside mask)",
                fontSize = 14.sp
            )
        }
        Text(
            text = "Footer (masked)",
            fontSize = 14.sp,
            modifier = Modifier.padding(6.dp)
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}

// ============================================================
// Scenario 5: MixpanelMask + MixpanelUnmask with Overflow
// MixpanelMask container with MixpanelUnmask child that overflows
// outside the mask's bounds. Overflow child is NOT currently
// masked — only auto-mask rules may apply. May change to align
// across platforms.
// ============================================================
@Composable
private fun Scenario5MaskWithInnerUnmaskOverflow() {
    ScenarioHeader(
        5,
        "MixpanelMask + MixpanelUnmask with Overflow",
        "Sensitive outer + Safe inner overflowing - Overflow Image IS masked (auto-mask), Text is NOT masked. May change to align across platforms."
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        Box(
            modifier = Modifier
                .size(150.dp, 80.dp)
                .mpReplaySensitive(true)
                .background(Color(0xFF2196F3))
        ) {
            Text(
                text = "Masked",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            Box(
                modifier = Modifier
                    .offset(x = 150.dp, y = 15.dp)
                    .size(130.dp, 50.dp)
                    .mpReplaySensitive(false)
                    .background(Color(0xFF4CAF50)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Unmasked",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

// ============================================================
// Scenario 6: MixpanelUnmask + MixpanelMask
// Outer: safe, Inner: sensitive
// Expected: "Public" visible, "Private" masked
// ============================================================
@Composable
private fun Scenario6UnmaskWithInnerMask() {
    ScenarioHeader(
        6,
        "MixpanelUnmask + MixpanelMask",
        "Safe outer + Sensitive inner - Inner child IS masked"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .mpReplaySensitive(false)
            .background(Color(0xFFE8F5E9))
            .padding(12.dp)
    ) {
        Text(
            text = "Public (visible)",
            fontSize = 14.sp,
            modifier = Modifier.padding(6.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .mpReplaySensitive(true)
                .background(Color(0xFFFFEBEE))
                .padding(6.dp)
        ) {
            Text(
                text = "Private (masked)",
                fontSize = 14.sp
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

// ============================================================
// Scenario 7: MixpanelUnmask + TextEntry
// Safe container with labels + TextFields
// Expected: Labels NOT masked, ALL TextFields ALWAYS masked
// ============================================================
@Composable
private fun Scenario7UnmaskWithTextEntry() {
    ScenarioHeader(
        7,
        "MixpanelUnmask + TextEntry",
        "Safe container - Labels visible, ALL TextFields ALWAYS masked (security)"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .mpReplaySensitive(false)
            .background(Color(0xFFE8F5E9))
            .padding(12.dp)
    ) {
        Text(
            text = "Username",
            fontSize = 14.sp,
            modifier = Modifier.padding(6.dp)
        )
        var username by remember { mutableStateOf("tyler@example.com") }
        TextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Password",
            fontSize = 14.sp,
            modifier = Modifier.padding(6.dp)
        )
        var pw by remember { mutableStateOf("secret123") }
        TextField(
            value = pw,
            onValueChange = { pw = it },
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}

// ============================================================
// Scenario 8: Auto-masking (No Explicit Directives)
// No mpReplaySensitive calls - just default auto-masking
// Expected: Text=masked, Image=masked, TextField=masked
// ============================================================
@Composable
private fun Scenario8AutoMasking() {
    ScenarioHeader(
        8,
        "Auto-masking (No Explicit Directives)",
        "Default behavior - Text, Image, TextField all auto-masked"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(12.dp)
    ) {
        Text(text = "Hello World", fontSize = 14.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "Auto-masked image",
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        var search by remember { mutableStateOf("search query") }
        TextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search") },
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}
