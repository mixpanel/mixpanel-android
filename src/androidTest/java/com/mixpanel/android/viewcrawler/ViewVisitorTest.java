package com.mixpanel.android.viewcrawler;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.test.ActivityUnitTestCase;
import android.test.AndroidTestCase;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ViewVisitorTest extends AndroidTestCase {
    public void setUp() throws Exception {
        super.setUp();

        mButton2Path = new ArrayList<ViewVisitor.PathElement>();
        mButton2Path.add(new ViewVisitor.PathElement("com.mixpanel.android.viewcrawler.TestView", 0));
        mButton2Path.add(new ViewVisitor.PathElement("android.widget.LinearLayout", 0));
        mButton2Path.add(new ViewVisitor.PathElement("android.widget.LinearLayout", 0));
        mButton2Path.add(new ViewVisitor.PathElement("android.widget.Button", 1));

        mWorkingRootPath = new ArrayList<ViewVisitor.PathElement>();
        mWorkingRootPath.add(new ViewVisitor.PathElement("java.lang.Object", 0));

        mFailingRootPath1 = new ArrayList<ViewVisitor.PathElement>();
        mFailingRootPath1.add(new ViewVisitor.PathElement("android.widget.Button", 0));

        mFailingRootPath2 = new ArrayList<ViewVisitor.PathElement>();
        mFailingRootPath2.add(new ViewVisitor.PathElement("java.lang.Object", 1));

        mTrackListener = new CollectingInteractionListener();

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
            final CollectorEditor rootPathEditor = new CollectorEditor(mWorkingRootPath);
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
            final CollectorEditor rootPathFails2 = new CollectorEditor(mFailingRootPath2);
            rootPathFails2.visit(mRootView);
            assertEquals(rootPathFails2.collected.size(), 0);
        }
    }

    public void testClickTracking() {
        final ViewVisitor.AddListenerVisitor visitor = new ViewVisitor.AddListenerVisitor(mButton2Path, "Visitor1", mTrackListener);
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
                new ViewVisitor.AddListenerVisitor(mButton2Path, "Visitor1", mTrackListener);
        visitor1.visit(mRootView);
        assertTrue(mTrackListener.events.isEmpty());

        mRootView.mAdHocButton2.performClick();
        assertEquals(mTrackListener.events.size(), 1);
        assertEquals(mTrackListener.events.get(0), "Visitor1");
        mTrackListener.events.clear();

        final ViewVisitor.AddListenerVisitor visitor2 =
                new ViewVisitor.AddListenerVisitor(mButton2Path, "Visitor2", mTrackListener);
        visitor2.visit(mRootView);

        mRootView.mAdHocButton2.performClick();
        assertEquals(mTrackListener.events.size(), 1);
        assertEquals(mTrackListener.events.get(0), "Visitor2");
    }

    public void testResetSameEventOnClick() {
        final ViewVisitor.AddListenerVisitor visitor1 =
                new ViewVisitor.AddListenerVisitor(mButton2Path, "Visitor1", mTrackListener);
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

    private static class CollectorEditor extends ViewVisitor.ViewEditor {
        public CollectorEditor(List<PathElement> path) {
            super(path);
            collected = new ArrayList<View>();
        }

        @Override
        protected void applyEdit(View targetView) {
            collected.add(targetView);
        }

        public List<View> collected;
    }

    private static class CollectingInteractionListener implements ViewVisitor.OnInteractionListener {

        @Override
        public void OnViewClicked(String eventName) {
            events.add(eventName);
        }

        public final List<String> events = new ArrayList<String>();
    }

    private List<ViewVisitor.PathElement> mButton2Path;
    private List<ViewVisitor.PathElement> mWorkingRootPath;
    private List<ViewVisitor.PathElement> mFailingRootPath1;
    private List<ViewVisitor.PathElement> mFailingRootPath2;
    private CollectingInteractionListener mTrackListener;
    private TestView mRootView;
}
