package com.mixpanel.android.viewcrawler;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.test.AndroidTestCase;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ViewVisitorTest extends AndroidTestCase {
    public void setUp() throws Exception {
        super.setUp();

        mButton2Path = new ArrayList<ViewVisitor.PathElement>();
        mButton2Path.add(new ViewVisitor.PathElement("com.mixpanel.android.viewcrawler.TestView", 0, -1, null));
        mButton2Path.add(new ViewVisitor.PathElement("android.widget.LinearLayout", 0, -1, null));
        mButton2Path.add(new ViewVisitor.PathElement("android.widget.LinearLayout", 0, -1, null));
        mButton2Path.add(new ViewVisitor.PathElement("android.widget.Button", 1, -1, null));

        mWorkingRootPath1 = new ArrayList<ViewVisitor.PathElement>();
        mWorkingRootPath1.add(new ViewVisitor.PathElement("java.lang.Object", 0, -1, null));

        mWorkingRootPath2 = new ArrayList<ViewVisitor.PathElement>();
        mWorkingRootPath2.add(new ViewVisitor.PathElement(null, -1, -1, null));

        mFailingRootPath1 = new ArrayList<ViewVisitor.PathElement>();
        mFailingRootPath1.add(new ViewVisitor.PathElement("android.widget.Button", 0, -1, null));

        mFailingRootPath2 = new ArrayList<ViewVisitor.PathElement>();
        mFailingRootPath2.add(new ViewVisitor.PathElement("java.lang.Object", 1, -1, null));

        mFailingRootPath3 = new ArrayList<ViewVisitor.PathElement>();
        mFailingRootPath3.add(new ViewVisitor.PathElement("java.lang.Object", 0, 1234, null));

        mFailingRootPath4 = new ArrayList<ViewVisitor.PathElement>();
        mFailingRootPath4.add(new ViewVisitor.PathElement("java.lang.Object", 0, -1, "NO SUCH TAG"));

        mRootWildcardPath = new ArrayList<ViewVisitor.PathElement>();
        mRootWildcardPath.add(new ViewVisitor.PathElement(null, -1, -1, null));

        mRootGoodTagIdPath = new ArrayList<ViewVisitor.PathElement>();
        mRootGoodTagIdPath.add(new ViewVisitor.PathElement(null, -1, TestView.ROOT_ID, TestView.CRAZY_TAG));

        mRootBadTagIdPath = new ArrayList<ViewVisitor.PathElement>();
        mRootBadTagIdPath.add(new ViewVisitor.PathElement(null, -1, TestView.ROOT_ID, "NO DICE"));

        mThirdLayerViewId = new ArrayList<ViewVisitor.PathElement>();
        mThirdLayerViewId.add(new ViewVisitor.PathElement(null, -1, -1, null));
        mThirdLayerViewId.add(new ViewVisitor.PathElement(null, -1, -1, null));
        mThirdLayerViewId.add(new ViewVisitor.PathElement(null, -1, TestView.TEXT_VIEW_ID, null));

        mThirdLayerViewTag = new ArrayList<ViewVisitor.PathElement>();
        mThirdLayerViewTag.add(new ViewVisitor.PathElement(null, -1, -1, null));
        mThirdLayerViewTag.add(new ViewVisitor.PathElement(null, -1, -1, null));
        mThirdLayerViewTag.add(new ViewVisitor.PathElement(null, -1, TestView.TEXT_VIEW_ID, TestView.CRAZY_TAG));

        mThirdLayerWildcard = new ArrayList<ViewVisitor.PathElement>();
        mThirdLayerWildcard.add(new ViewVisitor.PathElement(null, -1, -1, null));
        mThirdLayerWildcard.add(new ViewVisitor.PathElement(null, -1, -1, null));
        mThirdLayerWildcard.add(new ViewVisitor.PathElement(null, -1, -1, null));

        mTrackListener = new CollectingVisitedListener();

        mRootView = new TestView(getContext());
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
            final Set<View> allFound = new HashSet(collector.collected);
            assertEquals(mRootView.mThirdLayer, allFound);
        }
    }

    public void testClickTracking() {
        final ViewVisitor.AddListenerVisitor visitor = new ViewVisitor.AddListenerVisitor(mButton2Path, AccessibilityEvent.TYPE_VIEW_CLICKED, "Visitor1", mTrackListener);
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
        final ViewVisitor.AddListenerVisitor visitor1 =
                new ViewVisitor.AddListenerVisitor(mButton2Path, AccessibilityEvent.TYPE_VIEW_CLICKED, "Visitor1", mTrackListener);
        visitor1.visit(mRootView);
        assertTrue(mTrackListener.events.isEmpty());

        mRootView.mAdHocButton2.performClick();
        assertEquals(mTrackListener.events.size(), 1);
        assertEquals(mTrackListener.events.get(0), "Visitor1");
        mTrackListener.events.clear();

        final ViewVisitor.AddListenerVisitor visitor2 =
                new ViewVisitor.AddListenerVisitor(mButton2Path, AccessibilityEvent.TYPE_VIEW_CLICKED, "Visitor2", mTrackListener);
        visitor2.visit(mRootView);

        mRootView.mAdHocButton2.performClick();
        assertEquals(mTrackListener.events.size(), 1);
        assertEquals(mTrackListener.events.get(0), "Visitor2");
    }

    public void testResetSameEventOnClick() {
        final ViewVisitor.AddListenerVisitor visitor1 =
                new ViewVisitor.AddListenerVisitor(mButton2Path, AccessibilityEvent.TYPE_VIEW_CLICKED, "Visitor1", mTrackListener);
        visitor1.visit(mRootView);
        visitor1.visit(mRootView);

        mRootView.mAdHocButton2.performClick();
        assertEquals(mTrackListener.events.size(), 1);
        assertEquals(mTrackListener.events.get(0), "Visitor1");
        mTrackListener.events.clear();
    }

    public void testDuplicateBitmapSet() {
        final Paint paint = new Paint();
        paint.setColor(Color.BLUE);

        final Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        final Bitmap bitmap1a = Bitmap.createBitmap(10, 10, conf); // this creates a MUTABLE bitmap
        final Canvas canvas1 = new Canvas(bitmap1a);
        canvas1.drawCircle(3, 3, 4, paint);

        final Bitmap bitmap1b = Bitmap.createBitmap(bitmap1a);

        final Bitmap bitmap2 = Bitmap.createBitmap(10, 10, conf);
        final Canvas canvas2 = new Canvas(bitmap2);
        canvas2.drawCircle(6, 6, 4, paint);

        final Caller mutateBitmap1a = new Caller("setCountingProperty", new Object[] { bitmap1a }, Void.TYPE);
        final Caller mutateBitmap1b = new Caller("setCountingProperty", new Object[] { bitmap1b }, Void.TYPE);
        final Caller mutateBitmap2 = new Caller("setCountingProperty", new Object[] { bitmap2 }, Void.TYPE);
        final Caller accessBitmap = new Caller("getCountingProperty", new Object[]{}, Object.class);

        {
            final ViewVisitor propertySetVisitor1_1 =
                    new ViewVisitor.PropertySetVisitor(mButton2Path, mutateBitmap1a, accessBitmap);

            propertySetVisitor1_1.visit(mRootView);
            assertEquals(mRootView.mAdHocButton2.countingPropertyCount, 1);
            assertEquals(mRootView.mAdHocButton2.countingPropertyValue, bitmap1a);

            propertySetVisitor1_1.visit(mRootView);
            assertEquals(mRootView.mAdHocButton2.countingPropertyCount, 1);
        }

        {
            final ViewVisitor propertySetVisitor1_2 =
                    new ViewVisitor.PropertySetVisitor(mButton2Path, mutateBitmap1b, accessBitmap);

            propertySetVisitor1_2.visit(mRootView);
            assertEquals(mRootView.mAdHocButton2.countingPropertyCount, 1);
            assertEquals(mRootView.mAdHocButton2.countingPropertyValue, bitmap1a);
        }

        {
            final ViewVisitor propertySetVisitor2 =
                    new ViewVisitor.PropertySetVisitor(mButton2Path, mutateBitmap2, accessBitmap);
            propertySetVisitor2.visit(mRootView);
            assertEquals(mRootView.mAdHocButton2.countingPropertyCount, 2);
            assertEquals(mRootView.mAdHocButton2.countingPropertyValue, bitmap2);
        }
    }

    public void testDuplicateTextSet() {
        final Caller mutateCountingProperty1a = new Caller("setCountingProperty", new Object[]{"Set String1"}, Void.TYPE);
        final Caller mutateCountingProperty1b = new Caller("setCountingProperty", new Object[]{"Set String1"}, Void.TYPE);
        final Caller mutateCountingProperty2 = new Caller("setCountingProperty", new Object[]{"Set String2"}, Void.TYPE);
        final Caller accessCountingProperty = new Caller("getCountingProperty", new Object[]{}, Object.class);

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

    private static class CollectorEditor extends ViewVisitor {
        public CollectorEditor(List<PathElement> path) {
            super(path);
            collected = new ArrayList<View>();
        }

        @Override
        protected void accumulate(View targetView) {
            collected.add(targetView);
        }

        public List<View> collected;
    }

    private static class CollectingVisitedListener implements ViewVisitor.OnVisitedListener {

        @Override
        public void OnVisited(View v, String eventName) {
            events.add(eventName);
        }

        public final List<String> events = new ArrayList<String>();
    }

    private List<ViewVisitor.PathElement> mButton2Path;
    private List<ViewVisitor.PathElement> mWorkingRootPath1;
    private List<ViewVisitor.PathElement> mWorkingRootPath2;
    private List<ViewVisitor.PathElement> mFailingRootPath1;
    private List<ViewVisitor.PathElement> mFailingRootPath2;
    private List<ViewVisitor.PathElement> mFailingRootPath3;
    private List<ViewVisitor.PathElement> mFailingRootPath4;

    private List<ViewVisitor.PathElement> mRootWildcardPath;
    private List<ViewVisitor.PathElement> mRootGoodTagIdPath;
    private List<ViewVisitor.PathElement> mRootBadTagIdPath;
    private List<ViewVisitor.PathElement> mThirdLayerViewId;
    private List<ViewVisitor.PathElement> mThirdLayerViewTag;
    private List<ViewVisitor.PathElement> mThirdLayerWildcard;
    private CollectingVisitedListener mTrackListener;
    private TestView mRootView;
}
