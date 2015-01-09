package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.test.AndroidTestCase;

import com.mixpanel.android.util.Base64Coder;

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

public class HttpTest extends AndroidTestCase {

    public void setUp() {
        mMockPreferences = new TestUtils.EmptyPreferences(getContext());
    }

    public void testHTTPFailures() {
        final List<Object> flushResults = new ArrayList<Object>();
        final BlockingQueue<String> performRequestCalls = new LinkedBlockingQueue<String>();

        final ServerMessage mockPoster = new ServerMessage() {
            @Override
            public byte[] performRequest(String endpointUrl, List<NameValuePair> nameValuePairs) throws IOException {
                if (null == nameValuePairs) {
                    assertEquals("DECIDE ENDPOINT?version=1&lib=android&token=Test+Message+Queuing", endpointUrl);
                    return TestUtils.bytes("{}");
                }

                Object obj = flushResults.remove(0);
                try {
                    assertEquals(nameValuePairs.get(0).getName(), "data");
                    final String jsonData = Base64Coder.decodeString(nameValuePairs.get(0).getValue());
                    JSONArray msg = new JSONArray(jsonData);
                    JSONObject event = msg.getJSONObject(0);
                    performRequestCalls.put(event.getString("event"));

                    if (obj instanceof IOException) {
                        throw (IOException)obj;
                    } else if (obj instanceof MalformedURLException) {
                        throw (MalformedURLException)obj;
                    }
                } catch (JSONException e) {
                    throw new RuntimeException("Malformed data passed to test mock", e);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Could not write message to reporting queue for tests.", e);
                }
                return (byte[])obj;
            }
        };

        final MPConfig config = new MPConfig(new Bundle()) {
            public String getDecideEndpoint() {
                return "DECIDE ENDPOINT";
            }

            public String getEventsEndpoint() {
                return "EVENTS ENDPOINT";
            }

            public boolean getDisableFallback() {
                return false;
            }
        };

        final List<String> cleanupCalls = new ArrayList<String>();
        final MPDbAdapter mockAdapter = new MPDbAdapter(getContext()) {
            @Override
            public void cleanupEvents(String last_id, Table table) {
                cleanupCalls.add("called");
                super.cleanupEvents(last_id, table);
            }
        };

        final AnalyticsMessages listener = new AnalyticsMessages(getContext()) {
            @Override
            protected MPDbAdapter makeDbAdapter(Context context) {
                return mockAdapter;
            }

            @Override
            protected ServerMessage getPoster() {
                return mockPoster;
            }

            @Override
            protected MPConfig getConfig(Context context) {
                return config;
            }
        };

        MixpanelAPI metrics = new TestUtils.CleanMixpanelAPI(getContext(), mMockPreferences, "Test Message Queuing") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return listener;
            }
        };

        try {
            // Basic succeed on first, non-fallback url
            cleanupCalls.clear();
            flushResults.add(TestUtils.bytes("1\n"));
            metrics.track("Should Succeed", null);
            metrics.flush();
            Thread.sleep(500);
            assertEquals("Should Succeed", performRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(null, performRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(1, cleanupCalls.size());

            // Fallback test--first URL throws IOException
            cleanupCalls.clear();
            flushResults.add(new IOException());
            flushResults.add(TestUtils.bytes("1\n"));
            metrics.track("Should Succeed", null);
            metrics.flush();
            Thread.sleep(500);
            assertEquals("Should Succeed", performRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals("Should Succeed", performRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(1, cleanupCalls.size());

            // Two IOExceptions -- assume temporary network failure, no cleanup should happen until
            // second flush
            cleanupCalls.clear();
            flushResults.add(new IOException());
            flushResults.add(new IOException());
            flushResults.add(TestUtils.bytes("1\n"));
            metrics.track("Should Succeed", null);
            metrics.flush();
            Thread.sleep(500);
            assertEquals("Should Succeed", performRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals("Should Succeed", performRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(0, cleanupCalls.size());
            metrics.flush();
            Thread.sleep(500);
            assertEquals("Should Succeed", performRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(null, performRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(1, cleanupCalls.size());

            // MalformedURLException -- should dump the events since this will probably never succeed
            cleanupCalls.clear();
            flushResults.add(new MalformedURLException());
            metrics.track("Should Fail", null);
            metrics.flush();
            Thread.sleep(500);
            assertEquals("Should Fail", performRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(null, performRequestCalls.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(1, cleanupCalls.size());
        } catch (InterruptedException e) {
            throw new RuntimeException("Test was interrupted.");
        }
    }

    private Future<SharedPreferences> mMockPreferences;
    private static final int POLL_WAIT_SECONDS = 5;
}
