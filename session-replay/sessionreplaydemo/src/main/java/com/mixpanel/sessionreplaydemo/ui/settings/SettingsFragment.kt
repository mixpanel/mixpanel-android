package com.mixpanel.sessionreplaydemo.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.mixpanel.android.sessionreplay.MPSessionReplay
import com.mixpanel.sessionreplaydemo.MainApplication
import com.mixpanel.sessionreplaydemo.R
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isInitialized by remember { mutableStateOf(MPSessionReplay.getInstance() != null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(
            onClick = {
                scope.launch {
                    try {
                        isInitialized = false
                        val instance = MainApplication.initializeMixpanel(context.applicationContext)
                        isInitialized = instance != null
                    } catch (e: Exception) {
                        isInitialized = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(context.getString(R.string.settings_reinitialize_session_replay))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = context.getString(R.string.settings_initialization_status_label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Text(
            text = if (isInitialized) {
                context.getString(R.string.settings_status_initialized)
            } else {
                context.getString(R.string.settings_status_not_initialized)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
