package com.mixpanel.android.abtesting;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;

import com.mixpanel.android.mpmetrics.MPConfig;
import com.mixpanel.android.mpmetrics.MixpanelAPI;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
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
        mChanges = new HashMap<String, ArrayList<JSONObject>>();
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

    public interface OnMixpanelABTestReceivedListener {
        public abstract void onMixpanelABTestReceived();
    }

    public void registerOnMixpanelABTestReceivedListener(Activity activity, OnMixpanelABTestReceivedListener listener) {
        synchronized(mABTestReceivedListeners) {
            mABTestReceivedListeners.add(new Pair<Activity, OnMixpanelABTestReceivedListener>(activity, listener));
        }
        synchronized (mChanges) {
            if (! mChanges.isEmpty()) {
                runABTestReceivedListeners();
            }
        }
    }

    private class LifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

        @Override
        public void onActivityCreated(Activity activity, Bundle bundle) {
        }

        @Override
        public void onActivityStarted(Activity activity) {
            activity.getWindow().getDecorView().findViewById(android.R.id.content).setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    int action = motionEvent.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                        mDown++;
                        if (mDown == 5) {
                            mMessageThreadHandler.sendMessage(mMessageThreadHandler.obtainMessage(MESSAGE_CONNECT_TO_EDITOR));
                        }
                    } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                        mDown--;
                    }
                    return true;
                }

                private int mDown = 0;
            });

            synchronized (mLiveActivities) {
                mLiveActivities.add(activity);
            }

            synchronized (mChanges) {
                final ArrayList<JSONObject> changes = mChanges.get(activity.getClass().getCanonicalName());
                if (null != changes) {
                    for (JSONObject j : changes) {
                        try {
                            final ViewEdit inst = mProtocol.readEdit(j);
                            inst.edit(activity.getWindow().getDecorView().getRootView());
                        } catch (EditProtocol.BadInstructionsException e) {
                            Log.e(LOGTAG, "Bad change request saved in mChanges", e);
                        }
                    }
                }
            }
        }

        @Override
        public void onActivityResumed(Activity activity) {
        }

        @Override
        public void onActivityPaused(Activity activity) {
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
                        handleChangesReceived((JSONObject) msg.obj, false, true);
                    } catch (JSONException e) {
                        Log.e(LOGTAG, "Bad change request received", e);
                    }
                    break;
            }
        }

        private void initializeChanges() {
            final String sharedPrefsName = SHARED_PREF_CHANGES_FILE + mToken;
            mPreferences = mContext.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);
            final String changes = mPreferences.getString(SHARED_PREF_CHANGES_KEY, null);
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

        private void handleChangesReceived(JSONObject changes, boolean persist, boolean applyToLive) throws JSONException {
            final String targetActivity = changes.getString("target");
            final JSONObject change = changes.getJSONObject("change");

            if (applyToLive) {
                try {
                    final ViewEdit edit = mProtocol.readEdit(change);
                    mUiThreadHandler.post(new Runnable() {
                        public void run() {
                            for (Activity a : mLiveActivities) {
                                if (a.getClass().getCanonicalName().equals(targetActivity)) {
                                    edit.edit(a.getWindow().getDecorView().getRootView());
                                }
                            }
                        }
                    });
                } catch (EditProtocol.BadInstructionsException e) {
                    Log.e(LOGTAG, "Bad instructions received", e);
                    return;
                }
            } else {
                synchronized (mChanges) {
                    mChanges.clear();
                    final ArrayList<JSONObject> changeList;
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
                final SharedPreferences.Editor editor = mPreferences.edit();
                editor.putString(SHARED_PREF_CHANGES_KEY, changes.toString());
                editor.commit();
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
        public BadConfigException(String message) {
            super(message);
        }

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
                } else if ("boolean".equals(type)) {
                    return (Boolean) jsonArgument;
                } else if ("int".equals(type)) {
                    return ((Number) jsonArgument).intValue();
                } else if ("float".equals(type)) {
                    return ((Number) jsonArgument).floatValue();
                } else if ("B64Drawable".equals(type)) {
                    byte[] bytes = Base64.decode((String) jsonArgument, 0);
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                } else if ("hexstring".equals(type)) {
                    int ret = new BigInteger((String) jsonArgument, 16).intValue();
                    return ret;
                } else {
                    throw new BadInstructionsException("Don't know how to interpret type " + type + " (arg was " + jsonArgument + ")");
                }
            } catch (ClassCastException e) {
                throw new BadInstructionsException("Couldn't interpret <" + jsonArgument + "> as " + type);
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

    private static class RootViewInfo {
        public RootViewInfo(String activityName, View rootView) {
            this.activityName = activityName;
            this.rootView = rootView;
        }

        public final String activityName;
        public final View rootView;
    }

    private final EditProtocol mProtocol;

    private final ArrayList<Pair<Activity, OnMixpanelABTestReceivedListener>> mABTestReceivedListeners =
            new ArrayList<Pair<Activity, OnMixpanelABTestReceivedListener>>();

    // Map from canonical activity class name to description of changes
    private final Map<String, ArrayList<JSONObject>> mChanges;

    private final Tweaks mTweaks = new Tweaks();
    private final Handler mUiThreadHandler;
    private final ABHandler mMessageThreadHandler;
    private final MixpanelAPI mMixpanel;

    // mLiveActivites is accessed across multiple threads, and must be synchronized.
    private final Set<Activity> mLiveActivities = new HashSet<Activity>();

    private static final Class[] NO_PARAMS = new Class[0];

    private static final String SHARED_PREF_CHANGES_FILE = "mixpanel.abtesting.changes";
    private static final String SHARED_PREF_CHANGES_KEY = "mixpanel.abtesting.changes";

    private static final int MESSAGE_INITIALIZE_CHANGES = 0;
    private static final int MESSAGE_CONNECT_TO_EDITOR = 1;
    private static final int MESSAGE_SEND_STATE_FOR_EDITING = 2;
    private static final int MESSAGE_HANDLE_CHANGES_RECEIVED = 3;
    private static final int MESSAGE_SEND_DEVICE_INFO = 4;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "ABTesting";
}
