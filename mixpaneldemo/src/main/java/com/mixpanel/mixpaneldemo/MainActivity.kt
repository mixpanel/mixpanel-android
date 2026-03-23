// MainActivity.kt
@file:OptIn(ExperimentalMaterial3Api::class)

package com.mixpanel.mixpaneldemo

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.*
import com.mixpanel.android.eventbridge.MixpanelEventBridge
import com.mixpanel.mixpaneldemo.ui.theme.MixpanelandroidTheme
import kotlinx.coroutines.launch

const val MIXPANEL_PROJECT_TOKEN = "YOUR_PROJECT_TOKEN"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Collect events from the bridge - automatically cancelled when activity is destroyed
        lifecycleScope.launch {
            MixpanelEventBridge.events().collect { event ->
                Log.i("MixpanelEventBridge", "Event tracked: '${event.eventName}'")
                event.properties?.let {
                    Log.d("MixpanelEventBridge", "Properties: ${it.toString(2)}")
                }
            }
        }

        setContent {
            MixpanelandroidTheme {
                MyApp()
            }
        }
    }
}

@Composable
fun MyApp() {
    val navController = rememberNavController()
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        NavGraph(navController = navController, modifier = Modifier.padding(innerPadding))
    }
}





