package com.mixpanel.android.mpmetrics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

public class MPMetrics {
    public static final String VERSION = "2.1";

    private static final String LOGTAG = "MPMetrics";

    // Maps each token to a singleton MPMetrics instance
    public static HashMap<String, MPMetrics> mInstanceMap = new HashMap<String, MPMetrics>();


    private final Context mContext;
    private final AnalyticsMessages mMessages;

    private final String mToken;
    private final String mCarrier;
    private final String mModel;
    private final String mVersion;
    private final String mDeviceId;
    private final PeopleImpl mPeople;


    private final SharedPreferences mStoredPreferences;

    // Persistent members. These are loaded and stored from our preferences.
    private JSONObject mSuperProperties;
    private String mEventsDistinctId;
    private String mPeopleDistinctId;
    private WaitingPeopleRecord mWaitingPeopleRecord;

    /**
     * You shouldn't instantiate MPMetrics objects directly.
     * Use MPMetrics.getInstance to get an instance.
     *
     * @param context
     * @param token
     */
    private MPMetrics(Context context, String token) {
        mContext = context;
        mToken = token;

        mMessages = AnalyticsMessages.getInstance(mContext);

        mCarrier = getCarrier();
        mModel = getModel();
        mVersion = getVersion();
        mDeviceId = getDeviceId();
        mPeople = new PeopleImpl();

        mStoredPreferences = context.getSharedPreferences("com.mixpanel.android.mpmetrics.MPMetrics", Context.MODE_PRIVATE);
        readSuperProperties();
        readIdentities();
    }

    /**
     * Use getInstance to get an instance of MPMetrics you can use to send events
     * and People Analytics updates to Mixpanel. You should call this method from
     * the UI thread of your application.
     *
     * @param context The application context you are tracking
     * @param token Your Mixpanel project token. You can get your project token on the Mixpanel web site,
     *     in the settings dialog.
     */
    public static MPMetrics getInstance(Context context, String token) {
        MPMetrics instance = mInstanceMap.get(token);
        if (instance == null) {
            instance = new MPMetrics(context.getApplicationContext(), token);
            mInstanceMap.put(token,  instance);
        }

        return instance;
    }

    /**
     * Register super properties for events. SuperProperties are a collection of properties
     * that will be sent with every event to Mixpanel, and persist beyond the lifetime of
     * your application.
     *
     * Setting a superProperty with registerSuperProperties will store a new superProperty,
     * possibly overwriting any existing superProperty with the same name (to set a
     * superProperty only if it is currently unset, use {@link registerSuperPropertiesOnce})
     *
     * SuperProperties will persist even if your application is taken completely out of memory.
     * to remove a superProperty, call {@link unregisterSuperProperties} or {@link clearSuperProperties}
     *
     * @param superProperties    A JSONObject containing super properties to register
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

        storeSuperProperties();
    }

    /**
     * Unregister a single superProperty. If there is a superProperty that with
     * the given name, it will be permanently removed from the existing superProperties.
     * To clear all superProperties, use {@link clearSuperProperties}
     *
     * @param superPropertyName
     */
    public void unregisterSuperProperty(String superPropertyName) {
        mSuperProperties.remove(superPropertyName);

        storeSuperProperties();
    }

    /**
     * Register super properties for events, only if no other super properties with the
     * same names are already registered. Calling registerSuperPropertiesOnce will
     * never overwrite existing properties.
     *
     * @param superProperties A JSONObject containing the super properties to register.
     */
    public void registerSuperPropertiesOnce(JSONObject superProperties) {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "registerSuperPropertiesOnce");

        for (Iterator<?> iter = superProperties.keys(); iter.hasNext(); ) {
            String key = (String) iter.next();
            if (! mSuperProperties.has(key)) {
                try {
                    mSuperProperties.put(key, superProperties.get(key));
                } catch (JSONException e) {
                    Log.e(LOGTAG, "Exception registering super property.", e);
                }
            }
        }// for

        storeSuperProperties();
    }

    /**
     * Clear all superProperties. Future tracking calls to Mixpanel (even those already queued up but not
     * yet sent to Mixpanel servers) will not be associated with the superProperties registered
     * before this call was made.
     *
     * To remove a single superProperty, use {@link unregisterSuperProperty}
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
     * @param distinctId a string uniquely identifying this user. Events sent to
     *     Mixpanel using the same disinct_id will be considered associated with the
     *     same visitor/customer for retention and funnel reporting, so be sure that the given
     *     value is globally unique for each individual user you intend to track.
     */
    public void identify(String distinctId) {
       mEventsDistinctId = distinctId;
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

        try {
            long time = System.currentTimeMillis() / 1000;
            JSONObject dataObj = new JSONObject();

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

            if (mEventsDistinctId != null) {
                propertiesObj.put("distinct_id", mEventsDistinctId);
            }

            if (properties != null) {
                for (Iterator<?> iter = properties.keys(); iter.hasNext();) {
                    String key = (String) iter.next();
                    propertiesObj.put(key, properties.get(key));
                }
            }

            dataObj.put("properties", propertiesObj);

            mMessages.eventsMessage(dataObj);
        } catch (JSONException e) {
            Log.e(LOGTAG, "Exception tracking event " + eventName, e);
        }
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
         * @param distinctId
         */
        public void identify(String distinctId);

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
        @Override
        public void identify(String distinctId) {
            mPeopleDistinctId = distinctId;

            if (mWaitingPeopleRecord != null)
                pushWaitingPeopleRecord();
         }

        @Override
        public void set(JSONObject properties) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "set " + properties.toString());

            try {
                if (mPeopleDistinctId == null) {
                    if (mWaitingPeopleRecord == null)
                        mWaitingPeopleRecord = new WaitingPeopleRecord();

                    mWaitingPeopleRecord.setOnWaitingPeopleRecord(properties);
                    writeIdentities();
                }
                else {
                    JSONObject message = stdPeopleMessage("$set", properties);
                    mMessages.peopleMessage(message);
                }
            } catch (JSONException e) {
                Log.e(LOGTAG, "Exception setting people properties");
            }
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
            try {
                if (mPeopleDistinctId == null) {
                    if (mWaitingPeopleRecord == null)
                        mWaitingPeopleRecord = new WaitingPeopleRecord();

                    mWaitingPeopleRecord.incrementToWaitingPeopleRecord(properties);
                }

                JSONObject message = stdPeopleMessage("$add", json);
                mMessages.peopleMessage(message);
            } catch (JSONException e) {
                Log.e(LOGTAG, "Exception incrementing properties", e);
            }
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
            if (mPeopleDistinctId == null) {
                return;
            }

            try {
                JSONObject message = stdPeopleMessage("$add", null);
                mMessages.peopleMessage(message);
            } catch (JSONException e) {
                Log.e(LOGTAG, "Exception deleting a user");
            }
        }

        @Override
        public void flush() {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "People.flush");
            mMessages.submitPeople();
        }

        @Override
        public void setPushRegistrationId(String registrationId) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "setting push registration id: " + registrationId);
            if (mPeopleDistinctId == null) {
                return;
            }

            mStoredPreferences.edit().putString("push_id", registrationId).commit();
            try {
                JSONObject registrationInfo = new JSONObject().put("$android_devices", new JSONArray("[" + registrationId + "]"));
                JSONObject message = stdPeopleMessage("$union", registrationInfo);
                mMessages.peopleMessage(message);
            } catch (JSONException e) {
                Log.e(LOGTAG, "set push registration id error", e);
            }
        }

        @Override
        public void removePushRegistrationId() {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "removing push registration id");

            mStoredPreferences.edit().remove("push_id").commit();
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
            return mStoredPreferences.getString("push_id", null);
        }

        public JSONObject stdPeopleMessage(String actionType, JSONObject properties)
                throws JSONException {
                JSONObject dataObj = new JSONObject();
                dataObj.put(actionType, properties);
                dataObj.put("$token", mToken);
                dataObj.put("$distinct_id", mPeopleDistinctId);
                dataObj.put("$time", System.currentTimeMillis());

                return dataObj;
        }
    }// PeopleImpl

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

    /**
     * Will push all queued Mixpanel events (but not changes to People Analytics records)
     * to Mixpanel servers. See also {@link People.flush} and {@link flushAll}
     * for other ways of ensuring no values are left in the queue.
     */
    public void flushEvents() {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "flushEvents");
        mMessages.submitEvents();
    }

    ////////////////////////////////////////////////////

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

    private void pushWaitingPeopleRecord() {
        if ((mWaitingPeopleRecord != null) && (mPeopleDistinctId != null)) {
           JSONObject sets = mWaitingPeopleRecord.setMessage();
           Map<String, Long> adds = mWaitingPeopleRecord.incrementMessage();

           getPeople().set(sets);
           getPeople().increment(adds);
        }

        mWaitingPeopleRecord = null;
        writeIdentities();
    }

    private void readSuperProperties() {
        String props = mStoredPreferences.getString("super_properties", "{}");
        if (MPConfig.DEBUG) Log.d(LOGTAG, "Loading Super Properties " + props);

        try {
            mSuperProperties = new JSONObject(props);
        } catch (JSONException e) {
            Log.e(LOGTAG, "Cannot parse stored superProperties");
            mSuperProperties = new JSONObject();
            storeSuperProperties();
        }
    }

    private void storeSuperProperties() {
        String props = mSuperProperties.toString();

        if (MPConfig.DEBUG) Log.d(LOGTAG, "Storing Super Properties " + props);
        SharedPreferences.Editor prefsEditor = mStoredPreferences.edit();
        prefsEditor.putString("super_properties", props);
        prefsEditor.commit();
    }

    private void readIdentities() {
        mEventsDistinctId = mStoredPreferences.getString("events_distinct_id", null);
        mPeopleDistinctId = mStoredPreferences.getString("people_distinct_id", null);
        mWaitingPeopleRecord = null;

        String storedWaitingRecord = mStoredPreferences.getString("waiting_people_record", null);
        if (storedWaitingRecord != null) {
            try {
                mWaitingPeopleRecord = new WaitingPeopleRecord();
                mWaitingPeopleRecord.readFromJSONString(storedWaitingRecord);
            } catch (JSONException e) {
                Log.e(LOGTAG, "Could not interpret waiting people JSON record " + storedWaitingRecord);
            }
        }

        if ((mWaitingPeopleRecord != null) && (mPeopleDistinctId != null)) {
            pushWaitingPeopleRecord();
        }
    }

    private void writeIdentities() {
        SharedPreferences.Editor prefsEditor = mStoredPreferences.edit();

        prefsEditor.putString("events_distinct_id", mEventsDistinctId);
        prefsEditor.putString("people_distinct_id", mPeopleDistinctId);
        if (mWaitingPeopleRecord == null) {
            prefsEditor.remove("waiting_people_record");
        }
        else {
            prefsEditor.putString("waiting_people_record", mWaitingPeopleRecord.toJSONString());
        }
        prefsEditor.commit();
    }
}
