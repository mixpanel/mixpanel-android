package com.mixpanel.android.viewcrawler;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
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
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

/**
 * This class is for internal use by the Mixpanel API, and should
 * not be called directly by your code.
 */
@TargetApi(14)
public class ViewCrawler implements ViewVisitor.OnVisitedListener, UpdatesFromMixpanel {

    public ViewCrawler(Context context, String token, MixpanelAPI mixpanel) {
        mPersistentChanges = new HashMap<String, List<JSONObject>>();
        mEditorChanges = new HashMap<String, List<JSONObject>>();
        mPersistentEventBindings = new HashMap<String, List<JSONObject>>();
        mEditorEventBindings = new HashMap<String, List<JSONObject>>();
        mProtocol = new EditProtocol(context);

        final Application app = (Application) context.getApplicationContext();
        app.registerActivityLifecycleCallbacks(new LifecycleCallbacks());

        final HandlerThread thread = new HandlerThread(ViewCrawler.class.getCanonicalName());
        thread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mMessageThreadHandler = new ViewCrawlerHandler(context, token, thread.getLooper());
        mMessageThreadHandler.sendMessage(mMessageThreadHandler.obtainMessage(MESSAGE_INITIALIZE_CHANGES));

        // We build our own, private SSL context here to prevent things from going
        // crazy if client libs are using older versions of OkHTTP, or otherwise doing crazy junk
        // with the default SSL context: https://github.com/square/okhttp/issues/184
        SSLSocketFactory foundSSLFactory;
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            foundSSLFactory = sslContext.getSocketFactory();
        } catch (GeneralSecurityException e) {
            Log.d(LOGTAG, "System has no SSL support. Built-in events editor will not be available", e);
            foundSSLFactory = null;
        }
        mSSLSocketFactory = foundSSLFactory;

        mUiThreadHandler = new Handler(Looper.getMainLooper());
        mMixpanel = mixpanel;
    }

    @Override
    public Tweaks getTweaks() {
        return mTweaks;
    }

    @Override
    public void setEventBindings(JSONArray bindings) {
        Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_EVENT_BINDINGS_RECEIVED);
        msg.obj = bindings;
        mMessageThreadHandler.sendMessage(msg);
    }

    @Override
    public void OnVisited(String eventName) {
        mMixpanel.track(eventName, null);
    }

    private void applyAllChangesOnUiThread() {
        mUiThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                applyAllChanges();
            }
        });
    }

    // Must be called on UI Thread
    private void applyAllChanges() {
        synchronized (mLiveActivities) {
            for (Activity activity : mLiveActivities) {
                final String activityName = activity.getClass().getCanonicalName();
                final View rootView = activity.getWindow().getDecorView().getRootView();

                final List<JSONObject> persistentChanges;
                final List<JSONObject> wildcardPersistentChanges;
                synchronized (mPersistentChanges) {
                    persistentChanges = mPersistentChanges.get(activityName);
                    wildcardPersistentChanges = mPersistentChanges.get(null);
                }

                applyChangesFromList(rootView, persistentChanges);
                applyChangesFromList(rootView, wildcardPersistentChanges);

                final List<JSONObject> editorChanges;
                final List<JSONObject> wildcardEditorChanges;
                synchronized (mEditorChanges) {
                    editorChanges = mEditorChanges.get(activityName);
                    wildcardEditorChanges = mEditorChanges.get(null);
                }

                applyChangesFromList(rootView, editorChanges);
                applyChangesFromList(rootView, wildcardEditorChanges);

                final List<JSONObject> eventBindings;
                final List<JSONObject> wildcardEventBindings;
                synchronized (mPersistentEventBindings) {
                    eventBindings = mPersistentEventBindings.get(activityName);
                    wildcardEventBindings = mPersistentEventBindings.get(null);
                }

                applyBindingsFromList(rootView, eventBindings);
                applyBindingsFromList(rootView, wildcardEventBindings);

                final List<JSONObject> editorBindings;
                final List<JSONObject> wildcardEditorBindings;
                synchronized (mEditorEventBindings) {
                    editorBindings = mEditorEventBindings.get(activityName);
                    wildcardEditorBindings = mEditorEventBindings.get(null);
                }

                applyBindingsFromList(rootView, editorBindings);
                applyBindingsFromList(rootView, wildcardEditorBindings);
            }
        }
    }

    // Must be called on UI Thread
    private void applyChangesFromList(View rootView, List<JSONObject> changes) {
        if (null != changes) {
            int size = changes.size();
            for (int i = 0; i < size; i++) {
                JSONObject desc = changes.get(i);
                try {
                    final ViewVisitor visitor = mProtocol.readEdit(desc);
                    final EditBinding binding = new EditBinding(rootView, visitor);
                    binding.performEdit();
                } catch (EditProtocol.BadInstructionsException e) {
                    Log.e(LOGTAG, "Bad change request cannot be applied", e);
                }
            }
        }
    }

    // Must be called on the UI Thread
    private void applyBindingsFromList(View rootView, List<JSONObject> eventBindings) {
        if (null != eventBindings) {
            int size = eventBindings.size();
            for (int i = 0; i < size; i++) {
                JSONObject desc = eventBindings.get(i);
                try {
                    final ViewVisitor visitor = mProtocol.readEventBinding(desc, this);
                    final EditBinding binding = new EditBinding(rootView, visitor);
                    binding.performEdit();
                } catch (EditProtocol.BadInstructionsException e) {
                    Log.e(LOGTAG, "Bad binding cannot be applied", e);
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

    private class ViewCrawlerHandler extends Handler {

        public ViewCrawlerHandler(Context context, String token, Looper looper) {
            super(looper);
            mContext = context;
            mToken = token;
            mSnapshot = null;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_INITIALIZE_CHANGES:
                    initializeChanges();
                    break;
                case MESSAGE_CONNECT_TO_EDITOR:
                    connectToEditor();
                    break;
                case MESSAGE_DISCONNECT_FROM_EDITOR:
                    disconnectFromEditor();
                    break;
                case MESSAGE_SEND_DEVICE_INFO:
                    sendDeviceInfo();
                    break;
                case MESSAGE_SEND_STATE_FOR_EDITING:
                    sendStateForEditing((JSONObject) msg.obj);
                    break;
                case MESSAGE_HANDLE_EDITOR_CHANGES_RECEIVED:
                    handleEditorChangeReceived((JSONObject) msg.obj);
                    break;
                case MESSAGE_EVENT_BINDINGS_RECEIVED:
                    handleEventBindingsReceived((JSONArray) msg.obj);
                    break;
                case MESSAGE_HANDLE_EDITOR_BINDINGS_RECEIVED:
                    handleEditorBindingsReceived((JSONObject) msg.obj);
                    break;
            }
        }

        private void initializeChanges() {
            final SharedPreferences preferences = getSharedPreferences();
            final String storedChanges = preferences.getString(SHARED_PREF_CHANGES_KEY, null);
            final String storedBindings = preferences.getString(SHARED_PREF_BINDINGS_KEY, null);
            try {
                if (null != storedChanges) {
                    final JSONArray changes = new JSONArray(storedChanges);

                    synchronized(mPersistentChanges) {
                        mPersistentChanges.clear();
                        for (int i = 0; i < changes.length(); i++) {
                            final JSONObject change = changes.getJSONObject(i);
                            loadChange(mPersistentChanges, change);
                        }
                    }
                }

                if (null != storedBindings) {
                    final JSONArray bindings = new JSONArray(storedBindings);

                    synchronized(mPersistentEventBindings) {
                        mPersistentEventBindings.clear();
                        for (int i = 0; i < bindings.length(); i++) {
                            final JSONObject event = bindings.getJSONObject(i);
                            loadEventBinding(event, mPersistentEventBindings);
                        }
                    }
                }
            } catch (JSONException e) {
                Log.i(LOGTAG, "JSON error when initializing saved changes, clearing persistent memory", e);
                final SharedPreferences.Editor editor = preferences.edit();
                editor.remove(SHARED_PREF_CHANGES_KEY);
                editor.remove(SHARED_PREF_BINDINGS_KEY);
                editor.apply();
            }
        }

        private void connectToEditor() {
            if (MPConfig.DEBUG) {
                Log.d(LOGTAG, "connecting to editor");
            }

            if (mEditorConnection != null && mEditorConnection.isValid()) {
                if (MPConfig.DEBUG) {
                    Log.d(LOGTAG, "There is already a valid connection to an events editor.");
                }
                return;
            }

            if (null == mSSLSocketFactory) {
                Log.d(LOGTAG, "SSL is not available on this device, no connection will be attempted to the events editor.");
                return;
            }

            final String url = MPConfig.getInstance(mContext).getEditorUrl() + mToken;
            try {
                final Socket sslSocket = mSSLSocketFactory.createSocket();
                mEditorConnection = new EditorConnection(new URI(url), new Editor(), sslSocket);
            } catch (URISyntaxException e) {
                Log.e(LOGTAG, "Error parsing URI " + url + " for editor websocket", e);
            } catch (EditorConnection.EditorConnectionException e) {
                Log.e(LOGTAG, "Error connecting to URI " + url, e);
            } catch (IOException e) {
                Log.i(LOGTAG, "Can't create SSL Socket to connect to editor service", e);
            }
        }

        private void disconnectFromEditor() {
            if (MPConfig.DEBUG) {
                Log.d(LOGTAG, "disconnecting from editor");
            }

            if (mEditorConnection == null || !mEditorConnection.isValid()) {
                if (MPConfig.DEBUG) {
                    Log.d(LOGTAG, "Editor is already disconnected.");
                }
            }

            mEditorConnection.disconnect();
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
                    Log.e(LOGTAG, "Could not close output writer to editor", e);
                }
            }
        }

        private void sendDeviceInfo() {
            final OutputStream out = mEditorConnection.getBufferedOutputStream();
            final OutputStreamWriter writer = new OutputStreamWriter(out);

            try {
                writer.write("{\"type\": \"device_info_response\",");
                writer.write("\"payload\": {");
                writer.write("\"device_type\": \"Android\",");
                writer.write("\"device_name\": \"" + Build.BRAND + "/" + Build.MODEL + "\",");
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
            try {
                final JSONObject payload = message.getJSONObject("payload");
                if (payload.has("config")) {
                    mSnapshot = mProtocol.readSnapshotConfig(payload);
                }
            } catch (JSONException e) {
                Log.e(LOGTAG, "Payload with snapshot config required with snapshot request", e);
                sendError("Payload with snapshot config required with snapshot request");
                return;
            } catch (EditProtocol.BadInstructionsException e) {
                Log.e(LOGTAG, "Editor sent malformed message with snapshot request", e);
                sendError(e.getMessage());
                return;
            }
            // ELSE config is valid:
            if (null == mSnapshot) {
                sendError("No snapshot configuration was sent.");
                Log.w(LOGTAG, "Mixpanel editor is misconfigured, sent a snapshot request without configuration.");
            }

            final OutputStream out = mEditorConnection.getBufferedOutputStream();
            final OutputStreamWriter writer = new OutputStreamWriter(out);

            try {
                writer.write("{\"type\": \"snapshot_response\",");
                writer.write("\"payload\": {");
                writer.write("\"activities\": [");
                writer.flush();
                mSnapshot.snapshots(mLiveActivities, out);
                writer.write("]"); // activities
                writer.write("}"); // payload
                writer.write("}");
            } catch (IOException e) {
                Log.e(LOGTAG, "Can't write snapshot request to server", e);
            } finally {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.e(LOGTAG, "Can't close writer.", e);
                }
            }
        }

        private void handleEditorChangeReceived(JSONObject change) {
            try {
                loadChange(mEditorChanges, change);
                applyAllChangesOnUiThread();
            } catch (JSONException e) {
                Log.e(LOGTAG, "Bad change request received", e);
            }
        }

        private void handleEventBindingsReceived(JSONArray eventBindings) {
            final SharedPreferences preferences = getSharedPreferences();
            final SharedPreferences.Editor editor = preferences.edit();
            editor.putString(SHARED_PREF_BINDINGS_KEY, eventBindings.toString());
            editor.apply();
            initializeChanges();
            applyAllChangesOnUiThread();
        }

        private void handleEditorBindingsReceived(JSONObject message) {
            final JSONArray eventBindings;
            try {
                final JSONObject payload = message.getJSONObject("payload");
                eventBindings = payload.getJSONArray("events");
            } catch (JSONException e) {
                Log.e(LOGTAG, "Bad event bindings received", e);
                return;
            }

            int eventCount = eventBindings.length();
            for (int i = 0; i < eventCount; i++) {
                try {
                    final JSONObject event = eventBindings.getJSONObject(i);
                    loadEventBinding(event, mEditorEventBindings);
                } catch (JSONException e) {
                    Log.e(LOGTAG, "Bad event binding received from editor in " + eventBindings.toString(), e);
                }
            }
        }

        private void loadChange(Map <String, List<JSONObject>> changes, JSONObject newChange)
                throws JSONException {
            final String targetActivity = newChange.optString("target", null);
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

        private void loadEventBinding(JSONObject newBinding, Map<String, List<JSONObject>> bindings)
                throws JSONException {
            final String targetActivity = newBinding.optString("target_activity", null);

            synchronized (bindings) {
                final List<JSONObject> bindingList;
                if (bindings.containsKey(targetActivity)) {
                    bindingList = bindings.get(targetActivity);
                } else {
                    bindingList = new ArrayList<JSONObject>();
                    bindings.put(targetActivity, bindingList);
                }
                bindingList.add(newBinding);
            }
        }

        private SharedPreferences getSharedPreferences() {
            final String sharedPrefsName = SHARED_PREF_EDITS_FILE + mToken;
            return mContext.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);
        }

        private EditorConnection mEditorConnection;
        private ViewSnapshot mSnapshot;
        private final Context mContext;
        private final String mToken;
    }

    private class Editor implements EditorConnection.Editor {

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
        public void bindEvents(JSONObject message) {
            Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_HANDLE_EDITOR_BINDINGS_RECEIVED);
            msg.obj = message;
            mMessageThreadHandler.sendMessage(msg);
        }

        @Override
        public void sendDeviceInfo() {
            Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_SEND_DEVICE_INFO);
            mMessageThreadHandler.sendMessage(msg);
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

    // Map from canonical activity class name to description of changes
    // Accessed from Multiple Threads, must be synchronized

    // When A/B Test changes are made available from decide, they'll live in mPersistentChanges
    private final Map<String, List<JSONObject>> mPersistentChanges;
    private final Map<String, List<JSONObject>> mEditorChanges;
    private final Map<String, List<JSONObject>> mPersistentEventBindings;
    private final Map<String, List<JSONObject>> mEditorEventBindings;

    // mLiveActivites is accessed across multiple threads, and must be synchronized.
    private final Set<Activity> mLiveActivities = new HashSet<Activity>();

    private final SSLSocketFactory mSSLSocketFactory;
    private final EditProtocol mProtocol;
    private final Tweaks mTweaks = new Tweaks();
    private final Handler mUiThreadHandler;
    private final ViewCrawlerHandler mMessageThreadHandler;
    private final MixpanelAPI mMixpanel;

    private static final String SHARED_PREF_EDITS_FILE = "mixpanel.viewcrawler.changes";
    private static final String SHARED_PREF_CHANGES_KEY = "mixpanel.viewcrawler.changes";
    private static final String SHARED_PREF_BINDINGS_KEY = "mixpanel.viewcrawler.bindings";

    private static final int MESSAGE_INITIALIZE_CHANGES = 0;
    private static final int MESSAGE_CONNECT_TO_EDITOR = 1;
    private static final int MESSAGE_SEND_STATE_FOR_EDITING = 2;
    private static final int MESSAGE_HANDLE_EDITOR_CHANGES_RECEIVED = 3;
    private static final int MESSAGE_SEND_DEVICE_INFO = 4;
    private static final int MESSAGE_EVENT_BINDINGS_RECEIVED = 6;
    private static final int MESSAGE_DISCONNECT_FROM_EDITOR = 7;
    private static final int MESSAGE_HANDLE_EDITOR_BINDINGS_RECEIVED = 8;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.ViewCrawler";
}
