package com.mixpanel.android.mpmetrics;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.ComponentActivity;

import com.mixpanel.android.test.R;

/**
 * Test activity for walk-up-to-clickable-parent instrumentation tests.
 * UI is created programmatically to avoid R class issues in library modules.
 *
 * <p>Tests the SemanticExtractor.walkUpToClickableParent() behavior:
 * non-interactive views walk up to the nearest clickable ancestor (max 10 levels),
 * while clickable views own their own click and do not walk up.
 */
public class WalkUpXmlTestActivity extends ComponentActivity {

    // Real resource ids, not bare ints: $el_id resolves a view's identity from its resource entry
    // name, and generated ids have none. The names match what each fixture's contentDescription used
    // to supply, so the walk-up expectations are unchanged while the labels are now ignored.
    public static final int ID_BASIC_CONTAINER = R.id.card_container;
    public static final int ID_BASIC_LEAF = R.id.basic_leaf;
    public static final int ID_LABELED_LEAF_CONTAINER = R.id.parent_of_labeled;
    public static final int ID_LABELED_LEAF = R.id.leaf_label;
    // Deliberately a bare int: this fixture must have no resource entry name, because the
    // test that taps it asserts the structural hash fallback.
    public static final int ID_CLICKABLE_LEAF_NO_ID = 20005;
    public static final int ID_CLICKABLE_LEAF_PARENT = R.id.parent_of_clickable_no_id;
    public static final int ID_CLICKABLE_LEAF_WITH_ID = R.id.inner_clickable_btn;
    public static final int ID_CLICKABLE_LEAF_WITH_ID_PARENT = R.id.parent_of_clickable_with_id;
    public static final int ID_NON_CLICKABLE_CONTAINER = R.id.non_clickable_container;
    // Bare int for the same reason as ID_CLICKABLE_LEAF_NO_ID.
    public static final int ID_NON_CLICKABLE_LEAF = 20010;
    public static final int ID_NON_CLICKABLE_LABELED_LEAF = R.id.orphan_label;
    public static final int ID_OUTER_CLICKABLE = R.id.outer_clickable;
    public static final int ID_INNER_CLICKABLE = R.id.inner_clickable;
    public static final int ID_INNER_LEAF = R.id.inner_leaf;
    public static final int ID_DEEP_PARENT = R.id.deep_parent;
    public static final int ID_DEEP_LEAF = R.id.deep_leaf;
    public static final int ID_DISABLED_PARENT = R.id.disabled_parent;
    public static final int ID_ENABLED_GRANDPARENT = R.id.enabled_grandparent;
    public static final int ID_DISABLED_LEAF = R.id.disabled_leaf;
    public static final int ID_CHECKBOX = R.id.my_checkbox;
    public static final int ID_RADIO_BUTTON = R.id.my_radio;
    public static final int ID_CHECKABLE_CONTAINER = R.id.checkable_parent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        // 1. Basic walk-up: non-interactive leaf (no identity) inside clickable parent
        LinearLayout basicContainer = new LinearLayout(this);
        basicContainer.setId(ID_BASIC_CONTAINER);
        basicContainer.setOrientation(LinearLayout.VERTICAL);
        basicContainer.setClickable(true);
        basicContainer.setFocusable(true);
        basicContainer.setContentDescription("card_container");
        basicContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        basicContainer.setPadding(padding, padding, padding, padding);

        TextView basicLeaf = new TextView(this);
        basicLeaf.setId(ID_BASIC_LEAF);
        basicLeaf.setText("Plain text inside clickable container");
        basicLeaf.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        basicContainer.addView(basicLeaf);
        layout.addView(basicContainer);

        // 2. Non-interactive leaf WITH contentDescription inside clickable parent
        LinearLayout labeledLeafContainer = new LinearLayout(this);
        labeledLeafContainer.setId(ID_LABELED_LEAF_CONTAINER);
        labeledLeafContainer.setOrientation(LinearLayout.VERTICAL);
        labeledLeafContainer.setClickable(true);
        labeledLeafContainer.setFocusable(true);
        labeledLeafContainer.setContentDescription("parent_of_labeled");
        labeledLeafContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        labeledLeafContainer.setPadding(padding, padding, padding, padding);
        addMarginTop(labeledLeafContainer, 16);

        TextView labeledLeaf = new TextView(this);
        labeledLeaf.setId(ID_LABELED_LEAF);
        labeledLeaf.setText("I have my own contentDescription");
        labeledLeaf.setContentDescription("leaf_label");
        labeledLeaf.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        labeledLeafContainer.addView(labeledLeaf);
        layout.addView(labeledLeafContainer);

        // 3. Clickable leaf WITHOUT identity inside clickable parent
        LinearLayout clickableLeafParent = new LinearLayout(this);
        clickableLeafParent.setId(ID_CLICKABLE_LEAF_PARENT);
        clickableLeafParent.setOrientation(LinearLayout.VERTICAL);
        clickableLeafParent.setClickable(true);
        clickableLeafParent.setFocusable(true);
        clickableLeafParent.setContentDescription("parent_of_clickable_no_id");
        clickableLeafParent.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        clickableLeafParent.setPadding(padding, padding, padding, padding);
        addMarginTop(clickableLeafParent, 16);

        // A View that is clickable but has no contentDescription or valid resource ID
        View clickableLeafNoId = new View(this);
        clickableLeafNoId.setId(ID_CLICKABLE_LEAF_NO_ID);
        clickableLeafNoId.setClickable(true);
        clickableLeafNoId.setFocusable(true);
        clickableLeafNoId.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(48)));
        clickableLeafNoId.setBackgroundColor(0x1A0000FF);
        clickableLeafParent.addView(clickableLeafNoId);
        layout.addView(clickableLeafParent);

        // 4. Clickable leaf WITH identity inside clickable parent
        LinearLayout clickableLeafWithIdParent = new LinearLayout(this);
        clickableLeafWithIdParent.setId(ID_CLICKABLE_LEAF_WITH_ID_PARENT);
        clickableLeafWithIdParent.setOrientation(LinearLayout.VERTICAL);
        clickableLeafWithIdParent.setClickable(true);
        clickableLeafWithIdParent.setFocusable(true);
        clickableLeafWithIdParent.setContentDescription("parent_of_clickable_with_id");
        clickableLeafWithIdParent.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        clickableLeafWithIdParent.setPadding(padding, padding, padding, padding);
        addMarginTop(clickableLeafWithIdParent, 16);

        Button clickableLeafWithId = new Button(this);
        clickableLeafWithId.setId(ID_CLICKABLE_LEAF_WITH_ID);
        clickableLeafWithId.setText("Clickable with identity");
        clickableLeafWithId.setContentDescription("inner_clickable_btn");
        clickableLeafWithId.setOnClickListener(v -> {});
        clickableLeafWithId.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        clickableLeafWithIdParent.addView(clickableLeafWithId);
        layout.addView(clickableLeafWithIdParent);

        // 5. Non-interactive leaf inside non-clickable container (no walk-up target)
        LinearLayout nonClickableContainer = new LinearLayout(this);
        nonClickableContainer.setId(ID_NON_CLICKABLE_CONTAINER);
        nonClickableContainer.setOrientation(LinearLayout.VERTICAL);
        // NOT clickable
        nonClickableContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        nonClickableContainer.setPadding(padding, padding, padding, padding);
        addMarginTop(nonClickableContainer, 16);

        // 5a. leaf without identity
        TextView nonClickableLeaf = new TextView(this);
        nonClickableLeaf.setId(ID_NON_CLICKABLE_LEAF);
        nonClickableLeaf.setText("No clickable ancestor, no identity");
        nonClickableLeaf.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        nonClickableContainer.addView(nonClickableLeaf);

        // 5b. leaf with contentDescription
        TextView nonClickableLabeledLeaf = new TextView(this);
        nonClickableLabeledLeaf.setId(ID_NON_CLICKABLE_LABELED_LEAF);
        nonClickableLabeledLeaf.setText("No clickable ancestor, has identity");
        nonClickableLabeledLeaf.setContentDescription("orphan_label");
        nonClickableLabeledLeaf.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addMarginTop(nonClickableLabeledLeaf, 8);
        nonClickableContainer.addView(nonClickableLabeledLeaf);
        layout.addView(nonClickableContainer);

        // 6. Nested clickables: outer clickable > inner clickable > leaf
        LinearLayout outerClickable = new LinearLayout(this);
        outerClickable.setId(ID_OUTER_CLICKABLE);
        outerClickable.setOrientation(LinearLayout.VERTICAL);
        outerClickable.setClickable(true);
        outerClickable.setFocusable(true);
        outerClickable.setContentDescription("outer_clickable");
        outerClickable.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        outerClickable.setPadding(padding, padding, padding, padding);
        addMarginTop(outerClickable, 16);

        LinearLayout innerClickable = new LinearLayout(this);
        innerClickable.setId(ID_INNER_CLICKABLE);
        innerClickable.setOrientation(LinearLayout.VERTICAL);
        innerClickable.setClickable(true);
        innerClickable.setFocusable(true);
        innerClickable.setContentDescription("inner_clickable");
        innerClickable.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        innerClickable.setPadding(padding, padding, padding, padding);

        TextView innerLeaf = new TextView(this);
        innerLeaf.setId(ID_INNER_LEAF);
        innerLeaf.setText("Leaf inside nested clickables");
        innerLeaf.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        innerClickable.addView(innerLeaf);
        outerClickable.addView(innerClickable);
        layout.addView(outerClickable);

        // 7. Deep nesting (9 non-clickable wrappers, within 10-level limit)
        LinearLayout deepParent = new LinearLayout(this);
        deepParent.setId(ID_DEEP_PARENT);
        deepParent.setOrientation(LinearLayout.VERTICAL);
        deepParent.setClickable(true);
        deepParent.setFocusable(true);
        deepParent.setContentDescription("deep_parent");
        deepParent.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        deepParent.setPadding(padding, padding, padding, padding);
        addMarginTop(deepParent, 16);

        // Add 9 non-clickable wrappers
        LinearLayout current = deepParent;
        for (int i = 0; i < 9; i++) {
            LinearLayout wrapper = new LinearLayout(this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            current.addView(wrapper);
            current = wrapper;
        }

        TextView deepLeaf = new TextView(this);
        deepLeaf.setId(ID_DEEP_LEAF);
        deepLeaf.setText("Deep leaf (9 levels deep)");
        deepLeaf.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        current.addView(deepLeaf);
        layout.addView(deepParent);

        // 8. Disabled clickable ancestor should be skipped — walk up to enabled grandparent
        LinearLayout enabledGrandparent = new LinearLayout(this);
        enabledGrandparent.setId(ID_ENABLED_GRANDPARENT);
        enabledGrandparent.setOrientation(LinearLayout.VERTICAL);
        enabledGrandparent.setClickable(true);
        enabledGrandparent.setFocusable(true);
        enabledGrandparent.setEnabled(true);
        enabledGrandparent.setContentDescription("enabled_grandparent");
        enabledGrandparent.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        enabledGrandparent.setPadding(padding, padding, padding, padding);
        addMarginTop(enabledGrandparent, 16);

        LinearLayout disabledParent = new LinearLayout(this);
        disabledParent.setId(ID_DISABLED_PARENT);
        disabledParent.setOrientation(LinearLayout.VERTICAL);
        disabledParent.setClickable(true);
        disabledParent.setFocusable(true);
        disabledParent.setEnabled(false);
        disabledParent.setContentDescription("disabled_parent");
        disabledParent.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView disabledLeaf = new TextView(this);
        disabledLeaf.setId(ID_DISABLED_LEAF);
        disabledLeaf.setText("Leaf inside disabled parent");
        disabledLeaf.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        disabledParent.addView(disabledLeaf);
        enabledGrandparent.addView(disabledParent);
        layout.addView(enabledGrandparent);

        // 9. Checkable views (CheckBox, RadioButton) inside a clickable parent.
        // They are clickable by default, so they should NOT walk up.
        LinearLayout checkableContainer = new LinearLayout(this);
        checkableContainer.setId(ID_CHECKABLE_CONTAINER);
        checkableContainer.setOrientation(LinearLayout.VERTICAL);
        checkableContainer.setClickable(true);
        checkableContainer.setFocusable(true);
        checkableContainer.setContentDescription("checkable_parent");
        checkableContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        checkableContainer.setPadding(padding, padding, padding, padding);
        addMarginTop(checkableContainer, 16);

        CheckBox checkBox = new CheckBox(this);
        checkBox.setId(ID_CHECKBOX);
        checkBox.setText("A checkbox");
        checkBox.setContentDescription("my_checkbox");
        checkBox.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        checkableContainer.addView(checkBox);

        RadioButton radioButton = new RadioButton(this);
        radioButton.setId(ID_RADIO_BUTTON);
        radioButton.setText("A radio button");
        radioButton.setContentDescription("my_radio");
        radioButton.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addMarginTop(radioButton, 8);
        checkableContainer.addView(radioButton);
        layout.addView(checkableContainer);

        scrollView.addView(layout);
        setContentView(scrollView);
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
