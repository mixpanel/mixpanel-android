package com.mixpanel.android.mpmetrics;


import java.util.ArrayList;
import java.util.Collections;
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
import android.os.Bundle;
import android.test.AndroidTestCase;

public class MixpanelBasicTest extends AndroidTestCase {

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      AnalyticsMessages messages = AnalyticsMessages.getInstance(getContext());
      messages.hardKill();
      Thread.sleep(500);
    } // end of setUp() method definition

    public void testTrivialRunning() {
        assertTrue(getContext() != null);
    }

    public void testCustomConfig() {
        Bundle bundle = new Bundle();
        bundle.putInt("com.mixpanel.android.MPConfig.BulkUploadLimit", 101);
        bundle.putInt("com.mixpanel.android.MPConfig.FlushInterval", 102);
        bundle.putInt("com.mixpanel.android.MPConfig.DataExpiration", 103);
        bundle.putString("com.mixpanel.android.MPConfig.EventsEndpoint", "EventsEndpoint");
        bundle.putString("com.mixpanel.android.MPConfig.EventsFallbackEndpoint", "EventsFallback");
        bundle.putString("com.mixpanel.android.MPConfig.PeopleEndpoint", "PeopleEndpoint");
        bundle.putString("com.mixpanel.android.MPConfig.PeopleFallbackEndpoint", "PeopleFallback");
        bundle.putString("com.mixpanel.android.MPConfig.DecideEndpoint", "DecideEndpoint");
        bundle.putString("com.mixpanel.android.MPConfig.DecideFallbackEndpoint", "DecideFallback");

        MPConfig config = new MPConfig(bundle);
        assertEquals(config.getBulkUploadLimit(), 101);
        assertEquals(config.getFlushInterval(), 102);
        assertEquals(config.getDataExpiration(), 103);
        assertEquals(config.getEventsEndpoint(), "EventsEndpoint");
        assertEquals(config.getEventsFallbackEndpoint(), "EventsFallback");
        assertEquals(config.getPeopleEndpoint(), "PeopleEndpoint");
        assertEquals(config.getPeopleFallbackEndpoint(), "PeopleFallback");
        assertEquals(config.getDecideEndpoint(), "DecideEndpoint");
        assertEquals(config.getDecideFallbackEndpoint(), "DecideFallback");
    }

    public void testGeneratedDistinctId() {
        String fakeToken = UUID.randomUUID().toString();
        MixpanelAPI mixpanel = MixpanelAPI.getInstance(getContext(), fakeToken);
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

    public void testReadOlderWaitingPeopleRecord() {
        // $append is a late addition to the waiting record protocol, we need to handle old records as well.
        try {
            WaitingPeopleRecord r = new WaitingPeopleRecord();
            r.readFromJSONString("{ \"$set\":{\"a\":1}, \"$add\":{\"b\":2} }");

            JSONObject setMessage = r.setMessage();
            Map<String, Double> incrementMessage = r.incrementMessage();
            List<JSONObject> appendMessages = r.appendMessages();

            assertEquals(setMessage.getLong("a"), 1L);
            assertEquals(incrementMessage.get("b").longValue(), 2L);
            assertEquals(appendMessages.size(), 0);

        } catch(JSONException e) {
            fail("Can't read old-style waiting people record");
        }
    }

    public void testLooperDestruction() {

        final BlockingQueue<JSONObject> messages = new LinkedBlockingQueue<JSONObject>();

        // If something terrible happens in the worker thread, we
        // should make sure
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
        MixpanelAPI mixpanel = new MixpanelAPI(getContext(), "TEST TOKEN testLooperDisaster") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return explodingMessages;
            }
        };

        try {
            mixpanel.clearPreferences();
            assertFalse(explodingMessages.isDead());

            mixpanel.track("event1", null);
            JSONObject found = messages.poll(1, TimeUnit.SECONDS);
            assertNotNull(found);
            Thread.sleep(1000);
            assertTrue(explodingMessages.isDead());

            mixpanel.track("event2", null);
            JSONObject shouldntFind = messages.poll(1, TimeUnit.SECONDS);
            assertNull(shouldntFind);
            assertTrue(explodingMessages.isDead());
        } catch (InterruptedException e) {
            fail("Unexpected interruption");
        }
    }

    public void testIdentifyAfterSet() {
        final List<JSONObject> messages = new ArrayList<JSONObject>();

        final AnalyticsMessages listener = new AnalyticsMessages(getContext()) {
            @Override
            public void peopleMessage(JSONObject heard) {
                messages.add(heard);
            }
        };

        MixpanelAPI mixpanel = new MixpanelAPI(getContext(), "TEST TOKEN testIdentifyAfterSet") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return listener;
            }
        };

        mixpanel.clearPreferences();

        MixpanelAPI.People people = mixpanel.getPeople();
        people.increment("the prop", 100L);
        people.append("the prop", 66);

        people.set("the prop", 1); // should wipe out what comes before

        people.increment("the prop", 2L);
        people.increment("the prop", 3);
        people.append("the prop", 88);
        people.append("the prop", 99);
        people.identify("Personal Identity");

        assertEquals(messages.size(), 4);
        JSONObject setMessage = messages.get(0);

        try {
            JSONObject setValues = setMessage.getJSONObject("$set");
            Long setForProp = setValues.getLong("the prop");
            assertEquals(setForProp.longValue(), 1);
        } catch (JSONException e) {
            fail("Unexpected JSON for set message " + setMessage.toString());
        }

        // We can guarantee that appendOne happens before appendTwo,
        // but not that it happens before or after increment
        JSONObject addMessage = null;
        JSONObject appendOne = null;
        JSONObject appendTwo = null;

        for (int i = 1; i < 4; i ++) {
            JSONObject nextMessage = messages.get(i);
            if (nextMessage.has("$append") && appendOne == null) {
                appendOne = nextMessage;
            }
            else if(nextMessage.has("$append") && appendTwo == null) {
                appendTwo = nextMessage;
            }
            else if(nextMessage.has("$add") && addMessage == null) {
                addMessage = nextMessage;
            }
            else {
                fail("Unexpected JSON message sent: " + nextMessage.toString());
            }
        }

        assertTrue(addMessage != null);
        assertTrue(appendOne != null);
        assertTrue(appendTwo != null);

        try {
            JSONObject addValues = addMessage.getJSONObject("$add");
            Long addForProp = addValues.getLong("the prop");
            assertEquals(addForProp.longValue(), 5);
        } catch (JSONException e) {
            fail("Unexpected JSON for add message " + addMessage.toString());
        }

        try {
            JSONObject appendValuesOne = appendOne.getJSONObject("$append");
            Long appendForProp = appendValuesOne.getLong("the prop");
            assertEquals(appendForProp.longValue(), 88);
        } catch (JSONException e) {
            fail("Unexpected JSON for append message " + appendOne.toString());
        }

        messages.clear();

        Map<String, Long> lastIncrement = new HashMap<String, Long>();
        lastIncrement.put("the prop", 9000L);
        people.increment(lastIncrement);
        people.set("the prop", "identified");

        assertEquals(messages.size(), 2);

        JSONObject nextIncrement = messages.get(0);
        try {
            JSONObject addValues = nextIncrement.getJSONObject("$add");
            Long addForProp = addValues.getLong("the prop");
            assertEquals(addForProp.longValue(), 9000);
        } catch (JSONException e) {
            fail("Unexpected JSON for add message " + nextIncrement.toString());
        }

        JSONObject nextSet = messages.get(1);
        try {
            JSONObject setValues = nextSet.getJSONObject("$set");
            String setForProp = setValues.getString("the prop");
            assertEquals(setForProp, "identified");
        } catch (JSONException e) {
            fail("Unexpected JSON for set message " + nextSet.toString());
        }
    }

    public void testIdentifyAndGetDistinctId() {
        MixpanelAPI metrics = new MixpanelAPI(getContext(), "Identify Test Token");
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

    public void testSurveys() {
        final List<String> responses = Collections.synchronizedList(new ArrayList<String>());

        final ServerMessage mockMessage = new ServerMessage() {
            @Override public Result get(String endpointUrl, String fallbackUrl) {
                return new Result(Status.SUCCEEDED, responses.remove(0));
            }
        };
        final AnalyticsMessages mockMessages = new AnalyticsMessages(getContext()) {
            @Override protected ServerMessage getPoster() {
                return mockMessage;
            }
        };
        final MixpanelAPI mixpanel = new MixpanelAPI(getContext(), "TEST TOKEN testLooperDisaster") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return mockMessages;
            }
        };

        responses.add("{\"surveys\":[]}");
        mixpanel.getPeople().checkForSurvey(new SurveyCallbacks(){
            @Override public void foundSurvey(Survey s) {
                assertNull(s);
            }
        });

        responses.add(
                "{\"surveys\":[ {" +
                "   \"id\":291," +
                "   \"questions\":[" +
                "       {\"id\":275,\"type\":\"multiple_choice\",\"extra_data\":{\"$choices\":[\"Option 1\",\"Option 2\"]},\"prompt\":\"Multiple Choice Prompt\"}," +
                "       {\"id\":277,\"type\":\"text\",\"extra_data\":{},\"prompt\":\"Text Field Prompt\"}]," +
                "   \"collections\":[{\"selector\":\"\\\"@mixpanel\\\" in properties[\\\"$email\\\"]\",\"id\":141}]}" +
                "]}"
        );
        // TODO TEST IS BROKEN. What if this is never called (for example, because the survey didn't parse?)
        mixpanel.getPeople().checkForSurvey(new SurveyCallbacks(){
            @Override public void foundSurvey(Survey s) {
                assertEquals(s.getId(), 291);
                assertEquals(s.getCollectionId(), 141);

                List<Survey.Question> questions = s.getQuestions();
                assertEquals(questions.size(), 2);

                Survey.Question mcQuestion = questions.get(0);
                assertEquals(mcQuestion.getId(), 275);
                assertEquals(mcQuestion.getPrompt(), "Multiple Choice Prompt");
                assertEquals(mcQuestion.getType(), Survey.QuestionType.MULTIPLE_CHOICE);
                List<String> mcChoices = mcQuestion.getChoices();
                assertEquals(mcChoices.size(), 2);
                assertEquals(mcChoices.get(0), "Option 1");
                assertEquals(mcChoices.get(1), "Option 2");

                Survey.Question textQuestion = questions.get(1);
                assertEquals(textQuestion.getId(), 277);
                assertEquals(textQuestion.getPrompt(), "Text Field Prompt");
                assertEquals(textQuestion.getType(), Survey.QuestionType.TEXT);
                List<String> textChoices = textQuestion.getChoices();
                assertEquals(textChoices.size(), 0);
            }
        });

        responses.add(
               "{\"surveys\":[{\"collections\":[{\"id\":151,\"selector\":\"\\\"@mixpanel\\\" in properties[\\\"$email\\\"]\"}],\"id\":299,\"questions\":[{\"prompt\":\"PROMPT1\",\"extra_data\":{\"$choices\":[\"Answer1,1\",\"Answer1,2\",\"Answer1,3\"]},\"type\":\"multiple_choice\",\"id\":287},{\"prompt\":\"How has the demo affected you?\",\"extra_data\":{\"$choices\":[\"I laughed, I cried, it was better than \\\"Cats\\\"\",\"I want to see it again, and again, and again.\"]},\"type\":\"multiple_choice\",\"id\":289}]}]}"
        );
        mixpanel.getPeople().checkForSurvey(new SurveyCallbacks(){
            @Override public void foundSurvey(Survey s) {
                assertEquals(s.getId(), 299);
                assertEquals(s.getCollectionId(), 151);

                List<Survey.Question> questions = s.getQuestions();
                assertEquals(questions.size(), 2);

                Survey.Question mcQuestion = questions.get(0);
                assertEquals(mcQuestion.getId(), 287);
                assertEquals(mcQuestion.getPrompt(), "PROMPT1");
                assertEquals(mcQuestion.getType(), Survey.QuestionType.MULTIPLE_CHOICE);
                List<String> mcChoices = mcQuestion.getChoices();
                assertEquals(mcChoices.size(), 3);
                assertEquals(mcChoices.get(0), "Answer1,1");
                assertEquals(mcChoices.get(1), "Answer1,2");
                assertEquals(mcChoices.get(2), "Answer1,3");
            }
        });
    }

    public void testMessageQueuing() {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<String>();

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

        final ServerMessage mockPoster = new ServerMessage() {
            @Override
            public Result postData(String rawMessage, String endpointUrl, String fallbackUrl) {
                try {
                    messages.put("SENT FLUSH " + endpointUrl);
                    messages.put(rawMessage);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                return new Result(Status.SUCCEEDED, "1\n");
            }
        };

        final MPConfig mockConfig = new MPConfig(new Bundle()) {
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
            protected ServerMessage getPoster() {
                return mockPoster;
            }
        };

        MixpanelAPI metrics = new MixpanelAPI(getContext(), "Test Message Queuing") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                 return listener;
            }
        };
        // metrics.logPosts();

        // Test filling up the message queue
        for (int i=0; i < mockConfig.getBulkUploadLimit() - 1; i++) {
            metrics.track("frequent event", null);
        }

        metrics.track("final event", null);
        String expectedJSONMessage = "<No message actually recieved>";

        try {
            for (int i=0; i < mockConfig.getBulkUploadLimit() - 1; i++) {
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
            assertEquals("SENT FLUSH EVENTS_ENDPOINT", messageFlush);

            expectedJSONMessage = messages.poll(1, TimeUnit.SECONDS);
            JSONArray bigFlush = new JSONArray(expectedJSONMessage);
            assertEquals(mockConfig.getBulkUploadLimit(), bigFlush.length());

            metrics.track("next wave", null);
            metrics.flush();

            String nextWaveTable = messages.poll(1, TimeUnit.SECONDS);
            assertEquals("TABLE " + MPDbAdapter.Table.EVENTS.getName(), nextWaveTable);

            expectedJSONMessage = messages.poll(1, TimeUnit.SECONDS);
            JSONObject nextWaveMessage = new JSONObject(expectedJSONMessage);
            assertEquals("next wave", nextWaveMessage.getString("event"));

            String manualFlush = messages.poll(1, TimeUnit.SECONDS);
            assertEquals("SENT FLUSH EVENTS_ENDPOINT", manualFlush);

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
            assertEquals("SENT FLUSH PEOPLE_ENDPOINT", peopleFlush);

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

        final List<ServerMessage.Result> results = new ArrayList<ServerMessage.Result>();
        results.add(new ServerMessage.Result(ServerMessage.Status.SUCCEEDED, "1\n"));
        results.add(new ServerMessage.Result(ServerMessage.Status.FAILED_RECOVERABLE, null));
        results.add(new ServerMessage.Result(ServerMessage.Status.FAILED_UNRECOVERABLE, "0\n"));
        results.add(new ServerMessage.Result(ServerMessage.Status.FAILED_RECOVERABLE, null));
        results.add(new ServerMessage.Result(ServerMessage.Status.SUCCEEDED, "1\n"));

        final BlockingQueue<String> attempts = new LinkedBlockingQueue<String>();

        final ServerMessage mockPoster = new ServerMessage() {
            @Override
            public ServerMessage.Result postData(String rawMessage, String endpointUrl, String fallbackUrl) {
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

        final AnalyticsMessages listener = new AnalyticsMessages(getContext()) {
            @Override
            protected ServerMessage getPoster() {
                return mockPoster;
            }
        };

        MixpanelAPI metrics = new MixpanelAPI(getContext(), "Test Message Queuing") {
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

    public void testTrackCharge() {
        final List<JSONObject> messages = new ArrayList<JSONObject>();
        final AnalyticsMessages listener = new AnalyticsMessages(getContext()) {
            @Override
            public void eventsMessage(EventDTO heard) {
                throw new RuntimeException("Should not be called during this test");
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

        MixpanelAPI api = new ListeningAPI(getContext(), "TRACKCHARGE TEST TOKEN");
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
        MixpanelAPI metricsOne = new MixpanelAPI(getContext(), "SAME TOKEN");
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

        final List<Object> messages = new ArrayList<Object>();
        final AnalyticsMessages listener = new AnalyticsMessages(getContext()) {
            @Override
            public void eventsMessage(EventDTO heard) {
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

        MixpanelAPI differentToken = new ListeningAPI(getContext(), "DIFFERENT TOKEN");
        differentToken.track("other event", null);
        differentToken.getPeople().set("other people prop", "Word"); // should be queued up.

        assertEquals(1, messages.size());

        AnalyticsMessages.EventDTO eventMessage = (AnalyticsMessages.EventDTO) messages.get(0);

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

        MixpanelAPI metricsTwo = new ListeningAPI(getContext(), "SAME TOKEN");

        metricsTwo.track("eventname", null);
        metricsTwo.getPeople().set("people prop name", "Indeed");

        assertEquals(2, messages.size());

        eventMessage = (AnalyticsMessages.EventDTO) messages.get(0);
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

                MixpanelAPI mixpanel = new MixpanelAPI(getContext(), "TEST TOKEN") {
                    @Override
                    protected AnalyticsMessages getAnalyticsMessages() {
                        return analyticsMessages;
                    }
                };

                mixpanel.track("test in thread", new JSONObject());
            }
        }

        final BlockingQueue<JSONObject> messages = new LinkedBlockingQueue<JSONObject>();
        TestThread testThread = new TestThread(messages);
        testThread.start();
        JSONObject found = messages.poll(1, TimeUnit.SECONDS);
        assertNotNull(found);
        assertEquals(found.getString("event"), "test in thread");
        assertTrue(found.getJSONObject("properties").has("$bluetooth_version"));
    }


}
