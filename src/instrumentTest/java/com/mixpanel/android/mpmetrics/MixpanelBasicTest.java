package com.mixpanel.android.mpmetrics;


import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.test.AndroidTestCase;
import android.test.mock.MockContext;
import android.test.mock.MockPackageManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MixpanelBasicTest extends AndroidTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final SharedPreferences referrerPreferences = getContext().getSharedPreferences("MIXPANEL_TEST_PREFERENCES", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = referrerPreferences.edit();
        editor.clear();
        editor.commit();
        mMockPreferences = new Future<SharedPreferences>() {
            @Override
            public boolean cancel(final boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public SharedPreferences get() throws InterruptedException, ExecutionException {
                return referrerPreferences;
            }

            @Override
            public SharedPreferences get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return referrerPreferences;
            }
        };


        AnalyticsMessages messages = AnalyticsMessages.getInstance(getContext());
        messages.hardKill();
        Thread.sleep(500);
    } // end of setUp() method definition

    public void testTrivialRunning() {
        assertTrue(getContext() != null);
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
        MixpanelAPI mixpanel = new MixpanelAPI(getContext(), mMockPreferences, "TEST TOKEN testLooperDisaster") {
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

    public void testPeopleOperations() throws JSONException {
        final List<JSONObject> messages = new ArrayList<JSONObject>();

        final AnalyticsMessages listener = new AnalyticsMessages(getContext()) {
            @Override
            public void peopleMessage(JSONObject heard) {
                messages.add(heard);
            }
        };

        MixpanelAPI mixpanel = new MixpanelAPI(getContext(), mMockPreferences, "TEST TOKEN testIdentifyAfterSet") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return listener;
            }
        };

        mixpanel.getPeople().identify("TEST IDENTITY");

        mixpanel.getPeople().set("SET NAME", "SET VALUE");
        mixpanel.getPeople().increment("INCREMENT NAME", 1);
        mixpanel.getPeople().append("APPEND NAME", "APPEND VALUE");
        mixpanel.getPeople().setOnce("SET ONCE NAME", "SET ONCE VALUE");
        mixpanel.getPeople().union("UNION NAME", new JSONArray("[100]"));
        mixpanel.getPeople().unset("UNSET NAME");
        mixpanel.getPeople().trackCharge(100, new JSONObject("{\"name\": \"val\"}"));
        mixpanel.getPeople().clearCharges();
        mixpanel.getPeople().deleteUser();

        JSONObject setMessage = messages.get(0).getJSONObject("$set");
        assertEquals("SET VALUE", setMessage.getString("SET NAME"));

        JSONObject addMessage = messages.get(1).getJSONObject("$add");
        assertEquals(1, addMessage.getInt("INCREMENT NAME"));

        JSONObject appendMessage = messages.get(2).getJSONObject("$append");
        assertEquals("APPEND VALUE", appendMessage.get("APPEND NAME"));

        JSONObject setOnceMessage = messages.get(3).getJSONObject("$set_once");
        assertEquals("SET ONCE VALUE", setOnceMessage.getString("SET ONCE NAME"));

        JSONObject unionMessage = messages.get(4).getJSONObject("$union");
        JSONArray unionValues = unionMessage.getJSONArray("UNION NAME");
        assertEquals(1, unionValues.length());
        assertEquals(100, unionValues.getInt(0));

        JSONArray unsetMessage = messages.get(5).getJSONArray("$unset");
        assertEquals(1, unsetMessage.length());
        assertEquals("UNSET NAME", unsetMessage.get(0));

        JSONObject trackChargeMessage = messages.get(6).getJSONObject("$append");
        JSONObject transaction = trackChargeMessage.getJSONObject("$transactions");
        assertEquals(100.0d, transaction.getDouble("$amount"));

        JSONArray clearChargesMessage = messages.get(7).getJSONArray("$unset");
        assertEquals(1, clearChargesMessage.length());
        assertEquals("$transactions", clearChargesMessage.getString(0));

        assertTrue(messages.get(8).has("$delete"));
    }

    public void testIdentifyAfterSet() {
        final List<JSONObject> messages = new ArrayList<JSONObject>();

        final AnalyticsMessages listener = new AnalyticsMessages(getContext()) {
            @Override
            public void peopleMessage(JSONObject heard) {
                messages.add(heard);
            }
        };

        MixpanelAPI mixpanel = new MixpanelAPI(getContext(), mMockPreferences, "TEST TOKEN testIdentifyAfterSet") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return listener;
            }
        };

        mixpanel.clearPreferences();

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
        MixpanelAPI metrics = new MixpanelAPI(getContext(), mMockPreferences, "Identify Test Token");
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
        final BlockingQueue<String> foundQueue = new LinkedBlockingQueue<String>();
        MixpanelAPI mixpanel = apiForSurvey(responses);

        responses.add("{\"surveys\":[]}");
        mixpanel.getPeople().checkForSurvey(new SurveyCallbacks() {
            @Override
            public void foundSurvey(final Survey s) {
                fail("Shouldn't check for surveys if the user is not identified.");
            }
        });

        mixpanel = apiForSurvey(responses);
        mixpanel.getPeople().identify("SURVEY TEST USER");
        mixpanel.getPeople().checkForSurvey(new SurveyCallbacks(){
            @Override
            public void foundSurvey(Survey s) {
                assertNull(s);
                try {
                    foundQueue.put("OK 0");
                } catch (InterruptedException e) {
                    throw new RuntimeException("Test Interrupted");
                }
            }
        });

        try {
            final String ok = foundQueue.poll(1, TimeUnit.SECONDS);
            assertEquals("OK 0", ok);
        } catch(InterruptedException e) {
            fail("checkForSurvey never returned");
        }

        responses.add(
                "{\"surveys\":[ {" +
                "   \"id\":291," +
                "   \"questions\":[" +
                "       {\"id\":275,\"type\":\"multiple_choice\",\"extra_data\":{\"$choices\":[\"Option 1\",\"Option 2\"]},\"prompt\":\"Multiple Choice Prompt\"}," +
                "       {\"id\":277,\"type\":\"text\",\"extra_data\":{},\"prompt\":\"Text Field Prompt\"}]," +
                "   \"collections\":[{\"selector\":\"\\\"@mixpanel\\\" in properties[\\\"$email\\\"]\",\"id\":141}]}" +
                "]}"
        );

        mixpanel = apiForSurvey(responses);
        mixpanel.getPeople().identify("SURVEY TEST USER");
        mixpanel.getPeople().checkForSurvey(new SurveyCallbacks(){
            @Override public void foundSurvey(Survey s) {
                if (Build.VERSION.SDK_INT < 10) {
                    assertNull(s);
                } else {
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

                try {
                    foundQueue.put("OK 1");
                } catch (InterruptedException e) {
                    throw new RuntimeException("Test was interrupted");
                }
            }
        });

        try {
            final String ok = foundQueue.poll(1, TimeUnit.SECONDS);
            assertEquals("OK 1", ok);
        } catch(InterruptedException e) {
            fail("checkForSurvey never returned");
        }

        responses.add(
               "{\"surveys\":[{\"collections\":[{\"id\":151,\"selector\":\"\\\"@mixpanel\\\" in properties[\\\"$email\\\"]\"}],\"id\":299,\"questions\":[{\"prompt\":\"PROMPT1\",\"extra_data\":{\"$choices\":[\"Answer1,1\",\"Answer1,2\",\"Answer1,3\"]},\"type\":\"multiple_choice\",\"id\":287},{\"prompt\":\"How has the demo affected you?\",\"extra_data\":{\"$choices\":[\"I laughed, I cried, it was better than \\\"Cats\\\"\",\"I want to see it again, and again, and again.\"]},\"type\":\"multiple_choice\",\"id\":289}]}]}"
        );
        mixpanel = apiForSurvey(responses);
        mixpanel.getPeople().identify("SURVEY TEST USER");
        mixpanel.getPeople().checkForSurvey(new SurveyCallbacks(){
            @Override public void foundSurvey(Survey s) {
                if (Build.VERSION.SDK_INT < 10) {
                    assertNull(s);
                } else {
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

                try {
                    foundQueue.put("OK 2");
                } catch (InterruptedException e) {
                    throw new RuntimeException("Test was interrupted");
                }
            }
        });

        try {
            final String ok = foundQueue.poll(1, TimeUnit.SECONDS);
            assertEquals("OK 2", ok);
        } catch(InterruptedException e) {
            fail("checkForSurvey never returned");
        }

        // Corrupted or crazy responses.
        responses.add("{ WONT PARSE");
        mixpanel = apiForSurvey(responses);
        mixpanel.getPeople().identify("SURVEY TEST USER");
        getNoSurvey(mixpanel, foundQueue);

        // Valid JSON but bad (no name)
        responses.add("{\"surveys\":{\"id\":3,\"collections\":[{\"id\": 9}],\"questions\":[{\"id\":12,\"type\":\"text\",\"prompt\":\"P\",\"extra_data\":{}}]}");
        mixpanel = apiForSurvey(responses);
        mixpanel.getPeople().identify("SURVEY TEST USER");
        getNoSurvey(mixpanel, foundQueue);

        // Just pure craziness
        responses.add("null");
        mixpanel = apiForSurvey(responses);
        mixpanel.getPeople().identify("SURVEY TEST USER");
        getNoSurvey(mixpanel, foundQueue);

        // Valid JSON that isn't relevant
        responses.add("{\"Ziggy Startdust and the Spiders from Mars\":\"The Best Ever Number One\"}");
        mixpanel = apiForSurvey(responses);
        mixpanel.getPeople().identify("SURVEY TEST USER");
        getNoSurvey(mixpanel, foundQueue);

        // Valid survey with no questions
        responses.add(
                "{\"surveys\":[{\"collections\":[{\"id\":151,\"selector\":\"\\\"@mixpanel\\\" in properties[\\\"$email\\\"]\"}],\"id\":299,\"questions\":[]}]}"
        );
        mixpanel = apiForSurvey(responses);
        mixpanel.getPeople().identify("SURVEY TEST USER");
        getNoSurvey(mixpanel, foundQueue);

        // Valid survey with a question with no choices
        responses.add(
                "{\"surveys\":[ {" +
                        "   \"id\":291," +
                        "   \"questions\":[" +
                        "       {\"id\":275,\"type\":\"multiple_choice\",\"extra_data\":{\"$choices\":[]},\"prompt\":\"Multiple Choice Prompt\"}," +
                        "   \"collections\":[{\"selector\":\"\\\"@mixpanel\\\" in properties[\\\"$email\\\"]\",\"id\":141}]}" +
                        "]}"
        );
        mixpanel = apiForSurvey(responses);
        mixpanel.getPeople().identify("SURVEY TEST USER");
        getNoSurvey(mixpanel, foundQueue);
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

        MixpanelAPI metrics = new MixpanelAPI(getContext(), mMockPreferences, "Test Message Queuing") {
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

        MixpanelAPI metrics = new MixpanelAPI(getContext(), mMockPreferences, "Test Message Queuing") {
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
            public void eventsMessage(EventDescription heard) {
                throw new RuntimeException("Should not be called during this test");
            }

            @Override
            public void peopleMessage(JSONObject heard) {
                messages.add(heard);
            }
        };

        class ListeningAPI extends MixpanelAPI {
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

                MixpanelAPI mixpanel = new MixpanelAPI(getContext(), mMockPreferences, "TEST TOKEN") {
                    @Override
                    protected AnalyticsMessages getAnalyticsMessages() {
                        return analyticsMessages;
                    }
                };

                mixpanel.track("test in thread", new JSONObject());
            }
        }

        //////////////////////////////

        final BlockingQueue<JSONObject> messages = new LinkedBlockingQueue<JSONObject>();
        TestThread testThread = new TestThread(messages);
        testThread.start();
        JSONObject found = messages.poll(1, TimeUnit.SECONDS);
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
        appInfo.metaData.putBoolean("com.mixpanel.android.MPConfig.AutoCheckForSurveys", false);

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
        assertEquals(false, testConfig.getAutoCheckForSurveys());
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

        SurveyState originalSurveyState;
        try {
            final JSONObject surveyJson = new JSONObject(surveyJsonString);
            final Survey s = new Survey(surveyJson);
            originalSurveyState = new SurveyState(s, "DistinctId", "Token", testBitmap, Color.WHITE);
        } catch (JSONException e) {
            throw new RuntimeException("Survey string in test doesn't parse");
        } catch (Survey.BadSurveyException e) {
            throw new RuntimeException("Test survey string couldn't be made into a survey");
        }

        final Bundle inBundle = new Bundle();
        inBundle.putParcelable("TEST SURVEY PARCEL", originalSurveyState);
        final Parcel outerParcel = Parcel.obtain();
        inBundle.writeToParcel(outerParcel, 0);
        outerParcel.setDataPosition(0);
        final Bundle outBundle = outerParcel.readBundle();
        outBundle.setClassLoader(SurveyState.class.getClassLoader());
        final SurveyState inSurveyState = outBundle.getParcelable("TEST SURVEY PARCEL");

        final Survey inSurvey = inSurveyState.getSurvey();
        final String inDistinctId = inSurveyState.getDistinctId();
        final String inToken = inSurveyState.getToken();

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

        assertNotNull(inSurveyState.getBackground());
        assertNotNull(inSurveyState.getAnswers());
    }

    private void getNoSurvey(final MixpanelAPI mixpanel, final BlockingQueue<String> foundQueue) {
        mixpanel.getPeople().checkForSurvey(new SurveyCallbacks() {
            @Override
            public void foundSurvey(final Survey s) {
                assertNull(s);
                try {
                    foundQueue.put("OK NO SURVEY");
                } catch (InterruptedException e) {
                    throw new RuntimeException("Test was interrupted");
                }
            }
        });

        try {
            final String ok = foundQueue.poll(1, TimeUnit.SECONDS);
            assertEquals("OK NO SURVEY", ok);
        } catch (InterruptedException e) {
            throw new RuntimeException("Test was interrupted");
        }
    }

    private MixpanelAPI apiForSurvey(final List<String> responses) {
        final ServerMessage mockMessage = new ServerMessage() {
            @Override
            public Result get(String endpointUrl, String fallbackUrl) {
                return new Result(Status.SUCCEEDED, responses.remove(0));
            }
        };
        final AnalyticsMessages mockMessages = new AnalyticsMessages(getContext()) {
            @Override
            protected ServerMessage getPoster() {
                return mockMessage;
            }
        };
        final MixpanelAPI mixpanel = new MixpanelAPI(getContext(), mMockPreferences, "TEST TOKEN test checkForSurveys") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return mockMessages;
            }
        };
        mixpanel.clearPreferences();
        return mixpanel;
    }

    private Future<SharedPreferences> mMockPreferences;
}
