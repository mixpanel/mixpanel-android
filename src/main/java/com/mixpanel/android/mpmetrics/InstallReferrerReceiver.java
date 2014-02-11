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

        PersistentProperties.writeReferrerPrefs(context, MPConfig.REFERRER_PREFS_NAME, newPrefs);
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
