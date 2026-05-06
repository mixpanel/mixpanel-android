package com.mixpanel.sessionreplaydemo.ui.imageheuristic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.sessionreplaydemo.Constants

class ImageHeuristicActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Track screen view
        MixpanelAPI
            .getInstance(this, Constants.MIXPANEL_TOKEN, true)
            .track("Viewed Image Heuristic Test Screen")

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ImageHeuristicScreen(
                        onBackPressed = { finish() }
                    )
                }
            }
        }
    }
}
