package com.mixpanel.android.mpmetrics;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Base64OutputStream;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ABTesting class should at the parent level be very lightweight and simply proxy requests to
 * the ABHandler which runs on a HandlerThread
 */
public class ABTesting implements Application.ActivityLifecycleCallbacks {

    ABTesting(Context context) {
        mContext = context;

        if (android.os.Build.VERSION.SDK_INT >= 14) {
            final Application app = (Application) mContext.getApplicationContext();
            app.registerActivityLifecycleCallbacks(this);

            HandlerThread thread = new HandlerThread(getClass().getCanonicalName());
            thread.start();
            mHandler = new ABHandler(thread.getLooper());
            mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_CONNECT_TO_PROXY, "ws://anluswang.com/websocket_proxy/THE_KEY"));
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

    @Override
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
                        Message m = mHandler.obtainMessage(MESSAGE_SEND_STATE_FOR_EDITING, mContext);
                        mHandler.sendMessage(m);
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
    public void onActivityResumed(Activity activity) { }

    @Override
    public void onActivityPaused(Activity activity) { }

    @Override
    public void onActivityStopped(Activity activity) { }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {  }

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
            switch(msg.what) {
                case MESSAGE_CONNECT_TO_PROXY:
                    this.connectToProxy((String)msg.obj);
                    break;
                case MESSAGE_SEND_STATE_FOR_EDITING:
                    this.sendStateForEditing((Context) msg.obj);
                    break;
                case MESSAGE_HANDLE_CHANGES_RECEIVED:
                    Map<String, Object> args = (Map<String, Object>) msg.obj;
                    this.handleChangesReceived((JSONObject) args.get("changes"), (Boolean) args.get("persist"), (Boolean) args.get("applyToLive"));
                    break;
            }
        }

        private void connectToProxy(String url) {
            Log.v(LOGTAG, "connectToProxy called");

            if (mProxyClient == null) {
                mProxyClient = new ProxyClient(url);
            }

            if (!mProxyClient.isConnected()) {
                mProxyClient.connect();
            }
        }

        private void sendStateForEditing(Context context) {
            final Writer writer = new OutputStreamWriter(new WebSocketOutputStream(mProxyClient));
            try {
                writer.write("{\"type\": \"snapshot_response\",");

                boolean first = true;
                writer.write("\"activities\": [");
                for (Activity a : liveActivities) {
                    if (!first) {
                        writer.write(",");
                    }

                    View rootView = a.getWindow().getDecorView().getRootView();
                    writer.write("{\"class\":");
                    writer.write("\"" + a.getClass().getCanonicalName() + "\"");
                    writer.write(",");

                    writer.write("\"screenshot\":");
                    writeScreenshot(rootView, writer);
                    writer.write(",");

                    writer.write("\"view\": [");
                    writer.write(serializeView(rootView, 1).toString());

                    writer.write("}]");

                    first = false;
                }
                writer.write("\n}");
            } catch (IOException e) {
                Log.e(LOGTAG, "Can't write snapshot request to server", e);
            }
        }

        private void handleChangesReceived(JSONObject changes, boolean persist, boolean applyToLive) {
            liveChanges = changes;

            if (persist) {
                Log.v(LOGTAG, "persisting received changes");
                // todo: write persistence logic for changes
            }

            if (applyToLive) {
                for(Activity activity : liveActivities) {
                    applyChanges(activity, changes);
                }
            }
        }

        private void applyChanges(Activity activity, JSONObject changes) {
            Log.v(LOGTAG, "applyChanges called on " + activity.getClass().getCanonicalName());
        }

        // Writes a QUOTED, Base64 string to writer, or the string "null" if no bitmap could be written
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
                final Base64OutputStream b64out = new Base64OutputStream(writer);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 30, b64out);
                b64out.close();
                writer.write("\"");
            } else {
                writer.write("null");
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

        private ProxyClient mProxyClient;
        private List<Activity> liveActivities = new ArrayList<Activity>();
        private JSONObject liveChanges; // this will probably become a custom strongly-typed object instead of JSON
    }


    /**
     * ProxyClient should handle all communication to and from the socket. It should be fairly naive and
     * only know how to delegate messages to the ABHandler class.
     */
    private class ProxyClient {
        private ProxyClient(String url) {
            PROXY_URL = url;
        }

        boolean isConnected() {
            return false;
        }

        void connect() {
            Log.v(LOGTAG, "connecting to proxy at " + PROXY_URL + "...");
        }

        private final String PROXY_URL;
    }

    private ABHandler mHandler;

    private final Context mContext;
    private static final int MESSAGE_CONNECT_TO_PROXY = 0;
    private static final int MESSAGE_SEND_STATE_FOR_EDITING = 1;
    private static final int MESSAGE_HANDLE_CHANGES_RECEIVED = 2;
    private static final String LOGTAG = "ABTesting";
}
