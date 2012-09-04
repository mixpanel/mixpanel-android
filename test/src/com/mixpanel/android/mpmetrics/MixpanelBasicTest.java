package com.mixpanel.android.mpmetrics;


import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;

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

    @UiThreadTest
    public void testIdentifyAfterSet() {
        final List<JSONObject> messages = new ArrayList<JSONObject>();

        final AnalyticsMessages listener = new AnalyticsMessages(mActivity) {
            @Override
            public void peopleMessage(JSONObject heard) {
                messages.add(heard);
            }
        };

        MPMetrics mixpanel = new CleanableMPMetrics(mActivity, "TEST TOKEN testIdentifyAfterSet") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return listener;
            }
        };

        mixpanel.clearPreferences();
        MPMetrics.People people = mixpanel.getPeople();

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

    @UiThreadTest
    public void testPersistence() {
        MPMetrics metricsOne = new MPMetrics(mActivity, "SAME TOKEN");
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

        MPMetrics metricsTwo = new MPMetrics(mActivity, "SAME TOKEN") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                 return listener;
            }
        };
        metricsTwo.track("eventname", null);
        metricsTwo.getPeople().set("people prop name", "Indeed");

        assertEquals(2, messages.size());

        JSONObject eventMessage = messages.get(0);
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

    private class CleanableMPMetrics extends MPMetrics {
        public CleanableMPMetrics(Context context, String token) {
            super(context, token);
        }

        @Override
        public void clearPreferences() {
            super.clearPreferences();
        }
    }

    private HelloMixpanel mActivity;
}
