package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.AndroidTestCase;

public class MixpanelInstallReferrerTest extends AndroidTestCase {
    public void setUp() {
        mInstallReferrer = new InstallReferrerPlay(getContext(), null);
        SharedPreferences prefs = getContext().getSharedPreferences(MPConfig.REFERRER_PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    public void testWeirdReferrer() {
        mReferrerStr = "utm_source=source%3Dvalue&utm_medium=medium%26value&utm_term=term%20value&utm_content=content%2Bvalue&utm_campaign=name%3Fvalue";
        mInstallReferrer.saveReferrerDetails(mReferrerStr);
        SharedPreferences stored = getContext().getSharedPreferences(MPConfig.REFERRER_PREFS_NAME, Context.MODE_PRIVATE);
        assertEquals("utm_source=source%3Dvalue&utm_medium=medium%26value&utm_term=term%20value&utm_content=content%2Bvalue&utm_campaign=name%3Fvalue", stored.getString("referrer", "FAIL"));
        assertEquals("source=value", stored.getString("utm_source", "FAIL"));
        assertEquals("medium&value", stored.getString("utm_medium", "FAIL"));
        assertEquals("name?value", stored.getString("utm_campaign", "FAIL"));
        assertEquals("content+value", stored.getString("utm_content", "FAIL"));
        assertEquals("term value", stored.getString("utm_term", "FAIL"));
    }

    public void testNonParameter() {
        mReferrerStr = "utm_campaign=starts but isn't really a param, neither is utm_source=still no go or utm_medium=nope";
        mInstallReferrer.saveReferrerDetails(mReferrerStr);
        SharedPreferences stored = getContext().getSharedPreferences(MPConfig.REFERRER_PREFS_NAME, Context.MODE_PRIVATE);
        assertEquals("utm_campaign=starts but isn't really a param, neither is utm_source=still no go or utm_medium=nope", stored.getString("referrer", "FAIL"));
        assertFalse(stored.contains("utm_source"));
        assertFalse(stored.contains("utm_medium"));
        assertFalse(stored.contains("utm_campaign"));
        assertFalse(stored.contains("utm_content"));
        assertFalse(stored.contains("utm_term"));
    }

    public void testWackyUnicodeParameter() {
        mReferrerStr = "not utm_campaign=Nope&utm_source=%E2%98%83";
        mInstallReferrer.saveReferrerDetails(mReferrerStr);
        SharedPreferences stored = getContext().getSharedPreferences(MPConfig.REFERRER_PREFS_NAME, Context.MODE_PRIVATE);
        assertEquals("not utm_campaign=Nope&utm_source=%E2%98%83", stored.getString("referrer", "FAIL"));
        assertFalse(stored.contains("utm_campaign"));
        assertEquals("\u2603", stored.getString("utm_source", "FAIL"));
    }

    public void testMixedParameters() {
        mReferrerStr = "utm_source=source&SOMETHING STRANGE&utm_campaign=campaign&nope=utm_term&utm_content=content";
        mInstallReferrer.saveReferrerDetails(mReferrerStr);
        SharedPreferences stored = getContext().getSharedPreferences(MPConfig.REFERRER_PREFS_NAME, Context.MODE_PRIVATE);
        assertEquals("utm_source=source&SOMETHING STRANGE&utm_campaign=campaign&nope=utm_term&utm_content=content", stored.getString("referrer", "FAIL"));
        assertEquals("source", stored.getString("utm_source", "FAIL"));
        assertFalse(stored.contains("utm_medium"));
        assertEquals("campaign", stored.getString("utm_campaign", "FAIL"));
        assertEquals("content", stored.getString("utm_content", "FAIL"));
        assertFalse(stored.contains("utm_term"));
    }

    private InstallReferrerPlay mInstallReferrer;
    private String mReferrerStr;

}
