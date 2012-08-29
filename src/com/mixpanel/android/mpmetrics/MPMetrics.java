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

    private final Context mContext;

    private final String mToken;

    private final String mCarrier;
    private final String mModel;
    private final String mVersion;
    private final String mDeviceId;
    private final People mPeople;

    private String distinct_id;

    private final SharedPreferences mPushPref;

    private JSONObject mSuperProperties;
    private final MPDbAdapter mDbAdapter;

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
                mPeople.flush();
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
    private final UniqueHandler mTimerHandler;

    private MPMetrics(Context context, String token) {
        mContext = context;
        mToken = token;

        mCarrier = getCarrier();
        mModel = getModel();
        mVersion = getVersion();
        mDeviceId = getDeviceId();
        mPeople = new PeopleImpl();

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

        for (Iterator<?> iter = superProperties.keys(); iter.hasNext(); ) {
            String key = (String) iter.next();
            try {
                mSuperProperties.put(key, superProperties.get(key));
            } catch (JSONException e) {
                Log.e(LOGTAG, "Exception registering super property.", e);
            }
        }
    }

    /**
     * Clear all superProperties. Future tracking calls to Mixpanel (even those already queued up but not
     * yet sent to Mixpanel servers) will not be associated with the superProperties registered
     * before this call was made.
     */
    public void clearSuperProperties() {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "clearSuperProperties");
        mSuperProperties = new JSONObject();
    }

    /**
     * Associates all of the {@link track} events sent by this user with the
     * given disinct_id. This call does not identify the user for People Analytics;
     * to do that, see {@link People.identify}. Mixpanel recommends using
     * the same distinct_id for both calls, and using a distinct_id that is easy
     * to associate with the given user, for example, a server-side account identifier.
     *
     * @param distinct_id a string uniquely identifying this user. Events sent to
     *     Mixpanel using the same disinct_id will be considered associated with the
     *     same visitor/customer for retention and funnel reporting, so be sure that the given
     *     value is globally unique for each individual user you intend to track.
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
     * Returns a Mixpanel.People object that can be used to set and increment
     * People Analytics properties
     *
     * @return
     */
    public People getPeople() {
        return mPeople;
    }

    /**
     * Use MPMetrics.People to interact with Mixpanel People Analytics features
     */
    public interface People {
        /**
         * Identify a user with a unique ID. All future calls to the people object will rely on this
         * value to assign and increment properties. Calls to {@link set} and {@link increment}
         * will be queued until identify is called.
         * @param distinct_id
         */
        public void identify(String distinct_id);

        /**
         * Sets a single property with the given name and value for this user.
         * The given name and value will be assigned to the user in Mixpanel People Analytics,
         * possibly overwriting an existing property with the same name.
         *
         * @param propertyName The name of the Mixpanel property. This must be a String, for example "Zip Code"
         *
         * @param value The value of the Mixpanel property. For "Zip Code", this value might be the String "90210"
         */
        public void set(String propertyName, Object value);

        /**
         * Set a collection of properties on the identified user all at once.
         *
         * @param properties a JSONObject containing the collection of properties you wish to apply
         *      to the identified user. Each key in the JSONObject will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         */
        public void set(JSONObject properties);

        /**
         * Add the given amount to an existing property on the identified user. If the user does not already
         * have the associated property, the amount will be added to zero. To reduce a property,
         * provide a negative number for the value
         *
         * @param name the People Analytics property that should have its value changed
         * @param increment the amount to be added to the current value of the named property
         */
        public void increment(String name, long increment);

        /**
         * Like {@link increment}, changing the values of multiple properties at once
         *
         * @param properties A map of String properties names to Long amounts. Each
         *     property associated with a name in the map will have its value changed by the given amount
         */
        public void increment(Map<String, Long> properties);

        /**
         * Permanently deletes the identified user's record from People Analytics.
         */
        public void deleteUser();

        /**
         * Send all queued calls to {@link set} and {@link increment} to
         * Mixpanel servers. This method should be called before your application
         * is taken out of memory- we recommend calling it in the onDestroy method
         * in your the main activity of your application.
         */
        public void flush();

        /**
         * Enable end-to-end Google Cloud Messaging (GCM) from Mixpanel. Calling this method
         * will allow the Mixpanel libraries to handle GCM user registration, and enable
         * Mixpanel to show alerts when GCM messages arrive.
         *
         * If you're planning to use end-to-end support for Messaging, we recommend you
         * call this method immediately after calling {@link People.identify}, likely
         * early in your application's lifecycle.
         *
         * Calls to {@link registerForPush} should not be mixed with calls to {@link setPushRegistrationId}
         * and {@link removePushRegistrationId} in the same application. Application authors
         * should choose one or the other method for handling Mixpanel GCM messages.
         *
         * @param senderID of the Google API Project that registered for Google Cloud Messaging
         *     You can find your ID by looking at the URL of in your Google API Console
         *     at https://code.google.com/apis/console/; it is the twelve digit number after
         *     after "#project:" in the URL address bar on console pages.
         */
        public void registerForPush(String senderID);

        /**
         * If you are handling Google Cloud Messages in your own application, but would like to
         * allow Mixpanel to handle messages originating from Mixpanel campaigns, you should
         * call setPushRegistrationId with the "registration_id" property of the
         * com.google.android.c2dm.intent.REGISTRATION intent when it is received.
         *
         * Calls to setPushRegistrationId should not be mixed with calls to {@link registerForPush}
         * in the same application. In addition, applications that call setPushRegistrationId
         * should also call {@link removePushRegistrationId} when they receive an intent to unregister
         * (a com.google.android.c2dm.intent.REGISTRATION intent with getStringExtra("unregistered") != null)
         *
         * @param registrationId the result of calling intent.getStringExtra("registration_id")
         *     on a com.google.android.c2dm.intent.REGISTRATION intent
         */
        public void setPushRegistrationId(String registrationId);


        /**
         * If you are handling Google Cloud Messages in your own application, you should
         * call this method when your application receives a com.google.android.c2dm.intent.REGISTRATION
         * with getStringExtra("unregistered") != null
         *
         * In general, all applications that call {@link setPushRegistration} should include a call to
         * removePushRegistrationId, and no applications that call {@link registerForPush} should
         * call removePushRegistrationId
         */
        public void removePushRegistrationId();
    }

    private class PeopleImpl implements People {

        private String peopleDistinctId;

        public PeopleImpl() {
            peopleDistinctId = null;
        }

        @Override
        public void identify(String distinct_id) {
            this.peopleDistinctId = distinct_id;
         }

        @Override
        public void set(JSONObject properties) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "set " + properties.toString());
            if (this.peopleDistinctId == null) {
                return;
            }

            sExecutor.submit(new PeopleQueueTask("$set", properties));
        }

        @Override
        public void set(String property, Object value) {
            try {
                set(new JSONObject().put(property, value));
            } catch (JSONException e) {
                Log.e(LOGTAG, "set", e);
            }
        }

        @Override
        public void increment(Map<String, Long> properties) {
            JSONObject json = new JSONObject(properties);
            if (MPConfig.DEBUG) Log.d(LOGTAG, "increment " + json.toString());
            if (this.peopleDistinctId == null) {
                return;
            }

            sExecutor.submit(new PeopleQueueTask("$add", json));
        }

        @Override
        public void increment(String property, long value) {
            Map<String, Long> map = new HashMap<String, Long>();
            map.put(property, value);
            increment(map);
        }

        @Override
        public void deleteUser() {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "delete");
            if (this.peopleDistinctId == null) {
                return;
            }

            sExecutor.submit(new PeopleQueueTask("$delete", null));
        }

        @Override
        public void flush() {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "flushPeople");
            if (!sPeopleSubmitLock) {
                sPeopleSubmitLock = true;
                sExecutor.submit(new SubmitTask(FLUSH_PEOPLE));
            }
        }

        @Override
        public void setPushRegistrationId(String registrationId) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "setting push registration id: " + registrationId);
            if (this.peopleDistinctId == null) {
                return;
            }

            mPushPref.edit().putString("push_id", registrationId).commit();
            try {
                sExecutor.submit(new PeopleQueueTask("$union", new JSONObject().put("$android_devices", new JSONArray("[" + registrationId + "]"))));
            } catch (JSONException e) {
                Log.e(LOGTAG, "set push registration id error", e);
            }
        }

        @Override
        public void removePushRegistrationId() {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "removing push registration id");

            mPushPref.edit().remove("push_id").commit();
            set("$android_devices", new JSONArray());
        }

        @Override
        public void registerForPush(String senderID) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "registerForPush");
            if (Build.VERSION.SDK_INT < 8) { // older than froyo
                if (MPConfig.DEBUG) Log.d(LOGTAG, "Push not supported SDK " + Build.VERSION.SDK);
                return;
            }

            String pushId = getPushId();
            if (pushId == null) {
                if (MPConfig.DEBUG) Log.d(LOGTAG, "Registering a new push id");

                Intent registrationIntent = new Intent("com.google.android.c2dm.intent.REGISTER");
                registrationIntent.putExtra("app", PendingIntent.getBroadcast(mContext, 0, new Intent(), 0)); // boilerplate
                registrationIntent.putExtra("sender", senderID);
                mContext.startService(registrationIntent);
            } else {
                for (String token : mInstanceMap.keySet()) {
                    if (MPConfig.DEBUG) Log.d(LOGTAG, "Using existing pushId " + pushId);
                    mInstanceMap.get(token).getPeople().setPushRegistrationId(pushId);
                }
            }
        }

        public String getPushId() {
            return mPushPref.getString("push_id", null);
        }
    }

    /**
     * Will push all queued Mixpanel events and changes to Mixpanel People Analytics records
     * to Mixpanel servers. Events are pushed gradually throughout the lifetime of your application,
     * but to be sure to push all messages we recommend placing a call to flushAll() in
     * the onDestroy() method of your main application activity.
     */
    public void flushAll() {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "flushAll");
        flushEvents();
        mPeople.flush();
    }
    public void flushEvents() {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "flushEvents");
        if (!sEventsSubmitLock) {
            sEventsSubmitLock = true;
            sExecutor.submit(new SubmitTask(FLUSH_EVENTS));
        }
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
        private final int messageType;

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
                } catch (OutOfMemoryError e) { //?
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
        private final String eventName;
        private final JSONObject properties;
        private final long time;

        public EventsQueueTask(String eventName, JSONObject properties) {
            this.eventName = eventName;
            this.properties = properties;
            this.time = System.currentTimeMillis() / 1000;
        }

        @Override
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

                for (Iterator<?> iter = mSuperProperties.keys(); iter.hasNext(); ) {
                    String key = (String) iter.next();
                    propertiesObj.put(key, mSuperProperties.get(key));
                }

                if (distinct_id != null) {
                    propertiesObj.put("distinct_id", distinct_id);
                }

                if (properties != null) {
                    for (Iterator<?> iter = properties.keys(); iter.hasNext();) {
                        String key = (String) iter.next();
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
        private final String actionType;
        private final JSONObject properties;

        public PeopleQueueTask(String actionType, JSONObject properties) {
            this.actionType = actionType;
            this.properties = properties;
        }

        @Override
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
                getPeople().flush();
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
