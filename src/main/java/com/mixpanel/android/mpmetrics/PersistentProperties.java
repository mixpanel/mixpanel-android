package com.mixpanel.android.mpmetrics;

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

    public PersistentProperties(Future<SharedPreferences> referrerPreferences, Future<SharedPreferences> storedPreferences) {
        mLoadReferrerPreferences = referrerPreferences;
        mLoadStoredPreferences = storedPreferences;
        mSuperPropertiesCache = null;
    }

    public Future<SharedPreferences> getLoadStoredPreferences() {
        return mLoadStoredPreferences;
    }

    public Future<SharedPreferences> getLoadReferrerPreferences() {
        return mLoadReferrerPreferences;
    }

    public JSONObject getSuperProperties() {
        if (null == mSuperPropertiesCache) {
            readSuperProperties();
        }
        return mSuperPropertiesCache;
    }

    public Map<String, String> getReferrerProperties() {
        return mReferrerProperties;
    }

    public String getEventsDistinctId() {
        return mEventsDistinctId;
    }

    public void setEventsDistinctId(String mEventsDistinctId) {
        this.mEventsDistinctId = mEventsDistinctId;
        writeIdentities();
    }

    public String getPeopleDistinctId() {
        return mPeopleDistinctId;
    }

    public void setPeopleDistinctId(String mPeopleDistinctId) {
        this.mPeopleDistinctId = mPeopleDistinctId;
        writeIdentities();
    }

    public void storeWaitingPeopleRecord(JSONObject record) {
        if (null == mWaitingPeopleRecords) {
            mWaitingPeopleRecords = new JSONArray();
        }
        mWaitingPeopleRecords.put(record);
        writeIdentities();
    }

    public JSONArray getWaitingPeopleRecords() {
        return mWaitingPeopleRecords;
    }

    public void clearWaitingPeopleRecords() {
        mWaitingPeopleRecords = null;
        writeIdentities();
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

    public void readSuperProperties() {
        try {
            final SharedPreferences prefs = getLoadStoredPreferences().get();
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

    public void storeSuperProperties() {
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

    public void readIdentities() {
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

        final String storedWaitingRecord = prefs.getString("waiting_people_record", null);
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
    }

    public void writeIdentities() {
        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor prefsEditor = prefs.edit();

            prefsEditor.putString("events_distinct_id", mEventsDistinctId);
            prefsEditor.putString("people_distinct_id", mPeopleDistinctId);
            if (mWaitingPeopleRecords == null) {
                prefsEditor.remove("waiting_people_record");
            }
            else {
                prefsEditor.putString("waiting_people_record", mWaitingPeopleRecords.toString());
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
    private JSONObject mSuperPropertiesCache;
    private Map<String, String> mReferrerProperties;
    private boolean mIdentitiesLoaded;
    private String mEventsDistinctId;
    private String mPeopleDistinctId;
    private JSONArray mWaitingPeopleRecords;

    private static final String LOGTAG = "MixpanelAPI PersistentProperties";
}