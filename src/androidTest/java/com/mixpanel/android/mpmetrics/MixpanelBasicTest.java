package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcel;
import android.test.AndroidTestCase;
import android.test.mock.MockContext;
import android.test.mock.MockPackageManager;

import com.mixpanel.android.util.Base64Coder;
import com.mixpanel.android.util.RemoteService;
import com.mixpanel.android.util.HttpService;

import org.apache.http.NameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;

public class MixpanelBasicTest extends AndroidTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockPreferences = new TestUtils.EmptyPreferences(getContext());
        AnalyticsMessages messages = AnalyticsMessages.getInstance(getContext());
        messages.hardKill();
        Thread.sleep(500);
    } // end of setUp() method definition

    public void testVersionsMatch() {
        assertEquals(BuildConfig.MIXPANEL_VERSION, MPConfig.VERSION);
    }

    public void testGeneratedDistinctId() {
        String fakeToken = UUID.randomUUID().toString();
        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(getContext(), mMockPreferences, fakeToken);
        String generatedId1 = mixpanel.getDistinctId();
        assertTrue(generatedId1 != null);

        mixpanel.reset();
        String generatedId2 = mixpanel.getDistinctId();
        assertTrue(generatedId2 != null);
        assertTrue(generatedId1 != generatedId2);
    }

    public void testDeleteDB() {
        Map<String, String> beforeMap = new HashMap<String, String>();
        beforeMap.put("added", "before");
        JSONObject before = new JSONObject(beforeMap);

        Map<String, String> afterMap = new HashMap<String,String>();
        afterMap.put("added", "after");
        JSONObject after = new JSONObject(afterMap);

        MPDbAdapter adapter = new MPDbAdapter(getContext(), "DeleteTestDB");
        adapter.addJSON(before, MPDbAdapter.Table.EVENTS);
        adapter.addJSON(before, MPDbAdapter.Table.PEOPLE);
        adapter.deleteDB();

        String[] emptyEventsData = adapter.generateDataString(MPDbAdapter.Table.EVENTS);
        assertEquals(emptyEventsData, null);
        String[] emptyPeopleData = adapter.generateDataString(MPDbAdapter.Table.PEOPLE);
        assertEquals(emptyPeopleData, null);

        adapter.addJSON(after, MPDbAdapter.Table.EVENTS);
        adapter.addJSON(after, MPDbAdapter.Table.PEOPLE);

        try {
            String[] someEventsData = adapter.generateDataString(MPDbAdapter.Table.EVENTS);
            JSONArray someEvents = new JSONArray(someEventsData[1]);
            assertEquals(someEvents.length(), 1);
            assertEquals(someEvents.getJSONObject(0).get("added"), "after");

            String[] somePeopleData = adapter.generateDataString(MPDbAdapter.Table.PEOPLE);
            JSONArray somePeople = new JSONArray(somePeopleData[1]);
            assertEquals(somePeople.length(), 1);
            assertEquals(somePeople.getJSONObject(0).get("added"), "after");
        } catch (JSONException e) {
            fail("Unexpected JSON or lack thereof in MPDbAdapter test");
        }
    }

    public void testLooperDestruction() {

        final BlockingQueue<JSONObject> messages = new LinkedBlockingQueue<JSONObject>();

        final MPDbAdapter explodingDb = new MPDbAdapter(getContext()) {
            @Override
            public int addJSON(JSONObject message, MPDbAdapter.Table table) {
                messages.add(message);
                throw new RuntimeException("BANG!");
            }
        };

        final AnalyticsMessages explodingMessages = new AnalyticsMessages(getContext()) {
            // This will throw inside of our worker thread.
            @Override
            public MPDbAdapter makeDbAdapter(Context context) {
                return explodingDb;
            }
        };
        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(getContext(), mMockPreferences, "TEST TOKEN testLooperDisaster") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return explodingMessages;
            }
        };

        try {
            mixpanel.reset();
            assertFalse(explodingMessages.isDead());

            mixpanel.track("event1", null);
            JSONObject found = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            assertNotNull(found);
            Thread.sleep(1000);
            assertTrue(explodingMessages.isDead());

            mixpanel.track("event2", null);
            JSONObject shouldntFind = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            assertNull(shouldntFind);
            assertTrue(explodingMessages.isDead());
        } catch (InterruptedException e) {
            fail("Unexpected interruption");
        }
    }

    public void testEventOperations() throws JSONException {
        final BlockingQueue<JSONObject> messages = new LinkedBlockingQueue<JSONObject>();

        final MPDbAdapter eventOperationsAdapter = new MPDbAdapter(getContext()) {
            @Override
            public int addJSON(JSONObject message, MPDbAdapter.Table table) {
                messages.add(message);
                return 1;
            }
        };

        final AnalyticsMessages eventOperationsMessages = new AnalyticsMessages(getContext()) {
            // This will throw inside of our worker thread.
            @Override
            public MPDbAdapter makeDbAdapter(Context context) {
                return eventOperationsAdapter;
            }
        };

        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(getContext(), mMockPreferences, "Test event operations") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return eventOperationsMessages;
            }
        };

        JSONObject jsonObj1 = new JSONObject();
        JSONObject jsonObj2 = new JSONObject();
        JSONObject jsonObj3 = new JSONObject();
        JSONObject jsonObj4 = new JSONObject();
        Map<String, Object> mapObj1 = new HashMap<>();
        Map<String, Object> mapObj2 = new HashMap<>();
        Map<String, Object> mapObj3 = new HashMap<>();
        Map<String, Object> mapObj4 = new HashMap<>();

        jsonObj1.put("TRACK JSON STRING", "TRACK JSON STRING VALUE");
        jsonObj2.put("TRACK JSON INT", 1);
        jsonObj3.put("TRACK JSON STRING ONCE", "TRACK JSON STRING ONCE VALUE");
        jsonObj4.put("TRACK JSON STRING ONCE", "SHOULD NOT SEE ME");

        mapObj1.put("TRACK MAP STRING", "TRACK MAP STRING VALUE");
        mapObj2.put("TRACK MAP INT", 1);
        mapObj3.put("TRACK MAP STRING ONCE", "TRACK MAP STRING ONCE VALUE");
        mapObj4.put("TRACK MAP STRING ONCE", "SHOULD NOT SEE ME");

        try {
            JSONObject message;
            JSONObject properties;

            mixpanel.track("event1", null);
            message = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals("event1", message.getString("event"));

            mixpanel.track("event2", jsonObj1);
            message = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals("event2", message.getString("event"));
            properties = message.getJSONObject("properties");
            assertEquals(jsonObj1.getString("TRACK JSON STRING"), properties.getString("TRACK JSON STRING"));

            mixpanel.trackMap("event3", null);
            message = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals("event3", message.getString("event"));

            mixpanel.trackMap("event4", mapObj1);
            message = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals("event4", message.getString("event"));
            properties = message.getJSONObject("properties");
            assertEquals(mapObj1.get("TRACK MAP STRING"), properties.getString("TRACK MAP STRING"));

            mixpanel.registerSuperProperties(jsonObj2);
            mixpanel.registerSuperPropertiesOnce(jsonObj3);
            mixpanel.registerSuperPropertiesOnce(jsonObj4);
            mixpanel.registerSuperPropertiesMap(mapObj2);
            mixpanel.registerSuperPropertiesOnceMap(mapObj3);
            mixpanel.registerSuperPropertiesOnceMap(mapObj4);

            mixpanel.track("event5", null);
            message = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals("event5", message.getString("event"));
            properties = message.getJSONObject("properties");
            assertEquals(jsonObj2.getInt("TRACK JSON INT"), properties.getInt("TRACK JSON INT"));
            assertEquals(jsonObj3.getString("TRACK JSON STRING ONCE"), properties.getString("TRACK JSON STRING ONCE"));
            assertEquals(mapObj2.get("TRACK MAP INT"), properties.getInt("TRACK MAP INT"));
            assertEquals(mapObj3.get("TRACK MAP STRING ONCE"), properties.getString("TRACK MAP STRING ONCE"));

            mixpanel.unregisterSuperProperty("TRACK JSON INT");
            mixpanel.track("event6", null);
            message = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals("event6", message.getString("event"));
            properties = message.getJSONObject("properties");
            assertFalse(properties.has("TRACK JSON INT"));

            mixpanel.clearSuperProperties();
            mixpanel.track("event7", null);
            message = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals("event7", message.getString("event"));
            properties = message.getJSONObject("properties");
            assertFalse(properties.has("TRACK JSON STRING ONCE"));
        } catch (InterruptedException e) {
            fail("Unexpected interruption");
        }
    }

    public void testPeopleOperations() throws JSONException {
        final List<JSONObject> messages = new ArrayList<JSONObject>();

        final AnalyticsMessages listener = new AnalyticsMessages(getContext()) {
            @Override
            public void peopleMessage(JSONObject heard) {
                messages.add(heard);
            }
        };

        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(getContext(), mMockPreferences, "TEST TOKEN testIdentifyAfterSet") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return listener;
            }
        };

        Map<String, Object> mapObj1 = new HashMap<>();
        mapObj1.put("SET MAP INT", 1);
        Map<String, Object> mapObj2 = new HashMap<>();
        mapObj2.put("SET ONCE MAP STR", "SET ONCE MAP VALUE");

        mixpanel.getPeople().identify("TEST IDENTITY");

        mixpanel.getPeople().set("SET NAME", "SET VALUE");
        mixpanel.getPeople().setMap(mapObj1);
        mixpanel.getPeople().increment("INCREMENT NAME", 1);
        mixpanel.getPeople().append("APPEND NAME", "APPEND VALUE");
        mixpanel.getPeople().setOnce("SET ONCE NAME", "SET ONCE VALUE");
        mixpanel.getPeople().setOnceMap(mapObj2);
        mixpanel.getPeople().union("UNION NAME", new JSONArray("[100]"));
        mixpanel.getPeople().unset("UNSET NAME");
        mixpanel.getPeople().trackCharge(100, new JSONObject("{\"name\": \"val\"}"));
        mixpanel.getPeople().clearCharges();
        mixpanel.getPeople().deleteUser();

        JSONObject setMessage = messages.get(0).getJSONObject("$set");
        assertEquals("SET VALUE", setMessage.getString("SET NAME"));

        JSONObject setMapMessage = messages.get(1).getJSONObject("$set");
        assertEquals(mapObj1.get("SET MAP INT"), setMapMessage.getInt("SET MAP INT"));

        JSONObject addMessage = messages.get(2).getJSONObject("$add");
        assertEquals(1, addMessage.getInt("INCREMENT NAME"));

        JSONObject appendMessage = messages.get(3).getJSONObject("$append");
        assertEquals("APPEND VALUE", appendMessage.get("APPEND NAME"));

        JSONObject setOnceMessage = messages.get(4).getJSONObject("$set_once");
        assertEquals("SET ONCE VALUE", setOnceMessage.getString("SET ONCE NAME"));

        JSONObject setOnceMapMessage = messages.get(5).getJSONObject("$set_once");
        assertEquals(mapObj2.get("SET ONCE MAP STR"), setOnceMapMessage.getString("SET ONCE MAP STR"));

        JSONObject unionMessage = messages.get(6).getJSONObject("$union");
        JSONArray unionValues = unionMessage.getJSONArray("UNION NAME");
        assertEquals(1, unionValues.length());
        assertEquals(100, unionValues.getInt(0));

        JSONArray unsetMessage = messages.get(7).getJSONArray("$unset");
        assertEquals(1, unsetMessage.length());
        assertEquals("UNSET NAME", unsetMessage.get(0));

        JSONObject trackChargeMessage = messages.get(8).getJSONObject("$append");
        JSONObject transaction = trackChargeMessage.getJSONObject("$transactions");
        assertEquals(100.0d, transaction.getDouble("$amount"));

        JSONArray clearChargesMessage = messages.get(9).getJSONArray("$unset");
        assertEquals(1, clearChargesMessage.length());
        assertEquals("$transactions", clearChargesMessage.getString(0));

        assertTrue(messages.get(10).has("$delete"));
    }

    public void testIdentifyAfterSet() {
        final List<JSONObject> messages = new ArrayList<JSONObject>();

        final AnalyticsMessages listener = new AnalyticsMessages(getContext()) {
            @Override
            public void peopleMessage(JSONObject heard) {
                messages.add(heard);
            }
        };

        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(getContext(), mMockPreferences, "TEST TOKEN testIdentifyAfterSet") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return listener;
            }
        };

        MixpanelAPI.People people = mixpanel.getPeople();
        people.increment("the prop", 0L);
        people.append("the prop", 1);
        people.set("the prop", 2);
        people.increment("the prop", 3L);
        people.increment("the prop", 4);
        people.append("the prop", 5);
        people.append("the prop", 6);
        people.identify("Personal Identity");

        assertEquals(messages.size(), 7);
        try {
            for (JSONObject message: messages) {
                String distinctId = message.getString("$distinct_id");
                assertEquals(distinctId, "Personal Identity");
            }

            assertTrue(messages.get(0).has("$add"));
            assertTrue(messages.get(1).has("$append"));
            assertTrue(messages.get(2).has("$set"));
            assertTrue(messages.get(3).has("$add"));
            assertTrue(messages.get(4).has("$add"));
        } catch (JSONException e) {
            fail("Unexpected JSON error in stored messages.");
        }
    }

    public void testIdentifyAndGetDistinctId() {
        MixpanelAPI metrics = new TestUtils.CleanMixpanelAPI(getContext(), mMockPreferences, "Identify Test Token");

        String generatedId = metrics.getDistinctId();
        assertNotNull(generatedId);

        String emptyId = metrics.getPeople().getDistinctId();
        assertNull(emptyId);

        metrics.identify("Events Id");
        String setId = metrics.getDistinctId();
        assertEquals("Events Id", setId);

        String stillEmpty = metrics.getPeople().getDistinctId();
        assertNull(stillEmpty);

        metrics.getPeople().identify("People Id");
        String unchangedId = metrics.getDistinctId();
        assertEquals("Events Id", unchangedId);

        String setPeopleId = metrics.getPeople().getDistinctId();
        assertEquals("People Id", setPeopleId);
    }

    public void testMessageQueuing() {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<String>();
        final SynchronizedReference<Boolean> isIdentifiedRef = new SynchronizedReference<Boolean>();
        isIdentifiedRef.set(false);

        final MPDbAdapter mockAdapter = new MPDbAdapter(getContext()) {
            @Override
            public int addJSON(JSONObject message, MPDbAdapter.Table table) {
                try {
                    messages.put("TABLE " + table.getName());
                    messages.put(message.toString());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                return super.addJSON(message, table);
            }
        };
        mockAdapter.cleanupEvents(Long.MAX_VALUE, MPDbAdapter.Table.EVENTS);
        mockAdapter.cleanupEvents(Long.MAX_VALUE, MPDbAdapter.Table.PEOPLE);

        final RemoteService mockPoster = new HttpService() {
            @Override
            public byte[] performRequest(String endpointUrl, List<NameValuePair> nameValuePairs, SSLSocketFactory socketFactory) {
                final boolean isIdentified = isIdentifiedRef.get();
                if (null == nameValuePairs) {
                    if (isIdentified) {
                        assertEquals("DECIDE_ENDPOINT?version=1&lib=android&token=Test+Message+Queuing&distinct_id=PEOPLE+ID", endpointUrl);
                    } else {
                        assertEquals("DECIDE_ENDPOINT?version=1&lib=android&token=Test+Message+Queuing&distinct_id=EVENTS+ID", endpointUrl);
                    }
                    return TestUtils.bytes("{}");
                }

                assertEquals(nameValuePairs.get(0).getName(), "data");
                final String decoded = Base64Coder.decodeString(nameValuePairs.get(0).getValue());

                try {
                    messages.put("SENT FLUSH " + endpointUrl);
                    messages.put(decoded);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                return TestUtils.bytes("1\n");
            }
        };


        final MPConfig mockConfig = new MPConfig(new Bundle(), getContext()) {
            @Override
            public int getFlushInterval() {
                return -1;
            }

            @Override
            public int getBulkUploadLimit() {
                return 40;
            }

            @Override
            public String getEventsEndpoint() {
                return "EVENTS_ENDPOINT";
            }

            @Override
            public String getPeopleEndpoint() {
                return "PEOPLE_ENDPOINT";
            }

            @Override
            public String getDecideEndpoint() {
                return "DECIDE_ENDPOINT";
            }

            @Override
            public boolean getDisableAppOpenEvent() { return true; }
        };

        final AnalyticsMessages listener = new AnalyticsMessages(getContext()) {
            @Override
            protected MPDbAdapter makeDbAdapter(Context context) {
                return mockAdapter;
            }

            @Override
            protected MPConfig getConfig(Context context) {
                return mockConfig;
            }

            @Override
            protected RemoteService getPoster() {
                return mockPoster;
            }
        };

        MixpanelAPI metrics = new TestUtils.CleanMixpanelAPI(getContext(), mMockPreferences, "Test Message Queuing") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                 return listener;
            }
        };

        metrics.identify("EVENTS ID");

        // Test filling up the message queue
        for (int i=0; i < mockConfig.getBulkUploadLimit() - 1; i++) {
            metrics.track("frequent event", null);
        }

        metrics.track("final event", null);
        String expectedJSONMessage = "<No message actually received>";

        try {
            for (int i=0; i < mockConfig.getBulkUploadLimit() - 1; i++) {
                String messageTable = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
                assertEquals("TABLE " + MPDbAdapter.Table.EVENTS.getName(), messageTable);

                expectedJSONMessage = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
                JSONObject message = new JSONObject(expectedJSONMessage);
                assertEquals("frequent event", message.getString("event"));
            }

            String messageTable = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals("TABLE " + MPDbAdapter.Table.EVENTS.getName(), messageTable);

            expectedJSONMessage = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            JSONObject message = new JSONObject(expectedJSONMessage);
            assertEquals("final event", message.getString("event"));

            String messageFlush = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals("SENT FLUSH EVENTS_ENDPOINT", messageFlush);

            expectedJSONMessage = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            JSONArray bigFlush = new JSONArray(expectedJSONMessage);
            assertEquals(mockConfig.getBulkUploadLimit(), bigFlush.length());

            metrics.track("next wave", null);
            metrics.flush();

            String nextWaveTable = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals("TABLE " + MPDbAdapter.Table.EVENTS.getName(), nextWaveTable);

            expectedJSONMessage = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            JSONObject nextWaveMessage = new JSONObject(expectedJSONMessage);
            assertEquals("next wave", nextWaveMessage.getString("event"));

            String manualFlush = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals("SENT FLUSH EVENTS_ENDPOINT", manualFlush);

            expectedJSONMessage = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            JSONArray nextWave = new JSONArray(expectedJSONMessage);
            assertEquals(1, nextWave.length());

            JSONObject nextWaveEvent = nextWave.getJSONObject(0);
            assertEquals("next wave", nextWaveEvent.getString("event"));

            isIdentifiedRef.set(true);
            metrics.getPeople().identify("PEOPLE ID");
            metrics.getPeople().set("prop", "yup");
            metrics.flush();

            String peopleTable = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals("TABLE " + MPDbAdapter.Table.PEOPLE.getName(), peopleTable);

            expectedJSONMessage = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            JSONObject peopleMessage = new JSONObject(expectedJSONMessage);

            assertEquals("PEOPLE ID", peopleMessage.getString("$distinct_id"));
            assertEquals("yup", peopleMessage.getJSONObject("$set").getString("prop"));

            String peopleFlush = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals("SENT FLUSH PEOPLE_ENDPOINT", peopleFlush);

            expectedJSONMessage = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
            JSONArray peopleSent = new JSONArray(expectedJSONMessage);
            assertEquals(1, peopleSent.length());

        } catch (InterruptedException e) {
            fail("Expected a log message about mixpanel communication but did not receive it.");
        } catch (JSONException e) {
            fail("Expected a JSON object message and got something silly instead: " + expectedJSONMessage);
        }
    }

    public void testTrackCharge() {
        final List<JSONObject> messages = new ArrayList<JSONObject>();
        final AnalyticsMessages listener = new AnalyticsMessages(getContext()) {
            @Override
            public void eventsMessage(EventDescription heard) {
                throw new RuntimeException("Should not be called during this test");
            }

            @Override
            public void peopleMessage(JSONObject heard) {
                messages.add(heard);
            }
        };

        class ListeningAPI extends TestUtils.CleanMixpanelAPI {
            public ListeningAPI(Context c, Future<SharedPreferences> referrerPrefs, String token) {
                super(c, referrerPrefs, token);
            }

            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                 return listener;
            }
        }

        MixpanelAPI api = new ListeningAPI(getContext(), mMockPreferences, "TRACKCHARGE TEST TOKEN");
        api.getPeople().identify("TRACKCHARGE PERSON");

        JSONObject props;
        try {
            props = new JSONObject("{'$time':'Should override', 'Orange':'Banana'}");
        } catch (JSONException e) {
            throw new RuntimeException("Can't construct fixture for trackCharge test");
        }

        api.getPeople().trackCharge(2.13, props);
        assertEquals(messages.size(), 1);

        JSONObject message = messages.get(0);

        try {
            JSONObject append = message.getJSONObject("$append");
            JSONObject newTransaction = append.getJSONObject("$transactions");
            assertEquals(newTransaction.optString("Orange"), "Banana");
            assertEquals(newTransaction.optString("$time"), "Should override");
            assertEquals(newTransaction.optDouble("$amount"), 2.13);
        } catch (JSONException e) {
            fail("Transaction message had unexpected layout:\n" + message.toString());
        }
    }

    public void testPersistence() {
        MixpanelAPI metricsOne = new MixpanelAPI(getContext(), mMockPreferences, "SAME TOKEN");
        metricsOne.reset();

        JSONObject props;
        try {
            props = new JSONObject("{ 'a' : 'value of a', 'b' : 'value of b' }");
        } catch (JSONException e) {
            throw new RuntimeException("Can't construct fixture for super properties test.");
        }

        metricsOne.clearSuperProperties();
        metricsOne.registerSuperProperties(props);
        metricsOne.identify("Expected Events Identity");
        metricsOne.getPeople().identify("Expected People Identity");

        // We exploit the fact that any metrics object with the same token
        // will get their values from the same persistent store.

        final List<Object> messages = new ArrayList<Object>();
        final AnalyticsMessages listener = new AnalyticsMessages(getContext()) {
            @Override
            public void eventsMessage(EventDescription heard) {
                messages.add(heard);
            }

            @Override
            public void peopleMessage(JSONObject heard) {
                messages.add(heard);
            }
        };

        class ListeningAPI extends MixpanelAPI {
            public ListeningAPI(Context c, Future<SharedPreferences> prefs, String token) {
                super(c, prefs, token);
            }

            @Override
            /* package */ boolean sendAppOpen() {
                return false;
            }

            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                 return listener;
            }
        }

        MixpanelAPI differentToken = new ListeningAPI(getContext(), mMockPreferences, "DIFFERENT TOKEN");

        differentToken.track("other event", null);
        differentToken.getPeople().set("other people prop", "Word"); // should be queued up.

        assertEquals(1, messages.size());

        AnalyticsMessages.EventDescription eventMessage = (AnalyticsMessages.EventDescription) messages.get(0);

        try {
            JSONObject eventProps = eventMessage.getProperties();
            String sentId = eventProps.getString("distinct_id");
            String sentA = eventProps.optString("a");
            String sentB = eventProps.optString("b");

            assertFalse("Expected Events Identity".equals(sentId));
            assertEquals("", sentA);
            assertEquals("", sentB);
        } catch (JSONException e) {
            fail("Event message has an unexpected shape " + e);
        }

        messages.clear();

        MixpanelAPI metricsTwo = new ListeningAPI(getContext(), mMockPreferences, "SAME TOKEN");

        metricsTwo.track("eventname", null);
        metricsTwo.getPeople().set("people prop name", "Indeed");

        assertEquals(2, messages.size());

        eventMessage = (AnalyticsMessages.EventDescription) messages.get(0);
        JSONObject peopleMessage = (JSONObject) messages.get(1);

        try {
            JSONObject eventProps = eventMessage.getProperties();
            String sentId = eventProps.getString("distinct_id");
            String sentA = eventProps.getString("a");
            String sentB = eventProps.getString("b");

            assertEquals("Expected Events Identity", sentId);
            assertEquals("value of a", sentA);
            assertEquals("value of b", sentB);
        } catch (JSONException e) {
            fail("Event message has an unexpected shape " + e);
        }

        try {
            String sentId = peopleMessage.getString("$distinct_id");
            assertEquals("Expected People Identity", sentId);
        } catch (JSONException e) {
            fail("Event message has an unexpected shape: " + peopleMessage.toString());
        }
    }

    public void testTrackInThread() throws InterruptedException, JSONException {
        class TestThread extends Thread {
            BlockingQueue<JSONObject> mMessages;

            public TestThread(BlockingQueue<JSONObject> messages) {
                this.mMessages = messages;
            }

            @Override
            public void run() {

                final MPDbAdapter dbMock = new MPDbAdapter(getContext()) {
                    @Override
                    public int addJSON(JSONObject message, MPDbAdapter.Table table) {
                        mMessages.add(message);
                        return 1;
                    }
                };

                final AnalyticsMessages analyticsMessages = new AnalyticsMessages(getContext()) {
                    @Override
                    public MPDbAdapter makeDbAdapter(Context context) {
                        return dbMock;
                    }
                };

                MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(getContext(), mMockPreferences, "TEST TOKEN") {
                    @Override
                    protected AnalyticsMessages getAnalyticsMessages() {
                        return analyticsMessages;
                    }
                };
                mixpanel.reset();
                mixpanel.track("test in thread", new JSONObject());
            }
        }

        //////////////////////////////

        final BlockingQueue<JSONObject> messages = new LinkedBlockingQueue<JSONObject>();
        TestThread testThread = new TestThread(messages);
        testThread.start();
        JSONObject found = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(found);
        assertEquals(found.getString("event"), "test in thread");
        assertTrue(found.getJSONObject("properties").has("$bluetooth_version"));
    }

    public void testConfiguration() {
        final ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.metaData = new Bundle();
        appInfo.metaData.putInt("com.mixpanel.android.MPConfig.BulkUploadLimit", 1);
        appInfo.metaData.putInt("com.mixpanel.android.MPConfig.FlushInterval", 2);
        appInfo.metaData.putInt("com.mixpanel.android.MPConfig.DataExpiration", 3);
        appInfo.metaData.putBoolean("com.mixpanel.android.MPConfig.DisableFallback", true);
        appInfo.metaData.putBoolean("com.mixpanel.android.MPConfig.AutoShowMixpanelUpdates", false);
        appInfo.metaData.putBoolean("com.mixpanel.android.MPConfig.DisableGestureBindingUI", true);
        appInfo.metaData.putBoolean("com.mixpanel.android.MPConfig.DisableEmulatorBindingUI", true);
        appInfo.metaData.putBoolean("com.mixpanel.android.MPConfig.DisableAppOpenEvent", true);

        appInfo.metaData.putString("com.mixpanel.android.MPConfig.EventsEndpoint", "EVENTS ENDPOINT");
        appInfo.metaData.putString("com.mixpanel.android.MPConfig.EventsFallbackEndpoint", "EVENTS FALLBACK ENDPOINT");
        appInfo.metaData.putString("com.mixpanel.android.MPConfig.PeopleEndpoint", "PEOPLE ENDPOINT");
        appInfo.metaData.putString("com.mixpanel.android.MPConfig.PeopleFallbackEndpoint", "PEOPLE FALLBACK ENDPOINT");
        appInfo.metaData.putString("com.mixpanel.android.MPConfig.DecideEndpoint", "DECIDE ENDPOINT");
        appInfo.metaData.putString("com.mixpanel.android.MPConfig.DecideFallbackEndpoint", "DECIDE FALLBACK ENDPOINT");

        final PackageManager packageManager = new MockPackageManager() {
            @Override
            public ApplicationInfo getApplicationInfo(String packageName, int flags) {
                assertEquals(packageName, "TEST PACKAGE NAME");
                assertTrue((flags & PackageManager.GET_META_DATA) == PackageManager.GET_META_DATA);
                return appInfo;
            }
        };

        final Context context = new MockContext() {
            @Override
            public String getPackageName() {
                return "TEST PACKAGE NAME";
            }

            @Override
            public PackageManager getPackageManager() {
                return packageManager;
            }
        };

        final MPConfig testConfig = MPConfig.readConfig(context);
        assertEquals(1, testConfig.getBulkUploadLimit());
        assertEquals(2, testConfig.getFlushInterval());
        assertEquals(3, testConfig.getDataExpiration());
        assertEquals(true, testConfig.getDisableFallback());
        assertEquals(true, testConfig.getDisableEmulatorBindingUI());
        assertEquals(true, testConfig.getDisableGestureBindingUI());
        assertEquals(true, testConfig.getDisableAppOpenEvent());
        assertEquals(false, testConfig.getAutoShowMixpanelUpdates());
        assertEquals("EVENTS ENDPOINT", testConfig.getEventsEndpoint());
        assertEquals("EVENTS FALLBACK ENDPOINT", testConfig.getEventsFallbackEndpoint());
        assertEquals("PEOPLE ENDPOINT", testConfig.getPeopleEndpoint());
        assertEquals("PEOPLE FALLBACK ENDPOINT", testConfig.getPeopleFallbackEndpoint());
        assertEquals("DECIDE ENDPOINT", testConfig.getDecideEndpoint());
        assertEquals("DECIDE FALLBACK ENDPOINT", testConfig.getDecideFallbackEndpoint());
    }

    public void testSurveyStateSaving() {
        final String surveyJsonString =
            "{\"collections\":[{\"id\":151,\"selector\":\"\\\"@mixpanel\\\" in properties[\\\"$email\\\"]\"}]," +
             "\"id\":299," +
             "\"questions\":[" +
                 "{\"prompt\":\"PROMPT1\",\"extra_data\":{\"$choices\":[\"Answer1,1\",\"Answer1,2\",\"Answer1,3\"]},\"type\":\"multiple_choice\",\"id\":287}," +
                 "{\"prompt\":\"PROMPT2\",\"extra_data\":{},\"type\":\"text\",\"id\":289}]}";

        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        Bitmap testBitmap = Bitmap.createBitmap(100, 100, conf);

        UpdateDisplayState originalUpdateDisplayState;
        try {
            final JSONObject surveyJson = new JSONObject(surveyJsonString);
            final Survey s = new Survey(surveyJson);
            final UpdateDisplayState.DisplayState.SurveyState surveyState =
                new UpdateDisplayState.DisplayState.SurveyState(s);
            surveyState.setHighlightColor(Color.WHITE);
            surveyState.setBackground(testBitmap);
            originalUpdateDisplayState = new UpdateDisplayState(surveyState, "DistinctId", "Token");
        } catch (JSONException e) {
            throw new RuntimeException("Survey string in test doesn't parse");
        } catch (BadDecideObjectException e) {
            throw new RuntimeException("Test survey string couldn't be made into a survey");
        }

        final Bundle inBundle = new Bundle();
        inBundle.putParcelable("TEST SURVEY PARCEL", originalUpdateDisplayState);
        final Parcel outerParcel = Parcel.obtain();
        inBundle.writeToParcel(outerParcel, 0);
        outerParcel.setDataPosition(0);
        final Bundle outBundle = outerParcel.readBundle();
        outBundle.setClassLoader(UpdateDisplayState.class.getClassLoader());
        final UpdateDisplayState inUpdateDisplayState = outBundle.getParcelable("TEST SURVEY PARCEL");

        final UpdateDisplayState.DisplayState.SurveyState surveyState =
                (UpdateDisplayState.DisplayState.SurveyState) inUpdateDisplayState.getDisplayState();
        final Survey inSurvey = surveyState.getSurvey();
        final String inDistinctId = inUpdateDisplayState.getDistinctId();
        final String inToken = inUpdateDisplayState.getToken();

        assertEquals("DistinctId", inDistinctId);
        assertEquals("Token", inToken);
        assertEquals(inSurvey.getId(), 299);

        List<Survey.Question> inQuestions = inSurvey.getQuestions();
        assertEquals(2, inQuestions.size());

        final Survey.Question q1 = inQuestions.get(0);
        assertEquals(q1.getId(), 287);
        assertEquals(q1.getPrompt(), "PROMPT1");
        assertEquals(q1.getType(), Survey.QuestionType.MULTIPLE_CHOICE);

        final List<String> q1Choices = q1.getChoices();
        assertEquals(q1Choices.size(), 3);
        assertEquals(q1Choices.get(0), "Answer1,1");
        assertEquals(q1Choices.get(1), "Answer1,2");
        assertEquals(q1Choices.get(2), "Answer1,3");

        final Survey.Question q2 = inQuestions.get(1);
        assertEquals(q2.getId(), 289);
        assertEquals(q2.getPrompt(), "PROMPT2");
        assertEquals(q2.getType(), Survey.QuestionType.TEXT);

        assertNotNull(surveyState.getBackground());
        assertNotNull(surveyState.getAnswers());
    }

    public void test2XUrls() {
        final String twoXBalok = InAppNotification.sizeSuffixUrl("http://images.mxpnl.com/112690/1392337640909.49573.Balok_first.jpg", "@BANANAS");
        assertEquals(twoXBalok, "http://images.mxpnl.com/112690/1392337640909.49573.Balok_first@BANANAS.jpg");

        final String nothingMatches = InAppNotification.sizeSuffixUrl("http://images.mxpnl.com/112690/1392337640909.49573.Balok_first..", "@BANANAS");
        assertEquals(nothingMatches, "http://images.mxpnl.com/112690/1392337640909.49573.Balok_first..");

        final String emptyMatch = InAppNotification.sizeSuffixUrl("", "@BANANAS");
        assertEquals(emptyMatch, "");

        final String nothingExtensionful = InAppNotification.sizeSuffixUrl("http://images.mxpnl.com/112690/", "@BANANAS");
        assertEquals(nothingExtensionful, "http://images.mxpnl.com/112690/");
    }

    public void testAlias() {
        final RemoteService mockPoster = new HttpService() {
            @Override
            public byte[] performRequest(String endpointUrl, List<NameValuePair> nameValuePairs, SSLSocketFactory socketFactory) {
                try {
                    assertEquals(nameValuePairs.get(0).getName(), "data");
                    final String jsonData = Base64Coder.decodeString(nameValuePairs.get(0).getValue());
                    JSONArray msg = new JSONArray(jsonData);
                    JSONObject event = msg.getJSONObject(0);
                    JSONObject properties = event.getJSONObject("properties");

                    assertEquals(event.getString("event"), "$create_alias");
                    assertEquals(properties.getString("distinct_id"), "old id");
                    assertEquals(properties.getString("alias"), "new id");
                } catch (JSONException e) {
                    throw new RuntimeException("Malformed data passed to test mock", e);
                }
                return TestUtils.bytes("1\n");
            }
        };

        final AnalyticsMessages listener = new AnalyticsMessages(getContext()) {
            @Override
            protected RemoteService getPoster() {
                return mockPoster;
            }
        };

        MixpanelAPI metrics = new TestUtils.CleanMixpanelAPI(getContext(), mMockPreferences, "Test Message Queuing") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                 return listener;
            }
        };

        // Check that we post the alias immediately
        metrics.identify("old id");
        metrics.alias("new id", "old id");
    }

    private Future<SharedPreferences> mMockPreferences;

    private static final int POLL_WAIT_SECONDS = 10;
}
