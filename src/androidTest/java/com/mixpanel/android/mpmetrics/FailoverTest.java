package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.mixpanel.android.util.HttpService;
import com.mixpanel.android.util.MPLog;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FailoverTest {
  private static final String TEST_TOKEN = "Test Token";
  private Context mContext;
  private BlockingQueue<String> mLoggingMessages;

  @Before
  public void setUp() {
    mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    mLoggingMessages = new LinkedBlockingQueue<>();

    // Set up test logging to capture log messages
    MPLog.setLevel(MPLog.VERBOSE);
  }

  @Test
  public void testFailoverConfiguration() throws Exception {
    // Test manifest configuration
    Bundle metaData = new Bundle();
    metaData.putString(
        "com.mixpanel.android.MPConfig.FailoverServerURL", "https://api-backup.mixpanel.com");
    MPConfig config = new MPConfig(metaData, mContext, null);

    assertEquals("https://api-backup.mixpanel.com", config.getFailoverServerURL());

    // Test programmatic configuration
    config.setFailoverServerURL("https://api-eu.mixpanel.com");
    assertEquals("https://api-eu.mixpanel.com", config.getFailoverServerURL());
  }

  @Test
  public void testBasicFailoverConfiguration() throws Exception {
    // Simple test with just programmatic configuration
    MPConfig config = new MPConfig(new Bundle(), mContext, null);

    // Initially null
    assertNull(config.getFailoverServerURL());

    // Set failover URL
    config.setFailoverServerURL("https://api-backup.mixpanel.com");
    assertEquals("https://api-backup.mixpanel.com", config.getFailoverServerURL());
  }

  @Test
  public void testFailoverInMixpanelAPI() throws Exception {
    // Create a clean MixpanelAPI instance with test token
    TestUtils.EmptyPreferences mockPreferences = new TestUtils.EmptyPreferences(mContext);
    MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(mContext, mockPreferences, TEST_TOKEN);

    mixpanel.setFailoverServerURL("https://api-backup.mixpanel.com");

    // We can't access mConfig directly, but we can verify the behavior through the API
    // The failover URL should be set in the underlying config

    // Track an event and flush to ensure the failover URL would be used if needed
    mixpanel.track("Test Event");
    mixpanel.flush();
  }

  @Test
  public void testHttpServiceFailoverLogic() throws Exception {
    // Create a mock error listener to track network errors
    MockNetworkErrorListener errorListener = new MockNetworkErrorListener();

    // Test with failover URL
    HttpService service = new HttpService(false, errorListener, "https://api-backup.mixpanel.com");

    // Test URL transformation
    String originalUrl = "https://api.mixpanel.com/track/?ip=1";

    // Use reflection to test the private method
    java.lang.reflect.Method method =
        HttpService.class.getDeclaredMethod("getUrlWithFailoverHost", String.class);
    method.setAccessible(true);
    String failoverUrl = (String) method.invoke(service, originalUrl);

    assertEquals("https://api-backup.mixpanel.com/track/?ip=1", failoverUrl);
  }

  @Test
  public void testFailoverWithDifferentPorts() throws Exception {
    MockNetworkErrorListener errorListener = new MockNetworkErrorListener();
    HttpService service =
        new HttpService(false, errorListener, "https://api-backup.mixpanel.com:8443");

    String originalUrl = "https://api.mixpanel.com:443/track/?ip=1";

    java.lang.reflect.Method method =
        HttpService.class.getDeclaredMethod("getUrlWithFailoverHost", String.class);
    method.setAccessible(true);
    String failoverUrl = (String) method.invoke(service, originalUrl);

    assertEquals("https://api-backup.mixpanel.com:8443/track/?ip=1", failoverUrl);
  }

  @Test
  public void testFailoverWithQueryParameters() throws Exception {
    MockNetworkErrorListener errorListener = new MockNetworkErrorListener();
    HttpService service = new HttpService(false, errorListener, "https://api-eu.mixpanel.com");

    String originalUrl = "https://api.mixpanel.com/track/?ip=1&verbose=1&test=true";

    java.lang.reflect.Method method =
        HttpService.class.getDeclaredMethod("getUrlWithFailoverHost", String.class);
    method.setAccessible(true);
    String failoverUrl = (String) method.invoke(service, originalUrl);

    assertEquals("https://api-eu.mixpanel.com/track/?ip=1&verbose=1&test=true", failoverUrl);
  }

  @Test
  public void testNoFailoverWhenNotConfigured() throws Exception {
    MockNetworkErrorListener errorListener = new MockNetworkErrorListener();
    HttpService service = new HttpService(false, errorListener, null);

    String originalUrl = "https://api.mixpanel.com/track/?ip=1";

    java.lang.reflect.Method method =
        HttpService.class.getDeclaredMethod("getUrlWithFailoverHost", String.class);
    method.setAccessible(true);
    String failoverUrl = (String) method.invoke(service, originalUrl);

    // Should return original URL when no failover is configured
    assertEquals(originalUrl, failoverUrl);
  }

  private static class MockNetworkErrorListener
      implements com.mixpanel.android.util.MixpanelNetworkErrorListener {
    public int errorCount = 0;
    public String lastUrl;
    public Exception lastException;

    @Override
    public void onNetworkError(
        String endpointUrl,
        String ipAddress,
        long durationMillis,
        long uncompressedBodySize,
        long compressedBodySize,
        int responseCode,
        String responseMessage,
        Exception exception) {
      errorCount++;
      lastUrl = endpointUrl;
      lastException = exception;
    }
  }
}
