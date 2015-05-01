package com.mixpanel.android.viewcrawler;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class TestView extends FrameLayout {
    public TestView(Context context) {
        super(context);

        mAllViews = new HashSet<View>();
        mAllViews.add(this);

        mSecondLayer = new HashSet<View>();
        mThirdLayer = new HashSet<View>();
        mFourthLayer = new HashSet<View>();

        setId(ROOT_ID);
        setTag(CRAZY_TAG);
        setContentDescription(ROOT_DESCRIPTION);

        ViewGroup linear = new LinearLayout(getContext());
        linear.setId(LINEAR_ID);

        addView(linear);
        mAllViews.add(linear);

        mSecondLayer.add(linear);

        mTextView1 = new TextView(getContext());
        mTextView1.setId(TEXT_VIEW_ID);
        mTextView1.setTag(CRAZY_TAG);

        linear.addView(mTextView1);
        mAllViews.add(mTextView1);
        mThirdLayer.add(mTextView1);

        mTextView2 = new TextView(getContext());
        mTextView2.setId(TEXT2_VIEW_ID);
        mTextView2.setText("Original Text");
        mTextView2.setTag(SIMPLE_TAG);
        mTextView2.setContentDescription(TEXT_2_CONTENT_DESCRIPTION);

        linear.addView(mTextView2);
        mAllViews.add(mTextView2);
        mThirdLayer.add(mTextView2);

        mButtonGroup = new LinearLayout(getContext());
        mButtonGroup.setId(BUTTON_GROUP_ID);
        linear.addView(mButtonGroup);
        mAllViews.add(mButtonGroup);
        mThirdLayer.add(mButtonGroup);

        mAdHocButton1 = new AdHocButton1(getContext());
        mAdHocButton1.setTag(SIMPLE_TAG);
        mAdHocButton1.setText("{Hi!}");
        mAdHocButton1.setContentDescription(BUTTON_1_CONTENT_DESCRIPTION);
        mButtonGroup.addView(mAdHocButton1);
        mAllViews.add(mAdHocButton1);
        mFourthLayer.add(mAdHocButton1);

        mAdHocButton2 = new AdHocButton2(getContext());
        mAdHocButton2.setText("Hello \" There");
        mButtonGroup.addView(mAdHocButton2);
        mAllViews.add(mAdHocButton2);
        mFourthLayer.add(mAdHocButton2);

        mAdHocButton3 = new AdHocButton3(getContext());
        mAdHocButton2.setText("Howdy: ]");
        mAdHocButton3.setId(BUTTON_ID);
        mButtonGroup.addView(mAdHocButton3);
        mAdHocButton1.setContentDescription(BUTTON_3_CONTENT_DESCRIPTION);
        mAllViews.add(mAdHocButton3);
        mFourthLayer.add(mAdHocButton3);

        mImageView = new ImageView(getContext());
        mImageView.setId(IMAGE_VIEW_ID);
        mImageView.setImageResource(android.R.drawable.btn_star_big_off);
        linear.addView(mImageView);
        mAllViews.add(mImageView);
        mThirdLayer.add(mImageView);

        mButtonParentView = mButtonGroup;

        ViewGroup relative = new RelativeLayout(getContext());
        relative.setId(RELATIVE_LAYOUT_ID);
        addView(relative);
        mAllViews.add(relative);
        mSecondLayer.add(relative);
        mRelativeLayoutButton1 = new Button(getContext());
        mRelativeLayoutButton1.setText("Yo!");
        mRelativeLayoutButton1.setId(RELATIVE_LAYOUT_BUTTON1_ID);
        relative.addView(mRelativeLayoutButton1);
        mAllViews.add(mRelativeLayoutButton1);
        mThirdLayer.add(mRelativeLayoutButton1);

        mRelativeLayoutButton2 = new Button(getContext());
        mRelativeLayoutButton2.setText("Yeah!");
        mRelativeLayoutButton2.setId(RELATIVE_LAYOUT_BUTTON2_ID);
        relative.addView(mRelativeLayoutButton2);
        mAllViews.add(mRelativeLayoutButton2);
        mThirdLayer.add(mRelativeLayoutButton2);

        mViewsByHashcode = new HashMap<Integer, View>();
        for (View v:mAllViews) {
            mViewsByHashcode.put(v.hashCode(), v);
        }
    }

    public int viewCount() {
        return 1 + mSecondLayer.size() + mThirdLayer.size() + mFourthLayer.size();
    }

    public interface CustomPropButton {
        public CharSequence getCustomProperty();
        public void setCustomProperty(CharSequence s);
    }

    public static class AdHocButton1 extends Button implements CustomPropButton {
        public AdHocButton1(Context context) {
            super(context);
        }

        public CharSequence getCustomProperty() {
            return SIMPLE_TAG;
        }

        public void setCustomProperty(CharSequence s) {
            ; // OK
        }

        // This is a HACK- it's actually an override of a secret/public method of View.
        // It's added here so that we can test accessibilityDelegate tracking without
        // doing a lot of puzzling and unreliable functional tests.
        public boolean includeForAccessibility() {
            return true;
        }
    }

    public static class AdHocButton2 extends Button {
        public AdHocButton2(Context context) {
            super(context);
        }

        public void setCountingProperty(Object o) {
            countingPropertyValue = o;
            countingPropertyCount++;
        }

        public Object getCountingProperty() {
            return countingPropertyValue;
        }

        // This is a HACK- it's actually an override of a secret/public method of View.
        // It's added here so that we can test accessibilityDelegate tracking without
        // doing a lot of puzzling and unreliable functional tests.
        public boolean includeForAccessibility() {
            return true;
        }

        public Object countingPropertyValue = null;
        public int countingPropertyCount = 0;
    }

    public static class AdHocButton3 extends Button implements CustomPropButton {
        public AdHocButton3(Context context) {
            super(context);
        }

        public CharSequence getCustomProperty() {
            throw new RuntimeException("BANG!");
        }

        public void setCustomProperty(CharSequence s) {
            throw new RuntimeException("BANG!");
        }

        // This is a HACK- it's actually an override of a secret/public method of View.
        // It's added here so that we can test accessibilityDelegate tracking without
        // doing a lot of puzzling and unreliable functional tests.
        public boolean includeForAccessibility() {
            return true;
        }
    }

    public static class MockOnLayoutErrorListener implements ViewVisitor.OnLayoutErrorListener {
        public MockOnLayoutErrorListener() {
            errorList = new ArrayList<ViewVisitor.LayoutErrorMessage>();
        }

        public void onLayoutError(ViewVisitor.LayoutErrorMessage e) {
            errorList.add(e);
        }

        public List<ViewVisitor.LayoutErrorMessage> errorList;
    }

    public final Set<View> mAllViews;
    public final View mButtonParentView;
    public final ViewGroup mButtonGroup;
    public final TextView mTextView1;
    public final TextView mTextView2;
    public final AdHocButton1 mAdHocButton1;
    public final AdHocButton2 mAdHocButton2;
    public final AdHocButton3 mAdHocButton3;
    public final ImageView mImageView;
    public final Button mRelativeLayoutButton1;
    public final Button mRelativeLayoutButton2;
    public final Set<View> mSecondLayer;
    public final Set<View> mThirdLayer;
    public final Set<View> mFourthLayer;
    public final Map<Integer, View> mViewsByHashcode;

    public static final int ROOT_ID = 1000;
    public static final int BUTTON_ID = 2000;
    public static final int TEXT_VIEW_ID = 3000;
    public static final int TEXT2_VIEW_ID = 3500;
    public static final int LINEAR_ID = 4000;
    public static final int BUTTON_GROUP_ID = 5000;
    public static final int RELATIVE_LAYOUT_ID = 6000;
    public static final int RELATIVE_LAYOUT_BUTTON1_ID = 7000;
    public static final int RELATIVE_LAYOUT_BUTTON2_ID = 8000;
    public static final int IMAGE_VIEW_ID = 9000;
    public static final String SIMPLE_TAG = "this_is_a_simple_tag";
    public static final String CRAZY_TAG = "this is a long and \"CRAZY\" \\\"Tag";
    public static final String ROOT_DESCRIPTION = "This is the root view";
    public static final String TEXT_2_CONTENT_DESCRIPTION = "The Second Test Text View";
    public static final String BUTTON_1_CONTENT_DESCRIPTION = "Ad Hoc Button Number 1";
    public static final String BUTTON_3_CONTENT_DESCRIPTION = "Ad Hoc Button Number 3";

    public static final int NO_ANCHOR = 0;
}
