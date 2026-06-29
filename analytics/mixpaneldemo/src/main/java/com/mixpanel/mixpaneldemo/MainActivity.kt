// MainActivity.kt
@file:OptIn(ExperimentalMaterial3Api::class)

package com.mixpanel.mixpaneldemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.*
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.mixpaneldemo.ui.theme.MixpanelandroidTheme

const val MIXPANEL_PROJECT_TOKEN = "YOUR_PROJECT_TOKEN"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MixpanelandroidTheme {
                MyApp()
            }
        }
    }
}

@Composable
fun MyApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val mixpanel = remember { MixpanelAPI.getInstance(context, MIXPANEL_PROJECT_TOKEN, true) }

    // Track screen navigation changes automatically
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    var previousRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentRoute) {
        currentRoute?.let { route ->
            // Track screen leave for previous screen
            previousRoute?.let { prevRoute ->
                mixpanel.autocapture.trackScreenLeave(prevRoute)
            }

            // Track screen view for current screen
            mixpanel.autocapture.trackScreenView(route)

            // Update previous route
            previousRoute = route
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        NavGraph(navController = navController, modifier = Modifier.padding(innerPadding))
    }
}





