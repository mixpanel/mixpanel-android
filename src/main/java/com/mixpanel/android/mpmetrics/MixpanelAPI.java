package com.mixpanel.android.mpmetrics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.mixpanel.android.util.MPLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.concurrent.Future;


/**
 * Core class for interacting with Mixpanel Analytics.
 *
 * <p>Call {@link #getInstance(Context, String, boolean)} with
 * your main application activity and your Mixpanel API token as arguments
 * an to get an instance you can use to report how users are using your
 * application.
 *
 * <p>Once you have an instance, you can send events to Mixpanel
 * using {@link #track(String, JSONObject)}, and update People Analytics
 * records with {@link #getPeople()}
 *
 * <p>The Mixpanel library will periodically send information to
 * Mixpanel servers, so your application will need to have
 * <code>android.permission.INTERNET</code>. In addition, to preserve
 * battery life, messages to Mixpanel servers may not be sent immediately
 * when you call {@link #track(String)}or {@link People#set(String, Object)}.
 * The library will send messages periodically throughout the lifetime
 * of your application, but you will need to call {@link #flush()}
 * before your application is completely shutdown to ensure all of your
 * events are sent.
 *
 * <p>A typical use-case for the library might look like this:
 *
 * <pre>
 * {@code
 * public class MainActivity extends Activity {
 *      MixpanelAPI mMixpanel;
 *
 *      public void onCreate(Bundle saved) {
 *          mMixpanel = MixpanelAPI.getInstance(this, "YOUR MIXPANEL API TOKEN");
 *          ...
 *      }
 *
 *      public void whenSomethingInterestingHappens(int flavor) {
 *          JSONObject properties = new JSONObject();
 *          properties.put("flavor", flavor);
 *          mMixpanel.track("Something Interesting Happened", properties);
 *          ...
 *      }
 *
 *      public void onDestroy() {
 *          mMixpanel.flush();
 *          super.onDestroy();
 *      }
 * }
 * }
 * </pre>
 *
 * <p>In addition to this documentation, you may wish to take a look at
 * <a href="https://github.com/mixpanel/sample-android-mixpanel-integration">the Mixpanel sample Android application</a>.
 * It demonstrates a variety of techniques, including
 * updating People Analytics records with {@link People} and others.
 *
 * <p>There are also <a href="https://mixpanel.com/docs/">step-by-step getting started documents</a>
 * available at mixpanel.com
 *
 * @see <a href="https://mixpanel.com/docs/integration-libraries/android">getting started documentation for tracking events</a>
 * @see <a href="https://mixpanel.com/docs/people-analytics/android">getting started documentation for People Analytics</a>
 * @see <a href="https://github.com/mixpanel/sample-android-mixpanel-integration">The Mixpanel Android sample application</a>
 */
public class MixpanelAPI {
    /**
     * String version of the library.
     */
    public static final String VERSION = MPConfig.VERSION;


    /**
     * You shouldn't instantiate MixpanelAPI objects directly.
     * Use MixpanelAPI.getInstance to get an instance.
     */
    MixpanelAPI(Context context, Future<SharedPreferences> referrerPreferences, String token, boolean optOutTrackingDefault, JSONObject superProperties, boolean trackAutomaticEvents) {
        this(context, referrerPreferences, token, MPConfig.getInstance(context), optOutTrackingDefault, superProperties, null, trackAutomaticEvents);
    }

    /**
     * You shouldn't instantiate MixpanelAPI objects directly.
     * Use MixpanelAPI.getInstance to get an instance.
     */
    MixpanelAPI(Context context, Future<SharedPreferences> referrerPreferences, String token, boolean optOutTrackingDefault, JSONObject superProperties, String instanceName, boolean trackAutomaticEvents) {
        this(context, referrerPreferences, token, MPConfig.getInstance(context), optOutTrackingDefault, superProperties, instanceName, trackAutomaticEvents);
    }

    /**
     * You shouldn't instantiate MixpanelAPI objects directly.
     * Use MixpanelAPI.getInstance to get an instance.
     */
    MixpanelAPI(Context context, Future<SharedPreferences> referrerPreferences, String token, MPConfig config, boolean optOutTrackingDefault, JSONObject superProperties, String instanceName, boolean trackAutomaticEvents) {
        mContext = context;
        mToken = token;
        mPeople = new PeopleImpl();
        mGroups = new HashMap<String, GroupImpl>();
        mConfig = config;
        mTrackAutomaticEvents = trackAutomaticEvents;

        final Map<String, String> deviceInfo = new HashMap<String, String>();
        deviceInfo.put("$android_lib_version", MPConfig.VERSION);
        deviceInfo.put("$android_os", "Android");
        deviceInfo.put("$android_os_version", Build.VERSION.RELEASE == null ? "UNKNOWN" : Build.VERSION.RELEASE);
        deviceInfo.put("$android_manufacturer", Build.MANUFACTURER == null ? "UNKNOWN" : Build.MANUFACTURER);
        deviceInfo.put("$android_brand", Build.BRAND == null ? "UNKNOWN" : Build.BRAND);
        deviceInfo.put("$android_model", Build.MODEL == null ? "UNKNOWN" : Build.MODEL);
        try {
            final PackageManager manager = mContext.getPackageManager();
            final PackageInfo info = manager.getPackageInfo(mContext.getPackageName(), 0);
            deviceInfo.put("$android_app_version", info.versionName);
            deviceInfo.put("$android_app_version_code", Integer.toString(info.versionCode));
        } catch (final PackageManager.NameNotFoundException e) {
            MPLog.e(LOGTAG, "Exception getting app version name", e);
        }
        mDeviceInfo = Collections.unmodifiableMap(deviceInfo);

        mSessionMetadata = new SessionMetadata();
        mMessages = getAnalyticsMessages();
        mPersistentIdentity = getPersistentIdentity(context, referrerPreferences, token, instanceName);
        mEventTimings = mPersistentIdentity.getTimeEvents();

        if (optOutTrackingDefault && (hasOptedOutTracking() || !mPersistentIdentity.hasOptOutFlag(token))) {
            optOutTracking();
        }

        if (superProperties != null) {
            registerSuperProperties(superProperties);
        }

        final boolean dbExists = MPDbAdapter.getInstance(mContext).getDatabaseFile().exists();

        registerMixpanelActivityLifecycleCallbacks();

        if (mPersistentIdentity.isFirstLaunch(dbExists, mToken) && mTrackAutomaticEvents) {
            track(AutomaticEvents.FIRST_OPEN, null, true);
            mPersistentIdentity.setHasLaunched(mToken);
        }

        if (sendAppOpen() && mTrackAutomaticEvents) {
            track("$app_open", null);
        }

        if (!mPersistentIdentity.isFirstIntegration(mToken)) {
            try {
                sendHttpEvent("Integration", "85053bf24bba75239b16a601d9387e17", token, null, false);
                mPersistentIdentity.setIsIntegrated(mToken);
            } catch (JSONException e) {
            }
        }

        if (mPersistentIdentity.isNewVersion(deviceInfo.get("$android_app_version_code")) && mTrackAutomaticEvents) {
            try {
                final JSONObject messageProps = new JSONObject();
                messageProps.put(AutomaticEvents.VERSION_UPDATED, deviceInfo.get("$android_app_version"));
                track(AutomaticEvents.APP_UPDATED, messageProps, true);
            } catch (JSONException e) {}
        }

        if (!mConfig.getDisableExceptionHandler()) {
            ExceptionHandler.init();
        }
    }

    private void sendHttpEvent(String eventName, String token, String distinctId, JSONObject properties, boolean updatePeople) throws JSONException {
        final JSONObject superProperties = getSuperProperties();
        String lib = null;
        String libVersion = null;
        try {
            if (superProperties != null) {
                lib = (String) superProperties.get("mp_lib");
                libVersion = (String) superProperties.get("$lib_version");
            }
        } catch (JSONException e) {
        }

        final JSONObject messageProps = new JSONObject();
        messageProps.put("mp_lib", null != lib ? lib : "Android");
        messageProps.put("distinct_id", distinctId);
        messageProps.put("$lib_version", null != libVersion ? libVersion : MPConfig.VERSION);
        messageProps.put("Project Token", distinctId);
        if (null != properties) {
            final Iterator<?> propIter = properties.keys();
            while (propIter.hasNext()) {
                final String key = (String) propIter.next();
                messageProps.put(key, properties.get(key));
            }
        }
        final AnalyticsMessages.EventDescription eventDescription =
                new AnalyticsMessages.EventDescription(
                        eventName,
                        messageProps,
                        token);
        mMessages.eventsMessage(eventDescription);

        if (updatePeople) {
            final JSONObject peopleMessageProps = new JSONObject();
            final JSONObject addProperties = new JSONObject();
            addProperties.put(eventName, 1);
            peopleMessageProps.put("$add", addProperties);
            peopleMessageProps.put("$token", token);
            peopleMessageProps.put("$distinct_id", distinctId);
            mMessages.peopleMessage(new AnalyticsMessages.PeopleDescription(peopleMessageProps, token));
        }
        mMessages.postToServer(new AnalyticsMessages.MixpanelDescription(token));
    }

    /**
     * Get the instance of MixpanelAPI associated with your Mixpanel project token.
     *
     * <p>Use getInstance to get a reference to a shared
     * instance of MixpanelAPI you can use to send events
     * and People Analytics updates to Mixpanel.</p>
     * <p>getInstance is thread safe, but the returned instance is not,
     * and may be shared with other callers of getInstance.
     * The best practice is to call getInstance, and use the returned MixpanelAPI,
     * object from a single thread (probably the main UI thread of your application).</p>
     * <p>If you do choose to track events from multiple threads in your application,
     * you should synchronize your calls on the instance itself, like so:</p>
     * <pre>
     * {@code
     * MixpanelAPI instance = MixpanelAPI.getInstance(context, token);
     * synchronized(instance) { // Only necessary if the instance will be used in multiple threads.
     *     instance.track(...)
     * }
     * }
     * </pre>
     *
     * @param context The application context you are tracking
     * @param token Your Mixpanel project token. You can get your project token on the Mixpanel web site,
     *     in the settings dialog.
     * @param trackAutomaticEvents Whether or not to collect common mobile events
     *                             include app sessions, first app opens, app updated, etc.
     * @return an instance of MixpanelAPI associated with your project
     */
    public static MixpanelAPI getInstance(Context context, String token, boolean trackAutomaticEvents) {
        return getInstance(context, token, false, null, null, trackAutomaticEvents);
    }

    /**
     * Get the instance of MixpanelAPI associated with your Mixpanel project token.
     *
     * <p>Use getInstance to get a reference to a shared
     * instance of MixpanelAPI you can use to send events
     * and People Analytics updates to Mixpanel.</p>
     * <p>getInstance is thread safe, but the returned instance is not,
     * and may be shared with other callers of getInstance.
     * The best practice is to call getInstance, and use the returned MixpanelAPI,
     * object from a single thread (probably the main UI thread of your application).</p>
     * <p>If you do choose to track events from multiple threads in your application,
     * you should synchronize your calls on the instance itself, like so:</p>
     * <pre>
     * {@code
     * MixpanelAPI instance = MixpanelAPI.getInstance(context, token);
     * synchronized(instance) { // Only necessary if the instance will be used in multiple threads.
     *     instance.track(...)
     * }
     * }
     * </pre>
     *
     * @param context The application context you are tracking
     * @param token Your Mixpanel project token. You can get your project token on the Mixpanel web site,
     *     in the settings dialog.
     * @param instanceName The name you want to uniquely identify the Mixpanel Instance.
     *      It is useful when you want more than one Mixpanel instance under the same project token
     * @param trackAutomaticEvents Whether or not to collect common mobile events
     *                             include app sessions, first app opens, app updated, etc.
     * @return an instance of MixpanelAPI associated with your project
     */
    public static MixpanelAPI getInstance(Context context, String token, String instanceName, boolean trackAutomaticEvents) {
        return getInstance(context, token, false, null, instanceName, trackAutomaticEvents);
    }

    /**
     * Get the instance of MixpanelAPI associated with your Mixpanel project token.
     *
     * <p>Use getInstance to get a reference to a shared
     * instance of MixpanelAPI you can use to send events
     * and People Analytics updates to Mixpanel.</p>
     * <p>getInstance is thread safe, but the returned instance is not,
     * and may be shared with other callers of getInstance.
     * The best practice is to call getInstance, and use the returned MixpanelAPI,
     * object from a single thread (probably the main UI thread of your application).</p>
     * <p>If you do choose to track events from multiple threads in your application,
     * you should synchronize your calls on the instance itself, like so:</p>
     * <pre>
     * {@code
     * MixpanelAPI instance = MixpanelAPI.getInstance(context, token);
     * synchronized(instance) { // Only necessary if the instance will be used in multiple threads.
     *     instance.track(...)
     * }
     * }
     * </pre>
     *
     * @param context The application context you are tracking
     * @param token Your Mixpanel project token. You can get your project token on the Mixpanel web site,
     *     in the settings dialog.
     * @param optOutTrackingDefault Whether or not Mixpanel can start tracking by default. See
     *     {@link #optOutTracking()}.
     * @param trackAutomaticEvents Whether or not to collect common mobile events
     *                             include app sessions, first app opens, app updated, etc.
     * @return an instance of MixpanelAPI associated with your project
     */
    public static MixpanelAPI getInstance(Context context, String token, boolean optOutTrackingDefault, boolean trackAutomaticEvents) {
        return getInstance(context, token, optOutTrackingDefault, null, null, trackAutomaticEvents);
    }

    /**
     * Get the instance of MixpanelAPI associated with your Mixpanel project token.
     *
     * <p>Use getInstance to get a reference to a shared
     * instance of MixpanelAPI you can use to send events
     * and People Analytics updates to Mixpanel.</p>
     * <p>getInstance is thread safe, but the returned instance is not,
     * and may be shared with other callers of getInstance.
     * The best practice is to call getInstance, and use the returned MixpanelAPI,
     * object from a single thread (probably the main UI thread of your application).</p>
     * <p>If you do choose to track events from multiple threads in your application,
     * you should synchronize your calls on the instance itself, like so:</p>
     * <pre>
     * {@code
     * MixpanelAPI instance = MixpanelAPI.getInstance(context, token);
     * synchronized(instance) { // Only necessary if the instance will be used in multiple threads.
     *     instance.track(...)
     * }
     * }
     * </pre>
     *
     * @param context The application context you are tracking
     * @param token Your Mixpanel project token. You can get your project token on the Mixpanel web site,
     *     in the settings dialog.
     * @param optOutTrackingDefault Whether or not Mixpanel can start tracking by default. See
     *     {@link #optOutTracking()}.
     * @param instanceName The name you want to uniquely identify the Mixpanel Instance.
        It is useful when you want more than one Mixpanel instance under the same project token.
     * @param trackAutomaticEvents Whether or not to collect common mobile events
     *                             include app sessions, first app opens, app updated, etc.
     * @return an instance of MixpanelAPI associated with your project
     */
    public static MixpanelAPI getInstance(Context context, String token, boolean optOutTrackingDefault, String instanceName, boolean trackAutomaticEvents) {
        return getInstance(context, token, optOutTrackingDefault, null, instanceName, trackAutomaticEvents);
    }

    /**
     * Get the instance of MixpanelAPI associated with your Mixpanel project token.
     *
     * <p>Use getInstance to get a reference to a shared
     * instance of MixpanelAPI you can use to send events
     * and People Analytics updates to Mixpanel.</p>
     * <p>getInstance is thread safe, but the returned instance is not,
     * and may be shared with other callers of getInstance.
     * The best practice is to call getInstance, and use the returned MixpanelAPI,
     * object from a single thread (probably the main UI thread of your application).</p>
     * <p>If you do choose to track events from multiple threads in your application,
     * you should synchronize your calls on the instance itself, like so:</p>
     * <pre>
     * {@code
     * MixpanelAPI instance = MixpanelAPI.getInstance(context, token);
     * synchronized(instance) { // Only necessary if the instance will be used in multiple threads.
     *     instance.track(...)
     * }
     * }
     * </pre>
     *
     * @param context The application context you are tracking
     * @param token Your Mixpanel project token. You can get your project token on the Mixpanel web site,
     *     in the settings dialog.
     * @param superProperties A JSONObject containing super properties to register.
     * @param trackAutomaticEvents Whether or not to collect common mobile events
     *                             include app sessions, first app opens, app updated, etc.
     * @return an instance of MixpanelAPI associated with your project
     */
    public static MixpanelAPI getInstance(Context context, String token, JSONObject superProperties, boolean trackAutomaticEvents) {
        return getInstance(context, token, false, superProperties, null, trackAutomaticEvents);
    }

    /**
     * Get the instance of MixpanelAPI associated with your Mixpanel project token.
     *
     * <p>Use getInstance to get a reference to a shared
     * instance of MixpanelAPI you can use to send events
     * and People Analytics updates to Mixpanel.</p>
     * <p>getInstance is thread safe, but the returned instance is not,
     * and may be shared with other callers of getInstance.
     * The best practice is to call getInstance, and use the returned MixpanelAPI,
     * object from a single thread (probably the main UI thread of your application).</p>
     * <p>If you do choose to track events from multiple threads in your application,
     * you should synchronize your calls on the instance itself, like so:</p>
     * <pre>
     * {@code
     * MixpanelAPI instance = MixpanelAPI.getInstance(context, token);
     * synchronized(instance) { // Only necessary if the instance will be used in multiple threads.
     *     instance.track(...)
     * }
     * }
     * </pre>
     *
     * @param context The application context you are tracking
     * @param token Your Mixpanel project token. You can get your project token on the Mixpanel web site,
     *     in the settings dialog.
     * @param superProperties A JSONObject containing super properties to register.
     * @param instanceName The name you want to uniquely identify the Mixpanel Instance.
     *      It is useful when you want more than one Mixpanel instance under the same project token
     * @param trackAutomaticEvents Whether or not to collect common mobile events
     *                             include app sessions, first app opens, app updated, etc.
     * @return an instance of MixpanelAPI associated with your project
     */
    public static MixpanelAPI getInstance(Context context, String token, JSONObject superProperties, String instanceName, boolean trackAutomaticEvents) {
        return getInstance(context, token, false, superProperties, instanceName, trackAutomaticEvents);
    }

    /**
     * Get the instance of MixpanelAPI associated with your Mixpanel project token.
     *
     * <p>Use getInstance to get a reference to a shared
     * instance of MixpanelAPI you can use to send events
     * and People Analytics updates to Mixpanel.</p>
     * <p>getInstance is thread safe, but the returned instance is not,
     * and may be shared with other callers of getInstance.
     * The best practice is to call getInstance, and use the returned MixpanelAPI,
     * object from a single thread (probably the main UI thread of your application).</p>
     * <p>If you do choose to track events from multiple threads in your application,
     * you should synchronize your calls on the instance itself, like so:</p>
     * <pre>
     * {@code
     * MixpanelAPI instance = MixpanelAPI.getInstance(context, token);
     * synchronized(instance) { // Only necessary if the instance will be used in multiple threads.
     *     instance.track(...)
     * }
     * }
     * </pre>
     *
     * @param context The application context you are tracking
     * @param token Your Mixpanel project token. You can get your project token on the Mixpanel web site,
     *     in the settings dialog.
     * @param optOutTrackingDefault Whether or not Mixpanel can start tracking by default. See
     *     {@link #optOutTracking()}.
     * @param superProperties A JSONObject containing super properties to register.
     * @param instanceName The name you want to uniquely identify the Mixpanel Instance.
     *      It is useful when you want more than one Mixpanel instance under the same project token
     * @param trackAutomaticEvents Whether or not to collect common mobile events
     *                             include app sessions, first app opens, app updated, etc.
     * @return an instance of MixpanelAPI associated with your project
     */
    public static MixpanelAPI getInstance(Context context, String token, boolean optOutTrackingDefault, JSONObject superProperties, String instanceName, boolean trackAutomaticEvents) {
        if (null == token || null == context) {
            return null;
        }
        synchronized (sInstanceMap) {
            final Context appContext = context.getApplicationContext();

            if (null == sReferrerPrefs) {
                sReferrerPrefs = sPrefsLoader.loadPreferences(context, MPConfig.REFERRER_PREFS_NAME, null);
            }
            String instanceKey = instanceName != null ? instanceName : token;
            Map <Context, MixpanelAPI> instances = sInstanceMap.get(instanceKey);
            if (null == instances) {
                instances = new HashMap<Context, MixpanelAPI>();
                sInstanceMap.put(instanceKey, instances);
            }

            MixpanelAPI instance = instances.get(appContext);
            if (null == instance && ConfigurationChecker.checkBasicConfiguration(appContext)) {
                instance = new MixpanelAPI(appContext, sReferrerPrefs, token, optOutTrackingDefault, superProperties, instanceName, trackAutomaticEvents);
                registerAppLinksListeners(context, instance);
                instances.put(appContext, instance);
            }

            checkIntentForInboundAppLink(context);

            return instance;
        }
    }

    /**
     * Controls whether to automatically send the client IP Address as part of event tracking.
     *
     * <p> With an IP address, geo-location is possible down to neighborhoods within a city,
     * although the Mixpanel Dashboard will just show you city level location specificity.
     *
     * @param useIpAddressForGeolocation If true, automatically send the client IP Address. Defaults to true.
     */
    public void setUseIpAddressForGeolocation(boolean useIpAddressForGeolocation) {
        mConfig.setUseIpAddressForGeolocation(useIpAddressForGeolocation);
    }

    /**
     * Controls whether to enable the run time debug logging
     *
     * @param enableLogging If true, emit more detailed log messages. Defaults to false
     */
    public void setEnableLogging(boolean enableLogging) {
        mConfig.setEnableLogging(enableLogging);
    }

    /**
     * Set maximum number of events/updates to send in a single network request
     *
     * @param flushBatchSize  int, the number of events to be flushed at a time, defaults to 50
     */
    public void setFlushBatchSize(int flushBatchSize) {
        mConfig.setFlushBatchSize(flushBatchSize);
    }

    /**
     * Get maximum number of events/updates to send in a single network request
     *
     * @return the integer number of events to be flushed at a time
     */
    public int getFlushBatchSize() {
        return mConfig.getFlushBatchSize();
    }

    /**
     * Set an integer number of bytes, the maximum size limit to the Mixpanel database.
     *
     * @param maximumDatabaseLimit an integer number of bytes, the maximum size limit to the Mixpanel database.
     */
    public void setMaximumDatabaseLimit(int maximumDatabaseLimit) {
        mConfig.setMaximumDatabaseLimit(maximumDatabaseLimit);
    }

    /**
     * Get  the maximum size limit to the Mixpanel database.
     *
     * @return an integer number of bytes, the maximum size limit to the Mixpanel database.
     */
    public int getMaximumDatabaseLimit() {
        return mConfig.getMaximumDatabaseLimit();
    }

    /**
     * Set the base URL used for Mixpanel API requests.
     * Useful if you need to proxy Mixpanel requests. Defaults to https://api.mixpanel.com.
     * To route data to Mixpanel's EU servers, set to https://api-eu.mixpanel.com
     *
     * @param serverURL the base URL used for Mixpanel API requests
     */
    public void setServerURL(String serverURL) {
        mConfig.setServerURL(serverURL);
    }


    public Boolean getTrackAutomaticEvents() { return mTrackAutomaticEvents; }
    /**
     * This function creates a distinct_id alias from alias to original. If original is null, then it will create an alias
     * to the current events distinct_id, which may be the distinct_id randomly generated by the Mixpanel library
     * before {@link #identify(String)} is called.
     *
     * <p>This call does not identify the user after. You must still call {@link #identify(String)} if you wish the new alias to be used for Events and People.
     *
     * @param alias the new distinct_id that should represent original.
     * @param original the old distinct_id that alias will be mapped to.
     */
    public void alias(String alias, String original) {
        if (hasOptedOutTracking()) return;
        if (original == null) {
            original = getDistinctId();
        }
        if (alias.equals(original)) {
            MPLog.w(LOGTAG, "Attempted to alias identical distinct_ids " + alias + ". Alias message will not be sent.");
            return;
        }
        try {
            final JSONObject j = new JSONObject();
            j.put("alias", alias);
            j.put("original", original);
            track("$create_alias", j);
        } catch (final JSONException e) {
            MPLog.e(LOGTAG, "Failed to alias", e);
        }
        flush();
    }

    /**
     * Equivalent to {@link #identify(String, boolean)} with a true argument for usePeople.
     *
     * <p>By default, this method will also associate future calls
     * to {@link People#set(JSONObject)}, {@link People#increment(Map)}, {@link People#append(String, Object)}, etc...
     * with a particular People Analytics user with the distinct id.
     * If you do not want to do that, you must call {@link #identify(String, boolean)} with false for second argument.
     * NOTE: This behavior changed in version 6.2.0, previously {@link People#identify(String)} had
     * to be called separately.
     *
     * @param distinctId a string uniquely identifying this user. Events sent to
     *     Mixpanel or Users identified using the same distinct id will be considered associated with the
     *     same visitor/customer for retention and funnel reporting, so be sure that the given
     *     value is globally unique for each individual user you intend to track.
     */
    public void identify(String distinctId) {
        identify(distinctId, true, true);
    }

    /**
     * Associate all future calls to {@link #track(String, JSONObject)} with the user identified by
     * the given distinct id.
     *
     * <p>Calls to {@link #track(String, JSONObject)} made before corresponding calls to identify
     * will use an anonymous locally generated distinct id, which means it is best to call identify
     * early to ensure that your Mixpanel funnels and retention analytics can continue to track the
     * user throughout their lifetime. We recommend calling identify when the user authenticates.
     *
     * <p>Once identify is called, the local distinct id persists across restarts of
     * your application.
     *
     * @param distinctId a string uniquely identifying this user. Events sent to
     *     Mixpanel using the same disinct id will be considered associated with the
     *     same visitor/customer for retention and funnel reporting, so be sure that the given
     *     value is globally unique for each individual user you intend to track.
     *
     * @param usePeople boolean indicating whether or not to also call
     *      {@link People#identify(String)}
     *
     */
    public void identify(String distinctId, boolean usePeople) {
        identify(distinctId, true, usePeople);
    }

    private void identify(String distinctId, boolean markAsUserId, boolean usePeople) {
        if (hasOptedOutTracking()) return;
        if (distinctId == null) {
            MPLog.e(LOGTAG, "Can't identify with null distinct_id.");
            return;
        }
        synchronized (mPersistentIdentity) {
            String currentEventsDistinctId = mPersistentIdentity.getEventsDistinctId();
            mPersistentIdentity.setAnonymousIdIfAbsent(currentEventsDistinctId);
            mPersistentIdentity.setEventsDistinctId(distinctId);
            if(markAsUserId) {
                mPersistentIdentity.markEventsUserIdPresent();
            }

            if (!distinctId.equals(currentEventsDistinctId)) {
                try {
                    JSONObject identifyPayload = new JSONObject();
                    identifyPayload.put("$anon_distinct_id", currentEventsDistinctId);
                    track("$identify", identifyPayload);
                } catch (JSONException e) {
                    MPLog.e(LOGTAG, "Could not track $identify event");
                }
            }

            if (usePeople) {
                mPeople.identify_people(distinctId);
            }
        }
    }

    /**
     * Begin timing of an event. Calling timeEvent("Thing") will not send an event, but
     * when you eventually call track("Thing"), your tracked event will be sent with a "$duration"
     * property, representing the number of seconds between your calls.
     *
     * @param eventName the name of the event to track with timing.
     */
    public void timeEvent(final String eventName) {
        if (hasOptedOutTracking()) return;
        final long writeTime = System.currentTimeMillis();
        synchronized (mEventTimings) {
            mEventTimings.put(eventName, writeTime);
            mPersistentIdentity.addTimeEvent(eventName, writeTime);
        }
    }

    /**
     * Clears all current event timings.
     *
     */
    public void clearTimedEvents() {
        synchronized (mEventTimings) {
            mEventTimings.clear();
            mPersistentIdentity.clearTimedEvents();
        }
    }

    /**
     * Clears the event timing for an event.
     *
     * @param eventName the name of the timed event to clear.
     */
    public void clearTimedEvent(final String eventName) {
        synchronized (mEventTimings) {
            mEventTimings.remove(eventName);
            mPersistentIdentity.removeTimedEvent(eventName);
        }
    }

    /**
     * Retrieves the time elapsed for the named event since timeEvent() was called.
     *
     * @param eventName the name of the event to be tracked that was previously called with timeEvent()
     *
     * @return Time elapsed since {@link #timeEvent(String)} was called for the given eventName.
     */
    public double eventElapsedTime(final String eventName) {
        final long currentTime = System.currentTimeMillis();
        Long startTime;
        synchronized (mEventTimings) {
            startTime = mEventTimings.get(eventName);
        }
        return startTime == null ? 0 : (double)((currentTime - startTime) / 1000);
    }

    /**
     * Track an event.
     *
     * <p>Every call to track eventually results in a data point sent to Mixpanel. These data points
     * are what are measured, counted, and broken down to create your Mixpanel reports. Events
     * have a string name, and an optional set of name/value pairs that describe the properties of
     * that event.
     *
     * @param eventName The name of the event to send
     * @param properties A Map containing the key value pairs of the properties to include in this event.
     *                   Pass null if no extra properties exist.
     *
     * See also {@link #track(String, org.json.JSONObject)}
     */
    public void trackMap(String eventName, Map<String, Object> properties) {
        if (hasOptedOutTracking()) return;
        if (null == properties) {
            track(eventName, null);
        } else {
            try {
                track(eventName, new JSONObject(properties));
            } catch (NullPointerException e) {
                MPLog.w(LOGTAG, "Can't have null keys in the properties of trackMap!");
            }
        }
    }

    /**
     * Track an event with specific groups.
     *
     * <p>Every call to track eventually results in a data point sent to Mixpanel. These data points
     * are what are measured, counted, and broken down to create your Mixpanel reports. Events
     * have a string name, and an optional set of name/value pairs that describe the properties of
     * that event. Group key/value pairs are upserted into the property map before tracking.
     *
     * @param eventName The name of the event to send
     * @param properties A Map containing the key value pairs of the properties to include in this event.
     *                   Pass null if no extra properties exist.
     * @param groups A Map containing the group key value pairs for this event.
     *
     * See also {@link #track(String, org.json.JSONObject)}, {@link #trackMap(String, Map)}
     */
    public void trackWithGroups(String eventName, Map<String, Object> properties, Map<String, Object> groups) {
        if (hasOptedOutTracking()) return;

        if (null == groups) {
            trackMap(eventName, properties);
        } else if (null == properties) {
            trackMap(eventName, groups);
        } else {
            for (Entry<String, Object> e : groups.entrySet()) {
                if (e.getValue() != null) {
                    properties.put(e.getKey(), e.getValue());
                }
            }

            trackMap(eventName, properties);
        }
    }

    /**
     * Track an event.
     *
     * <p>Every call to track eventually results in a data point sent to Mixpanel. These data points
     * are what are measured, counted, and broken down to create your Mixpanel reports. Events
     * have a string name, and an optional set of name/value pairs that describe the properties of
     * that event.
     *
     * @param eventName The name of the event to send
     * @param properties A JSONObject containing the key value pairs of the properties to include in this event.
     *                   Pass null if no extra properties exist.
     */
    public void track(String eventName, JSONObject properties) {
        if (hasOptedOutTracking()) return;
        track(eventName, properties, false);
    }

    /**
     * Equivalent to {@link #track(String, JSONObject)} with a null argument for properties.
     * Consider adding properties to your tracking to get the best insights and experience from Mixpanel.
     * @param eventName the name of the event to send
     */
    public void track(String eventName) {
        if (hasOptedOutTracking()) return;
        track(eventName, null);
    }

    /**
     * Push all queued Mixpanel events and People Analytics changes to Mixpanel servers.
     *
     * <p>Events and People messages are pushed gradually throughout
     * the lifetime of your application. This means that to ensure that all messages
     * are sent to Mixpanel when your application is shut down, you will
     * need to call flush() to let the Mixpanel library know it should
     * send all remaining messages to the server. We strongly recommend
     * placing a call to flush() in the onDestroy() method of
     * your main application activity.
     */
    public void flush() {
        if (hasOptedOutTracking()) return;
        mMessages.postToServer(new AnalyticsMessages.MixpanelDescription(mToken));
    }

    /**
     * Returns a json object of the user's current super properties
     *
     *<p>SuperProperties are a collection of properties that will be sent with every event to Mixpanel,
     * and persist beyond the lifetime of your application.
     *
     * @return Super properties for this Mixpanel instance.
     */
      public JSONObject getSuperProperties() {
          JSONObject ret = new JSONObject();
          mPersistentIdentity.addSuperPropertiesToObject(ret);
          return ret;
      }

    /**
     * Returns the string id currently being used to uniquely identify the user. Before any calls to
     * {@link #identify(String)}, this will be an id automatically generated by the library.
     *
     *
     * @return The distinct id that uniquely identifies the current user.
     *
     * @see #identify(String)
     */
    public String getDistinctId() {
        return mPersistentIdentity.getEventsDistinctId();
    }

     /**
     * Returns the anonymoous id currently being used to uniquely identify the device and all
     * with events sent using {@link #track(String, JSONObject)} will have this id as a device
     * id
     *
     * @return The device id associated with event tracking
     */
    public String getAnonymousId() {
        return mPersistentIdentity.getAnonymousId();
    }

    /**
     * Returns the user id with which identify is called  and all the with events sent using
     * {@link #track(String, JSONObject)} will have this id as a user id
     *
     * @return The user id associated with event tracking
     */
    protected String getUserId() {
        return mPersistentIdentity.getEventsUserId();
    }

    /**
     * Register properties that will be sent with every subsequent call to {@link #track(String, JSONObject)}.
     *
     * <p>SuperProperties are a collection of properties that will be sent with every event to Mixpanel,
     * and persist beyond the lifetime of your application.
     *
     * <p>Setting a superProperty with registerSuperProperties will store a new superProperty,
     * possibly overwriting any existing superProperty with the same name (to set a
     * superProperty only if it is currently unset, use {@link #registerSuperPropertiesOnce(JSONObject)})
     *
     * <p>SuperProperties will persist even if your application is taken completely out of memory.
     * to remove a superProperty, call {@link #unregisterSuperProperty(String)} or {@link #clearSuperProperties()}
     *
     * @param superProperties    A Map containing super properties to register
     *
     * See also {@link #registerSuperProperties(org.json.JSONObject)}
     */
    public void registerSuperPropertiesMap(Map<String, Object> superProperties) {
        if (hasOptedOutTracking()) return;
        if (null == superProperties) {
            MPLog.e(LOGTAG, "registerSuperPropertiesMap does not accept null properties");
            return;
        }

        try {
            registerSuperProperties(new JSONObject(superProperties));
        } catch (NullPointerException e) {
            MPLog.w(LOGTAG, "Can't have null keys in the properties of registerSuperPropertiesMap");
        }
    }

    /**
     * Register properties that will be sent with every subsequent call to {@link #track(String, JSONObject)}.
     *
     * <p>SuperProperties are a collection of properties that will be sent with every event to Mixpanel,
     * and persist beyond the lifetime of your application.
     *
     * <p>Setting a superProperty with registerSuperProperties will store a new superProperty,
     * possibly overwriting any existing superProperty with the same name (to set a
     * superProperty only if it is currently unset, use {@link #registerSuperPropertiesOnce(JSONObject)})
     *
     * <p>SuperProperties will persist even if your application is taken completely out of memory.
     * to remove a superProperty, call {@link #unregisterSuperProperty(String)} or {@link #clearSuperProperties()}
     *
     * @param superProperties    A JSONObject containing super properties to register
     * @see #registerSuperPropertiesOnce(JSONObject)
     * @see #unregisterSuperProperty(String)
     * @see #clearSuperProperties()
     */
    public void registerSuperProperties(JSONObject superProperties) {
        if (hasOptedOutTracking()) return;
        mPersistentIdentity.registerSuperProperties(superProperties);
    }

    /**
     * Remove a single superProperty, so that it will not be sent with future calls to {@link #track(String, JSONObject)}.
     *
     * <p>If there is a superProperty registered with the given name, it will be permanently
     * removed from the existing superProperties.
     * To clear all superProperties, use {@link #clearSuperProperties()}
     *
     * @param superPropertyName name of the property to unregister
     * @see #registerSuperProperties(JSONObject)
     */
    public void unregisterSuperProperty(String superPropertyName) {
        if (hasOptedOutTracking()) return;
        mPersistentIdentity.unregisterSuperProperty(superPropertyName);
    }

    /**
     * Register super properties for events, only if no other super property with the
     * same names has already been registered.
     *
     * <p>Calling registerSuperPropertiesOnce will never overwrite existing properties.
     *
     * @param superProperties A Map containing the super properties to register.
     *
     * See also {@link #registerSuperPropertiesOnce(org.json.JSONObject)}
     */
    public void registerSuperPropertiesOnceMap(Map<String, Object> superProperties) {
        if (hasOptedOutTracking()) return;
        if (null == superProperties) {
            MPLog.e(LOGTAG, "registerSuperPropertiesOnceMap does not accept null properties");
            return;
        }

        try {
            registerSuperPropertiesOnce(new JSONObject(superProperties));
        } catch (NullPointerException e) {
            MPLog.w(LOGTAG, "Can't have null keys in the properties of registerSuperPropertiesOnce!");
        }
    }

    /**
     * Register super properties for events, only if no other super property with the
     * same names has already been registered.
     *
     * <p>Calling registerSuperPropertiesOnce will never overwrite existing properties.
     *
     * @param superProperties A JSONObject containing the super properties to register.
     * @see #registerSuperProperties(JSONObject)
     */
    public void registerSuperPropertiesOnce(JSONObject superProperties) {
        if (hasOptedOutTracking()) return;
        mPersistentIdentity.registerSuperPropertiesOnce(superProperties);
    }

    /**
     * Erase all currently registered superProperties.
     *
     * <p>Future tracking calls to Mixpanel will not contain the specific
     * superProperties registered before the clearSuperProperties method was called.
     *
     * <p>To remove a single superProperty, use {@link #unregisterSuperProperty(String)}
     *
     * @see #registerSuperProperties(JSONObject)
     */
    public void clearSuperProperties() {
        mPersistentIdentity.clearSuperProperties();
    }

    /**
     * Updates super properties in place. Given a SuperPropertyUpdate object, will
     * pass the current values of SuperProperties to that update and replace all
     * results with the return value of the update. Updates are synchronized on
     * the underlying super properties store, so they are guaranteed to be thread safe
     * (but long running updates may slow down your tracking.)
     *
     * @param update A function from one set of super properties to another. The update should not return null.
     */
    public void updateSuperProperties(SuperPropertyUpdate update) {
        if (hasOptedOutTracking()) return;
        mPersistentIdentity.updateSuperProperties(update);
    }

    /**
     * Set the group this user belongs to.
     *
     * @param groupKey The property name associated with this group type (must already have been set up).
     * @param groupID The group the user belongs to.
     */
    public void setGroup(String groupKey, Object groupID) {
        if (hasOptedOutTracking()) return;

        List<Object> groupIDs = new ArrayList<>(1);
        groupIDs.add(groupID);
        setGroup(groupKey, groupIDs);
    }

    /**
     * Set the groups this user belongs to.
     *
     * @param groupKey The property name associated with this group type (must already have been set up).
     * @param groupIDs The list of groups the user belongs to.
     */
    public void setGroup(String groupKey, List<Object> groupIDs) {
        if (hasOptedOutTracking()) return;

        JSONArray vals = new JSONArray();

        for (Object s : groupIDs) {
            if (s == null) {
                MPLog.w(LOGTAG, "groupID must be non-null");
            } else {
                vals.put(s);
            }
        }

        try {
            registerSuperProperties((new JSONObject()).put(groupKey, vals));
            mPeople.set(groupKey, vals);
        } catch (JSONException e) {
            MPLog.w(LOGTAG, "groupKey must be non-null");
        }
    }

    /**
     * Add a group to this user's membership for a particular group key
     *
     * @param groupKey The property name associated with this group type (must already have been set up).
     * @param groupID The new group the user belongs to.
     */
    public void addGroup(final String groupKey, final Object groupID) {
        if (hasOptedOutTracking()) return;

        updateSuperProperties(new SuperPropertyUpdate() {
            public JSONObject update(JSONObject in) {
                try {
                    in.accumulate(groupKey, groupID);
                } catch (JSONException e) {
                    MPLog.e(LOGTAG, "Failed to add groups superProperty", e);
                }

                return in;
            }
        });

        // This is a best effort--if the people property is not already a list, this call does nothing.
        mPeople.union(groupKey, (new JSONArray()).put(groupID));
    }

    /**
     * Remove a group from this user's membership for a particular group key
     *
     * @param groupKey The property name associated with this group type (must already have been set up).
     * @param groupID The group value to remove.
     */
    public void removeGroup(final String groupKey, final Object groupID) {
        if (hasOptedOutTracking()) return;

        updateSuperProperties(new SuperPropertyUpdate() {
            public JSONObject update(JSONObject in) {
                try {
                    JSONArray vals = in.getJSONArray(groupKey);
                    JSONArray newVals = new JSONArray();

                    if (vals.length() <= 1) {
                        in.remove(groupKey);

                        // This is a best effort--we can't guarantee people and super properties match
                        mPeople.unset(groupKey);
                    } else {

                        for (int i = 0; i < vals.length(); i++) {
                            if (!vals.get(i).equals(groupID)) {
                                newVals.put(vals.get(i));
                            }
                        }

                        in.put(groupKey, newVals);

                        // This is a best effort--we can't guarantee people and super properties match
                        // If people property is not a list, this call does nothing.
                        mPeople.remove(groupKey, groupID);
                    }
                } catch (JSONException e) {
                    in.remove(groupKey);

                    // This is a best effort--we can't guarantee people and super properties match
                    mPeople.unset(groupKey);
                }

                return in;
            }
        });
    }


    /**
     * Returns a Mixpanel.People object that can be used to set and increment
     * People Analytics properties.
     *
     * @return an instance of {@link People} that you can use to update
     *     records in Mixpanel People Analytics.
     */
    public People getPeople() {
        return mPeople;
    }

    /**
     * Returns a Mixpanel.Group object that can be used to set and increment
     * Group Analytics properties.
     *
     * @param groupKey String identifying the type of group (must be already in use as a group key)
     * @param groupID Object identifying the specific group
     * @return an instance of {@link Group} that you can use to update
     *     records in Mixpanel Group Analytics
     */
    public Group getGroup(String groupKey, Object groupID) {
        String mapKey = makeMapKey(groupKey, groupID);
        GroupImpl group = mGroups.get(mapKey);

        if (group == null) {
            group = new GroupImpl(groupKey, groupID);
            mGroups.put(mapKey, group);
        }

        if (!(group.mGroupKey.equals(groupKey) && group.mGroupID.equals(groupID))) {
            // we hit a map key collision, return a new group with the correct key and ID
            MPLog.i(LOGTAG, "groups map key collision " + mapKey);
            group = new GroupImpl(groupKey, groupID);
            mGroups.put(mapKey, group);
        }

        return group;
    }

    private String makeMapKey(String groupKey, Object groupID) {
        return groupKey + '_' + groupID;
    }

    /**
     * Clears tweaks and all distinct_ids, superProperties, and push registrations from persistent storage.
     * Will not clear referrer information.
     */
    public void reset() {
        // Will clear distinct_ids, superProperties,
        // and waiting People Analytics properties. Will have no effect
        // on messages already queued to send with AnalyticsMessages.
        mPersistentIdentity.clearPreferences();
        getAnalyticsMessages().clearAnonymousUpdatesMessage(new AnalyticsMessages.MixpanelDescription(mToken));
        identify(getDistinctId(), false);
        flush();
    }

    /**
     * Returns an unmodifiable map that contains the device description properties
     * that will be sent to Mixpanel. These are not all of the default properties,
     * but are a subset that are dependant on the user's device or installed version
     * of the host application, and are guaranteed not to change while the app is running.
     *
     * @return Map containing the device description properties that are sent to Mixpanel.
     */
    public Map<String, String> getDeviceInfo() {
        return mDeviceInfo;
    }

    /**
     * Use this method to opt-out a user from tracking. Events and people updates that haven't been
     * flushed yet will be deleted. Use {@link #flush()} before calling this method if you want
     * to send all the queues to Mixpanel before.
     *
     * This method will also remove any user-related information from the device.
     */
    public void optOutTracking() {
        getAnalyticsMessages().emptyTrackingQueues(new AnalyticsMessages.MixpanelDescription(mToken));
        if (getPeople().isIdentified()) {
            getPeople().deleteUser();
            getPeople().clearCharges();
        }
        mPersistentIdentity.clearPreferences();
        synchronized (mEventTimings) {
            mEventTimings.clear();
            mPersistentIdentity.clearTimedEvents();
        }
        mPersistentIdentity.clearReferrerProperties();
        mPersistentIdentity.setOptOutTracking(true, mToken);
    }

    /**
     * Use this method to opt-in an already opted-out user from tracking. People updates and track
     * calls will be sent to Mixpanel after using this method.
     * This method will internally track an opt-in event to your project. If you want to identify
     * the opt-in event and/or pass properties to the event, see {@link #optInTracking(String)} and
     * {@link #optInTracking(String, JSONObject)}
     *
     * See also {@link #optOutTracking()}.
     */
    public void optInTracking() {
        optInTracking(null, null);
    }

    /**
     * Use this method to opt-in an already opted-out user from tracking. People updates and track
     * calls will be sent to Mixpanel after using this method.
     * This method will internally track an opt-in event to your project.
     *
     * @param distinctId Optional string to use as the distinct ID for events.
     *                   This will call {@link #identify(String)}.
     *
     * See also {@link #optInTracking(String)}, {@link #optInTracking(String, JSONObject)} and
     *  {@link #optOutTracking()}.
     */
    public void optInTracking(String distinctId) {
        optInTracking(distinctId, null);
    }

    /**
     * Use this method to opt-in an already opted-out user from tracking. People updates and track
     * calls will be sent to Mixpanel after using this method.
     * This method will internally track an opt-in event to your project.
     *
     * @param distinctId Optional string to use as the distinct ID for events.
     *                   This will call {@link #identify(String)}.
     *
     * @param properties Optional JSONObject that could be passed to add properties to the
     *                   opt-in event that is sent to Mixpanel.
     *
     * See also {@link #optInTracking()} and {@link #optOutTracking()}.
     */
    public void optInTracking(String distinctId, JSONObject properties) {
        mPersistentIdentity.setOptOutTracking(false, mToken);
        if (distinctId != null) {
            identify(distinctId);
        }
        track("$opt_in", properties);
    }
    /**
     * Will return true if the user has opted out from tracking. See {@link #optOutTracking()} and
     * {@link
     * MixpanelAPI#getInstance(Context, String, boolean, JSONObject, String, boolean)} for more information.
     *
     * @return true if user has opted out from tracking. Defaults to false.
     */
    public boolean hasOptedOutTracking() {
        return mPersistentIdentity.getOptOutTracking(mToken);
    }

    /**
     * Core interface for using Mixpanel People Analytics features.
     * You can get an instance by calling {@link MixpanelAPI#getPeople()}
     *
     * <p>The People object is used to update properties in a user's People Analytics record.
     * For this reason, it's important to call {@link #identify(String)} on the People
     * object before you work with it. Once you call identify, the user identity will
     * persist across stops and starts of your application, until you make another
     * call to identify using a different id.
     *
     * A typical use case for the People object might look like this:
     *
     * <pre>
     * {@code
     *
     * public class MainActivity extends Activity {
     *      MixpanelAPI mMixpanel;
     *
     *      public void onCreate(Bundle saved) {
     *          mMixpanel = MixpanelAPI.getInstance(this, "YOUR MIXPANEL API TOKEN");
     *          mMixpanel.identify("A UNIQUE ID FOR THIS USER");
     *          ...
     *      }
     *
     *      public void userUpdatedJobTitle(String newTitle) {
     *          mMixpanel.getPeople().set("Job Title", newTitle);
     *          ...
     *      }
     *
     *      public void onDestroy() {
     *          mMixpanel.flush();
     *          super.onDestroy();
     *      }
     * }
     *
     * }
     * </pre>
     *
     * @see MixpanelAPI
     */
    public interface People {
        /**
         * @deprecated in 6.2.0
         * NOTE: This method is deprecated. Please use {@link MixpanelAPI#identify(String)} instead.
         *
         *
         * @param distinctId a String that uniquely identifies the user. Users identified with
         *     the same distinct id will be considered to be the same user in Mixpanel,
         *     across all platforms and devices. We recommend choosing a distinct id
         *     that is meaningful to your other systems (for example, a server-side account
         *     identifier)
         *
         * @see MixpanelAPI#identify(String)
         */
        @Deprecated
        void identify(String distinctId);

        /**
         * Sets a single property with the given name and value for this user.
         * The given name and value will be assigned to the user in Mixpanel People Analytics,
         * possibly overwriting an existing property with the same name.
         *
         * @param propertyName The name of the Mixpanel property. This must be a String, for example "Zip Code"
         * @param value The value of the Mixpanel property. For "Zip Code", this value might be the String "90210"
         */
        void set(String propertyName, Object value);

        /**
         * Set a collection of properties on the identified user all at once.
         *
         * @param properties a Map containing the collection of properties you wish to apply
         *      to the identified user. Each key in the Map will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         *
         * See also {@link #set(org.json.JSONObject)}
         */
        void setMap(Map<String, Object> properties);

        /**
         * Set a collection of properties on the identified user all at once.
         *
         * @param properties a JSONObject containing the collection of properties you wish to apply
         *      to the identified user. Each key in the JSONObject will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         */
        void set(JSONObject properties);

        /**
         * Works just like {@link People#set(String, Object)}, except it will not overwrite existing property values. This is useful for properties like "First login date".
         *
         * @param propertyName The name of the Mixpanel property. This must be a String, for example "Zip Code"
         * @param value The value of the Mixpanel property. For "Zip Code", this value might be the String "90210"
         */
        void setOnce(String propertyName, Object value);

        /**
         * Like {@link People#set(String, Object)}, but will not set properties that already exist on a record.
         *
         * @param properties a Map containing the collection of properties you wish to apply
         *      to the identified user. Each key in the Map will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         *
         * See also {@link #setOnce(org.json.JSONObject)}
         */
        void setOnceMap(Map<String, Object> properties);

        /**
         * Like {@link People#set(String, Object)}, but will not set properties that already exist on a record.
         *
         * @param properties a JSONObject containing the collection of properties you wish to apply
         *      to the identified user. Each key in the JSONObject will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         */
        void setOnce(JSONObject properties);

        /**
         * Add the given amount to an existing property on the identified user. If the user does not already
         * have the associated property, the amount will be added to zero. To reduce a property,
         * provide a negative number for the value.
         *
         * @param name the People Analytics property that should have its value changed
         * @param increment the amount to be added to the current value of the named property
         *
         * @see #increment(Map)
         */
        void increment(String name, double increment);

        /**
         * Merge a given JSONObject into the object-valued property named name. If the user does not
         * already have the associated property, an new property will be created with the value of
         * the given updates. If the user already has a value for the given property, the updates will
         * be merged into the existing value, with key/value pairs in updates taking precedence over
         * existing key/value pairs where the keys are the same.
         *
         * @param name the People Analytics property that should have the update merged into it
         * @param updates a JSONObject with keys and values that will be merged into the property
         */
        void merge(String name, JSONObject updates);

        /**
         * Change the existing values of multiple People Analytics properties at once.
         *
         * <p>If the user does not already have the associated property, the amount will
         * be added to zero. To reduce a property, provide a negative number for the value.
         *
         * @param properties A map of String properties names to Long amounts. Each
         *     property associated with a name in the map will have its value changed by the given amount
         *
         * @see #increment(String, double)
         */
        void increment(Map<String, ? extends Number> properties);

        /**
         * Appends a value to a list-valued property. If the property does not currently exist,
         * it will be created as a list of one element. If the property does exist and doesn't
         * currently have a list value, the append will be ignored.
         * @param name the People Analytics property that should have it's value appended to
         * @param value the new value that will appear at the end of the property's list
         */
        void append(String name, Object value);

        /**
         * Adds values to a list-valued property only if they are not already present in the list.
         * If the property does not currently exist, it will be created with the given list as it's value.
         * If the property exists and is not list-valued, the union will be ignored.
         *
         * @param name name of the list-valued property to set or modify
         * @param value an array of values to add to the property value if not already present
         */
        void union(String name, JSONArray value);

        /**
         * Remove value from a list-valued property only if they are already present in the list.
         * If the property does not currently exist, the remove will be ignored.
         * If the property exists and is not list-valued, the remove will be ignored.
         * @param name the People Analytics property that should have it's value removed from
         * @param value the value that will be removed from the property's list
         */
        void remove(String name, Object value);

        /**
         * permanently removes the property with the given name from the user's profile
         * @param name name of a property to unset
         */
        void unset(String name);

        /**
         * Track a revenue transaction for the identified people profile.
         *
         * @param amount the amount of money exchanged. Positive amounts represent purchases or income from the customer, negative amounts represent refunds or payments to the customer.
         * @param properties an optional collection of properties to associate with this transaction.
         */
        void trackCharge(double amount, JSONObject properties);

        /**
         * Permanently clear the whole transaction history for the identified people profile.
         */
        void clearCharges();

        /**
         * Permanently deletes the identified user's record from People Analytics.
         *
         * <p>Calling deleteUser deletes an entire record completely. Any future calls
         * to People Analytics using the same distinct id will create and store new values.
         */
        void deleteUser();

        /**
         * Checks if the people profile is identified or not.
         *
         * @return Whether the current user is identified or not.
         */
        boolean isIdentified();

        /**
         * Returns the string id currently being used to uniquely identify the user associated
         * with events sent using {@link People#set(String, Object)} and {@link People#increment(String, double)}.
         * If no calls to {@link MixpanelAPI#identify(String)} have been made, this method will return null.
         *
         * @deprecated in 6.2.0
         * NOTE: This method is deprecated. Please use {@link MixpanelAPI#getDistinctId()} instead.
         *
         * @return The distinct id associated with updates to People Analytics
         *
         * @see People#identify(String)
         * @see MixpanelAPI#getDistinctId()
         */
        @Deprecated
        String getDistinctId();

        /**
         * Return an instance of Mixpanel people with a temporary distinct id.
         *
         * @param distinctId Unique identifier (distinct_id) that the people object will have
         *
         * @return An instance of {@link MixpanelAPI.People} with the specified distinct_id
         */
        People withIdentity(String distinctId);
    }

    /**
     * Core interface for using Mixpanel Group Analytics features.
     * You can get an instance by calling {@link MixpanelAPI#getGroup(String, Object)}
     *
     * <p>The Group object is used to update properties in a group's Group Analytics record.
     *
     * A typical use case for the Group object might look like this:
     *
     * <pre>
     * {@code
     *
     * public class MainActivity extends Activity {
     *      MixpanelAPI mMixpanel;
     *
     *      public void onCreate(Bundle saved) {
     *          mMixpanel = MixpanelAPI.getInstance(this, "YOUR MIXPANEL API TOKEN");
     *          ...
     *      }
     *
     *      public void companyPlanTypeChanged(string company, String newPlan) {
     *          mMixpanel.getGroup("Company", company).set("Plan Type", newPlan);
     *          ...
     *      }
     *
     *      public void onDestroy() {
     *          mMixpanel.flush();
     *          super.onDestroy();
     *      }
     * }
     *
     * }
     * </pre>
     *
     * @see MixpanelAPI
     */
    public interface Group {
        /**
         * Sets a single property with the given name and value for this group.
         * The given name and value will be assigned to the user in Mixpanel Group Analytics,
         * possibly overwriting an existing property with the same name.
         *
         * @param propertyName The name of the Mixpanel property. This must be a String, for example "Zip Code"
         * @param value The value of the Mixpanel property. For "Zip Code", this value might be the String "90210"
         */
        void set(String propertyName, Object value);

        /**
         * Set a collection of properties on the identified group all at once.
         *
         * @param properties a Map containing the collection of properties you wish to apply
         *      to the identified group. Each key in the Map will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         *
         * See also {@link #set(org.json.JSONObject)}
         */
        void setMap(Map<String, Object> properties);

        /**
         * Set a collection of properties on the identified group all at once.
         *
         * @param properties a JSONObject containing the collection of properties you wish to apply
         *      to the identified group. Each key in the JSONObject will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         */
        void set(JSONObject properties);

        /**
         * Works just like {@link Group#set(String, Object)}, except it will not overwrite existing property values. This is useful for properties like "First login date".
         *
         * @param propertyName The name of the Mixpanel property. This must be a String, for example "Zip Code"
         * @param value The value of the Mixpanel property. For "Zip Code", this value might be the String "90210"
         */
        void setOnce(String propertyName, Object value);

        /**
         * Like {@link Group#set(String, Object)}, but will not set properties that already exist on a record.
         *
         * @param properties a Map containing the collection of properties you wish to apply
         *      to the identified group. Each key in the Map will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         *
         * See also {@link #setOnce(org.json.JSONObject)}
         */
        void setOnceMap(Map<String, Object> properties);

        /**
         * Like {@link Group#set(String, Object)}, but will not set properties that already exist on a record.
         *
         * @param properties a JSONObject containing the collection of properties you wish to apply
         *      to this group. Each key in the JSONObject will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         */
        void setOnce(JSONObject properties);

        /**
         * Adds values to a list-valued property only if they are not already present in the list.
         * If the property does not currently exist, it will be created with the given list as its value.
         * If the property exists and is not list-valued, the union will be ignored.
         *
         * @param name name of the list-valued property to set or modify
         * @param value an array of values to add to the property value if not already present
         */
        void union(String name, JSONArray value);

        /**
         * Remove value from a list-valued property only if it is already present in the list.
         * If the property does not currently exist, the remove will be ignored.
         * If the property exists and is not list-valued, the remove will be ignored.
         *
         * @param name the Group Analytics list-valued property that should have a value removed
         * @param value the value that will be removed from the list
         */
        void remove(String name, Object value);

        /**
         * Permanently removes the property with the given name from the group's profile
         * @param name name of a property to unset
         */
        void unset(String name);


        /**
         * Permanently deletes this group's record from Group Analytics.
         *
         * <p>Calling deleteGroup deletes an entire record completely. Any future calls
         * to Group Analytics using the same group value will create and store new values.
         */
        void deleteGroup();
    }

    /**
     * Attempt to register MixpanelActivityLifecycleCallbacks to the application's event lifecycle.
     * Once registered, we can automatically flush on an app background.
     *
     * This is only available if the android version is >= 16.
     *
     * This function is automatically called when the library is initialized unless you explicitly
     * set com.mixpanel.android.MPConfig.AutoShowMixpanelUpdates to false in your AndroidManifest.xml
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    /* package */ void registerMixpanelActivityLifecycleCallbacks() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (mContext.getApplicationContext() instanceof Application) {
                final Application app = (Application) mContext.getApplicationContext();
                mMixpanelActivityLifecycleCallbacks = new MixpanelActivityLifecycleCallbacks(this, mConfig);
                app.registerActivityLifecycleCallbacks(mMixpanelActivityLifecycleCallbacks);
            } else {
                MPLog.i(LOGTAG, "Context is not an Application, Mixpanel won't be able to automatically flush on an app background.");
            }
        }
    }

    /**
     * Based on the application's event lifecycle this method will determine whether the app
     * is running in the foreground or not.
     *
     * If your build version is below 14 this method will always return false.
     *
     * @return True if the app is running in the foreground.
     */
    public boolean isAppInForeground() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (mMixpanelActivityLifecycleCallbacks != null) {
                return mMixpanelActivityLifecycleCallbacks.isInForeground();
            }
        } else {
            MPLog.e(LOGTAG, "Your build version is below 14. This method will always return false.");
        }
        return false;
    }

    /* package */ void onBackground() {
        if (mConfig.getFlushOnBackground()) {
            flush();
        }
    }

    /* package */ void onForeground() {
        mSessionMetadata.initSession();
    }

    // Package-level access. Used (at least) by MixpanelFCMMessagingService
    // when OS-level events occur.
    /* package */ interface InstanceProcessor {
        void process(MixpanelAPI m);
    }

    /* package */ static void allInstances(InstanceProcessor processor) {
        synchronized (sInstanceMap) {
            for (final Map<Context, MixpanelAPI> contextInstances : sInstanceMap.values()) {
                for (final MixpanelAPI instance : contextInstances.values()) {
                    processor.process(instance);
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////
    // Conveniences for testing. These methods should not be called by
    // non-test client code.

    /* package */ AnalyticsMessages getAnalyticsMessages() {
        return AnalyticsMessages.getInstance(mContext);
    }


    /* package */ PersistentIdentity getPersistentIdentity(final Context context, Future<SharedPreferences> referrerPreferences, final String token) {
        return getPersistentIdentity(context, referrerPreferences, token, null);
    }

    /* package */ PersistentIdentity getPersistentIdentity(final Context context, Future<SharedPreferences> referrerPreferences, final String token, final String instanceName) {
        final SharedPreferencesLoader.OnPrefsLoadedListener listener = new SharedPreferencesLoader.OnPrefsLoadedListener() {
            @Override
            public void onPrefsLoaded(SharedPreferences preferences) {
                final String distinctId = PersistentIdentity.getPeopleDistinctId(preferences);
                if (null != distinctId) {
                    pushWaitingPeopleRecord(distinctId);
                }
            }
        };

        String instanceKey = instanceName != null ? instanceName : token;
        final String prefsName = "com.mixpanel.android.mpmetrics.MixpanelAPI_" + instanceKey;
        final Future<SharedPreferences> storedPreferences = sPrefsLoader.loadPreferences(context, prefsName, listener);

        final String timeEventsPrefsName = "com.mixpanel.android.mpmetrics.MixpanelAPI.TimeEvents_" + instanceKey;
        final Future<SharedPreferences> timeEventsPrefs = sPrefsLoader.loadPreferences(context, timeEventsPrefsName, null);

        final String mixpanelPrefsName = "com.mixpanel.android.mpmetrics.Mixpanel";
        final Future<SharedPreferences> mixpanelPrefs = sPrefsLoader.loadPreferences(context, mixpanelPrefsName, null);

        return new PersistentIdentity(referrerPreferences, storedPreferences, timeEventsPrefs, mixpanelPrefs);
    }

    /* package */ boolean sendAppOpen() {
        return !mConfig.getDisableAppOpenEvent();
    }

    ///////////////////////

    private class PeopleImpl implements People {
        @Override
        public void identify(String distinctId) {
            if (hasOptedOutTracking()) return;
            MPLog.w(LOGTAG, "People.identify() is deprecated and calling it is no longer necessary, " +
                    "please use MixpanelAPI.identify() and set 'usePeople' to true instead");
            if (distinctId == null) {
                MPLog.e(LOGTAG, "Can't identify with null distinct_id.");
                return;
            }
            if (distinctId != mPersistentIdentity.getEventsDistinctId()) {
                MPLog.w(LOGTAG, "Identifying with a distinct_id different from the one being set by MixpanelAPI.identify() is not supported.");
                return;
            }
            identify_people(distinctId);
         }

         private void identify_people(String distinctId) {
             synchronized (mPersistentIdentity) {
                 mPersistentIdentity.setPeopleDistinctId(distinctId);
             }
             pushWaitingPeopleRecord(distinctId);
         }

        @Override
        public void setMap(Map<String, Object> properties) {
            if (hasOptedOutTracking()) return;
            if (null == properties) {
                MPLog.e(LOGTAG, "setMap does not accept null properties");
                return;
            }

            try {
                set(new JSONObject(properties));
            } catch (NullPointerException e) {
                MPLog.w(LOGTAG, "Can't have null keys in the properties of setMap!");
            }
        }

        @Override
        public void set(JSONObject properties) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONObject sendProperties = new JSONObject(mDeviceInfo);
                for (final Iterator<?> iter = properties.keys(); iter.hasNext();) {
                    final String key = (String) iter.next();
                    sendProperties.put(key, properties.get(key));
                }

                final JSONObject message = stdPeopleMessage("$set", sendProperties);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception setting people properties", e);
            }
        }

        @Override
        public void set(String property, Object value) {
            if (hasOptedOutTracking()) return;
            try {
                set(new JSONObject().put(property, value));
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "set", e);
            }
        }

        @Override
        public void setOnceMap(Map<String, Object> properties) {
            if (hasOptedOutTracking()) return;
            if (null == properties) {
                MPLog.e(LOGTAG, "setOnceMap does not accept null properties");
                return;
            }
            try {
                setOnce(new JSONObject(properties));
            } catch (NullPointerException e) {
                MPLog.w(LOGTAG, "Can't have null keys in the properties setOnceMap!");
            }
        }

        @Override
        public void setOnce(JSONObject properties) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONObject message = stdPeopleMessage("$set_once", properties);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception setting people properties");
            }
        }

        @Override
        public void setOnce(String property, Object value) {
            if (hasOptedOutTracking()) return;
            try {
                setOnce(new JSONObject().put(property, value));
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "set", e);
            }
        }

        @Override
        public void increment(Map<String, ? extends Number> properties) {
            if (hasOptedOutTracking()) return;
            final JSONObject json = new JSONObject(properties);
            try {
                final JSONObject message = stdPeopleMessage("$add", json);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception incrementing properties", e);
            }
        }

        @Override
        // Must be thread safe
        public void merge(String property, JSONObject updates) {
            if (hasOptedOutTracking()) return;
            final JSONObject mergeMessage = new JSONObject();
            try {
                mergeMessage.put(property, updates);
                final JSONObject message = stdPeopleMessage("$merge", mergeMessage);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception merging a property", e);
            }
        }

        @Override
        public void increment(String property, double value) {
            if (hasOptedOutTracking()) return;
            final Map<String, Double> map = new HashMap<String, Double>();
            map.put(property, value);
            increment(map);
        }

        @Override
        public void append(String name, Object value) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONObject properties = new JSONObject();
                properties.put(name, value);
                final JSONObject message = stdPeopleMessage("$append", properties);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception appending a property", e);
            }
        }

        @Override
        public void union(String name, JSONArray value) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONObject properties = new JSONObject();
                properties.put(name, value);
                final JSONObject message = stdPeopleMessage("$union", properties);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception unioning a property");
            }
        }

        @Override
        public void remove(String name, Object value) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONObject properties = new JSONObject();
                properties.put(name, value);
                final JSONObject message = stdPeopleMessage("$remove", properties);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception appending a property", e);
            }
        }

        @Override
        public void unset(String name) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONArray names = new JSONArray();
                names.put(name);
                final JSONObject message = stdPeopleMessage("$unset", names);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception unsetting a property", e);
            }
        }

        @Override
        public void trackCharge(double amount, JSONObject properties) {
            if (hasOptedOutTracking()) return;
            final Date now = new Date();
            final DateFormat dateFormat = new SimpleDateFormat(ENGAGE_DATE_FORMAT_STRING, Locale.US);
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
                MPLog.e(LOGTAG, "Exception creating new charge", e);
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
                MPLog.e(LOGTAG, "Exception deleting a user");
            }
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

        private JSONObject stdPeopleMessage(String actionType, Object properties)
                throws JSONException {
            final JSONObject dataObj = new JSONObject();
            final String distinctId = getDistinctId(); // TODO ensure getDistinctId is thread safe
            final String anonymousId = getAnonymousId();
            dataObj.put(actionType, properties);
            dataObj.put("$token", mToken);
            dataObj.put("$time", System.currentTimeMillis());
            dataObj.put("$had_persisted_distinct_id", mPersistentIdentity.getHadPersistedDistinctId());
            if (null != anonymousId) {
                dataObj.put("$device_id", anonymousId);
            }
            if (null != distinctId) {
                dataObj.put("$distinct_id", distinctId);
                dataObj.put("$user_id", distinctId);
            }
            dataObj.put("$mp_metadata", mSessionMetadata.getMetadataForPeople());
            return dataObj;
        }

        @Override
        public boolean isIdentified() {
            return getDistinctId() != null;
        }
    }// PeopleImpl


    private class GroupImpl implements Group {
        private final String mGroupKey;
        private final Object mGroupID;

        public GroupImpl(String groupKey, Object groupID) {
            mGroupKey = groupKey;
            mGroupID = groupID;
        }

        @Override
        public void setMap(Map<String, Object> properties) {
            if (hasOptedOutTracking()) return;
            if (null == properties) {
                MPLog.e(LOGTAG, "setMap does not accept null properties");
                return;
            }

            set(new JSONObject(properties));
        }

        @Override
        public void set(JSONObject properties) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONObject sendProperties = new JSONObject();
                for (final Iterator<?> iter = properties.keys(); iter.hasNext();) {
                    final String key = (String) iter.next();
                    sendProperties.put(key, properties.get(key));
                }

                final JSONObject message = stdGroupMessage("$set", sendProperties);
                recordGroupMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception setting group properties", e);
            }
        }

        @Override
        public void set(String property, Object value) {
            if (hasOptedOutTracking()) return;
            try {
                set(new JSONObject().put(property, value));
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "set", e);
            }
        }

        @Override
        public void setOnceMap(Map<String, Object> properties) {
            if (hasOptedOutTracking()) return;
            if (null == properties) {
                MPLog.e(LOGTAG, "setOnceMap does not accept null properties");
                return;
            }
            try {
                setOnce(new JSONObject(properties));
            } catch (NullPointerException e) {
                MPLog.w(LOGTAG, "Can't have null keys in the properties for setOnceMap!");
            }
        }

        @Override
        public void setOnce(JSONObject properties) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONObject message = stdGroupMessage("$set_once", properties);
                recordGroupMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception setting group properties");
            }
        }

        @Override
        public void setOnce(String property, Object value) {
            if (hasOptedOutTracking()) return;
            try {
                setOnce(new JSONObject().put(property, value));
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Property name cannot be null", e);
            }
        }

        @Override
        public void union(String name, JSONArray value) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONObject properties = new JSONObject();
                properties.put(name, value);
                final JSONObject message = stdGroupMessage("$union", properties);
                recordGroupMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception unioning a property", e);
            }
        }

        @Override
        public void remove(String name, Object value) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONObject properties = new JSONObject();
                properties.put(name, value);
                final JSONObject message = stdGroupMessage("$remove", properties);
                recordGroupMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception removing a property", e);
            }
        }

        @Override
        public void unset(String name) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONArray names = new JSONArray();
                names.put(name);
                final JSONObject message = stdGroupMessage("$unset", names);
                recordGroupMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception unsetting a property", e);
            }
        }

        @Override
        public void deleteGroup() {
            try {
                final JSONObject message = stdGroupMessage("$delete", JSONObject.NULL);
                recordGroupMessage(message);
                mGroups.remove(makeMapKey(mGroupKey, mGroupID));
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception deleting a group", e);
            }
        }

        private JSONObject stdGroupMessage(String actionType, Object properties)
                throws JSONException {
            final JSONObject dataObj = new JSONObject();

            dataObj.put(actionType, properties);
            dataObj.put("$token", mToken);
            dataObj.put("$time", System.currentTimeMillis());
            dataObj.put("$group_key", mGroupKey);
            dataObj.put("$group_id", mGroupID);
            dataObj.put("$mp_metadata", mSessionMetadata.getMetadataForPeople());

            return dataObj;
        }
    }// GroupImpl

    protected void track(String eventName, JSONObject properties, boolean isAutomaticEvent) {
        if (hasOptedOutTracking() || (isAutomaticEvent && !mTrackAutomaticEvents)) {
            return;
        }

        final Long eventBegin;
        synchronized (mEventTimings) {
            eventBegin = mEventTimings.get(eventName);
            mEventTimings.remove(eventName);
            mPersistentIdentity.removeTimedEvent(eventName);
        }

        try {
            final JSONObject messageProps = new JSONObject();

            final Map<String, String> referrerProperties = mPersistentIdentity.getReferrerProperties();
            for (final Map.Entry<String, String> entry : referrerProperties.entrySet()) {
                final String key = entry.getKey();
                final String value = entry.getValue();
                messageProps.put(key, value);
            }

            mPersistentIdentity.addSuperPropertiesToObject(messageProps);

            // Don't allow super properties or referral properties to override these fields,
            // but DO allow the caller to override them in their given properties.
            final double timeSecondsDouble = (System.currentTimeMillis()) / 1000.0;
            final String distinctId = getDistinctId();
            final String anonymousId = getAnonymousId();
            final String userId = getUserId();
            messageProps.put("time", System.currentTimeMillis());
            messageProps.put("distinct_id", distinctId);
            messageProps.put("$had_persisted_distinct_id", mPersistentIdentity.getHadPersistedDistinctId());
            if(anonymousId != null) {
                messageProps.put("$device_id", anonymousId);
            }
            if(userId != null) {
                messageProps.put("$user_id", userId);
            }

            if (null != eventBegin) {
                final double eventBeginDouble = ((double) eventBegin) / 1000.0;
                final double secondsElapsed = timeSecondsDouble - eventBeginDouble;
                messageProps.put("$duration", secondsElapsed);
            }

            if (null != properties) {
                final Iterator<?> propIter = properties.keys();
                while (propIter.hasNext()) {
                    final String key = (String) propIter.next();
                    messageProps.put(key, properties.opt(key));
                }
            }

            final AnalyticsMessages.EventDescription eventDescription =
                    new AnalyticsMessages.EventDescription(eventName, messageProps,
                            mToken, isAutomaticEvent, mSessionMetadata.getMetadataForEvent());
            mMessages.eventsMessage(eventDescription);
        } catch (final JSONException e) {
            MPLog.e(LOGTAG, "Exception tracking event " + eventName, e);
        }
    }

    private void recordPeopleMessage(JSONObject message) {
        if (hasOptedOutTracking()) return;
        mMessages.peopleMessage(new AnalyticsMessages.PeopleDescription(message, mToken));
    }

    private void recordGroupMessage(JSONObject message) {
        if (hasOptedOutTracking()) return;
        if (message.has("$group_key") && message.has("$group_id")) {
            mMessages.groupMessage(new AnalyticsMessages.GroupDescription(message, mToken));
        } else {
            MPLog.e(LOGTAG, "Attempt to update group without key and value--this should not happen.");
        }
    }

    private void pushWaitingPeopleRecord(String distinctId) {
        mMessages.pushAnonymousPeopleMessage(new AnalyticsMessages.PushAnonymousPeopleDescription(distinctId, mToken));
    }

    private static void registerAppLinksListeners(Context context, final MixpanelAPI mixpanel) {
        // Register a BroadcastReceiver to receive com.parse.bolts.measurement_event and track a call to mixpanel
        try {
            final Class<?> clazz = Class.forName("androidx.localbroadcastmanager.content.LocalBroadcastManager");
            final Method methodGetInstance = clazz.getMethod("getInstance", Context.class);
            final Method methodRegisterReceiver = clazz.getMethod("registerReceiver", BroadcastReceiver.class, IntentFilter.class);
            final Object localBroadcastManager = methodGetInstance.invoke(null, context);
            methodRegisterReceiver.invoke(localBroadcastManager, new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final JSONObject properties = new JSONObject();
                    final Bundle args = intent.getBundleExtra("event_args");
                    if (args != null) {
                        for (final String key : args.keySet()) {
                            try {
                                properties.put(key, args.get(key));
                            } catch (final JSONException e) {
                                MPLog.e(APP_LINKS_LOGTAG, "failed to add key \"" + key + "\" to properties for tracking bolts event", e);
                            }
                        }
                    }
                    mixpanel.track("$" + intent.getStringExtra("event_name"), properties);
                }
            }, new IntentFilter("com.parse.bolts.measurement_event"));
        } catch (final InvocationTargetException e) {
            MPLog.d(APP_LINKS_LOGTAG, "Failed to invoke LocalBroadcastManager.registerReceiver() -- App Links tracking will not be enabled due to this exception", e);
        } catch (final ClassNotFoundException e) {
            MPLog.d(APP_LINKS_LOGTAG, "To enable App Links tracking, add implementation 'androidx.localbroadcastmanager:localbroadcastmanager:1.0.0': " + e.getMessage());
        } catch (final NoSuchMethodException e) {
            MPLog.d(APP_LINKS_LOGTAG, "To enable App Links tracking, add implementation 'androidx.localbroadcastmanager:localbroadcastmanager:1.0.0': " + e.getMessage());
        } catch (final IllegalAccessException e) {
            MPLog.d(APP_LINKS_LOGTAG, "App Links tracking will not be enabled due to this exception: " + e.getMessage());
        }
    }

    private static void checkIntentForInboundAppLink(Context context) {
        // call the Bolts getTargetUrlFromInboundIntent method simply for a side effect
        // if the intent is the result of an App Link, it'll trigger al_nav_in
        // https://github.com/BoltsFramework/Bolts-Android/blob/1.1.2/Bolts/src/bolts/AppLinks.java#L86
        if (context instanceof Activity) {
            try {
                final Class<?> clazz = Class.forName("bolts.AppLinks");
                final Intent intent = ((Activity) context).getIntent();
                final Method getTargetUrlFromInboundIntent = clazz.getMethod("getTargetUrlFromInboundIntent", Context.class, Intent.class);
                getTargetUrlFromInboundIntent.invoke(null, context, intent);
            } catch (final InvocationTargetException e) {
                MPLog.d(APP_LINKS_LOGTAG, "Failed to invoke bolts.AppLinks.getTargetUrlFromInboundIntent() -- Unable to detect inbound App Links", e);
            } catch (final ClassNotFoundException e) {
                MPLog.d(APP_LINKS_LOGTAG, "Please install the Bolts library >= 1.1.2 to track App Links: " + e.getMessage());
            } catch (final NoSuchMethodException e) {
                MPLog.d(APP_LINKS_LOGTAG, "Please install the Bolts library >= 1.1.2 to track App Links: " + e.getMessage());
            } catch (final IllegalAccessException e) {
                MPLog.d(APP_LINKS_LOGTAG, "Unable to detect inbound App Links: " + e.getMessage());
            }
        } else {
            MPLog.d(APP_LINKS_LOGTAG, "Context is not an instance of Activity. To detect inbound App Links, pass an instance of an Activity to getInstance.");
        }
    }

    /* package */ Context getContext() {
        return mContext;
    }

    private final Context mContext;
    private final AnalyticsMessages mMessages;
    private final MPConfig mConfig;
    private final Boolean mTrackAutomaticEvents;
    private final String mToken;
    private final PeopleImpl mPeople;
    private final Map<String, GroupImpl> mGroups;
    private final PersistentIdentity mPersistentIdentity;
    private final Map<String, String> mDeviceInfo;
    private final Map<String, Long> mEventTimings;
    private MixpanelActivityLifecycleCallbacks mMixpanelActivityLifecycleCallbacks;
    private final SessionMetadata mSessionMetadata;

    // Maps each token to a singleton MixpanelAPI instance
    private static final Map<String, Map<Context, MixpanelAPI>> sInstanceMap = new HashMap<String, Map<Context, MixpanelAPI>>();
    private static final SharedPreferencesLoader sPrefsLoader = new SharedPreferencesLoader();
    private static Future<SharedPreferences> sReferrerPrefs;

    private static final String LOGTAG = "MixpanelAPI.API";
    private static final String APP_LINKS_LOGTAG = "MixpanelAPI.AL";
    private static final String ENGAGE_DATE_FORMAT_STRING = "yyyy-MM-dd'T'HH:mm:ss";
}
