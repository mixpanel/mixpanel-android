package com.mixpanel.android.mpmetrics;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Future;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.mixpanel.android.R;

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
     * You shouldn't instantiate MixpanelAPI objects directly.
     * Use MixpanelAPI.getInstance to get an instance.
     */
    MixpanelAPI(Context context, Future<SharedPreferences> referrerPreferences, String token) {
        mContext = context;
        mToken = token;
        mPeople = new PeopleImpl();
        mMessages = getAnalyticsMessages();
        mSurveyAssets = new SynchronizedReference<SurveyAssets>();
        mDecideUpdates = new DecideUpdates(mContext, token);

        final SharedPreferencesLoader.OnPrefsLoadedListener listener = new SharedPreferencesLoader.OnPrefsLoadedListener() {
            @Override
            public void onPrefsLoaded(SharedPreferences preferences) {
                final JSONArray records = PersistentProperties.waitingPeopleRecordsForSending(preferences);
                if (null != records) {
                    sendAllPeopleRecords(records);
                }
            }
        };

        final Future<SharedPreferences> storedPreferences = sPrefsLoader.loadPreferences(context, "com.mixpanel.android.mpmetrics.MixpanelAPI_" + token, listener);
        mPersistentProperties = new PersistentProperties(referrerPreferences, storedPreferences);
        registerMixpanelActivityLifecycleCallbacks();
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
            if (null == instance) {
                instance = new MixpanelAPI(appContext, sReferrerPrefs, token);
                instances.put(appContext, instance);
            }
            return instance;
        }
    }

    /**
     * Sets the target frequency of messages to Mixpanel servers.
     * If no calls to {@link #flush()} are made, the Mixpanel
     * library attempts to send tracking information in batches at a rate
     * that provides a reasonable compromise between battery life and liveness of data.
     * Callers can override this value, for the whole application, by calling
     * <tt>setFlushInterval</tt>.
     *
     * If milliseconds is negative, Mixpanel will never flush the data automatically,
     * and require callers to call {@link #flush()} to send data. This can have
     * implications for storage and is not appropriate for most situations.
     *
     * @param context the execution context associated with this application, probably
     *      the main application activity.
     * @param milliseconds the target number of milliseconds between automatic flushes.
     *      this value is advisory, actual flushes may be more or less frequent
     * @deprecated in 4.0.0, use com.mixpanel.android.MPConfig.FlushInterval application metadata instead
     */
    @Deprecated
    public static void setFlushInterval(Context context, long milliseconds) {
        Log.i(
            LOGTAG,
            "MixpanelAPI.setFlushInterval is deprecated.\n" +
            "    To set a custom Mixpanel flush interval for your application, add\n" +
            "    <meta-data android:name=\"com.mixpanel.android.MPConfig.FlushInterval\" android:value=\"YOUR_INTERVAL\" />\n" +
            "    to the <application> section of your AndroidManifest.xml."
        );
        final AnalyticsMessages msgs = AnalyticsMessages.getInstance(context);
        msgs.setFlushInterval(milliseconds);
    }

    /**
     * By default, if the MixpanelAPI cannot contact the API server over HTTPS,
     * it will attempt to contact the server via regular HTTP. To disable this
     * behavior, call enableFallbackServer(context, false)
     *
     * @param context the execution context associated with this context.
     * @param enableIfTrue if true, the library will fall back to using http
     *      when https is unavailable.
     * @deprecated in 4.0.0, use com.mixpanel.android.MPConfig.EventsFallbackEndpoint, com.mixpanel.android.MPConfig.PeopleFallbackEndpoint, or com.mixpanel.android.MPConfig.DecideFallbackEndpoint instead
     */
    @Deprecated
    public static void enableFallbackServer(Context context, boolean enableIfTrue) {
        Log.i(
            LOGTAG,
            "MixpanelAPI.enableFallbackServer is deprecated.\n" +
            "    To disable fallback in your application, add\n" +
            "    <meta-data android:name=\"com.mixpanel.android.MPConfig.DisableFallback\" android:value=\"true\" />\n" +
            "    to the <application> section of your AndroidManifest.xml."
        );
        final AnalyticsMessages msgs = AnalyticsMessages.getInstance(context);
        msgs.setDisableFallback(! enableIfTrue);
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
       mPersistentProperties.setEventsDistinctId(distinctId);
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
    // DO NOT DOCUMENT, but track() must be thread safe (It might not always be thread safe)
    public void track(String eventName, JSONObject properties) {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "track " + eventName);
        try {
            final JSONObject messageProps = new JSONObject();

            final Map<String, String> referrerProperties = mPersistentProperties.getReferrerProperties();
            for (final Map.Entry<String, String> entry:referrerProperties.entrySet()) {
                final String key = entry.getKey();
                final String value = entry.getValue();
                messageProps.put(key, value);
            }

            final JSONObject superProperties = mPersistentProperties.getSuperProperties();
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
        if (MPConfig.DEBUG) Log.d(LOGTAG, "flushEvents");

        mMessages.postToServer();
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
        return mPersistentProperties.getEventsDistinctId();
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
        mPersistentProperties.registerSuperProperties(superProperties);
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
        mPersistentProperties.unregisterSuperProperty(superPropertyName);
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
        mPersistentProperties.registerSuperPropertiesOnce(superProperties);
    }

    /**
     * Erase all currently registered superProperties.
     *
     * <p>Future tracking calls to Mixpanel (even those already queued up but not
     * yet sent to Mixpanel servers) will not be associated with the superProperties registered
     * before this call was made.
     *
     * <p>To remove a single superProperty, use {@link #unregisterSuperProperty(String)}
     *
     * @see #registerSuperProperties(JSONObject)
     */
    public void clearSuperProperties() {
        mPersistentProperties.clearSuperProperties();
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
         * @param properties a JSONObject containing the collection of properties you wish to apply
         *      to the identified user. Each key in the JSONObject will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         */
        public void set(JSONObject properties);

        /**
         * Works just like set(), except it will not overwrite existing property values. This is useful for properties like "First login date".
         *
         * @param propertyName The name of the Mixpanel property. This must be a String, for example "Zip Code"
         * @param value The value of the Mixpanel property. For "Zip Code", this value might be the String "90210"
         */
        public void setOnce(String propertyName, Object value);

        /**
         * Like set(), but will not set properties that already exist on a record.
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
         * <p>To use initPushHandling(), you will need to add the following to your application manifest:
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
         * <p>Be sure to replace "YOUR_PACKAGE_NAME" with the name of your package. For
         * more information and a list of necessary permissions, see {@link GCMReceiver}.
         *
         * <p>If you're planning to use end-to-end support for Messaging, we recommend you
         * call this method immediately after calling {@link People#identify(String)}, likely
         * early in your application's life cycle. (for example, in the onCreate method of your
         * main application activity.)
         *
         * <p>Calls to initPushHandling should not be mixed with calls to
         * {@link #setPushRegistrationId(String)} and {@link #clearPushRegistrationId()}
         * in the same application. Application authors should choose one or the other
         * method for handling Mixpanel GCM messages.
         *
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
         * <p>Calls to setPushRegistrationId should not be mixed with calls to {@link #initPushHandling(String)}
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
         * <p>clearPushRegistrationId should only be called after {@link #identify(String)} has been called.
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
         * Checks to see if this user is eligible for any Mixpanel surveys.
         * If the check is successful, it will call its argument's
         * foundSurvey() method with a (possibly null) {@link com.mixpanel.android.mpmetrics.Survey}.
         * The typical use case is similar to
         * <pre>
         * {@code
         * Activity parent = this;
         * mixpanel.getPeople().checkForSurvey(new SurveyCallbacks() {
         *     public void foundSurvey(Survey survey) {
         *         if (survey != null) {
         *             mixpanel.getPeople().showSurvey(survey, parent);
         *         }
         *     }
         * });
         * }
         * </pre>
         *
         * The foundSurvey() may be (and will probably be) called on a different thread
         * than the one that called checkForSurvey(). The library doesn't guarantee
         * a particular thread, and callbacks are responsible for their own thread safety.
         *
         * This method is will always call back with null in environments with
         * Android API before Gingerbread/API level 10
         */
        public void checkForSurvey(SurveyCallbacks callbacks);

        /**
         * Checks to see if this user has any waiting Mixpanel notifications.
         * If the check is successful, it will call its argument's
         * foundNotifications() method with a (possibly null) {@link com.mixpanel.android.mpmetrics.InAppNotification}.
         * The typical use case is similar to
         * <pre>
         * {@code
         * Activity parent = this;
         * mixpanel.getPeople().checkForNotification(new InAppNotificationCallbacks() {
         *     public void foundNotification(InAppNotification notification) {
         *         if (notification != null) {
         *             mixpanel.getPeople().showNotification(notification, parent);
         *         }
         *     }
         * });
         * }
         * </pre>
         * @param callbacks
         */
        public void checkForNotification(InAppNotificationCallbacks callbacks);

        /**
         * Like {@link #checkForSurvey}, but will prepare visuals and do work associated
         * with showing the build in survey activity before calling the user callback.
         */
        public void checkForSurvey(SurveyCallbacks callbacks, Activity parent);

        /**
         * Will launch an activity that shows a survey to a user and sends the responses
         * to Mixpanel. To get a survey to show, use checkForSurvey()
         *
         * The survey activity will use the root of the given view to take a screenshot
         * for its background.
         *
         * This method is a noop in environments with
         * Android API before Gingerbread/API level 10
         */
        public void showSurvey(Survey s, Activity parent);


        /**
         * Will show a popup window above the given Activity that presents the content
         * of the given notification to the user. To get an InAppNotification to show,
         * use checkForNotification.
         */
        public void showNotification(InAppNotification notification, Activity parent);

        /**
         * Return an instance of Mixpanel people with a temporary distinct id.
         * This is used by Mixpanel Surveys but is likely not needed in your code.
         */
        public People withIdentity(String distinctId);

    }

    /**
     * Manage verbose logging about messages sent to Mixpanel.
     *
     * <p>Under ordinary circumstances, the Mixpanel library will only send messages
     * to the log when errors occur. However, after logPosts is called, Mixpanel will
     * send messages describing it's communication with the Mixpanel servers to
     * the system log.
     *
     * <p>Mixpanel will log its verbose messages tag "MixpanelAPI" with priority I("Information")
     */
    @SuppressWarnings("unused")
    public void logPosts() {
        mMessages.logPosts();
    }

    /**
     * Attempt to register MixpanelActivityLifecycleCallbacks to the application's event lifecycle.
     * Once registered, we can automatically check for and show surveys when the application is opened.
     * This is only available if the android version is >= 14. You can disable this by setting
     * com.mixpanel.android.MPConfig.AutoCheckForSurveys to false in your AndroidManifest.xml
     */
    /* package */
    @TargetApi(14)
    void registerMixpanelActivityLifecycleCallbacks() {
        if (android.os.Build.VERSION.SDK_INT >= 14 && MPConfig.readConfig(mContext).getAutoCheckForSurveys()) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "OS version is >= 14");
            if (mContext.getApplicationContext() instanceof Application) {
                if (MPConfig.DEBUG) Log.d(LOGTAG, "Context is instanceof Application, registering MixpanelActivityLifecycleCallbacks");
                final Application app = (Application) mContext.getApplicationContext();
                app.registerActivityLifecycleCallbacks((new MixpanelActivityLifecycleCallbacks(this)));
            } else {
                if (MPConfig.DEBUG) Log.d(LOGTAG, "Context is NOT instanceof Application, auto show surveys will be disabled.");
            }
        } else {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "OS version is < 14, auto show surveys will be disabled.");
        }
    }

    // Package-level access. Used (at least) by GCMReceiver
    // when OS-level events occur.
    /* package */ interface InstanceProcessor {
        public void process(MixpanelAPI m);
    }

    /* package */ static void allInstances(InstanceProcessor processor) {
        synchronized (sInstanceMap) {
            for (final Map<Context, MixpanelAPI> contextInstances:sInstanceMap.values()) {
                for (final MixpanelAPI instance:contextInstances.values()) {
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

    /* package */ void clearPreferences() {
        // Will clear distinct_ids, superProperties,
        // and waiting People Analytics properties. Will have no effect
        // on messages already queued to send with AnalyticsMessages.
        mPersistentProperties.clearPreferences();
    }

    ///////////////////////

    private class PeopleImpl implements People {
        @Override
        public void identify(String distinctId) {
            mPersistentProperties.setPeopleDistinctId(distinctId);
            pushWaitingPeopleRecord();
         }

        @Override
        public void set(JSONObject properties) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "set " + properties.toString());

            try {
                final JSONObject message = stdPeopleMessage("$set", properties);
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
            if (MPConfig.DEBUG) Log.d(LOGTAG, "setOnce " + properties.toString());

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
            if (MPConfig.DEBUG) Log.d(LOGTAG, "increment " + json.toString());
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
        public void checkForSurvey(final SurveyCallbacks callbacks) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "Checking for surveys...");

            if (null == callbacks) {
                Log.i(LOGTAG, "Skipping survey check because callback is null.");
                return;
            }

            final String checkDistinctId = getDistinctId();
            if (null == checkDistinctId) {
                Log.i(LOGTAG, "Skipping survey check because user has not yet been identified.");
                return;
            }

            if (Build.VERSION.SDK_INT < 10) { // TODO is this correct? Should we grab the surveys and just not DISPLAY them at this API level?
                Log.i(LOGTAG, "Surveys not supported on OS older than API 10, reporting null.");
                callbacks.foundSurvey(null);
                return;
            }

            mDecideUpdates.setSurveyCallback(callbacks, checkDistinctId, mMessages);
        }

        @Override
        public void checkForSurvey(final SurveyCallbacks callbacks, final Activity parentActivity) {
            // This is all about waiting to show a "See our survey" dialog until after the survey is
            // ready to show. We don't get to the end any faster, but we don't make users wait.
            mSurveyAssets.set(null);
            checkForSurvey(new SurveyCallbacks() {
                @Override
                public void foundSurvey(final Survey survey) {
                    BackgroundCapture.captureBackground(parentActivity, new BackgroundCapture.OnBackgroundCapturedListener() {
                        @Override
                        public void OnBackgroundCaptured(Bitmap bitmapCaptured, int highlightColorCaptured) {
                            final SurveyAssets assets = new SurveyAssets(
                                    parentActivity.hashCode(),
                                    bitmapCaptured,
                                    highlightColorCaptured
                            );
                            mSurveyAssets.set(assets);
                            callbacks.foundSurvey(survey);
                        }
                    });
                }
            });
        }

        @Override
        public void checkForNotification(final InAppNotificationCallbacks callbacks) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "Checking for notifications...");

            if (null == callbacks) {
                Log.i(LOGTAG, "Skipping notification check because callback is null.");
            }

            final String checkDistinctId = getDistinctId();
            if (null == checkDistinctId) {
                Log.i(LOGTAG, "Skipping notification check because user has not yet been identified.");
                return;
            }

            mDecideUpdates.setInAppCallback(callbacks, checkDistinctId, mMessages);
        }

        @Override
        // MUST BE THREAD SAFE.
        public void showSurvey(final Survey survey, final Activity parent) {
            // Surveys are not supported before Gingerbread
            if (Build.VERSION.SDK_INT < 10) {
                return;
            }

            // We can't show surveys if they haven't been configured.
            if (! ConfigurationChecker.checkSurveyActivityAvailable(parent.getApplicationContext())) {
                return;
            }

            final SurveyAssets assets = mSurveyAssets.getAndClear();
            if (null != assets && assets.activityHashcode == parent.hashCode()) {
                SurveyState.proposeSurvey(
                    survey,
                    parent,
                    getDistinctId(),
                    mToken,
                    assets.surveyBitmap,
                    assets.highlightColor
                );
            } else {
                BackgroundCapture.captureBackground(parent, new BackgroundCapture.OnBackgroundCapturedListener() {
                    @Override
                    public void OnBackgroundCaptured(Bitmap bitmapCaptured, int highlightColorCaptured) {
                        SurveyState.proposeSurvey(
                            survey,
                            parent,
                            getDistinctId(),
                            mToken,
                            bitmapCaptured,
                            highlightColorCaptured
                        );
                    }
                });
            }
        }

        @Override
        // MUST BE THREAD SAFE
        public void showNotification(final InAppNotification notification, final Activity parent) {
            if (null == notification) {
                return;
            }

            final InAppNotificationDisplay display = new InAppNotificationDisplay(notification, parent);
            parent.runOnUiThread(display);
            track("$campaign_delivery", notification.getCampaignProperties());
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
            if (MPConfig.DEBUG) Log.d(LOGTAG, "delete");
            try {
                final JSONObject message = stdPeopleMessage("$delete", JSONObject.NULL);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Exception deleting a user");
            }
        }

        @Override
        public void setPushRegistrationId(String registrationId) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "setting push registration id: " + registrationId);
            if (getDistinctId() == null) {
                return;
            }
            mPersistentProperties.storePushId(registrationId);
            try {
                union("$android_devices", new JSONArray("[" + registrationId + "]"));
            } catch (final JSONException e) {
                Log.e(LOGTAG, "set push registration id error", e);
            }
        }

        @Override
        public void clearPushRegistrationId() {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "removing push registration id");
            mPersistentProperties.clearPushId();
            set("$android_devices", new JSONArray());
        }

        @Override
        public void initPushHandling(String senderID) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "initPushHandling");

            if (! ConfigurationChecker.checkPushConfiguration(mContext) ) {
                Log.i(LOGTAG, "Can't start push notification service. Push notifications will not work.");
                Log.i(LOGTAG, "See log tagged " + ConfigurationChecker.LOGTAG + " above for details.");
            }
            else { // Configuration is good for push notifications
                final String pushId = mPersistentProperties.getPushId();
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
            return mPersistentProperties.getPeopleDistinctId();
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
    }// PeopleImpl

    ////////////////////////////////////////////////////

    private class InAppNotificationDisplay implements Runnable, View.OnClickListener {

        public InAppNotificationDisplay(final InAppNotification notification, final Activity parent) {
            mInAppNotification = notification;
            mParent = parent;
            mPopupWindow = null;
        }

        // Should be run only on the UI thread.
        @Override
        public void run() {
            if (mInAppNotification.getType() == InAppNotification.Type.TAKEOVER) {
                showTakeoverInAppNotification();
            } else {
                showMiniInAppNotification();
            }
        }

        @Override
        public void onClick(View clicked) {
            mPopupWindow.dismiss();
            if (clicked.getId() == R.id.com_mixpanel_android_notification_button) {
                track("$campaign_open", mInAppNotification.getCampaignProperties());
                String uriString = mInAppNotification.getCallToActionUrl();
                if (uriString != null && uriString.length() > 0) {
                    Uri uri = null;
                    try {
                        uri = Uri.parse(uriString);
                    } catch (IllegalArgumentException e) {
                        Log.i(LOGTAG, "Can't parse notification URI, will not take any action", e);
                        return;
                    }

                    assert(uri != null);
                    try {
                        Intent viewIntent = new Intent(Intent.ACTION_VIEW, uri);
                        mParent.startActivity(viewIntent);
                    } catch (ActivityNotFoundException e) {
                        Log.i(LOGTAG, "User doesn't have an activity for notification URI");
                    }
                }
            } // if button was clicked
        }

        private void showMiniInAppNotification() {
            LayoutInflater inflater = (LayoutInflater) mParent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View popupView = inflater.inflate(R.layout.com_mixpanel_android_activity_notification_mini, null, false);
            TextView titleView = (TextView) popupView.findViewById(R.id.com_mixpanel_android_notification_title);
            titleView.setText(mInAppNotification.getTitle());
            
            ImageView notifImageView = (ImageView) popupView.findViewById(R.id.com_mixpanel_android_notification_image);
            notifImageView.setImageBitmap(mInAppNotification.getImage());

            mPopupWindow = new PopupWindow(popupView);
            mPopupWindow.setWidth(WindowManager.LayoutParams.MATCH_PARENT);
            
            // WRAP_CONTENT behaves strangely, adding a ton more space than necessary, so we have to setHeight ourselves
            float heightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 75, mParent.getResources().getDisplayMetrics());
            mPopupWindow.setHeight((int) heightPx);

            final String uri = mInAppNotification.getCallToActionUrl();
            if (uri != null && uri.length() > 0) {
                popupView.setOnClickListener(this);
            }
            
            ScaleAnimation sa = new ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f, heightPx / 2, heightPx / 2);
            sa.setInterpolator(new SineBounceInterpolator());
            sa.setDuration(500);
            sa.setStartOffset(300);
            notifImageView.startAnimation(sa);
            
            mPopupWindow.setAnimationStyle(R.style.SlideInOutAnimation);
            mPopupWindow.showAtLocation(mParent.getWindow().getDecorView().findViewById(android.R.id.content), Gravity.BOTTOM, 0, 0);

            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    mPopupWindow.dismiss();
                }
            }, 6000);
        }

        private void showTakeoverInAppNotification() {
            LayoutInflater inflater = (LayoutInflater) mParent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View popupView = inflater.inflate(R.layout.com_mixpanel_android_activity_notification_full, null, false);
            ImageView notifImage = (ImageView) popupView.findViewById(R.id.com_mixpanel_android_notification_image);
            TextView titleView = (TextView) popupView.findViewById(R.id.com_mixpanel_android_notification_title);
            TextView subtextView = (TextView) popupView.findViewById(R.id.com_mixpanel_android_notification_subtext);
            Button ctaButton = (Button) popupView.findViewById(R.id.com_mixpanel_android_notification_button);
            ImageButton closeButton = (ImageButton) popupView.findViewById(R.id.com_mixpanel_android_button_exit);

            titleView.setText(mInAppNotification.getTitle());
            subtextView.setText(mInAppNotification.getBody());
            notifImage.setImageBitmap(mInAppNotification.getImage());

            mPopupWindow = new PopupWindow(popupView);
            mPopupWindow.setWidth(WindowManager.LayoutParams.MATCH_PARENT);

            // Handle PopupWindow normally hiding under status bar
            Rect r = new Rect();
            mParent.getWindow().getDecorView().getWindowVisibleDisplayFrame(r);
            mPopupWindow.setHeight(mParent.getWindow().getDecorView().getHeight() - r.top);

            // The following two lines are needed to make back button dismissal work.
            mPopupWindow.setBackgroundDrawable(new BitmapDrawable());
            mPopupWindow.setFocusable(true);

            final String callToAction = mInAppNotification.getCallToAction();
            ctaButton.setText(R.string.com_mixpanel_android_done);
            if (callToAction != null && callToAction.length() > 0) {
                ctaButton.setText(callToAction);
            }
            ctaButton.setOnClickListener(this);
            ctaButton.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        v.setBackgroundResource(R.drawable.com_mixpanel_android_cta_button_highlight);
                    } else {
                        v.setBackgroundResource(R.drawable.com_mixpanel_android_cta_button);
                    }
                    return false;
                }
            });
            closeButton.setOnClickListener(this);

            // Begin animations
            ScaleAnimation sa = new ScaleAnimation(
                .95f, 1.0f, .95f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 1.0f);
            sa.setDuration(200);
            notifImage.startAnimation(sa);

            TranslateAnimation ta = new TranslateAnimation(
                 Animation.RELATIVE_TO_SELF, 0.0f,
                 Animation.RELATIVE_TO_SELF, 0.0f,
                 Animation.RELATIVE_TO_SELF, 0.5f,
                 Animation.RELATIVE_TO_SELF, 0.0f
            );
            ta.setInterpolator(new DecelerateInterpolator());
            ta.setDuration(200);
            titleView.startAnimation(ta);
            subtextView.startAnimation(ta);
            ctaButton.startAnimation(ta);

            Animation fadeIn = AnimationUtils.loadAnimation(mParent, R.anim.fade_in);
            fadeIn.setStartOffset(100);
            closeButton.startAnimation(fadeIn);

            mPopupWindow.setAnimationStyle(R.style.FadeInOutAnimation);
            mPopupWindow.showAtLocation(mParent.getWindow().getDecorView().findViewById(android.R.id.content), Gravity.BOTTOM, 0, 0);
        }

        private class SineBounceInterpolator implements Interpolator {
            public SineBounceInterpolator() { }
            public float getInterpolation(float t) {
                return (float) -(Math.pow(Math.E, -8*t) * Math.cos(12*t)) + 1;
            }
        } 

        private PopupWindow mPopupWindow;
        private final Activity mParent;
        private final InAppNotification mInAppNotification;
    }

    private void recordPeopleMessage(JSONObject message) {
        if (message.has("$distinct_id")) {
           mMessages.peopleMessage(message);
        } else {
           mPersistentProperties.storeWaitingPeopleRecord(message);
        }
    }

    private void pushWaitingPeopleRecord() {
        final JSONArray records = mPersistentProperties.waitingPeopleRecordsForSending();
        if (null != records) {
            sendAllPeopleRecords(records);
        }
    }

    // MUST BE THREAD SAFE. Called from crazy places. mPersistentProperties may not exist
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

    private static class SurveyAssets {
        public SurveyAssets(int activityHashcode, Bitmap surveyBitmap, int highlightColor) {
            this.activityHashcode = activityHashcode;
            this.surveyBitmap = surveyBitmap;
            this.highlightColor = highlightColor;
        }
        public final int activityHashcode;
        public final Bitmap surveyBitmap;
        public final int highlightColor;
    }

    private static final String LOGTAG = "MixpanelAPI";
    private static final String ENGAGE_DATE_FORMAT_STRING = "yyyy-MM-dd'T'HH:mm:ss";

    private final Context mContext;
    private final AnalyticsMessages mMessages;
    private final String mToken;
    private final PeopleImpl mPeople;
    private final PersistentProperties mPersistentProperties;

    private final SynchronizedReference<SurveyAssets> mSurveyAssets;
    private final DecideUpdates mDecideUpdates;

    // Maps each token to a singleton MixpanelAPI instance
    private static final Map<String, Map<Context, MixpanelAPI>> sInstanceMap = new HashMap<String, Map<Context, MixpanelAPI>>();
    private static final SharedPreferencesLoader sPrefsLoader = new SharedPreferencesLoader();
    private static Future<SharedPreferences> sReferrerPrefs;
}
