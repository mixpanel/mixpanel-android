package com.mixpanel.android.abtesting;


import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;

/* package */ class ViewSnapshot {

    public static class PropertyDescription {
        public PropertyDescription(String name, Class targetClass, ViewEdit.Caller accessor, ViewEdit.Caller mutator) {
            this.name = name;
            this.targetClass = targetClass;
            this.accessor = accessor;
            this.mutator = mutator;
        }

        public final String name;
        public final Class targetClass;
        public final ViewEdit.Caller accessor;
        public final ViewEdit.Caller mutator;
    }

    public ViewSnapshot(List<PropertyDescription> properties) {
        mProperties = properties;
    }

    public void snapshot(String className, View rootView, OutputStream out) throws IOException {
        final Writer writer = new OutputStreamWriter(out);
        writer.write("{");

        writer.write("\"class\":");
        writer.write("\"" + className + "\"");
        writer.write(",");

        writer.write("\"screenshot\": ");
        writer.flush();
        writeScreenshot(rootView, out);
        writer.write(",");
        writer.write("\"rootView\": ");
        writer.write(Integer.toString(rootView.hashCode()));
        writer.write(",");

        writer.write("\"views\": [");
        snapshotView(writer, rootView, true);
        writer.write("]");

        writer.write("}");
        writer.flush();
    }


    // Writes a QUOTED, Base64 string to the given Writer, or the string "null" if no bitmap could be written
    // due to memory or rendering issues.
    private void writeScreenshot(View rootView, OutputStream out) throws IOException {
        // This screenshot method is not how the Android folks do it in View.createSnapshot,
        // but they use all kinds of secret internal stuff like clearing and setting
        // View.PFLAG_DIRTY_MASK and calling draw() - the below seems like the best we
        // can do without privileged access
        final boolean originalCacheState = rootView.isDrawingCacheEnabled();
        rootView.setDrawingCacheEnabled(true);
        rootView.buildDrawingCache(true);

        // We could get a null or zero px bitmap if the rootView hasn't been measured
        // appropriately, or we grab it before layout.
        // This is ok, and we should handle it gracefully.
        final Bitmap bitmap = rootView.getDrawingCache();
        if (null != bitmap && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
            out.write('"');
            final Base64OutputStream imageOut = new Base64OutputStream(out, Base64.NO_WRAP);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 30, imageOut);
            imageOut.flush();
            out.write('\"');
        } else {
            out.write("null".getBytes());
        }

        if (!originalCacheState) {
            rootView.setDrawingCacheEnabled(false);
        }
    }

    private void snapshotView(Writer writer, View view, boolean first)
            throws IOException {
        final JSONObject dump = new JSONObject();
        try {
            dump.put("hashCode", view.hashCode());
            dump.put("id", view.getId());
            dump.put("tag", view.getTag());

            dump.put("top", view.getTop());
            dump.put("left", view.getLeft());
            dump.put("width", view.getWidth());
            dump.put("height", view.getHeight());

            final JSONArray classes = new JSONArray();
            Class klass = view.getClass();
            do {
                classes.put(klass.getCanonicalName());
                klass = klass.getSuperclass();
            } while (klass != Object.class);
            dump.put("classes", classes);

            addProperties(view, dump);

            JSONArray children = new JSONArray();
            if (view instanceof ViewGroup) {
                final ViewGroup group = (ViewGroup) view;
                final int childCount = group.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    final View child = group.getChildAt(i);
                    children.put(child.hashCode());
                }
            }
            dump.put("children", children);
        } catch (JSONException impossible) {
            throw new RuntimeException("Apparently Impossible JSONException", impossible);
        }

        if (!first) {
            writer.write(", ");
        }

        writer.write(dump.toString());

        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            final int childCount = group.getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View child = group.getChildAt(i);
                snapshotView(writer, child, false);
            }
        }
    }

    private void addProperties(View v, JSONObject out) {
        for (PropertyDescription desc: mProperties) {
            if (desc.targetClass.isAssignableFrom(v.getClass())) {
                try {
                    Object value = desc.accessor.applyMethod(v);
                    out.put(desc.name, value);
                } catch (JSONException e) {
                    Log.e(LOGTAG, "Can't marshall value of property " + desc.name + " into JSON.", e);
                }
            }
        }
    }

    private final List<PropertyDescription> mProperties;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelABTest.ViewSnapshot";
}
