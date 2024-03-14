package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.mixpanel.android.util.Base64Coder;
import com.mixpanel.android.util.RemoteService;
import com.mixpanel.android.util.HttpService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class HttpTest {
    private Future<SharedPreferences> mMockPreferences;
    private List<Object> mFlushResults;
    private BlockingQueue<String> mPerformRequestCalls;
    private List<String> mCleanupCalls;
    private MixpanelAPI mMetrics;
    private volatile int mFlushInterval;
    private volatile boolean mForceOverMemThreshold;
    private static final long POLL_WAIT_MAX_MILLISECONDS = 3500;
    private static final TimeUnit DEFAULT_TIMEUNIT = TimeUnit.MILLISECONDS;
    private static final String SUCCEED_TEXT = "Should Succeed";
    private static final String FAIL_TEXT = "Should Fail";

    @Before
    public void setUp() {
        mFlushInterval = 2 * 1000;
        mMockPreferences = new TestUtils.EmptyPreferences(InstrumentationRegistry.getInstrumentation().getContext());
        mFlushResults = new ArrayList<Object>();
        mPerformRequestCalls = new LinkedBlockingQueue<String>();
        mCleanupCalls = new ArrayList<String>();
        mForceOverMemThreshold = false;

        final RemoteService mockPoster = new HttpService() {
            @Override
            public byte[] performRequest(String endpointUrl, Map<String, Object> params, SSLSocketFactory socketFactory)
                    throws ServiceUnavailableException, IOException {
                try {
                    if (mFlushResults.isEmpty()) {
                        mFlushResults.add(TestUtils.bytes("1\n"));
                    }
                    assertTrue(params.containsKey("data"));

                    final Object obj = mFlushResults.remove(0);
                    if (obj instanceof IOException) {
                        throw (IOException)obj;
                    } else if (obj instanceof MalformedURLException) {
                        throw (MalformedURLException)obj;
                    } else if (obj instanceof ServiceUnavailableException) {
                        throw (ServiceUnavailableException)obj;
                    } else if (obj instanceof SocketTimeoutException) {
                        throw (SocketTimeoutException)obj;
                    }

                    final String jsonData = Base64Coder.decodeString(params.get("data").toString());
                    JSONArray msg = new JSONArray(jsonData);
                    JSONObject event = msg.getJSONObject(0);
                    mPerformRequestCalls.put(event.getString("event"));

                    return (byte[])obj;
                } catch (JSONException e) {
                    throw new RuntimeException("Malformed data passed to test mock", e);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Could not write message to reporting queue for tests.", e);
                }
            }
        };

        final MPConfig config = new MPConfig(new Bundle(), InstrumentationRegistry.getInstrumentation().getContext(), null) {

            @Override
            public String getEventsEndpoint() {
                return "EVENTS ENDPOINT";
            }

            @Override
            public int getFlushInterval() {
                return mFlushInterval;
            }
        };

        final MPDbAdapter mockAdapter = new MPDbAdapter(InstrumentationRegistry.getInstrumentation().getContext(), config) {
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

        final AnalyticsMessages listener = new AnalyticsMessages(InstrumentationRegistry.getInstrumentation().getContext(), config) {
            @Override
            protected MPDbAdapter makeDbAdapter(Context context) {
                return mockAdapter;
            }

            @Override
            protected RemoteService getPoster() {
                return mockPoster;
            }

        };

        mMetrics = new TestUtils.CleanMixpanelAPI(InstrumentationRegistry.getInstrumentation().getContext(), mMockPreferences, "Test Message Queuing") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return listener;
            }
        };
    }

    @Test
    public void testHTTPFailures() {
        try {
            runBasicSucceed();
            runIOException();
            runMalformedURLException();
            runServiceUnavailableException(null);
            runServiceUnavailableException("10");
            runServiceUnavailableException("40");
            runDoubleServiceUnavailableException();
            runBasicSucceed();
            runMemoryTest();
        } catch (InterruptedException e) {
            throw new RuntimeException("Test was interrupted.");
        }
    }

    public void runBasicSucceed() throws InterruptedException {
        mCleanupCalls.clear();
        mMetrics.track(SUCCEED_TEXT, null);
        waitForFlushInternval();
        assertEquals(SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
        assertEquals(null, mPerformRequestCalls.poll());
        assertEquals(1, mCleanupCalls.size());
    }

    public void runIOException() throws InterruptedException {
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

        assertEquals(1, mCleanupCalls.size());
        assertEquals(SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
        assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));

        mMetrics.track(SUCCEED_TEXT, null);
        mMetrics.track(SUCCEED_TEXT, null);

        waitForFlushInternval();

        assertEquals(2, mCleanupCalls.size());
        assertEquals(SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
    }

    public void runMalformedURLException() throws InterruptedException {
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

        assertEquals(2, mCleanupCalls.size());
        assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
        assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));

        mMetrics.track(SUCCEED_TEXT, null);
        mMetrics.track(SUCCEED_TEXT, null);

        waitForFlushInternval();

        assertEquals(3, mCleanupCalls.size());
        assertEquals(SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
    }

    private void runServiceUnavailableException(String retryAfterSeconds) throws InterruptedException {
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

        assertEquals(1, mCleanupCalls.size());
        assertEquals(SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
        assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));

        mMetrics.track(SUCCEED_TEXT, null);
        mMetrics.track(SUCCEED_TEXT, null);

        waitForFlushInternval();

        assertEquals(2, mCleanupCalls.size());
        assertEquals(SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
    }

    private void runDoubleServiceUnavailableException() throws InterruptedException {
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

        assertEquals(3, mCleanupCalls.size());
        assertEquals(SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
        assertEquals(SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
        assertEquals(SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
        assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
    }

    private void runMemoryTest() throws InterruptedException {
        mForceOverMemThreshold = true;
        mCleanupCalls.clear();
        mMetrics.track(FAIL_TEXT, null);
        waitForFlushInternval();
        assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
        assertEquals(0, mCleanupCalls.size());

        mForceOverMemThreshold = false;
        mMetrics.track(SUCCEED_TEXT, null);
        waitForFlushInternval();
        assertEquals(SUCCEED_TEXT, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
        assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_MAX_MILLISECONDS, DEFAULT_TIMEUNIT));
        assertEquals(1, mCleanupCalls.size());
    }

    private void waitForBackOffTimeInterval() throws InterruptedException {
        long waitForMs = mMetrics.getAnalyticsMessages().getTrackEngageRetryAfter();
        Thread.sleep(waitForMs);
        Thread.sleep(1500);
    }

    private void waitForFlushInternval() throws InterruptedException {
        Thread.sleep(mFlushInterval);
        Thread.sleep(1500);
    }
}
