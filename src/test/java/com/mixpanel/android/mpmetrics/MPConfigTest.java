package com.mixpanel.android.mpmetrics;

import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class MPConfigTest {

    @Test
    public void testDefaultConfigValues() {
        Bundle metaData = new Bundle();
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);

        assertEquals(40, config.getBulkUploadLimit());
        assertEquals(60000, config.getFlushInterval());
        assertTrue(config.getFlushOnBackground());
        assertEquals(50, config.getFlushBatchSize());
        assertEquals(1000L * 60 * 60 * 24 * 5, config.getDataExpiration());
        assertEquals(20 * 1024 * 1024, config.getMinimumDatabaseLimit());
        assertEquals(Integer.MAX_VALUE, config.getMaximumDatabaseLimit());
        assertTrue(config.getDisableAppOpenEvent());
        assertFalse(config.getDisableExceptionHandler());
        assertEquals(10000, config.getMinimumSessionDuration());
        assertEquals(Integer.MAX_VALUE, config.getSessionTimeoutDuration());
        assertFalse(config.getRemoveLegacyResidualFiles());
        assertFalse(config.shouldGzipRequestPayload());
    }

    @Test
    public void testCustomConfigValues() {
        Bundle metaData = new Bundle();
        metaData.putInt("com.mixpanel.android.MPConfig.BulkUploadLimit", 100);
        metaData.putInt("com.mixpanel.android.MPConfig.FlushInterval", 30000);
        metaData.putBoolean("com.mixpanel.android.MPConfig.FlushOnBackground", false);
        metaData.putInt("com.mixpanel.android.MPConfig.FlushBatchSize", 25);
        metaData.putBoolean("com.mixpanel.android.MPConfig.DisableAppOpenEvent", false);
        metaData.putBoolean("com.mixpanel.android.MPConfig.DisableExceptionHandler", true);
        metaData.putInt("com.mixpanel.android.MPConfig.MinimumSessionDuration", 5000);
        metaData.putInt("com.mixpanel.android.MPConfig.SessionTimeoutDuration", 60000);
        metaData.putBoolean("com.mixpanel.android.MPConfig.RemoveLegacyResidualFiles", true);

        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);

        assertEquals(100, config.getBulkUploadLimit());
        assertEquals(30000, config.getFlushInterval());
        assertFalse(config.getFlushOnBackground());
        assertEquals(25, config.getFlushBatchSize());
        assertFalse(config.getDisableAppOpenEvent());
        assertTrue(config.getDisableExceptionHandler());
        assertEquals(5000, config.getMinimumSessionDuration());
        assertEquals(60000, config.getSessionTimeoutDuration());
        assertTrue(config.getRemoveLegacyResidualFiles());
    }

    @Test
    public void testDataExpirationWithInteger() {
        Bundle metaData = new Bundle();
        metaData.putInt("com.mixpanel.android.MPConfig.DataExpiration", 86400000);

        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);
        assertEquals(86400000L, config.getDataExpiration());
    }

    @Test
    public void testDefaultEndpoints() {
        Bundle metaData = new Bundle();
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);

        assertTrue(config.getEventsEndpoint().startsWith("https://api.mixpanel.com/track/"));
        assertTrue(config.getPeopleEndpoint().startsWith("https://api.mixpanel.com/engage/"));
        assertTrue(config.getGroupsEndpoint().startsWith("https://api.mixpanel.com/groups/"));
        assertTrue(config.getFlagsEndpoint().startsWith("https://api.mixpanel.com/flags/"));
    }

    @Test
    public void testSetServerURL() {
        Bundle metaData = new Bundle();
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);

        config.setServerURL("https://api-eu.mixpanel.com");

        assertTrue(config.getEventsEndpoint().startsWith("https://api-eu.mixpanel.com/track/"));
        assertTrue(config.getPeopleEndpoint().startsWith("https://api-eu.mixpanel.com/engage/"));
        assertTrue(config.getGroupsEndpoint().startsWith("https://api-eu.mixpanel.com/groups/"));
        assertTrue(config.getFlagsEndpoint().startsWith("https://api-eu.mixpanel.com/flags/"));
    }

    @Test
    public void testSetFlushBatchSize() {
        Bundle metaData = new Bundle();
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);

        config.setFlushBatchSize(100);
        assertEquals(100, config.getFlushBatchSize());
    }

    @Test
    public void testSetMaximumDatabaseLimit() {
        Bundle metaData = new Bundle();
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);

        config.setMaximumDatabaseLimit(50 * 1024 * 1024);
        assertEquals(50 * 1024 * 1024, config.getMaximumDatabaseLimit());
    }

    @Test
    public void testInstanceName() {
        Bundle metaData = new Bundle();
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), "test_instance");
        assertEquals("test_instance", config.getInstanceName());
    }

    @Test
    public void testTrackAutomaticEvents() {
        Bundle metaData = new Bundle();
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);

        assertTrue(config.getTrackAutomaticEvents());
        config.setTrackAutomaticEvents(false);
        assertFalse(config.getTrackAutomaticEvents());
    }

    @Test
    public void testGzipSetting() {
        Bundle metaData = new Bundle();
        metaData.putBoolean("com.mixpanel.android.MPConfig.GzipRequestPayload", true);
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);
        assertTrue(config.shouldGzipRequestPayload());
    }

    @Test
    public void testToString() {
        Bundle metaData = new Bundle();
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);
        String str = config.toString();
        assertNotNull(str);
        assertTrue(str.contains("Mixpanel"));
    }

    @Test
    public void testReadConfig() {
        MPConfig config = MPConfig.readConfig(ApplicationProvider.getApplicationContext(), null);
        assertNotNull(config);
    }
}
