package com.mixpanel.sessionreplaydemo.ui.maskingstandard

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.mixpanel.android.sessionreplay.MPSessionReplay
import com.mixpanel.sessionreplaydemo.databinding.ActivityMaskingStandardXmlTestBinding

class MaskingStandardXmlActivity : ComponentActivity() {

    private lateinit var binding: ActivityMaskingStandardXmlTestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMaskingStandardXmlTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTestCases()
    }

    private fun setupTestCases() {
        val sessionReplay = MPSessionReplay.getInstance()

        // Unmask all headers so they remain visible as labels
        sessionReplay?.addSafeView(binding.pageHeader)
        sessionReplay?.addSafeView(binding.scenario1Header)
        sessionReplay?.addSafeView(binding.scenario2Header)
        sessionReplay?.addSafeView(binding.scenario3Header)
        sessionReplay?.addSafeView(binding.scenario4Header)
        sessionReplay?.addSafeView(binding.scenario5Header)
        sessionReplay?.addSafeView(binding.scenario6Header)
        sessionReplay?.addSafeView(binding.scenario7Header)
        sessionReplay?.addSafeView(binding.scenario8Header)

        // Scenario 1: Sensitive Container
        // All leaf children should be individually masked
        sessionReplay?.addSensitiveView(binding.sensitiveContainer)

        // Scenario 2: Insensitive Container
        // Text+Image NOT masked, EditText ALWAYS masked
        sessionReplay?.addSafeView(binding.insensitiveContainer)

        // Scenario 3: Sensitive Container with Overflow
        // Overflow child should still be masked (follows logical tree parent)
        sessionReplay?.addSensitiveView(binding.sensitiveOverflowContainer)

        // Scenario 4: MixpanelMask + MixpanelUnmask
        // Outer=sensitive, Inner=safe
        // Expected: Header masked, Public note UNmasked, Footer masked
        sessionReplay?.addSensitiveView(binding.maskWithInnerUnmaskContainer)
        sessionReplay?.addSafeView(binding.innerUnmaskContainer)

        // Scenario 5: MixpanelMask + MixpanelUnmask with Overflow
        // Outer=sensitive, Overflow child=safe
        // Expected: Container masked, overflow child escapes mask (not masked)
        sessionReplay?.addSensitiveView(binding.maskWithUnmaskOverflowContainer)
        sessionReplay?.addSafeView(binding.unmaskOverflowChild)

        // Scenario 6: MixpanelUnmask + MixpanelMask
        // Outer=safe, Inner=sensitive
        // "Public" text is visible, "Private" text is masked
        sessionReplay?.addSafeView(binding.unmaskWithInnerMaskContainer)
        sessionReplay?.addSensitiveView(binding.innerMaskContainer)

        // Scenario 7: MixpanelUnmask + TextEntry
        // Labels visible, ALL EditTexts ALWAYS masked (security)
        sessionReplay?.addSafeView(binding.textFieldSecurityContainer)

        // Scenario 8: Auto-masking (no directives)
        // No explicit calls - relies entirely on auto-masking defaults
    }
}
