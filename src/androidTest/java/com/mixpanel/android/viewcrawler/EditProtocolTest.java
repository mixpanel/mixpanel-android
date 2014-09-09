package com.mixpanel.android.viewcrawler;

import android.test.AndroidTestCase;
import android.util.Property;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


public class EditProtocolTest extends AndroidTestCase {
    @Override
    public void setUp() throws JSONException {
        mProtocol = new EditProtocol();
        mSnapshotConfig = new JSONObject(SNAPSHOT_CONFIG);
        mPropertyEdit = new JSONObject(PROPERTY_EDIT);
        mClickEvent = new JSONObject(CLICK_EVENT);
        mAppearsEvent = new JSONObject(APPEARS_EVENT);
        mListener = new TestVisitedListener();
        mRootView = new TestView(getContext());
    }

    public void testSnapshotConfig() throws EditProtocol.BadInstructionsException {
        final ViewSnapshot snapshot = mProtocol.readSnapshotConfig(mSnapshotConfig);
        final List<PropertyDescription> properties = snapshot.getProperties();

        assertEquals(properties.size(), 4);
        final PropertyDescription prop1 = properties.get(0);
        final PropertyDescription prop2 = properties.get(1);
        final PropertyDescription prop3 = properties.get(2);
        final PropertyDescription prop4 = properties.get(3);

        assertEquals(prop1.name, "importantForAccessibility");
        assertEquals(prop2.name, "backgroundColor");
        assertEquals(prop3.name, "text");
        assertEquals(prop4.name, "image");

        assertEquals(prop1.targetClass, View.class);
        assertEquals(prop2.targetClass, View.class);
        assertEquals(prop3.targetClass, TextView.class);
        assertEquals(prop4.targetClass, ImageView.class);
    }

    public void testPropertyEdit() throws EditProtocol.BadInstructionsException {
        final ViewVisitor visitor = mProtocol.readEdit(mPropertyEdit, mListener);
        visitor.visit(mRootView);
        assertEquals(mRootView.mAdHocButton2.getText(), "Ground Control to Major Tom");
    }

    public void testClickEvent() throws EditProtocol.BadInstructionsException {
        final ViewVisitor eventListener = mProtocol.readEdit(mClickEvent, mListener);
        fail("WRITE THIS TEST");
    }

    public void testAppearsEvent() throws EditProtocol.BadInstructionsException {
        final ViewVisitor appearsListener = mProtocol.readEdit(mAppearsEvent, mListener);
        fail("WRITE THIS TEST");
    }

    private static class TestVisitedListener implements ViewVisitor.OnVisitedListener {
        @Override
        public void OnVisited(String eventName) {
            visitsRecorded.add(eventName);
        }

        public List<String> visitsRecorded = new ArrayList<String>();
    }

    private EditProtocol mProtocol;
    private JSONObject mSnapshotConfig;
    private JSONObject mPropertyEdit;
    private JSONObject mClickEvent;
    private JSONObject mAppearsEvent;
    private TestVisitedListener mListener;
    private TestView mRootView;

    private final String SNAPSHOT_CONFIG = "{\"classes\":[{\"name\":\"android.view.View\",\"properties\":[{\"name\":\"importantForAccessibility\",\"get\":{\"selector\":\"isImportantForAccessibility\",\"parameters\":[],\"result\":{\"type\":\"java.lang.Boolean\"}}},{\"name\":\"backgroundColor\",\"get\":{\"selector\":\"getBackgroundColor\",\"parameters\":[],\"result\":{\"type\":\"java.lang.Integer\"}},\"set\":{\"selector\":\"setBackgroundColor\",\"parameters\":[{\"type\":\"java.lang.Integer\"}]},\"editor\":\"hexstring\"}]},{\"name\":\"android.widget.TextView\",\"properties\":[{\"name\":\"text\",\"get\":{\"selector\":\"getText\",\"parameters\":[],\"result\":{\"type\":\"java.lang.CharSequence\"}},\"set\":{\"selector\":\"setText\",\"parameters\":[{\"type\":\"java.lang.CharSequence\"}]}}]},{\"name\":\"android.widget.ImageView\",\"properties\":[{\"name\":\"image\",\"set\":{\"selector\":\"setImageBitmap\",\"parameters\":[{\"type\":\"android.graphics.Bitmap\"}]}}]}]}";
    private final String PROPERTY_EDIT = "{\"path\":[{\"view_class\":\"com.mixpanel.android.viewcrawler.TestView\",\"index\":0},{\"view_class\":\"android.widget.LinearLayout\",\"index\":0},{\"view_class\":\"android.widget.LinearLayout\",\"index\":0},{\"view_class\":\"android.widget.Button\",\"index\":1}],\"property\":{\"name\":\"text\",\"get\":{\"selector\":\"getText\",\"parameters\":[],\"result\":{\"type\":\"java.lang.CharSequence\"}},\"set\":{\"selector\":\"setText\",\"parameters\":[{\"type\":\"java.lang.CharSequence\"}]}},\"args\":[[\"Ground Control to Major Tom\",\"java.lang.CharSequence\"]]}";
    private final String CLICK_EVENT = "{\"type\":\"change_request\",\"target\":\"org.wordpress.android.ui.posts.PostsActivity\",\"change\":{\"path\":[{\"view_class\":\"com.android.internal.policy.impl.PhoneWindow.DecorView\",\"index\":0},{\"view_class\":\"com.android.internal.widget.ActionBarOverlayLayout\",\"index\":0},{\"view_class\":\"com.android.internal.widget.ActionBarContainer\",\"index\":0},{\"view_class\":\"com.android.internal.widget.ActionBarView\",\"index\":0},{\"view_class\":\"android.widget.LinearLayout\",\"index\":0},{\"view_class\":\"android.widget.LinearLayout\",\"index\":0},{\"view_class\":\"android.widget.TextView\",\"index\":0}],\"event_type\":\"click\",\"event_name\":\"Commencing Count-Down\"}}";
    private final String APPEARS_EVENT = "{\"type\":\"change_request\",\"target\":\"org.wordpress.android.ui.posts.PostsActivity\",\"change\":{\"path\":[{\"view_class\":\"com.android.internal.policy.impl.PhoneWindow.DecorView\",\"index\":0},{\"view_class\":\"com.android.internal.widget.ActionBarOverlayLayout\",\"index\":0},{\"view_class\":\"com.android.internal.widget.ActionBarContainer\",\"index\":0},{\"view_class\":\"com.android.internal.widget.ActionBarView\",\"index\":0},{\"view_class\":\"android.widget.LinearLayout\",\"index\":0},{\"view_class\":\"android.widget.LinearLayout\",\"index\":0},{\"view_class\":\"android.widget.TextView\",\"index\":0}],\"event_type\":\"detected\",\"event_name\":\"Engines On!\"}}";
}
