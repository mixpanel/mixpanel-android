package com.mixpanel.android.mpmetrics;

import android.os.Bundle;
import android.test.AndroidTestCase;

public class MPConfigTest extends AndroidTestCase {

    public static final String TOKEN = "TOKEN";
    public static final String DISABLE_VIEW_CRAWLER_METADATA_KEY = "com.mixpanel.android.MPConfig.DisableViewCrawler";

    public void testSetServerURL() throws Exception {
        final Bundle metaData = new Bundle();
        MPConfig config = mpConfig(metaData);
        final MixpanelAPI mixpanelAPI = mixpanelApi(config);
        // default Mixpanel endpoint
        assertEquals("https://api.mixpanel.com/track?ip=1", config.getEventsEndpoint());
        assertEquals("https://api.mixpanel.com/engage?ip=1", config.getPeopleEndpoint());
        assertEquals("https://api.mixpanel.com/groups", config.getGroupsEndpoint());
        assertEquals("https://api.mixpanel.com/decide", config.getDecideEndpoint());

        mixpanelAPI.setServerURL("https://api-eu.mixpanel.com");
        assertEquals("https://api-eu.mixpanel.com/track?ip=1", config.getEventsEndpoint());
        assertEquals("https://api-eu.mixpanel.com/engage?ip=1", config.getPeopleEndpoint());
        assertEquals("https://api-eu.mixpanel.com/groups", config.getGroupsEndpoint());
        assertEquals("https://api-eu.mixpanel.com/decide", config.getDecideEndpoint());
    }

    public void testSetUseIpAddressForGeolocation() throws Exception {
        final Bundle metaData = new Bundle();
        metaData.putString("com.mixpanel.android.MPConfig.EventsEndpoint", "https://api.mixpanel.com/track?ip=1");
        metaData.putString("com.mixpanel.android.MPConfig.EventsEndpoint", "https://api.mixpanel.com/track?ip=1");

        MPConfig config = mpConfig(metaData);
        final MixpanelAPI mixpanelAPI = mixpanelApi(config);

        mixpanelAPI.setUseIpAddressForGeolocation(false);
        assertEquals("https://api.mixpanel.com/track?ip=0", config.getEventsEndpoint());
        assertEquals("https://api.mixpanel.com/engage?ip=0", config.getPeopleEndpoint());
        assertEquals("https://api.mixpanel.com/groups", config.getGroupsEndpoint());
        assertEquals("https://api.mixpanel.com/decide", config.getDecideEndpoint());

        mixpanelAPI.setUseIpAddressForGeolocation(true);

        assertEquals("https://api.mixpanel.com/track?ip=1", config.getEventsEndpoint());
        assertEquals("https://api.mixpanel.com/engage?ip=1", config.getPeopleEndpoint());
        assertEquals("https://api.mixpanel.com/groups", config.getGroupsEndpoint());
        assertEquals("https://api.mixpanel.com/decide", config.getDecideEndpoint());
    }

    public void testSetUseIpAddressForGeolocationOverwrite() throws Exception {
        final Bundle metaData = new Bundle();
        metaData.putString("com.mixpanel.android.MPConfig.EventsEndpoint", "https://api.mixpanel.com/track?ip=1");
        metaData.putString("com.mixpanel.android.MPConfig.PeopleEndpoint", "https://api.mixpanel.com/engage?ip=1");

        MPConfig config = mpConfig(metaData);
        final MixpanelAPI mixpanelAPI = mixpanelApi(config);
        assertEquals("https://api.mixpanel.com/track?ip=1", config.getEventsEndpoint());
        assertEquals("https://api.mixpanel.com/engage?ip=1", config.getPeopleEndpoint());

        mixpanelAPI.setUseIpAddressForGeolocation(false);
        assertEquals("https://api.mixpanel.com/track?ip=0", config.getEventsEndpoint());
        assertEquals("https://api.mixpanel.com/engage?ip=0", config.getPeopleEndpoint());

        final Bundle metaData2 = new Bundle();
        metaData2.putString("com.mixpanel.android.MPConfig.EventsEndpoint", "https://api.mixpanel.com/track?ip=0");
        metaData2.putString("com.mixpanel.android.MPConfig.PeopleEndpoint", "https://api.mixpanel.com/engage?ip=0");

        MPConfig config2 = mpConfig(metaData2);
        final MixpanelAPI mixpanelAPI2 = mixpanelApi(config2);
        assertEquals("https://api.mixpanel.com/track?ip=0", config2.getEventsEndpoint());
        assertEquals("https://api.mixpanel.com/engage?ip=0", config2.getPeopleEndpoint());

        mixpanelAPI2.setUseIpAddressForGeolocation(true);
        assertEquals("https://api.mixpanel.com/track?ip=1", config2.getEventsEndpoint());
        assertEquals("https://api.mixpanel.com/engage?ip=1", config2.getPeopleEndpoint());
    }

    public void testSetEnableLogging() throws Exception {
        final Bundle metaData = new Bundle();
        MPConfig config = mpConfig(metaData);
        final MixpanelAPI mixpanelAPI = mixpanelApi(config);
        mixpanelAPI.setEnableLogging(true);
        assertTrue(config.DEBUG);
        mixpanelAPI.setEnableLogging(false);
        assertFalse(config.DEBUG);
    }

    private MPConfig mpConfig(final Bundle metaData) {
        return new MPConfig(metaData, getContext());
    }

    private MixpanelAPI mixpanelApi(final MPConfig config) {
        return new MixpanelAPI(getContext(), new TestUtils.EmptyPreferences(getContext()), TOKEN, config, false, null);
    }
}
