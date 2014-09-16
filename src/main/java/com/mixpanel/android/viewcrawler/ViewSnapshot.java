package com.mixpanel.android.viewcrawler;


import android.graphics.Bitmap;
import android.os.Build;
import android.util.AndroidRuntimeException;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.mixpanel.android.mpmetrics.MPConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;

/* package */ class ViewSnapshot {

    public ViewSnapshot(List<PropertyDescription> properties) {
        mProperties = properties;
    }

    public void snapshot(String activityName, float scale, View rootView, OutputStream out) throws IOException {
        final Writer writer = new OutputStreamWriter(out);
        writer.write("{");

        writer.write("\"activity\":");
        writer.write("\"" + activityName + "\"");
        writer.write(",");
        writer.write("\"scale\":");
        writer.write(String.format("%s", scale));
        writer.write(",");

        writer.write("\"screenshot\": ");
        writer.flush();
        writeScreenshot(rootView, scale, out);
        writer.write(",");
        writer.write("\"serialized_objects\": ");

        writer.write("{");
        writer.write("\"rootObject\": ");
        writer.write(Integer.toString(rootView.hashCode()));
        writer.write(",");
        writer.write("\"objects\": [");
        snapshotView(writer, rootView, true);
        writer.write("]");
        writer.write("}"); // serialized_objects

        writer.write("}");
        writer.flush();
    }

    // For testing only
    /* package */ List<PropertyDescription> getProperties() {
        return mProperties;
    }

    // Writes a QUOTED, Base64 string to the given Writer, or the string "null" if no bitmap could be written
    // due to memory or rendering issues.
    private void writeScreenshot(View rootView, float scale, OutputStream out) throws IOException {
        // This screenshot method is not how the Android folks do it in View.createSnapshot,
        // but they use all kinds of secret internal stuff like clearing and setting
        // View.PFLAG_DIRTY_MASK and calling draw() - the below seems like the best we
        // can do without privileged access

        final boolean originalCacheState = rootView.isDrawingCacheEnabled();

        Bitmap bitmap;
        try {
            rootView.setDrawingCacheEnabled(true);
            rootView.buildDrawingCache(true);
            final Bitmap rawBitmap = rootView.getDrawingCache();
            final int scaledWidth = (int) (rawBitmap.getWidth() * scale);
            final int scaledHeight = (int) (rawBitmap.getHeight() * scale);
            bitmap = Bitmap.createScaledBitmap(rawBitmap, scaledWidth, scaledHeight, true);
        } catch (AndroidRuntimeException e) {
            // This can happen if buildDrawingCache invalidates the view, or basically anything in
            // View.draw tries to change the state of the view- we'll get a threading error in this case.
            // We can eliminate the errors by running this on the UI thread, but that requires
            // us to do a lot of tricky things and pass around a large and possibly invalid bitmap,
            // or do a lot of IO and compression on the UI thread.
            if (MPConfig.DEBUG) {
                Log.d(LOGTAG, "Can't take a bitmap snapshot of view " + rootView + ", skipping for now.", e);
            }
            bitmap = null;
        }


        // We could get a null or zero px bitmap if the rootView hasn't been measured
        // appropriately, or we grab it before layout.
        // This is ok, and we should handle it gracefully.
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

            float translationX = 0;
            float translationY = 0;
            if (Build.VERSION.SDK_INT >= 11) {
                translationX = view.getTranslationX();
                translationY = view.getTranslationY();
            }

            dump.put("translationX", translationX);
            dump.put("translationY", translationY);

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
            if (desc.targetClass.isAssignableFrom(v.getClass()) && null != desc.accessor) {
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
    private static final String LOGTAG = "MixpanelAPI.ViewSnapshot";
}
