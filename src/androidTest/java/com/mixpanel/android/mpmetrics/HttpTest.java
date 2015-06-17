package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.test.AndroidTestCase;

import com.mixpanel.android.util.Base64Coder;
import com.mixpanel.android.util.RemoteService;
import com.mixpanel.android.util.HttpService;

import org.apache.http.NameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;

public class HttpTest extends AndroidTestCase {

    public void setUp() {
        mDisableFallback = false;
        mMockPreferences = new TestUtils.EmptyPreferences(getContext());
        mFlushResults = new ArrayList<Object>();
        mPerformRequestCalls = new LinkedBlockingQueue<String>();
        mDecideCalls = new LinkedBlockingQueue<String>();
        mCleanupCalls = new ArrayList<String>();
        mDecideResults = new ArrayList<Object>();
        mForceOverMemThreshold = false;

        final RemoteService mockPoster = new HttpService() {
            @Override
            public byte[] performRequest(String endpointUrl, List<NameValuePair> nameValuePairs, SSLSocketFactory socketFactory)
                throws ServiceUnavailableException, IOException {
                try {
                    if (null == nameValuePairs) {
                        mDecideCalls.put(endpointUrl);

                        if (mDecideResults.isEmpty()) {
                            return TestUtils.bytes("{}");
                        }

                        final Object obj = mDecideResults.remove(0);
                        if (obj instanceof IOException) {
                            throw (IOException)obj;
                        } else if (obj instanceof MalformedURLException) {
                            throw (MalformedURLException)obj;
                        } else if (obj instanceof ServiceUnavailableException) {
                            throw (ServiceUnavailableException)obj;
                        }
                        return (byte[])obj;
                    }
                    // ELSE


                    assertEquals(nameValuePairs.get(0).getName(), "data");
                    final String jsonData = Base64Coder.decodeString(nameValuePairs.get(0).getValue());
                    JSONArray msg = new JSONArray(jsonData);
                    JSONObject event = msg.getJSONObject(0);
                    mPerformRequestCalls.put(event.getString("event"));

                    if (mFlushResults.isEmpty()) {
                        return TestUtils.bytes("1");
                    }

                    final Object obj = mFlushResults.remove(0);
                    if (obj instanceof IOException) {
                        throw (IOException)obj;
                    } else if (obj instanceof MalformedURLException) {
                        throw (MalformedURLException)obj;
                    } else if (obj instanceof ServiceUnavailableException) {
                        throw (ServiceUnavailableException)obj;
                    }
                    return (byte[])obj;

                } catch (JSONException e) {
                    throw new RuntimeException("Malformed data passed to test mock", e);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Could not write message to reporting queue for tests.", e);
                }
            }
        };

        final MPConfig config = new MPConfig(new Bundle(), getContext()) {
            @Override
            public String getDecideEndpoint() {
                return "DECIDE ENDPOINT";
            }

            @Override
            public String getDecideFallbackEndpoint() {
                return "DECIDE FALLBACK";
            }

            @Override
            public String getEventsEndpoint() {
                return "EVENTS ENDPOINT";
            }

            @Override
            public String getEventsFallbackEndpoint() {
                return "EVENTS FALLBACK";
            }

            @Override
            public boolean getDisableFallback() {
                return mDisableFallback;
            }
        };

        final MPDbAdapter mockAdapter = new MPDbAdapter(getContext()) {
            @Override
            public void cleanupEvents(String last_id, Table table) {
                mCleanupCalls.add("called");
                super.cleanupEvents(last_id, table);
            }

            @Override
            protected boolean belowMemThreshold() {
                if (mForceOverMemThreshold) {
                    return false;
                } else {
                    return super.belowMemThreshold();
                }
            }
        };

        final AnalyticsMessages listener = new AnalyticsMessages(getContext()) {
            @Override
            protected MPDbAdapter makeDbAdapter(Context context) {
                return mockAdapter;
            }

            @Override
            protected RemoteService getPoster() {
                return mockPoster;
            }

            @Override
            protected MPConfig getConfig(Context context) {
                return config;
            }
        };

        mMetrics = new TestUtils.CleanMixpanelAPI(getContext(), mMockPreferences, "Test Message Queuing") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return listener;
            }
        };

    }

    public void testHTTPFailures() {
        try {
            // Basic succeed on first, non-fallback url
            mCleanupCalls.clear();
            mFlushResults.add(TestUtils.bytes("1\n"));
            mMetrics.track("Should Succeed", null);
            mMetrics.flush();
            Thread.sleep(500);
            assertEquals("Should Succeed", mPerformRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(1, mCleanupCalls.size());

            // Fallback test--first URL throws IOException
            mCleanupCalls.clear();
            mFlushResults.add(new IOException());
            mFlushResults.add(TestUtils.bytes("1\n"));
            mMetrics.track("Should Succeed", null);
            mMetrics.flush();
            Thread.sleep(500);
            assertEquals("Should Succeed", mPerformRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals("Should Succeed", mPerformRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(1, mCleanupCalls.size());

            // Two IOExceptions -- assume temporary network failure, no cleanup should happen until
            // second flush
            mCleanupCalls.clear();
            mFlushResults.add(new IOException());
            mFlushResults.add(new IOException());
            mFlushResults.add(TestUtils.bytes("1\n"));
            mMetrics.track("Should Succeed", null);
            mMetrics.flush();
            Thread.sleep(500);
            assertEquals("Should Succeed", mPerformRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals("Should Succeed", mPerformRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(0, mCleanupCalls.size());
            mMetrics.flush();
            Thread.sleep(500);
            assertEquals("Should Succeed", mPerformRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(1, mCleanupCalls.size());

            // MalformedURLException -- should dump the events since this will probably never succeed
            mCleanupCalls.clear();
            mFlushResults.add(new MalformedURLException());
            mMetrics.track("Should Fail", null);
            mMetrics.flush();
            Thread.sleep(500);
            assertEquals("Should Fail", mPerformRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(1, mCleanupCalls.size());

            // 503 exception -- should wait for 10 seconds until the queue is able to flush
            mCleanupCalls.clear();
            mFlushResults.add(new RemoteService.ServiceUnavailableException("", "10"));
            mFlushResults.add(TestUtils.bytes("1\n"));
            mMetrics.track("Should Succeed", null);
            mMetrics.flush();
            Thread.sleep(500);
            assertEquals("Should Succeed", mPerformRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(0, mCleanupCalls.size());
            mMetrics.flush();
            Thread.sleep(500);
            assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(0, mCleanupCalls.size());
            Thread.sleep(10000);
            mMetrics.flush();
            assertEquals("Should Succeed", mPerformRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(1, mCleanupCalls.size());

            // short of memory test - should drop all the new queries
            mForceOverMemThreshold = true;
            mCleanupCalls.clear();
            mMetrics.track("Should Fail", null);
            mMetrics.flush();
            Thread.sleep(500);
            assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(0, mCleanupCalls.size());
            mForceOverMemThreshold = false;
            mMetrics.track("Should Succeed", null);
            mMetrics.flush();
            Thread.sleep(500);
            assertEquals("Should Succeed", mPerformRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(null, mPerformRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(1, mCleanupCalls.size());
        } catch (InterruptedException e) {
            throw new RuntimeException("Test was interrupted.");
        }
    }

    private Future<SharedPreferences> mMockPreferences;
    private List<Object> mFlushResults, mDecideResults;
    private BlockingQueue<String> mPerformRequestCalls, mDecideCalls;
    private List<String> mCleanupCalls;
    private MixpanelAPI mMetrics;
    private volatile boolean mDisableFallback;
    private volatile boolean mForceOverMemThreshold;
    private static final int POLL_WAIT_SECONDS = 5;
}
