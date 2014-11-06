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
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.mixpanel.android.mpmetrics.MPConfig;
import com.mixpanel.android.mpmetrics.MixpanelAPI;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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
public class ViewCrawler implements ViewVisitor.OnVisitedListener, UpdatesFromMixpanel, TrackingDebug {

    public ViewCrawler(Context context, String token, MixpanelAPI mixpanel) {
        mPersistentChanges = new ArrayList<Pair<String, JSONObject>>();
        mEditorChanges = new ArrayList<Pair<String, JSONObject>>();
        mPersistentEventBindings = new ArrayList<Pair<String, JSONObject>>();
        mEditorEventBindings = new ArrayList<Pair<String, JSONObject>>();
        mProtocol = new EditProtocol(context);
        mEditState = new EditState();

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
    public void OnVisited(View v, String eventName) {
        final JSONObject properties = eventPropertiesFromView(v);
        mMixpanel.track(eventName, properties);
    }

    @Override
    public void reportTrack(String eventName) {
        final Message m = mMessageThreadHandler.obtainMessage();
        m.what = MESSAGE_SEND_EVENT_TRACKED;
        m.obj = eventName;

        mMessageThreadHandler.sendMessage(m);
    }


    private JSONObject eventPropertiesFromView(View v) {
        try {
            final JSONObject ret = new JSONObject();
            final String text = textPropertyFromView(v);
            ret.put("mp_text", text);

            return ret;
        } catch (JSONException e) {
            Log.e(LOGTAG, "Can't format properties from view due to JSON issue", e);
            return null;
        }
    }

    private String textPropertyFromView(View v) {
        String ret = null;

        if (v instanceof TextView) {
            final TextView textV = (TextView) v;
            final CharSequence retSequence = textV.getText();
            if (null != retSequence) {
                ret = retSequence.toString();
            }
        } else if (v instanceof ViewGroup) {
            final StringBuilder builder = new StringBuilder();
            final ViewGroup vGroup = (ViewGroup) v;
            final int childCount = vGroup.getChildCount();
            boolean textSeen = false;
            for (int i = 0; i < childCount && builder.length() < MAX_PROPERTY_LENGTH; i++) {
                final View child = vGroup.getChildAt(i);
                final String childText = textPropertyFromView(child);
                if (null != childText && childText.length() > 0) {
                    if (textSeen) {
                        builder.append(", ");
                    }
                    builder.append(childText);
                    textSeen = true;
                }
            }

            if (builder.length() > MAX_PROPERTY_LENGTH) {
                ret = builder.substring(0, MAX_PROPERTY_LENGTH);
            } else if (textSeen) {
                ret = builder.toString();
            }
        }

        return ret;
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

            mEditState.applyAllChangesOnUiThread(mLiveActivities);
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
                            final String targetActivity = change.optString("target", null);
                            final JSONObject change1 = change.getJSONObject("change");
                            mPersistentChanges.add(new Pair<String, JSONObject>(targetActivity, change1));
                        }
                    }
                }

                if (null != storedBindings) {
                    final JSONArray bindings = new JSONArray(storedBindings);

                    synchronized(mPersistentEventBindings) {
                        mPersistentEventBindings.clear();
                        for (int i = 0; i < bindings.length(); i++) {
                            final JSONObject event = bindings.getJSONObject(i);
                            final String targetActivity = event.optString("target_activity", null);
                            mPersistentEventBindings.add(new Pair<String, JSONObject>(targetActivity, event));
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

            updateEditState();
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
                writer.write("{");
                writer.write("\"type\": \"snapshot_response\",");
                writer.write("\"payload\": {");
                {
                    writer.write("\"activities\":");
                    writer.flush();
                    mSnapshot.snapshots(mLiveActivities, out);
                }
                writer.write("}"); // } payload
                writer.write("}"); // } whole message
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
            } catch (IOException e) {
                Log.e(LOGTAG, "Can't write track_message to server", e);
            } finally {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.e(LOGTAG, "Can't close writer.", e);
                }
            }
        }

        private void handleEditorChangeReceived(JSONObject changeMessage) {
            try {
                final String targetActivity = changeMessage.optString("target", null);
                final JSONObject change = changeMessage.getJSONObject("change");
                synchronized (mEditorChanges) {
                    mEditorChanges.add(new Pair<String, JSONObject>(targetActivity, change));
                }
                updateEditState();
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
            synchronized (mEditorEventBindings) {
                for (int i = 0; i < eventCount; i++) {
                    try {
                        final JSONObject event = eventBindings.getJSONObject(i);
                        final String targetActivity = event.optString("target_activity", null);
                        mEditorEventBindings.add(new Pair<String, JSONObject>(targetActivity, event));
                    } catch (JSONException e) {
                        Log.e(LOGTAG, "Bad event binding received from editor in " + eventBindings.toString(), e);
                    }
                }
            }

            updateEditState();
        }

        private void updateEditState() {
            final List<Pair<String, ViewVisitor>> newEdits = new ArrayList<Pair<String, ViewVisitor>>();

            synchronized (mPersistentChanges) {
                final int size = mPersistentChanges.size();
                for (int i = 0; i < size; i++) {
                    final Pair<String, JSONObject> changeInfo = mPersistentChanges.get(i);
                    try {
                        final ViewVisitor visitor = mProtocol.readEdit(changeInfo.second);
                        newEdits.add(new Pair(changeInfo.first, visitor));
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
                        final ViewVisitor visitor = mProtocol.readEventBinding(changeInfo.second, ViewCrawler.this);
                        newEdits.add(new Pair<String, ViewVisitor>(changeInfo.first, visitor));
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
                        final ViewVisitor visitor = mProtocol.readEventBinding(changeInfo.second, ViewCrawler.this);
                        newEdits.add(new Pair<String, ViewVisitor>(changeInfo.first, visitor));
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
            mEditState.applyAllChangesOnUiThread(mLiveActivities);
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

    // Lists of edits. All accesses must be synchronized
    private final List<Pair<String,JSONObject>> mPersistentChanges;
    private final List<Pair<String,JSONObject>> mEditorChanges;
    private final List<Pair<String,JSONObject>> mPersistentEventBindings;
    private final List<Pair<String,JSONObject>> mEditorEventBindings;

    // mLiveActivites is accessed across multiple threads, and must be synchronized.
    private final Set<Activity> mLiveActivities = new HashSet<Activity>();

    private final SSLSocketFactory mSSLSocketFactory;
    private final EditProtocol mProtocol;
    private final EditState mEditState;
    private final Tweaks mTweaks = new Tweaks();
    private final ViewCrawlerHandler mMessageThreadHandler;
    private final MixpanelAPI mMixpanel;

    private static final String SHARED_PREF_EDITS_FILE = "mixpanel.viewcrawler.changes";
    private static final String SHARED_PREF_CHANGES_KEY = "mixpanel.viewcrawler.changes";
    private static final String SHARED_PREF_BINDINGS_KEY = "mixpanel.viewcrawler.bindings";

    private static final int MAX_PROPERTY_LENGTH = 128;

    private static final int MESSAGE_INITIALIZE_CHANGES = 0;
    private static final int MESSAGE_CONNECT_TO_EDITOR = 1;
    private static final int MESSAGE_SEND_STATE_FOR_EDITING = 2;
    private static final int MESSAGE_HANDLE_EDITOR_CHANGES_RECEIVED = 3;
    private static final int MESSAGE_SEND_DEVICE_INFO = 4;
    private static final int MESSAGE_EVENT_BINDINGS_RECEIVED = 6;
    private static final int MESSAGE_DISCONNECT_FROM_EDITOR = 7;
    private static final int MESSAGE_HANDLE_EDITOR_BINDINGS_RECEIVED = 8;
    private static final int MESSAGE_SEND_EVENT_TRACKED = 9;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.ViewCrawler";
}
