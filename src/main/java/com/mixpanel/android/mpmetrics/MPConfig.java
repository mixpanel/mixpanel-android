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
    public static final String VERSION = "4.4.0";

    public static boolean DEBUG = false;

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

         // Disable if EITHER of these is present and false, otherwise enable
        boolean surveysAutoCheck = metaData.getBoolean("com.mixpanel.android.MPConfig.AutoCheckForSurveys", true);
        boolean mixpanelUpdatesAutoShow = metaData.getBoolean("com.mixpanel.android.MPConfig.AutoShowMixpanelUpdates", true);
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

        if (DEBUG) {
            Log.d(LOGTAG,
                "Mixpanel configured with:\n" +
                "    AutoShowMixpanelUpdates " + getAutoShowMixpanelUpdates() + "\n" +
                "    BulkUploadLimit " + getBulkUploadLimit() + "\n" +
                "    FlushInterval " + getFlushInterval() + "\n" +
                "    DataExpiration " + getDataExpiration() + "\n" +
                "    DisableFallback " + getDisableFallback() + "\n" +
                "    EnableDebugLogging " + DEBUG + "\n" +
                "    TestMode " + getTestMode() + "\n" +
                "    EventsEndpoint " + getEventsEndpoint() + "\n" +
                "    PeopleEndpoint " + getPeopleEndpoint() + "\n" +
                "    DecideEndpoint " + getDecideEndpoint() + "\n" +
                "    EventsFallbackEndpoint " + getEventsFallbackEndpoint() + "\n" +
                "    PeopleFallbackEndpoint " + getPeopleFallbackEndpoint() + "\n" +
                "    DecideFallbackEndpoint " + getDecideFallbackEndpoint() + "\n"
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
    private final String mEventsEndpoint;
    private final String mEventsFallbackEndpoint;
    private final String mPeopleEndpoint;
    private final String mPeopleFallbackEndpoint;
    private final String mDecideEndpoint;
    private final String mDecideFallbackEndpoint;
    private final boolean mAutoShowMixpanelUpdates;

    private static MPConfig sInstance;
    private static final Object sInstanceLock = new Object();
    private static final String LOGTAG = "MixpanelAPI.MPConfig";
}
