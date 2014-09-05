package com.mixpanel.android.viewcrawler;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Base64;
import android.util.Log;
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
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLSocketFactory;

/**
 * This class is for internal use by the Mixpanel API, and should
 * not be called directly by your code.
 */
@TargetApi(14)
public class ViewCrawler {

    public ViewCrawler(Context context, String token, MixpanelAPI mixpanel) {
        mPersistentChanges = new HashMap<String, List<JSONObject>>();
        mEditorChanges = new HashMap<String, List<JSONObject>>();
        mProtocol = new EditProtocol();

        final Application app = (Application) context.getApplicationContext();
        app.registerActivityLifecycleCallbacks(new LifecycleCallbacks());

        final HandlerThread thread = new HandlerThread(ViewCrawler.class.getCanonicalName());
        thread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mMessageThreadHandler = new ViewCrawlerHandler(context, token, thread.getLooper());
        mMessageThreadHandler.sendMessage(mMessageThreadHandler.obtainMessage(MESSAGE_INITIALIZE_CHANGES));

        mUiThreadHandler = new Handler(Looper.getMainLooper());
        mMixpanel = mixpanel;
    }

    public Tweaks getTweaks() {
        return mTweaks;
    }

    // Must be called on UI Thread
    private void applyAllChanges() {
        synchronized (mLiveActivities) {
            for (Activity activity : mLiveActivities) {
                final String activityName = activity.getClass().getCanonicalName();

                synchronized (mPersistentChanges) {
                    final List<JSONObject> persistentChanges = mPersistentChanges.get(activityName);
                    applyChangesFromList(activity, persistentChanges);
                }

                synchronized (mEditorChanges) {
                    final List<JSONObject> editorChanges = mEditorChanges.get(activityName);
                    applyChangesFromList(activity, editorChanges);
                }
            }
        }
    }

    // Must be called on UI Thread
    private void applyChangesFromList(Activity activity, List<JSONObject> changes) {
        if (null != changes) {
            for (JSONObject j : changes) {
                try {
                    final ViewVisitor inst = mProtocol.readEdit(j);
                    final View rootView = activity.getWindow().getDecorView().getRootView();
                    final EditBinding binding = new EditBinding(rootView, inst);
                    binding.performEdit();
                } catch (BadInstructionsException e) {
                    Log.e(LOGTAG, "Bad change request cannot be applied", e);
                }
            }
        }
    }

    private class LifecycleCallbacks implements Application.ActivityLifecycleCallbacks, FlipGesture.OnFlipGestureListener {

        public LifecycleCallbacks() {
            mFlipGesture = new FlipGesture(this);
        }

        @Override
        public void onFlipGesture() {
            final Message message = mMessageThreadHandler.obtainMessage(MESSAGE_CONNECT_TO_EDITOR);
            mMessageThreadHandler.sendMessage(message);
        }

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
            final SensorManager sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
            final Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(mFlipGesture, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }

        @Override
        public void onActivityPaused(Activity activity) {
            final SensorManager sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
            sensorManager.unregisterListener(mFlipGesture);
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

        private final FlipGesture mFlipGesture;
    }

    /**
     * This class is really the main class for ABTesting. It does all the work on a HandlerThread.
     */
    private class ViewCrawlerHandler extends Handler {

        public ViewCrawlerHandler(Context context, String token, Looper looper) {
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
                case MESSAGE_HANDLE_EDITOR_CHANGES_RECEIVED:
                    this.handleEditorChangesReceived((JSONObject) msg.obj);
                    break;
                case MESSAGE_HANDLE_PERSISTENT_CHANGES_RECEIVED:
                    this.handlePersistentChangesReceived((JSONObject) msg.obj);
                    break;
            }
        }

        private void initializeChanges() {
            final String sharedPrefsName = prefChangesFileName();
            final SharedPreferences preferences = mContext.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);

            final String storedChanges = preferences.getString(SHARED_PREF_CHANGES_KEY, null);
            if (null != storedChanges) {
                try {
                    final JSONArray changes = new JSONArray(storedChanges);
                    synchronized(mPersistentChanges) {
                        mPersistentChanges.clear();
                        for (int i = 0; i < changes.length(); i++) {
                            final JSONObject change = changes.getJSONObject(i);
                            loadChange(mPersistentChanges, change);
                        }
                    }
                } catch (JSONException e) {
                    Log.i(LOGTAG, "JSON error when initializing saved ViewCrawler changes", e);
                    return;
                }
            }
        }

        private void connectToEditor() {
            if (MPConfig.DEBUG) {
                Log.d(LOGTAG, "connectToEditor called");
            }

            final String url = MPConfig.getInstance(mContext).getEditorUrl() + mToken;
            try {
                final SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                final Socket sslSocket = socketFactory.createSocket();
                mEditorConnection = new EditorConnection(new URI(url), new EditorService(), sslSocket);
            } catch (IOException e) {
                Log.i(LOGTAG, "Can't create SSL Socket to connect to editor service", e);
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
            final OutputStream out = mEditorConnection.getBufferedOutputStream();
            final OutputStreamWriter writer = new OutputStreamWriter(out);

            try {
                writer.write("{\"type\": \"device_info_response\",");
                writer.write("\"payload\": {");
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
            if (mEditorConnection == null || !mEditorConnection.isValid()) {
                this.connectToEditor();
            }

            final ViewSnapshot snapshot;
            try {
                final JSONObject payload = message.getJSONObject("payload");
                snapshot = mProtocol.readSnapshotConfig(payload);
            } catch (JSONException e) {
                Log.e(LOGTAG, "Payload with snapshot config required with snapshot request", e);
                sendError("Payload with snapshot config required with snapshot request");
                return;
            } catch (BadInstructionsException e) {
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

        private void handleEditorChangesReceived(JSONObject change) {
            try {
                loadChange(mEditorChanges, change);
                mUiThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        applyAllChanges();
                    }
                });
            } catch (JSONException e) {
                Log.e(LOGTAG, "Bad change request received", e);
            }
        }

        private void handlePersistentChangesReceived(JSONObject message) {
            final JSONArray changes;
            try {
                changes = message.getJSONArray("changes");
            } catch (JSONException e) {
                Log.e(LOGTAG, "Can't read persistent changes from JSONMessage " + message.toString(), e);
                return;
            }

            final String sharedPrefsName = prefChangesFileName();
            final SharedPreferences preferences = mContext.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);
            final SharedPreferences.Editor editor = preferences.edit();
            editor.putString(SHARED_PREF_CHANGES_KEY, changes.toString());
            editor.apply();
            initializeChanges();
            mUiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    applyAllChanges();
                }
            });
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

        private String prefChangesFileName() {
            return SHARED_PREF_CHANGES_FILE + mToken;
        }

        private EditorConnection mEditorConnection;
        private final Context mContext;
        private final String mToken;
    }

    private class EditorService implements EditorConnection.EditorService {

        @Override
        public void sendSnapshot(JSONObject message) {
            Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_SEND_STATE_FOR_EDITING);
            msg.obj = message;
            mMessageThreadHandler.sendMessage(msg);
        }

        @Override
        public void performEdit(JSONObject message) {
            Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_HANDLE_EDITOR_CHANGES_RECEIVED);
            msg.obj = message;
            mMessageThreadHandler.sendMessage(msg);
        }

        @Override
        public void persistEdits(JSONObject message) {
            Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_HANDLE_PERSISTENT_CHANGES_RECEIVED);
            msg.obj = message;
            mMessageThreadHandler.sendMessage(msg);
        }

        @Override
        public void sendDeviceInfo() {
            Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_SEND_DEVICE_INFO);
            mMessageThreadHandler.sendMessage(msg);
        }
    }

    public class BadInstructionsException extends Exception {
        public BadInstructionsException(String message) {
            super(message);
        }

        public BadInstructionsException(String message, Exception e) {
            super(message, e);
        }
    }

    private class EditProtocol {

        public PropertyDescription readPropertyDescription(Class targetClass, JSONObject propertyDesc)
            throws BadInstructionsException {
            try {
                final String propName = propertyDesc.getString("name");

                Caller accessor = null;
                if (propertyDesc.has("get")) {
                    final JSONObject accessorConfig = propertyDesc.getJSONObject("get");
                    final String accessorName = accessorConfig.getString("selector");
                    final String accessorResultTypeName = accessorConfig.getJSONObject("result").getString("type");
                    final Class accessorResultType = Class.forName(accessorResultTypeName);
                    accessor = new Caller(accessorName, NO_PARAMS, accessorResultType);
                }

                final String mutatorName;
                if (propertyDesc.has("set")) {
                    final JSONObject mutatorConfig = propertyDesc.getJSONObject("set");
                    mutatorName = mutatorConfig.getString("selector");
                } else {
                    mutatorName = null;
                }

                return new PropertyDescription(propName, targetClass, accessor, mutatorName);
            } catch (JSONException e) {
                throw new BadInstructionsException("Can't read property JSON", e);
            } catch (ClassNotFoundException e) {
                throw new BadInstructionsException("Can't read property JSON, relevant arg/return class not found", e);
            }
        }

        public ViewVisitor readEdit(JSONObject source)
                throws BadInstructionsException {
            try {
                final JSONArray pathDesc = source.getJSONArray("path");
                final List<ViewVisitor.PathElement> path = new ArrayList<ViewVisitor.PathElement>();

                for(int i = 0; i < pathDesc.length(); i++) {
                    final JSONObject targetView = pathDesc.getJSONObject(i);
                    final String targetViewClass = targetView.getString("view_class");
                    final int targetIndex = targetView.getInt("index");
                    path.add(new ViewVisitor.PathElement(targetViewClass, targetIndex));
                }

                if (path.size() == 0) {
                    throw new BadInstructionsException("Path selector was empty.");
                }

                if (source.has("property")) {
                    final ViewVisitor.PathElement pathEnd = path.get(path.size() - 1);
                    final String targetClassName = pathEnd.viewClassName;
                    final Class targetClass;
                    try {
                        targetClass = Class.forName(targetClassName);
                    } catch (ClassNotFoundException e) {
                        throw new BadInstructionsException("Can't find class for visit path: " + targetClassName, e);
                    }

                    final PropertyDescription prop = readPropertyDescription(targetClass, source.getJSONObject("property"));

                    final JSONArray argsAndTypes = source.getJSONArray("args");
                    final Object[] methodArgs = new Object[argsAndTypes.length()];
                    for (int i = 0; i < argsAndTypes.length(); i++) {
                        final JSONArray argPlusType = argsAndTypes.getJSONArray(i);
                        final Object jsonArg = argPlusType.get(0);
                        final String argType = argPlusType.getString(1);
                        methodArgs[i] = convertArgument(jsonArg, argType);
                    }

                    final Caller mutator = prop.makeMutator(methodArgs);
                    if (null == mutator) {
                        throw new BadInstructionsException("Can't update a read-only property " + prop.name + " (add a mutator to make this work)");
                    }

                    return new ViewVisitor.PropertySetVisitor(path, mutator, prop.accessor);
                } else if (source.has("event_name")) {
                    final String eventName = source.getString("event_name");
                    final String eventType = source.getString("event_type");
                    if ("click".equals(eventType)) {
                        return new ViewVisitor.AddListenerVisitor(path, eventName, mMixpanel);
                    } else if ("detected".equals(eventType)) {
                        return new ViewVisitor.ViewDetectorVisitor(path, eventName, mMixpanel);
                    } else {
                        throw new BadInstructionsException("Mixpanel can't track event type \"" + eventType + "\"");
                    }
                }

                throw new BadInstructionsException("Instructions contained neither a method to call nor an event to track");
            } catch (JSONException e) {
                throw new BadInstructionsException("Can't interpret instructions due to JSONException", e);
            }
        }

        private ViewSnapshot readSnapshotConfig(JSONObject source)
            throws BadInstructionsException {
            final List<PropertyDescription> properties = new ArrayList<PropertyDescription>();

            try {
                final JSONArray classes = source.getJSONArray("classes");
                for (int classIx = 0; classIx < classes.length(); classIx++) {
                    final JSONObject classDesc = classes.getJSONObject(classIx);
                    final String targetClassName = classDesc.getString("name");
                    final Class targetClass = Class.forName(targetClassName);

                    final JSONArray propertyDescs = classDesc.getJSONArray("properties");
                    for (int i = 0; i < propertyDescs.length(); i++) {
                        final JSONObject propertyDesc = propertyDescs.getJSONObject(i);
                        final PropertyDescription desc = readPropertyDescription(targetClass, propertyDesc);
                        properties.add(desc);
                    }
                }

                return new ViewSnapshot(properties);
            } catch (JSONException e) {
                throw new BadInstructionsException("Can't read snapshot configuration", e);
            } catch (ClassNotFoundException e) {
                throw new BadInstructionsException("Can't resolve types for snapshot configuration", e);
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
        public EditBinding(View viewRoot, ViewVisitor edit) {
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

            mEdit.visit(viewRoot);
        }

        private final WeakReference<View> mViewRoot;
        private final ViewVisitor mEdit;
    }

    private static class RootViewInfo {
        public RootViewInfo(String activityName, View rootView) {
            this.activityName = activityName;
            this.rootView = rootView;
        }

        public final String activityName;
        public final View rootView;
    }

    // Map from canonical activity class name to description of changes
    // Accessed from Multiple Threads, must be synchronized
    private final Map<String, List<JSONObject>> mPersistentChanges;
    private final Map<String, List<JSONObject>> mEditorChanges;

    // mLiveActivites is accessed across multiple threads, and must be synchronized.
    private final Set<Activity> mLiveActivities = new HashSet<Activity>();

    private final EditProtocol mProtocol;
    private final Tweaks mTweaks = new Tweaks();
    private final Handler mUiThreadHandler;
    private final ViewCrawlerHandler mMessageThreadHandler;
    private final MixpanelAPI mMixpanel;

    private static final Class[] NO_PARAMS = new Class[0];

    private static final String SHARED_PREF_CHANGES_FILE = "mixpanel.abtesting.changes";
    private static final String SHARED_PREF_CHANGES_KEY = "mixpanel.abtesting.changes";

    private static final int MESSAGE_INITIALIZE_CHANGES = 0;
    private static final int MESSAGE_CONNECT_TO_EDITOR = 1;
    private static final int MESSAGE_SEND_STATE_FOR_EDITING = 2;
    private static final int MESSAGE_HANDLE_EDITOR_CHANGES_RECEIVED = 3;
    private static final int MESSAGE_SEND_DEVICE_INFO = 4;
    private static final int MESSAGE_HANDLE_PERSISTENT_CHANGES_RECEIVED = 5;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.ViewCrawler";
}
