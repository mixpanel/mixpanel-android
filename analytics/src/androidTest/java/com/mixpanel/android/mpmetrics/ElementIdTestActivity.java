package com.mixpanel.android.mpmetrics;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.activity.ComponentActivity;

import com.mixpanel.android.test.R;

/**
 * Minimal activity for {@code $el_id} resolution tests.
 *
 * <p>Deliberately small: three buttons, all above the fold, so taps never land off-screen
 * (Android 14+ rejects injected touches aimed outside this app's own window).
 *
 * <p>Unlike {@link AutocaptureXmlTestActivity}, the buttons here carry <b>real</b> id resources
 * (from {@code src/androidTest/res/values/mp_test_ids.xml}) so the resource-entry-name step of the
 * resolution order is exercised — programmatically assigned numeric ids have no entry name.
 */
public class ElementIdTestActivity extends ComponentActivity {

    /** Resource entry name: {@code mp_test_checkout_button}. Also carries a contentDescription. */
    public static final int ID_CHECKOUT_BTN = R.id.mp_test_checkout_button;

    /** Resource entry name: {@code mp_test_bare_button}. No contentDescription. */
    public static final int ID_BARE_BTN = R.id.mp_test_bare_button;

    /** No id resource, contentDescription only. */
    public static final int ID_CONTENT_DESC_ONLY_BTN = 20001;

    public static final String CHECKOUT_CONTENT_DESCRIPTION = "Checkout Label";
    public static final String CONTENT_DESC_ONLY_LABEL = "content_desc_only_btn";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Resource id + contentDescription: the resource entry name must win.
        Button checkoutBtn = new Button(this);
        checkoutBtn.setId(ID_CHECKOUT_BTN);
        checkoutBtn.setText("Checkout");
        checkoutBtn.setContentDescription(CHECKOUT_CONTENT_DESCRIPTION);
        checkoutBtn.setOnClickListener(v -> {});
        layout.addView(checkoutBtn);

        // contentDescription only.
        Button contentDescOnlyBtn = new Button(this);
        contentDescOnlyBtn.setId(ID_CONTENT_DESC_ONLY_BTN);
        contentDescOnlyBtn.setText("Content Description Only");
        contentDescOnlyBtn.setContentDescription(CONTENT_DESC_ONLY_LABEL);
        contentDescOnlyBtn.setOnClickListener(v -> {});
        layout.addView(contentDescOnlyBtn);

        // Resource id only, no label of any kind.
        Button bareBtn = new Button(this);
        bareBtn.setId(ID_BARE_BTN);
        bareBtn.setText("Bare");
        bareBtn.setOnClickListener(v -> {});
        layout.addView(bareBtn);

        setContentView(layout);
    }
}
