package com.mixpanel.sessionreplaydemo.ui.jokes

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.sessionreplaydemo.Constants

class JokesActivity : ComponentActivity() {

    private val viewModel: JokesViewModel by viewModels()
    private val handler = Handler(Looper.getMainLooper())
    private var currentProgress = 0

    // Runnable to update progress indicator continuously
    private val progressRunnable = object : Runnable {
        override fun run() {
            currentProgress = (currentProgress + 1) % 101
            viewModel.updateProgress(currentProgress)

            // Schedule next update (faster for more UI refreshes)
            handler.postDelayed(this, 50) // Update every 50ms for smooth animation
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Track Mixpanel event
        MixpanelAPI
            .getInstance(this, Constants.MIXPANEL_TOKEN, true)
            .track("Viewed Jokes Screen (Compose)")

        // Start the progress animation
        handler.post(progressRunnable)

        setContent {
            MaterialTheme {
                Surface(
                    color = MaterialTheme.colorScheme.background
                ) {
                    JokesScreen(
                        viewModel = viewModel,
                        onBackPressed = { finish() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the progress animation when activity is destroyed
        handler.removeCallbacks(progressRunnable)
    }
}
