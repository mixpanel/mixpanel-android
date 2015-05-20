package com.mixpanel.android.viewcrawler;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.test.AndroidTestCase;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.RelativeLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ViewVisitorTest extends AndroidTestCase {
    public void setUp() throws Exception {
        super.setUp();

        final Paint paint = new Paint();
        paint.setColor(Color.BLUE);

        final Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        mBitmap1a = Bitmap.createBitmap(10, 10, conf); // this creates a MUTABLE bitmap
        final Canvas canvas1 = new Canvas(mBitmap1a);
        canvas1.drawCircle(3, 3, 4, paint);

        mBitmap1b = Bitmap.createBitmap(mBitmap1a);

        mBitmap2 = Bitmap.createBitmap(10, 10, conf);
        final Canvas canvas2 = new Canvas(mBitmap2);
        canvas2.drawCircle(6, 6, 4, paint);

        mRootView = new TestView(getContext());

        mButton2Path = new ArrayList<Pathfinder.PathElement>();
        mButton2Path.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, "com.mixpanel.android.viewcrawler.TestView", 0, -1, null, null));
        mButton2Path.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, "android.widget.LinearLayout", 0, -1, null, null));
        mButton2Path.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, "android.widget.LinearLayout", 0, -1, null, null));
        mButton2Path.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, "android.widget.Button", 1, -1, null, null));

        mWorkingRootPath1 = new ArrayList<Pathfinder.PathElement>();
        mWorkingRootPath1.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, "java.lang.Object", 0, -1, null, null));

        mWorkingRootPath2 = new ArrayList<Pathfinder.PathElement>();
        mWorkingRootPath2.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, null, -1, -1, null, null));

        mFailingRootPath1 = new ArrayList<Pathfinder.PathElement>();
        mFailingRootPath1.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, "android.widget.Button", 0, -1, null, null));

        mFailingRootPath2 = new ArrayList<Pathfinder.PathElement>();
        mFailingRootPath2.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, "java.lang.Object", 1, -1, null, null));

        mFailingRootPath3 = new ArrayList<Pathfinder.PathElement>();
        mFailingRootPath3.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, "java.lang.Object", 0, 1234, null, null));

        mFailingRootPath4 = new ArrayList<Pathfinder.PathElement>();
        mFailingRootPath4.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, "java.lang.Object", 0, -1, null, "NO SUCH TAG"));

        mRootWildcardPath = new ArrayList<Pathfinder.PathElement>();
        mRootWildcardPath.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, null, -1, -1, null, null));

        mRootGoodTagIdPath = new ArrayList<Pathfinder.PathElement>();
        mRootGoodTagIdPath.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, null, -1, TestView.ROOT_ID, null, TestView.CRAZY_TAG));

        mRootBadTagIdPath = new ArrayList<Pathfinder.PathElement>();
        mRootBadTagIdPath.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, null, -1, TestView.ROOT_ID, null, "NO DICE"));

        mFindText2DescriptionPath = new ArrayList<Pathfinder.PathElement>();
        mFindText2DescriptionPath.add(new Pathfinder.PathElement(Pathfinder.PathElement.SHORTEST_PREFIX, null, -1, TestView.TEXT2_VIEW_ID, TestView.TEXT_2_CONTENT_DESCRIPTION, null));

        mFailText2DescriptionPath = new ArrayList<Pathfinder.PathElement>();
        mFailText2DescriptionPath.add(new Pathfinder.PathElement(Pathfinder.PathElement.SHORTEST_PREFIX, null, -1, TestView.TEXT2_VIEW_ID, "DOESNT MATCH", null));

        mThirdLayerViewId = new ArrayList<Pathfinder.PathElement>();
        mThirdLayerViewId.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, null, -1, -1, null, null));
        mThirdLayerViewId.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, null, -1, -1, null, null));
        mThirdLayerViewId.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, null, -1, TestView.TEXT_VIEW_ID, null, null));

        mThirdLayerViewTag = new ArrayList<Pathfinder.PathElement>();
        mThirdLayerViewTag.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, null, -1, -1, null, null));
        mThirdLayerViewTag.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, null, -1, -1, null, null));
        mThirdLayerViewTag.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, null, -1, TestView.TEXT_VIEW_ID, null, TestView.CRAZY_TAG));

        mThirdLayerWildcard = new ArrayList<Pathfinder.PathElement>();
        mThirdLayerWildcard.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, null, -1, -1, null, null));
        mThirdLayerWildcard.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, null, -1, -1, null, null));
        mThirdLayerWildcard.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, null, -1, -1, null, null));

        mFindRootIdPath = new ArrayList<Pathfinder.PathElement>();
        mFindRootIdPath.add(new Pathfinder.PathElement(Pathfinder.PathElement.SHORTEST_PREFIX, null, -1, TestView.ROOT_ID, null, null));

        mFindNonsenseIdPath = new ArrayList<Pathfinder.PathElement>();
        mFindNonsenseIdPath.add(new Pathfinder.PathElement(Pathfinder.PathElement.SHORTEST_PREFIX, null, -1, 8080808, null, null));

        mFindTextViewIdPath = new ArrayList<Pathfinder.PathElement>();
        mFindTextViewIdPath.add(new Pathfinder.PathElement(Pathfinder.PathElement.SHORTEST_PREFIX, null, -1, TestView.TEXT_VIEW_ID, null, null));

        mFailTextViewIdPath = new ArrayList<Pathfinder.PathElement>();
        mFailTextViewIdPath.add(new Pathfinder.PathElement(Pathfinder.PathElement.SHORTEST_PREFIX, null, -1, TestView.TEXT_VIEW_ID, null, "NO SUCH TAG"));

        mFirstInButtonGroup = new ArrayList<Pathfinder.PathElement>();
        mFirstInButtonGroup.add(new Pathfinder.PathElement(Pathfinder.PathElement.SHORTEST_PREFIX, null, -1, TestView.BUTTON_GROUP_ID, null, null));
        mFirstInButtonGroup.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, null,  0, -1, null, null));

        mFindButtonGroupInRoot = new ArrayList<Pathfinder.PathElement>();
        mFindButtonGroupInRoot.add(new Pathfinder.PathElement(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, null, -1, TestView.ROOT_ID, null, null));
        mFindButtonGroupInRoot.add(new Pathfinder.PathElement(Pathfinder.PathElement.SHORTEST_PREFIX, null, -1, TestView.BUTTON_GROUP_ID, null, null));

        mFindButton2 = new ArrayList<Pathfinder.PathElement>();
        mFindButton2.add(new Pathfinder.PathElement(Pathfinder.PathElement.SHORTEST_PREFIX, mRootView.mAdHocButton2.getClass().getCanonicalName(), -1, -1, null, null));

        mRelativeLayoutPath = new ArrayList<Pathfinder.PathElement>();
        mRelativeLayoutPath.add(new Pathfinder.PathElement(Pathfinder.PathElement.SHORTEST_PREFIX, null, -1, TestView.RELATIVE_LAYOUT_ID, null, null));

        mRelativeLayoutButtonPath = new ArrayList<Pathfinder.PathElement>();
        mRelativeLayoutButtonPath.add(new Pathfinder.PathElement(Pathfinder.PathElement.SHORTEST_PREFIX, null, -1, TestView.RELATIVE_LAYOUT_BUTTON1_ID, null, null));

        mTrackListener = new CollectingEventListener();

        mLayoutErrorListener = new TestView.MockOnLayoutErrorListener();
    }

    public void testPath() {

        {
            final CollectorEditor button2Editor = new CollectorEditor(mButton2Path);
            button2Editor.visit(mRootView);
            assertEquals(button2Editor.collected.size(), 1);
            assertEquals(button2Editor.collected.get(0), mRootView.mAdHocButton2);
        }

        {
            final CollectorEditor rootPathEditor = new CollectorEditor(mWorkingRootPath1);
            rootPathEditor.visit(mRootView);
            assertEquals(rootPathEditor.collected.size(), 1);
            assertEquals(rootPathEditor.collected.get(0), mRootView);
        }

        {
            final CollectorEditor rootPathEditor = new CollectorEditor(mWorkingRootPath2);
            rootPathEditor.visit(mRootView);
            assertEquals(rootPathEditor.collected.size(), 1);
            assertEquals(rootPathEditor.collected.get(0), mRootView);
        }

        {
            final CollectorEditor rootPathEditor = new CollectorEditor(mRootGoodTagIdPath);
            rootPathEditor.visit(mRootView);
            assertEquals(rootPathEditor.collected.size(), 1);
            assertEquals(rootPathEditor.collected.get(0), mRootView);
        }

        {
            final CollectorEditor rootPathFails1 = new CollectorEditor(mFailingRootPath1);
            rootPathFails1.visit(mRootView);
            assertEquals(rootPathFails1.collected.size(), 0);
        }

        {
            final CollectorEditor rootPathFails3 = new CollectorEditor(mFailingRootPath3);
            rootPathFails3.visit(mRootView);
            assertEquals(rootPathFails3.collected.size(), 0);
        }

        {
            final CollectorEditor rootPathFails4 = new CollectorEditor(mFailingRootPath4);
            rootPathFails4.visit(mRootView);
            assertEquals(rootPathFails4.collected.size(), 0);
        }

        {
            final CollectorEditor rootPathFails5 = new CollectorEditor(mRootBadTagIdPath);
            rootPathFails5.visit(mRootView);
            assertEquals(rootPathFails5.collected.size(), 0);
        }

        {
            final CollectorEditor collector = new CollectorEditor(mFindText2DescriptionPath);
            collector.visit(mRootView);
            assertEquals(collector.collected.size(), 1);
            assertEquals(collector.collected.get(0), mRootView.mTextView2);
        }

        {
            final CollectorEditor collector = new CollectorEditor(mFailText2DescriptionPath);
            collector.visit(mRootView);
            assertEquals(collector.collected.size(), 0);
        }

        {
            final CollectorEditor collector = new CollectorEditor(mThirdLayerViewId);
            collector.visit(mRootView);
            assertEquals(collector.collected.size(), 1);
            assertEquals(collector.collected.get(0), mRootView.mTextView1);
        }

        {
            final CollectorEditor collector = new CollectorEditor(mThirdLayerViewTag);
            collector.visit(mRootView);
            assertEquals(collector.collected.size(), 1);
            assertEquals(collector.collected.get(0), mRootView.mTextView1);
        }

        {
            final CollectorEditor collector = new CollectorEditor(mThirdLayerWildcard);
            collector.visit(mRootView);
            assertEquals(collector.collected.size(), mRootView.mThirdLayer.size());
            final Set<View> allFound = new HashSet<View>(collector.collected);
            assertEquals(mRootView.mThirdLayer, allFound);
        }

        {
            final CollectorEditor collector = new CollectorEditor(mFindRootIdPath);
            collector.visit(mRootView);
            assertEquals(collector.collected.size(), 1);
            assertEquals(mRootView, collector.collected.get(0));
        }

        {
            final CollectorEditor collector = new CollectorEditor(mFindNonsenseIdPath);
            collector.visit(mRootView);
            assertEquals(collector.collected.size(), 0);
        }

        {
            final CollectorEditor collector = new CollectorEditor(mFindTextViewIdPath);
            collector.visit(mRootView);
            assertEquals(collector.collected.size(), 1);
            assertEquals(collector.collected.get(0), mRootView.mTextView1);
        }

        {
            final CollectorEditor collector = new CollectorEditor(mFailTextViewIdPath);
            collector.visit(mRootView);
            assertEquals(collector.collected.size(), 0);
        }

        {
            final CollectorEditor collector = new CollectorEditor(mFirstInButtonGroup);
            collector.visit(mRootView);
            assertEquals(collector.collected.size(), 1);
            assertEquals(collector.collected.get(0), mRootView.mAdHocButton1);
        }

        {
            final CollectorEditor collector = new CollectorEditor(mFindButtonGroupInRoot);
            collector.visit(mRootView);
            assertEquals(collector.collected.size(), 1);
            assertEquals(collector.collected.get(0), mRootView.mButtonGroup);
        }

        {
            final CollectorEditor collector = new CollectorEditor(mFindButton2);
            collector.visit(mRootView);
            assertEquals(collector.collected.size(), 1);
            assertEquals(collector.collected.get(0), mRootView.mAdHocButton2);
        }
    }

    public void testClickTracking() {
        final ViewVisitor.AddAccessibilityEventVisitor visitor = new ViewVisitor.AddAccessibilityEventVisitor(mButton2Path, AccessibilityEvent.TYPE_VIEW_CLICKED, "Visitor1", mTrackListener);
        visitor.visit(mRootView);
        assertTrue(mTrackListener.events.isEmpty());

        mRootView.mAdHocButton1.performClick();
        assertTrue(mTrackListener.events.isEmpty());

        mRootView.mAdHocButton2.performClick();
        assertEquals(mTrackListener.events.size(), 1);
        assertEquals(mTrackListener.events.get(0), "Visitor1");

        mRootView.mAdHocButton3.performClick();
        assertEquals(mTrackListener.events.size(), 1);
        assertEquals(mTrackListener.events.get(0), "Visitor1");
    }

    public void testMultipleEventsOnClick() {
        if (Build.VERSION.SDK_INT <= 16) {
            return; // We don't actually support multiple handlers on Jellybean.
        }

        final ViewVisitor.AddAccessibilityEventVisitor visitor1 =
                new ViewVisitor.AddAccessibilityEventVisitor(mButton2Path, AccessibilityEvent.TYPE_VIEW_CLICKED, "Visitor1", mTrackListener);
        visitor1.visit(mRootView);
        assertTrue(mTrackListener.events.isEmpty());

        mRootView.mAdHocButton2.performClick();
        assertEquals(mTrackListener.events.size(), 1);
        assertEquals(mTrackListener.events.get(0), "Visitor1");
        mTrackListener.events.clear();

        final ViewVisitor.AddAccessibilityEventVisitor visitor2 =
                new ViewVisitor.AddAccessibilityEventVisitor(mButton2Path, AccessibilityEvent.TYPE_VIEW_CLICKED, "Visitor2", mTrackListener);
        visitor2.visit(mRootView);

        mRootView.mAdHocButton2.performClick();
        assertEquals(mTrackListener.events.size(), 2);
        assertTrue(mTrackListener.events.contains("Visitor1"));
        assertTrue(mTrackListener.events.contains("Visitor2"));

        visitor1.cleanup();
        mTrackListener.events.clear();

        mRootView.mAdHocButton2.performClick();
        assertEquals(mTrackListener.events.size(), 1);
        assertTrue(mTrackListener.events.contains("Visitor2"));

        visitor2.cleanup();
        mTrackListener.events.clear();

        mRootView.mAdHocButton2.performClick();
        assertEquals(mTrackListener.events.size(), 0);
    }

    public void testResetSameEventOnClick() {
        final ViewVisitor.AddAccessibilityEventVisitor visitor1 =
                new ViewVisitor.AddAccessibilityEventVisitor(mButton2Path, AccessibilityEvent.TYPE_VIEW_CLICKED, "Visitor1", mTrackListener);
        visitor1.visit(mRootView);
        visitor1.visit(mRootView);

        mRootView.mAdHocButton2.performClick();
        assertEquals(mTrackListener.events.size(), 1);
        assertEquals(mTrackListener.events.get(0), "Visitor1");
        mTrackListener.events.clear();
    }

    public void testDuplicateBitmapSet() throws NoSuchMethodException {
        final Caller mutateBitmap1a = new Caller(TestView.AdHocButton2.class, "setCountingProperty", new Object[] { mBitmap1a }, Void.TYPE);
        final Caller mutateBitmap1b = new Caller(TestView.AdHocButton2.class, "setCountingProperty", new Object[] { mBitmap1b }, Void.TYPE);
        final Caller mutateBitmap2 = new Caller(TestView.AdHocButton2.class, "setCountingProperty", new Object[] { mBitmap2 }, Void.TYPE);
        final Caller accessor = new Caller(TestView.AdHocButton2.class, "getCountingProperty", new Object[]{}, Object.class);

        {
            final ViewVisitor propertySetVisitor1_1 =
                    new ViewVisitor.PropertySetVisitor(mButton2Path, mutateBitmap1a, accessor);

            propertySetVisitor1_1.visit(mRootView);
            assertEquals(mRootView.mAdHocButton2.countingPropertyCount, 1);
            assertEquals(mRootView.mAdHocButton2.countingPropertyValue, mBitmap1a);

            propertySetVisitor1_1.visit(mRootView);
            assertEquals(mRootView.mAdHocButton2.countingPropertyCount, 1);
        }

        {
            final ViewVisitor propertySetVisitor1_2 =
                    new ViewVisitor.PropertySetVisitor(mButton2Path, mutateBitmap1b, accessor);

            propertySetVisitor1_2.visit(mRootView);
            assertEquals(mRootView.mAdHocButton2.countingPropertyCount, 1);
            assertEquals(mRootView.mAdHocButton2.countingPropertyValue, mBitmap1a);
        }

        {
            final ViewVisitor propertySetVisitor2 =
                    new ViewVisitor.PropertySetVisitor(mButton2Path, mutateBitmap2, accessor);
            propertySetVisitor2.visit(mRootView);
            assertEquals(mRootView.mAdHocButton2.countingPropertyCount, 2);
            assertEquals(mRootView.mAdHocButton2.countingPropertyValue, mBitmap2);
        }
    }

    public void testDuplicateDrawableSet() throws NoSuchMethodException {
        final BitmapDrawable drawable1a_1 = new BitmapDrawable(Resources.getSystem(), mBitmap1a);
        final BitmapDrawable drawable1a_2 = new BitmapDrawable(Resources.getSystem(), mBitmap1a);
        final BitmapDrawable drawable2 = new BitmapDrawable(Resources.getSystem(), mBitmap2);
        final Caller mutateDrawable1a_1 = new Caller(TestView.AdHocButton2.class, "setCountingProperty", new Object[] { drawable1a_1 }, Void.TYPE);
        final Caller mutateDrawable1a_2 = new Caller(TestView.AdHocButton2.class, "setCountingProperty", new Object[] { drawable1a_2 }, Void.TYPE);
        final Caller mutateDrawable2 = new Caller(TestView.AdHocButton2.class, "setCountingProperty", new Object[] { drawable2 }, Void.TYPE);
        final Caller accessor = new Caller(TestView.AdHocButton2.class, "getCountingProperty", new Object[]{}, Object.class);

        {
            final ViewVisitor propertySetVisitor1_1 =
                    new ViewVisitor.PropertySetVisitor(mButton2Path, mutateDrawable1a_1, accessor);

            propertySetVisitor1_1.visit(mRootView);
            assertEquals(mRootView.mAdHocButton2.countingPropertyCount, 1);
            assertEquals(mRootView.mAdHocButton2.countingPropertyValue, drawable1a_1);

            propertySetVisitor1_1.visit(mRootView);
            assertEquals(mRootView.mAdHocButton2.countingPropertyCount, 1);
        }

        {
            final ViewVisitor propertySetVisitor1_2 =
                    new ViewVisitor.PropertySetVisitor(mButton2Path, mutateDrawable1a_2, accessor);

            propertySetVisitor1_2.visit(mRootView);
            assertEquals(mRootView.mAdHocButton2.countingPropertyCount, 1);
            assertEquals(mRootView.mAdHocButton2.countingPropertyValue, drawable1a_1);
        }

        {
            final ViewVisitor propertySetVisitor2 =
                    new ViewVisitor.PropertySetVisitor(mButton2Path, mutateDrawable2, accessor);
            propertySetVisitor2.visit(mRootView);
            assertEquals(mRootView.mAdHocButton2.countingPropertyCount, 2);
            assertEquals(mRootView.mAdHocButton2.countingPropertyValue, drawable2);
        }
    }

    public void testDuplicateTextSet() throws NoSuchMethodException {
        final Caller mutateCountingProperty1a = new Caller(TestView.AdHocButton2.class, "setCountingProperty", new Object[]{"Set String1"}, Void.TYPE);
        final Caller mutateCountingProperty1b = new Caller(TestView.AdHocButton2.class, "setCountingProperty", new Object[]{"Set String1"}, Void.TYPE);
        final Caller mutateCountingProperty2 = new Caller(TestView.AdHocButton2.class, "setCountingProperty", new Object[]{"Set String2"}, Void.TYPE);
        final Caller accessCountingProperty = new Caller(TestView.AdHocButton2.class, "getCountingProperty", new Object[]{}, Object.class);

        {
            final ViewVisitor propertySetVisitor1_1 =
                    new ViewVisitor.PropertySetVisitor(mButton2Path, mutateCountingProperty1a, accessCountingProperty);

            propertySetVisitor1_1.visit(mRootView);
            assertEquals(mRootView.mAdHocButton2.countingPropertyCount, 1);
            assertEquals(mRootView.mAdHocButton2.countingPropertyValue, "Set String1");

            propertySetVisitor1_1.visit(mRootView);
            assertEquals(mRootView.mAdHocButton2.countingPropertyCount, 1);
        }

        {
            final ViewVisitor propertySetVisitor1_2 =
                    new ViewVisitor.PropertySetVisitor(mButton2Path, mutateCountingProperty1a, accessCountingProperty);
            propertySetVisitor1_2.visit(mRootView);

            assertEquals(mRootView.mAdHocButton2.countingPropertyCount, 1);
            assertEquals(mRootView.mAdHocButton2.countingPropertyValue, "Set String1");
        }

        {
            final ViewVisitor propertySetVisitor1_3 =
                    new ViewVisitor.PropertySetVisitor(mButton2Path, mutateCountingProperty1b, accessCountingProperty);
            propertySetVisitor1_3.visit(mRootView);

            assertEquals(mRootView.mAdHocButton2.countingPropertyCount, 1);
            assertEquals(mRootView.mAdHocButton2.countingPropertyValue, "Set String1");
        }

        {
            final ViewVisitor propertySetVisitor2 =
                    new ViewVisitor.PropertySetVisitor(mButton2Path, mutateCountingProperty2, accessCountingProperty);
            propertySetVisitor2.visit(mRootView);

            assertEquals(mRootView.mAdHocButton2.countingPropertyCount, 2);
            assertEquals(mRootView.mAdHocButton2.countingPropertyValue, "Set String2");
        }
    }

    public void testLayoutBasicInteraction() {
        ArrayList<ViewVisitor.LayoutRule> params = new ArrayList<ViewVisitor.LayoutRule>();
        // add LAYOUT_ALIGNPARENTTOP
        params.add(new ViewVisitor.LayoutRule(TestView.RELATIVE_LAYOUT_BUTTON1_ID, RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE));
        // add "LAYOUT_BELOW mRelativeLayoutButton2" to mRelativeLayoutButton1
        params.add(new ViewVisitor.LayoutRule(TestView.RELATIVE_LAYOUT_BUTTON1_ID, RelativeLayout.BELOW, TestView.RELATIVE_LAYOUT_BUTTON2_ID));
        // add LAYOUT_ALIGNPARENTBOTTOM to mRelativeLayoutButton1
        params.add(new ViewVisitor.LayoutRule(TestView.RELATIVE_LAYOUT_BUTTON1_ID, RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE));
        // remove LAYOUT_ALIGNPARENTBOTTOM from mRelativeLayoutButton1
        params.add(new ViewVisitor.LayoutRule(TestView.RELATIVE_LAYOUT_BUTTON1_ID, RelativeLayout.ALIGN_PARENT_BOTTOM, TestView.NO_ANCHOR));

        final ViewVisitor layoutVisitor =
                new ViewVisitor.LayoutUpdateVisitor(mRelativeLayoutPath, params, "test", mLayoutErrorListener);
        layoutVisitor.visit(mRootView);

        RelativeLayout.LayoutParams layoutParams =
                (RelativeLayout.LayoutParams) mRootView.mRelativeLayoutButton1.getLayoutParams();
        int[] rules = layoutParams.getRules();
        assertEquals(RelativeLayout.TRUE, rules[RelativeLayout.ALIGN_PARENT_TOP]);
        assertEquals(TestView.RELATIVE_LAYOUT_BUTTON2_ID, rules[RelativeLayout.BELOW]);
        assertEquals(TestView.NO_ANCHOR, rules[RelativeLayout.ALIGN_PARENT_BOTTOM]);

        assertEquals(true, mLayoutErrorListener.errorList.isEmpty());
    }

    public void testLayoutCircularDependency() {
        {   // vertical layout circle
            ArrayList<ViewVisitor.LayoutRule> params = new ArrayList<ViewVisitor.LayoutRule>();
            // add ALIGN_PARENT_TOP
            params.add(new ViewVisitor.LayoutRule(TestView.RELATIVE_LAYOUT_BUTTON1_ID, RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE));
            // add "BELOW mRelativeLayoutButton2" to mRelativeLayoutButton1, should success
            params.add(new ViewVisitor.LayoutRule(TestView.RELATIVE_LAYOUT_BUTTON1_ID, RelativeLayout.BELOW, TestView.RELATIVE_LAYOUT_BUTTON2_ID));
            // add "ALIGN_LEFT mRelativeLayoutButton1" to mRelativeLayoutButton2, should success
            params.add(new ViewVisitor.LayoutRule(TestView.RELATIVE_LAYOUT_BUTTON2_ID, RelativeLayout.ALIGN_LEFT, TestView.RELATIVE_LAYOUT_BUTTON1_ID));
            // add "BELOW mRelativeLayoutButton1" to mRelativeLayoutButton2, should fail
            params.add(new ViewVisitor.LayoutRule(TestView.RELATIVE_LAYOUT_BUTTON2_ID, RelativeLayout.ABOVE, TestView.RELATIVE_LAYOUT_BUTTON1_ID));

            final ViewVisitor layoutVisitor =
                    new ViewVisitor.LayoutUpdateVisitor(mRelativeLayoutPath, params, "test1", mLayoutErrorListener);
            layoutVisitor.visit(mRootView);

            RelativeLayout.LayoutParams layoutParamsButton1 =
                    (RelativeLayout.LayoutParams) mRootView.mRelativeLayoutButton1.getLayoutParams();
            int[] rulesButton1 = layoutParamsButton1.getRules();
            assertEquals(TestView.NO_ANCHOR, rulesButton1[RelativeLayout.ALIGN_PARENT_TOP]);
            assertEquals(TestView.NO_ANCHOR, rulesButton1[RelativeLayout.BELOW]);

            RelativeLayout.LayoutParams layoutParamsButton2 =
                    (RelativeLayout.LayoutParams) mRootView.mRelativeLayoutButton2.getLayoutParams();
            int[] rulesButton2 = layoutParamsButton2.getRules();
            assertEquals(TestView.NO_ANCHOR, rulesButton2[RelativeLayout.ALIGN_LEFT]);
            assertEquals(TestView.NO_ANCHOR, rulesButton2[RelativeLayout.ABOVE]);

            ViewVisitor.LayoutErrorMessage e = mLayoutErrorListener.errorList.get(0);
            assertEquals("circular_dependency", e.getErrorType());
            assertEquals("test1", e.getName());
            mLayoutErrorListener.errorList.clear();
        }

        {   // horizontal layout circle
            ArrayList<ViewVisitor.LayoutRule> params = new ArrayList<ViewVisitor.LayoutRule>();
            // add ALIGN_PARENT_BOTTOM to mRelativeLayoutButton1, should success
            params.add(new ViewVisitor.LayoutRule(TestView.RELATIVE_LAYOUT_BUTTON1_ID, RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE));
            // add "LEFT_OF mRelativeLayoutButton2" to mRelativeLayoutButton1, should success
            params.add(new ViewVisitor.LayoutRule(TestView.RELATIVE_LAYOUT_BUTTON1_ID, RelativeLayout.LEFT_OF, TestView.RELATIVE_LAYOUT_BUTTON2_ID));
            // add "ALIGN_BOTTOM mRelativeLayoutButton1" to mRelativeLayoutButton2, should success
            params.add(new ViewVisitor.LayoutRule(TestView.RELATIVE_LAYOUT_BUTTON2_ID, RelativeLayout.ALIGN_BOTTOM, TestView.RELATIVE_LAYOUT_BUTTON1_ID));
            // add "ALIGN_RIGHT mRelativeLayoutButton1" to mRelativeLayoutButton2, should fail
            params.add(new ViewVisitor.LayoutRule(TestView.RELATIVE_LAYOUT_BUTTON2_ID, RelativeLayout.ALIGN_RIGHT, TestView.RELATIVE_LAYOUT_BUTTON1_ID));

            final ViewVisitor layoutVisitor =
                    new ViewVisitor.LayoutUpdateVisitor(mRelativeLayoutPath, params, "test2", mLayoutErrorListener);
            layoutVisitor.visit(mRootView);

            RelativeLayout.LayoutParams layoutParamsButton1 =
                    (RelativeLayout.LayoutParams) mRootView.mRelativeLayoutButton1.getLayoutParams();
            int[] rulesButton1 = layoutParamsButton1.getRules();
            assertEquals(TestView.NO_ANCHOR, rulesButton1[RelativeLayout.ALIGN_PARENT_BOTTOM]);
            assertEquals(TestView.NO_ANCHOR, rulesButton1[RelativeLayout.LEFT_OF]);

            RelativeLayout.LayoutParams layoutParamsButton2 =
                    (RelativeLayout.LayoutParams) mRootView.mRelativeLayoutButton2.getLayoutParams();
            int[] rulesButton2 = layoutParamsButton2.getRules();
            assertEquals(TestView.NO_ANCHOR, rulesButton2[RelativeLayout.ALIGN_BOTTOM]);
            assertEquals(TestView.NO_ANCHOR, rulesButton2[RelativeLayout.ALIGN_RIGHT]);

            ViewVisitor.LayoutErrorMessage e = mLayoutErrorListener.errorList.get(0);
            assertEquals("circular_dependency", e.getErrorType());
            assertEquals("test2", e.getName());
            mLayoutErrorListener.errorList.clear();
        }

        {   // mix of vertical and horizontal layouts, no circle
            ArrayList<ViewVisitor.LayoutRule> params = new ArrayList<ViewVisitor.LayoutRule>();
            // add ALIGN_PARENT_BOTTOM to mRelativeLayoutButton1, should success
            params.add(new ViewVisitor.LayoutRule(TestView.RELATIVE_LAYOUT_BUTTON1_ID, RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE));
            // add "LEFT_OF mRelativeLayoutButton2" to mRelativeLayoutButton1, should success
            params.add(new ViewVisitor.LayoutRule(TestView.RELATIVE_LAYOUT_BUTTON1_ID, RelativeLayout.LEFT_OF, TestView.RELATIVE_LAYOUT_BUTTON2_ID));
            // add "ALIGN_LEFT mRelativeLayoutButton1" to mRelativeLayoutButton2, should success
            params.add(new ViewVisitor.LayoutRule(TestView.RELATIVE_LAYOUT_BUTTON2_ID, RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE));
            // add "BELOW mRelativeLayoutButton1" to mRelativeLayoutButton2, should success
            params.add(new ViewVisitor.LayoutRule(TestView.RELATIVE_LAYOUT_BUTTON2_ID, RelativeLayout.BELOW, TestView.RELATIVE_LAYOUT_BUTTON1_ID));

            final ViewVisitor layoutVisitor =
                    new ViewVisitor.LayoutUpdateVisitor(mRelativeLayoutPath, params, "test3", mLayoutErrorListener);
            layoutVisitor.visit(mRootView);

            RelativeLayout.LayoutParams layoutParamsButton1 =
                    (RelativeLayout.LayoutParams) mRootView.mRelativeLayoutButton1.getLayoutParams();
            int[] rulesButton1 = layoutParamsButton1.getRules();
            assertEquals(RelativeLayout.TRUE, rulesButton1[RelativeLayout.ALIGN_PARENT_BOTTOM]);
            assertEquals(TestView.RELATIVE_LAYOUT_BUTTON2_ID, rulesButton1[RelativeLayout.LEFT_OF]);

            RelativeLayout.LayoutParams layoutParamsButton2 =
                    (RelativeLayout.LayoutParams) mRootView.mRelativeLayoutButton2.getLayoutParams();
            int[] rulesButton2 = layoutParamsButton2.getRules();
            assertEquals(RelativeLayout.TRUE, rulesButton2[RelativeLayout.ALIGN_PARENT_TOP]);
            assertEquals(TestView.RELATIVE_LAYOUT_BUTTON1_ID, rulesButton2[RelativeLayout.BELOW]);

            assertEquals(true, mLayoutErrorListener.errorList.isEmpty());
        }
    }

    private static class CollectorEditor extends ViewVisitor {
        public CollectorEditor(List<Pathfinder.PathElement> path) {
            super(path);
            collected = new ArrayList<View>();
        }

        @Override
        public void cleanup() {}

        @Override
        public void accumulate(View targetView) {
            collected.add(targetView);
        }

        @Override
        protected String name() {
            return "CollectorEditor";
        };

        public List<View> collected;
    }

    private static class CollectingEventListener implements ViewVisitor.OnEventListener {

        @Override
        public void OnEvent(View v, String eventName, boolean debounce) {
            events.add(eventName);
        }

        public final List<String> events = new ArrayList<String>();
    }

    private Bitmap mBitmap1a;
    private Bitmap mBitmap1b;
    private Bitmap mBitmap2;

    private List<Pathfinder.PathElement> mButton2Path;
    private List<Pathfinder.PathElement> mWorkingRootPath1;
    private List<Pathfinder.PathElement> mWorkingRootPath2;
    private List<Pathfinder.PathElement> mFailingRootPath1;
    private List<Pathfinder.PathElement> mFailingRootPath2;
    private List<Pathfinder.PathElement> mFailingRootPath3;
    private List<Pathfinder.PathElement> mFailingRootPath4;
    private List<Pathfinder.PathElement> mFindRootIdPath;
    private List<Pathfinder.PathElement> mFindNonsenseIdPath;
    private List<Pathfinder.PathElement> mFindText2DescriptionPath;
    private List<Pathfinder.PathElement> mFailText2DescriptionPath;
    private List<Pathfinder.PathElement> mFindTextViewIdPath;
    private List<Pathfinder.PathElement> mFailTextViewIdPath;
    private List<Pathfinder.PathElement> mFirstInButtonGroup;
    private List<Pathfinder.PathElement> mFindButtonGroupInRoot;
    private List<Pathfinder.PathElement> mFindButton2;
    private List<Pathfinder.PathElement> mRelativeLayoutPath;
    private List<Pathfinder.PathElement> mRelativeLayoutButtonPath;

    private List<Pathfinder.PathElement> mRootWildcardPath;
    private List<Pathfinder.PathElement> mRootGoodTagIdPath;
    private List<Pathfinder.PathElement> mRootBadTagIdPath;
    private List<Pathfinder.PathElement> mThirdLayerViewId;
    private List<Pathfinder.PathElement> mThirdLayerViewTag;
    private List<Pathfinder.PathElement> mThirdLayerWildcard;
    private CollectingEventListener mTrackListener;
    private TestView.MockOnLayoutErrorListener mLayoutErrorListener;
    private TestView mRootView;
}
