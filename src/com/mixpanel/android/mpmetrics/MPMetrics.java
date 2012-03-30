package com.mixpanel.android.mpmetrics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.mixpanel.android.util.StringUtils;

public class MPMetrics {
    private static final String LOGTAG = "MPMetrics";


    private static final int BULK_UPLOAD_LIMIT = 40;
    private static final int FLUSH_RATE = 60 * 1000; // time, in milliseconds that the data should be flushed

    // Remove events that have sat around for this many milliseconds
    private static final int DATA_EXPIRATION = 1000 * 60 * 60 * 48; // 48 hours

    // Maps each token to a singleton MPMetrics instance
    private static HashMap<String, MPMetrics> mInstanceMap = new HashMap<String, MPMetrics>();

    private static String track_endpoint = "http://api.mixpanel.com/track?ip=1";

    private Context mContext;

    private String mToken;

    private String mCarrier;
    private String mModel;
    private String mVersion;
    private String mDeviceId;

    private JSONObject mSuperProperties;

    private MPDbAdapter mDbAdapter;

    private boolean mTestMode;

    private Timer mTimer;

    // Creates a single thread pool to perform the HTTP requests and insert events into sqlite
    private ThreadPoolExecutor executor =
            new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());

    private MPMetrics(Context context, String token) {
        mContext = context;
        mToken = token;

        mCarrier = getCarrier();
        mModel = getModel();
        mVersion = getVersion();
        mDeviceId = getDeviceId();

        mSuperProperties = new JSONObject();

        mDbAdapter = new MPDbAdapter(mContext, mToken);
        mDbAdapter.cleanupEvents(System.currentTimeMillis() - DATA_EXPIRATION);

        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                flush();
            }
        }, 0, FLUSH_RATE);
    }

    /**
     * Register super properties for events
     * @param superProperties    A JSONObject containing super properties to register
     * @param type  Indicates which types of events to apply the super properties. Must be SUPER_PROPERTY_TYPE_ALL,
     *              SUPER_PROPERTY_TYPE_EVENTS, or SUPER_PROPERTY_TYPE_FUNNELS
     */
    public void registerSuperProperties(JSONObject superProperties) {
        if (Global.DEBUG) Log.d(LOGTAG, "registerSuperProperties");

        for (Iterator<String> iter = superProperties.keys(); iter.hasNext(); ) {
            String key = iter.next();
            try {
                mSuperProperties.put(key, superProperties.get(key));
            } catch (JSONException e) {
                Log.e(LOGTAG, "Exception registering super property.", e);
            }
        }
    }

    /**
     * Clear super properties
     */
    public void clearSuperProperties() {
        if (Global.DEBUG) Log.d(LOGTAG, "clearSuperProperties");
        mSuperProperties = new JSONObject();
    }

    /**
     * Track an event.
     *
     * @param eventName The name of the event to send
     * @param properties A JSONObject containing the key value pairs of the properties to include in this event.
     * Pass null if no extra properties exist.
     */
    public void track(String eventName, JSONObject properties) {
        if (Global.DEBUG) Log.d(LOGTAG, "track");

        executor.submit(new QueueTask(eventName, properties));
    }

    public void flush() {
        if (Global.DEBUG) Log.d(LOGTAG, "flush");

        executor.submit(new SubmitTask());
    }

    /**
     * Enable test mode. Normally, calls to MPMetrics.event() and MPMetrics.funnel() are collected and sent out in
     * bulk to conserve resources. By enabling test mode, data will be sent out as soon as a call to
     * event() or funnel() is made.
     */
    public void enableTestMode() {
        if (Global.DEBUG) Log.d(LOGTAG, "enableTestMode");
        mTestMode = true;
    }

    /**
     * Return the carrier of the phone
     * @return   A String containing the carrier
     */
    private String getCarrier() {
        return Build.BRAND;
    }

    /**
     * Return the model of the phone
     * @return  A String containing the model
     */
    private String getModel() {
        return Build.MODEL;
    }

    /**
     * Return the Android version of the phone
     * @return  A String containing the android version
     */
    private String getVersion() {
        return Build.VERSION.RELEASE;
    }

    /**
     * Return the unique device identifier of the phone
     * @param context   the context
     * @return  A String containing the device's unique identifier
     */
    private String getDeviceId() {
        String product = Build.PRODUCT;
        String androidId = Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (product == null || androidId == null) {
            return null;
        } else {
            return product + "_" + androidId;
        }
    }

    private class SubmitTask implements Runnable {
        private static final String LOGTAG = "SubmitTask";

        @Override
        public void run() {
            String[] data = mDbAdapter.generateDataString();
            if (data == null) {
                // Couldn't get data for whatever reason, so just return.
                return;
            }

            // Post the data
            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = new HttpPost(track_endpoint);

            try {
                // Add your data
                List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
                nameValuePairs.add(new BasicNameValuePair("data", data[1]));
                httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

                HttpResponse response = httpclient.execute(httppost);
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    return;
                }

                try {
                    String result = StringUtils.inputStreamToString(entity.getContent());
                    if (Global.DEBUG) {
                        Log.d(LOGTAG, "HttpResponse result: " + result);
                    }
                    if (!result.equals("1\n")) {
                        return;
                    }

                    // Success, so prune the database.
                    mDbAdapter.cleanupEvents(data[0]);

                // If anything went wrong, don't remove from the db so we can try again
                // on the next flush.
                } catch (IOException e) {
                    Log.e(LOGTAG, "SubmitTask", e);
                    return;
                } catch (OutOfMemoryError e) {
                    Log.e(LOGTAG, "SubmitTask", e);
                    return;
                }
            // Any exceptions, log them and stop this task.
            } catch (ClientProtocolException e) {
                Log.e(LOGTAG, "SubmitTask", e);
                return;
            } catch (IOException e) {
                Log.e(LOGTAG, "SubmitTask", e);
                return;
            }
        }
    }

    private class QueueTask implements Runnable {
        private String eventName;
        private JSONObject properties;
        private String time;

        public QueueTask(String eventName, JSONObject properties) {
            this.eventName = eventName;
            this.properties = properties;
            this.time = Long.toString(System.currentTimeMillis() / 1000);
        }

        @Override
        public void run() {
            JSONObject dataObj = new JSONObject();
            try {
                dataObj.put("event", eventName);
                JSONObject propertiesObj = new JSONObject();
                propertiesObj.put("token", mToken);
                propertiesObj.put("time", time);
                propertiesObj.put("distinct_id", mDeviceId == null ? "UNKNOWN" : mDeviceId);
                propertiesObj.put("carrier", mCarrier == null ? "UNKNOWN" : mCarrier);
                propertiesObj.put("model",  mModel == null ? "UNKNOWN" : mModel);
                propertiesObj.put("version", mVersion == null ? "UNKNOWN" : mVersion);
                propertiesObj.put("mp_lib", "android");

                for (Iterator<String> iter = mSuperProperties.keys(); iter.hasNext(); ) {
                    String key = iter.next();
                    propertiesObj.put(key, mSuperProperties.get(key));
                }

                if (properties != null) {
                    for (Iterator<String> iter = properties.keys(); iter.hasNext();) {
                        String key = iter.next();
                        propertiesObj.put(key, properties.get(key));
                    }
                }

                dataObj.put("properties", propertiesObj);
            } catch (JSONException e) {
                Log.e(LOGTAG, "event", e);
                return;
            }

            int count = mDbAdapter.addEvent(dataObj);

            if (mTestMode || (count >= BULK_UPLOAD_LIMIT && executor.getQueue().isEmpty())) {
                flush();
            }
        }
    }

    public static MPMetrics getInstance(Context context, String token) {
        MPMetrics instance = mInstanceMap.get(token);
        if (instance == null) {
            instance = new MPMetrics(context.getApplicationContext(), token);
            mInstanceMap.put(token,  instance);
        }

        return instance;
    }

    /**
     * If you want to post events to your own custom endpoint.
     * @param address the address where you want events sent to
     */
    public static void setTrackEndpoint(String address) {
        MPMetrics.track_endpoint = address;
    }
}
