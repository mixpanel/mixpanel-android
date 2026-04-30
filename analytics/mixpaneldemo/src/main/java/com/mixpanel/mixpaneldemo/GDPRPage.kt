@file:OptIn(ExperimentalMaterial3Api::class)

package com.mixpanel.mixpaneldemo


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mixpanel.android.mpmetrics.MixpanelAPI
import org.json.JSONObject


@Composable
fun GDPRPage(navController: NavHostController) {
    val showDialog = remember { mutableStateOf(false) }
    val dialogMessage = remember { mutableStateOf("") }
    val context = LocalContext.current
    val mixpanel = MixpanelAPI.getInstance(context, MIXPANEL_PROJECT_TOKEN, true)

    val gdprActions = listOf(
        Triple("Opt Out", "Event: \"Opt Out!\"", {
            println("Opting out")
            mixpanel.optOutTracking()
        }),
        Triple("Opt In", "Event: \"Opt In!\"", {
            println("Opting in")
            mixpanel.optInTracking()
        }),
        Triple("Opt In w DistinctId", "Event: \"Opt In w DistinctId!\"", {
            println("Opting in with Distinct ID")
            mixpanel.optInTracking("distinct_id")
        }),
        Triple("Opt In w DistinctId and Properties", "Event: \"Opt In w DistinctId and Properties!\"", {
            println("Opting in with Distinct ID and Properties")
            val properties = JSONObject(mapOf("property" to "value"))
            mixpanel.optInTracking("distinct_id", properties)
        }),
        Triple("Init with default opt-out", "Event: \"Init with default opt-out!\"", {
            println("Initializing with default opt-out")
            mixpanel.optOutTracking()
            // Additional initialization logic if required
        }),
        Triple("Init with default opt-in", "Event: \"Init with default opt-in!\"", {
            println("Initializing with default opt-in")
            mixpanel.optInTracking()
            // Additional initialization logic if required
        })
    )


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "GDPR Calls") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(gdprActions) { (title, message, action) ->
                TrackingButton(title, message, showDialog, dialogMessage, onButtonClick = action)
            }
        }

        if (showDialog.value) {
            AlertDialog(
                onDismissRequest = { showDialog.value = false },
                title = { Text(text = "GDPR Event") },
                text = { Text(dialogMessage.value) },
                confirmButton = {
                    Button(onClick = { showDialog.value = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
