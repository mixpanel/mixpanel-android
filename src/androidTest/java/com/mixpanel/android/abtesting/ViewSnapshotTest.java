package com.mixpanel.android.abtesting;


import android.test.AndroidTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ViewSnapshotTest extends AndroidTestCase {
    public void setUp() {
        mRootView = new TestView(this.getContext());

        final List<ViewSnapshot.PropertyDescription> props = new ArrayList<ViewSnapshot.PropertyDescription>();

        final ViewEdit.Caller textGetter = new ViewEdit.Caller("getText", new Object[0], CharSequence.class);
        final ViewEdit.Caller textSetter = new ViewEdit.Caller("setText", new Object[] { CharSequence.class }, Void.TYPE);
        final ViewSnapshot.PropertyDescription text = new ViewSnapshot.PropertyDescription("text", TextView.class, textGetter, textSetter);
        props.add(text);

        final ViewEdit.Caller customPropGetter = new ViewEdit.Caller("getCustomProperty", new Object[0], CharSequence.class);
        final ViewEdit.Caller customPropSetter = new ViewEdit.Caller("setCustomProperty", new Object[] { CharSequence.class }, Void.TYPE);
        final ViewSnapshot.PropertyDescription custom = new ViewSnapshot.PropertyDescription("custom", TestView.CustomPropButton.class, customPropGetter, customPropSetter);
        props.add(custom);

        final ViewEdit.Caller crazyGetter = new ViewEdit.Caller("CRAZY GETTER", new Object[0], Void.TYPE);
        final ViewEdit.Caller crazySetter = new ViewEdit.Caller("CRAZY SETTER", new Object[] { CharSequence.class}, Void.TYPE);
        final ViewSnapshot.PropertyDescription crazy = new ViewSnapshot.PropertyDescription("crazy", View.class, crazyGetter, crazySetter);
        props.add(crazy);

        final ViewEdit.Caller badTypesGetter = new ViewEdit.Caller("getText", new Object[0], Integer.class);
        final ViewEdit.Caller badTypesSetter = new ViewEdit.Caller("setText", new Object[] { Integer.class}, Void.TYPE);
        final ViewSnapshot.PropertyDescription badTypes = new ViewSnapshot.PropertyDescription("badTypes", TextView.class, badTypesGetter, badTypesSetter);
        props.add(badTypes);

        mSnapshot = new ViewSnapshot(props);
    }

    public void testViewSnapshot() throws IOException, JSONException {
        int width = View.MeasureSpec.makeMeasureSpec(768, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY);
        mRootView.measure(width, height);
        mRootView.layout(0, 0, 768, 1280);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        mSnapshot.snapshot("TEST CLASS", mRootView, out);

        final JSONObject json = new JSONObject(new String(out.toByteArray()));
        assertEquals("TEST CLASS", json.getString("class"));
        assertEquals(mRootView.hashCode(), json.getInt("rootView"));

        final JSONArray viewsJson = json.getJSONArray("views");
        assertEquals(mRootView.mAllViews.size(), viewsJson.length());

        final Map<Integer, View> viewsByHashcode = new HashMap<Integer, View>(mRootView.mViewsByHashcode);
        final Map<Integer, JSONObject> descsByHashcode = new HashMap<Integer, JSONObject>();
        for (int viewIx = 0; viewIx < viewsJson.length(); viewIx++) {
            final JSONObject viewDesc = viewsJson.getJSONObject(viewIx);
            final int descHashcode = viewDesc.getInt("hashCode");
            descsByHashcode.put(descHashcode, viewDesc);
            final View found = viewsByHashcode.remove(descHashcode);

            assertNotNull(found);
            assertEquals(found.getId(), viewDesc.getInt("id"));
            assertEquals(found.getHeight(), viewDesc.getInt("height"));
            assertEquals(found.getWidth(), viewDesc.getInt("width"));

            final JSONArray children = viewDesc.getJSONArray("children");
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

        assertTrue(viewsByHashcode.isEmpty());

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
    }

    public void testNoLayoutSnapshot() throws IOException, JSONException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        mSnapshot.snapshot("TEST CLASS", mRootView, out);

        final JSONObject json = new JSONObject(new String(out.toByteArray()));
        assertTrue(json.isNull("screenshot"));
    }

    private ViewSnapshot mSnapshot;
    private TestView mRootView;
}
