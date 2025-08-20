package com.mixpanel.android.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for HttpService backup host functionality */
@RunWith(AndroidJUnit4.class)
public class HttpServiceBackupTest {

  private static final String PRIMARY_HOST = "api.mixpanel.com";
  private static final String BACKUP_HOST = "backup.mixpanel.com";
  private static final String TEST_ENDPOINT = "https://" + PRIMARY_HOST + "/track";
  private static final byte[] SUCCESS_RESPONSE = "1\n".getBytes();

  private Context mContext;

  @Before
  public void setUp() {
    mContext = InstrumentationRegistry.getInstrumentation().getContext();
  }

  /** Test that HttpService constructor accepts backup host */
  @Test
  public void testConstructorWithBackupHost() {
    HttpService service = new HttpService(false, null, BACKUP_HOST);
    // Service should be created successfully
    assertNotNull(service);
  }

  /** Test that HttpService can set backup host after construction */
  @Test
  public void testSetBackupHost() {
    HttpService service = new HttpService(false, null);

    // Set backup host
    service.setBackupHost(BACKUP_HOST);

    // Can update backup host
    service.setBackupHost("new.backup.host");

    // Can clear backup host
    service.setBackupHost(null);
  }

  /** Test URL host replacement logic using reflection */
  @Test
  public void testUrlHostReplacement() throws Exception {
    HttpService service = new HttpService(false, null, BACKUP_HOST);

    // Use reflection to test the private replaceHost method
    Method replaceHostMethod =
        HttpService.class.getDeclaredMethod("replaceHost", String.class, String.class);
    replaceHostMethod.setAccessible(true);

    // Test standard HTTPS URL
    String result1 =
        (String) replaceHostMethod.invoke(service, "https://api.mixpanel.com/track", BACKUP_HOST);
    assertEquals("https://backup.mixpanel.com/track", result1);

    // Test HTTP URL
    String result2 =
        (String) replaceHostMethod.invoke(service, "http://api.mixpanel.com/track", BACKUP_HOST);
    assertEquals("http://backup.mixpanel.com/track", result2);

    // Test URL with port
    String result3 =
        (String)
            replaceHostMethod.invoke(service, "https://api.mixpanel.com:8443/track", BACKUP_HOST);
    assertEquals("https://backup.mixpanel.com:8443/track", result3);

    // Test URL with query parameters
    String result4 =
        (String)
            replaceHostMethod.invoke(
                service, "https://api.mixpanel.com/track?param=value&other=123", BACKUP_HOST);
    assertEquals("https://backup.mixpanel.com/track?param=value&other=123", result4);

    // Test URL with path segments
    String result5 =
        (String)
            replaceHostMethod.invoke(
                service, "https://api.mixpanel.com/api/v2/track/event", BACKUP_HOST);
    assertEquals("https://backup.mixpanel.com/api/v2/track/event", result5);

    // Test malformed URL (should return original)
    String result6 = (String) replaceHostMethod.invoke(service, "not-a-valid-url", BACKUP_HOST);
    assertEquals("not-a-valid-url", result6);
  }

  /**
   * Test URL replacement logic for backup host failover.
   * Note: This test verifies the URL replacement mechanism works correctly.
   * The actual failover logic (triggered on 5xx errors) is tested via integration tests.
   */
  @Test
  public void testBackupHostLogic() throws Exception {
    HttpService service = new HttpService(false, null, BACKUP_HOST);

    // Use reflection to verify the replaceHost is called when needed
    Method replaceHostMethod =
        HttpService.class.getDeclaredMethod("replaceHost", String.class, String.class);
    replaceHostMethod.setAccessible(true);

    // Test various URLs
    String[] testUrls = {
      "https://api.mixpanel.com/track",
      "https://api.mixpanel.com/engage",
      "https://api.mixpanel.com/groups",
      "https://api.mixpanel.com/import"
    };

    for (String url : testUrls) {
      String backupUrl = (String) replaceHostMethod.invoke(service, url, BACKUP_HOST);
      assertTrue("Backup URL should contain backup host", backupUrl.contains(BACKUP_HOST));
      assertTrue(
          "Backup URL should have same path",
          backupUrl
              .substring(backupUrl.indexOf("/", 8))
              .equals(url.substring(url.indexOf("/", 8))));
    }
  }

  /** Test runtime update of backup host */
  @Test
  public void testRuntimeBackupUpdate() throws Exception {
    HttpService service = new HttpService(false, null, null);

    // Use reflection to test replaceHost with different backup hosts
    Method replaceHostMethod =
        HttpService.class.getDeclaredMethod("replaceHost", String.class, String.class);
    replaceHostMethod.setAccessible(true);

    String testUrl = "https://api.mixpanel.com/track";

    // Initially with no backup
    service.setBackupHost(null);

    // Set backup host at runtime
    service.setBackupHost(BACKUP_HOST);
    String backupUrl = (String) replaceHostMethod.invoke(service, testUrl, BACKUP_HOST);
    assertEquals("https://backup.mixpanel.com/track", backupUrl);

    // Update to different backup host
    String newBackupHost = "secondary.mixpanel.com";
    service.setBackupHost(newBackupHost);
    String newBackupUrl = (String) replaceHostMethod.invoke(service, testUrl, newBackupHost);
    assertEquals("https://secondary.mixpanel.com/track", newBackupUrl);

    // Clear backup host
    service.setBackupHost(null);
  }

  /** Test with empty backup host string */
  @Test
  public void testEmptyBackupHost() {
    HttpService service = new HttpService(false, null, "");
    // Service should handle empty string gracefully
    assertNotNull(service);

    service.setBackupHost("");
    // Should not throw exception
  }

  /** Test isOnline method */
  @Test
  public void testIsOnline() {
    HttpService service = new HttpService(false, null, BACKUP_HOST);

    // Should be online by default
    boolean isOnline = service.isOnline(mContext, null);
    // We can't assert true/false as it depends on actual network state
    // Just verify it doesn't throw exception
  }

  /** Test that service handles null parameters gracefully */
  @Test
  public void testNullParameterHandling() throws Exception {
    HttpService service = new HttpService(false, null, BACKUP_HOST);

    // Use reflection to test replaceHost with null
    Method replaceHostMethod =
        HttpService.class.getDeclaredMethod("replaceHost", String.class, String.class);
    replaceHostMethod.setAccessible(true);

    // Test with null URL (should handle gracefully)
    try {
      replaceHostMethod.invoke(service, null, BACKUP_HOST);
    } catch (Exception e) {
      // Expected - wrapped NullPointerException or similar
      assertTrue("Should handle null URL", e.getCause() != null);
    }

    // Test with null backup host
    String result = (String) replaceHostMethod.invoke(service, TEST_ENDPOINT, null);
    // Should handle null backup host gracefully
    assertNotNull(result);
  }
}
