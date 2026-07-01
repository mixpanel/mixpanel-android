package com.mixpanel.android.mpmetrics;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * Test activity for autocapture instrumentation tests.
 * UI is created programmatically to avoid R class issues in library modules.
 */
public class XmlAutocaptureTestActivity extends Activity {

    public static final int ID_RULE1_BTN = 10001;
    public static final int ID_RULE2_BTN = android.R.id.button1;
    public static final int ID_RULE3_BTN = 10003;
    public static final int ID_DEAD_XML_BTN = 10004;
    public static final int ID_RAGE_ZONE = 10005;
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
