package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class OptOutTest {

    private MixpanelAPI mMixpanelAPI;
    private static final String TOKEN = "Opt Out Test Token";
    final private BlockingQueue<String> mPerformRequestEvents = new LinkedBlockingQueue<>();
    final private BlockingQueue<String> mStoredEvents = new LinkedBlockingQueue<>();
    final private BlockingQueue<String> mStoredPeopleUpdates = new LinkedBlockingQueue<>();
    final private BlockingQueue<String> mStoredAnonymousPeopleUpdates = new LinkedBlockingQueue<>();
    private CountDownLatch mCleanUpCalls = new CountDownLatch(1);

    private MPDbAdapter mMockAdapter;
    private Future<SharedPreferences> mMockReferrerPreferences;
    private AnalyticsMessages mAnalyticsMessages;
    private PersistentIdentity mPersistentIdentity;
    private static final int MAX_TIMEOUT_POLL = 6500;

    @Before
    public void setUp() {
        mMockReferrerPreferences = new TestUtils.EmptyPreferences(InstrumentationRegistry.getInstrumentation().getContext());

        final RemoteService mockPoster = new HttpService() {
            @Override
            public byte[] performRequest(String endpointUrl, Map<String, Object> params, SSLSocketFactory socketFactory) {
                if (params != null) {
                    final String jsonData = Base64Coder.decodeString(params.get("data").toString());
                    assertTrue(params.containsKey("data"));

                    try {
                        JSONArray jsonArray = new JSONArray(jsonData);
                        for (int i = 0; i < jsonArray.length(); i++) {
                            mPerformRequestEvents.put(jsonArray.getJSONObject(i).toString());
                        }
                        return TestUtils.bytes("1\n");
                    } catch (JSONException e) {
                        throw new RuntimeException("Malformed data passed to test mock", e);
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Could not write message to reporting queue for tests.", e);
                    }

                }

                return TestUtils.bytes("{\"automatic_events\": false}");
            }
        };

        mMockAdapter = getMockDBAdapter();
        mAnalyticsMessages = new AnalyticsMessages(InstrumentationRegistry.getInstrumentation().getContext()) {
            @Override
            protected RemoteService getPoster() {
                return mockPoster;
            }

            @Override
            protected MPDbAdapter makeDbAdapter(Context context) {
                return mMockAdapter;
            }
        };
    }

    @After
    public void tearDown() throws Exception {
        if (mPersistentIdentity != null) {
            mPersistentIdentity.clearPreferences();
            mPersistentIdentity.removeOptOutFlag(TOKEN);
            mPersistentIdentity = null;
        }
        mMockAdapter.deleteDB();
    }

    /**
     * Init Mixpanel without tracking.
     * <p>
     * Make sure that after initialization no events are stored nor flushed.
     * Check that super properties, unidentified people updates or people distinct ID are
     * not stored in the device.
     *
     * @throws InterruptedException
     */
    @Test
    public void testOptOutDefaultFlag() throws InterruptedException {
        mCleanUpCalls = new CountDownLatch(2); // optOutTrack calls
        mMixpanelAPI = new MixpanelAPI(InstrumentationRegistry.getInstrumentation().getContext(), mMockReferrerPreferences, TOKEN, true, null, true) {
            @Override
            PersistentIdentity getPersistentIdentity(Context context, Future<SharedPreferences> referrerPreferences, String token, String instanceName) {
                mPersistentIdentity = super.getPersistentIdentity(context, referrerPreferences, token, instanceName);
                return mPersistentIdentity;
            }

            @Override
            AnalyticsMessages getAnalyticsMessages() {
                return mAnalyticsMessages;
            }
        };
        mMixpanelAPI.flush();
        assertEquals(null, mStoredEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertEquals(null, mStoredPeopleUpdates.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertEquals(null, mStoredAnonymousPeopleUpdates.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertNull(mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertEquals(0, mMixpanelAPI.getSuperProperties().length());
        assertNull(mMixpanelAPI.getPeople().getDistinctId());
        assertTrue(mCleanUpCalls.await(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
    }

    /**
     * Check that calls to optInTracking()/optOutTracking() updates hasOptedOutTracking()
     *
     * @throws InterruptedException
     */
    @Test
    public void testHasOptOutTrackingOrNot() throws InterruptedException {
        mCleanUpCalls = new CountDownLatch(4); // optOutTrack calls
        MixpanelAPI mixpanel = new MixpanelAPI(InstrumentationRegistry.getInstrumentation().getContext(), mMockReferrerPreferences, "TOKEN", true, null, true) {
            @Override
            PersistentIdentity getPersistentIdentity(Context context, Future<SharedPreferences> referrerPreferences, String token, String instanceName) {
                mPersistentIdentity = super.getPersistentIdentity(context, referrerPreferences, token, instanceName);
                return mPersistentIdentity;
            }

            @Override
            AnalyticsMessages getAnalyticsMessages() {
                return mAnalyticsMessages;
            }
        };
        
        mixpanel.optInTracking();
        assertFalse(mixpanel.hasOptedOutTracking());
        mixpanel.optOutTracking();
        assertTrue(mixpanel.hasOptedOutTracking());
    }

    /**
     * Test People updates when opt out/in:
     * 1. Not identified user: Updates stored in SharedPreferences should be removed after opting out
     * Following updates should be dropped.
     * 2. Identified user: Updates stored in DB should be removed after opting out and never sent
     * to Mixpanel. Following updates should be dropped as well.
     *
     * @throws InterruptedException
     */
    @Test
    public void testPeopleUpdates() throws InterruptedException, JSONException {
        mCleanUpCalls = new CountDownLatch(2);
        mMixpanelAPI = new MixpanelAPI(InstrumentationRegistry.getInstrumentation().getContext(), mMockReferrerPreferences, TOKEN,false, null, true) {
            @Override
            PersistentIdentity getPersistentIdentity(Context context, Future<SharedPreferences> referrerPreferences, String token, String instanceName) {
                mPersistentIdentity = super.getPersistentIdentity(context, referrerPreferences, token, instanceName);
                return mPersistentIdentity;
            }

            @Override
            AnalyticsMessages getAnalyticsMessages() {
                return mAnalyticsMessages;
            }
        };

        mMixpanelAPI.getPeople().set("optOutProperty", "optOutPropertyValue");
        assertEquals("optOutPropertyValue", new JSONObject(mStoredAnonymousPeopleUpdates.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS)).getJSONObject("$set").getString("optOutProperty"));
        assertEquals(0, mStoredAnonymousPeopleUpdates.size());

        mMixpanelAPI.optOutTracking();
        mMixpanelAPI.getPeople().set("optOutProperty", "optOutPropertyValue");
        mMixpanelAPI.getPeople().increment("optOutPropertyIncrement", 1);
        mMixpanelAPI.getPeople().append("optOutPropertyAppend", "append");
        mMixpanelAPI.getPeople().merge("optOutPropertyMerge", new JSONObject("{'key':'value'}"));
        mMixpanelAPI.getPeople().union("optOutPropertyUnion", new JSONArray("[{'key':'value'},{'key2':'value2'}]"));
        mMixpanelAPI.getPeople().unset("optOutPropertyUnset");
        mMixpanelAPI.getPeople().setOnce("optOutPropertySetOnce", "setOnceValue");
        assertEquals(true, mStoredAnonymousPeopleUpdates.isEmpty());
        assertTrue(mCleanUpCalls.await(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));

        mMixpanelAPI.optInTracking();
        mMixpanelAPI.identify("identity");
        mMixpanelAPI.getPeople().identify("identity");
        mMixpanelAPI.getPeople().set("optOutProperty", "optOutPropertyValue");
        mMixpanelAPI.getPeople().increment("optOutPropertyIncrement", 1);
        mMixpanelAPI.getPeople().append("optOutPropertyAppend", "append");
        mMixpanelAPI.getPeople().merge("optOutPropertyMerge", new JSONObject("{'key':'value'}"));
        mMixpanelAPI.getPeople().union("optOutPropertyUnion", new JSONArray("[{'key':'value'},{'key2':'value2'}]"));
        mMixpanelAPI.getPeople().unset("optOutPropertyUnset");
        mMixpanelAPI.getPeople().setOnce("optOutPropertySetOnce", "setOnceValue");
        for (int i = 0; i < 7; i++) {
            assertNotNull(mStoredPeopleUpdates.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        }
        assertEquals(0, mStoredPeopleUpdates.size());
        mMockAdapter = getMockDBAdapter();
        assertNotNull(mMockAdapter.generateDataString(MPDbAdapter.Table.PEOPLE, TOKEN, true));

        mCleanUpCalls = new CountDownLatch(2);
        mMixpanelAPI.optOutTracking();
        assertTrue(mCleanUpCalls.await(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        for (int i = 0; i < 2; i++) {
            String test = mStoredPeopleUpdates.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS);
            assertNotNull(test);
        }
        String[] data = mMockAdapter.generateDataString(MPDbAdapter.Table.PEOPLE, TOKEN, true);
        JSONArray pendingPeopleUpdatesArray = new JSONArray(data[1]);
        assertEquals(2, pendingPeopleUpdatesArray.length());
        assertTrue(pendingPeopleUpdatesArray.getJSONObject(0).has("$delete")); // deleteUser
        assertTrue(pendingPeopleUpdatesArray.getJSONObject(1).has("$unset")); // clearCharges
        mMixpanelAPI.getPeople().set("optOutProperty", "optOutPropertyValue");
        mMixpanelAPI.getPeople().increment("optOutPropertyIncrement", 1);
        mMixpanelAPI.getPeople().append("optOutPropertyAppend", "append");

        data = mMockAdapter.generateDataString(MPDbAdapter.Table.PEOPLE, TOKEN, true);
        pendingPeopleUpdatesArray = new JSONArray(data[1]);
        assertEquals(2, pendingPeopleUpdatesArray.length());

        forceFlush();
        for (int i = 0; i < 2; i++) {
            assertNotNull(mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        }
        assertNull(mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
    }

    /**
     * Test that events are dropped when a user opts out. After opting in, an event should be sent.
     *
     * @throws InterruptedException
     */
    @Test
    public void testDropEventsAndOptInEvent() throws InterruptedException {
        mMixpanelAPI = new TestUtils.CleanMixpanelAPI(InstrumentationRegistry.getInstrumentation().getContext(), mMockReferrerPreferences, TOKEN) {
            @Override
            PersistentIdentity getPersistentIdentity(Context context, Future<SharedPreferences> referrerPreferences, String token, String instanceName) {
                mPersistentIdentity = super.getPersistentIdentity(context, referrerPreferences, token, instanceName);
                return mPersistentIdentity;
            }

            @Override
            AnalyticsMessages getAnalyticsMessages() {
                return mAnalyticsMessages;
            }
        };

        for (int i = 0; i < 20; i++) {
            mMixpanelAPI.track("An Event");
        }
        for (int i = 0; i < 20; i++) {
            assertEquals("An Event", mStoredEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        }

        mCleanUpCalls = new CountDownLatch(2);
        mMixpanelAPI.optOutTracking();
        mMockAdapter = getMockDBAdapter();
        assertTrue(mCleanUpCalls.await(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertNull(mMockAdapter.generateDataString(MPDbAdapter.Table.EVENTS, TOKEN, true));

        mMixpanelAPI.optInTracking();
        assertEquals("$opt_in", mStoredEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        forceFlush();
        assertNotNull(mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertNull(mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
    }

    /**
     * Track calls before and after opting out
     */
    @Test
    public void testTrackCalls() throws InterruptedException, JSONException {
        mMixpanelAPI = new MixpanelAPI(InstrumentationRegistry.getInstrumentation().getContext(), mMockReferrerPreferences, TOKEN, false, null, true) {
            @Override
            PersistentIdentity getPersistentIdentity(Context context, Future<SharedPreferences> referrerPreferences, String token, String instanceName) {
                mPersistentIdentity = super.getPersistentIdentity(context, referrerPreferences, token, instanceName);
                return mPersistentIdentity;
            }

            @Override
            AnalyticsMessages getAnalyticsMessages() {
                return mAnalyticsMessages;
            }
        };

        mMixpanelAPI.timeEvent("Time Event");
        mMixpanelAPI.trackMap("Event with map", new HashMap<String, Object>());
        mMixpanelAPI.track("Event with properties", new JSONObject());
        assertEquals(1, mPersistentIdentity.getTimeEvents().size());

        mCleanUpCalls = new CountDownLatch(2);
        mMixpanelAPI.optOutTracking();
        assertTrue(mCleanUpCalls.await(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        mStoredEvents.clear();
        assertEquals(0, mPersistentIdentity.getTimeEvents().size());

        mMixpanelAPI.timeEvent("Time Event");
        assertEquals(0, mPersistentIdentity.getTimeEvents().size());
        mMixpanelAPI.track("Time Event");

        mMixpanelAPI.optInTracking();
        mMixpanelAPI.track("Time Event");
        mMixpanelAPI.timeEvent("Time Event");
        assertEquals(1, mPersistentIdentity.getTimeEvents().size());
        mMixpanelAPI.track("Time Event");

        mMockAdapter = getMockDBAdapter();
        assertEquals("$opt_in", mStoredEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertEquals("Time Event", mStoredEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertEquals("Time Event", mStoredEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertNull(mStoredEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));

        String[] data = mMockAdapter.generateDataString(MPDbAdapter.Table.EVENTS, TOKEN, true);
        JSONArray pendingEventsArray = new JSONArray(data[1]);
        assertEquals(3, pendingEventsArray.length());
        assertEquals("$opt_in", pendingEventsArray.getJSONObject(0).getString("event"));
        assertEquals("Time Event", pendingEventsArray.getJSONObject(1).getString("event"));
        assertEquals("Time Event", pendingEventsArray.getJSONObject(2).getString("event"));
        assertFalse(pendingEventsArray.getJSONObject(1).getJSONObject("properties").has("$duration"));
        assertTrue(pendingEventsArray.getJSONObject(2).getJSONObject("properties").has("$duration"));

        forceFlush();
        for (int i = 0; i < 3; i++) {
            assertNotNull(mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        }
        assertNull(mPerformRequestEvents.poll(MAX_TIMEOUT_POLL, TimeUnit.MILLISECONDS));
        assertEquals(0, mPersistentIdentity.getTimeEvents().size());
    }

    private void forceFlush() {
        mAnalyticsMessages.postToServer(new AnalyticsMessages.MixpanelDescription(TOKEN));
    }

    private MPDbAdapter getMockDBAdapter() {
        return new MPDbAdapter(InstrumentationRegistry.getInstrumentation().getContext()) {

            @Override
            public void cleanupAllEvents(Table table, String token) {
                if (token.equalsIgnoreCase(TOKEN)) {
                    mCleanUpCalls.countDown();
                    super.cleanupAllEvents(table, token);
                }
            }

            @Override
            public int addJSON(JSONObject j, String token, Table table, boolean isAutomaticRecord) {
                int result = 1;
                if (token.equalsIgnoreCase(TOKEN)) {
                    result = super.addJSON(j, token, table, isAutomaticRecord);
                    try {
                        if (!isAutomaticRecord && Table.EVENTS == table) {
                            mStoredEvents.put(j.getString("event"));
                        } else if (Table.PEOPLE == table) {
                            mStoredPeopleUpdates.put(j.toString());
                        } else if (Table.ANONYMOUS_PEOPLE == table) {
                            mStoredAnonymousPeopleUpdates.put(j.toString());
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Malformed data passed to test mock adapter", e);
                    }
                }

                return result;
            }
        };
    }
}
