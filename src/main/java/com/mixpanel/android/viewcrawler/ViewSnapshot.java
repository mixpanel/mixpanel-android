package com.mixpanel.android.viewcrawler;


import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/* package */ class ViewSnapshot {

    public ViewSnapshot(List<PropertyDescription> properties, SparseArray<String> idsToNames) {
        mProperties = properties;
        mIdsToNames = idsToNames;
        mMainThreadHandler = new Handler(Looper.getMainLooper());
    }

    public void snapshots(final Set<Activity> liveActivities, OutputStream out) throws IOException {
        final RootViewFinder finder = new RootViewFinder(liveActivities);
        final FutureTask<List<RootViewInfo>> infoFuture = new FutureTask<List<RootViewInfo>>(finder);
        mMainThreadHandler.post(infoFuture);

        List<RootViewInfo> infoList;
        try {
            infoList = infoFuture.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            infoList = null;
        } catch (TimeoutException e) {
            if (MPConfig.DEBUG) {
                Log.i(LOGTAG, "Screenshot took *way* too long to be scheduled and executed", e);
            }
            infoList = null;
        } catch (ExecutionException e) {
            if (MPConfig.DEBUG) {
                Log.e(LOGTAG, "Exception thrown during screenshot attempt", e);
            }
            infoList = null;
        }

        if (null == infoList) {
            return; // For whatever reason, we can't snapshot right now
        }

        final Writer writer = new OutputStreamWriter(out);
        int infoCount = infoList.size();
        for (int i = 0; i < infoCount; i++) {
            if (i > 0) {
                writer.write(",");
                writer.flush();
            }

            final RootViewInfo info = infoList.get(i);
            writer.write("{");

            writer.write("\"activity\":");
            writer.write("\"" + info.activityName + "\"");
            writer.write(",");
            writer.write("\"scale\":");
            writer.write(String.format("%s", info.scale));
            writer.write(",");

            writer.flush();
            writeScreenshot(info.screenshot, out);
            writer.write(",");
            writer.write("\"serialized_objects\": ");

            writer.write("{");
            writer.write("\"rootObject\": ");
            writer.write(Integer.toString(info.rootView.hashCode()));
            writer.write(",");
            writer.write("\"objects\": [");
            snapshotView(writer, info.rootView, true);
            writer.write("]");
            writer.write("}"); // serialized_objects

            writer.write("}");
            writer.flush();
        }
    }

    // For testing only
    /* package */ List<PropertyDescription> getProperties() {
        return mProperties;
    }

    // Writes a QUOTED, Base64 string to the given Writer, or the string "null" if no bitmap could be written.
    private void writeScreenshot(Bitmap screenshot, OutputStream out) throws IOException {
        final Writer writer = new OutputStreamWriter(out);

        writer.write("\"screenshot\":");
        writer.flush();

        // We could get a null or zero px bitmap if the rootView hasn't been measured
        // appropriately, or we grab it before layout.
        // This is ok, and we should handle it gracefully.
        if (null != screenshot && screenshot.getWidth() > 0 && screenshot.getHeight() > 0) {
            out.write('"');
            final Base64OutputStream imageOut = new Base64OutputStream(out, Base64.NO_WRAP);
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, imageOut);
            imageOut.flush();
            out.write('\"');
        } else {
            out.write("null".getBytes());
        }
    }

    private void snapshotView(Writer writer, View view, boolean first)
            throws IOException {
        final JSONObject dump = new JSONObject();
        final int viewId = view.getId();
        final String viewIdName;
        if (-1 == viewId) {
            viewIdName = null;
        } else {
            viewIdName = mIdsToNames.get(viewId);
        }

        try {
            dump.put("hashCode", view.hashCode());
            dump.put("id", viewId);
            dump.put("mp_id_name", viewIdName);
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

            final JSONArray children = new JSONArray();
            if (view instanceof ViewGroup) {
                final ViewGroup group = (ViewGroup) view;
                final int childCount = group.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    final View child = group.getChildAt(i);
                    // child can be null when views are getting disposed.
                    if (null != child && View.VISIBLE == child.getVisibility()) {
                        children.put(child.hashCode());
                    }
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
                // child can be null when views are getting disposed.
                if (null != child && View.VISIBLE == child.getVisibility()) {
                    snapshotView(writer, child, false);
                }
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

    private static class RootViewFinder implements Callable<List<RootViewInfo>> {
        public RootViewFinder(Set<Activity> liveActivitySet) {
            mLiveActivitySet = liveActivitySet;
            mDisplayMetrics = new DisplayMetrics();
            mRootViews = new ArrayList<RootViewInfo>();
            mScalePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        }

        @Override
        public List<RootViewInfo> call() throws Exception {
            mRootViews.clear();

            // liveActivitySet is synchronized with (and updated in) another thread
            synchronized (mLiveActivitySet) {
                for (Activity a : mLiveActivitySet) {
                    final String activityName = a.getClass().getCanonicalName();

                    // We know (since we're synched w/ the UI thread on activity changes)
                    // that the activities in mLiveActivities are valid here.
                    final View rootView = a.getWindow().getDecorView().getRootView();
                    a.getWindowManager().getDefaultDisplay().getMetrics(mDisplayMetrics);
                    final RootViewInfo info = new RootViewInfo(activityName, rootView);
                    mRootViews.add(info);
                }
            }

            int viewCount = mRootViews.size();
            for (int i = 0; i < viewCount; i++) {
                final RootViewInfo info = mRootViews.get(i);
                takeScreenshot(info);
            }

            return mRootViews;
        }

        private void takeScreenshot(final RootViewInfo info) {
            final View rootView = info.rootView;
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
            } catch (RuntimeException e) {
                if (MPConfig.DEBUG) {
                    Log.d(LOGTAG, "Can't take a bitmap snapshot of view " + rootView + ", skipping for now.", e);
                }
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

            if (null != originalCacheState && !originalCacheState) {
                rootView.setDrawingCacheEnabled(false);
            }
            info.scale = scale;
            info.screenshot = bitmap;
        }

        private final Set<Activity> mLiveActivitySet;
        private final List<RootViewInfo> mRootViews;
        private final DisplayMetrics mDisplayMetrics;
        private final Paint mScalePaint;

        private final int mClientDensity = DisplayMetrics.DENSITY_DEFAULT;
    }

    private static class RootViewInfo {
        public RootViewInfo(String activityName, View rootView) {
            this.activityName = activityName;
            this.rootView = rootView;
            this.screenshot = null;
            this.scale = 1.0f;
        }

        public final String activityName;
        public final View rootView;
        public Bitmap screenshot;
        public float scale;
    }

    private final List<PropertyDescription> mProperties;
    private final SparseArray<String> mIdsToNames;
    private final Handler mMainThreadHandler;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.ViewSnapshot";
}
