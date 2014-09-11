package com.mixpanel.android.viewcrawler;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
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
public class ViewCrawler implements ViewVisitor.OnVisitedListener {

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
                    final ViewVisitor inst = mProtocol.readEdit(j, this);
                    final View rootView = activity.getWindow().getDecorView().getRootView();
                    final EditBinding binding = new EditBinding(rootView, inst);
                    binding.performEdit();
                } catch (EditProtocol.BadInstructionsException e) {
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
                    initializeChanges();
                    break;
                case MESSAGE_CONNECT_TO_EDITOR:
                    connectToEditor();
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
                case MESSAGE_HANDLE_PERSISTENT_CHANGES_RECEIVED:
                    handlePersistentChangesReceived((JSONObject) msg.obj);
                    break;
            }
        }

        private void initializeChanges() {
            final SharedPreferences preferences = getSharedPreferences();
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
                    Log.i(LOGTAG, "JSON error when initializing saved changes, clearing persistent memory", e);
                    preferences.edit().remove(SHARED_PREF_CHANGES_KEY).apply();
                    return;
                }
            }
        }

        private void connectToEditor() {
            if (MPConfig.DEBUG) {
                Log.d(LOGTAG, "connectToEditor called");
            }

            if (mEditorConnection != null && mEditorConnection.isValid()) {
                if (MPConfig.DEBUG) {
                    Log.d(LOGTAG, "There is already a valid connection to an editor.");
                }
                return;
            }

            final String url = MPConfig.getInstance(mContext).getEditorUrl() + mToken;
            try {
                final SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                final Socket sslSocket = socketFactory.createSocket();
                mEditorConnection = new EditorConnection(new URI(url), new Editor(), sslSocket);
            } catch (URISyntaxException e) {
                Log.e(LOGTAG, "Error parsing URI " + url + " for editor websocket", e);
            } catch (EditorConnection.EditorConnectionException e) {
                Log.e(LOGTAG, "Error connecting to URI " + url, e);
            } catch (IOException e) {
                Log.i(LOGTAG, "Can't create SSL Socket to connect to editor service", e);
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
            final ViewSnapshot snapshot;
            try {
                final JSONObject payload = message.getJSONObject("payload");
                snapshot = mProtocol.readSnapshotConfig(payload);
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
                writer.write("\"activities\": [");
                writer.flush();

                int viewCount = rootViews.size();
                for (int i = 0; i < viewCount; i++) {
                    if (i > 0) {
                        writer.write(",");
                        writer.flush();
                    }

                    final RootViewInfo info = rootViews.get(i);
                    snapshot.snapshot(info.activityName, info.rootView, out);
                }
                writer.write("]"); // activities
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

        private void handlePersistentChangesReceived(JSONObject message) {
            final JSONArray changes;
            try {
                changes = message.getJSONArray("changes");
            } catch (JSONException e) {
                Log.e(LOGTAG, "Can't read persistent changes from JSONMessage " + message.toString(), e);
                return;
            }

            final SharedPreferences preferences = getSharedPreferences();
            final SharedPreferences.Editor editor = preferences.edit();
            editor.putString(SHARED_PREF_CHANGES_KEY, changes.toString());
            editor.apply();
            initializeChanges();
            applyAllChangesOnUiThread();
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

        private SharedPreferences getSharedPreferences() {
            final String sharedPrefsName = SHARED_PREF_CHANGES_FILE + mToken;
            return mContext.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);
        }

        private EditorConnection mEditorConnection;
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
