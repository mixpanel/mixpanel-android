package com.mixpanel.android.mpmetrics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.SharedPreferences;
import android.util.Log;

/* package */ class PersistentProperties {

    // Will be called from crazy threads, BUT will be the only thread that has access to the given
    // SharedPreferences during the run.
    public static JSONArray waitingPeopleRecordsForSending(SharedPreferences storedPreferences) {
        JSONArray ret = null;
        final String peopleDistinctId = storedPreferences.getString("people_distinct_id", null);
        final String waitingPeopleRecords = storedPreferences.getString("waiting_array", null);
        if ((null != waitingPeopleRecords) && (null != peopleDistinctId)) {
            JSONArray waitingObjects = null;
            try {
                waitingObjects = new JSONArray(waitingPeopleRecords);
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Waiting people records were unreadable.");
                return null;
            }

            ret = new JSONArray();
            for (int i = 0; i < waitingObjects.length(); i++) {
                try {
                    final JSONObject ob = waitingObjects.getJSONObject(i);
                    ob.put("$distinct_id", peopleDistinctId);
                    ret.put(ob);
                } catch (final JSONException e) {
                    Log.e(LOGTAG, "Unparsable object found in waiting people records", e);
                }
            }

            final SharedPreferences.Editor editor = storedPreferences.edit();
            editor.remove("waiting_array");
            editor.commit();
        }
        return ret;
    }

    public PersistentProperties(Future<SharedPreferences> referrerPreferences, Future<SharedPreferences> storedPreferences) {
        mLoadReferrerPreferences = referrerPreferences;
        mLoadStoredPreferences = storedPreferences;
        mSuperPropertiesCache = null;
        mReferrerPropertiesCache = null;
        mIdentitiesLoaded = false;
        mReferrerChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
				readReferrerProperties();
			}
		};
    }

    public JSONObject getSuperProperties() {
        if (null == mSuperPropertiesCache) {
            readSuperProperties();
        }
        return mSuperPropertiesCache;
    }

    public Map<String, String> getReferrerProperties() {
        if (null == mReferrerPropertiesCache) {
            readReferrerProperties();
        }
        return mReferrerPropertiesCache;
    }

    public String getEventsDistinctId() {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        return mEventsDistinctId;
    }

    public void setEventsDistinctId(String eventsDistinctId) {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        mEventsDistinctId = eventsDistinctId;
        writeIdentities();
    }

    public String getPeopleDistinctId() {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        return mPeopleDistinctId;
    }

    public void setPeopleDistinctId(String peopleDistinctId) {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        mPeopleDistinctId = peopleDistinctId;
        writeIdentities();
    }

    public void storeWaitingPeopleRecord(JSONObject record) {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        if (null == mWaitingPeopleRecords) {
            mWaitingPeopleRecords = new JSONArray();
        }
        mWaitingPeopleRecords.put(record);
        writeIdentities();
    }

    public JSONArray waitingPeopleRecordsForSending() {
        JSONArray ret = null;
        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            ret = waitingPeopleRecordsForSending(prefs);
            readIdentities();
        } catch (final ExecutionException e) {
            Log.e(LOGTAG, "Couldn't read waiting people records from shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            Log.e(LOGTAG, "Couldn't read waiting people records from shared preferences.", e);
        }
        return ret;
    }

    public void clearPreferences() {
        // Will clear distinct_ids, superProperties,
        // and waiting People Analytics properties. Will have no effect
        // on messages already queued to send with AnalyticsMessages.

        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor prefsEdit = prefs.edit();
            prefsEdit.clear().commit();
            readSuperProperties();
            readIdentities();
        } catch (final ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    public void registerSuperProperties(JSONObject superProperties) {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "registerSuperProperties");
        final JSONObject propCache = getSuperProperties();

        for (final Iterator<?> iter = superProperties.keys(); iter.hasNext(); ) {
            final String key = (String) iter.next();
            try {
               propCache.put(key, superProperties.get(key));
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Exception registering super property.", e);
            }
        }

        storeSuperProperties();
    }

    public void storePushId(String registrationId) {
        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putString("push_id", registrationId);
            editor.commit();
        } catch (final ExecutionException e) {
            Log.e(LOGTAG, "Can't write push id to shared preferences", e.getCause());
        } catch (final InterruptedException e) {
            Log.e(LOGTAG, "Can't write push id to shared preferences", e);
        }
    }

    public void clearPushId() {
        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor editor = prefs.edit();
            editor.remove("push_id");
            editor.commit();
        } catch (final ExecutionException e) {
            Log.e(LOGTAG, "Can't write push id to shared preferences", e.getCause());
        } catch (final InterruptedException e) {
            Log.e(LOGTAG, "Can't write push id to shared preferences", e);
        }
    }

    public String getPushId() {
        String ret = null;
        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            ret = prefs.getString("push_id", null);
        } catch (final ExecutionException e) {
            Log.e(LOGTAG, "Can't write push id to shared preferences", e.getCause());
        } catch (final InterruptedException e) {
            Log.e(LOGTAG, "Can't write push id to shared preferences", e);
        }
        return ret;
    }

    public void unregisterSuperProperty(String superPropertyName) {
        final JSONObject propCache = getSuperProperties();
        propCache.remove(superPropertyName);

        storeSuperProperties();
    }

    public void registerSuperPropertiesOnce(JSONObject superProperties) {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "registerSuperPropertiesOnce");
        final JSONObject propCache = getSuperProperties();

        for (final Iterator<?> iter = superProperties.keys(); iter.hasNext(); ) {
            final String key = (String) iter.next();
            if (! propCache.has(key)) {
                try {
                    propCache.put(key, superProperties.get(key));
                } catch (final JSONException e) {
                    Log.e(LOGTAG, "Exception registering super property.", e);
                }
            }
        }// for

        storeSuperProperties();
    }

    public void clearSuperProperties() {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "clearSuperProperties");
        mSuperPropertiesCache = new JSONObject();
        storeSuperProperties();
    }

    //////////////////////////////////////////////////

    private void readSuperProperties() {
        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final String props = prefs.getString("super_properties", "{}");
            if (MPConfig.DEBUG) Log.d(LOGTAG, "Loading Super Properties " + props);
            mSuperPropertiesCache = new JSONObject(props);
        } catch (final ExecutionException e) {
            Log.e(LOGTAG, "Cannot load superProperties from SharedPreferences.", e.getCause());
        } catch (final InterruptedException e) {
            Log.e(LOGTAG, "Cannot load superProperties from SharedPreferences.", e);
        } catch (final JSONException e) {
            Log.e(LOGTAG, "Cannot parse stored superProperties");
            storeSuperProperties();
        } finally {
            if (null == mSuperPropertiesCache) {
                mSuperPropertiesCache = new JSONObject();
            }
        }
    }

    private void readReferrerProperties() {
        mReferrerPropertiesCache = new HashMap<String, String>();

        try {
            final SharedPreferences referrerPrefs = mLoadReferrerPreferences.get();
            referrerPrefs.unregisterOnSharedPreferenceChangeListener(mReferrerChangeListener);
            referrerPrefs.registerOnSharedPreferenceChangeListener(mReferrerChangeListener);

            final Map<String, ?> prefsMap = referrerPrefs.getAll();
            for (final Map.Entry<String, ?> entry:prefsMap.entrySet()) {
                final String prefsName = entry.getKey();
                final Object prefsVal = entry.getValue();
                mReferrerPropertiesCache.put(prefsName, prefsVal.toString());
            }
        } catch (final ExecutionException e) {
            Log.e(LOGTAG, "Cannot load referrer properties from shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            Log.e(LOGTAG, "Cannot load referrer properties from shared preferences.", e);
        }
    }

    private void storeSuperProperties() {
        if (null == mSuperPropertiesCache) {
            Log.e(LOGTAG, "storeSuperProperties should not be called with uninitialized superPropertiesCache.");
            return;
        }

        final String props = mSuperPropertiesCache.toString();
        if (MPConfig.DEBUG) Log.d(LOGTAG, "Storing Super Properties " + props);

        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putString("super_properties", props);
            editor.commit();
        } catch (final ExecutionException e) {
            Log.e(LOGTAG, "Cannot store superProperties in shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            Log.e(LOGTAG, "Cannot store superProperties in shared preferences.", e);
        }
    }

    private void readIdentities() {
        SharedPreferences prefs = null;
        try {
            prefs = mLoadStoredPreferences.get();
        } catch (final ExecutionException e) {
            Log.e(LOGTAG, "Cannot read distinct ids from sharedPreferences.", e.getCause());
        } catch (final InterruptedException e) {
            Log.e(LOGTAG, "Cannot read distinct ids from sharedPreferences.", e);
        }

        if (null == prefs) {
            return;
        }

        mEventsDistinctId = prefs.getString("events_distinct_id", null);
        mPeopleDistinctId = prefs.getString("people_distinct_id", null);
        mWaitingPeopleRecords = null;

        final String storedWaitingRecord = prefs.getString("waiting_array", null);
        if (storedWaitingRecord != null) {
            try {
                mWaitingPeopleRecords = new JSONArray(storedWaitingRecord);
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Could not interpret waiting people JSON record " + storedWaitingRecord);
            }
        }

        if (null == mEventsDistinctId) {
            mEventsDistinctId = UUID.randomUUID().toString();
            writeIdentities();
        }

        mIdentitiesLoaded = true;
    }

    private void writeIdentities() {
        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor prefsEditor = prefs.edit();

            prefsEditor.putString("events_distinct_id", mEventsDistinctId);
            prefsEditor.putString("people_distinct_id", mPeopleDistinctId);
            if (mWaitingPeopleRecords == null) {
                prefsEditor.remove("waiting_array");
            }
            else {
                prefsEditor.putString("waiting_array", mWaitingPeopleRecords.toString());
            }
            prefsEditor.commit();
        } catch (final ExecutionException e) {
            Log.e(LOGTAG, "Can't write distinct ids to shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            Log.e(LOGTAG, "Can't write distinct ids to shared preferences.", e);
        }
    }

    private final Future<SharedPreferences> mLoadStoredPreferences;
    private final Future<SharedPreferences> mLoadReferrerPreferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener mReferrerChangeListener;
    private JSONObject mSuperPropertiesCache;
    private Map<String, String> mReferrerPropertiesCache;
    private boolean mIdentitiesLoaded;
    private String mEventsDistinctId;
    private String mPeopleDistinctId;
    private JSONArray mWaitingPeopleRecords;

    private static final String LOGTAG = "MixpanelAPI PersistentProperties";
}
