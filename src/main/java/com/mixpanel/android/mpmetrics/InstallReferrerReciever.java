package com.mixpanel.android.mpmetrics;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

public class InstallReferrerReciever extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Bundle extras = intent.getExtras();
        final String referrer = extras.getString("referrer");
        if (null == referrer) {
            return;
        }

        final SharedPreferences referralInfo = context.getSharedPreferences(MPConfig.REFERRER_PREFS_NAME, Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = referralInfo.edit();
        editor.putString("referrer", referrer);

        try {
            final Matcher sourceMatcher = UTM_SOURCE_PATTERN.matcher(referrer);
            final String sourceEncoded = sourceMatcher.group(2);
            if (null != sourceEncoded) {
                final String source = URLDecoder.decode(sourceEncoded, "UTF-8");
                editor.putString("utm_source", source);
            }

            final Matcher mediumMatcher = UTM_MEDIUM_PATTERN.matcher(referrer);
            final String mediumEncoded = mediumMatcher.group(2);
            if (null != mediumEncoded) {
                final String medium = URLDecoder.decode(mediumEncoded, "UTF-8");
                editor.putString("utm_medium", medium);
            }

            final Matcher campaignMatcher = UTM_CAMPAIGN_PATTERN.matcher(referrer);
            final String campaignEncoded = campaignMatcher.group(2);
            if (null != campaignEncoded) {
                final String campaign = URLDecoder.decode(campaignEncoded, "UTF-8");
                editor.putString("utm_campaign", campaign);
            }

            final Matcher contentMatcher = UTM_CONTENT_PATTERN.matcher(referrer);
            final String contentEncoded = contentMatcher.group(2);
            if (null != contentEncoded) {
                final String content = URLDecoder.decode(contentEncoded, "UTF-8");
                editor.putString("utm_content", content);
            }

            final Matcher termMatcher = UTM_TERM_PATTERN.matcher(referrer);
            final String termEncoded = termMatcher.group(2);
            if (null != termEncoded) {
                final String term = URLDecoder.decode(termEncoded, "UTF-8");
                editor.putString("utm_term", term);
            }
        } catch (final UnsupportedEncodingException e) {
            Log.e(LOGTAG, "System does not support UTF8!");
        } finally {
            editor.apply();
        }
    }

    private final Pattern UTM_SOURCE_PATTERN = Pattern.compile("(^|&)utm_source=([^&#]*)");
    private final Pattern UTM_MEDIUM_PATTERN = Pattern.compile("(^|&)utm_medium=([^&#]*)");
    private final Pattern UTM_CAMPAIGN_PATTERN = Pattern.compile("(^|&)utm_campaign=([^&#]*)");
    private final Pattern UTM_CONTENT_PATTERN = Pattern.compile("(^|&)utm_content=([^&#]*)");
    private final Pattern UTM_TERM_PATTERN = Pattern.compile("(^|&)utm_term=([^&#]*)");

    private static final String LOGTAG = "Mixpanel InstallReferrer";
}
