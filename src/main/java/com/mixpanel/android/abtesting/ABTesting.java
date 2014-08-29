package com.mixpanel.android.abtesting;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewTreeObserver;

import com.mixpanel.android.mpmetrics.MPConfig;
import com.mixpanel.android.mpmetrics.MixpanelAPI;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ABTesting class should at the parent level be very lightweight and simply proxy requests to
 * the ABHandler which runs on a HandlerThread
 */
@TargetApi(14)
public class ABTesting { // TODO Rename, this is no longer about ABTesting if we're doing dynamic tracking

    public ABTesting(Context context, String token, MixpanelAPI mixpanel) {
        mPersistentChanges = new HashMap<String, List<JSONObject>>();
        mEditorChanges = new HashMap<String, List<JSONObject>>();
        mProtocol = new EditProtocol();

        final Application app = (Application) context.getApplicationContext();
        app.registerActivityLifecycleCallbacks(new LifecycleCallbacks());

        final HandlerThread thread = new HandlerThread(ABTesting.class.getCanonicalName());
        thread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mMessageThreadHandler = new ABHandler(context, token, thread.getLooper());
        mMessageThreadHandler.sendMessage(mMessageThreadHandler.obtainMessage(MESSAGE_INITIALIZE_CHANGES));

        mUiThreadHandler = new Handler(Looper.getMainLooper());
        mMixpanel = mixpanel;
    }

    public Tweaks getTweaks() {
        return mTweaks;
    }

    // TODO this is (or will be) part of the public API of the lib.
    @SuppressWarnings("unused")
    public void registerOnMixpanelABTestReceivedListener(Activity activity, OnMixpanelABTestReceivedListener listener) {
        synchronized (mABTestReceivedListeners) {
            mABTestReceivedListeners.add(new Pair<Activity, OnMixpanelABTestReceivedListener>(activity, listener));
        }
        synchronized (mPersistentChanges) {
            if (!mPersistentChanges.isEmpty()) {
                runABTestReceivedListeners();
            }
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

    public interface OnMixpanelABTestReceivedListener {
        public abstract void onMixpanelABTestReceived();
    }

    // TODO Must be called on UI Thread
    private void applyAllChanges() {
        synchronized (mLiveActivities) {
            for (Activity activity : mLiveActivities) {
                final String activityName = activity.getClass().getCanonicalName();

                final List<JSONObject> persistentChanges;
                synchronized (mPersistentChanges) {
                    persistentChanges = mPersistentChanges.get(activityName);
                    applyTheseChanges(activity, persistentChanges);
                }


                final List<JSONObject> editorChanges;
                synchronized (mEditorChanges) {
                    editorChanges = mEditorChanges.get(activityName);
                    applyTheseChanges(activity, editorChanges);
                }
            }
        }
    }

    // TODO Must be called on UI Thread
    private void applyTheseChanges(Activity activity, List<JSONObject> changes) {
        if (null != changes) {
            for (JSONObject j : changes) {
                try {
                    final ViewEdit inst = mProtocol.readEdit(j);
                    final View rootView = activity.getWindow().getDecorView().getRootView();
                    final EditBinding binding = new EditBinding(rootView, inst);
                    binding.performEdit();
                } catch (EditProtocol.BadInstructionsException e) {
                    Log.e(LOGTAG, "Bad change request cannot be applied", e);
                }
            }
        }
    }

    private class FlipListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {
            final long timestamp = event.timestamp;
            // Options - artificially downsample to

            final float[] smoothed = smoothXYZ(event.values);

            final int oldFlipState = mFlipState;

            // TODO wrong, must account for transitions between flipped up and flipped down
            // This only works on Earth, where gravity is near 9.8 m/s*s
            if (smoothed[0] > 2.0 || smoothed[0] < -2.0) {
                mFlipState = FLIP_STATE_NONE;
            } else if (smoothed[1] > 2.0 || smoothed[1] < -2.0) {
                mFlipState = FLIP_STATE_NONE;
            } else if (smoothed[2] > 9.0) {
                mFlipState = FLIP_STATE_UP;
            } else if (smoothed[2] < -9.0) {
                mFlipState = FLIP_STATE_DOWN;
            } else {
                mFlipState = FLIP_STATE_NONE;
            }

            if (oldFlipState != mFlipState) {
                mLastFlipTime = event.timestamp;
            }

            final long flipDurationNanos = event.timestamp - mLastFlipTime;

            if (mFlipState == FLIP_STATE_NONE && mTriggerState != 0) {
                if (flipDurationNanos > 1000000000) { // 1 sec to flip
                    mTriggerState = 0;
                    Log.d(LOGTAG, "No Flip, Resetting trigger to zero, duration " + flipDurationNanos);
                }
            } else if (mFlipState == FLIP_STATE_UP && mTriggerState == 0) {
                if (flipDurationNanos > 1000000000) { // 1 secs up
                    mTriggerState = 1;
                    Log.d(LOGTAG, "Flipped up! Setting trigger to 1 duration " + flipDurationNanos);
                }
            } else if (mFlipState == FLIP_STATE_DOWN && mTriggerState == 1) {
                if (flipDurationNanos > 1000000000) { // 1 secs down
                    mTriggerState = 2;
                    Log.d(LOGTAG, "Flipped Down! Setting trigger to 2 duration " + flipDurationNanos);
                }
            } else if (mFlipState == FLIP_STATE_UP && mTriggerState == 2) {
                if (flipDurationNanos > 1000000000) { // 1 secs up
                    mTriggerState = 0;
                    Log.d(LOGTAG, "Connection triggered! Attempting to connect duration " + flipDurationNanos);
                    final Message message = mMessageThreadHandler.obtainMessage(MESSAGE_CONNECT_TO_EDITOR);
                    mMessageThreadHandler.sendMessage(message);
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            ; // Do nothing
        }

        private float[] smoothXYZ(final float[] samples) {
            for (int i = 0; i < 3; i++) {
                final float oldVal = mSmoothed[i];
                mSmoothed[i] = oldVal + (ACCELEROMETER_SMOOTHING * (samples[i] - oldVal));
            }

            return mSmoothed;
        }

        int mTriggerState = -1;
        int mFlipState = FLIP_STATE_NONE;
        long mLastFlipTime = -1;
        final float[] mSmoothed = new float[3];
    }

    private class LifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

        @Override
        public void onActivityCreated(Activity activity, Bundle bundle) {
        }

        @Override
        public void onActivityStarted(Activity activity) {
            synchronized (mLiveActivities) {
                mLiveActivities.add(activity);
            }

            applyAllChanges();
        }

        @Override
        public void onActivityResumed(Activity activity) {
            Log.e(LOGTAG, "onActivityResumed was called with " + activity);
            final SensorManager sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
            final Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(mFlipListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }

        @Override
        public void onActivityPaused(Activity activity) {
            final SensorManager sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
            sensorManager.unregisterListener(mFlipListener);
        }

        @Override
        public void onActivityStopped(Activity activity) {
            synchronized (mLiveActivities) {
                mLiveActivities.remove(activity);
            }
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }

        private final FlipListener mFlipListener = new FlipListener();
    }

    /**
     * This class is really the main class for ABTesting. It does all the work on a HandlerThread.
     */
    private class ABHandler extends Handler {

        public ABHandler(Context context, String token, Looper looper) {
            super(looper);
            mContext = context;
            mToken = token;
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
                case MESSAGE_SEND_DEVICE_INFO:
                    this.sendDeviceInfo();
                    break;
                case MESSAGE_SEND_STATE_FOR_EDITING:
                    this.sendStateForEditing((JSONObject) msg.obj);
                    break;
                case MESSAGE_HANDLE_CHANGES_RECEIVED:
                    try {
                        final JSONObject change = (JSONObject) msg.obj;
                        loadChange(mEditorChanges, change);
                        mUiThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                applyAllChanges();
                            }
                        });
                        // handleChangesReceived((JSONObject) msg.obj, false, true);
                    } catch (JSONException e) {
                        Log.e(LOGTAG, "Bad change request received", e);
                    }
                    break;
            }
        }

        private void initializeChanges() {
            final String sharedPrefsName = SHARED_PREF_CHANGES_FILE + mToken;
            mPreferences = mContext.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);

            // TODO This should be list valued
            final String changes = mPreferences.getString(SHARED_PREF_CHANGES_KEY, null);
            if (null != changes) {
                try {
                    synchronized(mPersistentChanges) {
                        mPersistentChanges.clear();
                        loadChange(mPersistentChanges, new JSONObject(changes));
                    }
                    runABTestReceivedListeners();
                } catch (JSONException e) {
                    Log.i(LOGTAG, "JSON error when initializing saved ABTesting changes", e);
                    return;
                }
            }

        }

        private void connectToEditor() {
            Log.v(LOGTAG, "connectToEditor called");

            final String url = MPConfig.getInstance(mContext).getEditorUrl() + mToken;
            try {
                mEditorConnection = new EditorConnection(new URI(url), new EditorService());
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

        private void sendDeviceInfo() {
            Log.v(LOGTAG, "sendDeviceInfo");

            final OutputStream out = mEditorConnection.getBufferedOutputStream();
            final OutputStreamWriter writer = new OutputStreamWriter(out);

            try {
                writer.write("{\"type\": \"device_info_response\",");
                writer.write("\"payload\": {");
                writer.write("\"available_font_families\": [],"); // TODO temp value during development
                writer.write("\"device_name\": \"Android Device\",");
                writer.write("\"tweaks\":");
                writer.write(new JSONObject(mTweaks.getAll()).toString());
                writer.write("}"); // payload
                writer.write("}");
            } catch (IOException e) {
                Log.e(LOGTAG, "Can't write device_info to server", e);
            } finally {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.e(LOGTAG, "Can't close websocket writer", e);
                }
            }
        }

        private void sendStateForEditing(JSONObject message) {
            Log.v(LOGTAG, "sendStateForEditing");

            if (mEditorConnection == null || !mEditorConnection.isValid()) {
                this.connectToEditor();
            }

            ViewSnapshot snapshot;
            try {
                final JSONObject payload = message.getJSONObject("payload");
                snapshot = mProtocol.readSnapshotConfig(payload);
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

            final OutputStream out = mEditorConnection.getBufferedOutputStream();
            final OutputStreamWriter writer = new OutputStreamWriter(out);

            final List<RootViewInfo> rootViews = new ArrayList<RootViewInfo>();

            synchronized (mLiveActivities) {
                for (Activity a : mLiveActivities) {
                    final String activityName = a.getClass().getCanonicalName();

                    // We know (since we're synched w/ the UI thread on activity changes)
                    // that the activities in mLiveActivities are valid here.
                    final View rootView = a.getWindow().getDecorView().getRootView();
                    final RootViewInfo info = new RootViewInfo(activityName, rootView);
                    rootViews.add(info);
                }
            }

            try {
                writer.write("{\"type\": \"snapshot_response\",");

                boolean first = true;

                writer.write("\"activities\": [");
                writer.flush();
                for (RootViewInfo info : rootViews) {
                    if (!first) {
                        writer.write(",");
                        writer.flush();
                    }
                    first = false;
                    snapshot.snapshot(info.activityName, info.rootView, out);
                }
                writer.write("]");
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

        private void loadChange(Map <String, List<JSONObject>> changes, JSONObject newChange) throws JSONException {
            final String targetActivity = newChange.getString("target");
            final JSONObject change = newChange.getJSONObject("change");

            synchronized (changes) {
                final List<JSONObject> changeList;
                if (changes.containsKey(targetActivity)) {
                    changeList = changes.get(targetActivity);
                } else {
                    changeList = new ArrayList<JSONObject>();
                    changes.put(targetActivity, changeList);
                }
                changeList.add(change);
            }
        }

        private EditorConnection mEditorConnection;
        private SharedPreferences mPreferences;
        private final Context mContext;
        private final String mToken;
    }

    private class EditorService implements EditorConnection.EditorService {

        @Override
        public void sendSnapshot(JSONObject message) {
            Message msg = mMessageThreadHandler.obtainMessage(ABTesting.MESSAGE_SEND_STATE_FOR_EDITING);
            msg.obj = message;
            mMessageThreadHandler.sendMessage(msg);
        }

        @Override
        public void performEdit(JSONObject message) {
            Message msg = mMessageThreadHandler.obtainMessage(ABTesting.MESSAGE_HANDLE_CHANGES_RECEIVED);
            msg.obj = message;
            mMessageThreadHandler.sendMessage(msg);
        }

        @Override
        public void sendDeviceInfo() {
            Message msg = mMessageThreadHandler.obtainMessage(ABTesting.MESSAGE_SEND_DEVICE_INFO);
            mMessageThreadHandler.sendMessage(msg);
        }
    }

    private static class BadConfigException extends Exception {
        public BadConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private class EditProtocol {

        public class BadInstructionsException extends Exception {
            public BadInstructionsException(String message) {
                super(message);
            }

            public BadInstructionsException(String message, Exception e) {
                super(message, e);
            }
        }

        public ViewEdit readEdit(JSONObject source)
                throws BadInstructionsException {
            try {
                final int viewId = source.getInt("view_id");
                final JSONArray pathDesc = source.getJSONArray("path");
                final List<ViewEdit.PathElement> path = new ArrayList<ViewEdit.PathElement>();

                for(int i = 0; i < pathDesc.length(); i++) {
                    final JSONObject targetView = pathDesc.getJSONObject(i);
                    final String targetViewClass = targetView.getString("view_class");
                    final int targetIndex = targetView.getInt("index");
                    path.add(new ViewEdit.PathElement(targetViewClass, targetIndex));
                }

                if (viewId == -1 && path.size() == 0) {
                    throw new BadInstructionsException("Path selector was empty and no view id was provided.");
                }

                if (source.has("method")) {
                    final String methodName = source.getString("method");
                    final JSONArray argsAndTypes = source.getJSONArray("args");
                    final Object[] methodArgs = new Object[argsAndTypes.length()];
                    final Class[] methodTypes = new Class[argsAndTypes.length()];

                    for (int i = 0; i < argsAndTypes.length(); i++) {
                        final JSONArray argPlusType = argsAndTypes.getJSONArray(i);
                        final Object jsonArg = argPlusType.get(0);
                        final String argType = argPlusType.getString(1);
                        methodArgs[i] = convertArgument(jsonArg, argType);
                        methodTypes[i] = methodArgs[i].getClass();
                    }

                    final Caller caller = new Caller(methodName, methodArgs, Void.TYPE);
                    return new ViewEdit.PropertySetEdit(viewId, path, caller);
                }

                if (source.has("event_name")) {
                    final String eventName = source.getString("event_name");
                    return new ViewEdit.AddListenerEdit(viewId, path, eventName, mMixpanel);
                }

                throw new BadInstructionsException("Instructions contained neither a method to call nor an event to track");
            } catch (JSONException e) {
                throw new BadInstructionsException("Can't interpret instructions due to JSONException", e);
            }
        }

        private ViewSnapshot readSnapshotConfig(JSONObject source)
            throws BadConfigException {
            final List<ViewSnapshot.PropertyDescription> properties = new ArrayList<ViewSnapshot.PropertyDescription>();

            try {
                final JSONArray classes = source.getJSONArray("classes");
                for (int classIx = 0; classIx < classes.length(); classIx++) {
                    final JSONObject classDesc = classes.getJSONObject(classIx);
                    final String targetClassName = classDesc.getString("name");
                    final Class targetClass = Class.forName(targetClassName);

                    final JSONArray propertyDescs = classDesc.getJSONArray("properties");
                    for (int i = 0; i < propertyDescs.length(); i++) {
                        final JSONObject propertyDesc = propertyDescs.getJSONObject(i);
                        final String propName = propertyDesc.getString("name");

                        Caller accessor = null;
                        if (propertyDesc.has("get")) {
                            final JSONObject accessorConfig = propertyDesc.getJSONObject("get");
                            final String accessorName = accessorConfig.getString("selector");
                            final String accessorResultTypeName = accessorConfig.getJSONObject("result").getString("type");
                            final Class accessorResultType = Class.forName(accessorResultTypeName);
                            accessor = new Caller(accessorName, NO_PARAMS, accessorResultType);
                        }

                        Caller mutator = null;
                        if (propertyDesc.has("set")) {
                            final JSONObject mutatorConfig = propertyDesc.getJSONObject("set");
                            final JSONArray mutatorParamConfig = mutatorConfig.getJSONArray("parameters");
                            final Class[] mutatorParamTypes = new Class[mutatorParamConfig.length()];
                            for (int paramIx = 0; paramIx < mutatorParamConfig.length(); paramIx++) {
                                final String paramTypeName = mutatorParamConfig.getJSONObject(paramIx).getString("type");
                                mutatorParamTypes[paramIx] = Class.forName(paramTypeName);
                            }
                            final String mutatorName = mutatorConfig.getString("selector");
                            mutator = new Caller(mutatorName, mutatorParamTypes, Void.TYPE);
                        }

                        final ViewSnapshot.PropertyDescription desc = new ViewSnapshot.PropertyDescription(propName, targetClass, accessor, mutator);
                        properties.add(desc);
                    }
                }

                return new ViewSnapshot(properties);
            } catch (JSONException e) {
                throw new BadConfigException("Can't read snapshot configuration", e);
            } catch (ClassNotFoundException e) {
                throw new BadConfigException("Can't resolve types for snapshot configuration", e);
            }
        }

        private Object convertArgument(Object jsonArgument, String type)
                throws BadInstructionsException {
            // Object is a Boolean, JSONArray, JSONObject, Number, String, or JSONObject.NULL
            try {
                if ("java.lang.CharSequence".equals(type)) { // Because we're assignable
                    return (String) jsonArgument;
                } else if ("boolean".equals(type) || "java.lang.Boolean".equals(type)) {
                    return (Boolean) jsonArgument;
                } else if ("int".equals(type) || "java.lang.Integer".equals(type)) {
                    return ((Number) jsonArgument).intValue();
                } else if ("float".equals(type) || "java.lang.Float".equals(type)) {
                    return ((Number) jsonArgument).floatValue();
                } else if ("android.graphics.Bitmap".equals(type)) {
                    byte[] bytes = Base64.decode((String) jsonArgument, 0);
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                } else {
                    throw new BadInstructionsException("Don't know how to interpret type " + type + " (arg was " + jsonArgument + ")");
                }
            } catch (ClassCastException e) {
                throw new BadInstructionsException("Couldn't interpret <" + jsonArgument + "> as " + type);
            }
        }
    }

    /* The binding between a bunch of edits and a view. Should be instantiated and live on the UI thread */
    private static class EditBinding implements ViewTreeObserver.OnGlobalLayoutListener {
        public EditBinding(View viewRoot, ViewEdit edit) {
            mEdit = edit;
            mViewRoot = new WeakReference<View>(viewRoot);
            ViewTreeObserver observer = viewRoot.getViewTreeObserver();
            performEdit();

            if (observer.isAlive()) {
                observer.addOnGlobalLayoutListener(this);
            }
        }

        @Override
        public void onGlobalLayout() {
            performEdit();
        }

        public void performEdit() {
            View viewRoot = mViewRoot.get();
            if (null == viewRoot) {
                return;
            }
            // ELSE View is alive

            mEdit.edit(viewRoot);
        }

        public boolean viewIsAlive() {
            return mViewRoot.get() != null;
        }

        private final WeakReference<View> mViewRoot;
        private final ViewEdit mEdit;
    }

    private static class RootViewInfo {
        public RootViewInfo(String activityName, View rootView) {
            this.activityName = activityName;
            this.rootView = rootView;
        }

        public final String activityName;
        public final View rootView;
    }

    // mABTestReceivedListeners is accessed on multiple threads and must be synchronized
    private final ArrayList<Pair<Activity, OnMixpanelABTestReceivedListener>> mABTestReceivedListeners =
            new ArrayList<Pair<Activity, OnMixpanelABTestReceivedListener>>();

    // Map from canonical activity class name to description of changes
    // Accessed from Multiple Threads, must be synchronized
    private final Map<String, List<JSONObject>> mPersistentChanges;
    private final Map<String, List<JSONObject>> mEditorChanges;

    // mLiveActivites is accessed across multiple threads, and must be synchronized.
    private final Set<Activity> mLiveActivities = new HashSet<Activity>();

    private final EditProtocol mProtocol;
    private final Tweaks mTweaks = new Tweaks();
    private final Handler mUiThreadHandler;
    private final ABHandler mMessageThreadHandler;
    private final MixpanelAPI mMixpanel;

    private static final Class[] NO_PARAMS = new Class[0];

    private static final String SHARED_PREF_CHANGES_FILE = "mixpanel.abtesting.changes";
    private static final String SHARED_PREF_CHANGES_KEY = "mixpanel.abtesting.changes";

    private static final int MESSAGE_INITIALIZE_CHANGES = 0;
    private static final int MESSAGE_CONNECT_TO_EDITOR = 1;
    private static final int MESSAGE_SEND_STATE_FOR_EDITING = 2;
    private static final int MESSAGE_HANDLE_CHANGES_RECEIVED = 3;
    private static final int MESSAGE_SEND_DEVICE_INFO = 4;

    private static final int FLIP_STATE_UP = -1;
    private static final int FLIP_STATE_NONE = 0;
    private static final int FLIP_STATE_DOWN = 1;
    private static final float ACCELEROMETER_SMOOTHING = 0.3f;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "ABTesting";
}
