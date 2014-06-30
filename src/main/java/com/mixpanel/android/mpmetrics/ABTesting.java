package com.mixpanel.android.mpmetrics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
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

import com.mixpanel.android.abtesting.Tweaks;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
public class ABTesting {

    ABTesting(Context context, String token) {
        mContext = context;
        mToken = token;

        if (android.os.Build.VERSION.SDK_INT >= 14) {
            final Application app = (Application) mContext.getApplicationContext();
            app.registerActivityLifecycleCallbacks(new LifecycleCallbacks());

            HandlerThread thread = new HandlerThread(ABTesting.class.getCanonicalName());
            thread.start();
            mHandler = new ABHandler(thread.getLooper());
        }
    }

    void handleChangesReceived(JSONObject changes, boolean persist, boolean applyToLive) {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("changes", changes);
        args.put("persist", persist);
        args.put("applyToLive", applyToLive);
        Message m = mHandler.obtainMessage(MESSAGE_HANDLE_CHANGES_RECEIVED, args);
        mHandler.sendMessage(m);
    }

    @TargetApi(14)
    private class LifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
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
                            mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_CONNECT_TO_EDITOR));
                        }
                    } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                        mDown--;
                    }
                    return true;
                }

                private int mDown = 0;
            });
        }

        @Override
        public void onActivityResumed(Activity activity) {
            mLiveActivities.add(activity);
        }

        @Override
        public void onActivityPaused(Activity activity) {
            mLiveActivities.remove(activity);
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
    }

    /**
     * This class is really the main class for ABTesting. It does all the work on a HandlerThread.
     */
    public class ABHandler extends Handler {

        public ABHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MESSAGE_CONNECT_TO_EDITOR:
                    this.connectToEditor();
                    break;
                case MESSAGE_SEND_STATE_FOR_EDITING:
                    this.sendStateForEditing();
                    break;
                case MESSAGE_HANDLE_CHANGES_RECEIVED:
                    Map<String, Object> args = (Map<String, Object>) msg.obj;
                    this.handleChangesReceived((JSONObject) args.get("changes"), (Boolean) args.get("persist"), (Boolean) args.get("applyToLive"));
                    break;
            }
        }

        private void connectToEditor() {
            Log.v(LOGTAG, "connectToEditor called");

            if (mEditorClient == null || !mEditorClient.isAlive()) {
                final String baseUrl = MPConfig.getInstance(mContext).getABTestingUrl();
                try {
                    mEditorClient = new EditorClient(new URI(baseUrl + mToken));
                } catch (URISyntaxException e) {
                    Log.e(LOGTAG, "Error parsing URI for editor websocket", e);
                }

                try {
                    boolean connected = mEditorClient.connectBlocking();
                    if (! connected) {
                        Log.d(LOGTAG, "Can't connect to endpoint " + baseUrl);
                        mEditorClient = null;
                    }
                } catch (InterruptedException e) {
                    mEditorClient = null;
                    Log.e(LOGTAG, "Editor client was interrupted during connection", e);
                }
            }
        }

        private void sendStateForEditing() {
            Log.v(LOGTAG, "sendStateForEditing");
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("{\"type\": \"snapshot_response\",");

                boolean first = true;

                sb.append("\"activities\": [");
                for (Activity a : mLiveActivities) {
                    View rootView = a.getWindow().getDecorView().getRootView();
                    if (!first) {
                          sb.append(",");
                    }
                    first = false;

                    sb.append("{");

                    sb.append("\"class\":");
                    sb.append("\"" + a.getClass().getCanonicalName() + "\"");
                    sb.append(",");

                    sb.append("\"screenshot\": ");
                    writeScreenshot(rootView, sb);
                    sb.append(",");

                    sb.append("\"view\": [");
                    sb.append(serializeView(rootView, 1).toString());
                    sb.append("]");

                    sb.append("}");
                }
                sb.append("],");

                sb.append("\"tweaks\":");
                sb.append(new JSONObject(mTweaks.getAll()).toString());

                sb.append("}");
                mEditorClient.send(sb.toString());
            } catch (IOException e) {
                Log.e(LOGTAG, "Can't write snapshot request to server", e);
            }
        }

        private void handleChangesReceived(JSONObject changes, boolean persist, boolean applyToLive) {
            if (persist) {
                Log.v(LOGTAG, "persisting received changes");
                // todo: write persistence logic for changes
            }

            if (applyToLive) {
                for(Activity activity : mLiveActivities) {
                    applyChanges(activity, changes);
                }
            }
        }

        private void applyChanges(Activity activity, JSONObject changes) {
            Log.v(LOGTAG, "applyChanges called on " + activity.getClass().getCanonicalName());
        }

        // Writes a QUOTED, Base64 string to writer, or the string "null" if no bitmap could be written
        // due to memory or rendering issues.
        private void writeScreenshot(View rootView, StringBuilder sb) throws IOException {
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
                sb.append("\"");
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 30, out);
                sb.append(Base64.encodeToString(out.toByteArray(), Base64.NO_PADDING | Base64.NO_WRAP));
                sb.append("\"");
            } else {
                sb.append("null");
            }

            if (!originalCacheState) {
                rootView.setDrawingCacheEnabled(false);
            }
        }

        private JSONObject serializeView(View view, int index) throws IOException {
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
                    final Map<String, Integer> viewIndex = new HashMap<String, Integer>();
                    final ViewGroup group = (ViewGroup) view;
                    final int childCount = group.getChildCount();

                    for (int i = 0; i < childCount; i++) {
                        final View child = group.getChildAt(i);
                        int childIndex = 1;
                        if (viewIndex.containsKey(child.getClass().getCanonicalName())) {
                            childIndex = viewIndex.get(child.getClass().getCanonicalName()) + 1;
                        }
                        viewIndex.put(child.getClass().getCanonicalName(), childIndex);
                        children.put(serializeView(child, childIndex));
                    }
                }
                dump.put("children", children);
                dump.put("index", index);
            } catch (JSONException impossible) {
                throw new RuntimeException("Apparently Impossible JSONException", impossible);
            }
            return dump;
        }

        private EditorClient mEditorClient;
    }

    /**
     * EditorClient should handle all communication to and from the socket. It should be fairly naive and
     * only know how to delegate messages to the ABHandler class.
     */
    private class EditorClient extends WebSocketClient {
        public EditorClient(URI uri) {
            super(uri);
            mAlive = true;
            mProtocol = new Protocol();
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
                mProtocol.respond(messageJson);
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
        private final Protocol mProtocol;
    }

    /*
     * Protocol knows how to turn inbound JSON into instructions.
     */
    private class Protocol { // TODO MOVE JSON STUFF HERE
        public void respond(JSONObject messageJson) {
            try {
                String messageType;
                JSONObject payload = null;
                messageType = messageJson.getString("type");
                if (messageJson.has("payload")) {
                    payload = messageJson.getJSONObject("payload");
                }

                // TODO actual multiplexing messages based on messageType
                Message m = mHandler.obtainMessage(MESSAGE_SEND_STATE_FOR_EDITING, mContext);
                mHandler.sendMessage(m);
            } catch (JSONException e) {
                Log.e(LOGTAG, "Can't interpret message due to missing fields: " + messageJson.toString(), e);
            }
        }
    }

    public interface TweakChangeCallback {
        public void onChange(Object value);
    }

    public Tweaks getTweaks() {
        return mTweaks;
    }

    private ABHandler mHandler;
    private final Tweaks mTweaks = new Tweaks();
    private final Context mContext;
    private final List<Activity> mLiveActivities = new ArrayList<Activity>();
    private final String mToken;

    private static final int MESSAGE_CONNECT_TO_EDITOR = 0;
    private static final int MESSAGE_SEND_STATE_FOR_EDITING = 1;
    private static final int MESSAGE_HANDLE_CHANGES_RECEIVED = 2;
    private static final String LOGTAG = "ABTesting";
}
