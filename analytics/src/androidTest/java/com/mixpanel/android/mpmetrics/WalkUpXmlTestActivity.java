package com.mixpanel.android.mpmetrics;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.ComponentActivity;

/**
 * Test activity for walk-up-to-clickable-parent instrumentation tests.
 * UI is created programmatically to avoid R class issues in library modules.
 *
 * <p>Tests the SemanticExtractor.walkUpToClickableParent() behavior:
 * non-interactive views walk up to the nearest clickable ancestor (max 10 levels),
 * while clickable views own their own click and do not walk up.
 */
public class WalkUpXmlTestActivity extends ComponentActivity {

    // Use IDs starting from 20001 to avoid conflicts
    public static final int ID_BASIC_CONTAINER = 20001;
    public static final int ID_BASIC_LEAF = 20002;
    public static final int ID_LABELED_LEAF_CONTAINER = 20003;
    public static final int ID_LABELED_LEAF = 20004;
    public static final int ID_CLICKABLE_LEAF_NO_ID = 20005;
    public static final int ID_CLICKABLE_LEAF_PARENT = 20006;
    public static final int ID_CLICKABLE_LEAF_WITH_ID = 20007;
    public static final int ID_CLICKABLE_LEAF_WITH_ID_PARENT = 20008;
    public static final int ID_NON_CLICKABLE_CONTAINER = 20009;
    public static final int ID_NON_CLICKABLE_LEAF = 20010;
    public static final int ID_NON_CLICKABLE_LABELED_LEAF = 20011;
    public static final int ID_OUTER_CLICKABLE = 20012;
    public static final int ID_INNER_CLICKABLE = 20013;
    public static final int ID_INNER_LEAF = 20014;
    public static final int ID_DEEP_PARENT = 20015;
    public static final int ID_DEEP_LEAF = 20016;

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
