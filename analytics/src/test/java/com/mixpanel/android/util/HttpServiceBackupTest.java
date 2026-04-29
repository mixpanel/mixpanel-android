package com.mixpanel.android.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for HttpService backup host functionality */
@RunWith(RobolectricTestRunner.class)
public class HttpServiceBackupTest {

  private static final String PRIMARY_HOST = "api.mixpanel.com";
  private static final String BACKUP_HOST = "backup.mixpanel.com";
  private static final String TEST_ENDPOINT = "https://" + PRIMARY_HOST + "/track";

  private Context mContext;

  @Before
  public void setUp() {
    mContext = ApplicationProvider.getApplicationContext();
  }

  /** Test that HttpService constructor accepts backup host */
  @Test
  public void testConstructorWithBackupHost() {
    HttpService service = new HttpService(false, null, BACKUP_HOST, null);
    assertNotNull(service);
  }

  /** Test that HttpService can set backup host after construction and it affects URL replacement */
  @Test
  public void testSetBackupHost() {
    HttpService service = new HttpService(false, null);

    service.setBackupHost(BACKUP_HOST);
    String result = service.replaceHost(TEST_ENDPOINT, BACKUP_HOST);
    assertEquals("https://backup.mixpanel.com/track", result);

    service.setBackupHost("new.backup.host");
    result = service.replaceHost(TEST_ENDPOINT, "new.backup.host");
    assertEquals("https://new.backup.host/track", result);

    service.setBackupHost(null);
  }

  /** Test URL host replacement logic */
  @Test
  public void testUrlHostReplacement() {
    HttpService service = new HttpService(false, null, BACKUP_HOST, null);

    // Test standard HTTPS URL
    assertEquals("https://backup.mixpanel.com/track",
        service.replaceHost("https://api.mixpanel.com/track", BACKUP_HOST));

    // Test HTTP URL
    assertEquals("http://backup.mixpanel.com/track",
        service.replaceHost("http://api.mixpanel.com/track", BACKUP_HOST));

    // Test URL with port
    assertEquals("https://backup.mixpanel.com:8443/track",
        service.replaceHost("https://api.mixpanel.com:8443/track", BACKUP_HOST));

    // Test URL with query parameters
    assertEquals("https://backup.mixpanel.com/track?param=value&other=123",
        service.replaceHost("https://api.mixpanel.com/track?param=value&other=123", BACKUP_HOST));

    // Test URL with path segments
    assertEquals("https://backup.mixpanel.com/api/v2/track/event",
        service.replaceHost("https://api.mixpanel.com/api/v2/track/event", BACKUP_HOST));

    // Test malformed URL (should return original)
    assertEquals("not-a-valid-url",
        service.replaceHost("not-a-valid-url", BACKUP_HOST));
  }

  /** Test backup host replacement across all endpoint paths */
  @Test
  public void testBackupHostLogic() {
    HttpService service = new HttpService(false, null, BACKUP_HOST, null);

    String[] testUrls = {
      "https://api.mixpanel.com/track",
      "https://api.mixpanel.com/engage",
      "https://api.mixpanel.com/groups",
      "https://api.mixpanel.com/import"
    };

    for (String url : testUrls) {
      String backupUrl = service.replaceHost(url, BACKUP_HOST);
      assertTrue("Backup URL should contain backup host", backupUrl.contains(BACKUP_HOST));
      assertEquals("Path should be preserved",
          url.substring(url.indexOf("/", 8)),
          backupUrl.substring(backupUrl.indexOf("/", 8)));
    }
  }

  /** Test runtime update of backup host */
  @Test
  public void testRuntimeBackupUpdate() {
    HttpService service = new HttpService(false, null, null, null);
    String testUrl = "https://api.mixpanel.com/track";

    service.setBackupHost(BACKUP_HOST);
    assertEquals("https://backup.mixpanel.com/track",
        service.replaceHost(testUrl, BACKUP_HOST));

    String newBackupHost = "secondary.mixpanel.com";
    service.setBackupHost(newBackupHost);
    assertEquals("https://secondary.mixpanel.com/track",
        service.replaceHost(testUrl, newBackupHost));

    service.setBackupHost(null);
  }

  /** Test with empty backup host string */
  @Test
  public void testEmptyBackupHost() {
    HttpService service = new HttpService(false, null, "", null);
    assertNotNull(service);
    service.setBackupHost("");
  }

  /** Test isOnline method — Robolectric's ConnectivityManager shadow reports connected by default */
  @Test
  public void testIsOnline() {
    HttpService service = new HttpService(false, null, BACKUP_HOST, null);
    assertTrue("Should be online under Robolectric default shadow", service.isOnline(mContext, null));
  }

  /** Test that replaceHost handles null inputs gracefully */
  @Test
  public void testNullParameterHandling() {
    HttpService service = new HttpService(false, null, BACKUP_HOST, null);

    // null URL — replaceHost catches the exception and returns the original (null)
    String nullUrlResult = service.replaceHost(null, BACKUP_HOST);
    assertNull("Null URL input should return null", nullUrlResult);

    // Null backup host creates a URL with empty host (Java URL constructor accepts null host)
    String nullHostResult = service.replaceHost(TEST_ENDPOINT, null);
    assertNotNull("Null backup host should not return null", nullHostResult);
    assertFalse("Result should not contain original host",
        nullHostResult.contains(PRIMARY_HOST));
  }

  // ============================================================
  // Tests for checkIsServerBlockedSync (synchronous version)
  // ============================================================

  /** Primary not blocked -> not blocked */
  @Test
  public void testCheckBlocked_PrimaryNotBlocked() {
    HttpService service = new HttpService(false, null, null, "8.8.8.8");
    service.checkIsServerBlockedSync();
    assertFalse("Should not be blocked when primary is valid host", service.isServerBlocked());
  }

  /** Primary blocked (loopback), no backup -> blocked */
  @Test
  public void testCheckBlocked_PrimaryLoopbackNoBackup() {
    HttpService service = new HttpService(false, null, null, "127.0.0.1");
    service.checkIsServerBlockedSync();
    assertTrue("Should be blocked when primary is loopback", service.isServerBlocked());
  }

  /** Primary blocked, backup available -> not blocked */
  @Test
  public void testCheckBlocked_PrimaryBlockedBackupAvailable() {
    HttpService service = new HttpService(false, null, "8.8.8.8", "127.0.0.1");
    service.checkIsServerBlockedSync();
    assertFalse("Should not be blocked when backup is available", service.isServerBlocked());
  }

  /** Both primary and backup blocked -> blocked */
  @Test
  public void testCheckBlocked_BothBlocked() {
    HttpService service = new HttpService(false, null, "127.0.0.1", "127.0.0.1");
    service.checkIsServerBlockedSync();
    assertTrue("Should be blocked when both are loopback", service.isServerBlocked());
  }

  /** Primary blocked, backup DNS fails -> blocked */
  @Test
  public void testCheckBlocked_BackupDnsFails() {
    HttpService service = new HttpService(false, null, "invalid..hostname..test", "127.0.0.1");
    service.checkIsServerBlockedSync();
    assertTrue("Should be blocked when backup DNS fails", service.isServerBlocked());
  }

  /** Primary DNS fails -> don't assume blocked (state unchanged) */
  @Test
  public void testCheckBlocked_PrimaryDnsFails() {
    HttpService service = new HttpService(false, null, null, "invalid..hostname..test");
    service.checkIsServerBlockedSync();
    assertFalse("DNS failure should not set blocked state", service.isServerBlocked());
  }

  /** Primary blocked, empty backup -> blocked */
  @Test
  public void testCheckBlocked_PrimaryBlockedEmptyBackup() {
    HttpService service = new HttpService(false, null, "", "127.0.0.1");
    service.checkIsServerBlockedSync();
    assertTrue("Should be blocked when backup is empty", service.isServerBlocked());
  }
}
