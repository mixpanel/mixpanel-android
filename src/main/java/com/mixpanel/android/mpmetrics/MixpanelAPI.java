package com.mixpanel.android.mpmetrics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.mixpanel.android.R;
import com.mixpanel.android.viewcrawler.UpdatesFromMixpanel;
import com.mixpanel.android.viewcrawler.TrackingDebug;
import com.mixpanel.android.viewcrawler.ViewCrawler;
import com.mixpanel.android.surveys.SurveyActivity;
import com.mixpanel.android.util.ActivityImageUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Core class for interacting with Mixpanel Analytics.
 *
 * <p>Call {@link #getInstance(Context, String)} with
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
 * <tt>android.permission.INTERNET</tt>. In addition, to preserve
 * battery life, messages to Mixpanel servers may not be sent immediately
 * when you call <tt>track</tt> or {@link People#set(String, Object)}.
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
 * updating People Analytics records with {@link People} and registering for
 * and receiving push notifications with {@link People#initPushHandling(String)}.
 *
 * <p>There are also <a href="https://mixpanel.com/docs/">step-by-step getting started documents</a>
 * available at mixpanel.com
 *
 * @see <a href="https://mixpanel.com/docs/integration-libraries/android">getting started documentation for tracking events</a>
 * @see <a href="https://mixpanel.com/docs/people-analytics/android">getting started documentation for People Analytics</a>
 * @see <a href="https://mixpanel.com/docs/people-analytics/android-push">getting started with push notifications for Android</a>
 * @see <a href="https://github.com/mixpanel/sample-android-mixpanel-integration">The Mixpanel Android sample application</a>
 */
public class MixpanelAPI {
    /**
     * String version of the library.
     */
    public static final String VERSION = MPConfig.VERSION;

    /**
     * Declare a string-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mixpanel A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     */
    public static Tweak<String> stringTweak(String tweakName, String defaultValue) {
        return sSharedTweaks.stringTweak(tweakName, defaultValue);
    }

    /**
     * Declare a boolean-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mixpanel A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     */
    public static Tweak<Boolean> booleanTweak(String tweakName, boolean defaultValue) {
        return sSharedTweaks.booleanTweak(tweakName, defaultValue);
    }

    /**
     * Declare a double-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mixpanel A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     */
    public static Tweak<Double> doubleTweak(String tweakName, double defaultValue) {
        return sSharedTweaks.doubleTweak(tweakName, defaultValue);
    }

    /**
     * Declare a float-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mixpanel A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     */
    public static Tweak<Float> floatTweak(String tweakName, float defaultValue) {
        return sSharedTweaks.floatTweak(tweakName, defaultValue);
    }

    /**
     * Declare a long-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mixpanel A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     */
    public static Tweak<Long> longTweak(String tweakName, long defaultValue) {
        return sSharedTweaks.longTweak(tweakName, defaultValue);
    }

    /**
     * Declare an int-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mixpanel A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     */
    public static Tweak<Integer> intTweak(String tweakName, int defaultValue) {
        return sSharedTweaks.intTweak(tweakName, defaultValue);
    }

    /**
     * Declare short-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mixpanel A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     */
    public static Tweak<Short> shortTweak(String tweakName, short defaultValue) {
        return sSharedTweaks.shortTweak(tweakName, defaultValue);
    }

    /**
     * Declare byte-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mixpanel A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     */
    public static Tweak<Byte> byteTweak(String tweakName, byte defaultValue) {
        return sSharedTweaks.byteTweak(tweakName, defaultValue);
    }

    /**
     * You shouldn't instantiate MixpanelAPI objects directly.
     * Use MixpanelAPI.getInstance to get an instance.
     */
    MixpanelAPI(Context context, Future<SharedPreferences> referrerPreferences, String token) {
        this(context, referrerPreferences, token, MPConfig.getInstance(context));
    }

    /**
     * You shouldn't instantiate MixpanelAPI objects directly.
     * Use MixpanelAPI.getInstance to get an instance.
     */
    MixpanelAPI(Context context, Future<SharedPreferences> referrerPreferences, String token, MPConfig config) {
        mContext = context;
        mToken = token;
        mEventTimings = new HashMap<String, Long>();
        mPeople = new PeopleImpl();
        mConfig = config;

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
            Log.e(LOGTAG, "Exception getting app version name", e);
        }
        mDeviceInfo = Collections.unmodifiableMap(deviceInfo);

        mUpdatesFromMixpanel = constructUpdatesFromMixpanel(context, token);
        mTrackingDebug = constructTrackingDebug();
        mPersistentIdentity = getPersistentIdentity(context, referrerPreferences, token);
        mUpdatesListener = constructUpdatesListener();
        mDecideMessages = constructDecideUpdates(token, mUpdatesListener, mUpdatesFromMixpanel);

        // TODO reading persistent identify immediately forces the lazy load of the preferences, and defeats the
        // purpose of PersistentIdentity's laziness.
        String decideId = mPersistentIdentity.getPeopleDistinctId();
        if (null == decideId) {
            decideId = mPersistentIdentity.getEventsDistinctId();
        }
        mDecideMessages.setDistinctId(decideId);
        mMessages = getAnalyticsMessages();

        if (!mConfig.getDisableDecideChecker()) {
            mMessages.installDecideCheck(mDecideMessages);
        }

        registerMixpanelActivityLifecycleCallbacks();

        if (sendAppOpen()) {
            track("$app_open", null);
        }

        mUpdatesFromMixpanel.startUpdates();
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
     * @return an instance of MixpanelAPI associated with your project
     */
    public static MixpanelAPI getInstance(Context context, String token) {
        if (null == token || null == context) {
            return null;
        }
        synchronized (sInstanceMap) {
            final Context appContext = context.getApplicationContext();

            if (null == sReferrerPrefs) {
                sReferrerPrefs = sPrefsLoader.loadPreferences(context, MPConfig.REFERRER_PREFS_NAME, null);
            }

            Map <Context, MixpanelAPI> instances = sInstanceMap.get(token);
            if (null == instances) {
                instances = new HashMap<Context, MixpanelAPI>();
                sInstanceMap.put(token, instances);
            }

            MixpanelAPI instance = instances.get(appContext);
            if (null == instance && ConfigurationChecker.checkBasicConfiguration(appContext)) {
                instance = new MixpanelAPI(appContext, sReferrerPrefs, token);
                registerAppLinksListeners(context, instance);
                instances.put(appContext, instance);
            }

            checkIntentForInboundAppLink(context);

            return instance;
        }
    }

    /**
     * This call is a no-op, and will be removed in future versions.
     *
     * @deprecated in 4.0.0, use com.mixpanel.android.MPConfig.FlushInterval application metadata instead
     */
    @Deprecated
    public static void setFlushInterval(Context context, long milliseconds) {
        Log.i(
            LOGTAG,
            "MixpanelAPI.setFlushInterval is deprecated. Calling is now a no-op.\n" +
            "    To set a custom Mixpanel flush interval for your application, add\n" +
            "    <meta-data android:name=\"com.mixpanel.android.MPConfig.FlushInterval\" android:value=\"YOUR_INTERVAL\" />\n" +
            "    to the <application> section of your AndroidManifest.xml."
        );
    }

    /**
     * This call is a no-op, and will be removed in future versions of the library.
     *
     * @deprecated in 4.0.0, use com.mixpanel.android.MPConfig.EventsFallbackEndpoint, com.mixpanel.android.MPConfig.PeopleFallbackEndpoint, or com.mixpanel.android.MPConfig.DecideFallbackEndpoint instead
     */
    @Deprecated
    public static void enableFallbackServer(Context context, boolean enableIfTrue) {
        Log.i(
            LOGTAG,
            "MixpanelAPI.enableFallbackServer is deprecated. This call is a no-op.\n" +
            "    To enable fallback in your application, add\n" +
            "    <meta-data android:name=\"com.mixpanel.android.MPConfig.DisableFallback\" android:value=\"false\" />\n" +
            "    to the <application> section of your AndroidManifest.xml."
        );
    }

    /**
     * This function creates a distinct_id alias from alias to original. If original is null, then it will create an alias
     * to the current events distinct_id, which may be the distinct_id randomly generated by the Mixpanel library
     * before {@link #identify(String)} is called.
     *
     * <p>This call does not identify the user after. You must still call both {@link #identify(String)} and
     * {@link People#identify(String)} if you wish the new alias to be used for Events and People.
     *
     * @param alias the new distinct_id that should represent original.
     * @param original the old distinct_id that alias will be mapped to.
     */
    public void alias(String alias, String original) {
        if (original == null) {
            original = getDistinctId();
        }
        if (alias.equals(original)) {
            Log.w(LOGTAG, "Attempted to alias identical distinct_ids " + alias + ". Alias message will not be sent.");
            return;
        }

        try {
            final JSONObject j = new JSONObject();
            j.put("alias", alias);
            j.put("original", original);
            track("$create_alias", j);
        } catch (final JSONException e) {
            Log.e(LOGTAG, "Failed to alias", e);
        }
        flush();
    }

    /**
     * Associate all future calls to {@link #track(String, JSONObject)} with the user identified by
     * the given distinct id.
     *
     * <p>This call does not identify the user for People Analytics;
     * to do that, see {@link People#identify(String)}. Mixpanel recommends using
     * the same distinct_id for both calls, and using a distinct_id that is easy
     * to associate with the given user, for example, a server-side account identifier.
     *
     * <p>Calls to {@link #track(String, JSONObject)} made before corresponding calls to
     * identify will use an internally generated distinct id, which means it is best
     * to call identify early to ensure that your Mixpanel funnels and retention
     * analytics can continue to track the user throughout their lifetime. We recommend
     * calling identify as early as you can.
     *
     * <p>Once identify is called, the given distinct id persists across restarts of your
     * application.
     *
     * @param distinctId a string uniquely identifying this user. Events sent to
     *     Mixpanel using the same disinct_id will be considered associated with the
     *     same visitor/customer for retention and funnel reporting, so be sure that the given
     *     value is globally unique for each individual user you intend to track.
     *
     * @see People#identify(String)
     */
    public void identify(String distinctId) {
        synchronized (mPersistentIdentity) {
            mPersistentIdentity.setEventsDistinctId(distinctId);
            String decideId = mPersistentIdentity.getPeopleDistinctId();
            if (null == decideId) {
                decideId = mPersistentIdentity.getEventsDistinctId();
            }
            mDecideMessages.setDistinctId(decideId);
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
        final long writeTime = System.currentTimeMillis();
        synchronized (mEventTimings) {
            mEventTimings.put(eventName, writeTime);
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
     * @param properties A Map containing the key value pairs of the properties to include in this event.
     *                   Pass null if no extra properties exist.
     *
     * See also {@link #track(String, org.json.JSONObject)}
     */
    public void trackMap(String eventName, Map<String, Object> properties) {
        if (null == properties) {
            track(eventName, null);
        } else {
            try {
                track(eventName, new JSONObject(properties));
            } catch (NullPointerException e) {
                Log.w(LOGTAG, "Can't have null keys in the properties of trackMap!");
            }
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
    // DO NOT DOCUMENT, but track() must be thread safe since it is used to track events in
    // notifications from the UI thread, which might not be our MixpanelAPI "home" thread.
    // This MAY CHANGE IN FUTURE RELEASES, so minimize code that assumes thread safety
    // (and perhaps document that code here).
    public void track(String eventName, JSONObject properties) {
        final Long eventBegin;
        synchronized (mEventTimings) {
            eventBegin = mEventTimings.get(eventName);
            mEventTimings.remove(eventName);
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
            final long timeSeconds = (long) timeSecondsDouble;
            messageProps.put("time", timeSeconds);
            messageProps.put("distinct_id", getDistinctId());

            if (null != eventBegin) {
                final double eventBeginDouble = ((double) eventBegin) / 1000.0;
                final double secondsElapsed = timeSecondsDouble - eventBeginDouble;
                messageProps.put("$duration", secondsElapsed);
            }

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

            if (null != mTrackingDebug) {
                mTrackingDebug.reportTrack(eventName);
            }
        } catch (final JSONException e) {
            Log.e(LOGTAG, "Exception tracking event " + eventName, e);
        }
    }

    /**
     * Equivalent to {@link #track(String, JSONObject)} with a null argument for properties.
     * Consider adding properties to your tracking to get the best insights and experience from Mixpanel.
     * @param eventName the name of the event to send
     */
    public void track(String eventName) {
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
        mMessages.postToServer();
    }

    /**
     * Returns a json object of the user's current super properties
     *
     *<p>SuperProperties are a collection of properties that will be sent with every event to Mixpanel,
     * and persist beyond the lifetime of your application.
     */
      public JSONObject getSuperProperties() {
          JSONObject ret = new JSONObject();
          mPersistentIdentity.addSuperPropertiesToObject(ret);
          return ret;
      }

    /**
     * Returns the string id currently being used to uniquely identify the user associated
     * with events sent using {@link #track(String, JSONObject)}. Before any calls to
     * {@link #identify(String)}, this will be an id automatically generated by the library.
     *
     * <p>The id returned by getDistinctId is independent of the distinct id used to identify
     * any People Analytics properties in Mixpanel. To read and write that identifier,
     * use {@link People#identify(String)} and {@link People#getDistinctId()}.
     *
     * @return The distinct id associated with event tracking
     *
     * @see #identify(String)
     * @see People#getDistinctId()
     */
    public String getDistinctId() {
        return mPersistentIdentity.getEventsDistinctId();
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
        if (null == superProperties) {
            Log.e(LOGTAG, "registerSuperPropertiesMap does not accept null properties");
            return;
        }

        try {
            registerSuperProperties(new JSONObject(superProperties));
        } catch (NullPointerException e) {
            Log.w(LOGTAG, "Can't have null keys in the properties of registerSuperPropertiesMap!");
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
        if (null == superProperties) {
            Log.e(LOGTAG, "registerSuperPropertiesOnceMap does not accept null properties");
            return;
        }

        try {
            registerSuperPropertiesOnce(new JSONObject(superProperties));
        } catch (NullPointerException e) {
            Log.w(LOGTAG, "Can't have null keys in the properties of registerSuperPropertiesOnce!");
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
        mPersistentIdentity.updateSuperProperties(update);
    }

    /**
     * Returns a Mixpanel.People object that can be used to set and increment
     * People Analytics properties.
     *
     * @return an instance of {@link People} that you can use to update
     *     records in Mixpanel People Analytics and manage Mixpanel Google Cloud Messaging notifications.
     */
    public People getPeople() {
        return mPeople;
    }

    /**
     * Clears all distinct_ids, superProperties, and push registrations from persistent storage.
     * Will not clear referrer information.
     */
    public void reset() {
        // Will clear distinct_ids, superProperties,
        // and waiting People Analytics properties. Will have no effect
        // on messages already queued to send with AnalyticsMessages.
        mPersistentIdentity.clearPreferences();
    }

    /**
     * Returns an unmodifiable map that contains the device description properties
     * that will be sent to Mixpanel. These are not all of the default properties,
     * but are a subset that are dependant on the user's device or installed version
     * of the host application, and are guaranteed not to change while the app is running.
     */
    public Map<String, String> getDeviceInfo() {
        return mDeviceInfo;
    }

    /**
     * Core interface for using Mixpanel People Analytics features.
     * You can get an instance by calling {@link MixpanelAPI#getPeople()}
     *
     * <p>The People object is used to update properties in a user's People Analytics record,
     * and to manage the receipt of push notifications sent via Mixpanel Engage.
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
     *          mMixpanel.getPeople().identify("A UNIQUE ID FOR THIS USER");
     *          mMixpanel.getPeople().initPushHandling("YOUR 12 DIGIT GOOGLE SENDER API");
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
         * Associate future calls to {@link #set(JSONObject)}, {@link #increment(Map)},
         * and {@link #initPushHandling(String)} with a particular People Analytics user.
         *
         * <p>All future calls to the People object will rely on this value to assign
         * and increment properties. The user identification will persist across
         * restarts of your application. We recommend calling
         * People.identify as soon as you know the distinct id of the user.
         *
         * @param distinctId a String that uniquely identifies the user. Users identified with
         *     the same distinct id will be considered to be the same user in Mixpanel,
         *     across all platforms and devices. We recommend choosing a distinct id
         *     that is meaningful to your other systems (for example, a server-side account
         *     identifier), and using the same distinct id for both calls to People.identify
         *     and {@link MixpanelAPI#identify(String)}
         *
         * @see MixpanelAPI#identify(String)
         */
        public void identify(String distinctId);

        /**
         * Sets a single property with the given name and value for this user.
         * The given name and value will be assigned to the user in Mixpanel People Analytics,
         * possibly overwriting an existing property with the same name.
         *
         * @param propertyName The name of the Mixpanel property. This must be a String, for example "Zip Code"
         * @param value The value of the Mixpanel property. For "Zip Code", this value might be the String "90210"
         */
        public void set(String propertyName, Object value);

        /**
         * Set a collection of properties on the identified user all at once.
         *
         * @param properties a Map containing the collection of properties you wish to apply
         *      to the identified user. Each key in the Map will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         *
         * See also {@link #set(org.json.JSONObject)}
         */
        public void setMap(Map<String, Object> properties);

        /**
         * Set a collection of properties on the identified user all at once.
         *
         * @param properties a JSONObject containing the collection of properties you wish to apply
         *      to the identified user. Each key in the JSONObject will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         */
        public void set(JSONObject properties);

        /**
         * Works just like {@link People#set(String, Object)}, except it will not overwrite existing property values. This is useful for properties like "First login date".
         *
         * @param propertyName The name of the Mixpanel property. This must be a String, for example "Zip Code"
         * @param value The value of the Mixpanel property. For "Zip Code", this value might be the String "90210"
         */
        public void setOnce(String propertyName, Object value);

        /**
         * Like {@link People#set(String, Object)}, but will not set properties that already exist on a record.
         *
         * @param properties a Map containing the collection of properties you wish to apply
         *      to the identified user. Each key in the Map will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         *
         * See also {@link #setOnce(org.json.JSONObject)}
         */
        public void setOnceMap(Map<String, Object> properties);

        /**
         * Like {@link People#set(String, Object)}, but will not set properties that already exist on a record.
         *
         * @param properties a JSONObject containing the collection of properties you wish to apply
         *      to the identified user. Each key in the JSONObject will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         */
        public void setOnce(JSONObject properties);

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
        public void increment(String name, double increment);

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
        public void merge(String name, JSONObject updates);

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
        public void increment(Map<String, ? extends Number> properties);

        /**
         * Appends a value to a list-valued property. If the property does not currently exist,
         * it will be created as a list of one element. If the property does exist and doesn't
         * currently have a list value, the append will be ignored.
         * @param name the People Analytics property that should have it's value appended to
         * @param value the new value that will appear at the end of the property's list
         */
        public void append(String name, Object value);

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
        public void trackCharge(double amount, JSONObject properties);

        /**
         * Permanently clear the whole transaction history for the identified people profile.
         */
        public void clearCharges();

        /**
         * Permanently deletes the identified user's record from People Analytics.
         *
         * <p>Calling deleteUser deletes an entire record completely. Any future calls
         * to People Analytics using the same distinct id will create and store new values.
         */
        public void deleteUser();

        /**
         * Enable end-to-end Google Cloud Messaging (GCM) from Mixpanel.
         *
         * <p>Calling this method will allow the Mixpanel libraries to handle GCM user
         * registration, and enable Mixpanel to show alerts when GCM messages arrive.
         *
         * <p>To use {@link People#initPushHandling}, you will need to add the following to your application manifest:
         *
         * <pre>
         * {@code
         * <receiver android:name="com.mixpanel.android.mpmetrics.GCMReceiver"
         *           android:permission="com.google.android.c2dm.permission.SEND" >
         *     <intent-filter>
         *         <action android:name="com.google.android.c2dm.intent.RECEIVE" />
         *         <action android:name="com.google.android.c2dm.intent.REGISTRATION" />
         *         <category android:name="YOUR_PACKAGE_NAME" />
         *     </intent-filter>
         * </receiver>
         * }
         * </pre>
         *
         * Be sure to replace "YOUR_PACKAGE_NAME" with the name of your package. For
         * more information and a list of necessary permissions, see {@link GCMReceiver}.
         *
         * <p>If you're planning to use end-to-end support for Messaging, we recommend you
         * call this method immediately after calling {@link People#identify(String)}, likely
         * early in your application's life cycle. (for example, in the onCreate method of your
         * main application activity.)
         *
         * <p>Calls to {@link People#initPushHandling} should not be mixed with calls to
         * {@link #setPushRegistrationId(String)} and {@link #clearPushRegistrationId()}
         * in the same application. Application authors should choose one or the other
         * method for handling Mixpanel GCM messages.
         *
         * @param senderID of the Google API Project that registered for Google Cloud Messaging
         *     You can find your ID by looking at the URL of in your Google API Console
         *     at https://code.google.com/apis/console/; it is the twelve digit number after
         *     after "#project:" in the URL address bar on console pages.
         *
         * @see com.mixpanel.android.mpmetrics.GCMReceiver
         * @see <a href="https://mixpanel.com/docs/people-analytics/android-push">Getting Started with Android Push Notifications</a>
         */
        public void initPushHandling(String senderID);

        /**
         * Manually send a Google Cloud Messaging registration id to Mixpanel.
         *
         * <p>If you are handling Google Cloud Messages in your own application, but would like to
         * allow Mixpanel to handle messages originating from Mixpanel campaigns, you should
         * call setPushRegistrationId with the "registration_id" property of the
         * com.google.android.c2dm.intent.REGISTRATION intent when it is received.
         *
         * <p>setPushRegistrationId should only be called after {@link #identify(String)} has been called.
         *
         * <p>Calls to {@link People#setPushRegistrationId} should not be mixed with calls to {@link #initPushHandling(String)}
         * in the same application. In addition, applications that call setPushRegistrationId
         * should also call {@link #clearPushRegistrationId()} when they receive an intent to unregister
         * (a com.google.android.c2dm.intent.REGISTRATION intent with getStringExtra("unregistered") != null)
         *
         * @param registrationId the result of calling intent.getStringExtra("registration_id")
         *     on a com.google.android.c2dm.intent.REGISTRATION intent
         *
         * @see #initPushHandling(String)
         * @see #clearPushRegistrationId()
         */
        public void setPushRegistrationId(String registrationId);

        /**
         * Manually clear a current Google Cloud Messaging registration id from Mixpanel.
         *
         * <p>If you are handling Google Cloud Messages in your own application, you should
         * call this method when your application receives a com.google.android.c2dm.intent.REGISTRATION
         * with getStringExtra("unregistered") != null
         *
         * <p>{@link People#clearPushRegistrationId} should only be called after {@link #identify(String)} has been called.
         *
         * <p>In general, all applications that call {@link #setPushRegistrationId(String)} should include a call to
         * removePushRegistrationId, and no applications that call {@link #initPushHandling(String)} should
         * call removePushRegistrationId
         */
        public void clearPushRegistrationId();

        /**
         * Returns the string id currently being used to uniquely identify the user associated
         * with events sent using {@link People#set(String, Object)} and {@link People#increment(String, double)}.
         * If no calls to {@link People#identify(String)} have been made, this method will return null.
         *
         * <p>The id returned by getDistinctId is independent of the distinct id used to identify
         * any events sent with {@link MixpanelAPI#track(String, JSONObject)}. To read and write that identifier,
         * use {@link MixpanelAPI#identify(String)} and {@link MixpanelAPI#getDistinctId()}.
         *
         * @return The distinct id associated with updates to People Analytics
         *
         * @see People#identify(String)
         * @see MixpanelAPI#getDistinctId()
         */
        public String getDistinctId();

        /**
         * If a survey is currently available, this method will launch an activity that shows a
         * survey to the user and then send the responses to Mixpanel.
         *
         * <p>The survey activity will use the root of the given view to take a screenshot
         * for its background.
         *
         * <p>It is safe to call this method any time you want to potentially display an in-app notification.
         * This method will be a no-op if there is already a survey or in-app notification being displayed.
         * Thus, if you have both surveys and in-app notification campaigns built in Mixpanel, you may call
         * both this and {@link People#showNotificationIfAvailable(Activity)} right after each other, and
         * only one of them will be displayed.
         *
         * <p>This method is a no-op in environments with
         * Android API before JellyBean/API level 16.
         *
         * @param parent the Activity that this Survey will be displayed on top of. A snapshot will be
         * taken of parent to be used as a blurred background.
         */
        public void showSurveyIfAvailable(Activity parent);

        /**
         * Shows an in-app notification to the user if one is available. If the notification
         * is a mini notification, this method will attach and remove a Fragment to parent.
         * The lifecycle of the Fragment will be handled entirely by the Mixpanel library.
         *
         * <p>If the notification is a takeover notification, a SurveyActivity will be launched to
         * display the Takeover notification.
         *
         * <p>It is safe to call this method any time you want to potentially display an in-app notification.
         * This method will be a no-op if there is already a survey or in-app notification being displayed.
         * Thus, if you have both surveys and in-app notification campaigns built in Mixpanel, you may call
         * both this and {@link People#showSurveyIfAvailable(Activity)} right after each other, and
         * only one of them will be displayed.
         *
         * <p>This method is a no-op in environments with
         * Android API before JellyBean/API level 16.
         *
         * @param parent the Activity that the mini notification will be displayed in, or the Activity
         * that will be used to launch SurveyActivity for the takeover notification.
         */
        public void showNotificationIfAvailable(Activity parent);

        /**
         * Applies A/B test changes, if they are present. By default, your application will attempt
         * to join available experiments any time an activity is resumed, but you can disable this
         * automatic behavior by adding the following tag to the &lt;application&gt; tag in your AndroidManifest.xml
         * {@code
         *     <meta-data android:name="com.mixpanel.android.MPConfig.AutoShowMixpanelUpdates"
         *                android:value="false" />
         * }
         *
         * If you disable AutoShowMixpanelUpdates, you'll need to call joinExperimentIfAvailable to
         * join or clear existing experiments. If you want to display a loading screen or otherwise
         * wait for experiments to load from the server before you apply them, you can use
         * {@link #addOnMixpanelUpdatesReceivedListener(OnMixpanelUpdatesReceivedListener)} to
         * be informed that new experiments are ready.
         */
        public void joinExperimentIfAvailable();

        /**
         * Shows the given in-app notification to the user. Display will occur just as if the
         * notification was shown via showNotificationIfAvailable. In most cases, it is
         * easier and more efficient to use showNotificationIfAvailable.
         *
         * @param notif the {@link com.mixpanel.android.mpmetrics.InAppNotification} to show
         *
         * @param parent the Activity that the mini notification will be displayed in, or the Activity
         * that will be used to launch SurveyActivity for the takeover notification.
         */
        public void showGivenNotification(InAppNotification notif, Activity parent);

        /**
         * Sends an event to Mixpanel that includes the automatic properties associated
         * with the given notification. In most cases this is not required, unless you're
         * not showing notifications using the library-provided in views and activities.
         *
         * @param eventName the name to use when the event is tracked.
         *
         * @param notif the {@link com.mixpanel.android.mpmetrics.InAppNotification} associated with the event you'd like to track.
         */
        public void trackNotification(String eventName, InAppNotification notif);

        /**
         * Returns a Survey object if one is available and being held by the library, or null if
         * no survey is currently available. Callers who want to display surveys with their own UI
         * should call this method to get the Survey data. A given survey will be returned only once
         * from this method, so callers should be ready to consume any non-null return value.
         *
         * <p>This function will always return quickly, and will not cause any communication with
         * Mixpanel's servers, so it is safe to call this from the UI thread.
         *
         * @return a Survey object if one is available, null otherwise.
         */
        public Survey getSurveyIfAvailable();

        /**
         * Returns an InAppNotification object if one is available and being held by the library, or null if
         * no survey is currently available. Callers who want to display in-app notifications should call this
         * method periodically. A given InAppNotification will be returned only once from this method, so callers
         * should be ready to consume any non-null return value.
         *
         * <p>This function will return quickly, and will not cause any communication with
         * Mixpanel's servers, so it is safe to call this from the UI thread.
         *
         * Note: you must call call {@link People#trackNotificationSeen(InAppNotification)} or you will
         * receive the same {@link com.mixpanel.android.mpmetrics.InAppNotification} again the
         * next time notifications are refreshed from Mixpanel's servers (on identify, or when
         * your app is destroyed and re-created)
         *
         * @return an InAppNotification object if one is available, null otherwise.
         */
        public InAppNotification getNotificationIfAvailable();

        /**
         * Tells MixPanel that you have handled an {@link com.mixpanel.android.mpmetrics.InAppNotification}
         * in the case where you are manually dealing with your notifications ({@link People#getNotificationIfAvailable()}).
         *
         * Note: if you do not acknowledge the notification you will receive it again each time
         * you call {@link People#identify(String)} and then call {@link People#getNotificationIfAvailable()}
         *
         * @param notif the notification to track (no-op on null)
         */
        void trackNotificationSeen(InAppNotification notif);

        /**
         * Shows a survey identified by id. The behavior of this is otherwise identical to
         * {@link People#showSurveyIfAvailable(Activity)}.
         *
         * @param id the id of the Survey you wish to show.
         * @param parent the Activity that this Survey will be displayed on top of. A snapshot will be
         * taken of parent to be used as a blurred background.
         */
        public void showSurveyById(int id, final Activity parent);

        /**
         * Shows an in-app notification identified by id. The behavior of this is otherwise identical to
         * {@link People#showNotificationIfAvailable(Activity)}.
         *
         * @param id the id of the InAppNotification you wish to show.
         * @param parent  the Activity that the mini notification will be displayed in, or the Activity
         * that will be used to launch SurveyActivity for the takeover notification.
         */
        public void showNotificationById(int id, final Activity parent);

        /**
         * Return an instance of Mixpanel people with a temporary distinct id.
         * This is used by Mixpanel Surveys but is likely not needed in your code.
         * Instances returned by withIdentity will not check decide with the given distinctId.
         */
        public People withIdentity(String distinctId);

        /**
         * Adds a new listener that will receive a callback when new updates from Mixpanel
         * (like surveys, in-app notifications, or A/B test experiments) are discovered. Most users of the library
         * will not need this method, since surveys, in-app notifications, and experiments are
         * applied automatically to your application by default.
         *
         * <p>The given listener will be called when a new batch of updates is detected. Handlers
         * should be prepared to handle the callback on an arbitrary thread.
         *
         * <p>The listener will be called when new surveys, in-app notifications, or experiments
         * are detected as available. That means you wait to call {@link People#showSurveyIfAvailable(Activity)},
         * {@link People#showNotificationIfAvailable(Activity)}, and {@link People#joinExperimentIfAvailable()}
         * to show content and updates that have been delivered to your app. (You can also call these
         * functions whenever else you would like, they're inexpensive and will do nothing if no
         * content is available.)
         *
         * @param listener the listener to add
         */
        public void addOnMixpanelUpdatesReceivedListener(OnMixpanelUpdatesReceivedListener listener);

        /**
         * Removes a listener previously registered with addOnMixpanelUpdatesReceivedListener.
         *
         * @param listener the listener to add
         */
        public void removeOnMixpanelUpdatesReceivedListener(OnMixpanelUpdatesReceivedListener listener);

        /**
         * @deprecated in 4.1.0, Use showSurveyIfAvailable() instead.
         */
        @Deprecated
        public void showSurvey(Survey s, Activity parent);

        /**
         * @deprecated in 4.1.0, Use getSurveyIfAvailable() instead.
         */
        @Deprecated
        public void checkForSurvey(SurveyCallbacks callbacks);

        /**
         * @deprecated in 4.1.0, Use getSurveyIfAvailable() instead.
         */
        @Deprecated
        public void checkForSurvey(SurveyCallbacks callbacks, Activity parent);
    }

    /**
     * This method is a no-op, kept for compatibility purposes.
     *
     * To enable verbose logging about communication with Mixpanel, add
     * {@code
     * <meta-data android:name="com.mixpanel.android.MPConfig.EnableDebugLogging" />
     * }
     *
     * To the {@code <application>} tag of your AndroidManifest.xml file.
     *
     * @deprecated in 4.1.0, use Manifest meta-data instead
     */
    @Deprecated
    public void logPosts() {
        Log.i(
                LOGTAG,
                "MixpanelAPI.logPosts() is deprecated.\n" +
                        "    To get verbose debug level logging, add\n" +
                        "    <meta-data android:name=\"com.mixpanel.android.MPConfig.EnableDebugLogging\" value=\"true\" />\n" +
                        "    to the <application> section of your AndroidManifest.xml."
        );
    }

    /**
     * Attempt to register MixpanelActivityLifecycleCallbacks to the application's event lifecycle.
     * Once registered, we can automatically check for and show surveys and in-app notifications
     * when any Activity is opened.
     *
     * This is only available if the android version is >= 16. You can disable livecycle callbacks by setting
     * com.mixpanel.android.MPConfig.AutoShowMixpanelUpdates to false in your AndroidManifest.xml
     *
     * This function is automatically called when the library is initialized unless you explicitly
     * set com.mixpanel.android.MPConfig.AutoShowMixpanelUpdates to false in your AndroidManifest.xml
     */
    @TargetApi(MPConfig.UI_FEATURES_MIN_API)
    /* package */ void registerMixpanelActivityLifecycleCallbacks() {
        if (android.os.Build.VERSION.SDK_INT >= MPConfig.UI_FEATURES_MIN_API &&
                mConfig.getAutoShowMixpanelUpdates()) {
            if (mContext.getApplicationContext() instanceof Application) {
                final Application app = (Application) mContext.getApplicationContext();
                app.registerActivityLifecycleCallbacks((new MixpanelActivityLifecycleCallbacks(this)));
            } else {
                Log.i(LOGTAG, "Context is not an Application, Mixpanel will not automatically show surveys, in-app notifications, or A/B test experiments.");
            }
        }
    }

    // Package-level access. Used (at least) by GCMReceiver
    // when OS-level events occur.
    /* package */ interface InstanceProcessor {
        public void process(MixpanelAPI m);
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

    /* package */ DecideMessages constructDecideUpdates(final String token, final DecideMessages.OnNewResultsListener listener, UpdatesFromMixpanel updatesFromMixpanel) {
        return new DecideMessages(token, listener, updatesFromMixpanel);
    }

    /* package */ UpdatesListener constructUpdatesListener() {
        if (Build.VERSION.SDK_INT < MPConfig.UI_FEATURES_MIN_API) {
            Log.i(LOGTAG, "Surveys and Notifications are not supported on this Android OS Version");
            return new UnsupportedUpdatesListener();
        } else {
            return new SupportedUpdatesListener();
        }
    }

    /* package */ UpdatesFromMixpanel constructUpdatesFromMixpanel(final Context context, final String token) {
        if (Build.VERSION.SDK_INT < MPConfig.UI_FEATURES_MIN_API) {
            Log.i(LOGTAG, "SDK version is lower than " + MPConfig.UI_FEATURES_MIN_API + ". Web Configuration, A/B Testing, and Dynamic Tweaks are disabled.");
            return new NoOpUpdatesFromMixpanel(sSharedTweaks);
        } else if (mConfig.getDisableViewCrawler()) {
            Log.i(LOGTAG, "DisableViewCrawler is set to true. Web Configuration, A/B Testing, and Dynamic Tweaks are disabled.");
            return new NoOpUpdatesFromMixpanel(sSharedTweaks);
        } else {
            return new ViewCrawler(mContext, mToken, this, sSharedTweaks);
        }
    }

    /* package */ TrackingDebug constructTrackingDebug() {
        if (mUpdatesFromMixpanel instanceof ViewCrawler) {
            return (TrackingDebug) mUpdatesFromMixpanel;
        }

        return null;
    }

    /* package */ boolean sendAppOpen() {
        return !mConfig.getDisableAppOpenEvent();
    }

    ///////////////////////

    private class PeopleImpl implements People {
        @Override
        public void identify(String distinctId) {
            synchronized (mPersistentIdentity) {
                mPersistentIdentity.setPeopleDistinctId(distinctId);
                mDecideMessages.setDistinctId(distinctId);
            }
            pushWaitingPeopleRecord();
         }

        @Override
        public void setMap(Map<String, Object> properties) {
            if (null == properties) {
                Log.e(LOGTAG, "setMap does not accept null properties");
                return;
            }

            try {
                set(new JSONObject(properties));
            } catch (NullPointerException e) {
                Log.w(LOGTAG, "Can't have null keys in the properties of setMap!");
            }
        }

        @Override
        public void set(JSONObject properties) {
            try {
                final JSONObject sendProperties = new JSONObject(mDeviceInfo);
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
        public void setOnceMap(Map<String, Object> properties) {
            if (null == properties) {
                Log.e(LOGTAG, "setOnceMap does not accept null properties");
                return;
            }
            try {
                setOnce(new JSONObject(properties));
            } catch (NullPointerException e) {
                Log.w(LOGTAG, "Can't have null keys in the properties setOnceMap!");
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
        // Must be thread safe
        public void merge(String property, JSONObject updates) {
            final JSONObject mergeMessage = new JSONObject();
            try {
                mergeMessage.put(property, updates);
                final JSONObject message = stdPeopleMessage("$merge", mergeMessage);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Exception merging a property", e);
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
            Log.i(
                    LOGTAG,
                    "MixpanelAPI.checkForSurvey is deprecated. Calling is now a no-op.\n" +
                     "    to query surveys, call MixpanelAPI.getPeople().getSurveyIfAvailable()"
            );
        }

        @Override
        @Deprecated
        public void checkForSurvey(final SurveyCallbacks callbacks,
                final Activity parentActivity) {
            Log.i(
                    LOGTAG,
                    "MixpanelAPI.checkForSurvey is deprecated. Calling is now a no-op.\n" +
                            "    to query surveys, call MixpanelAPI.getPeople().getSurveyIfAvailable()"
            );
        }

        @Override
        public InAppNotification getNotificationIfAvailable() {
            return mDecideMessages.getNotification(mConfig.getTestMode());
        }

        @Override
        public void trackNotificationSeen(InAppNotification notif) {
            if(notif == null) return;

            trackNotification("$campaign_delivery", notif);
            final MixpanelAPI.People people = getPeople().withIdentity(getDistinctId());
            final DateFormat dateFormat = new SimpleDateFormat(ENGAGE_DATE_FORMAT_STRING, Locale.US);
            final JSONObject notifProperties = notif.getCampaignProperties();
            try {
                notifProperties.put("$time", dateFormat.format(new Date()));
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Exception trying to track an in-app notification seen", e);
            }
            people.append("$campaigns", notif.getId());
            people.append("$notifications", notifProperties);
        }

        @Override
        public Survey getSurveyIfAvailable() {
            return mDecideMessages.getSurvey(mConfig.getTestMode());
        }

        @Override
        @Deprecated
        public void showSurvey(final Survey survey, final Activity parent) {
            showGivenOrAvailableSurvey(survey, parent);
        }

        @Override
        public void showSurveyIfAvailable(final Activity parent) {
            if (Build.VERSION.SDK_INT < MPConfig.UI_FEATURES_MIN_API) {
                return;
            }

            showGivenOrAvailableSurvey(null, parent);
        }

        @Override
        public void showSurveyById(int id, final Activity parent) {
            final Survey s = mDecideMessages.getSurvey(id, mConfig.getTestMode());
            if (s != null) {
                showGivenOrAvailableSurvey(s, parent);
            }
        }

        @Override
        public void showNotificationIfAvailable(final Activity parent) {
            if (Build.VERSION.SDK_INT < MPConfig.UI_FEATURES_MIN_API) {
                return;
            }

            showGivenOrAvailableNotification(null, parent);
        }

        @Override
        public void showNotificationById(int id, final Activity parent) {
            final InAppNotification notif = mDecideMessages.getNotification(id, mConfig.getTestMode());
            showGivenNotification(notif, parent);
        }

        @Override
        public void showGivenNotification(final InAppNotification notif, final Activity parent) {
            if (notif != null) {
                showGivenOrAvailableNotification(notif, parent);
            }
        }

        @Override
        public void trackNotification(final String eventName, final InAppNotification notif) {
            track(eventName, notif.getCampaignProperties());
        }

        @Override
        public void joinExperimentIfAvailable() {
            final JSONArray variants = mDecideMessages.getVariants();
            if (null != variants) {
                mUpdatesFromMixpanel.setVariants(variants);
            }
        }

        @Override
        public void trackCharge(double amount, JSONObject properties) {
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
            // Must be thread safe, will be called from a lot of different threads.
            synchronized (mPersistentIdentity) {
                if (mPersistentIdentity.getPeopleDistinctId() == null) {
                    return;
                }

                mPersistentIdentity.storePushId(registrationId);
                final JSONArray ids = new JSONArray();
                ids.put(registrationId);
                union("$android_devices", ids);
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
                Log.i(LOGTAG, "Can't register for push notification services. Push notifications will not work.");
                Log.i(LOGTAG, "See log tagged " + ConfigurationChecker.LOGTAG + " above for details.");
            }
            else { // Configuration is good for at least some push notifications
                final String pushId = mPersistentIdentity.getPushId();
                if (pushId == null) {
                    if (Build.VERSION.SDK_INT >= 21) {
                        registerForPushIdAPI21AndUp(senderID);
                    } else {
                        registerForPushIdAPI19AndOlder(senderID);
                    }
                } else {
                    MixpanelAPI.allInstances(new InstanceProcessor() {
                        @Override
                        public void process(MixpanelAPI api) {
                            if (MPConfig.DEBUG) {
                                Log.v(LOGTAG, "Using existing pushId " + pushId);
                            }
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

        private JSONObject stdPeopleMessage(String actionType, Object properties)
                throws JSONException {
            final JSONObject dataObj = new JSONObject();
            final String distinctId = getDistinctId(); // TODO ensure getDistinctId is thread safe

            dataObj.put(actionType, properties);
            dataObj.put("$token", mToken);
            dataObj.put("$time", System.currentTimeMillis());

            if (null != distinctId) {
                dataObj.put("$distinct_id", distinctId);
            }

            return dataObj;
        }

        @TargetApi(21)
        private void registerForPushIdAPI21AndUp(String senderID) {
            mMessages.registerForGCM(senderID);
        }

        @TargetApi(19)
        private void registerForPushIdAPI19AndOlder(String senderID) {
            try {
                if (MPConfig.DEBUG) Log.v(LOGTAG, "Registering a new push id");
                final Intent registrationIntent = new Intent("com.google.android.c2dm.intent.REGISTER");
                registrationIntent.putExtra("app", PendingIntent.getBroadcast(mContext, 0, new Intent(), 0));
                registrationIntent.putExtra("sender", senderID);
                mContext.startService(registrationIntent);
            } catch (final SecurityException e) {
                Log.w(LOGTAG, e);
            }
        }

        private void showGivenOrAvailableSurvey(final Survey surveyOrNull, final Activity parent) {
            // Showing surveys is not supported before Jelly Bean
            if (Build.VERSION.SDK_INT < MPConfig.UI_FEATURES_MIN_API) {
                if (MPConfig.DEBUG) {
                    Log.v(LOGTAG, "Will not show survey, os version is too low.");
                }
                return;
            }

            if (! ConfigurationChecker.checkSurveyActivityAvailable(parent.getApplicationContext())) {
                if (MPConfig.DEBUG) {
                    Log.v(LOGTAG, "Will not show survey, application isn't configured appropriately.");
                }
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
                if (intentId <= 0) {
                    Log.e(LOGTAG, "DisplayState Lock is in an inconsistent state! Please report this issue to Mixpanel");
                    return;
                }

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

            BackgroundCapture.captureBackground(parent, listener);
        }

        private void showGivenOrAvailableNotification(final InAppNotification notifOrNull, final Activity parent) {
            if (Build.VERSION.SDK_INT < MPConfig.UI_FEATURES_MIN_API) {
                if (MPConfig.DEBUG) {
                    Log.v(LOGTAG, "Will not show notifications, os version is too low.");
                }
                return;
            }

            parent.runOnUiThread(new Runnable() {
                @Override
                @TargetApi(MPConfig.UI_FEATURES_MIN_API)
                public void run() {
                    final ReentrantLock lock = UpdateDisplayState.getLockObject();
                    lock.lock();
                    try {
                        if (UpdateDisplayState.hasCurrentProposal()) {
                            if (MPConfig.DEBUG) {
                                Log.v(LOGTAG, "DisplayState is locked, will not show notifications.");
                            }
                            return; // Already being used.
                        }

                        InAppNotification toShow = notifOrNull;
                        if (null == toShow) {
                            toShow = getNotificationIfAvailable();
                        }
                        if (null == toShow) {
                            if (MPConfig.DEBUG) {
                                Log.v(LOGTAG, "No notification available, will not show.");
                            }
                            return; // Nothing to show
                        }

                        final InAppNotification.Type inAppType = toShow.getType();
                        if (inAppType == InAppNotification.Type.TAKEOVER && ! ConfigurationChecker.checkSurveyActivityAvailable(parent.getApplicationContext())) {
                            if (MPConfig.DEBUG) {
                                Log.v(LOGTAG, "Application is not configured to show takeover notifications, none will be shown.");
                            }
                            return; // Can't show due to config.
                        }

                        final int highlightColor = ActivityImageUtils.getHighlightColorFromBackground(parent);
                        final UpdateDisplayState.DisplayState.InAppNotificationState proposal =
                                new UpdateDisplayState.DisplayState.InAppNotificationState(toShow, highlightColor);
                        final int intentId = UpdateDisplayState.proposeDisplay(proposal, getDistinctId(), mToken);
                        if (intentId <= 0) {
                            Log.e(LOGTAG, "DisplayState Lock in inconsistent state! Please report this issue to Mixpanel");
                            return;
                        }

                        switch (inAppType) {
                            case MINI: {
                                final UpdateDisplayState claimed = UpdateDisplayState.claimDisplayState(intentId);
                                if (null == claimed) {
                                    if (MPConfig.DEBUG) {
                                        Log.v(LOGTAG, "Notification's display proposal was already consumed, no notification will be shown.");
                                    }
                                    return; // Can't claim the display state
                                }
                                final InAppFragment inapp = new InAppFragment();
                                inapp.setDisplayState(
                                    MixpanelAPI.this,
                                    intentId,
                                    (UpdateDisplayState.DisplayState.InAppNotificationState) claimed.getDisplayState()
                                );
                                inapp.setRetainInstance(true);

                                if (MPConfig.DEBUG) {
                                    Log.v(LOGTAG, "Attempting to show mini notification.");
                                }
                                final FragmentTransaction transaction = parent.getFragmentManager().beginTransaction();
                                transaction.setCustomAnimations(0, R.anim.com_mixpanel_android_slide_down);
                                transaction.add(android.R.id.content, inapp);

                                try {
                                    transaction.commit();
                                } catch (IllegalStateException e) {
                                    // if the app is in the background or the current activity gets killed, rendering the
                                    // notifiction will lead to a crash
                                    if (MPConfig.DEBUG) {
                                        Log.v(LOGTAG, "Unable to show notification.");
                                    }
                                    mDecideMessages.markNotificationAsUnseen(toShow);
                                }
                            }
                            break;
                            case TAKEOVER: {
                                if (MPConfig.DEBUG) {
                                    Log.v(LOGTAG, "Sending intent for takeover notification.");
                                }

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

            });
        }
    }// PeopleImpl

    private interface UpdatesListener extends DecideMessages.OnNewResultsListener {
        public void addOnMixpanelUpdatesReceivedListener(OnMixpanelUpdatesReceivedListener listener);
        public void removeOnMixpanelUpdatesReceivedListener(OnMixpanelUpdatesReceivedListener listener);
    }

    private class UnsupportedUpdatesListener implements UpdatesListener {
        @Override
        public void onNewResults() {
            // Do nothing, these features aren't supported in older versions of the library
        }

        @Override
        public void addOnMixpanelUpdatesReceivedListener(OnMixpanelUpdatesReceivedListener listener) {
            // Do nothing, not supported
        }

        @Override
        public void removeOnMixpanelUpdatesReceivedListener(OnMixpanelUpdatesReceivedListener listener) {
            // Do nothing, not supported
        }
    }

    private class SupportedUpdatesListener implements UpdatesListener, Runnable {
        @Override
        public void onNewResults() {
            mExecutor.execute(this);
        }

        @Override
        public synchronized void addOnMixpanelUpdatesReceivedListener(OnMixpanelUpdatesReceivedListener listener) {
            if (mDecideMessages.hasUpdatesAvailable()) {
                onNewResults();
            }

            mListeners.add(listener);
        }

        @Override
        public synchronized void removeOnMixpanelUpdatesReceivedListener(OnMixpanelUpdatesReceivedListener listener) {
            mListeners.remove(listener);
        }

        @Override
        public synchronized void run() {
            // It's possible that by the time this has run the updates we detected are no longer
            // present, which is ok.
            for (final OnMixpanelUpdatesReceivedListener listener : mListeners) {
                listener.onMixpanelUpdatesReceived();
            }
        }

        private final Set<OnMixpanelUpdatesReceivedListener> mListeners = new HashSet<OnMixpanelUpdatesReceivedListener>();
        private final Executor mExecutor = Executors.newSingleThreadExecutor();
    }

    /* package */ class NoOpUpdatesFromMixpanel implements UpdatesFromMixpanel {
        public NoOpUpdatesFromMixpanel(Tweaks tweaks) {
            mTweaks = tweaks;
        }

        @Override
        public void startUpdates() {
            // No op
        }

        @Override
        public void setEventBindings(JSONArray bindings) {
            // No op
        }

        @Override
        public void setVariants(JSONArray variants) {
            // No op
        }

        @Override
        public Tweaks getTweaks() {
            return mTweaks;
        }

        private final Tweaks mTweaks;
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

    private static void registerAppLinksListeners(Context context, final MixpanelAPI mixpanel) {
        // Register a BroadcastReceiver to receive com.parse.bolts.measurement_event and track a call to mixpanel
        try {
            final Class<?> clazz = Class.forName("android.support.v4.content.LocalBroadcastManager");
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
                                Log.e(APP_LINKS_LOGTAG, "failed to add key \"" + key + "\" to properties for tracking bolts event", e);
                            }
                        }
                    }
                    mixpanel.track("$" + intent.getStringExtra("event_name"), properties);
                }
            }, new IntentFilter("com.parse.bolts.measurement_event"));
        } catch (final InvocationTargetException e) {
            Log.d(APP_LINKS_LOGTAG, "Failed to invoke LocalBroadcastManager.registerReceiver() -- App Links tracking will not be enabled due to this exception", e);
        } catch (final ClassNotFoundException e) {
            Log.d(APP_LINKS_LOGTAG, "To enable App Links tracking android.support.v4 must be installed: " + e.getMessage());
        } catch (final NoSuchMethodException e) {
            Log.d(APP_LINKS_LOGTAG, "To enable App Links tracking android.support.v4 must be installed: " + e.getMessage());
        } catch (final IllegalAccessException e) {
            Log.d(APP_LINKS_LOGTAG, "App Links tracking will not be enabled due to this exception: " + e.getMessage());
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
                Log.d(APP_LINKS_LOGTAG, "Failed to invoke bolts.AppLinks.getTargetUrlFromInboundIntent() -- Unable to detect inbound App Links", e);
            } catch (final ClassNotFoundException e) {
                Log.d(APP_LINKS_LOGTAG, "Please install the Bolts library >= 1.1.2 to track App Links: " + e.getMessage());
            } catch (final NoSuchMethodException e) {
                Log.d(APP_LINKS_LOGTAG, "Please install the Bolts library >= 1.1.2 to track App Links: " + e.getMessage());
            } catch (final IllegalAccessException e) {
                Log.d(APP_LINKS_LOGTAG, "Unable to detect inbound App Links: " + e.getMessage());
            }
        } else {
            Log.d(APP_LINKS_LOGTAG, "Context is not an instance of Activity. To detect inbound App Links, pass an instance of an Activity to getInstance.");
        }
    }

    private final Context mContext;
    private final AnalyticsMessages mMessages;
    private final MPConfig mConfig;
    private final String mToken;
    private final PeopleImpl mPeople;
    private final UpdatesFromMixpanel mUpdatesFromMixpanel;
    private final PersistentIdentity mPersistentIdentity;
    private final UpdatesListener mUpdatesListener;
    private final TrackingDebug mTrackingDebug;
    private final DecideMessages mDecideMessages;
    private final Map<String, String> mDeviceInfo;
    private final Map<String, Long> mEventTimings;

    // Maps each token to a singleton MixpanelAPI instance
    private static final Map<String, Map<Context, MixpanelAPI>> sInstanceMap = new HashMap<String, Map<Context, MixpanelAPI>>();
    private static final SharedPreferencesLoader sPrefsLoader = new SharedPreferencesLoader();
    private static final Tweaks sSharedTweaks = new Tweaks();
    private static Future<SharedPreferences> sReferrerPrefs;

    private static final String LOGTAG = "MixpanelAPI.API";
    private static final String APP_LINKS_LOGTAG = "MixpanelAPI.AL";
    private static final String ENGAGE_DATE_FORMAT_STRING = "yyyy-MM-dd'T'HH:mm:ss";
}
