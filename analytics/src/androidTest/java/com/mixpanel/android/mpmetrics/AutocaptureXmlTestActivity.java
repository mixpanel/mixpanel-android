package com.mixpanel.android.mpmetrics;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.compose.runtime.MutableIntState;
import androidx.compose.ui.platform.ComposeView;

/**
 * Test activity for autocapture instrumentation tests.
 * UI is created programmatically to avoid R class issues in library modules.
 *
 * <p>Includes mixed-framework elements (ComposeView inside XML) for cross-framework
 * dead click testing.
 */
public class AutocaptureXmlTestActivity extends ComponentActivity {

    public static final int ID_RULE1_BTN = 10001;
    public static final int ID_RULE2_BTN = android.R.id.button1;
    public static final int ID_RULE3_BTN = 10003;
    public static final int ID_DEAD_XML_BTN = 10004;
    public static final int ID_RAGE_ZONE = 10005;
    public static final int ID_ALERT_DIALOG_BTN = 10006;
    public static final int ID_BOTTOM_SHEET_BTN = 10007;

    // Mixed-framework dead click test IDs
    public static final int ID_XML_BTN_XML_TEXT = 10010;
    public static final int ID_XML_TEXT_COUNTER = 10011;
    public static final int ID_XML_BTN_COMPOSE_TEXT = 10012;
    public static final int ID_COMPOSE_VIEW = 10013;

    // Counters for mixed-framework tests
    private int mXmlCounter = 0;
    private int mComposeCounter = 0;

    // Compose state exposed for the ComposeView
    // Accessed from MixedFrameworkComposeContent helper
    MutableIntState composeTextCounter;
    TextView xmlTextCounter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create layout programmatically
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        int padding = dpToPx(16);
        layout.setPadding(padding, padding, padding, padding);

        // Rule 1 button - contentDescription priority
        Button rule1Btn = createButton("Rule 1 - contentDescription");
        rule1Btn.setId(ID_RULE1_BTN);
        rule1Btn.setContentDescription("rule1_btn");
        rule1Btn.setOnClickListener(v -> {});
        layout.addView(rule1Btn);

        // Rule 2 button - resource ID fallback
        Button rule2Btn = createButton("Rule 2 - resource ID");
        rule2Btn.setId(ID_RULE2_BTN);
        rule2Btn.setOnClickListener(v -> {});
        addMarginTop(rule2Btn, 8);
        layout.addView(rule2Btn);

        // Rule 3 button - hash fallback (no contentDescription, invalid resource ID)
        Button rule3Btn = createButton("Rule 3 - hash fallback");
        rule3Btn.setId(ID_RULE3_BTN);
        rule3Btn.setOnClickListener(v -> {});
        addMarginTop(rule3Btn, 8);
        layout.addView(rule3Btn);

        // Dead click button - no listener
        Button deadBtn = createButton("Dead Button (no listener)");
        deadBtn.setId(ID_DEAD_XML_BTN);
        deadBtn.setContentDescription("dead_xml_btn");
        addMarginTop(deadBtn, 16);
        layout.addView(deadBtn);

        // Rage zone - clickable view
        View rageZone = new View(this);
        rageZone.setId(ID_RAGE_ZONE);
        rageZone.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(80)));
        rageZone.setBackgroundColor(0x1AFF0000); // Semi-transparent red
        rageZone.setClickable(true);
        rageZone.setFocusable(true);
        rageZone.setContentDescription("rage_zone");
        addMarginTop(rageZone, 16);
        layout.addView(rageZone);

        // AlertDialog trigger button
        Button alertBtn = createButton("Show AlertDialog");
        alertBtn.setId(ID_ALERT_DIALOG_BTN);
        alertBtn.setContentDescription("test_alert_trigger");
        alertBtn.setOnClickListener(v -> {
            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                    .setTitle("Test Alert Dialog")
                    .setMessage("Tap buttons inside this dialog")
                    .setPositiveButton("Confirm", null)
                    .setNegativeButton("Cancel", null)
                    .show();
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                    .setContentDescription("test_alert_confirm");
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                    .setContentDescription("test_alert_cancel");
        });
        addMarginTop(alertBtn, 16);
        layout.addView(alertBtn);

        // BottomSheet trigger button (uses a regular Dialog for test simplicity)
        Button sheetBtn = createButton("Show Bottom Sheet");
        sheetBtn.setId(ID_BOTTOM_SHEET_BTN);
        sheetBtn.setContentDescription("test_sheet_trigger");
        sheetBtn.setOnClickListener(v -> {
            android.app.Dialog dialog = new android.app.Dialog(this);
            LinearLayout sheetLayout = new LinearLayout(this);
            sheetLayout.setOrientation(LinearLayout.VERTICAL);
            int pad = dpToPx(16);
            sheetLayout.setPadding(pad, pad, pad, pad);

            Button sheetAction = new Button(this);
            sheetAction.setText("Sheet Action");
            sheetAction.setContentDescription("test_sheet_action");
            sheetLayout.addView(sheetAction, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            dialog.setContentView(sheetLayout);
            dialog.show();
        });
        addMarginTop(sheetBtn, 8);
        layout.addView(sheetBtn);

        // ============ Mixed-Framework Dead Click Test Elements ============

        // XML button that changes XML text
        Button xmlBtnXmlText = createButton("XML Btn -> XML Text");
        xmlBtnXmlText.setId(ID_XML_BTN_XML_TEXT);
        xmlBtnXmlText.setContentDescription("xml_btn_xml_text");
        addMarginTop(xmlBtnXmlText, 16);
        layout.addView(xmlBtnXmlText);

        // XML text counter (updated by XML and Compose buttons)
        xmlTextCounter = new TextView(this);
        xmlTextCounter.setId(ID_XML_TEXT_COUNTER);
        xmlTextCounter.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        xmlTextCounter.setText("XML counter: 0");
        xmlTextCounter.setContentDescription("xml_text_counter");
        addMarginTop(xmlTextCounter, 4);
        layout.addView(xmlTextCounter);

        // XML button that changes Compose text
        Button xmlBtnComposeText = createButton("XML Btn -> Compose Text");
        xmlBtnComposeText.setId(ID_XML_BTN_COMPOSE_TEXT);
        xmlBtnComposeText.setContentDescription("xml_btn_compose_text");
        addMarginTop(xmlBtnComposeText, 8);
        layout.addView(xmlBtnComposeText);

        // ComposeView hosting Compose button + Compose text counter
        ComposeView composeView = new ComposeView(this);
        composeView.setId(ID_COMPOSE_VIEW);
        composeView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addMarginTop(composeView, 8);
        layout.addView(composeView);

        // Wire up XML button -> XML text
        xmlBtnXmlText.setOnClickListener(v -> {
            mXmlCounter++;
            xmlTextCounter.setText("XML counter: " + mXmlCounter);
        });

        // Wire up XML button -> Compose text (increments compose state)
        xmlBtnComposeText.setOnClickListener(v -> {
            if (composeTextCounter != null) {
                composeTextCounter.setIntValue(composeTextCounter.getIntValue() + 1);
            }
        });

        // Set Compose content with buttons and text counter
        MixedFrameworkComposeContent.setContent(composeView, this);

        scrollView.addView(layout);
        setContentView(scrollView);
    }

    private Button createButton(String text) {
        Button button = new Button(this);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        button.setText(text);
        return button;
    }

    private void addMarginTop(View view, int dp) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.topMargin = dpToPx(dp);
        view.setLayoutParams(params);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
