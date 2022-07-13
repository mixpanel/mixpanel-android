package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.mixpanel.android.util.Base64Coder;
import com.mixpanel.android.util.HttpService;
import com.mixpanel.android.util.RemoteService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;

/**
 * Created by sergioalonso on 5/16/17.
 */

@RunWith(AndroidJUnit4.class)
@SmallTest
public class AutomaticEventsTest {

    private MixpanelAPI mCleanMixpanelAPI;
    private static final String TOKEN = "Automatic Events Token";
    private static final int MAX_TIMEOUT_POLL = 6500;
    final private BlockingQueue<String> mPerformRequestEvents = new LinkedBlockingQueue<>();
    private Future<SharedPreferences> mMockReferrerPreferences;
    private int mTrackedEvents;
    private CountDownLatch mLatch = new CountDownLatch(1);
    private MPDbAdapter mockAdapter;
    private CountDownLatch mMinRequestsLatch;

    @Before
    public void setUp() {
        mMockReferrerPreferences = new TestUtils.EmptyPreferences(InstrumentationRegistry.getInstrumentation().getContext());
        mTrackedEvents = 0;
        mMinRequestsLatch = new CountDownLatch(2); // First Time Open and Update
    }

    private void setUpInstance(boolean trackAutomaticEvents) {
        final RemoteService mockPoster = new HttpService() {
            @Override
            public byte[] performRequest(String endpointUrl, Map<String, Object> params, SSLSocketFactory socketFactory) {

                final String jsonData = Base64Coder.decodeString(params.get("data").toString());
                assertTrue(params.containsKey("data"));
                try {
                    JSONArray jsonArray = new JSONArray(jsonData);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        mPerformRequestEvents.put(jsonArray.getJSONObject(i).getString("event"));
                        mMinRequestsLatch.countDown();
                    }
                    return TestUtils.bytes("1\n");
                } catch (JSONException e) {
                    throw new RuntimeException("Malformed data passed to test mock", e);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Could not write message to reporting queue for tests.", e);
                }
            }
        };

        InstrumentationRegistry.getInstrumentation().getContext().deleteDatabase("mixpanel");
        mockAdapter = new MPDbAdapter(InstrumentationRegistry.getInstrumentation().getContext()) {
            @Override
            public void cleanupEvents(String last_id, Table table, String token, boolean includeAutomaticEvents) {
                if (token.equalsIgnoreCase(TOKEN)) {
                    super.cleanupEvents(last_id, table, token, includeAutomaticEvents);
                }
            }

            @Override
            public int addJSON(JSONObject j, String token, Table table, boolean isAutomaticRecord) {
                if (token.equalsIgnoreCase(TOKEN)) {
                    mTrackedEvents++;
                    mLatch.countDown();
                    return super.addJSON(j, token, table, isAutomaticRecord);
                }

                return 1;
            }
        };

        final AnalyticsMessages automaticAnalyticsMessages = new AnalyticsMessages(InstrumentationRegistry.getInstrumentation().getContext()) {

            @Override
            protected RemoteService getPoster() {
                return mockPoster;
            }

            @Override
            protected MPDbAdapter makeDbAdapter(Context context) {
                return mockAdapter;
            }

            @Override
            protected Worker createWorker() {
                return new Worker() {
                    @Override
                    protected Handler restartWorkerThread() {
                        final HandlerThread thread = new HandlerThread("com.mixpanel.android.AnalyticsWorker", Process.THREAD_PRIORITY_BACKGROUND);
                        thread.start();
                        final Handler ret = new AnalyticsMessageHandler(thread.getLooper()) {
                        };
                        return ret;
                    }
                };
            }
        };

        mCleanMixpanelAPI = new MixpanelAPI(InstrumentationRegistry.getInstrumentation().getContext(), mMockReferrerPreferences, TOKEN, false, null, trackAutomaticEvents) {

            @Override
                /* package */ PersistentIdentity getPersistentIdentity(final Context context, final Future<SharedPreferences> referrerPreferences, final String token, final String instanceName) {
                String instanceKey = instanceName != null ? instanceName : token;
                final String prefsName = "com.mixpanel.android.mpmetrics.MixpanelAPI_" + instanceKey;
                final SharedPreferences ret = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
                ret.edit().clear().commit();

                final String timeEventsPrefsName = "com.mixpanel.android.mpmetrics.MixpanelAPI.TimeEvents_" + instanceKey;
                final SharedPreferences timeSharedPrefs = context.getSharedPreferences(timeEventsPrefsName, Context.MODE_PRIVATE);
                timeSharedPrefs.edit().clear().commit();

                final String mixpanelPrefsName = "com.mixpanel.android.mpmetrics.Mixpanel";
                final SharedPreferences mpSharedPrefs = context.getSharedPreferences(mixpanelPrefsName, Context.MODE_PRIVATE);
                mpSharedPrefs.edit().clear().putInt("latest_version_code", -2).commit(); // -1 is the default value

                return super.getPersistentIdentity(context, referrerPreferences, token, instanceName);
            }

            @Override
            AnalyticsMessages getAnalyticsMessages() {
                return automaticAnalyticsMessages;
            }
        };
    }

    @After
    public void tearDown() throws Exception {
        mMinRequestsLatch.await(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testAutomaticOneInstance() throws InterruptedException {
        int calls = 3; // First Time Open, App Update, An Event One
        mLatch = new CountDownLatch(calls);
        setUpInstance(true);
        mCleanMixpanelAPI.track("An event One");
        mCleanMixpanelAPI.flush();
        assertTrue(mLatch.await(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertEquals(calls, mTrackedEvents);
        assertEquals(AutomaticEvents.FIRST_OPEN, mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertEquals(AutomaticEvents.APP_UPDATED, mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertEquals("An event One", mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertEquals(null, mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testDisableAutomaticEvents() throws InterruptedException {
        int calls = 3; // First Time Open, App Update, An Event Three
        setUpInstance(false);
        mLatch = new CountDownLatch(calls);
        mCleanMixpanelAPI.track("An Event Three");
        assertTrue(mLatch.await(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertEquals(calls, mTrackedEvents);

        mCleanMixpanelAPI.track("Automatic Event Two", null, true); // dropped
        mCleanMixpanelAPI.track("Automatic Event Three", null, true); // dropped
        mCleanMixpanelAPI.track("Automatic Event Four", null, true); // dropped
        mCleanMixpanelAPI.flush();
        assertEquals("An Event Three", mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertEquals(null, mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));

        String[] noEvents = mockAdapter.generateDataString(MPDbAdapter.Table.EVENTS, TOKEN, false);
        assertNull(noEvents);

        mCleanMixpanelAPI.flush();
        assertEquals(null, mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testAutomaticMultipleInstances() throws InterruptedException {
        final String SECOND_TOKEN = "Automatic Events Token Two";
        int initialCalls = 2;
        setUpInstance(false);
        mLatch = new CountDownLatch(initialCalls);
        final CountDownLatch secondLatch = new CountDownLatch(initialCalls);
        final BlockingQueue<String> secondPerformedRequests = new LinkedBlockingQueue<>();

        final HttpService mpSecondPoster = new HttpService() {
            @Override
            public byte[] performRequest(String endpointUrl, Map<String, Object> params, SSLSocketFactory socketFactory) {
                final String jsonData = Base64Coder.decodeString(params.get("data").toString());
                assertTrue(params.containsKey("data"));
                try {
                    JSONArray jsonArray = new JSONArray(jsonData);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        secondPerformedRequests.put(jsonArray.getJSONObject(i).getString("event"));
                    }
                    return TestUtils.bytes("1\n");
                } catch (JSONException e) {
                    throw new RuntimeException("Malformed data passed to test mock", e);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Could not write message to reporting queue for tests.", e);
                }
            }
        };

        final MPDbAdapter mpSecondDbAdapter = new MPDbAdapter(InstrumentationRegistry.getInstrumentation().getContext()) {
            @Override
            public void cleanupEvents(String last_id, Table table, String token, boolean includeAutomaticEvents) {
                if (token.equalsIgnoreCase(SECOND_TOKEN)) {
                    super.cleanupEvents(last_id, table, token, includeAutomaticEvents);
                }
            }

            @Override
            public int addJSON(JSONObject j, String token, Table table, boolean isAutomaticRecord) {
                if (token.equalsIgnoreCase(SECOND_TOKEN)) {
                    secondLatch.countDown();
                    return super.addJSON(j, token, table, isAutomaticRecord);
                }

                return 1;
            }
        };

        final AnalyticsMessages mpSecondAnalyticsMessages = new AnalyticsMessages(InstrumentationRegistry.getInstrumentation().getContext()) {
            @Override
            protected RemoteService getPoster() {
                return mpSecondPoster;
            }

            @Override
            protected MPDbAdapter makeDbAdapter(Context context) {
                return mpSecondDbAdapter;
            }

            @Override
            protected Worker createWorker() {
                return new Worker() {
                    @Override
                    protected Handler restartWorkerThread() {
                        final HandlerThread thread = new HandlerThread("com.mixpanel.android.AnalyticsWorker", Process.THREAD_PRIORITY_BACKGROUND);
                        thread.start();
                        final Handler ret = new AnalyticsMessageHandler(thread.getLooper()) {
                        };
                        return ret;
                    }
                };
            }
        };

        MixpanelAPI mpSecondInstance = new TestUtils.CleanMixpanelAPI(InstrumentationRegistry.getInstrumentation().getContext(), new TestUtils.EmptyPreferences(InstrumentationRegistry.getInstrumentation().getContext()), SECOND_TOKEN, true) {
            @Override
            AnalyticsMessages getAnalyticsMessages() {
                return mpSecondAnalyticsMessages;
            }
        };

        assertTrue(mLatch.await(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertEquals(initialCalls, mTrackedEvents);

        mLatch = new CountDownLatch(MPConfig.getInstance(InstrumentationRegistry.getInstrumentation().getContext()).getBulkUploadLimit() - initialCalls);
        for (int i = 0; i < MPConfig.getInstance(InstrumentationRegistry.getInstrumentation().getContext()).getBulkUploadLimit() - initialCalls; i++) {
            mCleanMixpanelAPI.track("Track event " + i);
        }
        assertTrue(mLatch.await(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));

        assertEquals(AutomaticEvents.FIRST_OPEN, mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertEquals(AutomaticEvents.APP_UPDATED, mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        for (int i = 0; i < MPConfig.getInstance(InstrumentationRegistry.getInstrumentation().getContext()).getBulkUploadLimit() - initialCalls; i++) {
            assertEquals("Track event " + i, mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        }

        assertNull(mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));

        assertNull(secondPerformedRequests.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));

        mpSecondInstance.flush();
        mCleanMixpanelAPI.track("First Instance Event One");
        mpSecondInstance.track("Second Instance Event One");
        mpSecondInstance.track("Second Instance Event Two");
        mpSecondInstance.flush();

        assertEquals("Second Instance Event One", secondPerformedRequests.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertEquals("Second Instance Event Two", secondPerformedRequests.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertNull(secondPerformedRequests.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));

        assertNull(mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
    }
}