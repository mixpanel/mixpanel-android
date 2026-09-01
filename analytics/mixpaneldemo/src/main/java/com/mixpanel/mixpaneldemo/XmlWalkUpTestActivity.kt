package com.mixpanel.mixpaneldemo

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

/**
 * Walk-Up-to-Clickable-Parent Test Screen (XML Views)
 *
 * Validates that when a non-interactive leaf (e.g., TextView inside a Button)
 * is tapped, the SDK walks up to the nearest clickable ancestor for $el_id.
 */
class XmlWalkUpTestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xml_walk_up_test)
        title = "XML Walk-Up Test"

        // Set click listeners so the views are interactive
        findViewById<Button>(R.id.walkup_basic_btn).setOnClickListener { }
        findViewById<LinearLayout>(R.id.walkup_outer_card).setOnClickListener { }
        findViewById<Button>(R.id.walkup_delete_btn).setOnClickListener { }
        findViewById<LinearLayout>(R.id.walkup_checkout_row).setOnClickListener { }
        findViewById<Button>(R.id.walkup_outer_btn).setOnClickListener { }
    }
}
