package com.mixpanel.android.mpmetrics;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;

import com.mixpanel.android.dummy.DummyActivity;

public class MixpanelBasicTest extends
        ActivityInstrumentationTestCase2<DummyActivity> {

    public MixpanelBasicTest(){
        super("com.mixpanel.android.dummy", DummyActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      mActivity = getActivity();

      setActivityInitialTouchMode(false);


      AnalyticsMessages messages = AnalyticsMessages.getInstance(mActivity);
      messages.hardKill();
      Thread.sleep(500);
    } // end of setUp() method definition

    public void testTrivialRunning() {
        assertTrue(mActivity != null);
    }

    public void testGeneratedDistinctId() {
        String fakeToken = UUID.randomUUID().toString();
        MixpanelAPI mixpanel = MixpanelAPI.getInstance(mActivity, fakeToken);
        String generatedId1 = mixpanel.getDistinctId();
        assertTrue(generatedId1 != null);

        mixpanel.clearPreferences();
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

        MPDbAdapter adapter = new MPDbAdapter(mActivity, "DeleteTestDB");
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

    public void testReadOlderWaitingPeopleRecord() {
        // $append is a late addition to the waiting record protocol, we need to handle old records as well.
        try {
            WaitingPeopleRecord r = new WaitingPeopleRecord();
            r.readFromJSONString("{ \"$set\":{\"a\":1}, \"$add\":{\"b\":2} }");

            JSONObject setMessage = r.setMessage();
            Map<String, Long> incrementMessage = r.incrementMessage();
            List<JSONObject> appendMessages = r.appendMessages();

            assertTrue(setMessage.getLong("a") == 1);
            assertTrue(incrementMessage.get("b") == 2);
            assertTrue(appendMessages.size() == 0);

        } catch(JSONException e) {
            fail("Can't read old-style waiting people record");
        }
    }

    public void testIdentifyAfterSet() {
        final List<JSONObject> messages = new ArrayList<JSONObject>();

        final AnalyticsMessages listener = new AnalyticsMessages(mActivity) {
            @Override
            public void peopleMessage(JSONObject heard) {
                messages.add(heard);
            }
        };

        MixpanelAPI mixpanel = new MixpanelAPI(mActivity, "TEST TOKEN testIdentifyAfterSet") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return listener;
            }
        };

        mixpanel.clearPreferences();

        MixpanelAPI.People people = mixpanel.getPeople();
        people.increment("the prop", 100);
        people.set("the prop", 1);
        people.increment("the prop", 2);
        people.increment("the prop", 3);
        people.identify("Personal Identity");

        assertEquals(messages.size(), 2);
        JSONObject setMessage = messages.get(0);

        try {
            JSONObject setValues = setMessage.getJSONObject("$set");
            Long setForProp = setValues.getLong("the prop");
            assertEquals(setForProp.longValue(), 1);
        } catch (JSONException e) {
            fail("Unexpected JSON for set message " + setMessage.toString());
        }

        JSONObject addMessage = messages.get(1);

        try {
            JSONObject addValues = addMessage.getJSONObject("$add");
            Long addForProp = addValues.getLong("the prop");
            assertEquals(addForProp.longValue(), 5);
        } catch (JSONException e) {
            fail("Unexpected JSON for add message " + addMessage.toString());
        }

        people.increment("the prop", 9000);
        people.set("the prop", "identified");

        assertEquals(messages.size(), 4);

        JSONObject nextIncrement = messages.get(2);
        try {
            JSONObject addValues = nextIncrement.getJSONObject("$add");
            Long addForProp = addValues.getLong("the prop");
            assertEquals(addForProp.longValue(), 9000);
        } catch (JSONException e) {
            fail("Unexpected JSON for add message " + addMessage.toString());
        }

        JSONObject nextSet = messages.get(3);
        try {
            JSONObject setValues = nextSet.getJSONObject("$set");
            String setForProp = setValues.getString("the prop");
            assertEquals(setForProp, "identified");
        } catch (JSONException e) {
            fail("Unexpected JSON for set message " + setMessage.toString());
        }
    }

    public void testIdentifyAndGetDistinctId() {
        MixpanelAPI metrics = new MixpanelAPI(mActivity, "Identify Test Token");
        metrics.clearPreferences();
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
        MixpanelAPI.setFlushInterval(mActivity, Long.MAX_VALUE);

        final BlockingQueue<String> messages = new LinkedBlockingQueue<String>();

        final MPDbAdapter mockAdapter = new MPDbAdapter(mActivity) {
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

        final HttpPoster mockPoster = new HttpPoster() {
            @Override
            public HttpPoster.PostResult postData(String rawMessage, String endpointUrl) {
                try {
                    messages.put("SENT FLUSH " + endpointUrl);
                    messages.put(rawMessage);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                return HttpPoster.PostResult.SUCCEEDED;
            }
        };

        final AnalyticsMessages listener = new AnalyticsMessages(mActivity) {
            @Override
            protected MPDbAdapter makeDbAdapter(Context context) {
                return mockAdapter;
            }

            @Override
            protected HttpPoster getPoster() {
                return mockPoster;
            }
        };

        MixpanelAPI metrics = new MixpanelAPI(mActivity, "Test Message Queuing") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                 return listener;
            }
        };
        // metrics.logPosts();

        // Test filling up the message queue
        for (int i=0; i < (MPConfig.BULK_UPLOAD_LIMIT - 1); i++) {
            metrics.track("frequent event", null);
        }

        metrics.track("final event", null);
        String expectedJSONMessage = "<No message actually recieved>";

        try {
            for (int i=0; i < (MPConfig.BULK_UPLOAD_LIMIT - 1); i++) {
                String messageTable = messages.poll(1, TimeUnit.SECONDS);
                assertEquals("TABLE " + MPDbAdapter.Table.EVENTS.getName(), messageTable);

                expectedJSONMessage = messages.poll(1, TimeUnit.SECONDS);
                JSONObject message = new JSONObject(expectedJSONMessage);
                assertEquals("frequent event", message.getString("event"));
            }

            String messageTable = messages.poll(1, TimeUnit.SECONDS);
            assertEquals("TABLE " + MPDbAdapter.Table.EVENTS.getName(), messageTable);

            expectedJSONMessage = messages.poll(1, TimeUnit.SECONDS);
            JSONObject message = new JSONObject(expectedJSONMessage);
            assertEquals("final event", message.getString("event"));

            String messageFlush = messages.poll(1, TimeUnit.SECONDS);
            assertEquals("SENT FLUSH " + MPConfig.BASE_ENDPOINT + "/track?ip=1", messageFlush);

            expectedJSONMessage = messages.poll(1, TimeUnit.SECONDS);
            JSONArray bigFlush = new JSONArray(expectedJSONMessage);
            assertEquals(MPConfig.BULK_UPLOAD_LIMIT, bigFlush.length());

            metrics.track("next wave", null);
            metrics.flush();

            String nextWaveTable = messages.poll(1, TimeUnit.SECONDS);
            assertEquals("TABLE " + MPDbAdapter.Table.EVENTS.getName(), nextWaveTable);

            expectedJSONMessage = messages.poll(1, TimeUnit.SECONDS);
            JSONObject nextWaveMessage = new JSONObject(expectedJSONMessage);
            assertEquals("next wave", nextWaveMessage.getString("event"));

            String manualFlush = messages.poll(1, TimeUnit.SECONDS);
            assertEquals("SENT FLUSH " + MPConfig.BASE_ENDPOINT + "/track?ip=1", manualFlush);

            expectedJSONMessage = messages.poll(1, TimeUnit.SECONDS);
            JSONArray nextWave = new JSONArray(expectedJSONMessage);
            assertEquals(1, nextWave.length());

            JSONObject nextWaveEvent = nextWave.getJSONObject(0);
            assertEquals("next wave", nextWaveEvent.getString("event"));

            metrics.getPeople().identify("new person");
            metrics.getPeople().set("prop", "yup");
            metrics.flush();

            String peopleTable = messages.poll(1, TimeUnit.SECONDS);
            assertEquals("TABLE " + MPDbAdapter.Table.PEOPLE.getName(), peopleTable);

            expectedJSONMessage = messages.poll(1, TimeUnit.SECONDS);
            JSONObject peopleMessage = new JSONObject(expectedJSONMessage);

            assertEquals("new person", peopleMessage.getString("$distinct_id"));
            assertEquals("yup", peopleMessage.getJSONObject("$set").getString("prop"));

            String peopleFlush = messages.poll(1, TimeUnit.SECONDS);
            assertEquals("SENT FLUSH " + MPConfig.BASE_ENDPOINT + "/engage", peopleFlush);

            expectedJSONMessage = messages.poll(1, TimeUnit.SECONDS);
            JSONArray peopleSent = new JSONArray(expectedJSONMessage);
            assertEquals(1, peopleSent.length());

        } catch (InterruptedException e) {
            fail("Expected a log message about mixpanel communication but did not recieve it.");
        } catch (JSONException e) {
            fail("Expected a JSON object message and got something silly instead: " + expectedJSONMessage);
        }
    }

    public void testHTTPFailures() {

        final List<HttpPoster.PostResult> results = new ArrayList<HttpPoster.PostResult>();
        results.add(HttpPoster.PostResult.SUCCEEDED);
        results.add(HttpPoster.PostResult.FAILED_RECOVERABLE);
        results.add(HttpPoster.PostResult.FAILED_UNRECOVERABLE);
        results.add(HttpPoster.PostResult.FAILED_RECOVERABLE);
        results.add(HttpPoster.PostResult.SUCCEEDED);

        final BlockingQueue<String> attempts = new LinkedBlockingQueue<String>();

        final HttpPoster mockPoster = new HttpPoster() {
            @Override
            public HttpPoster.PostResult postData(String rawMessage, String endpointUrl) {
                try {
                    JSONArray msg = new JSONArray(rawMessage);
                    JSONObject event = msg.getJSONObject(0);
                    attempts.put(event.getString("event"));
                } catch (JSONException e) {
                    throw new RuntimeException("Malformed data passed to test mock");
                } catch (InterruptedException e) {
                    throw new RuntimeException("Could not write message to reporting queue for tests.");
                }
                return results.remove(0);
            }
        };

        final AnalyticsMessages listener = new AnalyticsMessages(mActivity) {
            @Override
            protected HttpPoster getPoster() {
                return mockPoster;
            }
        };

        MixpanelAPI metrics = new MixpanelAPI(mActivity, "Test Message Queuing") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                 return listener;
            }
        };

        try {
            metrics.track("Should Succeed", null);
            metrics.flush(); // Should result in SUCCEEDED
            Thread.sleep(200);

            metrics.track("Should Retry, then Fail", null);
            metrics.flush();
            Thread.sleep(200);
            metrics.flush();

            metrics.track("Should Retry, then Succeed", null);
            metrics.flush();
            Thread.sleep(200);
            metrics.flush();

            String success1 = attempts.poll(1, TimeUnit.SECONDS);
            assertEquals(success1, "Should Succeed");

            String recoverable2 = attempts.poll(1, TimeUnit.SECONDS);
            assertEquals(recoverable2, "Should Retry, then Fail");

            String hard_fail3 = attempts.poll(1, TimeUnit.SECONDS);
            assertEquals(hard_fail3, "Should Retry, then Fail");

            String recoverable4 = attempts.poll(1, TimeUnit.SECONDS);
            assertEquals(recoverable4, "Should Retry, then Succeed");

            String success5 = attempts.poll(1, TimeUnit.SECONDS);
            assertEquals(success5, "Should Retry, then Succeed");
        } catch (InterruptedException e) {
            throw new RuntimeException("Test was interrupted.");
        }

    }

    public void testPersistence() {
        MixpanelAPI metricsOne = new MixpanelAPI(mActivity, "SAME TOKEN");
        metricsOne.clearPreferences();

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

        final List<JSONObject> messages = new ArrayList<JSONObject>();
        final AnalyticsMessages listener = new AnalyticsMessages(mActivity) {
            @Override
            public void eventsMessage(JSONObject heard) {
                messages.add(heard);
            }

            @Override
            public void peopleMessage(JSONObject heard) {
                messages.add(heard);
            }
        };

        class ListeningAPI extends MixpanelAPI {
            public ListeningAPI(Context c, String token) {
                super(c, token);
            }

            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                 return listener;
            }
        }

        MixpanelAPI differentToken = new ListeningAPI(mActivity, "DIFFERENT TOKEN");
        differentToken.track("other event", null);
        differentToken.getPeople().set("other people prop", "Word"); // should be queued up.

        assertEquals(1, messages.size());

        JSONObject eventMessage = messages.get(0);

        try {
            JSONObject eventProps = eventMessage.getJSONObject("properties");
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

        MixpanelAPI metricsTwo = new ListeningAPI(mActivity, "SAME TOKEN");

        metricsTwo.track("eventname", null);
        metricsTwo.getPeople().set("people prop name", "Indeed");

        assertEquals(2, messages.size());

        eventMessage = messages.get(0);
        JSONObject peopleMessage = messages.get(1);

        try {
            JSONObject eventProps = eventMessage.getJSONObject("properties");
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


    private DummyActivity mActivity;
}
