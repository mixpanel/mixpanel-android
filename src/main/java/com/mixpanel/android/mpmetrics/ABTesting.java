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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.mixpanel.android.abtesting.SampleConfig;
import com.mixpanel.android.abtesting.Tweaks;
import com.mixpanel.android.abtesting.ViewEdit;
import com.mixpanel.android.abtesting.ViewTraversal;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
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

        if (android.os.Build.VERSION.SDK_INT >= 14) {
            final Application app = (Application) mContext.getApplicationContext();
            app.registerActivityLifecycleCallbacks(this);

            HandlerThread thread = new HandlerThread(ABTesting.class.getCanonicalName());
            thread.start();
            mHandler = new ABHandler(thread.getLooper());
            mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_INITIALIZE_CHANGES));
        }

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
                case MESSAGE_CONNECT_TO_EDITOR:
                    this.connectToEditor();
                    break;
                case MESSAGE_SEND_STATE_FOR_EDITING:
                    this.sendStateForEditing();
                    break;
                case MESSAGE_HANDLE_CHANGES_RECEIVED:
                    try {
                        handleChangesReceived((JSONObject) msg.obj, true, false);
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

            if (mEditorConnection == null || !mEditorConnection.isAlive()) {
                final String url = MPConfig.getInstance(mContext).getABTestingUrl() + mToken;
                try {
                    mEditorConnection = new EditorConnection(new URI(url));

                } catch (URISyntaxException e) {
                    Log.e(LOGTAG, "Error parsing URI " + url + " for editor websocket", e);
                }

                try {
                    boolean connected = mEditorConnection.connectBlocking();
                    if (!connected) {
                        Log.d(LOGTAG, "Can't connect to endpoint " + url);
                        mEditorConnection = null;

                    }
                } catch (InterruptedException e) {
                    mEditorConnection = null;
                    Log.e(LOGTAG, "Editor client was interrupted during connection", e);
                }
            }
        }

        private void sendStateForEditing() {
            Log.v(LOGTAG, "sendStateForEditing");
            final OutputStream out = mEditorConnection.getOutputStream();
            final OutputStreamWriter writer = new OutputStreamWriter(out);
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
                    final ViewTraversal traversal = new ViewTraversal(rootView);
                    boolean firstView = true;
                    while (traversal.hasNext()) {
                        if (firstView) {
                            firstView = false;
                        } else {
                            writer.write(", ");
                        }
                        writer.write(serializeView(traversal.next()).toString());
                    }
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
                mEditorConnection.releaseOutputStream(out);
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
                synchronized(mChanges) {
                    ArrayList<JSONObject> changeList;
                    if (mChanges.containsKey(targetActivity)) {
                        changeList = mChanges.get(targetActivity);
                    } else {
                        changeList = new ArrayList<JSONObject>();
                        mChanges.put(targetActivity, changeList);
                    }
                    changeList.add(change);
                }
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
                writer.write(Base64.encodeToString(out.toByteArray(), Base64.NO_PADDING | Base64.NO_WRAP)); // TODO I think this NO_PADDING is gonna break stuff?
                writer.write("\"");
            } else {
                writer.write("null");
            }

            if (!originalCacheState) {
                rootView.setDrawingCacheEnabled(false);
            }
        }

        private JSONObject serializeView(View view) throws IOException {
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

                // TODO interpret whitelist for methods
                JSONArray methodList = new JSONArray();
                for (Method m : view.getClass().getMethods()) {
                    JSONObject method = new JSONObject();
                    if (m.getName().startsWith("set")) {
                        JSONArray argTypes = new JSONArray();
                        for (Class c : m.getParameterTypes()) {
                            argTypes.put(c.getCanonicalName());
                        }

                        method.put("name", m.getName());
                        method.put("args", argTypes);
                        methodList.put(method);
                    }
                }
                dump.put("methods", methodList);

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
            return dump;
        }

        private class EditorConnection {
            public EditorConnection(URI uri) {
                mClient = new EditorClient(uri);
                mInUse = null;
            }

            public boolean isAlive() {
                return mClient.isAlive();
            }

            public boolean connectBlocking() throws InterruptedException {
                return mClient.connectBlocking();
            }

            public OutputStream getOutputStream() {
                if (null != mInUse) {
                    throw new RuntimeException("Only one websocket output stream should be in use at a time");
                }
                mInUse = new WebSocketOutputStream();
                return mInUse;
            }

            public void releaseOutputStream(OutputStream out) {
                if (out != mInUse) {
                    throw new RuntimeException("Only one output stream should be in use at a time");
                }
                mInUse = null;
            }

            /* WILL SEND GARBAGE if multiple responses end up interleaved.
             * Only one response should be in progress at a time.
             */
            private class WebSocketOutputStream extends ByteArrayOutputStream {
                @Override
                public void write(int b) {
                    checkFlush();
                    super.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    checkFlush();
                    super.write(b, off, len);
                }

                @Override
                public void close() {
                    final ByteBuffer message = ByteBuffer.wrap(toByteArray());
                    mClient.sendFragmentedFrame(Framedata.Opcode.TEXT, message, true);
                    reset();
                }

                private void checkFlush() {
                    if (size() > MAX_BUFFER_SIZE) {
                        final ByteBuffer message = ByteBuffer.wrap(toByteArray());
                        mClient.sendFragmentedFrame(Framedata.Opcode.TEXT, message, false);
                        reset();
                    }
                }

                private static final int MAX_BUFFER_SIZE = 4096; // TODO too small?
            }

            private WebSocketOutputStream mInUse;
            private final EditorClient mClient;
        }

        /**
         * EditorClient should handle all communication to and from the socket. It should be fairly naive and
         * only know how to delegate messages to the ABHandler class.
         */
        private class EditorClient extends WebSocketClient {
            public EditorClient(URI uri) {
                super(uri);
                mAlive = true;
            }

            @Override
            public void onOpen(ServerHandshake handshakedata) {
                Log.i(LOGTAG, "Connected");
            }

            @Override
            public void onMessage(String message) {
                Log.d(LOGTAG, "message: " + message);
                try {
                    final JSONObject messageJson = new JSONObject(message);
                    String type = messageJson.getString("type");
                    if (type.equals("snapshot_request")) {
                        Message msg = mHandler.obtainMessage(MESSAGE_SEND_STATE_FOR_EDITING);
                        mHandler.sendMessage(msg);
                    } else if (type.equals("change_request")) {
                        Message msg = mHandler.obtainMessage(MESSAGE_HANDLE_CHANGES_RECEIVED);
                        msg.obj = messageJson;
                        mHandler.sendMessage(msg);

                        // TODO actual multiplexing messages based on messageType
                    }
                } catch (JSONException e) {
                    Log.e(LOGTAG, "Bad JSON received:" + message, e);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                synchronized (this) {
                    mAlive = false;
                }
                Log.i(LOGTAG, "WebSocket closed. Code: " + code + ", reason: " + reason);
            }

            @Override
            public void onError(Exception ex) {
                if (ex != null && ex.getMessage() != null) {
                    Log.e(LOGTAG, "Websocket Error: " + ex.getMessage());
                } else {
                    Log.e(LOGTAG, "a Websocket error occurred");
                }
            }

            public boolean isAlive() {
                synchronized (this) {
                    return mAlive;
                }
            }

            private boolean mAlive;
        }

        private EditorConnection mEditorConnection;
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

    private ABHandler mHandler;
    private final Tweaks mTweaks = new Tweaks();
    private final Context mContext;
    private final List<Activity> mLiveActivities = new ArrayList<Activity>();
    private final Map<String, ArrayList<JSONObject>> mChanges =
            new HashMap<String, ArrayList<JSONObject>>();
    private final String mToken;

    private static final int MESSAGE_INITIALIZE_CHANGES = 0;
    private static final int MESSAGE_CONNECT_TO_EDITOR = 1;
    private static final int MESSAGE_SEND_STATE_FOR_EDITING = 2;
    private static final int MESSAGE_HANDLE_CHANGES_RECEIVED = 3;
    private static final String SHARED_PREF_CHANGES_FILE = "mixpanel.abtesting.changes";
    private static final String SHARED_PREF_CHANGES_KEY = "mixpanel.abtesting.changes";

    @SuppressWarnings("unused")
    private static final String LOGTAG = "ABTesting";
}
