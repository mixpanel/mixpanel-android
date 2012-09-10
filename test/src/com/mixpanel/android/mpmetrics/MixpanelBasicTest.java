package com.mixpanel.android.mpmetrics;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;

import com.mixpanel.android.hellomixpanel.HelloMixpanel;

public class MixpanelBasicTest extends
        ActivityInstrumentationTestCase2<HelloMixpanel> {

    public MixpanelBasicTest(){
        super("com.mixpanel.android.hellomixpanel", HelloMixpanel.class);
    }

    @Override
    protected void setUp() throws Exception {
      super.setUp();

      setActivityInitialTouchMode(false);

      mActivity = getActivity();
    } // end of setUp() method definition

    public void testTrivialRunning() {
        assertTrue(mActivity != null);
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

    public void testMessageQueuing() {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<String>();

        final MPDbAdapter mockAdapter = new MPDbAdapter(mActivity) {
            @Override
            public int addJSON(JSONObject message, String table) {

                try {
                    messages.put("TABLE " + table);
                    messages.put(message.toString());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                return super.addJSON(message, table);
            }
        };
        mockAdapter.cleanupEvents(Long.MAX_VALUE, "events");
        mockAdapter.cleanupEvents(Long.MAX_VALUE, "people");

        final HttpPoster mockPoster = new HttpPoster() {
            @Override
            public boolean postData(String rawMessage, String endpointUrl) {
                try {
                    messages.put("SENT FLUSH " + endpointUrl);
                    messages.put(rawMessage);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                return true;
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
                assertEquals("TABLE " + MPDbAdapter.EVENTS_TABLE, messageTable);

                expectedJSONMessage = messages.poll(1, TimeUnit.SECONDS);
                JSONObject message = new JSONObject(expectedJSONMessage);
                assertEquals("frequent event", message.getString("event"));
            }

            String messageTable = messages.poll(1, TimeUnit.SECONDS);
            assertEquals("TABLE " + MPDbAdapter.EVENTS_TABLE, messageTable);

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
            assertEquals("TABLE " + MPDbAdapter.EVENTS_TABLE, nextWaveTable);

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
            assertEquals("TABLE " + MPDbAdapter.PEOPLE_TABLE, peopleTable);

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


    private HelloMixpanel mActivity;
}
