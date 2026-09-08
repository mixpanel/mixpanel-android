package com.mixpanel.android.mpmetrics;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

import com.mixpanel.android.test.R;
import androidx.compose.runtime.MutableIntState;
import androidx.compose.ui.platform.ComposeView;

/**
 * Test activity for autocapture instrumentation tests.
 * UI is created programmatically to avoid R class issues in library modules.
 *
 * <p>Includes mixed-framework elements (ComposeView inside XML) for cross-framework
 * dead click testing.
 */
/*
 * Fixtures that a test identifies by $el_id use real resource ids: identity now comes from the
 * resource entry name, never from contentDescription, which is localized and can carry user data.
 * The contentDescriptions are kept so the tests also prove the label is ignored.
 */
public class AutocaptureXmlTestActivity extends ComponentActivity {

    public static final int ID_RULE1_BTN = R.id.rule1_btn;
    public static final int ID_RULE2_BTN = android.R.id.button1;
    public static final int ID_RULE3_BTN = 10003;
    public static final int ID_DEAD_XML_BTN = R.id.dead_xml_btn;
    public static final int ID_RAGE_ZONE = R.id.rage_zone;
    public static final int ID_ALERT_DIALOG_BTN = R.id.test_alert_trigger;
    public static final int ID_BOTTOM_SHEET_BTN = R.id.test_sheet_trigger;
    public static final int ID_NOT_IMPORTANT_VIEW = 10008;

    // Accessibility guard test IDs
    public static final int ID_ACCESSIBLE_NO_CD = 10020;
    public static final int ID_NOT_IMPORTANT_WITH_CD = 10021;
    public static final int ID_ACCESSIBLE_WITH_CD = R.id.accessible_with_cd;

    // Visibility test IDs
    public static final int ID_INVISIBLE_BTN = R.id.invisible_btn;
    public static final int ID_GONE_BTN = R.id.gone_btn;
    public static final int ID_ZERO_ALPHA_BTN = R.id.zero_alpha_btn;

    // Mixed-framework dead click test IDs
    public static final int ID_XML_BTN_XML_TEXT = R.id.xml_btn_xml_text;
    public static final int ID_XML_TEXT_COUNTER = R.id.xml_text_counter;
    public static final int ID_XML_BTN_COMPOSE_TEXT = R.id.xml_btn_compose_text;
    public static final int ID_COMPOSE_VIEW = R.id.compose_view_in_xml;

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

        // Simulates a React Native Pressable with accessible={false}:
        // - Parent container has a child TextView with visible text
        // - contentDescription is NOT set (null) on the parent
        // - The child text must NOT leak into $attr-aria-label or $el_id
        LinearLayout notImportantContainer = new LinearLayout(this);
        notImportantContainer.setId(ID_NOT_IMPORTANT_VIEW);
        notImportantContainer.setOrientation(LinearLayout.VERTICAL);
        notImportantContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(60)));
        notImportantContainer.setBackgroundColor(0x1A0000FF);
        notImportantContainer.setClickable(true);
        notImportantContainer.setFocusable(true);
        // No contentDescription set — it stays null
        addMarginTop(notImportantContainer, 16);
        // Child label text (like RN's <Text> inside a <Pressable>)
        TextView childLabel = new TextView(this);
        childLabel.setText("Sensitive Account 1234");
        childLabel.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        notImportantContainer.addView(childLabel);
        layout.addView(notImportantContainer);

        // ============ Accessibility Guard Test Elements ============

        // Scenario 2: Accessible (default) + no contentDescription + child text
        // Simulates RN Pressable with accessible={true} but no accessibilityLabel
        LinearLayout accessibleNoCd = new LinearLayout(this);
        accessibleNoCd.setId(ID_ACCESSIBLE_NO_CD);
        accessibleNoCd.setOrientation(LinearLayout.VERTICAL);
        accessibleNoCd.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(60)));
        accessibleNoCd.setBackgroundColor(0x1A00FF00);
        accessibleNoCd.setClickable(true);
        accessibleNoCd.setFocusable(true);
        // importantForAccessibility defaults to YES — no contentDescription set
        addMarginTop(accessibleNoCd, 8);
        TextView accessibleNoCdLabel = new TextView(this);
        accessibleNoCdLabel.setText("Sensitive Account 5678");
        accessibleNoCdLabel.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        accessibleNoCd.addView(accessibleNoCdLabel);
        layout.addView(accessibleNoCd);

        // Scenario 4: Not important for accessibility + HAS contentDescription
        // Tests that contentDescription is NOT captured when view is not important
        LinearLayout notImportantWithCd = new LinearLayout(this);
        notImportantWithCd.setId(ID_NOT_IMPORTANT_WITH_CD);
        notImportantWithCd.setOrientation(LinearLayout.VERTICAL);
        notImportantWithCd.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(60)));
        notImportantWithCd.setBackgroundColor(0x1AFF00FF);
        notImportantWithCd.setClickable(true);
        notImportantWithCd.setFocusable(true);
        notImportantWithCd.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        notImportantWithCd.setContentDescription("Sensitive Account 9999");
        addMarginTop(notImportantWithCd, 8);
        TextView notImportantWithCdLabel = new TextView(this);
        notImportantWithCdLabel.setText("Some Label");
        notImportantWithCdLabel.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        notImportantWithCd.addView(notImportantWithCdLabel);
        layout.addView(notImportantWithCd);

        // Positive case: Accessible + explicit contentDescription
        // Tests that contentDescription IS captured when view is important
        LinearLayout accessibleWithCd = new LinearLayout(this);
        accessibleWithCd.setId(ID_ACCESSIBLE_WITH_CD);
        accessibleWithCd.setOrientation(LinearLayout.VERTICAL);
        accessibleWithCd.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(60)));
        accessibleWithCd.setBackgroundColor(0x1A0000FF);
        accessibleWithCd.setClickable(true);
        accessibleWithCd.setFocusable(true);
        accessibleWithCd.setContentDescription("Intended Label");
        addMarginTop(accessibleWithCd, 8);
        layout.addView(accessibleWithCd);

        // ============ Visibility Test Elements ============

        // INVISIBLE button — View.INVISIBLE, still in layout but not drawn
        Button invisibleBtn = createButton("Invisible Button");
        invisibleBtn.setId(ID_INVISIBLE_BTN);
        invisibleBtn.setContentDescription("invisible_btn");
        invisibleBtn.setOnClickListener(v -> {});
        invisibleBtn.setVisibility(View.INVISIBLE);
        addMarginTop(invisibleBtn, 8);
        layout.addView(invisibleBtn);

        // GONE button — View.GONE, removed from layout entirely
        Button goneBtn = createButton("Gone Button");
        goneBtn.setId(ID_GONE_BTN);
        goneBtn.setContentDescription("gone_btn");
        goneBtn.setOnClickListener(v -> {});
        goneBtn.setVisibility(View.GONE);
        addMarginTop(goneBtn, 8);
        layout.addView(goneBtn);

        // Zero-alpha button — alpha=0, fully transparent but in layout
        Button zeroAlphaBtn = createButton("Zero Alpha Button");
        zeroAlphaBtn.setId(ID_ZERO_ALPHA_BTN);
        zeroAlphaBtn.setContentDescription("zero_alpha_btn");
        zeroAlphaBtn.setOnClickListener(v -> {});
        zeroAlphaBtn.setAlpha(0f);
        addMarginTop(zeroAlphaBtn, 8);
        layout.addView(zeroAlphaBtn);

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
