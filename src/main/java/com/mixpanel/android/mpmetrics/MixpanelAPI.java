package com.mixpanel.android.mpmetrics;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

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
public abstract class MixpanelAPI {
    /**
     * String version of the library.
     */
    public static final String VERSION = MPConfig.VERSION;

    protected MixpanelAPI() {
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
                instance = new MixpanelRealAPI(appContext, sReferrerPrefs, token);
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
            "MixpanelAPI",
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
            "MixpanelAPI",
            "MixpanelAPI.enableFallbackServer is deprecated.\n" +
            "    To enable fallback in your application, add\n" +
            "    <meta-data android:name=\"com.mixpanel.android.MPConfig.DisableFallback\" android:value=\"false\" />\n" +
            "    to the <application> section of your AndroidManifest.xml."
        );
        final AnalyticsMessages msgs = AnalyticsMessages.getInstance(context);
        msgs.setDisableFallback(! enableIfTrue);
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
    public abstract void alias(String alias, String original);

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
    public abstract void identify(String distinctId);

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
    public abstract void track(String eventName, JSONObject properties);

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
    public abstract void flush();

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
    public abstract String getDistinctId();

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
    public abstract void registerSuperProperties(JSONObject superProperties);

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
    public abstract void unregisterSuperProperty(String superPropertyName);

    /**
     * Register super properties for events, only if no other super property with the
     * same names has already been registered.
     *
     * <p>Calling registerSuperPropertiesOnce will never overwrite existing properties.
     *
     * @param superProperties A JSONObject containing the super properties to register.
     * @see #registerSuperProperties(JSONObject)
     */
    public abstract void registerSuperPropertiesOnce(JSONObject superProperties);

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
    public abstract void clearSuperProperties();

    /**
     * Returns a Mixpanel.People object that can be used to set and increment
     * People Analytics properties.
     *
     * @return an instance of {@link People} that you can use to update
     *     records in Mixpanel People Analytics and manage Mixpanel Google Cloud Messaging notifications.
     */
    public abstract People getPeople();

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
         * Works just like {@link People#set(String, Object)}, except it will not overwrite existing property values. This is useful for properties like "First login date".
         *
         * @param propertyName The name of the Mixpanel property. This must be a String, for example "Zip Code"
         * @param value The value of the Mixpanel property. For "Zip Code", this value might be the String "90210"
         */
        public void setOnce(String propertyName, Object value);

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
         * <p>It is safe to call this method any time you want to potentially display an in app notification.
         * This method will be a no-op if there is already a survey or in app notification being displayed.
         * Thus, if you have both surveys and in app notification campaigns built in Mixpanel, you may call
         * both this and {@link People#showNotificationIfAvailable(Activity)} right after each other, and
         * only one of them will be displayed.
         *
         * <p>This method is a no-op in environments with
         * Android API before Ice Cream Sandwich/API level 14.
         *
         * @param parent the Activity that this Survey will be displayed on top of. A snapshot will be
         * taken of parent to be used as a blurred background.
         */
        public void showSurveyIfAvailable(Activity parent);

        /**
         * Shows an in app notification to the user if one is available. If the notification
         * is a mini notification, this method will attach and remove a Fragment to parent.
         * The lifecycle of the Fragment will be handled entirely by the Mixpanel library.
         *
         * <p>If the notification is a takeover notification, a SurveyActivity will be launched to
         * display the Takeover notification.
         *
         * <p>It is safe to call this method any time you want to potentially display an in app notification.
         * This method will be a no-op if there is already a survey or in app notification being displayed.
         * Thus, if you have both surveys and in app notification campaigns built in Mixpanel, you may call
         * both this and {@link People#showSurveyIfAvailable(Activity)} right after each other, and
         * only one of them will be displayed.
         *
         * <p>This method is a no-op in environments with
         * Android API before Ice Cream Sandwich/API level 14.
         *
         * @param parent the Activity that the mini notification will be displayed in, or the Activity
         * that will be used to launch SurveyActivity for the takeover notification.
         */
        public void showNotificationIfAvailable(Activity parent);

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
         * no survey is currently available. Callers who want to display in app notifications should call this
         * method periodically. A given InAppNotification will be returned only once from this method, so callers
         * should be ready to consume any non-null return value.
         *
         * <p>This function will return quickly, and will not cause any communication with
         * Mixpanel's servers, so it is safe to call this from the UI thread.
         *
         * @return an InAppNotification object if one is available, null otherwise.
         */
        public InAppNotification getNotificationIfAvailable();

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
         * Shows an in app notification identified by id. The behavior of this is otherwise identical to
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
         */
        public People withIdentity(String distinctId);

        /**
         * Adds a new listener that will receive a callback when new updates from Mixpanel
         * (like surveys or in app notifications) are discovered.
         *
         * <p>The given listener will be called when a new batch of updates is detected. Handlers
         * should be prepared to handle the callback on an arbitrary thread.
         *
         * <p>Once this listener is called, you may call {@link People#getSurveyIfAvailable()}
         * or {@link People#getNotificationIfAvailable()}
         * to retrieve a Survey or InAppNotification object. However, if you have multiple
         * listeners registered, one listener may have consumed the available Survey or
         * InAppNotification, and so the other listeners may obtain null when calling
         * {@link People#getSurveyIfAvailable()} or {@link People#getNotificationIfAvailable()}.
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
         * @deprecated Use showSurveyIfAvailable() instead.
         */
        @Deprecated
        public void showSurvey(Survey s, Activity parent);

        /**
         * @deprecated Use getSurveyIfAvailable() instead.
         */
        @Deprecated
        public void checkForSurvey(SurveyCallbacks callbacks);

        /**
         * @deprecated Use getSurveyIfAvailable() instead.
         */
        @Deprecated
        public void checkForSurvey(SurveyCallbacks callbacks, Activity parent);
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
    @Deprecated
    public void logPosts() {
        Log.i(
                "MixpanelAPI",
                "MixpanelAPI.logPosts() is deprecated.\n" +
                        "    To get verbose debug level logging, add\n" +
                        "    <meta-data android:name=\"com.mixpanel.android.MPConfig.EnableDebugLogging\" />\n" +
                        "    to the <application> section of your AndroidManifest.xml."
        );
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

    // Maps each token to a singleton MixpanelAPI instance
    private static final Map<String, Map<Context, MixpanelAPI>> sInstanceMap = new HashMap<String, Map<Context, MixpanelAPI>>();
    private static final SharedPreferencesLoader sPrefsLoader = new SharedPreferencesLoader();
    private static Future<SharedPreferences> sReferrerPrefs;
}
