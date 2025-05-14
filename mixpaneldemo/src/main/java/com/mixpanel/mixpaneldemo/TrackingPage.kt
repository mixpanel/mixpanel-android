@file:OptIn(ExperimentalMaterial3Api::class)

package com.mixpanel.mixpaneldemo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.mixpanel.android.mpmetrics.FeatureFlagData
import com.mixpanel.android.mpmetrics.FlagCompletionCallback
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.android.mpmetrics.MixpanelOptions
import org.json.JSONObject

@Composable
fun TrackingPage(navController: NavHostController) {
    val showDialog = remember { mutableStateOf(false) }
    val dialogMessage = remember { mutableStateOf("") }
    val context = LocalContext.current
    val flagsContext = JSONObject()
    flagsContext.put("context_key", "context_value")
    val mpOptionsBuilder = MixpanelOptions.Builder().featureFlagsEnabled(true).featureFlagsContext(flagsContext)
    val mpOptions = mpOptionsBuilder.build()
    val mixpanel = MixpanelAPI.getInstance(context, MIXPANEL_PROJECT_TOKEN, true, mpOptions)
    mixpanel.setEnableLogging(true)
    mixpanel.identify("demo_user")
//    mixpanel.setShouldGzipRequestPayload(true)
    mixpanel.setNetworkErrorListener(SimpleLoggingErrorListener())

    // Define some flag keys and fallbacks for testing
    val testFlagKeyString = "jared_android_test"
    val testFlagKeyInt = "test-int-flag"
    val testFlagKeyBool = "test-boolean-flag"
    val testFlagKeyMissing = "missing-flag"

    val stringFallback = FeatureFlagData(testFlagKeyString, "default-string") // Fallback Data object
    val intFallbackData = FeatureFlagData(testFlagKeyInt, 0)       // Fallback Data object
    val boolFallbackData = FeatureFlagData(testFlagKeyBool, false)    // Fallback Data object
    val missingFallbackData = FeatureFlagData("missing-fallback-key", "missing-fallback-val")


    val trackingActions = listOf(
        Triple("Track w/o Properties" ,"Event: \"Track Event!\"", {  println("Tracking without properties")
            mixpanel.track("Track w/o Properties")
            mixpanel.flush()}),
        Triple("Track w Properties", "Event: \"Track Event with Properties!\"", {
            println("Tracking with properties")
            val properties = JSONObject(mapOf("key" to "value"))
            mixpanel.track("Track w Properties",  properties)
            mixpanel.flush()
        }),
        Triple("Time Event 5secs", "Event: \"Timed Event after 5 secs!\"", {
            println("Timing event for 5 seconds")
            mixpanel.timeEvent("Time Event 5secs")
            // Assume some delay logic here if needed
            mixpanel.flush()
        }),
        Triple("Clear Timed Events", "Event: \"Timed Events Cleared!\"", {
            println("Clearing timed events")
            mixpanel.clearTimedEvents()
            mixpanel.flush()
        }),
        Triple("Get Current SuperProperties", "Event: \"Get Current SuperProperties!\"", {
            println("Getting current super properties")
            val superProperties = mixpanel.superProperties
            println(superProperties.toString())
        }),
        Triple("Clear SuperProperties", "Event: \"SuperProperties Cleared!\"", {
            println("Clearing super properties")
            mixpanel.clearSuperProperties()
            mixpanel.flush()
        }),
        Triple("Register SuperProperties", "Event: \"Register SuperProperties!\"", {
            println("Registering super properties")
            val superProperties = JSONObject(mapOf("property" to "value"))
            mixpanel.registerSuperProperties(superProperties)
            mixpanel.flush()
        }),
        Triple("Register SuperProperties Once", "Event: \"Register SuperProperties Once!\"", {
            println("Registering super properties once")
            val superPropertiesOnce = JSONObject(mapOf("property_once" to "value_once"))
            mixpanel.registerSuperPropertiesOnce(superPropertiesOnce)
            mixpanel.flush()
        }),
        Triple("Register SP Once w Default Value", "Event: \"Register SP Once w Default Value!\"", {
            println("Registering super properties once with default value")
            val defaultProperties = JSONObject(mapOf("default_property" to "default_value"))
            mixpanel.registerSuperPropertiesOnce(defaultProperties)
            mixpanel.flush()
        }),
        Triple("Unregister SuperProperty", "Event: \"Unregister SuperProperty!\"", {
            println("Unregistering super property")
            mixpanel.unregisterSuperProperty("property_to_unregister")
            mixpanel.flush()
        }),
        // --- Feature Flag Actions ---
        Triple("Load Flags", "Action: Loading flags async...", {
            println(">>> Action: Loading flags")
            mixpanel.loadFlags()
            // You might want to call getFeature async shortly after to see results
        }),
        Triple("Are Flags Ready?", "Action: Checking areFeaturesReady...", {
            println(">>> Action: Checking areFeaturesReady")
            val ready = mixpanel.areFeaturesReady()
            println("areFeaturesReady Result: $ready")
            // Update dialog or state if needed based on 'ready'
            dialogMessage.value = "areFeaturesReady() returned: $ready"
            showDialog.value = true
        }),
        Triple("Get Feature Sync", "Action: Getting flag '$testFlagKeyString' sync...", {
            println(">>> Action: Getting feature sync '$testFlagKeyString'")
            // Note: Fallback for getFeatureSync is FeatureFlagData type
            val featureFlagData = mixpanel.getFeatureSync(testFlagKeyString, stringFallback)
            println("Sync Get Feature Result for '$testFlagKeyString':")
            println("  Key: ${featureFlagData.key}")
            println("  Value: ${featureFlagData.value}")
            // Also try a missing key
            val missingData = mixpanel.getFeatureSync(testFlagKeyMissing, missingFallbackData)
            println("Sync Get Feature Result for '$testFlagKeyMissing':")
            println("  Key: ${missingData.key}")
            println("  Value: ${missingData.value}")
        }),
        // --- NEW ---
        Triple("Get Feature Async", "Action: Getting flag '$testFlagKeyString' async...", {
            println(">>> Action: Getting feature async '$testFlagKeyString'")
            mixpanel.getFeature(testFlagKeyString, stringFallback,
                FlagCompletionCallback { result -> // Use SAM conversion for callback
                    println("Async Get Feature Result for '$testFlagKeyString':")
                    // FeatureFlagData result itself should not be null, but value inside can be
                    println("  Key: ${result.key}")
                    println("  Value: ${result.value}")
                })
            // Also try a missing key async
            mixpanel.getFeature(testFlagKeyMissing, missingFallbackData,
                FlagCompletionCallback { result ->
                    println("Async Get Feature Result for '$testFlagKeyMissing':")
                    println("  Key: ${result.key}")
                    println("  Value: ${result.value}")
                })
        }),
        Triple("Get Flag Data Sync", "Action: Getting data for '$testFlagKeyInt' sync...", {
            println(">>> Action: Getting feature data sync '$testFlagKeyInt'")
            val fallbackValue = -1 // Fallback for getFeatureDataSync is the value type
            val value = mixpanel.getFeatureDataSync(testFlagKeyInt, fallbackValue)
            println("Sync Get Data Result for '$testFlagKeyInt': $value (Type: ${value?.javaClass?.simpleName})")
            // Also try a missing key
            val missingValue = mixpanel.getFeatureDataSync(testFlagKeyMissing, "missing_default")
            println("Sync Get Data Result for '$testFlagKeyMissing': $missingValue")
        }),
        Triple("Get Flag Data Async", "Action: Getting data for '$testFlagKeyInt' async...", {
            println(">>> Action: Getting feature data async '$testFlagKeyInt'")
            val fallbackValue = -1
            mixpanel.getFeatureData(testFlagKeyInt, fallbackValue,
                FlagCompletionCallback { value -> // SAM conversion
                    println("Async Get Data Result for '$testFlagKeyInt': $value (Type: ${value?.javaClass?.simpleName})")
                })
            // Also try a missing key
            mixpanel.getFeatureData(testFlagKeyMissing, "missing_default",
                FlagCompletionCallback { value ->
                    println("Async Get Data Result for '$testFlagKeyMissing': $value")
                })
        }),
        Triple("Is Enabled Sync", "Action: Checking '$testFlagKeyBool' sync...", {
            println(">>> Action: Checking feature enabled sync '$testFlagKeyBool'")
            val fallbackValue = false // Fallback for isEnabledSync is boolean
            val isEnabled = mixpanel.isFeatureEnabledSync(testFlagKeyBool, fallbackValue)
            println("Sync Is Enabled Result for '$testFlagKeyBool': $isEnabled")
            // Also try a missing key
            val isMissingEnabled = mixpanel.isFeatureEnabledSync(testFlagKeyMissing, true) // Use different fallback
            println("Sync Is Enabled Result for '$testFlagKeyMissing': $isMissingEnabled")
            // Try on a non-boolean flag
            val isIntEnabled = mixpanel.isFeatureEnabledSync(testFlagKeyInt, true) // Should return fallback
            println("Sync Is Enabled Result for '$testFlagKeyInt': $isIntEnabled")
        }),
        Triple("Is Enabled Async", "Action: Checking '$testFlagKeyBool' async...", {
            println(">>> Action: Checking feature enabled async '$testFlagKeyBool'")
            val fallbackValue = false
            mixpanel.isFeatureEnabled(testFlagKeyBool, fallbackValue,
                FlagCompletionCallback { isEnabled -> // SAM conversion
                    println("Async Is Enabled Result for '$testFlagKeyBool': $isEnabled")
                })
            // Also try a missing key
            mixpanel.isFeatureEnabled(testFlagKeyMissing, true, // Use different fallback
                FlagCompletionCallback { isEnabled ->
                    println("Async Is Enabled Result for '$testFlagKeyMissing': $isEnabled")
                })
            // Try on a non-boolean flag
            mixpanel.isFeatureEnabled(testFlagKeyInt, true, // Should return fallback
                FlagCompletionCallback { isEnabled ->
                    println("Async Is Enabled Result for '$testFlagKeyInt': $isEnabled")
                })
        })
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Tracking Calls") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            items(trackingActions) { (title, message, action) ->
                TrackingButton(title, message, showDialog, dialogMessage, onButtonClick = action)
            }
        }

        if (showDialog.value) {
            AlertDialog(
                onDismissRequest = { showDialog.value = false },
                title = { Text(text = "Tracking Event") },
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
