package com.mixpanel.android.viewcrawler;


import android.annotation.TargetApi;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.test.AndroidTestCase;
import android.util.JsonWriter;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.mixpanel.android.mpmetrics.MPConfig;
import com.mixpanel.android.mpmetrics.ResourceIds;
import com.mixpanel.android.mpmetrics.TestUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ViewSnapshotTest extends AndroidTestCase {
    public void setUp() throws NoSuchMethodException {
        mRootView = new TestView(this.getContext());

        final List<PropertyDescription> props = new ArrayList<PropertyDescription>();

        final Caller textGetter = new Caller(TextView.class, "getText", new Object[0], CharSequence.class);
        final PropertyDescription text = new PropertyDescription("text", TextView.class, textGetter, "setText");
        props.add(text);

        final Caller customPropGetter = new Caller(TestView.CustomPropButton.class, "getCustomProperty", new Object[0], CharSequence.class);
        final PropertyDescription custom = new PropertyDescription(
            "custom",
            TestView.CustomPropButton.class,
            customPropGetter,
            "setCustomProperty"
        );
        props.add(custom);

        final Caller drawableGetter = new Caller(ImageView.class, "getDrawable", new Object[0], Drawable.class);
        final PropertyDescription drawable = new PropertyDescription(
            "drawable",
            ImageView.class,
            drawableGetter,
            "setImageDrawable"
        );
        props.add(drawable);

        final Map<String, Integer> idNamesToIds = new HashMap<String, Integer>();
        idNamesToIds.put("ROOT_ID", TestView.ROOT_ID);
        idNamesToIds.put("TEXT_VIEW_ID", TestView.TEXT_VIEW_ID);
        idNamesToIds.put("CRAZYSAUCE ID", 1234567);
        // NO BUTTON_ID in the table

        final ResourceIds resourceIds = new TestUtils.TestResourceIds(idNamesToIds);
        mSnapshot = new ViewSnapshot(props, resourceIds);

        int width = View.MeasureSpec.makeMeasureSpec(768, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY);
        mRootView.measure(width, height);
        mRootView.layout(0, 0, 768, 1280);
    }

    public void testBadMethods() {
        try {
            final Caller crazyGetter = new Caller(View.class, "CRAZY GETTER", new Object[0], Void.TYPE);
            fail("Exception was not thrown when constructing a bad caller");
        } catch (NoSuchMethodException e) {
            // OK!
        }

        try {
            final Caller badTypesGetter = new Caller(TextView.class, "getText", new Object[0], Integer.class);
            fail("Exception was not thrown when constructing a caller with bad types");
        } catch (NoSuchMethodException e) {
            // OK!
        }
    }

    public void testDrawableSnapshot() throws IOException, JSONException {
        final Drawable realDrawable = mRootView.mImageView.getDrawable();
        final SparseArray<JSONObject> descsByHashcode = snapshotsByHashcode();
        final JSONObject imageDesc = descsByHashcode.get(mRootView.mImageView.hashCode());
        final JSONObject drawableDesc = imageDesc.getJSONObject("drawable");
        final JSONArray classes = drawableDesc.getJSONArray("classes");
        assertEquals("android.graphics.drawable.BitmapDrawable", classes.get(0));
        assertEquals("android.graphics.drawable.Drawable", classes.get(1));

        final Rect realBounds = realDrawable.getBounds();
        final JSONObject dimensions = drawableDesc.getJSONObject("dimensions");
        final int top = dimensions.getInt("top");
        final int bottom = dimensions.getInt("bottom");
        final int left = dimensions.getInt("left");
        final int right = dimensions.getInt("right");

        assertEquals(realBounds.top, top);
        assertEquals(realBounds.bottom, bottom);
        assertEquals(realBounds.left, left);
        assertEquals(realBounds.right, right);
    }

    public void testSnapshotComplete() throws IOException, JSONException {
        final JSONArray viewsJson = requestSnapshot();
        assertEquals(mRootView.mAllViews.size(), viewsJson.length());
    }

    public void testCommonProperties() throws IOException, JSONException {
        final SparseArray<JSONObject> descsByHashcode = snapshotsByHashcode();
        final Map<Integer, View> viewsByHashCode = mRootView.mViewsByHashcode;
        final int size = descsByHashcode.size();
        for (int i = 0; i < size; i++) {
            final int hashCode = descsByHashcode.keyAt(i);
            final JSONObject viewDesc = descsByHashcode.valueAt(i);
            final View found = viewsByHashCode.get(hashCode);
            assertEquals(found.getId(), viewDesc.getInt("id"));
            assertEquals(found.getHeight(), viewDesc.getInt("height"));
            assertEquals(found.getWidth(), viewDesc.getInt("width"));

            final CharSequence foundContentDescription = found.getContentDescription();
            if (null == foundContentDescription) {
                assertTrue(viewDesc.isNull("contentDescription"));
            } else {
                assertEquals(foundContentDescription.toString(), viewDesc.getString("contentDescription"));
            }
        }
    }

    public void testChildrenAccountedFor() throws IOException, JSONException {
        final SparseArray<JSONObject> descsByHashcode = snapshotsByHashcode();
        final Map<Integer, View> viewsByHashCode = mRootView.mViewsByHashcode;
        final int size = descsByHashcode.size();
        for (int i = 0; i < size; i++) {
            final int hashCode = descsByHashcode.keyAt(i);
            final JSONObject viewDesc = descsByHashcode.valueAt(i);
            final View found = viewsByHashCode.get(hashCode);

            final JSONArray children = viewDesc.getJSONArray("subviews");
            if (found instanceof ViewGroup) {
                final ViewGroup group = (ViewGroup) found;
                final int childCount = group.getChildCount();
                assertEquals(childCount, children.length());

                final Set<Integer> expectHashCodes = new HashSet<Integer>();
                final Set<Integer> snapshotHashCodes = new HashSet<Integer>();
                for (int childIx = 0; childIx < childCount; childIx++) {
                    expectHashCodes.add(group.getChildAt(childIx).hashCode());
                    snapshotHashCodes.add(children.getInt(childIx));
                }

                assertEquals(expectHashCodes, snapshotHashCodes);
            }
        }
    }

    public void testViewSpecificProperties() throws IOException, JSONException {
        final SparseArray<JSONObject> descsByHashcode = snapshotsByHashcode();

        final JSONObject adhoc1Desc = descsByHashcode.get(mRootView.mAdHocButton1.hashCode());
        final JSONObject adhoc2Desc = descsByHashcode.get(mRootView.mAdHocButton2.hashCode());
        final JSONObject adhoc3Desc = descsByHashcode.get(mRootView.mAdHocButton3.hashCode());

        assertEquals(mRootView.mAdHocButton1.getText(), adhoc1Desc.getString("text"));
        assertEquals(mRootView.mAdHocButton2.getText(), adhoc2Desc.getString("text"));
        assertEquals(mRootView.mAdHocButton3.getText(), adhoc3Desc.getString("text"));

        assertEquals(mRootView.mAdHocButton1.getCustomProperty(), adhoc1Desc.getString("custom"));
        assertFalse(adhoc2Desc.has("custom"));
        assertFalse(adhoc3Desc.has("custom"));

        assertFalse(adhoc1Desc.has("crazy"));
        assertFalse(adhoc1Desc.has("badTypes"));
        assertFalse(adhoc2Desc.has("crazy"));
        assertFalse(adhoc2Desc.has("badTypes"));
        assertFalse(adhoc3Desc.has("crazy"));
        assertFalse(adhoc3Desc.has("badTypes"));

        final JSONObject rootDesc = descsByHashcode.get(mRootView.hashCode());
        final JSONObject textViewDesc = descsByHashcode.get(mRootView.mTextView1.hashCode());

        assertEquals(rootDesc.getString("mp_id_name"), "ROOT_ID");
        assertEquals(textViewDesc.getString("mp_id_name"), "TEXT_VIEW_ID");
        assertEquals(adhoc3Desc.get("mp_id_name"), JSONObject.NULL);
        assertEquals(adhoc3Desc.getInt("id"), TestView.BUTTON_ID);
    }

    private SparseArray<JSONObject> snapshotsByHashcode() throws IOException, JSONException {
        final JSONArray viewsJson = requestSnapshot();
        final Map<Integer, View> viewsByHashcode = new HashMap<Integer, View>(mRootView.mViewsByHashcode);
        final SparseArray<JSONObject> ret = new SparseArray<JSONObject>();

        for (int viewIx = 0; viewIx < viewsJson.length(); viewIx++) {
            final JSONObject viewDesc = viewsJson.getJSONObject(viewIx);
            final int descHashcode = viewDesc.getInt("hashCode");
            ret.put(descHashcode, viewDesc);
            final View found = viewsByHashcode.remove(descHashcode);

            assertNotNull(found);
        }

        assertTrue(viewsByHashcode.isEmpty());

        return ret;
    }

    @TargetApi(MPConfig.UI_FEATURES_MIN_API)
    private JSONArray requestSnapshot() throws IOException, JSONException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final OutputStreamWriter writer = new OutputStreamWriter(out);
        final JsonWriter j = new JsonWriter(writer);
        mSnapshot.snapshotViewHierarchy(j, mRootView);
        j.flush();
        return new JSONArray(new String(out.toByteArray()));
    }

    private ViewSnapshot mSnapshot;
    private TestView mRootView;
}
