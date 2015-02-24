package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.Log;


/**
 * Stores global configuration options for the Mixpanel library.
 */
public class MPConfig {

    // Unfortunately, as long as we support building from source in Eclipse,
    // we can't rely on BuildConfig.MIXPANEL_VERSION existing, so this must
    // be hard-coded both in our gradle files and here in code.
    public static final String VERSION = "4.5.3";

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

    /* package */ MPConfig(Bundle metaData) {
        DEBUG = metaData.getBoolean("com.mixpanel.android.MPConfig.EnableDebugLogging", false);

        if (metaData.containsKey("com.mixpanel.android.MPConfig.AutoCheckForSurveys")) {
            Log.w(LOGTAG, "com.mixpanel.android.MPConfig.AutoCheckForSurveys has been deprecated in favor of " +
                          "com.mixpanel.android.MPConfig.AutoShowMixpanelUpdates. Please update this key as soon as possible.");
        }

        mBulkUploadLimit = metaData.getInt("com.mixpanel.android.MPConfig.BulkUploadLimit", 40); // 40 records default
        mFlushInterval = metaData.getInt("com.mixpanel.android.MPConfig.FlushInterval", 60 * 1000); // one minute default
        mDataExpiration = metaData.getInt("com.mixpanel.android.MPConfig.DataExpiration",  1000 * 60 * 60 * 24 * 5); // 5 days default
        mDisableFallback = metaData.getBoolean("com.mixpanel.android.MPConfig.DisableFallback", true);
        mResourcePackageName = metaData.getString("com.mixpanel.android.MPConfig.ResourcePackageName"); // default is null
        mDisableGestureBindingUI = metaData.getBoolean("com.mixpanel.android.MPConfig.DisableGestureBindingUI", false);
        mDisableEmulatorBindingUI = metaData.getBoolean("com.mixpanel.android.MPConfig.DisableEmulatorBindingUI", false);
        mDisableAppOpenEvent = metaData.getBoolean("com.mixpanel.android.MPConfig.DisableAppOpenEvent", true);

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
                "    DisableFallback " + getDisableFallback() + "\n" +
                "    DisableAppOpenEvent " + getDisableAppOpenEvent() + "\n" +
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
                "    EditorUrl " + getEditorUrl() + "\n"
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
            return new MPConfig(configBundle);
        } catch (final NameNotFoundException e) {
            throw new RuntimeException("Can't configure Mixpanel with package name " + packageName, e);
        }
    }

    private final int mBulkUploadLimit;
    private final int mFlushInterval;
    private final int mDataExpiration;
    private final boolean mDisableFallback;
    private final boolean mTestMode;
    private final boolean mDisableGestureBindingUI;
    private final boolean mDisableEmulatorBindingUI;
    private final boolean mDisableAppOpenEvent;
    private final String mEventsEndpoint;
    private final String mEventsFallbackEndpoint;
    private final String mPeopleEndpoint;
    private final String mPeopleFallbackEndpoint;
    private final String mDecideEndpoint;
    private final String mDecideFallbackEndpoint;
    private final boolean mAutoShowMixpanelUpdates;
    private final String mEditorUrl;
    private final String mResourcePackageName;

    private static MPConfig sInstance;
    private static final Object sInstanceLock = new Object();
    private static final String LOGTAG = "MixpanelAPI.Configuration";
}
