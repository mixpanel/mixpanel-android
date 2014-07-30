package com.mixpanel.android.mpmetrics;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * BroadcastReceiver for automatically storing Google Play Store referrer information as Mixpanel Super Properties.
 *
 * <p>You can use InstallReferrerReceiver to capture and store referrer information,
 * and use that information to track how users from different sources are using your app.
 * To enable InstallReferrerReceiver, add a clause like the following
 * to the &lt;application&gt; tag of your AndroidManifest.xml.</p>
 *
 * <pre>
 * {@code
 * <receiver android:name="com.mixpanel.android.mpmetrics.InstallReferrerReceiver"
 *           android:exported="true">
 *     <intent-filter>
 *         <action android:name="com.android.vending.INSTALL_REFERRER" />
 *     </intent-filter>
 * </receiver>
 * }
 * </pre>
 *
 * <p>Once you've added the &lt;receiver&gt; tag to your manifest,
 * all calls to {@link com.mixpanel.android.mpmetrics.MixpanelAPI#track(String, org.json.JSONObject)}
 * will include the user's Google Play Referrer as metadata. In addition, if
 * you include utm parameters in your link to Google Play, they will be parsed and
 * provided as individual properties in your track calls.</p>
 *
 * <p>InstallReferrerReceiver looks for any of the following parameters. All are optional.</p>
 * <ul>
 *     <li>utm_source: often represents the source of your traffic (for example, a search engine or an ad)</li>
 *     <li>utm_medium: indicates whether the link was sent via email, on facebook, or pay per click</li>
 *     <li>utm_term: indicates the keyword or search term associated with the link</li>
 *     <li>utm_content: indicates the particular content associated with the link (for example, which email message was sent)</li>
 *     <li>utm_campaign: the name of the marketing campaign associated with the link.</li>
 * </ul>
 *
 * <p>Whether or not the utm parameters are present, the InstallReferrerReceiver will
 * also create a "referrer" super property with the complete referrer string.</p>
 */
public class InstallReferrerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Bundle extras = intent.getExtras();
        if (null == extras) {
            return;
        }
        final String referrer = extras.getString("referrer");
        if (null == referrer) {
            return;
        }

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

        PersistentIdentity.writeReferrerPrefs(context, MPConfig.REFERRER_PREFS_NAME, newPrefs);
    }

    private String find(Matcher matcher) {
        if (matcher.find()) {
            final String encoded = matcher.group(2);
            if (null != encoded) {
                try {
                    return URLDecoder.decode(encoded, "UTF-8");
                } catch (final UnsupportedEncodingException e) {
                    Log.e(LOGTAG, "Could not decode a parameter into UTF-8");
                }
            }
        }
        return null;
    }

    private final Pattern UTM_SOURCE_PATTERN = Pattern.compile("(^|&)utm_source=([^&#=]*)([#&]|$)");
    private final Pattern UTM_MEDIUM_PATTERN = Pattern.compile("(^|&)utm_medium=([^&#=]*)([#&]|$)");
    private final Pattern UTM_CAMPAIGN_PATTERN = Pattern.compile("(^|&)utm_campaign=([^&#=]*)([#&]|$)");
    private final Pattern UTM_CONTENT_PATTERN = Pattern.compile("(^|&)utm_content=([^&#=]*)([#&]|$)");
    private final Pattern UTM_TERM_PATTERN = Pattern.compile("(^|&)utm_term=([^&#=]*)([#&]|$)");

    private static final String LOGTAG = "Mixpanel InstallReferrer";
}
