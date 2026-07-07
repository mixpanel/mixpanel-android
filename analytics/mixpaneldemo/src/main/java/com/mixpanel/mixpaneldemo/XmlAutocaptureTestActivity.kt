package com.mixpanel.mixpaneldemo

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * XML Views Autocapture Test Screen
 *
 * Tests $mp_click, $mp_rage_click, and $mp_dead_click on traditional XML View elements,
 * and verifies $el_id resolution across all three Android rules:
 * 1. contentDescription (if non-empty)
 * 2. Resource ID name (R.id.xxx)
 * 3. ClassName_view_<hashCode>
 */
class XmlAutocaptureTestActivity : AppCompatActivity() {

    private var tapCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xml_autocapture_test)
        title = "XML Autocapture Test"

        setupElIdResolutionTests()
        setupRageClickTests()
        setupPrivacyTests()
        setupMultiWindowTests()
    }

    private fun setupElIdResolutionTests() {
        // Rule 1 - contentDescription set in XML (rule1_btn)
        findViewById<Button>(R.id.rule1_btn).setOnClickListener {
            // Click action - shows $mp_click
        }

        // Rule 2 - resource ID name, no contentDescription (rule2_btn)
        findViewById<Button>(R.id.rule2_btn).setOnClickListener {
            // Click action - shows $mp_click
        }

        // Rule 3 - remove contentDescription programmatically for hash fallback
        val rule3Btn = findViewById<Button>(R.id.rule3_btn)
        rule3Btn.contentDescription = null
        rule3Btn.setOnClickListener {
            // Click action - shows $mp_click with hash-based $el_id
        }

        // Rule 1 wins over Rule 2 (has both contentDescription and ID)
        findViewById<Button>(R.id.also_has_id).setOnClickListener {
            // Click action - $el_id should be "desc_wins"
        }

        // Clickable container (non-button)
        findViewById<LinearLayout>(R.id.custom_layout).setOnClickListener {
            // Click action
        }

        // ImageView with click listener
        findViewById<ImageView>(R.id.tap_image).setOnClickListener {
            // Click action
        }
    }

    private fun setupRageClickTests() {
        // Dead button - intentionally NO listener
        // R.id.dead_xml_btn has no setOnClickListener - should trigger $mp_dead_click

        // Rage zone - listener that produces no UI change
        findViewById<View>(R.id.rage_zone).setOnClickListener {
            // Does nothing visible - good for rage click testing
        }

        // Rage + Click - updates counter TextView (UI change)
        val tapCounter = findViewById<TextView>(R.id.tap_counter)
        findViewById<Button>(R.id.rage_and_click_btn).setOnClickListener {
            tapCount++
            tapCounter.text = "Tap count: $tapCount"
            // Each tap: $mp_click
            // 4th+ tap within 1s: also $mp_rage_click
        }
    }

    private fun setupPrivacyTests() {
    }

    private fun setupMultiWindowTests() {
        // AlertDialog
        findViewById<Button>(R.id.show_alert_dialog_btn).setOnClickListener {
            val dialog = AlertDialog.Builder(this)
                .setTitle("Test Alert Dialog")
                .setMessage("Tap buttons inside this dialog")
                .setPositiveButton("Confirm", null)
                .setNegativeButton("Cancel", null)
                .show()

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.contentDescription = "xml_alert_confirm"
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.contentDescription = "xml_alert_cancel"
        }

        // BottomSheetDialog
        findViewById<Button>(R.id.show_bottom_sheet_btn).setOnClickListener {
            val bottomSheet = BottomSheetDialog(this)

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 48, 48, 48)
            }

            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }

            for (i in 1..3) {
                val btn = Button(this).apply {
                    text = "Bottom Sheet Action $i"
                    contentDescription = "xml_sheet_action_$i"
                }
                layout.addView(btn, layoutParams)
            }

            val closeBtn = Button(this).apply {
                text = "Close"
                contentDescription = "xml_sheet_close"
                setOnClickListener { bottomSheet.dismiss() }
            }
            layout.addView(closeBtn, layoutParams)

            bottomSheet.setContentView(layout)
            bottomSheet.show()
        }

        // PopupMenu
        findViewById<Button>(R.id.show_popup_menu_btn).setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add(0, 1, 0, "Option 1")
            popup.menu.add(0, 2, 1, "Option 2")
            popup.menu.add(0, 3, 2, "Option 3")
            popup.show()
        }
    }
}
