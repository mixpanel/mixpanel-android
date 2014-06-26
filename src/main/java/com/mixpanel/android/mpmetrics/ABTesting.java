package com.mixpanel.android.mpmetrics;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONObject;

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
        Log.i("ASDF", "creating ABTESTING");

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
    public void onActivityCreated(Activity activity, Bundle bundle) {
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
    public void onActivityStarted(Activity activity) { }

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
            Log.v(LOGTAG, "sendStateForEditing called");
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
