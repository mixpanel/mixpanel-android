package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class MPConfigTest {

    public static final String TOKEN = "TOKEN";
    public static final String DISABLE_VIEW_CRAWLER_METADATA_KEY = "com.mixpanel.android.MPConfig.DisableViewCrawler";

    @Test
    public void testSetUseIpAddressForGeolocation() {
        final Bundle metaData = new Bundle();
        metaData.putString("com.mixpanel.android.MPConfig.EventsEndpoint", "https://api.mixpanel.com/track/?ip=1");
        metaData.putString("com.mixpanel.android.MPConfig.EventsEndpoint", "https://api.mixpanel.com/track/?ip=1");

        MPConfig config = mpConfig(metaData);
        final MixpanelAPI mixpanelAPI = mixpanelApi(config);

        mixpanelAPI.setUseIpAddressForGeolocation(false);
        assertEquals("https://api.mixpanel.com/track/?ip=0", config.getEventsEndpoint());
        assertEquals("https://api.mixpanel.com/engage/?ip=0", config.getPeopleEndpoint());
        assertEquals("https://api.mixpanel.com/groups/?ip=0", config.getGroupsEndpoint());

        mixpanelAPI.setUseIpAddressForGeolocation(true);
        assertEquals("https://api.mixpanel.com/track/?ip=1", config.getEventsEndpoint());
        assertEquals("https://api.mixpanel.com/engage/?ip=1", config.getPeopleEndpoint());
        assertEquals("https://api.mixpanel.com/groups/?ip=1", config.getGroupsEndpoint());
    }

    @Test
    public void testSetUseIpAddressForGeolocationOverwrite() {
        final Bundle metaData = new Bundle();
        metaData.putString("com.mixpanel.android.MPConfig.EventsEndpoint", "https://api.mixpanel.com/track/?ip=1");
        metaData.putString("com.mixpanel.android.MPConfig.PeopleEndpoint", "https://api.mixpanel.com/engage/?ip=1");

        MPConfig config = mpConfig(metaData);
        final MixpanelAPI mixpanelAPI = mixpanelApi(config);
        assertEquals("https://api.mixpanel.com/track/?ip=1", config.getEventsEndpoint());
        assertEquals("https://api.mixpanel.com/engage/?ip=1", config.getPeopleEndpoint());

        mixpanelAPI.setUseIpAddressForGeolocation(false);
        assertEquals("https://api.mixpanel.com/track/?ip=0", config.getEventsEndpoint());
        assertEquals("https://api.mixpanel.com/engage/?ip=0", config.getPeopleEndpoint());

        final Bundle metaData2 = new Bundle();
        metaData2.putString("com.mixpanel.android.MPConfig.EventsEndpoint", "https://api.mixpanel.com/track/?ip=0");
        metaData2.putString("com.mixpanel.android.MPConfig.PeopleEndpoint", "https://api.mixpanel.com/engage/?ip=0");

        MPConfig config2 = mpConfig(metaData2);
        final MixpanelAPI mixpanelAPI2 = mixpanelApi(config2);
        assertEquals("https://api.mixpanel.com/track/?ip=0", config2.getEventsEndpoint());
        assertEquals("https://api.mixpanel.com/engage/?ip=0", config2.getPeopleEndpoint());

        mixpanelAPI2.setUseIpAddressForGeolocation(true);
        assertEquals("https://api.mixpanel.com/track/?ip=1", config2.getEventsEndpoint());
        assertEquals("https://api.mixpanel.com/engage/?ip=1", config2.getPeopleEndpoint());
    }

    @Test
    public void testEndPointAndGeoSettingBothReadFromConfigTrue() {
        final Bundle metaData = new Bundle();
        metaData.putString("com.mixpanel.android.MPConfig.EventsEndpoint", "https://api.mixpanel.com/track/");
        metaData.putString("com.mixpanel.android.MPConfig.PeopleEndpoint", "https://api.mixpanel.com/engage/");
        metaData.putString("com.mixpanel.android.MPConfig.GroupsEndpoint", "https://api.mixpanel.com/groups/");
        metaData.putBoolean("com.mixpanel.android.MPConfig.UseIpAddressForGeolocation", true);

        MPConfig config = mpConfig(metaData);
        final MixpanelAPI mixpanelAPI = mixpanelApi(config);
        assertEquals("https://api.mixpanel.com/track/?ip=1", config.getEventsEndpoint());
        assertEquals("https://api.mixpanel.com/engage/?ip=1", config.getPeopleEndpoint());
        assertEquals("https://api.mixpanel.com/groups/?ip=1", config.getGroupsEndpoint());
    }

    public void testSetServerURL() throws Exception {
        final Bundle metaData = new Bundle();
        MPConfig config = mpConfig(metaData);
        final MixpanelAPI mixpanelAPI = mixpanelApi(config);
        // default Mixpanel endpoint
        assertEquals("https://api.mixpanel.com/track/?ip=1", config.getEventsEndpoint());
        assertEquals("https://api.mixpanel.com/engage/?ip=1", config.getPeopleEndpoint());
        assertEquals("https://api.mixpanel.com/groups/?ip=1", config.getGroupsEndpoint());

        mixpanelAPI.setServerURL("https://api-eu.mixpanel.com");
        assertEquals("https://api-eu.mixpanel.com/track/?ip=1", config.getEventsEndpoint());
        assertEquals("https://api-eu.mixpanel.com/engage/?ip=1", config.getPeopleEndpoint());
        assertEquals("https://api-eu.mixpanel.com/groups/?ip=1", config.getGroupsEndpoint());
    }

    @Test
    public void testEndPointAndGeoSettingBothReadFromConfigFalse() {
        final Bundle metaData = new Bundle();
        metaData.putString("com.mixpanel.android.MPConfig.EventsEndpoint", "https://api.mixpanel.com/track/");
        metaData.putString("com.mixpanel.android.MPConfig.PeopleEndpoint", "https://api.mixpanel.com/engage/");
        metaData.putString("com.mixpanel.android.MPConfig.GroupsEndpoint", "https://api.mixpanel.com/groups/");
        metaData.putBoolean("com.mixpanel.android.MPConfig.UseIpAddressForGeolocation", false);

        MPConfig config = mpConfig(metaData);
        final MixpanelAPI mixpanelAPI = mixpanelApi(config);
        assertEquals("https://api.mixpanel.com/track/?ip=0", config.getEventsEndpoint());
        assertEquals("https://api.mixpanel.com/engage/?ip=0", config.getPeopleEndpoint());
        assertEquals("https://api.mixpanel.com/groups/?ip=0", config.getGroupsEndpoint());
    }

    @Test
    public void testEndPointAndGeoSettingBothReadFromConfigFalseOverwrite() {
        final Bundle metaData = new Bundle();
        metaData.putString("com.mixpanel.android.MPConfig.EventsEndpoint", "https://api.mixpanel.com/track/?ip=1");
        metaData.putString("com.mixpanel.android.MPConfig.PeopleEndpoint", "https://api.mixpanel.com/engage/?ip=1");
        metaData.putString("com.mixpanel.android.MPConfig.GroupsEndpoint", "https://api.mixpanel.com/groups/?ip=1");
        metaData.putBoolean("com.mixpanel.android.MPConfig.UseIpAddressForGeolocation", false);

        MPConfig config = mpConfig(metaData);
        final MixpanelAPI mixpanelAPI = mixpanelApi(config);
        assertEquals("https://api.mixpanel.com/track/?ip=0", config.getEventsEndpoint());
        assertEquals("https://api.mixpanel.com/engage/?ip=0", config.getPeopleEndpoint());
        assertEquals("https://api.mixpanel.com/groups/?ip=0", config.getGroupsEndpoint());
    }

    @Test
    public void testSetEnableLogging() throws Exception {
        final Bundle metaData = new Bundle();
        MPConfig config = mpConfig(metaData);
        final MixpanelAPI mixpanelAPI = mixpanelApi(config);
        mixpanelAPI.setEnableLogging(true);
        assertTrue(config.DEBUG);
        mixpanelAPI.setEnableLogging(false);
        assertFalse(config.DEBUG);
    }


    @Test
    public void testSetFlushBatchSize() {
        final Bundle metaData = new Bundle();
        MPConfig config = mpConfig(metaData);
        final MixpanelAPI mixpanelAPI = mixpanelApi(config);
        mixpanelAPI.setFlushBatchSize(10);
        assertEquals(10, config.getFlushBatchSize());
        mixpanelAPI.setFlushBatchSize(100);
        assertEquals(100, config.getFlushBatchSize());
    }

    @Test
    public void testSetFlushBatchSize2() {
        final Bundle metaData = new Bundle();
        metaData.putInt("com.mixpanel.android.MPConfig.FlushBatchSize", 5);
        MPConfig config = mpConfig(metaData);
        final MixpanelAPI mixpanelAPI = mixpanelApi(config);
        assertEquals(5, mixpanelAPI.getFlushBatchSize());
    }

    @Test
    public void testSetFlushBatchSizeMulptipleConfigs() {
        String fakeToken = UUID.randomUUID().toString();
        MixpanelAPI mixpanel1 = MixpanelAPI.getInstance(InstrumentationRegistry.getInstrumentation().getContext(), fakeToken, false);
        mixpanel1.setFlushBatchSize(10);
        assertEquals(10, mixpanel1.getFlushBatchSize());

        String fakeToken2 = UUID.randomUUID().toString();
        MixpanelAPI mixpanel2 = MixpanelAPI.getInstance(InstrumentationRegistry.getInstrumentation().getContext(), fakeToken2, false);
        mixpanel2.setFlushBatchSize(20);
        assertEquals(20, mixpanel2.getFlushBatchSize());
        // mixpanel2 should not overwrite the settings to mixpanel1
        assertEquals(10, mixpanel1.getFlushBatchSize());
    }


    @Test
    public void testSetMaximumDatabaseLimit() {
        final Bundle metaData = new Bundle();
        MPConfig config = mpConfig(metaData);
        final MixpanelAPI mixpanelAPI = mixpanelApi(config);
        mixpanelAPI.setMaximumDatabaseLimit(10000);
        assertEquals(10000, config.getMaximumDatabaseLimit());
    }

    @Test
    public void testSetMaximumDatabaseLimit2() {
        final Bundle metaData = new Bundle();
        metaData.putInt("com.mixpanel.android.MPConfig.MaximumDatabaseLimit", 100000000);
        MPConfig config = mpConfig(metaData);
        final MixpanelAPI mixpanelAPI = mixpanelApi(config);
        assertEquals(100000000, mixpanelAPI.getMaximumDatabaseLimit());
    }

    @Test
    public void testShouldGzipRequestPayload() {
        final Bundle metaData = new Bundle();
        metaData.putBoolean("com.mixpanel.android.MPConfig.GzipRequestPayload", true);
        MPConfig mpConfig = mpConfig(metaData);
        assertTrue(mpConfig.shouldGzipRequestPayload());

        mpConfig.setShouldGzipRequestPayload(false);
        assertFalse(mpConfig.shouldGzipRequestPayload());

        mpConfig.setShouldGzipRequestPayload(true);
        assertTrue(mpConfig.shouldGzipRequestPayload());

        // assert false by default
        MPConfig mpConfig2 = mpConfig(new Bundle());
        assertFalse(mpConfig2.shouldGzipRequestPayload());

        MixpanelAPI mixpanelAPI = mixpanelApi(mpConfig);

        assertTrue(mixpanelAPI.shouldGzipRequestPayload());

        mixpanelAPI.setShouldGzipRequestPayload(false);
        assertFalse(mixpanelAPI.shouldGzipRequestPayload());

    }

    @Test
    public void testServerURLFromMixpanelOptions() {
        MixpanelOptions options = new MixpanelOptions.Builder()
                .serverURL("https://api-eu.mixpanel.com")
                .build();

        MixpanelAPI mixpanel = MixpanelAPI.getInstance(
                InstrumentationRegistry.getInstrumentation().getContext(),
                UUID.randomUUID().toString(),
                true,
                options
        );

        MPConfig config = mixpanel.getMPConfig();
        assertEquals("https://api-eu.mixpanel.com/track/?ip=1", config.getEventsEndpoint());
        assertEquals("https://api-eu.mixpanel.com/engage/?ip=1", config.getPeopleEndpoint());
        assertEquals("https://api-eu.mixpanel.com/groups/?ip=1", config.getGroupsEndpoint());
        assertEquals("https://api-eu.mixpanel.com/flags/", config.getFlagsEndpoint());
    }

    @Test
    public void testServerURLDefaultWhenNotSet() {
        MixpanelOptions options = new MixpanelOptions.Builder()
                .build();

        MixpanelAPI mixpanel = MixpanelAPI.getInstance(
                InstrumentationRegistry.getInstrumentation().getContext(),
                UUID.randomUUID().toString(),
                true,
                options
        );

        MPConfig config = mixpanel.getMPConfig();
        assertEquals("https://api.mixpanel.com/track/?ip=1", config.getEventsEndpoint());
        assertEquals("https://api.mixpanel.com/engage/?ip=1", config.getPeopleEndpoint());
        assertEquals("https://api.mixpanel.com/groups/?ip=1", config.getGroupsEndpoint());
    }

    private MPConfig mpConfig(final Bundle metaData) {
        return new MPConfig(metaData, InstrumentationRegistry.getInstrumentation().getContext(), null);
    }

    private MixpanelAPI mixpanelApi(final MPConfig config) {
        return new MixpanelAPI(InstrumentationRegistry.getInstrumentation().getContext(), new TestUtils.EmptyPreferences(InstrumentationRegistry.getInstrumentation().getContext()), TOKEN, config, false, null, null, true);
    }
}
