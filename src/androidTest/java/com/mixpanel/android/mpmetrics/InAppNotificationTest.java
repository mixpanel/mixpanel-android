package com.mixpanel.android.mpmetrics;

import android.test.AndroidTestCase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class InAppNotificationTest extends AndroidTestCase {
    public void testMatchesEventDescription() throws BadDecideObjectException, JSONException, ParseException {
        JSONArray displayTriggers = new JSONArray();
        displayTriggers.put(new JSONObject("{\"event\": \"test_event_1\", \"selector\": " +
                "{\"operator\": \">\", \"children\": [{\"operator\": \"datetime\", \"children\": [" +
                "{\"property\": \"event\", \"value\": \"prop1\"}]}," +
                "{\"property\": \"literal\", \"value\": {\"window\": {\"value\": 1, \"unit\": \"hour\"}}}" +
                "]}}"));
        displayTriggers.put(new JSONObject("{\"event\": \"test_event_2\", \"selector\": " +
                "{\"operator\": \"==\", \"children\": [{\"operator\": \"string\", \"children\": [" +
                "{\"property\": \"event\", \"value\": \"city\"}]}," +
                "{\"property\": \"literal\", \"value\": \"San Francisco\"}" +
                "]}}"));
        JSONObject obj = new JSONObject();
        obj.put("extras", new JSONObject("{\"image_fade\": true}"));
        obj.put("id", 1);
        obj.put("message_id", 1);
        obj.put("bg_color", 1);
        obj.put("body_color", 1);
        obj.put("image_url", "https://www.test.com/test.jpg");
        obj.put("display_triggers", displayTriggers);
        obj.put("buttons", new JSONArray());
        obj.put("close_color", 1);
        obj.put("title_color", 1);
        final InAppNotification notif = new TakeoverInAppNotification(obj);

        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        final Date dt = format.parse("2019-01-02T12:00:00");
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(dt);
        SelectorEvaluator.setCalendar(calendar, true);

        obj = new JSONObject();

        assertFalse(notif.matchesEventDescription(new AnalyticsMessages.EventDescription("test_event_1", obj, "test")));
        obj.put("city", "San Francisco");
        assertFalse(notif.matchesEventDescription(new AnalyticsMessages.EventDescription("test_event_1", obj, "test")));
        obj.put("prop1", "2019-01-02T10:59:59");
        assertFalse(notif.matchesEventDescription(new AnalyticsMessages.EventDescription("test_event_1", obj, "test")));
        obj.put("prop1", "2019-01-02T11:00:00");
        assertFalse(notif.matchesEventDescription(new AnalyticsMessages.EventDescription("test_event_1", obj, "test")));
        obj.put("prop1", "2019-01-02T11:00:01");
        assertTrue(notif.matchesEventDescription(new AnalyticsMessages.EventDescription("test_event_1", obj, "test")));

        obj = new JSONObject();
        assertFalse(notif.matchesEventDescription(new AnalyticsMessages.EventDescription("test_event_2", obj, "test")));
        obj.put("city", "Los Angeles");
        assertFalse(notif.matchesEventDescription(new AnalyticsMessages.EventDescription("test_event_2", obj, "test")));
        obj.put("prop1", "2019-01-02T11:00:01");
        assertFalse(notif.matchesEventDescription(new AnalyticsMessages.EventDescription("test_event_2", obj, "test")));
        obj.put("city", "San Francisco");
        assertTrue(notif.matchesEventDescription(new AnalyticsMessages.EventDescription("test_event_2", obj, "test")));
    }
}
