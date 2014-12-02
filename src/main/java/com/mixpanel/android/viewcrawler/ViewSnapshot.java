package com.mixpanel.android.viewcrawler;


import android.annotation.TargetApi;
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
import android.util.JsonWriter;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import com.mixpanel.android.mpmetrics.MPConfig;

import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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

@TargetApi(MPConfig.UI_FEATURES_MIN_API)
/* package */ class ViewSnapshot {

    public ViewSnapshot(List<PropertyDescription> properties, SparseArray<String> idsToNames) {
        mProperties = properties;
        mIdsToNames = idsToNames;
        mMainThreadHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Take a snapshot of each activity in liveActivities. The given UIThreadSet will be accessed
     * on the main UI thread, and should contain a set with elements for every activity to be
     * snapshotted. Given stream out will be written on the calling thread.
     */
    public void snapshots(UIThreadSet<Activity> liveActivities, OutputStream out) throws IOException {
        final RootViewFinder finder = new RootViewFinder(liveActivities);
        final FutureTask<List<RootViewInfo>> infoFuture = new FutureTask<List<RootViewInfo>>(finder);
        mMainThreadHandler.post(infoFuture);

        final OutputStreamWriter writer = new OutputStreamWriter(out);

        try {
            final List<RootViewInfo> infoList = infoFuture.get(1, TimeUnit.SECONDS);
            int infoCount = infoList.size();
            writer.write("[");
            for (int i = 0; i < infoCount; i++) {
                if (i > 0) {
                    writer.write(",");
                }
                final RootViewInfo info = infoList.get(i);
                writer.write("{");
                writer.write("\"activity\":");
                writer.write(JSONObject.quote(info.activityName));
                writer.write(",");
                writer.write("\"scale\":");
                writer.write(String.format("%s", info.scale));
                writer.write(",");
                writer.write("\"serialized_objects\":");
                {
                    final JsonWriter j = new JsonWriter(writer);
                    j.beginObject();
                    j.name("rootObject").value(info.rootView.hashCode());
                    j.name("objects");
                    snapshotViewHierarchy(j, info.rootView);
                    j.endObject();
                    j.flush();
                }
                writer.write(",");
                writer.write("\"screenshot\":");
                writer.flush();
                writeScreenshot(info.screenshot, out);
                writer.write("}");
            }
            writer.write("]");
            writer.flush();
        } catch (InterruptedException e) {
            if (MPConfig.DEBUG) {
                Log.d(LOGTAG, "Screenshot interrupted, no screenshot will be sent.", e);
            }
        } catch (TimeoutException e) {
            if (MPConfig.DEBUG) {
                Log.i(LOGTAG, "Screenshot took more than 1 second to be scheduled and executed. No screenshot will be sent.", e);
            }
        } catch (ExecutionException e) {
            if (MPConfig.DEBUG) {
                Log.e(LOGTAG, "Exception thrown during screenshot attempt", e);
            }
        }
    }

    // For testing only
    /* package */ List<PropertyDescription> getProperties() {
        return mProperties;
    }

    /* package */ void snapshotViewHierarchy(JsonWriter j, View rootView)
        throws IOException {
        j.beginArray();
        snapshotView(j, rootView);
        j.endArray();
    }

    private void snapshotView(JsonWriter j, View view)
            throws IOException {
        final int viewId = view.getId();
        final String viewIdName;
        if (-1 == viewId) {
            viewIdName = null;
        } else {
            viewIdName = mIdsToNames.get(viewId);
        }

        j.beginObject();
        j.name("hashCode").value(view.hashCode());
        j.name("id").value(viewId);
        j.name("mp_id_name").value(viewIdName);

        final CharSequence description = view.getContentDescription();
        if (null == description) {
            j.name("contentDescription").nullValue();
        } else {
            j.name("contentDescription").value(description.toString());
        }

        final Object tag = view.getTag();
        if (null == tag) {
            j.name("tag").nullValue();
        } else if (tag instanceof CharSequence) {
            j.name("tag").value(tag.toString());
        }

        j.name("top").value(view.getTop());
        j.name("left").value(view.getLeft());
        j.name("width").value(view.getWidth());
        j.name("height").value(view.getHeight());
        j.name("scrollX").value(view.getScrollX());
        j.name("scrollY").value(view.getScrollY());

        float translationX = 0;
        float translationY = 0;
        if (Build.VERSION.SDK_INT >= 11) {
            translationX = view.getTranslationX();
            translationY = view.getTranslationY();
        }

        j.name("translationX").value(translationX);
        j.name("translationY").value(translationY);

        j.name("classes");
        j.beginArray();
        Class klass = view.getClass();
        do {
            j.value(klass.getCanonicalName());
            klass = klass.getSuperclass();
        } while (klass != Object.class);
        j.endArray();

        addProperties(j, view);

        j.name("subviews");
        j.beginArray();
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            final int childCount = group.getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View child = group.getChildAt(i);
                // child can be null when views are getting disposed.
                if (null != child && View.VISIBLE == child.getVisibility()) {
                    j.value(child.hashCode());
                }
            }
        }
        j.endArray();
        j.endObject();

        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            final int childCount = group.getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View child = group.getChildAt(i);
                // child can be null when views are getting disposed.
                if (null != child && View.VISIBLE == child.getVisibility()) {
                    snapshotView(j, child);
                }
            }
        }
    }

    // Writes a QUOTED, Base64 string to the given Writer, or the string "null" if no bitmap could be written.
    private void writeScreenshot(Bitmap screenshot, OutputStream out) throws IOException {
        // We could get a null or zero px bitmap if the rootView hasn't been measured
        // appropriately, or we grab it before layout.
        // This is ok, and we should handle it gracefully.
        if (null != screenshot && screenshot.getWidth() > 0 && screenshot.getHeight() > 0) {
            out.write('"');
            final Base64OutputStream imageOut = new Base64OutputStream(out, Base64.NO_WRAP);
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, imageOut);
            imageOut.flush();
            out.write('"');
        } else {
            out.write("null".getBytes());
        }
    }

    private void addProperties(JsonWriter j, View v)
        throws IOException {
        for (PropertyDescription desc : mProperties) {
            if (desc.targetClass.isAssignableFrom(v.getClass()) && null != desc.accessor) {
                Object value = desc.accessor.applyMethod(v);
                if (null == value) {
                    // Don't produce anything in this case
                } else if (value instanceof Number) {
                    j.name(desc.name).value((Number) value);
                } else if (value instanceof Boolean) {
                    j.name(desc.name).value((Boolean) value);
                } else {
                    j.name(desc.name).value(value.toString());
                }
            }
        }
    }

    private static class RootViewFinder implements Callable<List<RootViewInfo>> {
        public RootViewFinder(final UIThreadSet<Activity> liveActivities) {
            mLiveActivities = liveActivities;
            mDisplayMetrics = new DisplayMetrics();
            mRootViews = new ArrayList<RootViewInfo>();
            mScalePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        }

        @Override
        public List<RootViewInfo> call() throws Exception {
            mRootViews.clear();

            final Set<Activity> liveActivities = mLiveActivities.getAll();

            for (Activity a : liveActivities) {
                final String activityName = a.getClass().getCanonicalName();
                final View rootView = a.getWindow().getDecorView().getRootView();
                a.getWindowManager().getDefaultDisplay().getMetrics(mDisplayMetrics);
                final RootViewInfo info = new RootViewInfo(activityName, rootView);
                mRootViews.add(info);
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
                if (MPConfig.DEBUG) {
                    Log.v(LOGTAG, "Can't call createSnapshot, will use drawCache", e);
                }
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
                    originalCacheState = rootView.isDrawingCacheEnabled();
                    rootView.setDrawingCacheEnabled(true);
                    rootView.buildDrawingCache(true);
                    rawBitmap = rootView.getDrawingCache();
                }
            } catch (RuntimeException e) {
                if (MPConfig.DEBUG) {
                    Log.v(LOGTAG, "Can't take a bitmap snapshot of view " + rootView + ", skipping for now.", e);
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

        private final UIThreadSet<Activity> mLiveActivities;
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
