package com.mixpanel.android.mpmetrics;

import android.os.Build;
import android.os.Bundle;
import android.test.AndroidTestCase;
import com.mixpanel.android.viewcrawler.ViewCrawler;

public class MPConfigTest extends AndroidTestCase {

    public static final String TOKEN = "TOKEN";
    public static final String DISABLE_VIEW_CRAWLER_METADATA_KEY = "com.mixpanel.android.MPConfig.DisableViewCrawler";

    public void testDisableViewCrawlerDefaultsToFalse() throws Exception {
        final Bundle metaData = new Bundle();

        // DON'T set "com.mixpanel.android.MPConfig.DisableViewCrawler" in the bundle

        final MixpanelAPI mixpanelAPI = mixpanelApi(mpConfig(metaData));

        if (Build.VERSION.SDK_INT >= MPConfig.UI_FEATURES_MIN_API) {
            assertTrue("By default, we should use ViewCrawler as our Impl of UpdatesFromMixpanel",
                       mixpanelAPI.constructUpdatesFromMixpanel(getContext(), TOKEN) instanceof ViewCrawler);
        } else {
            assertTrue("When API is older than MPConfig.UI_FEATURES_MIN_API, we should use NoOp",
                       mixpanelAPI.constructUpdatesFromMixpanel(getContext(), TOKEN) instanceof MixpanelAPI.NoOpUpdatesFromMixpanel);
        }
    }

    public void testDisableViewCrawlerTrueGetsNoOpImpl() throws Exception {
        final Bundle metaData = new Bundle();

        metaData.putBoolean(DISABLE_VIEW_CRAWLER_METADATA_KEY, true);

        final MixpanelAPI mixpanelAPI = mixpanelApi(mpConfig(metaData));

        if (Build.VERSION.SDK_INT >= MPConfig.UI_FEATURES_MIN_API) {
            assertTrue("When DisableViewCrawler is true, we should use a NoOp Impl of UpdatesFromMixpanel",
                       mixpanelAPI.constructUpdatesFromMixpanel(getContext(), TOKEN) instanceof MixpanelAPI.NoOpUpdatesFromMixpanel);
        } else {
            assertTrue("When API is older than MPConfig.UI_FEATURES_MIN_API, we should use NoOp",
                       mixpanelAPI.constructUpdatesFromMixpanel(getContext(), TOKEN) instanceof MixpanelAPI.NoOpUpdatesFromMixpanel);
        }
    }

    public void testDisableViewCrawlerFalseGetsViewCrawler() throws Exception {
        final Bundle metaData = new Bundle();

        metaData.putBoolean(DISABLE_VIEW_CRAWLER_METADATA_KEY, false);

        final MixpanelAPI mixpanelAPI = mixpanelApi(mpConfig(metaData));

        if (Build.VERSION.SDK_INT >= MPConfig.UI_FEATURES_MIN_API) {
            assertTrue("When DisableViewCrawler is false, we should use ViewCrawler as our Impl of UpdatesFromMixpanel",
                       mixpanelAPI.constructUpdatesFromMixpanel(getContext(), TOKEN) instanceof ViewCrawler);
        } else {
            assertTrue("When API is older than MPConfig.UI_FEATURES_MIN_API, we should use NoOp",
                       mixpanelAPI.constructUpdatesFromMixpanel(getContext(), TOKEN) instanceof MixpanelAPI.NoOpUpdatesFromMixpanel);
        }
    }

    private MPConfig mpConfig(final Bundle metaData) {
        return new MPConfig(metaData, getContext());
    }

    private MixpanelAPI mixpanelApi(final MPConfig config) {
        return new MixpanelAPI(getContext(), new TestUtils.EmptyPreferences(getContext()), TOKEN, config, false);
    }
}
