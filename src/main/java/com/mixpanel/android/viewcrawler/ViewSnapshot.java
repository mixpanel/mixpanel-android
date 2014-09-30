package com.mixpanel.android.viewcrawler;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.util.AndroidRuntimeException;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.DisplayMetrics;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/* package */ class ViewSnapshot {

    public ViewSnapshot(List<PropertyDescription> properties) {
        mProperties = properties;
        mScalePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    }

    public void snapshot(String activityName, View rootView, OutputStream out) throws IOException {
        final Writer writer = new OutputStreamWriter(out);
        writer.write("{");

        writer.write("\"activity\":");
        writer.write("\"" + activityName + "\"");
        writer.write(",");

        writer.flush();
        writeScreenshot(rootView, out);
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
    private void writeScreenshot(View rootView, OutputStream out) throws IOException {
        // This screenshot method is not how the Android folks do it in View.createSnapshot,
        // but they use all kinds of secret internal stuff like clearing and setting
        // View.PFLAG_DIRTY_MASK and calling draw() - the below seems like the best we
        // can do without privileged access

        Bitmap rawBitmap = null;

        try {
            final Method createSnapshot = View.class.getDeclaredMethod("createSnapshot", Bitmap.Config.class, Integer.TYPE, Boolean.TYPE);
            createSnapshot.setAccessible(true);
            rawBitmap = (Bitmap) createSnapshot.invoke(rootView, Bitmap.Config.RGB_565, Color.WHITE, false);
        } catch (NoSuchMethodException e) {
            Log.d(LOGTAG, "Can't call createSnapshot, using drawCache", e);
        } catch (IllegalArgumentException e) {
            Log.d(LOGTAG, "Can't call createSnapshot with arguments", e);
        } catch (InvocationTargetException e) {
            Log.e(LOGTAG, "Exception when calling createSnapshot", e);
        } catch (IllegalAccessException e) {
            Log.e(LOGTAG, "Can't access createSnapshot, using drawCache", e);
        } catch (ClassCastException e) {
            Log.e(LOGTAG, "createSnapshot didn't return a bitmap?", e);
        }

        Boolean originalCacheState = null;
        try {
            if (null == rawBitmap) {
                Log.d(LOGTAG, "View.createSnapshot not available. Rendering from drawCache");
                originalCacheState = rootView.isDrawingCacheEnabled();
                rootView.setDrawingCacheEnabled(true);
                rootView.buildDrawingCache(true);
                rawBitmap = rootView.getDrawingCache();
            }
        } catch (AndroidRuntimeException e) {
            // This can happen if buildDrawingCache invalidates the view, or basically anything in
            // View.draw tries to change the state of the view- we'll get a threading error in this case.
            // We can eliminate the errors by running this on the UI thread, but that requires
            // us to do a lot of tricky things and pass around a large and possibly invalid bitmap,
            // or do a lot of IO and compression on the UI thread.
            if (MPConfig.DEBUG) {
                Log.d(LOGTAG, "Can't take a bitmap snapshot of view " + rootView + ", skipping for now.", e);
            }
        }

        if (null != originalCacheState && !originalCacheState) {
            rootView.setDrawingCacheEnabled(false);
        }

        float scale = 1.0f;
        Bitmap bitmap = null;
        if (null != rawBitmap) {
            final int rawDensity = rawBitmap.getDensity();

            if (rawDensity != Bitmap.DENSITY_NONE) {
                scale = ((float) mClientDensity) / rawDensity;
            }

            final int rawWidth = rawBitmap.getWidth();
            final int rawHeight = rawBitmap.getHeight();
            final int destWidth = (int) ((rawBitmap.getWidth() * scale) + 0.5);
            final int destHeight = (int) ((rawBitmap.getHeight() * scale) + 0.5);

            if (rawWidth > 0 && rawHeight > 0 && destWidth > 0 && destHeight > 0) {
                try {
                    bitmap = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.RGB_565);

                    if (null != bitmap) {
                        bitmap.setDensity(mClientDensity);
                        final Canvas scaledCanvas = new Canvas(bitmap);
                        scaledCanvas.drawBitmap(rawBitmap, 0, 0, mScalePaint);
                    }
                } catch (OutOfMemoryError e) {
                    bitmap = null;
                }
            }
        }

        final Writer writer = new OutputStreamWriter(out);
        writer.write("\"scale\":");
        writer.write(String.format("%s", scale));
        writer.write(",");
        writer.write("\"screenshot\":");
        writer.flush();

        // We could get a null or zero px bitmap if the rootView hasn't been measured
        // appropriately, or we grab it before layout.
        // This is ok, and we should handle it gracefully.
        if (null != bitmap && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
            out.write('"');
            final Base64OutputStream imageOut = new Base64OutputStream(out, Base64.NO_WRAP);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, imageOut);
            imageOut.flush();
            out.write('\"');
        } else {
            out.write("null".getBytes());
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
            dump.put("subviews", children);
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
    private final Paint mScalePaint;
    private final int mClientDensity = DisplayMetrics.DENSITY_DEFAULT;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.ViewSnapshot";
}
