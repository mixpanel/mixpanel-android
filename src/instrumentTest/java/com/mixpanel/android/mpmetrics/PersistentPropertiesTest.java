package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.AndroidTestCase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

public class PersistentPropertiesTest extends AndroidTestCase {
    public void setUp() {
        SharedPreferences referrerPrefs = getContext().getSharedPreferences(MPConfig.REFERRER_PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor referrerEditor = referrerPrefs.edit();
        referrerEditor.clear();
        referrerEditor.putString("referrer", "REFERRER");
        referrerEditor.putString("utm_source", "SOURCE VALUE");
        referrerEditor.putString("utm_medium", "MEDIUM VALUE");
        referrerEditor.putString("utm_campaign", "CAMPAIGN NAME VALUE");
        referrerEditor.putString("utm_content", "CONTENT VALUE");
        referrerEditor.putString("utm_term", "TERM VALUE");
        referrerEditor.commit();

        SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = testPreferences.edit();
        prefsEditor.clear();
        prefsEditor.putString("events_distinct_id", "EVENTS DISTINCT ID");
        prefsEditor.putString("people_distinct_id", "PEOPLE DISTINCT ID");
        prefsEditor.putString("push_id", "PUSH ID");
        prefsEditor.putString("waiting_array", "[ {\"thing\": 1}, {\"thing\": 2} ]");
        prefsEditor.putString("super_properties", "{\"thing\": \"superprops\"}");
        prefsEditor.commit();

        SharedPreferencesLoader loader = new SharedPreferencesLoader();
        Future<SharedPreferences> referrerLoader = loader.loadPreferences(getContext(), MPConfig.REFERRER_PREFS_NAME, null);
        Future<SharedPreferences> testLoader = loader.loadPreferences(getContext(), TEST_PREFERENCES, null);

        mPersistentProperties = new PersistentProperties(referrerLoader, testLoader);
    }

    public void testStaticWaitingPeopleRecordsWithId() throws JSONException {
        SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        JSONArray records = PersistentProperties.waitingPeopleRecordsForSending(testPreferences);
        assertEquals(records.length(), 2);
        for (int i = 0; i < records.length(); i++) {
            JSONObject obj = records.getJSONObject(i);
            assertTrue(obj.has("thing"));
            assertEquals(obj.getString("$distinct_id"), "PEOPLE DISTINCT ID");
        }
        JSONArray unseenRecords = PersistentProperties.waitingPeopleRecordsForSending(testPreferences);
        assertNull(unseenRecords);
    }

    public void testStaticWaitingPeopleRecordsNoId() {
        SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        testPreferences.edit().remove("people_distinct_id").commit();
        JSONArray records = PersistentProperties.waitingPeopleRecordsForSending(testPreferences);
        assertNull(records);
    }

    public void testStaticWaitingPeopleRecordsNoRecords() {
        SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        testPreferences.edit().remove("waiting_array").commit();
        JSONArray records = PersistentProperties.waitingPeopleRecordsForSending(testPreferences);
        assertNull(records);
    }

    public void testWaitingPeopleRecordsWithId() throws JSONException {
        JSONArray records = mPersistentProperties.waitingPeopleRecordsForSending();
        assertEquals(records.length(), 2);
        for (int i = 0; i < records.length(); i++) {
            JSONObject obj = records.getJSONObject(i);
            assertTrue(obj.has("thing"));
            assertEquals(obj.getString("$distinct_id"), "PEOPLE DISTINCT ID");
        }
        JSONArray unseenRecords = mPersistentProperties.waitingPeopleRecordsForSending();
        assertNull(unseenRecords);
    }

    public void testWaitingPeopleRecordsWithNoId() throws JSONException {
        SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        testPreferences.edit().remove("people_distinct_id").commit();
        JSONArray unseenRecords = mPersistentProperties.waitingPeopleRecordsForSending();
        assertNull(unseenRecords);
    }

    public void testWaitingPeopleRecordsWithNoRecords() throws JSONException {
        SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        testPreferences.edit().remove("waiting_array").commit();
        JSONArray unseenRecords = mPersistentProperties.waitingPeopleRecordsForSending();
        assertNull(unseenRecords);
    }

    public void testStoreWaitingPeopleRecord() throws JSONException {
        mPersistentProperties.storeWaitingPeopleRecord(new JSONObject("{\"new1\": 1}"));
        mPersistentProperties.storeWaitingPeopleRecord(new JSONObject("{\"new2\": 2}"));

        SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        String waitingString = testPreferences.getString("waiting_array", "FAIL");
        JSONArray waitingArray = new JSONArray(waitingString);
        assertEquals(waitingArray.length(), 4);
        JSONObject new1 = waitingArray.getJSONObject(2);
        assertEquals(1, new1.getInt("new1"));
        JSONObject new2 = waitingArray.getJSONObject(3);
        assertEquals(2, new2.getInt("new2"));
    }

    public void testReferrerProperties() {
        final Map<String, String> props = mPersistentProperties.getReferrerProperties();
        assertEquals("REFERRER", props.get("referrer"));
        assertEquals("SOURCE VALUE", props.get("utm_source"));
        assertEquals("MEDIUM VALUE", props.get("utm_medium"));
        assertEquals("CAMPAIGN NAME VALUE", props.get("utm_campaign"));
        assertEquals("CONTENT VALUE", props.get("utm_content"));
        assertEquals("TERM VALUE", props.get("utm_term"));

        final SharedPreferences referrerPrefs = getContext().getSharedPreferences(MPConfig.REFERRER_PREFS_NAME, Context.MODE_PRIVATE);
        final SharedPreferences.Editor referrerEditor = referrerPrefs.edit();
        referrerEditor.putString("referrer", "BJORK");
        referrerEditor.putString("mystery", "BOO!");
        referrerEditor.remove("utm_medium");
        referrerEditor.commit();

        final Map<String, String> propsAfterChange = mPersistentProperties.getReferrerProperties();
        assertFalse(propsAfterChange.containsKey("utm_medium"));
        assertEquals("BJORK", propsAfterChange.get("referrer"));
        assertEquals("SOURCE VALUE", propsAfterChange.get("utm_source"));
        assertEquals("CAMPAIGN NAME VALUE", propsAfterChange.get("utm_campaign"));
        assertEquals("CONTENT VALUE", propsAfterChange.get("utm_content"));
        assertEquals("TERM VALUE", propsAfterChange.get("utm_term"));
        assertEquals("BOO!", propsAfterChange.get("mystery"));
    }

    public void testUnsetEventsId() {
        final SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        testPreferences.edit().clear().commit();
        final String eventsId = mPersistentProperties.getEventsDistinctId();
        assertTrue(Pattern.matches("^[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}$", eventsId));

        final String autoId = testPreferences.getString("events_distinct_id", "NOPE");
        assertEquals(autoId, eventsId);

        mPersistentProperties.setEventsDistinctId("TEST ID TO SET");
        final String heardId = mPersistentProperties.getEventsDistinctId();
        assertEquals("TEST ID TO SET", heardId);

        final String storedId = testPreferences.getString("events_distinct_id", "NOPE");
        assertEquals("TEST ID TO SET", storedId);
    }

    public void testUnsetPeopleId() {
        final SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        testPreferences.edit().clear().commit();
        final String peopleId = mPersistentProperties.getPeopleDistinctId();
        assertNull(peopleId);

        mPersistentProperties.setPeopleDistinctId("TEST ID TO SET");
        final String heardId = mPersistentProperties.getPeopleDistinctId();
        assertEquals("TEST ID TO SET", heardId);

        final String storedId = testPreferences.getString("people_distinct_id", "NOPE");
        assertEquals("TEST ID TO SET", storedId);
    }

    public void testPushId() {
        final String pushId = mPersistentProperties.getPushId();
        assertEquals("PUSH ID", pushId);

        mPersistentProperties.clearPushId();
        final String noId = mPersistentProperties.getPushId();
        assertNull(noId);

        mPersistentProperties.storePushId("STORED PUSH ID");
        final String storedId = mPersistentProperties.getPushId();
        assertEquals("STORED PUSH ID", storedId);

        final SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        assertEquals("STORED PUSH ID", testPreferences.getString("push_id", "FAIL"));
    }

    private PersistentProperties mPersistentProperties;
    private static final String TEST_PREFERENCES = "TEST PERSISTENT PROPERTIES PREFS";

}
