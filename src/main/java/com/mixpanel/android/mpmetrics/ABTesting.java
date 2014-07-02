package com.mixpanel.android.mpmetrics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.mixpanel.android.abtesting.EditorConnection;
import com.mixpanel.android.abtesting.SampleConfig;
import com.mixpanel.android.abtesting.Tweaks;
import com.mixpanel.android.abtesting.ViewEdit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ABTesting class should at the parent level be very lightweight and simply proxy requests to
 * the ABHandler which runs on a HandlerThread
 */
@TargetApi(14)
public class ABTesting implements Application.ActivityLifecycleCallbacks {

    ABTesting(Context context, String token) {
        mContext = context;
        mToken = token;
        mChanges = null;

        final Application app = (Application) mContext.getApplicationContext();
        app.registerActivityLifecycleCallbacks(this);

        HandlerThread thread = new HandlerThread(ABTesting.class.getCanonicalName());
        thread.start();
        mHandler = new ABHandler(thread.getLooper());
        mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_INITIALIZE_CHANGES));

        Log.v(LOGTAG, "using hierarchy config:");
        Log.v(LOGTAG, getHierarchyConfig().toString());
    }

    public void onActivityCreated(Activity activity, Bundle bundle) { }

    @Override
    public void onActivityStarted(Activity activity) {
        activity.getWindow().getDecorView().findViewById(android.R.id.content).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                int action = motionEvent.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                    mDown++;
                    if (mDown == 5) {
                        mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_CONNECT_TO_EDITOR));
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                    mDown--;
                }
                return true;
            }

            private int mDown = 0;
        });
        mLiveActivities.add(activity);

        synchronized(mChanges) {
            ArrayList<JSONObject> changes = mChanges.get(activity.getClass().getCanonicalName());
            if (null != changes) {
                for (JSONObject j : changes) {
                    try {
                        ViewEdit inst = new ViewEdit(j, activity.getWindow().getDecorView().getRootView());
                        activity.runOnUiThread(inst);
                    } catch (ViewEdit.BadInstructionsException e) {
                        Log.e(LOGTAG, "Bad change request saved in mChanges", e);
                    }
                }
            }
        }
    }

    @Override
    public void onActivityResumed(Activity activity) { }

    @Override
    public void onActivityPaused(Activity activity) { }

    @Override
    public void onActivityStopped(Activity activity) {
        mLiveActivities.remove(activity);
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) { }

    @Override
    public void onActivityDestroyed(Activity activity) { }

    /**
     * This class is really the main class for ABTesting. It does all the work on a HandlerThread.
     */
    public class ABHandler extends Handler {

        public ABHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_INITIALIZE_CHANGES:
                    this.initializeChanges();
                    break;
                case MESSAGE_CONNECT_TO_EDITOR:
                    if (mEditorConnection == null || !mEditorConnection.isValid()) {
                        this.connectToEditor();
                    }
                    break;
                case MESSAGE_SEND_STATE_FOR_EDITING:
                    this.sendStateForEditing((JSONObject) msg.obj);
                    break;
                case MESSAGE_HANDLE_CHANGES_RECEIVED:
                    try {
                        handleChangesReceived((JSONObject) msg.obj, false, true);
                    } catch (JSONException e) {
                        Log.e(LOGTAG, "Bad change request received", e);
                    }
                    break;
            }
        }

        private void initializeChanges() {
            SharedPreferences prefs =
                    mContext.getSharedPreferences(SHARED_PREF_CHANGES_FILE, Context.MODE_PRIVATE);
            String changes = prefs.getString(SHARED_PREF_CHANGES_KEY, null);
            if (null != changes) {
                try {
                    handleChangesReceived(new JSONObject(changes), false, false);
                } catch (JSONException e) {
                    Log.i(LOGTAG, "JSON error when initializing saved ABTesting changes", e);
                    return;
                }
            }

        }

        private void connectToEditor() {
            Log.v(LOGTAG, "connectToEditor called");

            final String url = MPConfig.getInstance(mContext).getABTestingUrl() + mToken;
            try {
                mEditorConnection = new EditorConnection(new URI(url), mHandler);
            } catch (URISyntaxException e) {
                Log.e(LOGTAG, "Error parsing URI " + url + " for editor websocket", e);
            } catch (InterruptedException e) {
                Log.e(LOGTAG, "Error connecting to URI " + url, e);
            }
        }

        private void sendError(String errorMessage) {
            final JSONObject errorObject = new JSONObject();
            try {
                errorObject.put("error_message", errorMessage);
            } catch (JSONException e) {
                Log.e(LOGTAG, "Apparently impossible JSONException", e);
            }

            final OutputStreamWriter writer = new OutputStreamWriter(mEditorConnection.getBufferedOutputStream());
            try {
                writer.write("{\"type\": \"error\", ");
                writer.write("\"payload\": ");
                writer.write(errorObject.toString());
                writer.write("}");
            } catch (IOException e) {
                Log.e(LOGTAG, "Can't write error message to editor", e);
            } finally {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.e(LOGTAG, "    Could not close output writer to editor", e);
                }
            }
        }

        private void sendStateForEditing(JSONObject message) {
            Log.v(LOGTAG, "sendStateForEditing");

            if (mEditorConnection == null || !mEditorConnection.isValid()) {
                this.connectToEditor();
            }

            SnapshotConfig config;
            try {
                final JSONObject payload = message.getJSONObject("payload");
                config = new SnapshotConfig(payload); // TODO should this just be an EditInstructions?
            } catch (JSONException e) {
                Log.e(LOGTAG, "Payload with snapshot config required with snapshot request", e);
                sendError("Payload with snapshot config required with snapshot request");
                return;
            } catch (BadConfigException e) {
                Log.e(LOGTAG, "Editor sent malformed message with snapshot request", e);
                sendError(e.getMessage());
                return;
            }
            // ELSE config is valid:

            final OutputStreamWriter writer = new OutputStreamWriter(mEditorConnection.getBufferedOutputStream());
            try {
                writer.write("{\"type\": \"snapshot_response\",");

                boolean first = true;

                writer.write("\"activities\": [");
                for (Activity a : mLiveActivities) {
                    View rootView = a.getWindow().getDecorView().getRootView();
                    if (!first) {
                        writer.write(",");
                    }
                    first = false;

                    writer.write("{");

                    writer.write("\"class\":");
                    writer.write("\"" + a.getClass().getCanonicalName() + "\"");
                    writer.write(",");

                    writer.write("\"screenshot\": ");
                    writeScreenshot(rootView, writer);
                    writer.write(",");
                    writer.write("\"rootView\": ");
                    writer.write(Integer.toString(rootView.hashCode()));
                    writer.write(",");

                    writer.write("\"views\": [");
                    snapshotView(writer, rootView, config, true);
                    writer.write("]");

                    writer.write("}");
                }
                writer.write("],");

                writer.write("\"tweaks\":");
                writer.write(new JSONObject(mTweaks.getAll()).toString());

                writer.write("}");
            } catch (IOException e) {
                Log.e(LOGTAG, "Can't write snapshot request to server", e);
            } finally {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.e(LOGTAG, "    Can't close writer.", e);
                }
            }
        }

        private void handleChangesReceived(JSONObject changes, boolean persist, boolean applyToLive) throws JSONException {
            String targetActivity = changes.getString("target");
            JSONObject change = changes.getJSONObject("change");

            if (applyToLive) {
                try {
                    for (Activity a : mLiveActivities) {
                        if (a.getClass().getCanonicalName().equals(targetActivity)) {
                            ViewEdit inst = new ViewEdit(change, a.getWindow().getDecorView().getRootView());
                            a.runOnUiThread(inst);
                            break;
                        }
                    }
                } catch (ViewEdit.BadInstructionsException e) {
                    Log.e(LOGTAG, "Bad instructions received", e);
                }
            } else {
                mChanges = new HashMap<String, ArrayList<JSONObject>>();
                synchronized (mChanges) {
                    ArrayList<JSONObject> changeList;
                    if (mChanges.containsKey(targetActivity)) {
                        changeList = mChanges.get(targetActivity);
                    } else {
                        changeList = new ArrayList<JSONObject>();
                        mChanges.put(targetActivity, changeList);
                    }
                    changeList.add(change);
                }
                runABTestReceivedListeners();
            }

            if (persist) {
                Log.v(LOGTAG, "Persisting received changes");
                SharedPreferences.Editor editor =
                        mContext.getSharedPreferences(SHARED_PREF_CHANGES_FILE, Context.MODE_PRIVATE).edit();
                editor.putString(SHARED_PREF_CHANGES_KEY, changes.toString());
                editor.commit();
            }
        }

        // Writes a QUOTED, Base64 string to the given Writer, or the string "null" if no bitmap could be written
        // due to memory or rendering issues.
        private void writeScreenshot(View rootView, Writer writer) throws IOException {
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
                writer.write("\"");
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 30, out);
                writer.write(Base64.encodeToString(out.toByteArray(), Base64.NO_PADDING | Base64.NO_WRAP)); // TODO We can stream the base64, and this could be a *lot* of memory otherwise
                writer.write("\"");
            } else {
                writer.write("null");
            }

            if (!originalCacheState) {
                rootView.setDrawingCacheEnabled(false);
            }
        }

        private void snapshotView(Writer writer, View view, SnapshotConfig config, boolean first)
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
                    classes.put(klass.getName());
                    klass = klass.getSuperclass();
                } while (klass != Object.class);
                dump.put("classes", classes);

                config.addProperties(view, dump);

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
                    snapshotView(writer, child, config, false);
                }
            }
        }
    }

    private static class BadConfigException extends Exception {
        public BadConfigException(String message) {
            super(message);
        }

        public BadConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class SnapshotConfig {
        public SnapshotConfig(JSONObject config)
            throws BadConfigException {
            mProperties = new ArrayList<PropertyDescription>();

            try {
                final JSONArray classes = config.getJSONArray("classes");
                for (int classIx = 0; classIx < classes.length(); classIx++) {
                    final JSONObject classDesc = classes.getJSONObject(classIx);
                    final String targetClassName = classDesc.getString("name");
                    final Class targetClass = Class.forName(targetClassName);

                    final JSONArray propertyDescs = classDesc.getJSONArray("properties");
                    for (int i = 0; i < propertyDescs.length(); i++) {
                        final JSONObject propertyDesc = propertyDescs.getJSONObject(i);
                        final String propName = propertyDesc.getString("name");
                        final JSONObject accessorConfig = propertyDesc.getJSONObject("get");
                        final JSONObject mutatorConfig = propertyDesc.getJSONObject("set");

                        final String accessorName = accessorConfig.getString("selector");
                        final String accessorResultTypeName = accessorConfig.getJSONObject("result").getString("type");
                        final Class accessorResultType = Class.forName(accessorResultTypeName);
                        final Method accessorMethod = ViewEdit.getCompatibleMethod(targetClass, accessorName, NO_PARAMS, accessorResultType);
                        if (null == accessorMethod) {
                            throw new BadConfigException("Property accessor " + accessorName + " doesn't appear on type " + targetClassName + " (or param/return types are incorrect)");
                        }

                        final JSONArray mutatorParamConfig = mutatorConfig.getJSONArray("parameters");
                        final Class[] mutatorParamTypes = new Class[mutatorParamConfig.length()];
                        for (int paramIx = 0; paramIx < mutatorParamConfig.length(); paramIx++) {
                            final String paramTypeName = mutatorParamConfig.getJSONObject(paramIx).getString("type");
                            mutatorParamTypes[paramIx] = Class.forName(paramTypeName);
                        }
                        final String mutatorName = mutatorConfig.getString("selector");
                        final Method mutatorMethod = ViewEdit.getCompatibleMethod(targetClass, mutatorName, mutatorParamTypes, Void.TYPE);
                        if (null == mutatorMethod) {
                            throw new BadConfigException("Property accessor " + mutatorName + " doesn't appear on type " + targetClassName + " (or param/return types are incorrect)");
                        }

                        final PropertyDescription desc = new PropertyDescription(propName, targetClass, accessorMethod, mutatorMethod);
                        mProperties.add(desc);
                    }
                }
            } catch (JSONException e) {
                throw new BadConfigException("Can't read snapshot configuration", e);
            } catch (ClassNotFoundException e) {
                throw new BadConfigException("Can't resolve types for snapshot configuration", e);
            }
        }

        public void addProperties(View v, JSONObject out) {
            for (PropertyDescription desc: mProperties) {
                if (desc.targetClass.isAssignableFrom(v.getClass())) {
                    try {
                        Object value = desc.accessor.invoke(v); // TODO Marshalling this value?
                        out.put(desc.name, value);
                    } catch (IllegalAccessException e) {
                        Log.e(LOGTAG, "Can't call property access method " + desc.name + " - it is private or protected.", e);
                    } catch (InvocationTargetException e) {
                        Log.e(LOGTAG, "Encountered an exception while calling " + desc.name + " to read property.", e);
                    } catch (JSONException e) {
                        Log.e(LOGTAG, "Can't marshall value of property " + desc.name + " into JSON.", e);
                    }
                }
            }
        }

        private static class PropertyDescription {
            public PropertyDescription(String nm, Class target, Method access, Method mutate) {
                name = nm;
                targetClass = target;
                accessor = access;
                mutator = mutate;
            }

            public final String name;
            public final Class targetClass;
            public final Method accessor;
            public final Method mutator;
        }

        private final List<PropertyDescription> mProperties;

        private static final Class[] NO_PARAMS = new Class[0];
    }

    public interface TweakChangeCallback {
        public void onChange(Object value);
    }

    public Tweaks getTweaks() {
        return mTweaks;
    }

    public JSONObject getHierarchyConfig() {
        try {
            return SampleConfig.get();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public interface OnMixpanelABTestReceivedListener {
        public abstract void onMixpanelABTestReceived();
    }

    public void registerOnMixpanelABTestReceivedListener(Activity activity, OnMixpanelABTestReceivedListener listener) {
        synchronized(mABTestReceivedListeners) {
            mABTestReceivedListeners.add(new Pair<Activity, OnMixpanelABTestReceivedListener>(activity, listener));
        }
        if (null != mChanges) {
            runABTestReceivedListeners();
        }
    }

    private void runABTestReceivedListeners() {
        synchronized (mABTestReceivedListeners) {
            for (final Pair<Activity, OnMixpanelABTestReceivedListener> p : mABTestReceivedListeners) {
                p.first.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        p.second.onMixpanelABTestReceived();
                    }
                });
            }
        }
    }

    private ABHandler mHandler;
    private ArrayList<Pair<Activity, OnMixpanelABTestReceivedListener>> mABTestReceivedListeners =
            new ArrayList<Pair<Activity, OnMixpanelABTestReceivedListener>>();
    private Map<String, ArrayList<JSONObject>> mChanges;
    private EditorConnection mEditorConnection;

    private final Context mContext;
    private final String mToken;
    private final Tweaks mTweaks = new Tweaks();
    private final List<Activity> mLiveActivities = new ArrayList<Activity>();

    private static final int MESSAGE_INITIALIZE_CHANGES = 0;
    private static final int MESSAGE_CONNECT_TO_EDITOR = 1;
    private static final String SHARED_PREF_CHANGES_FILE = "mixpanel.abtesting.changes";
    private static final String SHARED_PREF_CHANGES_KEY = "mixpanel.abtesting.changes";

    private static final int MAX_BUFFER_SIZE = 4096; // TODO too small?

    public static final int MESSAGE_SEND_STATE_FOR_EDITING = 2;
    public static final int MESSAGE_HANDLE_CHANGES_RECEIVED = 3;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "ABTesting";
}
