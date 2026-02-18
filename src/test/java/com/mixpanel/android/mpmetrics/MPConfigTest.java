package com.mixpanel.android.mpmetrics;

import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import com.mixpanel.android.util.MPLog;
import com.mixpanel.android.util.OfflineMode;
import com.mixpanel.android.util.ProxyServerInteractor;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

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

    @Test
    public void testSSLSocketFactory() {
        Bundle metaData = new Bundle();
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);

        // Default SSLSocketFactory should be non-null
        SSLSocketFactory defaultFactory = config.getSSLSocketFactory();
        assertNotNull(defaultFactory);

        // Set a custom factory
        SSLSocketFactory customFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        config.setSSLSocketFactory(customFactory);
        assertSame(customFactory, config.getSSLSocketFactory());
    }

    @Test
    public void testOfflineMode() {
        Bundle metaData = new Bundle();
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);

        // Default should be null
        assertNull(config.getOfflineMode());

        // Set and get
        OfflineMode offlineMode = () -> true;
        config.setOfflineMode(offlineMode);
        assertSame(offlineMode, config.getOfflineMode());
    }

    @Test
    public void testProxyServerInteractor() {
        Bundle metaData = new Bundle();
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);

        // Default should be null
        assertNull(config.getProxyServerInteractor());

        // Set and get
        ProxyServerInteractor interactor = new ProxyServerInteractor() {
            @Override
            public Map<String, String> getProxyRequestHeaders() {
                return Collections.singletonMap("X-Custom", "value");
            }

            @Override
            public void onProxyResponse(String apiPath, int responseCode) {
            }
        };
        config.setProxyServerInteractor(interactor);
        assertSame(interactor, config.getProxyServerInteractor());
    }

    @Test
    public void testSetServerURLWithInteractor() {
        Bundle metaData = new Bundle();
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);

        ProxyServerInteractor interactor = new ProxyServerInteractor() {
            @Override
            public Map<String, String> getProxyRequestHeaders() {
                return Collections.emptyMap();
            }

            @Override
            public void onProxyResponse(String apiPath, int responseCode) {
            }
        };

        config.setServerURL("https://proxy.example.com", interactor);

        assertTrue(config.getEventsEndpoint().startsWith("https://proxy.example.com/track/"));
        assertSame(interactor, config.getProxyServerInteractor());
    }

    @Test
    public void testSetUseIpAddressForGeolocation() {
        Bundle metaData = new Bundle();
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);

        // Default is ip=1
        assertTrue(config.getEventsEndpoint().contains("ip=1"));
        assertTrue(config.getPeopleEndpoint().contains("ip=1"));
        assertTrue(config.getGroupsEndpoint().contains("ip=1"));

        // Toggle to false
        config.setUseIpAddressForGeolocation(false);
        assertTrue(config.getEventsEndpoint().contains("ip=0"));
        assertTrue(config.getPeopleEndpoint().contains("ip=0"));
        assertTrue(config.getGroupsEndpoint().contains("ip=0"));

        // Toggle back to true
        config.setUseIpAddressForGeolocation(true);
        assertTrue(config.getEventsEndpoint().contains("ip=1"));
    }

    @Test
    public void testSetEnableLogging() {
        Bundle metaData = new Bundle();
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);

        config.setEnableLogging(true);
        assertTrue(MPConfig.DEBUG);
        assertEquals(MPLog.VERBOSE, MPLog.getLevel());

        config.setEnableLogging(false);
        assertFalse(MPConfig.DEBUG);
        assertEquals(MPLog.NONE, MPLog.getLevel());
    }

    @Test
    public void testSetShouldGzipRequestPayload() {
        Bundle metaData = new Bundle();
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);

        assertFalse(config.shouldGzipRequestPayload());

        config.setShouldGzipRequestPayload(true);
        assertTrue(config.shouldGzipRequestPayload());

        config.setShouldGzipRequestPayload(false);
        assertFalse(config.shouldGzipRequestPayload());
    }

    @Test
    public void testResourcePackageName() {
        // Default is null
        Bundle metaData = new Bundle();
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);
        assertNull(config.getResourcePackageName());

        // Custom value
        metaData.putString("com.mixpanel.android.MPConfig.ResourcePackageName", "com.custom.pkg");
        config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);
        assertEquals("com.custom.pkg", config.getResourcePackageName());
    }

    @Test
    public void testBackupHost() {
        // Default is null
        Bundle metaData = new Bundle();
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);
        assertNull(config.getBackupHost());

        // Set via setter
        config.setBackupHost("backup.example.com");
        assertEquals("backup.example.com", config.getBackupHost());

        // Set via metadata
        metaData.putString("com.mixpanel.android.MPConfig.BackupHost", "meta-backup.example.com");
        config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);
        assertEquals("meta-backup.example.com", config.getBackupHost());
    }

    @Test
    public void testCustomEndpointsFromMetadata() {
        Bundle metaData = new Bundle();
        metaData.putString("com.mixpanel.android.MPConfig.EventsEndpoint", "https://custom.example.com/events");
        metaData.putString("com.mixpanel.android.MPConfig.PeopleEndpoint", "https://custom.example.com/people");
        metaData.putString("com.mixpanel.android.MPConfig.GroupsEndpoint", "https://custom.example.com/groups");
        metaData.putString("com.mixpanel.android.MPConfig.FlagsEndpoint", "https://custom.example.com/flags");

        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);

        // When UseIpAddressForGeolocation is not explicitly set (noUseIpAddressForGeolocationSetting=true),
        // custom endpoints are used as-is
        assertEquals("https://custom.example.com/events", config.getEventsEndpoint());
        assertEquals("https://custom.example.com/people", config.getPeopleEndpoint());
        assertEquals("https://custom.example.com/groups", config.getGroupsEndpoint());
        assertEquals("https://custom.example.com/flags", config.getFlagsEndpoint());
    }

    @Test
    public void testCustomEndpointsWithIpTracking() {
        Bundle metaData = new Bundle();
        metaData.putString("com.mixpanel.android.MPConfig.EventsEndpoint", "https://custom.example.com/events");
        metaData.putString("com.mixpanel.android.MPConfig.PeopleEndpoint", "https://custom.example.com/people");
        metaData.putString("com.mixpanel.android.MPConfig.GroupsEndpoint", "https://custom.example.com/groups");
        // Explicitly set UseIpAddressForGeolocation to false
        metaData.putBoolean("com.mixpanel.android.MPConfig.UseIpAddressForGeolocation", false);

        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);

        // When UseIpAddressForGeolocation is explicitly set, ip param is appended
        assertTrue(config.getEventsEndpoint().contains("ip=0"));
        assertTrue(config.getPeopleEndpoint().contains("ip=0"));
        assertTrue(config.getGroupsEndpoint().contains("ip=0"));
    }

    @Test
    public void testDataExpirationWithFloat() {
        Bundle metaData = new Bundle();
        metaData.putFloat("com.mixpanel.android.MPConfig.DataExpiration", 172800000.0f);

        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);
        assertEquals((long) 172800000.0f, config.getDataExpiration());
    }

    @Test
    public void testDataExpirationWithInvalidType() {
        Bundle metaData = new Bundle();
        metaData.putString("com.mixpanel.android.MPConfig.DataExpiration", "not_a_number");

        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);
        // Should fall back to default 5 days
        assertEquals(1000L * 60 * 60 * 24 * 5, config.getDataExpiration());
    }

    @Test
    public void testDebugLoggingEnabled() {
        Bundle metaData = new Bundle();
        metaData.putBoolean("com.mixpanel.android.MPConfig.EnableDebugLogging", true);

        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);
        assertTrue(MPConfig.DEBUG);
    }

    @Test
    public void testDeprecatedDebugFlushInterval() {
        Bundle metaData = new Bundle();
        metaData.putInt("com.mixpanel.android.MPConfig.DebugFlushInterval", 5000);

        // This should not crash, just log a warning
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);
        assertNotNull(config);
    }

    @Test
    public void testEndpointWithExistingIpParam() {
        Bundle metaData = new Bundle();
        MPConfig config = new MPConfig(metaData, ApplicationProvider.getApplicationContext(), null);

        // Default endpoints have ?ip=1, toggling should replace the existing param
        config.setUseIpAddressForGeolocation(false);
        String eventsEndpoint = config.getEventsEndpoint();
        assertTrue(eventsEndpoint.contains("?ip=0"));
        // Should not have double ?ip= params
        assertEquals(eventsEndpoint.indexOf("?ip="), eventsEndpoint.lastIndexOf("?ip="));
    }
}
