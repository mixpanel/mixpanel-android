package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.Log;

import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;


/**
 * Stores global configuration options for the Mixpanel library. You can enable and disable configuration
 * options using &lt;meta-data&gt; tags inside of the &lt;application&gt; tag in your AndroidManifest.xml.
 * All settings are optional, and default to reasonable recommended values. Most users will not have to
 * set any options.
 *
 * Mixpanel understands the following options:
 *
 * <dl>
 *     <dt>com.mixpanel.android.MPConfig.EnableDebugLogging</dt>
 *     <dd>A boolean value. If true, emit more detailed log messages. Defaults to false</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.BulkUploadLimit</dt>
 *     <dd>An integer count of messages, the maximum number of messages to queue before an upload attempt. This value should be less than 50.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.FlushInterval</dt>
 *     <dd>An integer number of milliseconds, the maximum time to wait before an upload if the bulk upload limit isn't reached.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.DebugFlushInterval</dt>
 *     <dd>An integer number of milliseconds, the maximum time to wait before an upload if the bulk upload limit isn't reached in debug mode.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.DataExpiration</dt>
 *     <dd>An integer number of milliseconds, the maximum age of records to send to Mixpanel. Corresponds to Mixpanel's server-side limit on record age.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.MinimumDatabaseLimit</dt>
 *     <dd>An integer number of bytes. Mixpanel attempts to limit the size of its persistent data
 *          queue based on the storage capacity of the device, but will always allow queing below this limit. Higher values
 *          will take up more storage even when user storage is very full.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.DisableFallback</dt>
 *     <dd>A boolean value. If true, do not send data over HTTP, even if HTTPS is unavailable. Defaults to true - by default, Mixpanel will only attempt to communicate over HTTPS.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.ResourcePackageName</dt>
 *     <dd>A string java package name. Defaults to the package name of the Application. Users should set if the package name of their R class is different from the application package name due to application id settings.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.DisableGestureBindingUI</dt>
 *     <dd>A boolean value. If true, do not allow connecting to the codeless event binding or A/B testing editor using an accelerometer gesture. Defaults to false.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.DisableEmulatorBindingUI</dt>
 *     <dd>A boolean value. If true, do not attempt to connect to the codeless event binding or A/B testing editor when running in the Android emulator. Defaults to false.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.DisableAppOpenEvent</dt>
 *     <dd>A boolean value. If true, do not send an "$app_open" event when the MixpanelAPI object is created for the first time. Defaults to true - the $app_open event will not be sent by default.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.AutoShowMixpanelUpdates</dt>
 *     <dd>A boolean value. If true, automatically show surveys, notifications, and A/B test variants. Defaults to true.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.EventsEndpoint</dt>
 *     <dd>A string URL. If present, the library will attempt to send events to this endpoint rather than to the default Mixpanel endpoint.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.EventsFallbackEndpoint</dt>
 *     <dd>A string URL. If present, AND if DisableFallback is false, events will be sent to this endpoint if the EventsEndpoint cannot be reached.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.PeopleEndpoint</dt>
 *     <dd>A string URL. If present, the library will attempt to send people updates to this endpoint rather than to the default Mixpanel endpoint.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.PeopleFallbackEndpoint</dt>
 *     <dd>A string URL. If present, AND if DisableFallback is false, people updates will be sent to this endpoint if the EventsEndpoint cannot be reached.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.DecideEndpoint</dt>
 *     <dd>A string URL. If present, the library will attempt to get survey, notification, codeless event tracking, and A/B test variant information from this url rather than the default Mixpanel endpoint.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.DecideFallbackEndpoint</dt>
 *     <dd>A string URL. If present, AND if DisableFallback is false, the library will query this url if the DecideEndpoint url cannot be reached.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.EditorUrl</dt>
 *     <dd>A string URL. If present, the library will attempt to connect to this endpoint when in interactive editing mode, rather than to the default Mixpanel editor url.</dd>
 * </dl>
 *
 */
public class MPConfig {

    // Unfortunately, as long as we support building from source in Eclipse,
    // we can't rely on BuildConfig.MIXPANEL_VERSION existing, so this must
    // be hard-coded both in our gradle files and here in code.
    public static final String VERSION = "4.8.0";

    public static boolean DEBUG = false;

    /**
     * Minimum API level for support of rich UI features, like Surveys, In-App notifications, and dynamic event binding.
     * Devices running OS versions below this level will still support tracking and push notification features.
     */
    public static final int UI_FEATURES_MIN_API = 16;

    // Name for persistent storage of app referral SharedPreferences
    /* package */ static final String REFERRER_PREFS_NAME = "com.mixpanel.android.mpmetrics.ReferralInfo";

    // Max size of the number of notifications we will hold in memory. Since they may contain images,
    // we don't want to suck up all of the memory on the device.
    /* package */ static final int MAX_NOTIFICATION_CACHE_COUNT = 2;

    // Instances are safe to store, since they're immutable and always the same.
    public static MPConfig getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (null == sInstance) {
                final Context appContext = context.getApplicationContext();
                sInstance = readConfig(appContext);
            }
        }

        return sInstance;
    }

    /**
     * The MixpanelAPI will use the system default SSL socket settings under ordinary circumstances.
     * That means it will ignore settings you associated with the default SSLSocketFactory in the
     * schema registry or in underlying HTTP libraries. If you'd prefer for Mixpanel to use your
     * own SSL settings, you'll need to call setSSLSocketFactory early in your code, like this
     *
     * {@code
     * <pre>
     *     MPConfig.getInstance(context).setSSLSocketFactory(someCustomizedSocketFactory);
     * </pre>
     * }
     *
     * Your settings will be globally available to all Mixpanel instances, and will be used for
     * all SSL connections in the library. The call is thread safe, but should be done before
     * your first call to MixpanelAPI.getInstance to insure that the library never uses it's
     * default.
     *
     * The given socket factory may be used from multiple threads, which is safe for the system
     * SSLSocketFactory class, but if you pass a subclass you should ensure that it is thread-safe
     * before passing it to Mixpanel.
     *
     * @param factory an SSLSocketFactory that
     */
    public synchronized void setSSLSocketFactory(SSLSocketFactory factory) {
        mSSLSocketFactory = factory;
    }

    /* package */ MPConfig(Bundle metaData, Context context) {

        // By default, we use a clean, FACTORY default SSLSocket. In general this is the right
        // thing to do, and some other third party libraries change the
        SSLSocketFactory foundSSLFactory;
        try {
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            foundSSLFactory = sslContext.getSocketFactory();
        } catch (final GeneralSecurityException e) {
            Log.i("MixpanelAPI.Conf", "System has no SSL support. Built-in events editor will not be available", e);
            foundSSLFactory = null;
        }
        mSSLSocketFactory = foundSSLFactory;

        DEBUG = metaData.getBoolean("com.mixpanel.android.MPConfig.EnableDebugLogging", false);

        if (metaData.containsKey("com.mixpanel.android.MPConfig.AutoCheckForSurveys")) {
            Log.w(LOGTAG, "com.mixpanel.android.MPConfig.AutoCheckForSurveys has been deprecated in favor of " +
                          "com.mixpanel.android.MPConfig.AutoShowMixpanelUpdates. Please update this key as soon as possible.");
        }
        if (metaData.containsKey("com.mixpanel.android.MPConfig.DebugFlushInterval")) {
            Log.w(LOGTAG, "We do not support com.mixpanel.android.MPConfig.DebugFlushInterval anymore. There will only be one flush interval. Please, update your AndroidManifest.xml.");
        }

        mBulkUploadLimit = metaData.getInt("com.mixpanel.android.MPConfig.BulkUploadLimit", 40); // 40 records default
        mFlushInterval = metaData.getInt("com.mixpanel.android.MPConfig.FlushInterval", 60 * 1000); // one minute default
        mDataExpiration = metaData.getInt("com.mixpanel.android.MPConfig.DataExpiration", 1000 * 60 * 60 * 24 * 5); // 5 days default
        mMinimumDatabaseLimit = metaData.getInt("com.mixpanel.android.MPConfig.MinimumDatabaseLimit", 20 * 1024 * 1024); // 20 Mb
        mDisableFallback = metaData.getBoolean("com.mixpanel.android.MPConfig.DisableFallback", true);
        mResourcePackageName = metaData.getString("com.mixpanel.android.MPConfig.ResourcePackageName"); // default is null
        mDisableGestureBindingUI = metaData.getBoolean("com.mixpanel.android.MPConfig.DisableGestureBindingUI", false);
        mDisableEmulatorBindingUI = metaData.getBoolean("com.mixpanel.android.MPConfig.DisableEmulatorBindingUI", false);
        mDisableAppOpenEvent = metaData.getBoolean("com.mixpanel.android.MPConfig.DisableAppOpenEvent", true);
        mDisableViewCrawler = metaData.getBoolean("com.mixpanel.android.MPConfig.DisableViewCrawler", false);
        mDisableDecideChecker = metaData.getBoolean("com.mixpanel.android.MPConfig.DisableDecideChecker", false);

        // Disable if EITHER of these is present and false, otherwise enable
        final boolean surveysAutoCheck = metaData.getBoolean("com.mixpanel.android.MPConfig.AutoCheckForSurveys", true);
        final boolean mixpanelUpdatesAutoShow = metaData.getBoolean("com.mixpanel.android.MPConfig.AutoShowMixpanelUpdates", true);
        mAutoShowMixpanelUpdates = surveysAutoCheck && mixpanelUpdatesAutoShow;

        mTestMode = metaData.getBoolean("com.mixpanel.android.MPConfig.TestMode", false);

        String eventsEndpoint = metaData.getString("com.mixpanel.android.MPConfig.EventsEndpoint");
        if (null == eventsEndpoint) {
            eventsEndpoint = "https://api.mixpanel.com/track?ip=1";
        }
        mEventsEndpoint = eventsEndpoint;

        String eventsFallbackEndpoint = metaData.getString("com.mixpanel.android.MPConfig.EventsFallbackEndpoint");
        if (null == eventsFallbackEndpoint) {
            eventsFallbackEndpoint = "http://api.mixpanel.com/track?ip=1";
        }
        mEventsFallbackEndpoint = eventsFallbackEndpoint;

        String peopleEndpoint = metaData.getString("com.mixpanel.android.MPConfig.PeopleEndpoint");
        if (null == peopleEndpoint) {
            peopleEndpoint = "https://api.mixpanel.com/engage";
        }
        mPeopleEndpoint = peopleEndpoint;

        String peopleFallbackEndpoint = metaData.getString("com.mixpanel.android.MPConfig.PeopleFallbackEndpoint");
        if (null == peopleFallbackEndpoint) {
            peopleFallbackEndpoint = "http://api.mixpanel.com/engage";
        }
        mPeopleFallbackEndpoint = peopleFallbackEndpoint;

        String decideEndpoint = metaData.getString("com.mixpanel.android.MPConfig.DecideEndpoint");
        if (null == decideEndpoint) {
            decideEndpoint = "https://decide.mixpanel.com/decide";
        }
        mDecideEndpoint = decideEndpoint;

        String decideFallbackEndpoint = metaData.getString("com.mixpanel.android.MPConfig.DecideFallbackEndpoint");
        if (null == decideFallbackEndpoint) {
            decideFallbackEndpoint = "http://decide.mixpanel.com/decide";
        }
        mDecideFallbackEndpoint = decideFallbackEndpoint;

        String editorUrl = metaData.getString("com.mixpanel.android.MPConfig.EditorUrl");
        if (null == editorUrl) {
            editorUrl = "wss://switchboard.mixpanel.com/connect/";
        }
        mEditorUrl = editorUrl;

        if (DEBUG) {
            Log.v(LOGTAG,
                "Mixpanel configured with:\n" +
                "    AutoShowMixpanelUpdates " + getAutoShowMixpanelUpdates() + "\n" +
                "    BulkUploadLimit " + getBulkUploadLimit() + "\n" +
                "    FlushInterval " + getFlushInterval() + "\n" +
                "    DataExpiration " + getDataExpiration() + "\n" +
                "    MinimumDatabaseLimit " + getMinimumDatabaseLimit() + "\n" +
                "    DisableFallback " + getDisableFallback() + "\n" +
                "    DisableAppOpenEvent " + getDisableAppOpenEvent() + "\n" +
                "    DisableViewCrawler " + getDisableViewCrawler() + "\n" +
                "    DisableDeviceUIBinding " + getDisableGestureBindingUI() + "\n" +
                "    DisableEmulatorUIBinding " + getDisableEmulatorBindingUI() + "\n" +
                "    EnableDebugLogging " + DEBUG + "\n" +
                "    TestMode " + getTestMode() + "\n" +
                "    EventsEndpoint " + getEventsEndpoint() + "\n" +
                "    PeopleEndpoint " + getPeopleEndpoint() + "\n" +
                "    DecideEndpoint " + getDecideEndpoint() + "\n" +
                "    EventsFallbackEndpoint " + getEventsFallbackEndpoint() + "\n" +
                "    PeopleFallbackEndpoint " + getPeopleFallbackEndpoint() + "\n" +
                "    DecideFallbackEndpoint " + getDecideFallbackEndpoint() + "\n" +
                "    EditorUrl " + getEditorUrl() + "\n" +
                "    DisableDecideChecker " + getDisableDecideChecker() + "\n"
            );
        }
    }

    // Max size of queue before we require a flush. Must be below the limit the service will accept.
    public int getBulkUploadLimit() {
        return mBulkUploadLimit;
    }

    // Target max milliseconds between flushes. This is advisory.
    public int getFlushInterval() {
        return mFlushInterval;
    }

    // Throw away records that are older than this in milliseconds. Should be below the server side age limit for events.
    public int getDataExpiration() {
        return mDataExpiration;
    }

    public int getMinimumDatabaseLimit() { return mMinimumDatabaseLimit; }

    public boolean getDisableFallback() {
        return mDisableFallback;
    }

    public boolean getDisableGestureBindingUI() {
        return mDisableGestureBindingUI;
    }

    public boolean getDisableEmulatorBindingUI() {
        return mDisableEmulatorBindingUI;
    }

    public boolean getDisableAppOpenEvent() {
        return mDisableAppOpenEvent;
    }

    public boolean getDisableViewCrawler() {
        return mDisableViewCrawler;
    }

    public boolean getTestMode() {
        return mTestMode;
    }

    // Preferred URL for tracking events
    public String getEventsEndpoint() {
        return mEventsEndpoint;
    }

    // Preferred URL for tracking people
    public String getPeopleEndpoint() {
        return mPeopleEndpoint;
    }

    // Preferred URL for pulling decide data
    public String getDecideEndpoint() {
        return mDecideEndpoint;
    }

    // Fallback URL for tracking events if post to preferred URL fails
    public String getEventsFallbackEndpoint() {
        return mEventsFallbackEndpoint;
    }

    // Fallback URL for tracking people if post to preferred URL fails
    public String getPeopleFallbackEndpoint() {
        return mPeopleFallbackEndpoint;
    }

    // Fallback URL for pulling decide data if preferred URL fails
    public String getDecideFallbackEndpoint() {
        return mDecideFallbackEndpoint;
    }

    // Check for and show eligible surveys and in app notifications on Activity changes
    public boolean getAutoShowMixpanelUpdates() {
        return mAutoShowMixpanelUpdates;
    }

    // Preferred URL for connecting to the editor websocket
    public String getEditorUrl() {
        return mEditorUrl;
    }

    public boolean getDisableDecideChecker() {
        return mDisableDecideChecker;
    }

    // Pre-configured package name for resources, if they differ from the application package name
    //
    // mContext.getPackageName() actually returns the "application id", which
    // usually (but not always) the same as package of the generated R class.
    //
    //  See: http://tools.android.com/tech-docs/new-build-system/applicationid-vs-packagename
    //
    // As far as I can tell, the original package name is lost in the build
    // process in these cases, and must be specified by the developer using
    // MPConfig meta-data.
    public String getResourcePackageName() {
        return mResourcePackageName;
    }

    // This method is thread safe, and assumes that SSLSocketFactory is also thread safe
    // (At this writing, all HttpsURLConnections in the framework share a single factory,
    // so this is pretty safe even if the docs are ambiguous)
    public synchronized SSLSocketFactory getSSLSocketFactory() {
        return mSSLSocketFactory;
    }

    ///////////////////////////////////////////////

    // Package access for testing only- do not call directly in library code
    /* package */ static MPConfig readConfig(Context appContext) {
        final String packageName = appContext.getPackageName();
        try {
            final ApplicationInfo appInfo = appContext.getPackageManager().getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            Bundle configBundle = appInfo.metaData;
            if (null == configBundle) {
                configBundle = new Bundle();
            }
            return new MPConfig(configBundle, appContext);
        } catch (final NameNotFoundException e) {
            throw new RuntimeException("Can't configure Mixpanel with package name " + packageName, e);
        }
    }

    private final int mBulkUploadLimit;
    private final int mFlushInterval;
    private final int mDataExpiration;
    private final int mMinimumDatabaseLimit;
    private final boolean mDisableFallback;
    private final boolean mTestMode;
    private final boolean mDisableGestureBindingUI;
    private final boolean mDisableEmulatorBindingUI;
    private final boolean mDisableAppOpenEvent;
    private final boolean mDisableViewCrawler;
    private final String mEventsEndpoint;
    private final String mEventsFallbackEndpoint;
    private final String mPeopleEndpoint;
    private final String mPeopleFallbackEndpoint;
    private final String mDecideEndpoint;
    private final String mDecideFallbackEndpoint;
    private final boolean mAutoShowMixpanelUpdates;
    private final String mEditorUrl;
    private final String mResourcePackageName;
    private final boolean mDisableDecideChecker;

    // Mutable, with synchronized accessor and mutator
    private SSLSocketFactory mSSLSocketFactory;

    private static MPConfig sInstance;
    private static final Object sInstanceLock = new Object();
    private static final String LOGTAG = "MixpanelAPI.Conf";
}
