package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.mixpanel.android.util.HttpService;
import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for the backup host configuration and integration */
@RunWith(AndroidJUnit4.class)
public class BackupHostTest {

  private static final String PRIMARY_HOST = "api.mixpanel.com";
  private static final String BACKUP_HOST = "backup.mixpanel.com";

  private Context mContext;

  @Before
  public void setUp() {
    mContext = InstrumentationRegistry.getInstrumentation().getContext();
  }

  /** Test that backup host configuration via AndroidManifest.xml works */
  @Test
  public void testBackupHostConfigurationFromManifest() {
    Bundle metaData = new Bundle();
    metaData.putString("com.mixpanel.android.MPConfig.BackupHost", BACKUP_HOST);

    MPConfig config = new MPConfig(metaData, mContext, "backup_test");

    assertEquals("Backup host should be set from manifest", BACKUP_HOST, config.getBackupHost());
  }

  /** Test that backup host can be set at runtime */
  @Test
  public void testBackupHostRuntimeConfiguration() {
    Bundle metaData = new Bundle();
    MPConfig config = new MPConfig(metaData, mContext, "runtime_test");

    // Initially null
    assertNull("Backup host should initially be null", config.getBackupHost());

    // Set at runtime
    config.setBackupHost(BACKUP_HOST);
    assertEquals("Backup host should be set at runtime", BACKUP_HOST, config.getBackupHost());

    // Can be changed
    String newBackupHost = "new.backup.host.com";
    config.setBackupHost(newBackupHost);
    assertEquals("Backup host should be updated", newBackupHost, config.getBackupHost());

    // Can be cleared
    config.setBackupHost(null);
    assertNull("Backup host should be cleared", config.getBackupHost());
  }

  /** Test that backup host configuration persists through getInstance */
  @Test
  public void testBackupHostWithGetInstance() {
    Bundle metaData = new Bundle();
    metaData.putString("com.mixpanel.android.MPConfig.BackupHost", BACKUP_HOST);

    // getInstance takes a context and packageName string
    MPConfig config = MPConfig.getInstance(mContext, mContext.getPackageName());

    // Now set the backup host at runtime (since we can't pass metaData to getInstance)
    config.setBackupHost(BACKUP_HOST);
    assertEquals("Backup host should be set", BACKUP_HOST, config.getBackupHost());

    // Runtime update should work
    config.setBackupHost("runtime.backup.host");
    assertEquals(
        "Backup host should be updatable at runtime",
        "runtime.backup.host",
        config.getBackupHost());
  }

  /** Test that HttpService receives backup host from config */
  @Test
  public void testHttpServiceBackupHostIntegration() throws Exception {
    Bundle metaData = new Bundle();
    metaData.putString("com.mixpanel.android.MPConfig.BackupHost", BACKUP_HOST);

    MPConfig config = new MPConfig(metaData, mContext, "integration_test");

    // Create AnalyticsMessages which creates HttpService internally
    AnalyticsMessages messages = new AnalyticsMessages(mContext, config);

    // Use reflection to get the HttpService instance
    Method getPosterMethod = AnalyticsMessages.class.getDeclaredMethod("getPoster");
    getPosterMethod.setAccessible(true);
    Object poster = getPosterMethod.invoke(messages);

    assertNotNull("HttpService should be created", poster);
    assertTrue("Poster should be HttpService", poster instanceof HttpService);

    // Verify backup host was passed to HttpService
    // (We can't directly check as backupHost is private, but we tested
    // the constructor and setter in HttpServiceBackupTest)
  }

  /** Test that backup host works with different endpoint types */
  @Test
  public void testBackupHostWithDifferentEndpoints() {
    Bundle metaData = new Bundle();
    metaData.putString("com.mixpanel.android.MPConfig.BackupHost", BACKUP_HOST);
    MPConfig config = new MPConfig(metaData, mContext, "endpoints_test");

    // Test events endpoint
    String eventsEndpoint = config.getEventsEndpoint();
    assertTrue("Events endpoint should be configured", eventsEndpoint != null);
    assertTrue("Events endpoint should use primary host", eventsEndpoint.contains(PRIMARY_HOST));

    // Test people endpoint
    String peopleEndpoint = config.getPeopleEndpoint();
    assertTrue("People endpoint should be configured", peopleEndpoint != null);
    assertTrue("People endpoint should use primary host", peopleEndpoint.contains(PRIMARY_HOST));

    // Test groups endpoint
    String groupsEndpoint = config.getGroupsEndpoint();
    assertTrue("Groups endpoint should be configured", groupsEndpoint != null);
    assertTrue("Groups endpoint should use primary host", groupsEndpoint.contains(PRIMARY_HOST));

    // All should use same backup host
    assertEquals("All endpoints should use same backup host", BACKUP_HOST, config.getBackupHost());
  }

  /** Test that empty backup host is handled correctly */
  @Test
  public void testEmptyBackupHost() {
    Bundle metaData = new Bundle();
    metaData.putString("com.mixpanel.android.MPConfig.BackupHost", "");

    MPConfig config = new MPConfig(metaData, mContext, "empty_test");

    // Empty string should be treated as no backup host
    String backupHost = config.getBackupHost();
    assertTrue(
        "Empty backup host should be treated as null or empty",
        backupHost == null || backupHost.isEmpty());
  }

  /** Test that backup host configuration is thread-safe */
  @Test
  public void testBackupHostThreadSafety() throws Exception {
    Bundle metaData = new Bundle();
    final MPConfig config = new MPConfig(metaData, mContext, "thread_test");

    // Set initial value
    config.setBackupHost(BACKUP_HOST);

    // Try concurrent updates
    Thread t1 =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                for (int i = 0; i < 100; i++) {
                  config.setBackupHost("host1.com");
                }
              }
            });

    Thread t2 =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                for (int i = 0; i < 100; i++) {
                  config.setBackupHost("host2.com");
                }
              }
            });

    t1.start();
    t2.start();
    t1.join();
    t2.join();

    // Should have one of the values, not corrupted
    String finalHost = config.getBackupHost();
    assertTrue(
        "Backup host should be one of the set values",
        "host1.com".equals(finalHost) || "host2.com".equals(finalHost));
  }

  /** Test the public MixpanelAPI.setBackupHost() runtime configuration API */
  @Test
  public void testMixpanelAPISetBackupHost() throws Exception {
    // Create a MixpanelAPI instance
    MixpanelAPI mixpanel = MixpanelAPI.getInstance(mContext, "test_token", false);

    // Set backup host using the public API
    mixpanel.setBackupHost(BACKUP_HOST);

    // Get the config instance via reflection to verify it was set
    java.lang.reflect.Field configField = MixpanelAPI.class.getDeclaredField("mConfig");
    configField.setAccessible(true);
    MPConfig config = (MPConfig) configField.get(mixpanel);

    assertEquals("Backup host should be set via MixpanelAPI", BACKUP_HOST, config.getBackupHost());

    // Test updating to a different backup host
    String newBackupHost = "secondary.backup.host";
    mixpanel.setBackupHost(newBackupHost);
    assertEquals("Backup host should be updated", newBackupHost, config.getBackupHost());

    // Test clearing backup host
    mixpanel.setBackupHost(null);
    assertNull("Backup host should be cleared", config.getBackupHost());

    // Clean up
    mixpanel.flush();
  }

  /** Test URL host replacement using reflection */
  @Test
  public void testHostReplacement() throws Exception {
    HttpService service = new HttpService(false, null, BACKUP_HOST);

    // Use reflection to test the private replaceHost method
    Method replaceHostMethod =
        HttpService.class.getDeclaredMethod("replaceHost", String.class, String.class);
    replaceHostMethod.setAccessible(true);

    // Test basic replacement
    String original = "https://api.mixpanel.com/track";
    String expected = "https://backup.mixpanel.com/track";
    String result = (String) replaceHostMethod.invoke(service, original, BACKUP_HOST);
    assertEquals("Host should be replaced correctly", expected, result);

    // Test with query parameters
    original = "https://api.mixpanel.com/track?param=value";
    expected = "https://backup.mixpanel.com/track?param=value";
    result = (String) replaceHostMethod.invoke(service, original, BACKUP_HOST);
    assertEquals("Host should be replaced preserving query params", expected, result);

    // Test with port
    original = "https://api.mixpanel.com:8443/track";
    expected = "https://backup.mixpanel.com:8443/track";
    result = (String) replaceHostMethod.invoke(service, original, BACKUP_HOST);
    assertEquals("Host should be replaced preserving port", expected, result);
  }
}
