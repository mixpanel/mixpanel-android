package com.mixpanel.android.viewcrawler;

import android.test.AndroidTestCase;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class EditProtocolTest extends AndroidTestCase {
    @Override
    public void setUp() throws JSONException {
        mProtocol = new EditProtocol(getContext());
        mSnapshotConfig = new JSONObject(
            "{\"config\": {\"classes\":[{\"name\":\"android.view.View\",\"properties\":[{\"name\":\"importantForAccessibility\",\"get\":{\"selector\":\"isImportantForAccessibility\",\"parameters\":[],\"result\":{\"type\":\"java.lang.Boolean\"}}},{\"name\":\"backgroundColor\",\"get\":{\"selector\":\"getBackgroundColor\",\"parameters\":[],\"result\":{\"type\":\"java.lang.Integer\"}},\"set\":{\"selector\":\"setBackgroundColor\",\"parameters\":[{\"type\":\"java.lang.Integer\"}]},\"editor\":\"hexstring\"}]},{\"name\":\"android.widget.TextView\",\"properties\":[{\"name\":\"text\",\"get\":{\"selector\":\"getText\",\"parameters\":[],\"result\":{\"type\":\"java.lang.CharSequence\"}},\"set\":{\"selector\":\"setText\",\"parameters\":[{\"type\":\"java.lang.CharSequence\"}]}}]},{\"name\":\"android.widget.ImageView\",\"properties\":[{\"name\":\"image\",\"set\":{\"selector\":\"setImageBitmap\",\"parameters\":[{\"type\":\"android.graphics.Bitmap\"}]}}]}]}}"
        );
        mPropertyEdit = new JSONObject(
            "{\"path\":[{\"view_class\":\"com.mixpanel.android.viewcrawler.TestView\",\"index\":0},{\"view_class\":\"android.widget.LinearLayout\",\"index\":0},{\"view_class\":\"android.widget.LinearLayout\",\"index\":0},{\"view_class\":\"android.widget.Button\",\"index\":1}],\"property\":{\"name\":\"text\",\"get\":{\"selector\":\"getText\",\"parameters\":[],\"result\":{\"type\":\"java.lang.CharSequence\"}},\"set\":{\"selector\":\"setText\",\"parameters\":[{\"type\":\"java.lang.CharSequence\"}]}},\"args\":[[\"Ground Control to Major Tom\",\"java.lang.CharSequence\"]]}"
        );
        mClickEvent = new JSONObject(
            "{\"path\":[{\"view_class\":\"com.mixpanel.android.viewcrawler.TestView\",\"index\":0},{\"view_class\":\"android.widget.LinearLayout\",\"index\":0},{\"view_class\":\"android.widget.LinearLayout\",\"index\":0},{\"view_class\":\"android.widget.Button\",\"index\":1}],\"event_type\":\"click\",\"event_name\":\"Commencing Count-Down\"}"
        );
        mAppearsEvent = new JSONObject(
            "{\"path\":[{\"view_class\":\"com.mixpanel.android.viewcrawler.TestView\",\"index\":0},{\"view_class\":\"android.widget.LinearLayout\",\"index\":0},{\"view_class\":\"android.widget.LinearLayout\",\"index\":0},{\"view_class\":\"android.widget.Button\",\"index\":3}],\"event_type\":\"detected\",\"event_name\":\"Engines On!\"}"
        );
        mJustClassPath = new JSONArray("[{},{},{},{\"view_class\":\"android.widget.Button\"}]");
        mJustIdPath = new JSONArray("[{},{},{},{\"id\": 2000}]");
        mJustIndexPath = new JSONArray("[{},{},{},{\"index\": 2}]");
        mJustTagPath = new JSONArray("[{},{},{},{\"tag\": \"this_is_a_simple_tag\"}]");
        mJustIdNamePath = new JSONArray("[{},{},{},{\"mp_id_name\": \"NAME PRESENT\"}]");
        mIdNameAndIdPath = new JSONArray("[{},{},{},{\"mp_id_name\": \"NAME PRESENT\", \"id\": 1001}]");
        mJustFindIdPath = new JSONArray("[{},{},{},{\"**/id\": 1001}]");
        mJustFindNamePath = new JSONArray("[{},{},{},{\"**/mp_id_name\": \"NAME PRESENT\"}]");
        mUselessFindIdPath = new JSONArray("[{},{},{},{\"**/mp_id_name\": \"NAME PRESENT\", \"id\": 1001}]");

        mIdAndNameDontMatch = new JSONArray("[{},{},{},{\"mp_id_name\": \"NO SUCH NAME\", \"id\": 90210}]");
        mIdAndFindDontMatch = new JSONArray("[{},{},{},{\"mp_id_name\": \"NAME PRESENT\", \"**/mp_id_name\": \"ALSO PRESENT\"}]");

        mIdMap = new HashMap<String, Integer>();
        mIdMap.put("NAME PRESENT", 1001);
        mIdMap.put("ALSO PRESENT", 1002);

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

    public void testReadPaths() throws JSONException {
        {
            final List<ViewVisitor.PathElement> p = mProtocol.readPath(mJustClassPath, mIdMap);
            final ViewVisitor.PathElement first = p.get(0);
            final ViewVisitor.PathElement last = p.get(p.size() - 1);
            assertEquals(null, first.viewClassName);
            assertEquals(-1, first.index);
            assertEquals(-1, first.viewId);
            assertEquals(null, first.tag);

            assertEquals("android.widget.Button", last.viewClassName);
            assertEquals(-1, last.index);
            assertEquals(-1, last.viewId);
            assertEquals(null, last.tag);
        }

        {
            final List<ViewVisitor.PathElement> p = mProtocol.readPath(mJustIdPath, mIdMap);
            final ViewVisitor.PathElement first = p.get(0);
            final ViewVisitor.PathElement last = p.get(p.size() - 1);
            assertEquals(null, first.viewClassName);
            assertEquals(-1, first.index);
            assertEquals(-1, first.viewId);
            assertEquals(null, first.tag);

            assertEquals(null, last.viewClassName);
            assertEquals(-1, last.index);
            assertEquals(2000, last.viewId);
            assertEquals(null, last.tag);
        }

        {
            final List<ViewVisitor.PathElement> p = mProtocol.readPath(mJustIndexPath, mIdMap);
            final ViewVisitor.PathElement first = p.get(0);
            final ViewVisitor.PathElement last = p.get(p.size() - 1);
            assertEquals(null, first.viewClassName);
            assertEquals(-1, first.index);
            assertEquals(-1, first.viewId);
            assertEquals(null, first.tag);

            assertEquals(null, last.viewClassName);
            assertEquals(2, last.index);
            assertEquals(-1, last.viewId);
            assertEquals(null, last.tag);
        }

        {
            final List<ViewVisitor.PathElement> p = mProtocol.readPath(mJustTagPath, mIdMap);
            final ViewVisitor.PathElement first = p.get(0);
            final ViewVisitor.PathElement last = p.get(p.size() - 1);
            assertEquals(null, first.viewClassName);
            assertEquals(-1, first.index);
            assertEquals(-1, first.viewId);
            assertEquals(null, first.tag);

            assertEquals(null, last.viewClassName);
            assertEquals(-1, last.index);
            assertEquals(-1, last.viewId);
            assertEquals("this_is_a_simple_tag", last.tag);
        }

        {
            final List<ViewVisitor.PathElement> p = mProtocol.readPath(mJustIdNamePath, mIdMap);
            final ViewVisitor.PathElement first = p.get(0);
            final ViewVisitor.PathElement last = p.get(p.size() - 1);
            assertEquals(null, first.viewClassName);
            assertEquals(-1, first.index);
            assertEquals(-1, first.viewId);
            assertEquals(null, first.tag);

            assertEquals(null, last.viewClassName);
            assertEquals(-1, last.index);
            assertEquals(1001, last.viewId);
            assertEquals(null, last.tag);
        }

        {
            final List<ViewVisitor.PathElement> p = mProtocol.readPath(mJustIdNamePath, new HashMap<String, Integer>());
            assertTrue(p.isEmpty());
        }

        {
            final List<ViewVisitor.PathElement> p = mProtocol.readPath(mIdNameAndIdPath, mIdMap);
            final ViewVisitor.PathElement first = p.get(0);
            final ViewVisitor.PathElement last = p.get(p.size() - 1);
            assertEquals(null, first.viewClassName);
            assertEquals(-1, first.index);
            assertEquals(-1, first.viewId);
            assertEquals(null, first.tag);

            assertEquals(null, last.viewClassName);
            assertEquals(-1, last.index);
            assertEquals(1001, last.viewId);
            assertEquals(null, last.tag);
        }

        {
            final Map<String, Integer> nonMatchingIdMap = new HashMap<String, Integer>();
            nonMatchingIdMap.put("NAME PRESENT", 1985);
            final List<ViewVisitor.PathElement> p = mProtocol.readPath(mIdNameAndIdPath, nonMatchingIdMap);
            assertTrue(p.isEmpty());
        }

        {
            final List<ViewVisitor.PathElement> p = mProtocol.readPath(mJustFindIdPath, mIdMap);
            final ViewVisitor.PathElement first = p.get(0);
            final ViewVisitor.PathElement last = p.get(p.size() - 1);
            assertEquals(null, first.viewClassName);
            assertEquals(-1, first.index);
            assertEquals(-1, first.viewId);
            assertEquals(null, first.tag);

            assertEquals(null, last.viewClassName);
            assertEquals(-1, last.index);
            assertEquals(1001, last.findId);
        }

        {
            final List<ViewVisitor.PathElement> p = mProtocol.readPath(mJustFindNamePath, mIdMap);
            final ViewVisitor.PathElement first = p.get(0);
            final ViewVisitor.PathElement last = p.get(p.size() - 1);
            assertEquals(null, first.viewClassName);
            assertEquals(-1, first.index);
            assertEquals(-1, first.viewId);
            assertEquals(null, first.tag);

            assertEquals(null, last.viewClassName);
            assertEquals(-1, last.index);
            assertEquals(1001, last.findId);
        }

        {
            final List<ViewVisitor.PathElement> p = mProtocol.readPath(mUselessFindIdPath, mIdMap);
            final ViewVisitor.PathElement first = p.get(0);
            final ViewVisitor.PathElement last = p.get(p.size() - 1);
            assertEquals(null, first.viewClassName);
            assertEquals(-1, first.index);
            assertEquals(-1, first.viewId);
            assertEquals(null, first.tag);

            assertEquals(null, last.viewClassName);
            assertEquals(-1, last.index);
            assertEquals(1001, last.viewId);
            assertEquals(1001, last.findId);
        }

        {
            final List<ViewVisitor.PathElement> p = mProtocol.readPath(mIdAndNameDontMatch, mIdMap);
            assertTrue(p.isEmpty());
        }

        {
            final List<ViewVisitor.PathElement> p = mProtocol.readPath(mIdAndFindDontMatch, mIdMap);
            assertTrue(p.isEmpty());
        }
    }

    public void testPropertyEdit() throws EditProtocol.BadInstructionsException {
        final ViewVisitor visitor = mProtocol.readEdit(mPropertyEdit);
        visitor.visit(mRootView);
        assertEquals(mRootView.mAdHocButton2.getText(), "Ground Control to Major Tom");
    }

    public void testClickEvent() throws EditProtocol.BadInstructionsException {
        final ViewVisitor eventListener = mProtocol.readEventBinding(mClickEvent, mListener);
        eventListener.visit(mRootView);
        mRootView.mAdHocButton2.performClick();
        assertEquals(mListener.visitsRecorded.size(), 1);
        assertEquals(mListener.visitsRecorded.get(0), "Commencing Count-Down");
    }

    public void testAppearsEvent() throws EditProtocol.BadInstructionsException {
        final ViewVisitor appearsListener = mProtocol.readEventBinding(mAppearsEvent, mListener);
        appearsListener.visit(mRootView);
        assertTrue(mListener.visitsRecorded.isEmpty());

        mRootView.mButtonGroup.addView(new Button(getContext()));
        appearsListener.visit(mRootView);
        assertEquals(mListener.visitsRecorded.size(), 1);
        assertEquals(mListener.visitsRecorded.get(0), "Engines On!");
    }

    private static class TestVisitedListener implements ViewVisitor.OnVisitedListener {
        @Override
        public void OnVisited(View v, String eventName) {
            visitsRecorded.add(eventName);
        }

        public List<String> visitsRecorded = new ArrayList<String>();
    }

    private EditProtocol mProtocol;
    private JSONObject mSnapshotConfig;
    private JSONObject mPropertyEdit;
    private JSONObject mClickEvent;
    private JSONObject mAppearsEvent;
    private JSONArray mJustClassPath;
    private JSONArray mJustIdPath;
    private JSONArray mJustIndexPath;
    private JSONArray mJustTagPath;
    private JSONArray mJustIdNamePath;
    private JSONArray mIdNameAndIdPath;
    private JSONArray mJustFindIdPath;
    private JSONArray mJustFindNamePath;
    private JSONArray mUselessFindIdPath;
    private JSONArray mIdAndNameDontMatch;
    private JSONArray mIdAndFindDontMatch;
    private TestVisitedListener mListener;
    private TestView mRootView;
    private Map<String, Integer> mIdMap;

}
