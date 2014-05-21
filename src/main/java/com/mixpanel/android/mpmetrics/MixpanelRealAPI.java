package com.mixpanel.android.mpmetrics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import com.mixpanel.android.R;
import com.mixpanel.android.surveys.SurveyActivity;
import com.mixpanel.android.util.ActivityImageUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

final class MixpanelRealAPI extends MixpanelAPI {
    /**
     * Use MixpanelAPI.getInstance to get an instance.
     */
    MixpanelRealAPI(Context context, Future<SharedPreferences> referrerPreferences, String token) {
        mContext = context;
        mToken = token;
        mPeople = new PeopleImpl();
        mMessages = getAnalyticsMessages();
        mConfig = getConfig();
        mPersistentIdentity = getPersistentIdentity(context, referrerPreferences, token);

        mUpdatesListener = new UpdatesListener();
        mDecideUpdates = null;

        // TODO this immediately forces the lazy load of the preferences, and defeats the
        // purpose of PersistentIdentity's laziness.
        final String peopleId = mPersistentIdentity.getPeopleDistinctId();
        if (null != peopleId) {
            mDecideUpdates = constructDecideUpdates(token, peopleId, mUpdatesListener);
        }

        registerMixpanelActivityLifecycleCallbacks();

        if (null != mDecideUpdates) {
            mMessages.installDecideCheck(mDecideUpdates);
        }
    }

    @Override
    public void alias(String alias, String original) {
        if (original == null) {
            original = getDistinctId();
        }
        if (alias.equals(original)) {
            Log.w(LOGTAG, "Attempted to alias identical distinct_ids " + alias + ", returning.");
            return;
        }

        try {
            JSONObject j = new JSONObject();
            j.put("alias", alias);
            j.put("original", original);
            track("$create_alias", j);
        } catch (JSONException e) {
            Log.e(LOGTAG, "Failed to alias", e);
        }
        flush();
    }

    @Override
    public void identify(String distinctId) {
       mPersistentIdentity.setEventsDistinctId(distinctId);
    }

    // DO NOT DOCUMENT, but track() must be thread safe since it is used to track events in
    // notifications from the UI thread, which might not be our MixpanelAPI "home" thread.
    // This MAY CHANGE IN FUTURE RELEASES, so minimize code that assumes thread safety
    // (and perhaps document that code here).
    @Override
    public void track(String eventName, JSONObject properties) {
        try {
            final JSONObject messageProps = new JSONObject();

            final Map<String, String> referrerProperties = mPersistentIdentity.getReferrerProperties();
            for (final Map.Entry<String, String> entry:referrerProperties.entrySet()) {
                final String key = entry.getKey();
                final String value = entry.getValue();
                messageProps.put(key, value);
            }

            final JSONObject superProperties = mPersistentIdentity.getSuperProperties();
            final Iterator<?> superIter = superProperties.keys();
            while (superIter.hasNext()) {
                final String key = (String) superIter.next();
                messageProps.put(key, superProperties.get(key));
            }

            // Don't allow super properties or referral properties to override these fields,
            // but DO allow the caller to override them in their given properties.
            final long time = System.currentTimeMillis() / 1000;
            messageProps.put("time", time);
            messageProps.put("distinct_id", getDistinctId());

            if (null != properties) {
                final Iterator<?> propIter = properties.keys();
                while (propIter.hasNext()) {
                    final String key = (String) propIter.next();
                    messageProps.put(key, properties.get(key));
                }
            }

            final AnalyticsMessages.EventDescription eventDescription =
                    new AnalyticsMessages.EventDescription(eventName, messageProps, mToken);
            mMessages.eventsMessage(eventDescription);
        } catch (final JSONException e) {
            Log.e(LOGTAG, "Exception tracking event " + eventName, e);
        }
    }

    @Override
    public void flush() {
        mMessages.postToServer();
    }

    @Override
    public String getDistinctId() {
        return mPersistentIdentity.getEventsDistinctId();
     }

    @Override
    public void registerSuperProperties(JSONObject superProperties) {
        mPersistentIdentity.registerSuperProperties(superProperties);
    }

    @Override
    public void unregisterSuperProperty(String superPropertyName) {
        mPersistentIdentity.unregisterSuperProperty(superPropertyName);
    }

    @Override
    public void registerSuperPropertiesOnce(JSONObject superProperties) {
        mPersistentIdentity.registerSuperPropertiesOnce(superProperties);
    }

    @Override
    public void clearSuperProperties() {
        mPersistentIdentity.clearSuperProperties();
    }

    @Override
    public People getPeople() {
        return mPeople;
    }

    /**
     * Attempt to register MixpanelActivityLifecycleCallbacks to the application's event lifecycle.
     * Once registered, we can automatically check for and show surveys and in app notifications
     * when any Activity is opened.
     *
     * This is only available if the android version is >= 14. You can disable this by setting
     * com.mixpanel.android.MPConfig.AutoShowMixpanelUpdates to false in your AndroidManifest.xml
     *
     * This function is automatically called when the library is initialized unless you explicitly
     * set com.mixpanel.android.MPConfig.AutoShowMixpanelUpdates to false in your AndroidManifest.xml
     */
    /* package */
    @TargetApi(14)
    void registerMixpanelActivityLifecycleCallbacks() {
        if (android.os.Build.VERSION.SDK_INT >= 14 && mConfig.getAutoShowMixpanelUpdates()) {
            if (mContext.getApplicationContext() instanceof Application) {
                final Application app = (Application) mContext.getApplicationContext();
                app.registerActivityLifecycleCallbacks((new MixpanelActivityLifecycleCallbacks(this)));
            } else {
                if (MPConfig.DEBUG) Log.d(LOGTAG, "Context is NOT instanceof Application, AutoShowMixpanelUpdates will be disabled.");
            }
        }
    }

    ////////////////////////////////////////////////////////////////////
    // Conveniences for testing. These methods should not be called by
    // non-test client code.

    /* package */ AnalyticsMessages getAnalyticsMessages() {
        return AnalyticsMessages.getInstance(mContext);
    }

    /* package */ MPConfig getConfig() {
        return MPConfig.getInstance(mContext);
    }

    /* package */ PersistentIdentity getPersistentIdentity(final Context context, Future<SharedPreferences> referrerPreferences, final String token) {
        final SharedPreferencesLoader.OnPrefsLoadedListener listener = new SharedPreferencesLoader.OnPrefsLoadedListener() {
            @Override
            public void onPrefsLoaded(SharedPreferences preferences) {
                final JSONArray records = PersistentIdentity.waitingPeopleRecordsForSending(preferences);
                if (null != records) {
                    sendAllPeopleRecords(records);
                }
            }
        };

        final String prefsName = "com.mixpanel.android.mpmetrics.MixpanelAPI_" + token;
        final Future<SharedPreferences> storedPreferences = sPrefsLoader.loadPreferences(context, prefsName, listener);
        return new PersistentIdentity(referrerPreferences, storedPreferences);
    }

    /* package */ DecideUpdates constructDecideUpdates(final String token, final String peopleId, final DecideUpdates.OnNewResultsListener listener) {
        return new DecideUpdates(token, peopleId, listener);
    }

    /* package */ void clearPreferences() {
        // Will clear distinct_ids, superProperties,
        // and waiting People Analytics properties. Will have no effect
        // on messages already queued to send with AnalyticsMessages.
        mPersistentIdentity.clearPreferences();
    }

    ///////////////////////

    private class PeopleImpl implements People {
        @Override
        public void identify(String distinctId) {
            mPersistentIdentity.setPeopleDistinctId(distinctId);
            if (null != mDecideUpdates && !mDecideUpdates.getDistinctId().equals(distinctId)) {
                mDecideUpdates.destroy();
                mDecideUpdates = null;
            }

            if (null == mDecideUpdates) {
                mDecideUpdates = constructDecideUpdates(mToken, distinctId, mUpdatesListener);
                mMessages.installDecideCheck(mDecideUpdates);
            }
            pushWaitingPeopleRecord();
         }

        @Override
        public void set(JSONObject properties) {
            try {
                final JSONObject sendProperties = new JSONObject();
                sendProperties.put("$android_lib_version", MPConfig.VERSION);
                sendProperties.put("$android_os", "Android");
                sendProperties.put("$android_os_version", Build.VERSION.RELEASE == null ? "UNKNOWN" : Build.VERSION.RELEASE);
                try {
                    PackageManager manager = mContext.getPackageManager();
                    PackageInfo info = manager.getPackageInfo(mContext.getPackageName(), 0);
                    sendProperties.put("$android_app_version", info.versionName);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(LOGTAG, "Exception getting app version name", e);
                }
                sendProperties.put("$android_manufacturer", Build.MANUFACTURER == null ? "UNKNOWN" : Build.MANUFACTURER);
                sendProperties.put("$android_brand", Build.BRAND == null ? "UNKNOWN" : Build.BRAND);
                sendProperties.put("$android_model", Build.MODEL == null ? "UNKNOWN" : Build.MODEL);

                for (final Iterator<?> iter = properties.keys(); iter.hasNext();) {
                    final String key = (String) iter.next();
                    sendProperties.put(key, properties.get(key));
                }

                final JSONObject message = stdPeopleMessage("$set", sendProperties);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Exception setting people properties", e);
            }
        }

        @Override
        public void set(String property, Object value) {
            try {
                set(new JSONObject().put(property, value));
            } catch (final JSONException e) {
                Log.e(LOGTAG, "set", e);
            }
        }

        @Override
        public void setOnce(JSONObject properties) {
            try {
                final JSONObject message = stdPeopleMessage("$set_once", properties);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Exception setting people properties");
            }
        }

        @Override
        public void setOnce(String property, Object value) {
            try {
                setOnce(new JSONObject().put(property, value));
            } catch (final JSONException e) {
                Log.e(LOGTAG, "set", e);
            }
        }

        @Override
        public void increment(Map<String, ? extends Number> properties) {
            final JSONObject json = new JSONObject(properties);
            try {
                final JSONObject message = stdPeopleMessage("$add", json);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Exception incrementing properties", e);
            }
        }

        @Override
        public void increment(String property, double value) {
            final Map<String, Double> map = new HashMap<String, Double>();
            map.put(property, value);
            increment(map);
        }

        @Override
        public void append(String name, Object value) {
            try {
                final JSONObject properties = new JSONObject();
                properties.put(name, value);
                final JSONObject message = stdPeopleMessage("$append", properties);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Exception appending a property", e);
            }
        }

        @Override
        public void union(String name, JSONArray value) {
            try {
                final JSONObject properties = new JSONObject();
                properties.put(name, value);
                final JSONObject message = stdPeopleMessage("$union", properties);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Exception unioning a property");
            }
        }

        @Override
        public void unset(String name) {
            try {
                final JSONArray names = new JSONArray();
                names.put(name);
                final JSONObject message = stdPeopleMessage("$unset", names);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Exception unsetting a property", e);
            }
        }

        @Override
        @Deprecated
        public void checkForSurvey(final SurveyCallbacks callbacks) {
            if (null == callbacks) {
                Log.i(LOGTAG, "Skipping survey check because callback is null.");
                return;
            }

            final Survey found = getSurveyIfAvailable();
            callbacks.foundSurvey(found);
        }

        @Override
        @Deprecated
        public void checkForSurvey(final SurveyCallbacks callbacks, final Activity parentActivity) {
            // Originally this call pre-computed UI chrome while it was waiting for the check to run.
            // Since modern checks run asynchronously, it's useless nowdays.
            checkForSurvey(callbacks);
        }

        @Override
        public InAppNotification getNotificationIfAvailable() {
            if (null == getDistinctId()) {
                return null;
            }
            return mDecideUpdates.getNotification(mConfig.getTestMode());
        }

        @Override
        public Survey getSurveyIfAvailable() {
            if (null == getDistinctId()) {
                return null;
            }
            return mDecideUpdates.getSurvey(mConfig.getTestMode());
        }

        @Override
        @Deprecated
        public void showSurvey(final Survey survey, final Activity parent) {
            showGivenOrAvailableSurvey(survey, parent);
        }

        @Override
        public void showSurveyIfAvailable(final Activity parent) {
            if (Build.VERSION.SDK_INT < 14) {
                return;
            }

            showGivenOrAvailableSurvey(null, parent);
        }

        @Override
        public void showSurveyById(int id, final Activity parent) {
            Survey s = mDecideUpdates.getSurvey(id, mConfig.getTestMode());
            if (s != null) {
                showGivenOrAvailableSurvey(s, parent);
            }
        }

        @Override
        public void showNotificationIfAvailable(final Activity parent) {
            if (Build.VERSION.SDK_INT < 14) {
                return;
            }

            showGivenOrAvailableNotification(null, parent);
        }

        @Override
        public void showNotificationById(int id, final Activity parent) {
            InAppNotification notif = mDecideUpdates.getNotification(id, mConfig.getTestMode());
            if (notif != null) {
                showGivenOrAvailableNotification(notif, parent);
            }
        }

        @Override
        public void trackCharge(double amount, JSONObject properties) {
            final Date now = new Date();
            final DateFormat dateFormat = new SimpleDateFormat(ENGAGE_DATE_FORMAT_STRING);
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            try {
                final JSONObject transactionValue = new JSONObject();
                transactionValue.put("$amount", amount);
                transactionValue.put("$time", dateFormat.format(now));

                if (null != properties) {
                    for (final Iterator<?> iter = properties.keys(); iter.hasNext();) {
                        final String key = (String) iter.next();
                        transactionValue.put(key, properties.get(key));
                    }
                }

                this.append("$transactions", transactionValue);
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Exception creating new charge", e);
            }
        }

        /**
         * Permanently clear the whole transaction history for the identified people profile.
         */
        @Override
        public void clearCharges() {
            this.unset("$transactions");
        }

        @Override
        public void deleteUser() {
            try {
                final JSONObject message = stdPeopleMessage("$delete", JSONObject.NULL);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Exception deleting a user");
            }
        }

        @Override
        public void setPushRegistrationId(String registrationId) {
            if (getDistinctId() == null) {
                return;
            }
            mPersistentIdentity.storePushId(registrationId);
            try {
                union("$android_devices", new JSONArray("[" + registrationId + "]"));
            } catch (final JSONException e) {
                Log.e(LOGTAG, "set push registration id error", e);
            }
        }

        @Override
        public void clearPushRegistrationId() {
            mPersistentIdentity.clearPushId();
            set("$android_devices", new JSONArray());
        }

        @Override
        public void initPushHandling(String senderID) {
            if (! ConfigurationChecker.checkPushConfiguration(mContext) ) {
                Log.i(LOGTAG, "Can't start push notification service. Push notifications will not work.");
                Log.i(LOGTAG, "See log tagged " + ConfigurationChecker.LOGTAG + " above for details.");
            }
            else { // Configuration is good for push notifications
                final String pushId = mPersistentIdentity.getPushId();
                if (pushId == null) {
                    if (MPConfig.DEBUG) Log.d(LOGTAG, "Registering a new push id");

                    try {
                        final Intent registrationIntent = new Intent("com.google.android.c2dm.intent.REGISTER");
                        registrationIntent.putExtra("app", PendingIntent.getBroadcast(mContext, 0, new Intent(), 0)); // boilerplate
                        registrationIntent.putExtra("sender", senderID);
                        mContext.startService(registrationIntent);
                    } catch (final SecurityException e) {
                        Log.w(LOGTAG, e);
                    }
                } else {
                    MixpanelAPI.allInstances(new InstanceProcessor() {
                        @Override
                        public void process(MixpanelAPI api) {
                            if (MPConfig.DEBUG) Log.d(LOGTAG, "Using existing pushId " + pushId);
                            api.getPeople().setPushRegistrationId(pushId);
                        }
                    });
                }
            }// endelse
        }

        @Override
        public String getDistinctId() {
            return mPersistentIdentity.getPeopleDistinctId();
        }

        @Override
        public People withIdentity(final String distinctId) {
            if (null == distinctId) {
                return null;
            }
            return new PeopleImpl() {
                @Override
                public String getDistinctId() {
                    return distinctId;
                }

                @Override
                public void identify(String distinctId) {
                    throw new RuntimeException("This MixpanelPeople object has a fixed, constant distinctId");
                }
            };
        }

        @Override
        public void addOnMixpanelUpdatesReceivedListener(final OnMixpanelUpdatesReceivedListener listener) {
            mUpdatesListener.addOnMixpanelUpdatesReceivedListener(listener);
        }

        @Override
        public void removeOnMixpanelUpdatesReceivedListener(final OnMixpanelUpdatesReceivedListener listener) {
            mUpdatesListener.removeOnMixpanelUpdatesReceivedListener(listener);
        }

        public JSONObject stdPeopleMessage(String actionType, Object properties)
                throws JSONException {
                final JSONObject dataObj = new JSONObject();
                final String distinctId = getDistinctId();

                dataObj.put(actionType, properties);
                dataObj.put("$token", mToken);
                dataObj.put("$time", System.currentTimeMillis());

                if (null != distinctId) {
                    dataObj.put("$distinct_id", getDistinctId());
                }

                return dataObj;
        }

        private void showGivenOrAvailableSurvey(final Survey surveyOrNull, final Activity parent) {
            // Showing surveys is not supported before Ice Cream Sandwich
            if (Build.VERSION.SDK_INT < 14) {
                return;
            }

            if (! ConfigurationChecker.checkSurveyActivityAvailable(parent.getApplicationContext())) {
                return;
            }

            BackgroundCapture.OnBackgroundCapturedListener listener = null;
            final ReentrantLock lock = UpdateDisplayState.getLockObject();
            lock.lock();
            try {
                if (UpdateDisplayState.hasCurrentProposal()) {
                    return; // Already being used.
                }
                Survey toShow = surveyOrNull;
                if (null == toShow) {
                    toShow = getSurveyIfAvailable();
                }
                if (null == toShow) {
                    return; // Nothing to show
                }

                final UpdateDisplayState.DisplayState.SurveyState surveyDisplay =
                        new UpdateDisplayState.DisplayState.SurveyState(toShow);

                final int intentId = UpdateDisplayState.proposeDisplay(surveyDisplay, getDistinctId(), mToken);
                assert intentId > 0; // Since we hold the lock, and !hasCurrentProposal

                listener = new BackgroundCapture.OnBackgroundCapturedListener() {
                    @Override
                    public void onBackgroundCaptured(Bitmap bitmapCaptured, int highlightColorCaptured) {
                        surveyDisplay.setBackground(bitmapCaptured);
                        surveyDisplay.setHighlightColor(highlightColorCaptured);

                        final Intent surveyIntent = new Intent(parent.getApplicationContext(), SurveyActivity.class);
                        surveyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        surveyIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        surveyIntent.putExtra(SurveyActivity.INTENT_ID_KEY, intentId);
                        parent.startActivity(surveyIntent);
                    }
                };
            } finally {
                lock.unlock();
            }

            assert listener != null;
            BackgroundCapture.captureBackground(parent, listener);
        }

        private void showGivenOrAvailableNotification(final InAppNotification notifOrNull, final Activity parent) {
            if (Build.VERSION.SDK_INT < 14) {
                return;
            }

            parent.runOnUiThread(new Runnable() {
                @Override
                @TargetApi(14)
                public void run() {
                    final ReentrantLock lock = UpdateDisplayState.getLockObject();
                    lock.lock();
                    try {
                        if (UpdateDisplayState.hasCurrentProposal()) {
                            return; // Already being used.
                        }

                        InAppNotification toShow = notifOrNull;
                        if (null == toShow) {
                            toShow = getNotificationIfAvailable();
                        }
                        if (null == toShow) {
                            return; // Nothing to show
                        }

                        final InAppNotification.Type inAppType = toShow.getType();
                        if (inAppType == InAppNotification.Type.TAKEOVER && ! ConfigurationChecker.checkSurveyActivityAvailable(parent.getApplicationContext())) {
                            return; // Can't show due to config.
                        }

                        final int highlightColor = ActivityImageUtils.getHighlightColorFromBackground(parent);
                        final UpdateDisplayState.DisplayState.InAppNotificationState proposal =
                                new UpdateDisplayState.DisplayState.InAppNotificationState(toShow, highlightColor);
                        final int intentId = UpdateDisplayState.proposeDisplay(proposal, getDistinctId(), mToken);
                        assert intentId > 0; // Since we're holding the lock and !hasCurrentProposal

                        switch (inAppType) {
                            case MINI: {
                                final UpdateDisplayState claimed = UpdateDisplayState.claimDisplayState(intentId);
                                InAppFragment inapp = new InAppFragment();
                                inapp.setDisplayState(intentId, (UpdateDisplayState.DisplayState.InAppNotificationState) claimed.getDisplayState());
                                inapp.setRetainInstance(true);
                                FragmentTransaction transaction = parent.getFragmentManager().beginTransaction();
                                transaction.setCustomAnimations(0, R.anim.com_mixpanel_android_slide_down);
                                transaction.add(android.R.id.content, inapp);
                                transaction.commit();
                            }
                            break;
                            case TAKEOVER: {
                                final Intent intent = new Intent(parent.getApplicationContext(), SurveyActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                                intent.putExtra(SurveyActivity.INTENT_ID_KEY, intentId);
                                parent.startActivity(intent);
                            }
                            break;
                            default:
                                Log.e(LOGTAG, "Unrecognized notification type " + inAppType + " can't be shown");
                        }
                        if (!mConfig.getTestMode()) {
                            trackNotificationSeen(toShow);
                        }
                    } finally {
                        lock.unlock();
                    }
                } // run()

                private void trackNotificationSeen(InAppNotification notif) {
                    track("$campaign_delivery", notif.getCampaignProperties());

                    final MixpanelAPI.People people = getPeople().withIdentity(getDistinctId());
                    final DateFormat dateFormat = new SimpleDateFormat(ENGAGE_DATE_FORMAT_STRING);
                    final JSONObject notifProperties = notif.getCampaignProperties();
                    try {
                        notifProperties.put("$time", dateFormat.format(new Date()));
                    } catch (JSONException e) {
                        Log.e(LOGTAG, "Exception trying to track an in app notification seen", e);
                    }
                    people.append("$campaigns", notif.getId());
                    people.append("$notifications", notifProperties);
                }
            });
        }
    }// PeopleImpl

    private class UpdatesListener implements DecideUpdates.OnNewResultsListener, Runnable {
        @Override
        public void onNewResults(final String distinctId) {
            mExecutor.execute(this);
        }

        public synchronized void addOnMixpanelUpdatesReceivedListener(OnMixpanelUpdatesReceivedListener listener) {
            // Workaround for a race between checking for updates using getSurveyIfAvailable() and getNotificationIfAvailable()
            // and registering a listener.
            synchronized (mDecideUpdates) {
                if (mDecideUpdates.hasUpdatesAvailable()) {
                    onNewResults(mDecideUpdates.getDistinctId());
                }
            }

            mListeners.add(listener);
        }

        public synchronized void removeOnMixpanelUpdatesReceivedListener(OnMixpanelUpdatesReceivedListener listener) {
            mListeners.remove(listener);
        }

        public synchronized void run() {
            // It's possible that by the time this has run the updates we detected are no longer
            // present, which is ok.
            Log.e(LOGTAG, "UPDATE RECIEVED, INFORMING " + mListeners.size() + " LISTENERS");
            for (OnMixpanelUpdatesReceivedListener listener: mListeners) {
                listener.onMixpanelUpdatesReceived();
            }
        }

        private final Set<OnMixpanelUpdatesReceivedListener> mListeners = new HashSet<OnMixpanelUpdatesReceivedListener>();
        private final Executor mExecutor = Executors.newSingleThreadExecutor();
    }

    ////////////////////////////////////////////////////

    private void recordPeopleMessage(JSONObject message) {
        if (message.has("$distinct_id")) {
           mMessages.peopleMessage(message);
        } else {
           mPersistentIdentity.storeWaitingPeopleRecord(message);
        }
    }

    private void pushWaitingPeopleRecord() {
        final JSONArray records = mPersistentIdentity.waitingPeopleRecordsForSending();
        if (null != records) {
            sendAllPeopleRecords(records);
        }
    }

    // MUST BE THREAD SAFE. Called from crazy places. mPersistentIdentity may not exist
    // when this is called (from its crazy thread)
    private void sendAllPeopleRecords(JSONArray records) {
        for (int i = 0; i < records.length(); i++) {
            try {
                final JSONObject message = records.getJSONObject(i);
                mMessages.peopleMessage(message);
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Malformed people record stored pending identity, will not send it.", e);
            }
        }
    }

    private static final String LOGTAG = "MixpanelAPI";
    private static final String ENGAGE_DATE_FORMAT_STRING = "yyyy-MM-dd'T'HH:mm:ss";

    private final Context mContext;
    private final AnalyticsMessages mMessages;
    private final MPConfig mConfig;
    private final String mToken;
    private final PeopleImpl mPeople;
    private final PersistentIdentity mPersistentIdentity;
    private final UpdatesListener mUpdatesListener;

    private DecideUpdates mDecideUpdates;

    private static final SharedPreferencesLoader sPrefsLoader = new SharedPreferencesLoader();
}
