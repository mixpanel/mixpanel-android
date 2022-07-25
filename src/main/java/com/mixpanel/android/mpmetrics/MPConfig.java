package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;

import com.mixpanel.android.BuildConfig;
import com.mixpanel.android.util.MPConstants;
import com.mixpanel.android.util.MPLog;
import com.mixpanel.android.util.OfflineMode;

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
 *     <dt>com.mixpanel.android.MPConfig.FlushBatchSize</dt>
 *     <dd>Maximum number of events/updates to send in a single network request</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.FlushOnBackground</dt>
 *     <dd>A boolean value. If false, the library will not flush the event and people queues when the app goes into the background. Defaults to true.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.DebugFlushInterval</dt>
 *     <dd>An integer number of milliseconds, the maximum time to wait before an upload if the bulk upload limit isn't reached in debug mode.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.DataExpiration</dt>
 *     <dd>An integer number of milliseconds, the maximum age of records to send to Mixpanel. Corresponds to Mixpanel's server-side limit on record age.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.MinimumDatabaseLimit</dt>
 *     <dd>An integer number of bytes. Mixpanel attempts to limit the size of its persistent data
 *          queue based on the storage capacity of the device, but will always allow queuing below this limit. Higher values
 *          will take up more storage even when user storage is very full.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.MaximumDatabaseLimit</dt>
 *     <dd>An integer number of bytes, the maximum size limit to the Mixpanel database. </dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.ResourcePackageName</dt>
 *     <dd>A string java package name. Defaults to the package name of the Application. Users should set if the package name of their R class is different from the application package name due to application id settings.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.DisableAppOpenEvent</dt>
 *     <dd>A boolean value. If true, do not send an "$app_open" event when the MixpanelAPI object is created for the first time. Defaults to true - the $app_open event will not be sent by default.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.DisableExceptionHandler</dt>
 *     <dd>A boolean value. If true, do not automatically capture app crashes. "App Crashed" events won't show up on Mixpanel. Defaults to false.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.EventsEndpoint</dt>
 *     <dd>A string URL. If present, the library will attempt to send events to this endpoint rather than to the default Mixpanel endpoint.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.PeopleEndpoint</dt>
 *     <dd>A string URL. If present, the library will attempt to send people updates to this endpoint rather than to the default Mixpanel endpoint.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.GroupsEndpoint</dt>
 *     <dd>A string URL. If present, the library will attempt to send group updates to this endpoint rather than to the default Mixpanel endpoint.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.DecideEndpoint</dt>
 *     <dd>A string URL. If present, the library will attempt to get the settings of enabling Mixpanel to automatically collect common mobile events from this url rather than the default Mixpanel endpoint.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.DisableDecideChecker</dt>
 *     <dd>A boolean value. If true, the library will not query our decide endpoint for the settings of enabling Mixpanel to automatically collect common mobile events. Defaults to false.</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.MinimumSessionDuration</dt>
 *     <dd>An integer number. The minimum session duration (ms) that is tracked in automatic events. Defaults to 10000 (10 seconds).</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.SessionTimeoutDuration</dt>
 *     <dd>An integer number. The maximum session duration (ms) that is tracked in automatic events. Defaults to Integer.MAX_VALUE (no maximum session duration).</dd>
 *
 *     <dt>com.mixpanel.android.MPConfig.UseIpAddressForGeolocation</dt>
 *     <dd>A boolean value. If true, Mixpanel will automatically determine city, region and country data using the IP address of the client.Defaults to true.</dd>
 * </dl>
 *
 */
public class MPConfig {

    public static final String VERSION = BuildConfig.MIXPANEL_VERSION;

    public static boolean DEBUG = false;

    // Name for persistent storage of app referral SharedPreferences
    /* package */ static final String REFERRER_PREFS_NAME = "com.mixpanel.android.mpmetrics.ReferralInfo";

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

    /**
     * {@link OfflineMode} allows Mixpanel to be in-sync with client offline internal logic.
     * If you want to integrate your own logic with Mixpanel you'll need to call
     * {@link #setOfflineMode(OfflineMode)} early in your code, like this
     *
     * {@code
     * <pre>
     *     MPConfig.getInstance(context).setOfflineMode(OfflineModeImplementation);
     * </pre>
     * }
     *
     * Your settings will be globally available to all Mixpanel instances, and will be used across
     * all the library. The call is thread safe, but should be done before
     * your first call to MixpanelAPI.getInstance to insure that the library never uses it's
     * default.
     *
     * The given {@link OfflineMode} may be used from multiple threads, you should ensure that
     * your implementation is thread-safe before passing it to Mixpanel.
     *
     * @param offlineMode client offline implementation to use on Mixpanel
     */
    public synchronized void setOfflineMode(OfflineMode offlineMode) {
        mOfflineMode = offlineMode;
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
            MPLog.i("MixpanelAPI.Conf", "System has no SSL support. Built-in events editor will not be available", e);
            foundSSLFactory = null;
        }
        mSSLSocketFactory = foundSSLFactory;

        DEBUG = metaData.getBoolean("com.mixpanel.android.MPConfig.EnableDebugLogging", false);
        if (DEBUG) {
            MPLog.setLevel(MPLog.VERBOSE);
        }

        if (metaData.containsKey("com.mixpanel.android.MPConfig.DebugFlushInterval")) {
            MPLog.w(LOGTAG, "We do not support com.mixpanel.android.MPConfig.DebugFlushInterval anymore. There will only be one flush interval. Please, update your AndroidManifest.xml.");
        }

        mBulkUploadLimit = metaData.getInt("com.mixpanel.android.MPConfig.BulkUploadLimit", 40); // 40 records default
        mFlushInterval = metaData.getInt("com.mixpanel.android.MPConfig.FlushInterval", 60 * 1000); // one minute default
        mFlushBatchSize = metaData.getInt("com.mixpanel.android.MPConfig.FlushBatchSize", 50); // flush 50 events at a time by default
        mFlushOnBackground = metaData.getBoolean("com.mixpanel.android.MPConfig.FlushOnBackground", true);
        mMinimumDatabaseLimit = metaData.getInt("com.mixpanel.android.MPConfig.MinimumDatabaseLimit", 20 * 1024 * 1024); // 20 Mb
        mMaximumDatabaseLimit = metaData.getInt("com.mixpanel.android.MPConfig.MaximumDatabaseLimit", Integer.MAX_VALUE); // 2 Gb
        mResourcePackageName = metaData.getString("com.mixpanel.android.MPConfig.ResourcePackageName"); // default is null
        mDisableDecideChecker = metaData.getBoolean("com.mixpanel.android.MPConfig.DisableDecideChecker", false);
        mDisableAppOpenEvent = metaData.getBoolean("com.mixpanel.android.MPConfig.DisableAppOpenEvent", true);
        mDisableExceptionHandler = metaData.getBoolean("com.mixpanel.android.MPConfig.DisableExceptionHandler", false);
        mMinSessionDuration = metaData.getInt("com.mixpanel.android.MPConfig.MinimumSessionDuration", 10 * 1000); // 10 seconds
        mSessionTimeoutDuration = metaData.getInt("com.mixpanel.android.MPConfig.SessionTimeoutDuration", Integer.MAX_VALUE); // no timeout by default
        mUseIpAddressForGeolocation = metaData.getBoolean("com.mixpanel.android.MPConfig.UseIpAddressForGeolocation", true);


        Object dataExpirationMetaData = metaData.get("com.mixpanel.android.MPConfig.DataExpiration");
        long dataExpirationLong = 1000 * 60 * 60 * 24 * 5; // 5 days default
        if (dataExpirationMetaData != null) {
            try {
                if (dataExpirationMetaData instanceof Integer) {
                    dataExpirationLong = (long) (int) dataExpirationMetaData;
                } else if (dataExpirationMetaData instanceof Float) {
                    dataExpirationLong = (long) (float) dataExpirationMetaData;
                } else {
                    throw new NumberFormatException(dataExpirationMetaData.toString() + " is not a number.");
                }
            } catch (Exception e) {
                MPLog.e(LOGTAG,"Error parsing com.mixpanel.android.MPConfig.DataExpiration meta-data value", e);
            }
        }
        mDataExpiration = dataExpirationLong;
        boolean noUseIpAddressForGeolocationSetting = !metaData.containsKey("com.mixpanel.android.MPConfig.UseIpAddressForGeolocation");

        String eventsEndpoint = metaData.getString("com.mixpanel.android.MPConfig.EventsEndpoint");
        if (eventsEndpoint != null) {
            setEventsEndpoint(noUseIpAddressForGeolocationSetting ? eventsEndpoint : getEndPointWithIpTrackingParam(eventsEndpoint, getUseIpAddressForGeolocation()));
        } else {
            setEventsEndpointWithBaseURL(MPConstants.URL.MIXPANEL_API);
        }

        String peopleEndpoint = metaData.getString("com.mixpanel.android.MPConfig.PeopleEndpoint");
        if (peopleEndpoint != null) {
            setPeopleEndpoint(noUseIpAddressForGeolocationSetting ? peopleEndpoint : getEndPointWithIpTrackingParam(peopleEndpoint, getUseIpAddressForGeolocation()));
        } else {
            setPeopleEndpointWithBaseURL(MPConstants.URL.MIXPANEL_API);
        }

        String groupsEndpoint = metaData.getString("com.mixpanel.android.MPConfig.GroupsEndpoint");
        if (groupsEndpoint != null) {
            setGroupsEndpoint(noUseIpAddressForGeolocationSetting ? groupsEndpoint : getEndPointWithIpTrackingParam(groupsEndpoint, getUseIpAddressForGeolocation()));
        } else {
            setGroupsEndpointWithBaseURL(MPConstants.URL.MIXPANEL_API);
        }

        String decideEndpoint = metaData.getString("com.mixpanel.android.MPConfig.DecideEndpoint");
        if (decideEndpoint != null) {
            setDecideEndpoint(decideEndpoint);
        } else {
            setDecideEndpointWithBaseURL(MPConstants.URL.MIXPANEL_API);
        }

        MPLog.v(LOGTAG, toString());
    }

    // Max size of queue before we require a flush. Must be below the limit the service will accept.
    public int getBulkUploadLimit() {
        return mBulkUploadLimit;
    }

    // Target max milliseconds between flushes. This is advisory.
    public int getFlushInterval() {
        return mFlushInterval;
    }

    // Whether the SDK should flush() queues when the app goes into the background or not.
    public boolean getFlushOnBackground() {
        return mFlushOnBackground;
    }

    // Maximum number of events/updates to send in a single network request
    public int getFlushBatchSize() {
        return mFlushBatchSize;
    }


    public void setFlushBatchSize(int flushBatchSize) {
        mFlushBatchSize = flushBatchSize;
    }

    // Throw away records that are older than this in milliseconds. Should be below the server side age limit for events.
    public long getDataExpiration() {
        return mDataExpiration;
    }

    public int getMinimumDatabaseLimit() { return mMinimumDatabaseLimit; }

    public int getMaximumDatabaseLimit() { return mMaximumDatabaseLimit; }

    public void setMaximumDatabaseLimit(int maximumDatabaseLimit) {
        mMaximumDatabaseLimit = maximumDatabaseLimit;
    }

    public boolean getDisableAppOpenEvent() {
        return mDisableAppOpenEvent;
    }

    // Preferred URL for tracking events
    public String getEventsEndpoint() {
        return mEventsEndpoint;
    }

    // In parity with iOS SDK
    public void setServerURL(String serverURL) {
        setEventsEndpointWithBaseURL(serverURL);
        setPeopleEndpointWithBaseURL(serverURL);
        setGroupsEndpointWithBaseURL(serverURL);
        setDecideEndpointWithBaseURL(serverURL);
    }

    private String getEndPointWithIpTrackingParam(String endPoint, boolean ifUseIpAddressForGeolocation) {
        if (endPoint.contains("?ip=")) {
            return endPoint.substring(0, endPoint.indexOf("?ip=")) + "?ip=" + (ifUseIpAddressForGeolocation ? "1" : "0");
        } else {
            return endPoint + "?ip=" + (ifUseIpAddressForGeolocation ? "1" : "0");
        }
    }

    private void setEventsEndpointWithBaseURL(String baseURL) {
        setEventsEndpoint(getEndPointWithIpTrackingParam(baseURL + MPConstants.URL.EVENT, getUseIpAddressForGeolocation()));
    }

    private void setEventsEndpoint(String eventsEndpoint) {
        mEventsEndpoint = eventsEndpoint;
    }

    // Preferred URL for tracking people
    public String getPeopleEndpoint() {
        return mPeopleEndpoint;
    }

    private void setPeopleEndpointWithBaseURL(String baseURL) {
        setPeopleEndpoint(getEndPointWithIpTrackingParam(baseURL + MPConstants.URL.PEOPLE, getUseIpAddressForGeolocation()));
    }

    private void setPeopleEndpoint(String peopleEndpoint) {
        mPeopleEndpoint = peopleEndpoint;
    }

    // Preferred URL for tracking groups
    public String getGroupsEndpoint() {
        return mGroupsEndpoint;
    }

    private void setGroupsEndpointWithBaseURL(String baseURL) {
        setGroupsEndpoint(getEndPointWithIpTrackingParam(baseURL + MPConstants.URL.GROUPS, getUseIpAddressForGeolocation()));
    }

    private void setGroupsEndpoint(String groupsEndpoint) {
        mGroupsEndpoint = groupsEndpoint;
    }

    // Preferred URL for pulling decide data
    public String getDecideEndpoint() {
        return mDecideEndpoint;
    }

    private void setDecideEndpointWithBaseURL(String baseURL) {
        setDecideEndpoint(baseURL + MPConstants.URL.DECIDE);
    }

    private void setDecideEndpoint(String decideEndpoint) {
        mDecideEndpoint = decideEndpoint;
    }

    public int getMinimumSessionDuration() {
        return mMinSessionDuration;
    }

    public int getSessionTimeoutDuration() {
        return mSessionTimeoutDuration;
    }

    public boolean getDisableExceptionHandler() {
        return mDisableExceptionHandler;
    }

    public boolean getDisableDecideChecker() {
        return mDisableDecideChecker;
    }

    private boolean getUseIpAddressForGeolocation() {
        return mUseIpAddressForGeolocation;
    }

    public void setUseIpAddressForGeolocation(boolean useIpAddressForGeolocation) {
        mUseIpAddressForGeolocation = useIpAddressForGeolocation;
        setEventsEndpoint(getEndPointWithIpTrackingParam(getEventsEndpoint(), useIpAddressForGeolocation));
        setPeopleEndpoint(getEndPointWithIpTrackingParam(getPeopleEndpoint(), useIpAddressForGeolocation));
        setGroupsEndpoint(getEndPointWithIpTrackingParam(getGroupsEndpoint(), useIpAddressForGeolocation));
    }

    public void setEnableLogging(boolean enableLogging) {
        DEBUG = enableLogging;
        MPLog.setLevel(DEBUG ? MPLog.VERBOSE : MPLog.NONE);
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

    // This method is thread safe, and assumes that OfflineMode is also thread safe
    public synchronized OfflineMode getOfflineMode() {
        return mOfflineMode;
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

    @Override
    public String toString() {
        return "Mixpanel (" + VERSION + ") configured with:\n" +
                "    BulkUploadLimit " + getBulkUploadLimit() + "\n" +
                "    FlushInterval " + getFlushInterval() + "\n" +
                "    FlushInterval " + getFlushBatchSize() + "\n" +
                "    DataExpiration " + getDataExpiration() + "\n" +
                "    MinimumDatabaseLimit " + getMinimumDatabaseLimit() + "\n" +
                "    MaximumDatabaseLimit " + getMaximumDatabaseLimit() + "\n" +
                "    DisableAppOpenEvent " + getDisableAppOpenEvent() + "\n" +
                "    EnableDebugLogging " + DEBUG + "\n" +
                "    EventsEndpoint " + getEventsEndpoint() + "\n" +
                "    PeopleEndpoint " + getPeopleEndpoint() + "\n" +
                "    DecideEndpoint " + getDecideEndpoint() + "\n" +
                "    DisableDecideChecker " + getDisableDecideChecker() + "\n" +
                "    MinimumSessionDuration: " + getMinimumSessionDuration() + "\n" +
                "    SessionTimeoutDuration: " + getSessionTimeoutDuration() + "\n" +
                "    DisableExceptionHandler: " + getDisableExceptionHandler() + "\n" +
                "    FlushOnBackground: " + getFlushOnBackground();
    }

    private final int mBulkUploadLimit;
    private final int mFlushInterval;
    private final boolean mFlushOnBackground;
    private final long mDataExpiration;
    private final int mMinimumDatabaseLimit;
    private int mMaximumDatabaseLimit;
    private final boolean mDisableDecideChecker;
    private final boolean mDisableAppOpenEvent;
    private final boolean mDisableExceptionHandler;
    private String mEventsEndpoint;
    private String mPeopleEndpoint;
    private String mGroupsEndpoint;
    private String mDecideEndpoint;
    private int mFlushBatchSize;

    private final String mResourcePackageName;
    private final int mMinSessionDuration;
    private final int mSessionTimeoutDuration;
    private boolean mUseIpAddressForGeolocation;

    // Mutable, with synchronized accessor and mutator
    private SSLSocketFactory mSSLSocketFactory;
    private OfflineMode mOfflineMode;

    private static MPConfig sInstance;
    private static final Object sInstanceLock = new Object();
    private static final String LOGTAG = "MixpanelAPI.Conf";
}
