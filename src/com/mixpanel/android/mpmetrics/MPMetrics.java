package com.mixpanel.android.mpmetrics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;

import com.mixpanel.android.util.StringUtils;

public class MPMetrics {
    public static final String VERSION = "2.0";

    private static final String LOGTAG = "MPMetrics";

    // Maps each token to a singleton MPMetrics instance
    public static HashMap<String, MPMetrics> mInstanceMap = new HashMap<String, MPMetrics>();

    // Creates a single thread pool to perform the HTTP requests and insert events into sqlite
    private static ThreadPoolExecutor sExecutor =
            new ThreadPoolExecutor(0, 1, MPConfig.SUBMIT_THREAD_TTL, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), new LowPriorityThreadFactory());

    private Context mContext;

    private String mToken;

    private String mCarrier;
    private String mModel;
    private String mVersion;
    private String mDeviceId;
    private String distinct_id;
    
    private SharedPreferences mPushPref;

    private JSONObject mSuperProperties;
    private MPDbAdapter mDbAdapter;

    // Used to allow only one events/people submit task in the queue
    // at a time to prevent unnecessary extraneous requests
    private static volatile boolean sEventsSubmitLock = false;
    private static volatile boolean sPeopleSubmitLock = false;

    private static int FLUSH_EVENTS = 0;
    private static int FLUSH_PEOPLE = 1;

    // Used for sending flushes every MPConfig.FLUSH_RATE seconds when events/people
    // are actually being tracked.
    private class UniqueHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == FLUSH_EVENTS) {
                flushEvents();
            } else if (msg.what == FLUSH_PEOPLE){
                flushPeople();
            }
        }

        public boolean sendUniqueEmptyMessageDelayed(int what, long delayMillis) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "sendUniqueEmptyMessageDelayed " + what + "...");
            if (!this.hasMessages(what)) {
                if (MPConfig.DEBUG) Log.d(LOGTAG, "success.");
                return this.sendEmptyMessageDelayed(what, delayMillis);
            }
            if (MPConfig.DEBUG) Log.d(LOGTAG, "blocked.");
            return false;
        }
    }
    private UniqueHandler mTimerHandler;

    private MPMetrics(Context context, String token) {
        mContext = context;
        mToken = token;

        mCarrier = getCarrier();
        mModel = getModel();
        mVersion = getVersion();
        mDeviceId = getDeviceId();

        mSuperProperties = new JSONObject();

        mDbAdapter = new MPDbAdapter(mContext, mToken);
        mDbAdapter.cleanupEvents(System.currentTimeMillis() - MPConfig.DATA_EXPIRATION, MPDbAdapter.EVENTS_TABLE);
        mDbAdapter.cleanupEvents(System.currentTimeMillis() - MPConfig.DATA_EXPIRATION, MPDbAdapter.PEOPLE_TABLE);

        sExecutor.setKeepAliveTime(MPConfig.SUBMIT_THREAD_TTL, TimeUnit.MILLISECONDS);
        mTimerHandler = new UniqueHandler();
        
        mPushPref = context.getSharedPreferences("com.mixpanel.android.mpmetrics.MPMetrics", Context.MODE_PRIVATE);
    }

    /**
     * Register super properties for events
     * @param superProperties    A JSONObject containing super properties to register
     * @param type  Indicates which types of events to apply the super properties. Must be SUPER_PROPERTY_TYPE_ALL,
     *              SUPER_PROPERTY_TYPE_EVENTS, or SUPER_PROPERTY_TYPE_FUNNELS
     */
    public void registerSuperProperties(JSONObject superProperties) {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "registerSuperProperties");

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
        if (MPConfig.DEBUG) Log.d(LOGTAG, "clearSuperProperties");
        mSuperProperties = new JSONObject();
    }

    /**
     * Identifies all the requests sent with the given distinct_id
     * @param distinct_id
     */
    public void identify(String distinct_id) {
       this.distinct_id = distinct_id;
    }

    /**
     * Track an event.
     *
     * @param eventName The name of the event to send
     * @param properties A JSONObject containing the key value pairs of the properties to include in this event.
     * Pass null if no extra properties exist.
     */
    public void track(String eventName, JSONObject properties) {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "track " + eventName);

        sExecutor.submit(new EventsQueueTask(eventName, properties));
    }

    /**
     * Set the given properties for the identified distinct_id
     * @param properties
     */
    public void set(JSONObject properties) {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "set " + properties.toString());
        if (this.distinct_id == null) {
            return;
        }

        sExecutor.submit(new PeopleQueueTask("$set", properties));
    }
    public void set(String property, Object value) {
        try {
            set(new JSONObject().put(property, value));
        } catch (JSONException e) {
            Log.e(LOGTAG, "set", e);
        }
    }

    /**
     * Increment the given properties by the long value, which may be negative.
     * @param properties
     */
    public void increment(Map<String, Long> properties) {
        JSONObject json = new JSONObject(properties);
        if (MPConfig.DEBUG) Log.d(LOGTAG, "increment " + json.toString());
        if (this.distinct_id == null) {
            return;
        }

        sExecutor.submit(new PeopleQueueTask("$add", json));
    }
    public void increment(String property, long value) {
        Map<String, Long> map = new HashMap<String, Long>();
        map.put(property, value);
        increment(map);
    }

    /**
     * Delete all properties set for this user.
     */
    public void delete() {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "delete");
        if (this.distinct_id == null) {
            return;
        }

        sExecutor.submit(new PeopleQueueTask("$delete", null));
    }

    public void flushAll() {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "flushAll");
        flushEvents();
        flushPeople();
    }
    public void flushEvents() {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "flushEvents");
        if (!sEventsSubmitLock) {
            sEventsSubmitLock = true;
            sExecutor.submit(new SubmitTask(FLUSH_EVENTS));
        }
    }
    public void flushPeople() {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "flushPeople");
        if (!sPeopleSubmitLock) {
            sPeopleSubmitLock = true;
            sExecutor.submit(new SubmitTask(FLUSH_PEOPLE));
        }
    }

    public void setPushRegistrationId(String registrationId) {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "setting push registration id: " + registrationId);
        
        mPushPref.edit().putString("push_id", registrationId).commit();
        try {
            set("$android_devices",  new JSONArray("[" + registrationId + "]"));
        } catch (JSONException e) {}
    }

    public void removePushRegistrationId() {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "removing push registration id");
        
        mPushPref.edit().remove("push_id").commit();
        set("$android_devices", new JSONArray());
    }

    /**
     * Enables push notifications from GCM.
     * @param accountEmail the Google account that registered for GCM
     */
    public void enablePush(String senderID) {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "enablePush");
        if (Build.VERSION.SDK_INT < 8) { // older than froyo
            return;
        }

        if (getPushId() == null) {
            Intent registrationIntent = new Intent("com.google.android.c2dm.intent.REGISTER");
            registrationIntent.putExtra("app", PendingIntent.getBroadcast(mContext, 0, new Intent(), 0)); // boilerplate
            registrationIntent.putExtra("sender", senderID);
            mContext.startService(registrationIntent);
        }
    }

    public void disablePush() {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "disablePush");
        if (Build.VERSION.SDK_INT < 8) { // older than froyo
            return;
        }

        Intent unregIntent = new Intent("com.google.android.c2dm.intent.UNREGISTER");
        unregIntent.putExtra("app", PendingIntent.getBroadcast(mContext, 0, new Intent(), 0));
        mContext.startService(unregIntent);
    }
    
    public String getPushId() {
        return mPushPref.getString("push_id", null);
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
        private String table;
        private int messageType;

        public SubmitTask(int messageType) {
            this.messageType = messageType;
            if (messageType == FLUSH_PEOPLE) {
                this.table = MPDbAdapter.PEOPLE_TABLE;
                sPeopleSubmitLock = false;
            } else {
                this.table = MPDbAdapter.EVENTS_TABLE;
                sEventsSubmitLock = false;
            }
        }

        @Override
        public void run() {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "SubmitTask " + this.table + " running.");

            String[] data = mDbAdapter.generateDataString(table);
            if (data == null) {
                return;
            }

            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost;
            if (this.table.equals(MPDbAdapter.PEOPLE_TABLE)) {
                httppost = new HttpPost(MPConfig.BASE_ENDPOINT + "/engage");
            } else {
                httppost = new HttpPost(MPConfig.BASE_ENDPOINT + "/track?ip=1");
            }

            try {
                List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
                nameValuePairs.add(new BasicNameValuePair("data", data[1]));
                httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

                HttpResponse response = httpclient.execute(httppost);
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    mTimerHandler.sendUniqueEmptyMessageDelayed(this.messageType, MPConfig.FLUSH_RATE);
                    return;
                }

                try {
                    String result = StringUtils.inputStreamToString(entity.getContent());
                    if (MPConfig.DEBUG) {
                        Log.d(LOGTAG, "HttpResponse result: " + result);
                    }
                    if (!result.equals("1\n")) {
                        mTimerHandler.sendUniqueEmptyMessageDelayed(this.messageType, MPConfig.FLUSH_RATE);
                        return;
                    }

                    // Success, so prune the database.
                    mDbAdapter.cleanupEvents(data[0], table);

                } catch (IOException e) {
                    Log.e(LOGTAG, "SubmitTask " + table, e);
                    mTimerHandler.sendUniqueEmptyMessageDelayed(this.messageType, MPConfig.FLUSH_RATE);
                    return;
                } catch (OutOfMemoryError e) {
                    Log.e(LOGTAG, "SubmitTask " + table, e);
                    mTimerHandler.sendUniqueEmptyMessageDelayed(this.messageType, MPConfig.FLUSH_RATE);
                    return;
                }
            // Any exceptions, log them and stop this task.
            } catch (ClientProtocolException e) {
                Log.e(LOGTAG, "SubmitTask " + table, e);
                mTimerHandler.sendUniqueEmptyMessageDelayed(this.messageType, MPConfig.FLUSH_RATE);
                return;
            } catch (IOException e) {
                Log.e(LOGTAG, "SubmitTask " + table, e);
                mTimerHandler.sendUniqueEmptyMessageDelayed(this.messageType, MPConfig.FLUSH_RATE);
                return;
            }
        }
    }

    private class EventsQueueTask implements Runnable {
        private String eventName;
        private JSONObject properties;
        private long time;

        public EventsQueueTask(String eventName, JSONObject properties) {
            this.eventName = eventName;
            this.properties = properties;
            this.time = System.currentTimeMillis() / 1000;
        }

        public void run() {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "EventsQueueTask running, queueing " + this.eventName);

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

                if (distinct_id != null) {
                    propertiesObj.put("distinct_id", distinct_id);
                }

                if (properties != null) {
                    for (Iterator<String> iter = properties.keys(); iter.hasNext();) {
                        String key = iter.next();
                        propertiesObj.put(key, properties.get(key));
                    }
                }

                dataObj.put("properties", propertiesObj);
            } catch (JSONException e) {
                Log.e(LOGTAG, "EventsQueueTask " + eventName, e);
                return;
            }

            int count = mDbAdapter.addJSON(dataObj, MPDbAdapter.EVENTS_TABLE);
            if (MPConfig.TEST_MODE || count >= MPConfig.BULK_UPLOAD_LIMIT) {
                if (MPConfig.DEBUG) Log.d(LOGTAG, "EventsQueueTask in test or count greater than MPConfig.BULK_UPLOAD_LIMIT");
                flushEvents();
            } else {
                mTimerHandler.sendUniqueEmptyMessageDelayed(FLUSH_EVENTS, MPConfig.FLUSH_RATE);
            }
        }
    }

    private class PeopleQueueTask implements Runnable {
        private String actionType;
        private JSONObject properties;

        public PeopleQueueTask(String actionType, JSONObject properties) {
            this.actionType = actionType;
            this.properties = properties;
        }

        public void run() {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "PeopleQueueTask running, queueing " + this.actionType);
            JSONObject dataObj = new JSONObject();
            try {
                dataObj.put(this.actionType, properties);
                dataObj.put("$token", mToken);
                dataObj.put("$distinct_id", distinct_id);
                dataObj.put("$time", System.currentTimeMillis());
            } catch (JSONException e) {
                Log.e(LOGTAG, "PeopleQueueTask " + properties.toString(), e);
                return;
            }

            int count = mDbAdapter.addJSON(dataObj, MPDbAdapter.PEOPLE_TABLE);
            if (MPConfig.TEST_MODE || count >= MPConfig.BULK_UPLOAD_LIMIT) {
                if (MPConfig.DEBUG) Log.d(LOGTAG, "PeopleQueueTask MPConfig.TEST_MODE set or count greater than MPConfig.BULK_UPLOAD_LIMIT");
                flushPeople();
            } else {
                mTimerHandler.sendUniqueEmptyMessageDelayed(FLUSH_PEOPLE, MPConfig.FLUSH_RATE);
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
}
