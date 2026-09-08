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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    private var xmlCounter = 0
    private val composeCounter = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xml_autocapture_test)
        title = "XML Autocapture Test"

        setupElIdResolutionTests()
        setupRageClickTests()
        setupPrivacyTests()
        setupMultiWindowTests()
        setupComposeInXmlTests()
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

    private fun setupComposeInXmlTests() {
        val xmlTextCounter = findViewById<TextView>(R.id.xml_text_counter)

        // Case 1: XML Button -> XML Text
        findViewById<android.widget.Button>(R.id.xml_btn_xml_text).setOnClickListener {
            xmlCounter++
            xmlTextCounter.text = "XML counter: $xmlCounter"
        }

        // Case 2: XML Button -> Compose Text
        findViewById<android.widget.Button>(R.id.xml_btn_compose_text).setOnClickListener {
            composeCounter.intValue++
        }

        // Cases 3 & 4 are Compose buttons inside the ComposeView
        findViewById<ComposeView>(R.id.compose_click_counter).setContent {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE8F5E9))
                    .padding(16.dp)
            ) {
                // Case 3: Compose Button -> XML Text
                Button(
                    onClick = {
                        xmlCounter++
                        xmlTextCounter.text = "XML counter: $xmlCounter"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "compose_btn_xml_text" }
                ) {
                    Text("3. Compose Btn -> XML Text")
                }

                // Case 4: Compose Button -> Compose Text
                Button(
                    onClick = { composeCounter.intValue++ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .semantics { contentDescription = "compose_btn_compose_text" }
                ) {
                    Text("4. Compose Btn -> Compose Text")
                }

                // Compose text counter (updated by cases 2 & 4)
                Text(
                    text = "Compose counter: ${composeCounter.intValue}",
                    fontSize = 16.sp,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { contentDescription = "compose_text_counter" }
                )
            }
        }
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
