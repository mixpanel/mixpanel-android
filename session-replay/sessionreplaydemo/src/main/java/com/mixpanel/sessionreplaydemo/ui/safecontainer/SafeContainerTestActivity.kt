package com.mixpanel.sessionreplaydemo.ui.safecontainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.android.sessionreplay.MPSessionReplay
import com.mixpanel.sessionreplaydemo.Constants
import com.mixpanel.sessionreplaydemo.databinding.ActivitySafeContainerTestBinding

class SafeContainerTestActivity : ComponentActivity() {

    private lateinit var binding: ActivitySafeContainerTestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Track Mixpanel event
        MixpanelAPI
            .getInstance(this, Constants.MIXPANEL_TOKEN, true)
            .track("Viewed Safe Container Test Screen")

        // Inflate XML layout
        binding = ActivitySafeContainerTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTestCases()
        setupComposeTestSection()
    }

    private fun setupTestCases() {
        val sessionReplay = MPSessionReplay.getInstance()

        // Test Case 1: Basic safe container with TextView and ImageView
        // Mark the container as safe - children (TextView, ImageView) should NOT be masked
        sessionReplay?.addSafeView(binding.safeContainerBasic)

        // Test Case 2: EditText in safe container
        // Mark container as safe, but EditText should ALWAYS be masked
        sessionReplay?.addSafeView(binding.safeContainerWithEditText)

        // Test Case 3: Nested safe containers
        // Only mark the outermost container - safe status should propagate to all children
        sessionReplay?.addSafeView(binding.nestedSafeContainerLevel1)
        // The TextView at level 3 should be protected by ancestor safe status
    }

    private fun setupComposeTestSection() {
        // Set up the Compose section for testing Compose safe containers
        binding.composeTestSection.setContent {
            MaterialTheme {
                SafeContainerTestScreen()
            }
        }
    }
}
