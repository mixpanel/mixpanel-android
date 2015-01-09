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
import android.util.JsonWriter;
import android.util.Log;
import android.util.Pair;

import com.mixpanel.android.mpmetrics.MPConfig;
import com.mixpanel.android.mpmetrics.MixpanelAPI;
import com.mixpanel.android.mpmetrics.ResourceIds;
import com.mixpanel.android.mpmetrics.ResourceReader;
import com.mixpanel.android.mpmetrics.Tweaks;
import com.mixpanel.android.util.JSONUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

/**
 * This class is for internal use by the Mixpanel API, and should
 * not be called directly by your code.
 */
@TargetApi(MPConfig.UI_FEATURES_MIN_API)
public class ViewCrawler implements UpdatesFromMixpanel, TrackingDebug {

    public ViewCrawler(Context context, String token, MixpanelAPI mixpanel) {
        mConfig = MPConfig.getInstance(context);
        mPersistentChanges = new ArrayList<Pair<String, JSONObject>>();
        mEditorChanges = new ArrayList<Pair<String, JSONObject>>();
        mPersistentEventBindings = new ArrayList<Pair<String, JSONObject>>();
        mEditorEventBindings = new ArrayList<Pair<String, JSONObject>>();

        String resourcePackage = mConfig.getResourcePackageName();
        if (null == resourcePackage) {
            resourcePackage = context.getPackageName();
        }

        final ResourceIds resourceIds = new ResourceReader.Ids(resourcePackage, context);
        mProtocol = new EditProtocol(resourceIds);
        mEditState = new EditState();
        mTweaks = new Tweaks();
        mDeviceInfo = mixpanel.getDeviceInfo();

        final Application app = (Application) context.getApplicationContext();
        app.registerActivityLifecycleCallbacks(new LifecycleCallbacks());

        final HandlerThread thread = new HandlerThread(ViewCrawler.class.getCanonicalName());
        thread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mMessageThreadHandler = new ViewCrawlerHandler(context, token, thread.getLooper());
        mMessageThreadHandler.sendMessage(mMessageThreadHandler.obtainMessage(MESSAGE_INITIALIZE_CHANGES));

        mTracker = new DynamicEventTracker(mixpanel, mMessageThreadHandler);

        // We build our own, private SSL context here to prevent things from going
        // crazy if client libs are using older versions of OkHTTP, or otherwise doing crazy junk
        // with the default SSL context: https://github.com/square/okhttp/issues/184
        SSLSocketFactory foundSSLFactory;
        try {
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            foundSSLFactory = sslContext.getSocketFactory();
        } catch (final GeneralSecurityException e) {
            Log.i(LOGTAG, "System has no SSL support. Built-in events editor will not be available", e);
            foundSSLFactory = null;
        }
        mSSLSocketFactory = foundSSLFactory;
    }

    @Override
    public Tweaks getTweaks() {
        return mTweaks;
    }

    @Override
    public void setEventBindings(JSONArray bindings) {
        final Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_EVENT_BINDINGS_RECEIVED);
        msg.obj = bindings;
        mMessageThreadHandler.sendMessage(msg);
    }

    @Override
    public void reportTrack(String eventName) {
        final Message m = mMessageThreadHandler.obtainMessage();
        m.what = MESSAGE_SEND_EVENT_TRACKED;
        m.obj = eventName;

        mMessageThreadHandler.sendMessage(m);
    }

    private class EmulatorConnector implements Runnable {
        public EmulatorConnector() {
            mStopped = true;
        }

        @Override
        public void run() {
            if (! mStopped) {
                final Message message = mMessageThreadHandler.obtainMessage(MESSAGE_CONNECT_TO_EDITOR);
                mMessageThreadHandler.sendMessage(message);
            }

            mMessageThreadHandler.postDelayed(this, EMULATOR_CONNECT_ATTEMPT_INTERVAL_MILLIS);
        }

        public void start() {
            mStopped = false;
            mMessageThreadHandler.post(this);
        }

        public void stop() {
            mStopped = true;
            mMessageThreadHandler.removeCallbacks(this);
        }

        private volatile boolean mStopped;
    }

    private class LifecycleCallbacks implements Application.ActivityLifecycleCallbacks, FlipGesture.OnFlipGestureListener {

        public LifecycleCallbacks() {
            mFlipGesture = new FlipGesture(this);
            mEmulatorConnector = new EmulatorConnector();
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
        }

        @Override
        public void onActivityResumed(Activity activity) {
            installConnectionSensor(activity);
            mEditState.add(activity);
        }

        @Override
        public void onActivityPaused(Activity activity) {
            mEditState.remove(activity);
            if (mEditState.isEmpty()) {
                uninstallConnectionSensor(activity);
            }
        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }

        private void installConnectionSensor(final Activity activity) {
            if (isInEmulator() && !mConfig.getDisableEmulatorBindingUI()) {
                mEmulatorConnector.start();
            } else if (!mConfig.getDisableGestureBindingUI()) {
                final SensorManager sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
                final Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                sensorManager.registerListener(mFlipGesture, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }

        private void uninstallConnectionSensor(final Activity activity) {
            if (isInEmulator() && !mConfig.getDisableEmulatorBindingUI()) {
                mEmulatorConnector.stop();
            } else if (!mConfig.getDisableGestureBindingUI()) {
                final SensorManager sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
                sensorManager.unregisterListener(mFlipGesture);
            }
        }

        private boolean isInEmulator() {
            if (!Build.HARDWARE.equals("goldfish")) {
                return false;
            }

            if (!Build.BRAND.startsWith("generic")) {
                return false;
            }

            if (!Build.DEVICE.startsWith("generic")) {
                return false;
            }

            if (!Build.PRODUCT.contains("sdk")) {
                return false;
            }

            if (!Build.MODEL.toLowerCase().contains("sdk")) {
                return false;
            }

            return true;
        }

        private final FlipGesture mFlipGesture;
        private final EmulatorConnector mEmulatorConnector;
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
                case MESSAGE_SEND_DEVICE_INFO:
                    sendDeviceInfo();
                    break;
                case MESSAGE_SEND_STATE_FOR_EDITING:
                    sendSnapshot((JSONObject) msg.obj);
                    break;
                case MESSAGE_SEND_EVENT_TRACKED:
                    sendReportTrackToEditor((String) msg.obj);
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
                case MESSAGE_HANDLE_EDITOR_CLOSED:
                    handleEditorClosed();
                    break;
            }
        }

        /**
         * Load stored changes from persistent storage and apply them to the application.
         */
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
                            final JSONObject changeMessage = changes.getJSONObject(i);
                            final String targetActivity = JSONUtils.optionalStringKey(changeMessage, "target");
                            final JSONObject change = changeMessage.getJSONObject("change");
                            mPersistentChanges.add(new Pair<String, JSONObject>(targetActivity, change));
                        }
                    }
                }

                if (null != storedBindings) {
                    final JSONArray bindings = new JSONArray(storedBindings);

                    synchronized(mPersistentEventBindings) {
                        mPersistentEventBindings.clear();
                        for (int i = 0; i < bindings.length(); i++) {
                            final JSONObject event = bindings.getJSONObject(i);
                            final String targetActivity = JSONUtils.optionalStringKey(event, "target_activity");
                            mPersistentEventBindings.add(new Pair<String, JSONObject>(targetActivity, event));
                        }
                    }
                }
            } catch (final JSONException e) {
                Log.i(LOGTAG, "JSON error when initializing saved changes, clearing persistent memory", e);
                final SharedPreferences.Editor editor = preferences.edit();
                editor.remove(SHARED_PREF_CHANGES_KEY);
                editor.remove(SHARED_PREF_BINDINGS_KEY);
                editor.apply();
            }

            updateEditState();
        }

        /**
         * Try to connect to the remote interactive editor, if a connection does not already exist.
         */
        private void connectToEditor() {
            if (MPConfig.DEBUG) {
                Log.v(LOGTAG, "connecting to editor");
            }

            if (mEditorConnection != null && mEditorConnection.isValid()) {
                if (MPConfig.DEBUG) {
                    Log.v(LOGTAG, "There is already a valid connection to an events editor.");
                }
                return;
            }

            if (null == mSSLSocketFactory) {
                if (MPConfig.DEBUG) {
                    Log.v(LOGTAG, "SSL is not available on this device, no connection will be attempted to the events editor.");
                }
                return;
            }

            final String url = MPConfig.getInstance(mContext).getEditorUrl() + mToken;
            try {
                final Socket sslSocket = mSSLSocketFactory.createSocket();
                mEditorConnection = new EditorConnection(new URI(url), new Editor(), sslSocket);
            } catch (final URISyntaxException e) {
                Log.e(LOGTAG, "Error parsing URI " + url + " for editor websocket", e);
            } catch (final EditorConnection.EditorConnectionException e) {
                Log.e(LOGTAG, "Error connecting to URI " + url, e);
            } catch (final IOException e) {
                Log.i(LOGTAG, "Can't create SSL Socket to connect to editor service", e);
            }
        }

        /**
         * Send a string error message to the connected web UI.
         */
        private void sendError(String errorMessage) {
            final JSONObject errorObject = new JSONObject();
            try {
                errorObject.put("error_message", errorMessage);
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Apparently impossible JSONException", e);
            }

            final OutputStreamWriter writer = new OutputStreamWriter(mEditorConnection.getBufferedOutputStream());
            try {
                writer.write("{\"type\": \"error\", ");
                writer.write("\"payload\": ");
                writer.write(errorObject.toString());
                writer.write("}");
            } catch (final IOException e) {
                Log.e(LOGTAG, "Can't write error message to editor", e);
            } finally {
                try {
                    writer.close();
                } catch (final IOException e) {
                    Log.e(LOGTAG, "Could not close output writer to editor", e);
                }
            }
        }

        /**
         * Report on device info to the connected web UI.
         */
        private void sendDeviceInfo() {
            final OutputStream out = mEditorConnection.getBufferedOutputStream();
            final OutputStreamWriter writer = new OutputStreamWriter(out);

            try {
                writer.write("{\"type\": \"device_info_response\",");
                writer.write("\"payload\": {");
                writer.write("\"device_type\": \"Android\",");
                writer.write("\"device_name\":");
                writer.write(JSONObject.quote(Build.BRAND + "/" + Build.MODEL));
                writer.write(",");
                writer.write("\"tweaks\":");
                writer.write(new JSONObject(mTweaks.getAll()).toString());
                for (final Map.Entry<String, String> entry : mDeviceInfo.entrySet()) {
                    writer.write(",");
                    writer.write(JSONObject.quote(entry.getKey()));
                    writer.write(":");
                    writer.write(JSONObject.quote(entry.getValue()));
                }
                writer.write("}"); // payload
                writer.write("}");
            } catch (final IOException e) {
                Log.e(LOGTAG, "Can't write device_info to server", e);
            } finally {
                try {
                    writer.close();
                } catch (final IOException e) {
                    Log.e(LOGTAG, "Can't close websocket writer", e);
                }
            }
        }

        /**
         * Send a snapshot response, with crawled views and screenshot image, to the connected web UI.
         */
        private void sendSnapshot(JSONObject message) {
            final long startSnapshot = System.currentTimeMillis();
            try {
                final JSONObject payload = message.getJSONObject("payload");
                if (payload.has("config")) {
                    mSnapshot = mProtocol.readSnapshotConfig(payload);
                }
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Payload with snapshot config required with snapshot request", e);
                sendError("Payload with snapshot config required with snapshot request");
                return;
            } catch (final EditProtocol.BadInstructionsException e) {
                Log.e(LOGTAG, "Editor sent malformed message with snapshot request", e);
                sendError(e.getMessage());
                return;
            }

            if (null == mSnapshot) {
                sendError("No snapshot configuration (or a malformed snapshot configuration) was sent.");
                Log.w(LOGTAG, "Mixpanel editor is misconfigured, sent a snapshot request without a valid configuration.");
                return;
            }
            // ELSE config is valid:

            final OutputStream out = mEditorConnection.getBufferedOutputStream();
            final OutputStreamWriter writer = new OutputStreamWriter(out);

            try {
                writer.write("{");
                writer.write("\"type\": \"snapshot_response\",");
                writer.write("\"payload\": {");
                {
                    writer.write("\"activities\":");
                    writer.flush();
                    mSnapshot.snapshots(mEditState, out);
                }

                final long snapshotTime = System.currentTimeMillis() - startSnapshot;
                writer.write(",\"snapshot_time_millis\": ");
                writer.write(Long.toString(snapshotTime));

                writer.write("}"); // } payload
                writer.write("}"); // } whole message
            } catch (final IOException e) {
                Log.e(LOGTAG, "Can't write snapshot request to server", e);
            } finally {
                try {
                    writer.close();
                } catch (final IOException e) {
                    Log.e(LOGTAG, "Can't close writer.", e);
                }
            }
        }

        /**
         * Report that a track has occurred to the connected web UI.
         */
        private void sendReportTrackToEditor(String eventName) {
            if (mEditorConnection == null || !mEditorConnection.isValid()) {
                return;
            }

            final OutputStream out = mEditorConnection.getBufferedOutputStream();
            final OutputStreamWriter writer = new OutputStreamWriter(out);
            final JsonWriter j = new JsonWriter(writer);

            try {
                j.beginObject();
                j.name("type").value("track_message");
                j.name("payload");
                {
                    j.beginObject();
                    j.name("event_name").value(eventName);
                    j.endObject();
                }
                j.endObject();
                j.flush();
            } catch (final IOException e) {
                Log.e(LOGTAG, "Can't write track_message to server", e);
            } finally {
                try {
                    j.close();
                } catch (final IOException e) {
                    Log.e(LOGTAG, "Can't close writer.", e);
                }
            }
        }

        /**
         * Accept and apply a change from the connected UI.
         */
        private void handleEditorChangeReceived(JSONObject changeMessage) {
            try {
                final String targetActivity = JSONUtils.optionalStringKey(changeMessage, "target");
                final JSONObject change = changeMessage.getJSONObject("change");
                synchronized (mEditorChanges) {
                    mEditorChanges.add(new Pair<String, JSONObject>(targetActivity, change));
                }
                updateEditState();
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Bad change request received", e);
            }
        }

        /**
         * Accept and apply a persistent event binding from a non-interactive source.
         */
        private void handleEventBindingsReceived(JSONArray eventBindings) {
            final SharedPreferences preferences = getSharedPreferences();
            final SharedPreferences.Editor editor = preferences.edit();
            editor.putString(SHARED_PREF_BINDINGS_KEY, eventBindings.toString());
            editor.apply();
            initializeChanges();
        }

        /**
         * Accept and apply a temporary event binding from the connected UI.
         */
        private void handleEditorBindingsReceived(JSONObject message) {
            final JSONArray eventBindings;
            try {
                final JSONObject payload = message.getJSONObject("payload");
                eventBindings = payload.getJSONArray("events");
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Bad event bindings received", e);
                return;
            }

            final int eventCount = eventBindings.length();
            synchronized (mEditorEventBindings) {
                mEditorEventBindings.clear();
                for (int i = 0; i < eventCount; i++) {
                    try {
                        final JSONObject event = eventBindings.getJSONObject(i);
                        final String targetActivity = JSONUtils.optionalStringKey(event, "target_activity");
                        mEditorEventBindings.add(new Pair<String, JSONObject>(targetActivity, event));
                    } catch (final JSONException e) {
                        Log.e(LOGTAG, "Bad event binding received from editor in " + eventBindings.toString(), e);
                    }
                }
            }

            updateEditState();
        }

        /**
         * Clear state associated with the editor now that the editor is gone.
         */
        private void handleEditorClosed() {
            synchronized (mEditorChanges) {
                mEditorChanges.clear();
            }

            synchronized (mEditorEventBindings) {
                mEditorEventBindings.clear();
            }

            // Free (or make available) snapshot memory
            mSnapshot = null;

            updateEditState();
        }

        /**
         * Reads our JSON-stored edits from memory and submits them to our EditState. Overwrites
         * any existing edits at the time that it is run.
         *
         * updateEditState should be called any time we load new edits from disk or
         * receive new edits from the interactive UI editor. Changes and event bindings
         * from our persistent storage and temporary changes received from interactive editing
         * will all be submitted to our EditState
         */
        private void updateEditState() {
            final List<Pair<String, ViewVisitor>> newEdits = new ArrayList<Pair<String, ViewVisitor>>();

            synchronized (mPersistentChanges) {
                final int size = mPersistentChanges.size();
                for (int i = 0; i < size; i++) {
                    final Pair<String, JSONObject> changeInfo = mPersistentChanges.get(i);
                    try {
                        final ViewVisitor visitor = mProtocol.readEdit(changeInfo.second);
                        newEdits.add(new Pair<String, ViewVisitor>(changeInfo.first, visitor));
                    } catch (final EditProtocol.InapplicableInstructionsException e) {
                        Log.i(LOGTAG, e.getMessage());
                    } catch (final EditProtocol.BadInstructionsException e) {
                        Log.e(LOGTAG, "Bad persistent change request cannot be applied.", e);
                    }
                }
            }

            synchronized (mEditorChanges) {
                final int size = mEditorChanges.size();
                for (int i = 0; i < size; i++) {
                    final Pair<String, JSONObject> changeInfo = mEditorChanges.get(i);
                    try {
                        final ViewVisitor visitor = mProtocol.readEdit(changeInfo.second);
                        newEdits.add(new Pair<String, ViewVisitor>(changeInfo.first, visitor));
                    } catch (final EditProtocol.InapplicableInstructionsException e) {
                        Log.i(LOGTAG, e.getMessage());
                    } catch (final EditProtocol.BadInstructionsException e) {
                        Log.e(LOGTAG, "Bad editor change request cannot be applied.", e);
                    }
                }
            }

            synchronized (mPersistentEventBindings) {
                final int size = mPersistentEventBindings.size();
                for (int i = 0; i < size; i++) {
                    final Pair<String, JSONObject> changeInfo = mPersistentEventBindings.get(i);
                    try {
                        final ViewVisitor visitor = mProtocol.readEventBinding(changeInfo.second, mTracker);
                        newEdits.add(new Pair<String, ViewVisitor>(changeInfo.first, visitor));
                    } catch (final EditProtocol.InapplicableInstructionsException e) {
                        Log.i(LOGTAG, e.getMessage());
                    } catch (final EditProtocol.BadInstructionsException e) {
                        Log.e(LOGTAG, "Bad persistent event binding cannot be applied.", e);
                    }
                }
            }

            synchronized (mEditorEventBindings) {
                final int size = mEditorEventBindings.size();
                for (int i = 0; i < size; i++) {
                    final Pair<String, JSONObject> changeInfo = mEditorEventBindings.get(i);
                    try {
                        final ViewVisitor visitor = mProtocol.readEventBinding(changeInfo.second, mTracker);
                        newEdits.add(new Pair<String, ViewVisitor>(changeInfo.first, visitor));
                    } catch (final EditProtocol.InapplicableInstructionsException e) {
                        Log.i(LOGTAG, e.getMessage());
                    } catch (final EditProtocol.BadInstructionsException e) {
                        Log.e(LOGTAG, "Bad editor event binding cannot be applied.", e);
                    }
                }
            }

            final Map<String, List<ViewVisitor>> editMap = new HashMap<String, List<ViewVisitor>>();
            final int totalEdits = newEdits.size();
            for (int i = 0; i < totalEdits; i++) {
                final Pair<String, ViewVisitor> next = newEdits.get(i);
                final List<ViewVisitor> mapElement;
                if (editMap.containsKey(next.first)) {
                    mapElement = editMap.get(next.first);
                } else {
                    mapElement = new ArrayList<ViewVisitor>();
                    editMap.put(next.first, mapElement);
                }
                mapElement.add(next.second);
            }

            mEditState.setEdits(editMap);
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
            final Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_SEND_STATE_FOR_EDITING);
            msg.obj = message;
            mMessageThreadHandler.sendMessage(msg);
        }

        @Override
        public void performEdit(JSONObject message) {
            final Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_HANDLE_EDITOR_CHANGES_RECEIVED);
            msg.obj = message;
            mMessageThreadHandler.sendMessage(msg);
        }

        @Override
        public void bindEvents(JSONObject message) {
            final Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_HANDLE_EDITOR_BINDINGS_RECEIVED);
            msg.obj = message;
            mMessageThreadHandler.sendMessage(msg);
        }

        @Override
        public void sendDeviceInfo() {
            final Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_SEND_DEVICE_INFO);
            mMessageThreadHandler.sendMessage(msg);
        }

        @Override
        public void cleanup() {
            final Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_HANDLE_EDITOR_CLOSED);
            mMessageThreadHandler.sendMessage(msg);
        }
    }

    // Lists of edits. All accesses must be synchronized
    private final List<Pair<String,JSONObject>> mPersistentChanges;
    private final List<Pair<String,JSONObject>> mEditorChanges;
    private final List<Pair<String,JSONObject>> mPersistentEventBindings;
    private final List<Pair<String,JSONObject>> mEditorEventBindings;

    private final MPConfig mConfig;
    private final DynamicEventTracker mTracker;
    private final SSLSocketFactory mSSLSocketFactory;
    private final EditProtocol mProtocol;
    private final EditState mEditState;
    private final Tweaks mTweaks;
    private final Map<String, String> mDeviceInfo;
    private final ViewCrawlerHandler mMessageThreadHandler;

    private static final String SHARED_PREF_EDITS_FILE = "mixpanel.viewcrawler.changes";
    private static final String SHARED_PREF_CHANGES_KEY = "mixpanel.viewcrawler.changes";
    private static final String SHARED_PREF_BINDINGS_KEY = "mixpanel.viewcrawler.bindings";

    private static final int MESSAGE_INITIALIZE_CHANGES = 0;
    private static final int MESSAGE_CONNECT_TO_EDITOR = 1;
    private static final int MESSAGE_SEND_STATE_FOR_EDITING = 2;
    private static final int MESSAGE_HANDLE_EDITOR_CHANGES_RECEIVED = 3;
    private static final int MESSAGE_SEND_DEVICE_INFO = 4;
    private static final int MESSAGE_EVENT_BINDINGS_RECEIVED = 6;
    private static final int MESSAGE_HANDLE_EDITOR_BINDINGS_RECEIVED = 8;
    private static final int MESSAGE_SEND_EVENT_TRACKED = 9;
    private static final int MESSAGE_HANDLE_EDITOR_CLOSED = 10;

    private static final int EMULATOR_CONNECT_ATTEMPT_INTERVAL_MILLIS = 1000 * 30;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.ViewCrawler";
}
