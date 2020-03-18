package com.mixpanel.android.mpmetrics;

import android.content.Context;

import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;
import com.mixpanel.android.util.MPLog;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This internal class handles Google Play referrer details.
 *
 * <p>You can use Mixpanel to capture and store referrer information,
 * and use that information to track how users from different sources are using your app.
 * To enable this feature, include com.andorid.installreferrer dependency
 * in your build.gradle file.</p>
 *
 * <pre>
 * {@code
 * dependencies {
 *     implementation 'com.android.installreferrer:installreferrer:1.1'
 *     ...
 * }
 * </pre>
 * {@code
 *
 * <p>Once you've added the dependency to your gradle file,
 * all calls to {@link com.mixpanel.android.mpmetrics.MixpanelAPI#track(String, org.json.JSONObject)}
 * will include the user's Google Play Referrer as metadata. In addition, if
 * you include utm parameters in your link to Google Play, they will be parsed and
 * provided as individual properties in your track calls.</p>
 *
 * <p>Our referrer tracker looks for any of the following parameters. All are optional.</p>
 * <ul>
 *     <li>utm_source: often represents the source of your traffic (for example, a search engine or an ad)</li>
 *     <li>utm_medium: indicates whether the link was sent via email, on facebook, or pay per click</li>
 *     <li>utm_term: indicates the keyword or search term associated with the link</li>
 *     <li>utm_content: indicates the particular content associated with the link (for example, which email message was sent)</li>
 *     <li>utm_campaign: the name of the marketing campaign associated with the link.</li>
 * </ul>
 *
 * <p>Whether or not the utm parameters are present, the refferer tracker will
 * also create a "referrer" super property with the complete referrer string.</p>
 */
/* package */ class InstallReferrerPlay implements InstallReferrerStateListener {

    private static String TAG = "MixpanelAPI.InstallReferrerPlay";

    private static final int MAX_INSTALL_REFERRER_RETRIES = 5;
    private static final int TIME_MS_BETWEEN_RETRIES = 2500;

    protected static final Pattern UTM_SOURCE_PATTERN = Pattern.compile("(^|&)utm_source=([^&#=]*)([#&]|$)");
    private final Pattern UTM_MEDIUM_PATTERN = Pattern.compile("(^|&)utm_medium=([^&#=]*)([#&]|$)");
    private final Pattern UTM_CAMPAIGN_PATTERN = Pattern.compile("(^|&)utm_campaign=([^&#=]*)([#&]|$)");
    private final Pattern UTM_CONTENT_PATTERN = Pattern.compile("(^|&)utm_content=([^&#=]*)([#&]|$)");
    private final Pattern UTM_TERM_PATTERN = Pattern.compile("(^|&)utm_term=([^&#=]*)([#&]|$)");

    private static boolean sHasStartedConnection = false;

    private Context mContext;
    private ReferrerCallback mCallBack;
    private InstallReferrerClient mReferrerClient;
    private int mRetryCount;
    private Timer mTimer;

    public InstallReferrerPlay(Context appContext, ReferrerCallback callback) {
        this.mContext = appContext;
        this.mCallBack = callback;
        this.mRetryCount = 0;
        this.mTimer= new Timer();
    }

    @Override
    public void onInstallReferrerSetupFinished(int responseCode) {
        boolean shouldRetry = false;
        switch (responseCode) {
            case InstallReferrerClient.InstallReferrerResponse.OK:
                try {
                    ReferrerDetails details = mReferrerClient.getInstallReferrer();
                    String referrer = details.getInstallReferrer();
                    saveReferrerDetails(referrer);
                } catch (Exception e) {
                    MPLog.d(TAG, "There was an error fetching your referrer details.", e);
                    shouldRetry = true;
                }
                break;
            case InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE:
                shouldRetry = true;
                MPLog.d(TAG, "Service is currently unavailable.");
                break;
            case InstallReferrerClient.InstallReferrerResponse.SERVICE_DISCONNECTED:
                shouldRetry = true;
                MPLog.d(TAG, "Service was disconnected unexpectedly.");
                break;
            case InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED:
                MPLog.d(TAG, "API not available on the current Play Store app.");
                break;
            case InstallReferrerClient.InstallReferrerResponse.DEVELOPER_ERROR:
                MPLog.d(TAG, "Unexpected error.");
                break;
            default:
                break;
        }

        if (shouldRetry) {
            retryConnection();
        } else {
            disconnect();
        }
    }

    @Override
    public void onInstallReferrerServiceDisconnected() {
        MPLog.d(TAG, "Install Referrer Service Disconnected.");
        retryConnection();
    }

    public void connect() {
        mReferrerClient = InstallReferrerClient.newBuilder(mContext).build();
        mReferrerClient.startConnection(this);
        sHasStartedConnection = true;
    }

    private void retryConnection() {
        if (mRetryCount > MAX_INSTALL_REFERRER_RETRIES) {
            MPLog.d(TAG, "Already retried " + MAX_INSTALL_REFERRER_RETRIES + " times. Disconnecting...");
            disconnect();
            return;
        }

        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                connect();
            }
        }, TIME_MS_BETWEEN_RETRIES);

        mRetryCount++;
    }

    public void disconnect() {
        if (mReferrerClient != null && mReferrerClient.isReady()) {
            try {
                mReferrerClient.endConnection();
            } catch (Exception e) {
                MPLog.e(TAG, "Error closing referrer connection", e);
            }
        }
    }

    public static boolean hasStartedConnection() {
        return sHasStartedConnection;
    }

    /* package */ void saveReferrerDetails(String referrer) {
        if (referrer == null) return;
        final Map<String, String> newPrefs = new HashMap<String, String>();
        newPrefs.put("referrer", referrer);

        final Matcher sourceMatcher = UTM_SOURCE_PATTERN.matcher(referrer);
        final String source = find(sourceMatcher);
        if (null != source) {
            newPrefs.put("utm_source", source);
        }

        final Matcher mediumMatcher = UTM_MEDIUM_PATTERN.matcher(referrer);
        final String medium = find(mediumMatcher);
        if (null != medium) {
            newPrefs.put("utm_medium", medium);
        }

        final Matcher campaignMatcher = UTM_CAMPAIGN_PATTERN.matcher(referrer);
        final String campaign = find(campaignMatcher);
        if (null != campaign) {
            newPrefs.put("utm_campaign", campaign);
        }

        final Matcher contentMatcher = UTM_CONTENT_PATTERN.matcher(referrer);
        final String content = find(contentMatcher);
        if (null != content) {
            newPrefs.put("utm_content", content);
        }

        final Matcher termMatcher = UTM_TERM_PATTERN.matcher(referrer);
        final String term = find(termMatcher);
        if (null != term) {
            newPrefs.put("utm_term", term);
        }

        PersistentIdentity.writeReferrerPrefs(mContext, MPConfig.REFERRER_PREFS_NAME, newPrefs);

        if (mCallBack != null) {
            mCallBack.onReferrerReadSuccess();
        }
    }

    private String find(Matcher matcher) {
        if (matcher.find()) {
            final String encoded = matcher.group(2);
            if (null != encoded) {
                try {
                    return URLDecoder.decode(encoded, "UTF-8");
                } catch (final UnsupportedEncodingException e) {
                    MPLog.e(TAG, "Could not decode a parameter into UTF-8");
                }
            }
        }
        return null;
    }

    interface ReferrerCallback {
        void onReferrerReadSuccess();
    }
}
