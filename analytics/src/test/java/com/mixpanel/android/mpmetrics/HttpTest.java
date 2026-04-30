package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import com.mixpanel.android.util.Base64Coder;
import com.mixpanel.android.util.HttpService;
import com.mixpanel.android.util.ProxyServerInteractor;
import com.mixpanel.android.util.RemoteService;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
public class HttpTest {
  private Future<SharedPreferences> mMockPreferences;
  private List<Object> mFlushResults;
  private BlockingQueue<String> mPerformRequestCalls;
  private List<String> mCleanupCalls;
  private MixpanelAPI mMetrics;
  private volatile int mFlushInterval;
  private volatile boolean mForceOverMemThreshold;
  private static final long POLL_WAIT_MAX_MILLISECONDS = 1000; // Reduced from 3500ms
  private static final TimeUnit DEFAULT_TIMEUNIT = TimeUnit.MILLISECONDS;
  private static final String SUCCEED_TEXT = "Should Succeed";
  private static final String FAIL_TEXT = "Should Fail";

  @Before
  public void setUp() {
    mFlushInterval = 100; // Reduced from 2000ms for faster tests
    mMockPreferences =
        new TestUtils.EmptyPreferences(ApplicationProvider.getApplicationContext());
    mFlushResults = new ArrayList<Object>();
    mPerformRequestCalls = new LinkedBlockingQueue<String>();
    mCleanupCalls = new ArrayList<String>();
    mForceOverMemThreshold = false;

    final RemoteService mockPoster =
        new HttpService() {
          @Override
          public RemoteService.RequestResult performRequest(
              @NonNull String endpointUrl,
              @Nullable ProxyServerInteractor interactor,
              @Nullable Map<String, Object> params, // Used only if requestBodyBytes is null
              @Nullable Map<String, String> headers,
              @Nullable byte[] requestBodyBytes, // If provided, send this as raw body
              @Nullable SSLSocketFactory socketFactory)
              throws ServiceUnavailableException, IOException {
            return performRequest(RemoteService.HttpMethod.POST, endpointUrl, interactor, params, headers, requestBodyBytes, socketFactory);
          }

          @Override
          public RemoteService.RequestResult performRequest(
              @NonNull RemoteService.HttpMethod method,
              @NonNull String endpointUrl,
              @Nullable ProxyServerInteractor interactor,
              @Nullable Map<String, Object> params,
              @Nullable Map<String, String> headers,
              @Nullable byte[] requestBodyBytes,
              @Nullable SSLSocketFactory socketFactory)
              throws ServiceUnavailableException, IOException {
            try {
              if (mFlushResults.isEmpty()) {
                mFlushResults.add(TestUtils.bytes("1\n"));
              }
              assertTrue(params.containsKey("data"));

              final Object obj = mFlushResults.remove(0);
              if (obj instanceof IOException) {
                throw (IOException) obj;
              } else if (obj instanceof MalformedURLException) {
                throw (MalformedURLException) obj;
              } else if (obj instanceof ServiceUnavailableException) {
                throw (ServiceUnavailableException) obj;
              } else if (obj instanceof SocketTimeoutException) {
                throw (SocketTimeoutException) obj;
              }

              final String jsonData = Base64Coder.decodeString(params.get("data").toString());
              JSONArray msg = new JSONArray(jsonData);
              JSONObject event = msg.getJSONObject(0);
              mPerformRequestCalls.put(event.getString("event"));

              return RemoteService.RequestResult.success((byte[]) obj, endpointUrl);
            } catch (JSONException e) {
              throw new RuntimeException("Malformed data passed to test mock", e);
            } catch (InterruptedException e) {
              throw new RuntimeException(
                  "Could not write message to reporting queue for tests.", e);
            }
          }
        };

    final MPConfig config =
        new MPConfig(
            new Bundle(), ApplicationProvider.getApplicationContext(), null) {

          @Override
          public String getEventsEndpoint() {
            return "EVENTS ENDPOINT";
          }

          @Override
          public int getFlushInterval() {
            return mFlushInterval;
          }
        };

    final MPDbAdapter mockAdapter =
        new MPDbAdapter(ApplicationProvider.getApplicationContext(), config) {
          @Override
          public void cleanupEvents(String last_id, Table table, String token) {
            mCleanupCalls.add("called");
            super.cleanupEvents(last_id, table, token);
          }

          @Override
          protected boolean aboveMemThreshold() {
            if (mForceOverMemThreshold) {
              return true;
            } else {
              return super.aboveMemThreshold();
            }
          }
        };

    final AnalyticsMessages listener =
        new AnalyticsMessages(ApplicationProvider.getApplicationContext(), config) {
          @Override
          protected MPDbAdapter makeDbAdapter(Context context) {
            return mockAdapter;
          }

          @Override
          protected RemoteService getPoster() {
            return mockPoster;
          }
        };

    mMetrics =
        new TestUtils.CleanMixpanelAPI(
            ApplicationProvider.getApplicationContext(),
            mMockPreferences,
            "Test Message Queuing") {
          @Override
          protected AnalyticsMessages getAnalyticsMessages() {
            return listener;
          }
        };
  }

  @Test
  public void testBasicSucceed() throws InterruptedException {
    mCleanupCalls.clear();
    mMetrics.track(SUCCEED_TEXT, null);
    waitForFlushInternval();
    assertEquals(
        SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
    assertEquals(null, mPerformRequestCalls.poll());
    // Wait a bit for cleanup to complete
    waitForCleanup(1);
    assertEquals(1, mCleanupCalls.size());
  }

  @Test
  public void testIOException() throws InterruptedException {
    mCleanupCalls.clear();
    mFlushResults.add(new IOException());
    mMetrics.track(SUCCEED_TEXT, null);

    waitForFlushInternval();

    assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
    assertEquals(0, mCleanupCalls.size());

    mMetrics.track(SUCCEED_TEXT, null);
    mMetrics.track(SUCCEED_TEXT, null);
    mMetrics.track(SUCCEED_TEXT, null);
    mMetrics.track(SUCCEED_TEXT, null);

    waitForBackOffTimeInterval();

    waitForCleanup(1);
    assertEquals(1, mCleanupCalls.size());
    assertEquals(
        SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
    assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));

    mMetrics.track(SUCCEED_TEXT, null);
    mMetrics.track(SUCCEED_TEXT, null);

    waitForFlushInternval();

    waitForCleanup(2);
    assertEquals(2, mCleanupCalls.size());
    assertEquals(
        SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
  }

  @Test
  public void testMalformedURLException() throws InterruptedException {
    mCleanupCalls.clear();
    mFlushResults.add(new MalformedURLException());
    mMetrics.track(SUCCEED_TEXT, null);

    waitForFlushInternval();

    assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
    assertEquals(1, mCleanupCalls.size());

    mFlushResults.add(new MalformedURLException());
    mMetrics.track(SUCCEED_TEXT, null);
    mMetrics.track(SUCCEED_TEXT, null);
    mMetrics.track(SUCCEED_TEXT, null);
    mMetrics.track(SUCCEED_TEXT, null);

    waitForFlushInternval();

    waitForCleanup(2);
    assertEquals(2, mCleanupCalls.size());
    assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
    assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));

    mMetrics.track(SUCCEED_TEXT, null);
    mMetrics.track(SUCCEED_TEXT, null);

    waitForFlushInternval();

    waitForCleanup(3);
    assertEquals(3, mCleanupCalls.size());
    assertEquals(
        SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
  }

  @Test
  public void testServiceUnavailableException() throws InterruptedException {
    runServiceUnavailableExceptionWithRetryAfter(null);
  }

  @Test
  public void testServiceUnavailableExceptionWithRetryAfter10() throws InterruptedException {
    runServiceUnavailableExceptionWithRetryAfter("10");
  }

  @Test
  public void testServiceUnavailableExceptionWithRetryAfter40() throws InterruptedException {
    runServiceUnavailableExceptionWithRetryAfter("40");
  }

  private void runServiceUnavailableExceptionWithRetryAfter(String retryAfterSeconds)
      throws InterruptedException {
    mCleanupCalls.clear();
    mFlushResults.add(new RemoteService.ServiceUnavailableException("", retryAfterSeconds));
    mMetrics.track(SUCCEED_TEXT, null);

    waitForFlushInternval();

    assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
    assertEquals(0, mCleanupCalls.size());

    mMetrics.track(SUCCEED_TEXT, null);
    mMetrics.track(SUCCEED_TEXT, null);
    mMetrics.track(SUCCEED_TEXT, null);
    mMetrics.track(SUCCEED_TEXT, null);

    waitForBackOffTimeInterval();

    waitForCleanup(1);
    assertEquals(1, mCleanupCalls.size());
    assertEquals(
        SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
    assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));

    mMetrics.track(SUCCEED_TEXT, null);
    mMetrics.track(SUCCEED_TEXT, null);

    waitForFlushInternval();

    waitForCleanup(2);
    assertEquals(2, mCleanupCalls.size());
    assertEquals(
        SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
  }

  @Ignore("Double back-off timing not reliably simulated via ShadowLooper — requires emulator")
  @Test
  public void testDoubleServiceUnavailableException() throws InterruptedException {
    mCleanupCalls.clear();
    mFlushResults.add(new RemoteService.ServiceUnavailableException("", ""));
    mFlushResults.add(new RemoteService.ServiceUnavailableException("", ""));
    mMetrics.track(SUCCEED_TEXT, null);

    waitForFlushInternval();

    assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
    assertEquals(0, mCleanupCalls.size());

    int numEvents = 2 * 50 + 20; // we send batches of 50 each time
    for (int i = 0; i <= numEvents; i++) {
      mMetrics.track(SUCCEED_TEXT, null);
    }

    waitForBackOffTimeInterval();

    assertEquals(0, mCleanupCalls.size());
    assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));

    waitForBackOffTimeInterval();

    waitForCleanup(3);
    assertEquals(3, mCleanupCalls.size());
    assertEquals(
        SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
    assertEquals(
        SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
    assertEquals(
        SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
    assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
  }

  @Test
  public void testMemoryThreshold() throws InterruptedException {
    mForceOverMemThreshold = true;
    mCleanupCalls.clear();
    mMetrics.track(FAIL_TEXT, null);
    waitForFlushInternval();
    assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
    assertEquals(0, mCleanupCalls.size());

    mForceOverMemThreshold = false;
    mMetrics.track(SUCCEED_TEXT, null);
    waitForFlushInternval();
    assertEquals(
        SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
    assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
    waitForCleanup(1);
    assertEquals(1, mCleanupCalls.size());
  }

  private void flushAllLoopers() {
    for (int i = 0; i < 10; i++) {
      ShadowLooper.idleMainLooper();
      for (Looper looper : ShadowLooper.getAllLoopers()) {
        Shadows.shadowOf(looper).idle();
      }
      try { Thread.sleep(50); } catch (InterruptedException ignored) {}
      ShadowLooper.idleMainLooper();
      for (Looper looper : ShadowLooper.getAllLoopers()) {
        Shadows.shadowOf(looper).idle();
      }
    }
  }

  private void waitForBackOffTimeInterval() throws InterruptedException {
    long waitForMs = mMetrics.getAnalyticsMessages().getTrackEngageRetryAfter();
    for (Looper looper : ShadowLooper.getAllLoopers()) {
      Shadows.shadowOf(looper).idleFor(Duration.ofMillis(waitForMs + 100));
    }
    flushAllLoopers();
  }

  private void waitForFlushInternval() throws InterruptedException {
    for (Looper looper : ShadowLooper.getAllLoopers()) {
      Shadows.shadowOf(looper).idleFor(Duration.ofMillis(mFlushInterval + 100));
    }
    flushAllLoopers();
  }

  private void waitForCleanup(int expectedCount) throws InterruptedException {
    // Flush loopers and give time for cleanup callbacks
    for (int i = 0; i < 5; i++) {
      if (mCleanupCalls.size() >= expectedCount) {
        return;
      }
      flushAllLoopers();
      Thread.sleep(50);
    }
  }
}
