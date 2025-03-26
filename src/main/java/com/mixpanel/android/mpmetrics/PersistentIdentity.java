package com.mixpanel.android.mpmetrics;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;

import com.mixpanel.android.util.MPLog;

// In order to use writeEdits, we have to suppress the linter's check for commit()/apply()
@SuppressLint("CommitPrefEdits")
        /* package */ class PersistentIdentity {
    // Should ONLY be called from an OnPrefsLoadedListener (since it should NEVER be called concurrently)
    public static String getPeopleDistinctId(SharedPreferences storedPreferences) {
        return storedPreferences.getString("people_distinct_id", null);
    }

    public static void writeReferrerPrefs(Context context, String preferencesName, Map<String, String> properties) {
        synchronized (sReferrerPrefsLock) {
            final SharedPreferences referralInfo = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
            final SharedPreferences.Editor editor = referralInfo.edit();
            editor.clear();
            for (final Map.Entry<String, String> entry : properties.entrySet()) {
                editor.putString(entry.getKey(), entry.getValue());
            }
            writeEdits(editor);
            sReferrerPrefsDirty = true;
        }
    }

    public PersistentIdentity(Future<SharedPreferences> referrerPreferences, Future<SharedPreferences> storedPreferences, Future<SharedPreferences> timeEventsPreferences, Future<SharedPreferences> mixpanelPreferences) {
        mLoadReferrerPreferences = referrerPreferences;
        mLoadStoredPreferences = storedPreferences;
        mTimeEventsPreferences = timeEventsPreferences;
        mMixpanelPreferences = mixpanelPreferences;
        mSuperPropertiesCache = null;
        mReferrerPropertiesCache = null;
        mIdentitiesLoaded = false;
        mReferrerChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                synchronized (sReferrerPrefsLock) {
                    readReferrerProperties();
                    sReferrerPrefsDirty = false;
                }
            }
        };

        // Preload time events in the background to avoid main thread disk reads
        preloadTimeEventsAsync();
    }

    // Super properties
    public void addSuperPropertiesToObject(JSONObject ob) {
        synchronized (mSuperPropsLock) {
            final JSONObject superProperties = this.getSuperPropertiesCache();
            final Iterator<?> superIter = superProperties.keys();
            while (superIter.hasNext()) {
                final String key = (String) superIter.next();

                try {
                    ob.put(key, superProperties.get(key));
                } catch (JSONException e) {
                    MPLog.e(LOGTAG, "Object read from one JSON Object cannot be written to another", e);
                }
            }
        }
    }

    public void updateSuperProperties(SuperPropertyUpdate updates) {
        synchronized (mSuperPropsLock) {
            final JSONObject oldPropCache = getSuperPropertiesCache();
            final JSONObject copy = new JSONObject();

            try {
                final Iterator<String> keys = oldPropCache.keys();
                while (keys.hasNext()) {
                    final String k = keys.next();
                    final Object v = oldPropCache.get(k);
                    copy.put(k, v);
                }
            } catch (JSONException e) {
                MPLog.e(LOGTAG, "Can't copy from one JSONObject to another", e);
                return;
            }

            final JSONObject replacementCache = updates.update(copy);
            if (replacementCache == null) {
                MPLog.w(LOGTAG, "An update to Mixpanel's super properties returned null, and will have no effect.");
                return;
            }

            mSuperPropertiesCache = replacementCache;
            storeSuperProperties();
        }
    }

    public void registerSuperProperties(JSONObject superProperties) {
        synchronized (mSuperPropsLock) {
            final JSONObject propCache = getSuperPropertiesCache();

            for (final Iterator<?> iter = superProperties.keys(); iter.hasNext(); ) {
                final String key = (String) iter.next();
                try {
                    propCache.put(key, superProperties.get(key));
                } catch (final JSONException e) {
                    MPLog.e(LOGTAG, "Exception registering super property.", e);
                }
            }

            storeSuperProperties();
        }
    }

    public void unregisterSuperProperty(String superPropertyName) {
        synchronized (mSuperPropsLock) {
            final JSONObject propCache = getSuperPropertiesCache();
            propCache.remove(superPropertyName);

            storeSuperProperties();
        }
    }

    public void registerSuperPropertiesOnce(JSONObject superProperties) {
        synchronized (mSuperPropsLock) {
            final JSONObject propCache = getSuperPropertiesCache();

            for (final Iterator<?> iter = superProperties.keys(); iter.hasNext(); ) {
                final String key = (String) iter.next();
                if (! propCache.has(key)) {
                    try {
                        propCache.put(key, superProperties.get(key));
                    } catch (final JSONException e) {
                        MPLog.e(LOGTAG, "Exception registering super property.", e);
                    }
                }
            }// for

            storeSuperProperties();
        }
    }

    public void clearSuperProperties() {
        synchronized (mSuperPropsLock) {
            mSuperPropertiesCache = new JSONObject();
            storeSuperProperties();
        }
    }

    public Map<String, String> getReferrerProperties() {
        synchronized (sReferrerPrefsLock) {
            if (sReferrerPrefsDirty || null == mReferrerPropertiesCache) {
                readReferrerProperties();
                sReferrerPrefsDirty = false;
            }
        }
        return mReferrerPropertiesCache;
    }

    public void clearReferrerProperties() {
        synchronized (sReferrerPrefsLock) {
            try {
                final SharedPreferences referrerPrefs = mLoadReferrerPreferences.get();
                final SharedPreferences.Editor prefsEdit = referrerPrefs.edit();
                prefsEdit.clear();
                writeEdits(prefsEdit);
            } catch (final ExecutionException e) {
                MPLog.e(LOGTAG, "Cannot load referrer properties from shared preferences.", e.getCause());
            } catch (final InterruptedException e) {
                MPLog.e(LOGTAG, "Cannot load referrer properties from shared preferences.", e);
            }
        }
    }

    public synchronized String getAnonymousId() {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        return mAnonymousId;
    }

    public synchronized boolean getHadPersistedDistinctId() {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        return mHadPersistedDistinctId;
    }

    public synchronized String getEventsDistinctId() {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        return mEventsDistinctId;
    }

    public synchronized String getEventsUserId() {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        if(mEventsUserIdPresent) {
            return mEventsDistinctId;
        }
        return null;
    }

    public synchronized void setAnonymousIdIfAbsent(String anonymousId) {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        if (mAnonymousId != null) {
            return;
        }
        mAnonymousId = anonymousId;
        mHadPersistedDistinctId = true;
        writeIdentities();
    }

    public synchronized void setEventsDistinctId(String eventsDistinctId) {
        if(!mIdentitiesLoaded) {
            readIdentities();
        }
        mEventsDistinctId = eventsDistinctId;
        writeIdentities();
    }

    public synchronized void markEventsUserIdPresent() {
        if(!mIdentitiesLoaded) {
            readIdentities();
        }
        mEventsUserIdPresent = true;
        writeIdentities();
    }

    public synchronized String getPeopleDistinctId() {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        return mPeopleDistinctId;
    }

    public synchronized void setPeopleDistinctId(String peopleDistinctId) {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        mPeopleDistinctId = peopleDistinctId;
        writeIdentities();
    }

    public synchronized void clearPreferences() {
        // Will clear distinct_ids, superProperties,
        // and waiting People Analytics properties. Will have no effect
        // on messages already queued to send with AnalyticsMessages.

        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor prefsEdit = prefs.edit();
            prefsEdit.clear();
            writeEdits(prefsEdit);
            readSuperProperties();
            readIdentities();
        } catch (final ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    public void clearTimedEvents() {
        try {
            final SharedPreferences prefs = mTimeEventsPreferences.get();
            final SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            writeEdits(editor);

            // Clear cache if initialized
            synchronized (mTimeEventsCacheLock) {
                if (mTimeEventsCache != null) {
                    mTimeEventsCache.clear();
                }
            }
        } catch (InterruptedException e) {
            MPLog.e(LOGTAG, "Failed to clear time events", e);
        } catch (ExecutionException e) {
            MPLog.e(LOGTAG, "Failed to clear time events", e.getCause());
        }
    }

    public Map<String, Long> getTimeEvents() {
        // First check if cache is already loaded
        synchronized (mTimeEventsCacheLock) {
            if (mTimeEventsCache != null) {
                return new HashMap<>(mTimeEventsCache);
            }
        }

        // Detect if we're on the main thread
        if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
            // Running on main thread - return empty map and load asynchronously
            final Map<String, Long> emptyMap = new HashMap<>();

            new Thread(() -> {
                loadTimeEventsCache();
            }).start();

            return emptyMap;
        } else {
            // Not on main thread - safe to load synchronously
            return loadTimeEventsCache();
        }
    }

    // Helper method to load time events
    private Map<String, Long> loadTimeEventsCache() {
        synchronized (mTimeEventsCacheLock) {
            if (mTimeEventsCache != null) {
                return new HashMap<>(mTimeEventsCache);
            }

            mTimeEventsCache = new HashMap<>();

            try {
                final SharedPreferences prefs = mTimeEventsPreferences.get();
                Map<String, ?> allEntries = prefs.getAll();
                for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                    mTimeEventsCache.put(entry.getKey(), Long.valueOf(entry.getValue().toString()));
                }
            } catch (InterruptedException e) {
                MPLog.e(LOGTAG, "Failed to load time events", e);
            } catch (ExecutionException e) {
                MPLog.e(LOGTAG, "Failed to load time events", e.getCause());
            }

            return new HashMap<>(mTimeEventsCache);
        }
    }

    // Method to explicitly preload the cache
    public void preloadTimeEventsAsync() {
        synchronized (mTimeEventsCacheLock) {
            if (mTimeEventsCache == null) {
                new Thread(() -> loadTimeEventsCache()).start();
            }
        }
    }

    // access is synchronized outside (mEventTimings)
    public void removeTimedEvent(String timeEventName) {
        try {
            final SharedPreferences prefs = mTimeEventsPreferences.get();
            final SharedPreferences.Editor editor = prefs.edit();
            editor.remove(timeEventName);
            writeEdits(editor);

            // Update cache if initialized
            synchronized (mTimeEventsCacheLock) {
                if (mTimeEventsCache != null) {
                    mTimeEventsCache.remove(timeEventName);
                }
            }
        } catch (InterruptedException e) {
            MPLog.e(LOGTAG, "Failed to remove time event", e);
        } catch (ExecutionException e) {
            MPLog.e(LOGTAG, "Failed to remove time event", e.getCause());
        }
    }

    // access is synchronized outside (mEventTimings)
    public void addTimeEvent(String timeEventName, Long timeEventTimestamp) {
        try {
            final SharedPreferences prefs = mTimeEventsPreferences.get();
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(timeEventName, timeEventTimestamp);
            writeEdits(editor);

            // Update cache if initialized
            synchronized (mTimeEventsCacheLock) {
                if (mTimeEventsCache != null) {
                    mTimeEventsCache.put(timeEventName, timeEventTimestamp);
                }
            }
        } catch (InterruptedException e) {
            MPLog.e(LOGTAG, "Failed to add time event", e);
        } catch (ExecutionException e) {
            MPLog.e(LOGTAG, "Failed to add time event", e.getCause());
        }
    }

    public synchronized boolean isFirstIntegration(String token) {
        boolean firstLaunch = false;
        try {
            SharedPreferences prefs = mMixpanelPreferences.get();
            firstLaunch = prefs.getBoolean(token, false);
        }  catch (final ExecutionException e) {
            MPLog.e(LOGTAG, "Couldn't read internal Mixpanel shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            MPLog.e(LOGTAG, "Couldn't read internal Mixpanel from shared preferences.", e);
        }
        return firstLaunch;
    }

    public synchronized void setIsIntegrated(String token) {
        try {
            SharedPreferences.Editor mixpanelEditor = mMixpanelPreferences.get().edit();
            mixpanelEditor.putBoolean(token, true);
            writeEdits(mixpanelEditor);
        } catch (ExecutionException e) {
            MPLog.e(LOGTAG, "Couldn't write internal Mixpanel shared preferences.", e.getCause());
        } catch (InterruptedException e) {
            MPLog.e(LOGTAG, "Couldn't write internal Mixpanel from shared preferences.", e);
        }
    }

    public synchronized boolean isNewVersion(String versionCode) {
        if (versionCode == null) {
            return false;
        }

        Integer version = Integer.valueOf(versionCode);
        try {
            if (sPreviousVersionCode == null) {
                SharedPreferences mixpanelPreferences = mMixpanelPreferences.get();
                sPreviousVersionCode = mixpanelPreferences.getInt("latest_version_code", -1);
                if (sPreviousVersionCode == -1) {
                    sPreviousVersionCode = version;
                    SharedPreferences.Editor mixpanelPreferencesEditor = mMixpanelPreferences.get().edit();
                    mixpanelPreferencesEditor.putInt("latest_version_code", version);
                    writeEdits(mixpanelPreferencesEditor);
                }
            }

            if (sPreviousVersionCode.intValue() < version.intValue()) {
                SharedPreferences.Editor mixpanelPreferencesEditor = mMixpanelPreferences.get().edit();
                mixpanelPreferencesEditor.putInt("latest_version_code", version);
                writeEdits(mixpanelPreferencesEditor);
                return true;
            }
        } catch (ExecutionException e) {
            MPLog.e(LOGTAG, "Couldn't write internal Mixpanel shared preferences.", e.getCause());
        } catch (InterruptedException e) {
            MPLog.e(LOGTAG, "Couldn't write internal Mixpanel from shared preferences.", e);
        }

        return false;
    }

    public synchronized boolean isFirstLaunch(boolean dbExists, String token) {
        if (sIsFirstAppLaunch == null) {
            try {
                SharedPreferences mixpanelPreferences = mMixpanelPreferences.get();
                boolean hasLaunched = mixpanelPreferences.getBoolean("has_launched_" + token, false);
                if (hasLaunched) {
                    sIsFirstAppLaunch = false;
                } else {
                    sIsFirstAppLaunch = !dbExists;
                    if (!sIsFirstAppLaunch) {
                        setHasLaunched(token);
                    }
                }
            } catch (ExecutionException e) {
                sIsFirstAppLaunch = false;
            } catch (InterruptedException e) {
                sIsFirstAppLaunch = false;
            }
        }

        return sIsFirstAppLaunch;
    }

    public synchronized void setHasLaunched(String token) {
        try {
            SharedPreferences.Editor mixpanelPreferencesEditor = mMixpanelPreferences.get().edit();
            mixpanelPreferencesEditor.putBoolean("has_launched_" + token, true);
            writeEdits(mixpanelPreferencesEditor);
        } catch (ExecutionException e) {
            MPLog.e(LOGTAG, "Couldn't write internal Mixpanel shared preferences.", e.getCause());
        } catch (InterruptedException e) {
            MPLog.e(LOGTAG, "Couldn't write internal Mixpanel shared preferences.", e);
        }
    }

    public synchronized void setOptOutTracking(boolean optOutTracking, String token) {
        mIsUserOptOut = optOutTracking;
        writeOptOutFlag(token);
    }

    public synchronized boolean getOptOutTracking(String token) {
        if (mIsUserOptOut == null) {
            readOptOutFlag(token);
            if (mIsUserOptOut == null) {
                mIsUserOptOut = false;
            }
        }

        return mIsUserOptOut;
    }

    //////////////////////////////////////////////////

    // Must be called from a synchronized setting
    private JSONObject getSuperPropertiesCache() {
        if (mSuperPropertiesCache == null) {
            readSuperProperties();
        }
        return mSuperPropertiesCache;
    }

    // All access should be synchronized on this
    private void readSuperProperties() {
        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final String props = prefs.getString("super_properties", "{}");
            MPLog.v(LOGTAG, "Loading Super Properties " + props);
            mSuperPropertiesCache = new JSONObject(props);
        } catch (final ExecutionException e) {
            MPLog.e(LOGTAG, "Cannot load superProperties from SharedPreferences.", e.getCause());
        } catch (final InterruptedException e) {
            MPLog.e(LOGTAG, "Cannot load superProperties from SharedPreferences.", e);
        } catch (final JSONException e) {
            MPLog.e(LOGTAG, "Cannot parse stored superProperties");
            storeSuperProperties();
        } finally {
            if (mSuperPropertiesCache == null) {
                mSuperPropertiesCache = new JSONObject();
            }
        }
    }

    // All access should be synchronized on this
    private void readReferrerProperties() {
        mReferrerPropertiesCache = new HashMap<String, String>();

        try {
            final SharedPreferences referrerPrefs = mLoadReferrerPreferences.get();
            referrerPrefs.unregisterOnSharedPreferenceChangeListener(mReferrerChangeListener);
            referrerPrefs.registerOnSharedPreferenceChangeListener(mReferrerChangeListener);

            final Map<String, ?> prefsMap = referrerPrefs.getAll();
            for (final Map.Entry<String, ?> entry : prefsMap.entrySet()) {
                final String prefsName = entry.getKey();
                final Object prefsVal = entry.getValue();
                mReferrerPropertiesCache.put(prefsName, prefsVal.toString());
            }
        } catch (final ExecutionException e) {
            MPLog.e(LOGTAG, "Cannot load referrer properties from shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            MPLog.e(LOGTAG, "Cannot load referrer properties from shared preferences.", e);
        }
    }

    // All access should be synchronized on this
    private void storeSuperProperties() {
        if (mSuperPropertiesCache == null) {
            MPLog.e(LOGTAG, "storeSuperProperties should not be called with uninitialized superPropertiesCache.");
            return;
        }

        final String props = mSuperPropertiesCache.toString();
        MPLog.v(LOGTAG, "Storing Super Properties " + props);

        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putString("super_properties", props);
            writeEdits(editor);
        } catch (final ExecutionException e) {
            MPLog.e(LOGTAG, "Cannot store superProperties in shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            MPLog.e(LOGTAG, "Cannot store superProperties in shared preferences.", e);
        }
    }

    // All access should be synchronized on this
    private void readIdentities() {
        SharedPreferences prefs = null;
        try {
            prefs = mLoadStoredPreferences.get();
        } catch (final ExecutionException e) {
            MPLog.e(LOGTAG, "Cannot read distinct ids from sharedPreferences.", e.getCause());
        } catch (final InterruptedException e) {
            MPLog.e(LOGTAG, "Cannot read distinct ids from sharedPreferences.", e);
        }

        if (prefs == null) {
            return;
        }

        mEventsDistinctId = prefs.getString("events_distinct_id", null);
        mEventsUserIdPresent = prefs.getBoolean("events_user_id_present", false);
        mPeopleDistinctId = prefs.getString("people_distinct_id", null);
        mAnonymousId = prefs.getString("anonymous_id", null);
        mHadPersistedDistinctId = prefs.getBoolean("had_persisted_distinct_id", false);

        if (mEventsDistinctId == null) {
            mAnonymousId = UUID.randomUUID().toString();
            mEventsDistinctId = "$device:" + mAnonymousId;
            mEventsUserIdPresent = false;
            writeIdentities();
        }
        mIdentitiesLoaded = true;
    }

    private void readOptOutFlag(String token) {
        SharedPreferences prefs = null;
        try {
            prefs = mMixpanelPreferences.get();
        } catch (final ExecutionException e) {
            MPLog.e(LOGTAG, "Cannot read opt out flag from sharedPreferences.", e.getCause());
        } catch (final InterruptedException e) {
            MPLog.e(LOGTAG, "Cannot read opt out flag from sharedPreferences.", e);
        }

        if (prefs == null) {
            return;
        }
        mIsUserOptOut = prefs.getBoolean("opt_out_" + token, false);
    }

    private void writeOptOutFlag(String token) {
        try {
            final SharedPreferences prefs = mMixpanelPreferences.get();
            final SharedPreferences.Editor prefsEditor = prefs.edit();
            prefsEditor.putBoolean("opt_out_" + token, mIsUserOptOut);
            writeEdits(prefsEditor);
        } catch (final ExecutionException e) {
            MPLog.e(LOGTAG, "Can't write opt-out shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            MPLog.e(LOGTAG, "Can't write opt-out shared preferences.", e);
        }
    }

    protected void removeOptOutFlag(String token) {
        try {
            final SharedPreferences prefs = mMixpanelPreferences.get();
            final SharedPreferences.Editor prefsEditor = prefs.edit();
            prefsEditor.clear();
            writeEdits(prefsEditor);
        } catch (final ExecutionException e) {
            MPLog.e(LOGTAG, "Can't remove opt-out shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            MPLog.e(LOGTAG, "Can't remove opt-out shared preferences.", e);
        }
    }

    protected boolean hasOptOutFlag(String token) {
        try {
            final SharedPreferences prefs = mMixpanelPreferences.get();
            return prefs.contains("opt_out_" + token);
        } catch (final ExecutionException e) {
            MPLog.e(LOGTAG, "Can't read opt-out shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            MPLog.e(LOGTAG, "Can't read opt-out shared preferences.", e);
        }
        return false;
    }
    // All access should be synchronized on this
    private void writeIdentities() {
        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor prefsEditor = prefs.edit();

            prefsEditor.putString("events_distinct_id", mEventsDistinctId);
            prefsEditor.putBoolean("events_user_id_present", mEventsUserIdPresent);
            prefsEditor.putString("people_distinct_id", mPeopleDistinctId);
            prefsEditor.putString("anonymous_id", mAnonymousId);
            prefsEditor.putBoolean("had_persisted_distinct_id", mHadPersistedDistinctId);
            writeEdits(prefsEditor);
        } catch (final ExecutionException e) {
            MPLog.e(LOGTAG, "Can't write distinct ids to shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            MPLog.e(LOGTAG, "Can't write distinct ids to shared preferences.", e);
        }
    }

    private static void writeEdits(final SharedPreferences.Editor editor) {
        editor.apply();
    }

    private final Future<SharedPreferences> mLoadStoredPreferences;
    private final Future<SharedPreferences> mLoadReferrerPreferences;
    private final Future<SharedPreferences> mTimeEventsPreferences;
    private final Future<SharedPreferences> mMixpanelPreferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener mReferrerChangeListener;
    private JSONObject mSuperPropertiesCache;
    private final Object mSuperPropsLock = new Object();
    private Map<String, String> mReferrerPropertiesCache;
    private boolean mIdentitiesLoaded;
    private String mEventsDistinctId;
    private boolean mEventsUserIdPresent;
    private String mPeopleDistinctId;
    private String mAnonymousId;
    private boolean mHadPersistedDistinctId;
    private Boolean mIsUserOptOut;
    private static Integer sPreviousVersionCode;
    private static Boolean sIsFirstAppLaunch;

    // Time events caching
    private Map<String, Long> mTimeEventsCache = null;
    private final Object mTimeEventsCacheLock = new Object();

    private static boolean sReferrerPrefsDirty = true;
    private static final Object sReferrerPrefsLock = new Object();
    private static final String DELIMITER = ",";
    private static final String LOGTAG = "MixpanelAPI.PIdentity";
}