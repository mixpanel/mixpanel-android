package com.mixpanel.android.viewcrawler;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.test.AndroidTestCase;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.mixpanel.android.mpmetrics.ResourceIds;
import com.mixpanel.android.mpmetrics.TestUtils;
import com.mixpanel.android.util.ImageStore;

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
        final Map<String, Integer> idMap = new HashMap<String, Integer>();
        idMap.put("NAME PRESENT", 1001);
        idMap.put("ALSO PRESENT", 1002);
        idMap.put("relativelayout_button1", TestView.RELATIVE_LAYOUT_BUTTON1_ID);
        idMap.put("relativelayout_button2", TestView.RELATIVE_LAYOUT_BUTTON2_ID);

        mLayoutErrorListener = new TestView.MockOnLayoutErrorListener();
        mResourceIds = new TestUtils.TestResourceIds(idMap);
        mProtocol = new EditProtocol(mResourceIds, new ImageStore(getContext(), "EditProtocolTest") {
            @Override
            public Bitmap getImage(String url) {
                fail("Unexpected call to getImage");
                return null;
            }
        }, mLayoutErrorListener);
        mSnapshotConfig = new JSONObject(
            "{\"config\": {\"classes\":[{\"name\":\"android.view.View\",\"properties\":[{\"name\":\"importantForAccessibility\",\"get\":{\"selector\":\"isImportantForAccessibility\",\"parameters\":[],\"result\":{\"type\":\"java.lang.Boolean\"}}}]},{\"name\":\"android.widget.TextView\",\"properties\":[{\"name\":\"text\",\"get\":{\"selector\":\"getText\",\"parameters\":[],\"result\":{\"type\":\"java.lang.CharSequence\"}},\"set\":{\"selector\":\"setText\",\"parameters\":[{\"type\":\"java.lang.CharSequence\"}]}}]},{\"name\":\"android.widget.ImageView\",\"properties\":[{\"name\":\"image\",\"set\":{\"selector\":\"setImageDrawable\",\"parameters\":[{\"type\":\"android.graphics.drawable.Drawable\"}]}}]}]}}"
        );
        mPropertyEdit = new JSONObject(
            "{\"path\":[{\"view_class\":\"com.mixpanel.android.viewcrawler.TestView\",\"index\":0},{\"view_class\":\"android.widget.LinearLayout\",\"index\":0},{\"view_class\":\"android.widget.LinearLayout\",\"index\":0},{\"view_class\":\"android.widget.Button\",\"index\":1}],\"property\":{\"classname\":\"android.widget.Button\",\"name\":\"text\",\"get\":{\"selector\":\"getText\",\"parameters\":[],\"result\":{\"type\":\"java.lang.CharSequence\"}},\"set\":{\"selector\":\"setText\",\"parameters\":[{\"type\":\"java.lang.CharSequence\"}]}},\"args\":[[\"Ground Control to Major Tom\",\"java.lang.CharSequence\"]],\"change_type\": \"property\"}"
        );
        mLayoutEditAlignParentRight = new JSONObject(
            String.format("{\"args\":[{\"verb\":%d,\"anchor_id_name\":\"%d\",\"view_id_name\":\"relativelayout_button1\"}],\"path\": [{\"prefix\": \"shortest\", \"index\": 0, \"id\": %d }],\"change_type\": \"layout\", \"name\": \"test1\"}", RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE, TestView.RELATIVE_LAYOUT_ID)
        );
        mLayoutEditBelow = new JSONObject(
                String.format("{\"args\":[{\"verb\":%d,\"anchor_id_name\":\"relativelayout_button1\",\"view_id_name\":\"relativelayout_button2\"}],\"path\": [{\"prefix\": \"shortest\", \"index\": 0, \"id\": %d }],\"change_type\": \"layout\", \"name\": \"test2\"}", RelativeLayout.BELOW, TestView.RELATIVE_LAYOUT_ID)
        );
        mLayoutEditRemoveBelow = new JSONObject(
                String.format("{\"args\":[{\"verb\":%d,\"anchor_id_name\":\"%d\",\"view_id_name\":\"relativelayout_button2\"}],\"path\": [{\"prefix\": \"shortest\", \"index\": 0, \"id\": %d }],\"change_type\": \"layout\", \"name\": \"test3\"}", RelativeLayout.BELOW, TestView.NO_ANCHOR, TestView.RELATIVE_LAYOUT_ID)
        );
        mLayoutEditAbsentAnchor = new JSONObject(
                String.format("{\"args\":[{\"verb\":%d,\"anchor_id_name\":\"relativelayout_button3\",\"view_id_name\":\"relativelayout_button2\"}],\"path\": [{\"prefix\": \"shortest\", \"index\": 0, \"id\": %d }],\"change_type\": \"layout\", \"name\": \"test4\"}", RelativeLayout.BELOW, TestView.RELATIVE_LAYOUT_ID)
        );
        mClickEvent = new JSONObject(
            "{\"path\":[{\"view_class\":\"com.mixpanel.android.viewcrawler.TestView\",\"index\":0},{\"view_class\":\"android.widget.LinearLayout\",\"index\":0},{\"view_class\":\"android.widget.LinearLayout\",\"index\":0},{\"view_class\":\"android.widget.Button\",\"index\":1}],\"event_type\":\"click\",\"event_name\":\"Commencing Count-Down\"}"
        );
        mAppearsEvent = new JSONObject(
            "{\"path\":[{\"view_class\":\"com.mixpanel.android.viewcrawler.TestView\",\"index\":0},{\"view_class\":\"android.widget.LinearLayout\",\"index\":0},{\"view_class\":\"android.widget.LinearLayout\",\"index\":0},{\"view_class\":\"android.widget.Button\",\"index\":3}],\"event_type\":\"detected\",\"event_name\":\"Engines On!\"}"
        );
        mTextEdit = new JSONObject(
            "{\"args\":[[\"Hello\",\"java.lang.CharSequence\"]],\"name\":\"c236\",\"path\":[{\"prefix\":\"shortest\",\"index\":0,\"id\":" + TestView.TEXT2_VIEW_ID + "}],\"change_type\": \"property\",\"property\":{\"name\":\"text\",\"get\":{\"selector\":\"getText\",\"parameters\":[],\"result\":{\"type\":\"java.lang.CharSequence\"}},\"set\":{\"selector\":\"setText\",\"parameters\":[{\"type\":\"java.lang.CharSequence\"}]},\"classname\":\"android.widget.TextView\"}}"
        );
        mJustClassPath = new JSONArray("[{},{},{},{\"view_class\":\"android.widget.Button\"}]");
        mJustIdPath = new JSONArray("[{},{},{},{\"id\": 2000}]");
        mJustIndexPath = new JSONArray("[{},{},{},{\"index\": 2}]");
        mJustTagPath = new JSONArray("[{},{},{},{\"tag\": \"this_is_a_simple_tag\"}]");
        mJustIdNamePath = new JSONArray("[{},{},{},{\"mp_id_name\": \"NAME PRESENT\"}]");
        mIdNameAndIdPath = new JSONArray("[{},{},{},{\"mp_id_name\": \"NAME PRESENT\", \"id\": 1001}]");
        mJustFindIdPath = new JSONArray("[{},{},{},{\"prefix\": \"shortest\", \"id\": 1001}]");
        mJustFindNamePath = new JSONArray("[{},{},{},{\"prefix\": \"shortest\", \"mp_id_name\": \"NAME PRESENT\"}]");
        mUselessFindIdPath = new JSONArray("[{},{},{},{\"prefix\": \"shortest\", \"mp_id_name\": \"NAME PRESENT\", \"id\": 1001}]");
        mIdAndNameDontMatch = new JSONArray("[{},{},{},{\"mp_id_name\": \"NO SUCH NAME\", \"id\": 90210}]");

        mListener = new TestEventListener();
        mRootView = new TestView(getContext());
    }

    public void testSnapshotConfig() throws EditProtocol.BadInstructionsException {
        final ViewSnapshot snapshot = mProtocol.readSnapshotConfig(mSnapshotConfig);
        final List<PropertyDescription> properties = snapshot.getProperties();

        assertEquals(properties.size(), 3);
        final PropertyDescription prop1 = properties.get(0);
        final PropertyDescription prop2 = properties.get(1);
        final PropertyDescription prop3 = properties.get(2);

        assertEquals(prop1.name, "importantForAccessibility");
        assertEquals(prop2.name, "text");
        assertEquals(prop3.name, "image");

        assertEquals(prop1.targetClass, View.class);
        assertEquals(prop2.targetClass, TextView.class);
        assertEquals(prop3.targetClass, ImageView.class);
    }

    public void testReadPaths() throws JSONException {
        {
            final List<Pathfinder.PathElement> p = mProtocol.readPath(mJustClassPath, mResourceIds);
            final Pathfinder.PathElement first = p.get(0);
            final Pathfinder.PathElement last = p.get(p.size() - 1);
            assertEquals(null, first.viewClassName);
            assertEquals(-1, first.index);
            assertEquals(-1, first.viewId);
            assertEquals(null, first.tag);

            assertEquals(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, last.prefix);
            assertEquals("android.widget.Button", last.viewClassName);
            assertEquals(-1, last.index);
            assertEquals(-1, last.viewId);
            assertEquals(null, last.tag);
        }

        {
            final List<Pathfinder.PathElement> p = mProtocol.readPath(mJustIdPath, mResourceIds);
            final Pathfinder.PathElement first = p.get(0);
            final Pathfinder.PathElement last = p.get(p.size() - 1);
            assertEquals(null, first.viewClassName);
            assertEquals(-1, first.index);
            assertEquals(-1, first.viewId);
            assertEquals(null, first.tag);

            assertEquals(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, last.prefix);
            assertEquals(null, last.viewClassName);
            assertEquals(-1, last.index);
            assertEquals(2000, last.viewId);
            assertEquals(null, last.tag);
        }

        {
            final List<Pathfinder.PathElement> p = mProtocol.readPath(mJustIndexPath, mResourceIds);
            final Pathfinder.PathElement first = p.get(0);
            final Pathfinder.PathElement last = p.get(p.size() - 1);
            assertEquals(null, first.viewClassName);
            assertEquals(-1, first.index);
            assertEquals(-1, first.viewId);
            assertEquals(null, first.tag);

            assertEquals(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, last.prefix);
            assertEquals(null, last.viewClassName);
            assertEquals(2, last.index);
            assertEquals(-1, last.viewId);
            assertEquals(null, last.tag);
        }

        {
            final List<Pathfinder.PathElement> p = mProtocol.readPath(mJustTagPath, mResourceIds);
            final Pathfinder.PathElement first = p.get(0);
            final Pathfinder.PathElement last = p.get(p.size() - 1);
            assertEquals(null, first.viewClassName);
            assertEquals(-1, first.index);
            assertEquals(-1, first.viewId);
            assertEquals(null, first.tag);

            assertEquals(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, last.prefix);
            assertEquals(null, last.viewClassName);
            assertEquals(-1, last.index);
            assertEquals(-1, last.viewId);
            assertEquals("this_is_a_simple_tag", last.tag);
        }

        {
            final List<Pathfinder.PathElement> p = mProtocol.readPath(mJustIdNamePath, mResourceIds);
            final Pathfinder.PathElement first = p.get(0);
            final Pathfinder.PathElement last = p.get(p.size() - 1);
            assertEquals(null, first.viewClassName);
            assertEquals(-1, first.index);
            assertEquals(-1, first.viewId);
            assertEquals(null, first.tag);

            assertEquals(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, last.prefix);
            assertEquals(null, last.viewClassName);
            assertEquals(-1, last.index);
            assertEquals(1001, last.viewId);
            assertEquals(null, last.tag);
        }

        {
            final ResourceIds emptyIds = new TestUtils.TestResourceIds(new HashMap<String, Integer>());
            final List<Pathfinder.PathElement> p = mProtocol.readPath(mJustIdNamePath, emptyIds);
            assertTrue(p.isEmpty());
        }

        {
            final List<Pathfinder.PathElement> p = mProtocol.readPath(mIdNameAndIdPath, mResourceIds);
            final Pathfinder.PathElement first = p.get(0);
            final Pathfinder.PathElement last = p.get(p.size() - 1);
            assertEquals(null, first.viewClassName);
            assertEquals(-1, first.index);
            assertEquals(-1, first.viewId);
            assertEquals(null, first.tag);

            assertEquals(Pathfinder.PathElement.ZERO_LENGTH_PREFIX, last.prefix);
            assertEquals(null, last.viewClassName);
            assertEquals(-1, last.index);
            assertEquals(1001, last.viewId);
            assertEquals(null, last.tag);
        }

        {
            final Map<String, Integer> nonMatchingIdMap = new HashMap<String, Integer>();
            nonMatchingIdMap.put("NAME PRESENT", 1985);
            final ResourceIds resourceIds = new TestUtils.TestResourceIds(nonMatchingIdMap);
            final List<Pathfinder.PathElement> p = mProtocol.readPath(mIdNameAndIdPath, resourceIds);
            assertTrue(p.isEmpty());
        }

        {
            final List<Pathfinder.PathElement> p = mProtocol.readPath(mJustFindIdPath, mResourceIds);
            final Pathfinder.PathElement first = p.get(0);
            final Pathfinder.PathElement last = p.get(p.size() - 1);
            assertEquals(null, first.viewClassName);
            assertEquals(-1, first.index);
            assertEquals(-1, first.viewId);
            assertEquals(null, first.tag);

            assertEquals(Pathfinder.PathElement.SHORTEST_PREFIX, last.prefix);
            assertEquals(null, last.viewClassName);
            assertEquals(-1, last.index);
            assertEquals(1001, last.viewId);
        }

        {
            final List<Pathfinder.PathElement> p = mProtocol.readPath(mJustFindNamePath, mResourceIds);
            final Pathfinder.PathElement first = p.get(0);
            final Pathfinder.PathElement last = p.get(p.size() - 1);
            assertEquals(null, first.viewClassName);
            assertEquals(-1, first.index);
            assertEquals(-1, first.viewId);
            assertEquals(null, first.tag);

            assertEquals(Pathfinder.PathElement.SHORTEST_PREFIX, last.prefix);
            assertEquals(null, last.viewClassName);
            assertEquals(-1, last.index);
            assertEquals(1001, last.viewId);
        }

        {
            final List<Pathfinder.PathElement> p = mProtocol.readPath(mUselessFindIdPath, mResourceIds);
            final Pathfinder.PathElement first = p.get(0);
            final Pathfinder.PathElement last = p.get(p.size() - 1);
            assertEquals(null, first.viewClassName);
            assertEquals(-1, first.index);
            assertEquals(-1, first.viewId);
            assertEquals(null, first.tag);

            assertEquals(Pathfinder.PathElement.SHORTEST_PREFIX, last.prefix);
            assertEquals(null, last.viewClassName);
            assertEquals(-1, last.index);
            assertEquals(1001, last.viewId);
        }

        {
            final List<Pathfinder.PathElement> p = mProtocol.readPath(mIdAndNameDontMatch, mResourceIds);
            assertTrue(p.isEmpty());
        }
    }

    public void testPropertyEdit() throws EditProtocol.BadInstructionsException, EditProtocol.CantGetEditAssetsException {
        final EditProtocol.Edit edit = mProtocol.readEdit(mPropertyEdit);
        edit.visitor.visit(mRootView);
        assertEquals(mRootView.mAdHocButton2.getText(), "Ground Control to Major Tom");
    }

    public void testLayoutEdit() throws EditProtocol.BadInstructionsException, EditProtocol.CantGetEditAssetsException {
        // add ALIGN_PARENT_RIGHT to mRelativeLayoutButton1, should success
        final EditProtocol.Edit edit1 = mProtocol.readEdit(mLayoutEditAlignParentRight);
        edit1.visitor.visit(mRootView);
        RelativeLayout.LayoutParams params1 = (RelativeLayout.LayoutParams)mRootView.mRelativeLayoutButton1.getLayoutParams();
        int[] rules1 = params1.getRules();
        assertEquals(RelativeLayout.TRUE, rules1[RelativeLayout.ALIGN_PARENT_RIGHT]);

        // add "BELOW mRelativeLayoutButton1" to mRelativeLayoutButton2, should success
        final EditProtocol.Edit edit2 = mProtocol.readEdit(mLayoutEditBelow);
        edit2.visitor.visit(mRootView);
        RelativeLayout.LayoutParams params2 = (RelativeLayout.LayoutParams)mRootView.mRelativeLayoutButton2.getLayoutParams();
        int[] rules2 = params2.getRules();
        assertEquals(TestView.RELATIVE_LAYOUT_BUTTON1_ID, rules2[RelativeLayout.BELOW]);

        // remove "BELOW mRelativeLayoutButton1" from mRelativeLayoutButton2, should success
        final EditProtocol.Edit edit3 = mProtocol.readEdit(mLayoutEditRemoveBelow);
        edit3.visitor.visit(mRootView);
        RelativeLayout.LayoutParams params3 = (RelativeLayout.LayoutParams)mRootView.mRelativeLayoutButton2.getLayoutParams();
        int[] rule3 = params3.getRules();
        assertEquals(TestView.NO_ANCHOR, rule3[RelativeLayout.BELOW]);

        // add "BELOW mRelativeLayoutButton3 (absent)" to mRelativeLayoutButton2, should fail
        final EditProtocol.Edit edit4 = mProtocol.readEdit(mLayoutEditAbsentAnchor);
        edit4.visitor.visit(mRootView);
        RelativeLayout.LayoutParams params4 = (RelativeLayout.LayoutParams)mRootView.mRelativeLayoutButton2.getLayoutParams();
        int[] rules4 = params4.getRules();
        assertEquals( TestView.NO_ANCHOR, rules4[RelativeLayout.BELOW]);
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

    public void testEdit() throws EditProtocol.BadInstructionsException, EditProtocol.CantGetEditAssetsException {
        final EditProtocol.Edit textEdit = mProtocol.readEdit(mTextEdit);
        textEdit.visitor.visit(mRootView);
        assertEquals("Hello", mRootView.mTextView2.getText());

        textEdit.visitor.cleanup();
        assertEquals("Original Text", mRootView.mTextView2.getText());
    }

    public void testEditWithImage() throws JSONException, EditProtocol.BadInstructionsException,  EditProtocol.CantGetEditAssetsException {
        final ResourceIds resourceIds = new TestUtils.TestResourceIds(new HashMap<String, Integer>());
        final EditProtocol protocol = new EditProtocol(resourceIds, new ImageStore(getContext(), "testEditWithImage") {
            @Override
            public Bitmap getImage(String url) {
                assertEquals("TEST URL", url);
                return IMAGE_10x10_GREEN;
            }
        }, mLayoutErrorListener);

        final JSONObject obj = new JSONObject(
                "{\"args\":[[{\"url\":\"TEST URL\", \"dimensions\":{\"left\":10,\"right\":20,\"top\":40,\"bottom\":50}},\"android.graphics.drawable.Drawable\"]],\"name\":\"test\",\"path\":[{\"prefix\":\"shortest\",\"index\":0,\"id\":" + TestView.IMAGE_VIEW_ID + "}],\"change_type\": \"property\",\"property\":{\"name\":\"image\",\"get\":{\"selector\":\"getDrawable\",\"parameters\":[],\"result\":{\"type\":\"android.graphics.drawable.Drawable\"}},\"set\":{\"selector\":\"setImageDrawable\",\"parameters\":[{\"type\":\"android.graphics.drawable.Drawable\"}]},\"classname\":\"android.widget.ImageView\"}}"
        );

        final EditProtocol.Edit imageEdit = protocol.readEdit(obj);
        assertEquals(1, imageEdit.imageUrls.size());
        assertEquals("TEST URL", imageEdit.imageUrls.get(0));
        imageEdit.visitor.visit(mRootView);
        final BitmapDrawable drawable = (BitmapDrawable) mRootView.mImageView.getDrawable();
        final Bitmap bmp = drawable.getBitmap();

        for (int x = 0; x < bmp.getWidth(); x++) {
            for (int y = 0; y < bmp.getHeight(); y++) {
                assertEquals(
                    IMAGE_10x10_GREEN.getPixel(x, y),
                    bmp.getPixel(x, y)
                );
            }
        }
    }

    public void testWithMissingImage() throws JSONException, EditProtocol.BadInstructionsException {
        final ResourceIds resourceIds = new TestUtils.TestResourceIds(new HashMap<String, Integer>());
        final EditProtocol protocol = new EditProtocol(resourceIds, new ImageStore(getContext(), "testWithMissingImage") {
            @Override
            public Bitmap getImage(String url) throws CantGetImageException {
                assertEquals("TEST URL", url);
                throw new CantGetImageException("Bang!");
            }
        }, mLayoutErrorListener);

        final JSONObject obj = new JSONObject(
                "{\"args\":[[{\"url\":\"TEST URL\", \"dimensions\":{\"left\":10,\"right\":20,\"top\":40,\"bottom\":50}},\"android.graphics.drawable.Drawable\"]],\"name\":\"test\",\"path\":[{\"prefix\":\"shortest\",\"index\":0,\"id\":" + TestView.IMAGE_VIEW_ID + "}],\"change_type\": \"property\",\"property\":{\"name\":\"image\",\"get\":{\"selector\":\"getDrawable\",\"parameters\":[],\"result\":{\"type\":\"android.graphics.drawable.Drawable\"}},\"set\":{\"selector\":\"setImageDrawable\",\"parameters\":[{\"type\":\"android.graphics.drawable.Drawable\"}]},\"classname\":\"android.widget.ImageView\"}}"
        );

        try {
            final EditProtocol.Edit imageEdit = protocol.readEdit(obj);
            fail("Expected a CantGetEditAssetsException to be thrown");
        } catch (EditProtocol.CantGetEditAssetsException e) {
            ; // ok! expected this
        }
    }

    private static class TestEventListener implements ViewVisitor.OnEventListener {
        @Override
        public void OnEvent(View v, String eventName, boolean debounce) {
            visitsRecorded.add(eventName);
        }

        public List<String> visitsRecorded = new ArrayList<String>();
    }

    private EditProtocol mProtocol;
    private ResourceIds mResourceIds;
    private JSONObject mSnapshotConfig;
    private JSONObject mPropertyEdit;
    private JSONObject mLayoutEditAlignParentRight;
    private JSONObject mLayoutEditBelow;
    private JSONObject mLayoutEditAbove;
    private JSONObject mLayoutEditRemoveBelow;
    private JSONObject mLayoutEditAbsentAnchor;
    private JSONObject mClickEvent;
    private JSONObject mAppearsEvent;
    private JSONObject mTextEdit;
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
    private TestEventListener mListener;
    private TestView mRootView;
    private TestView.MockOnLayoutErrorListener mLayoutErrorListener;

    private static final byte[] IMAGE_10x10_GREEN_BYTES = Base64.decode("R0lGODlhCgAKALMAAAAAAIAAAACAAICAAAAAgIAAgACAgMDAwICAgP8AAAD/AP//AAAA//8A/wD//////ywAAAAACgAKAAAEClDJSau9OOvNe44AOw==".getBytes(), 0);
    private static final Bitmap IMAGE_10x10_GREEN = BitmapFactory.decodeByteArray(IMAGE_10x10_GREEN_BYTES, 0, IMAGE_10x10_GREEN_BYTES.length);
}
