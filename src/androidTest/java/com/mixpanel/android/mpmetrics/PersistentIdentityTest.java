package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.AndroidTestCase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

public class PersistentIdentityTest extends AndroidTestCase {
    public void setUp() {
        SharedPreferences referrerPrefs = getContext().getSharedPreferences(TEST_REFERRER_PREFERENCES, Context.MODE_PRIVATE);
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
        Future<SharedPreferences> referrerLoader = loader.loadPreferences(getContext(), TEST_REFERRER_PREFERENCES, null);
        Future<SharedPreferences> testLoader = loader.loadPreferences(getContext(), TEST_PREFERENCES, null);

        mPersistentIdentity = new PersistentIdentity(referrerLoader, testLoader);
    }

    public void testStaticWaitingPeopleRecordsWithId() throws JSONException {
        SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        JSONArray records = PersistentIdentity.waitingPeopleRecordsForSending(testPreferences);
        assertEquals(records.length(), 2);
        for (int i = 0; i < records.length(); i++) {
            JSONObject obj = records.getJSONObject(i);
            assertTrue(obj.has("thing"));
            assertEquals(obj.getString("$distinct_id"), "PEOPLE DISTINCT ID");
        }
        JSONArray unseenRecords = PersistentIdentity.waitingPeopleRecordsForSending(testPreferences);
        assertNull(unseenRecords);
    }

    public void testStaticWaitingPeopleRecordsNoId() {
        SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        testPreferences.edit().remove("people_distinct_id").commit();
        JSONArray records = PersistentIdentity.waitingPeopleRecordsForSending(testPreferences);
        assertNull(records);
    }

    public void testStaticWaitingPeopleRecordsNoRecords() {
        SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        testPreferences.edit().remove("waiting_array").commit();
        JSONArray records = PersistentIdentity.waitingPeopleRecordsForSending(testPreferences);
        assertNull(records);
    }

    public void testWaitingPeopleRecordsWithId() throws JSONException {
        JSONArray records = mPersistentIdentity.waitingPeopleRecordsForSending();
        assertEquals(records.length(), 2);
        for (int i = 0; i < records.length(); i++) {
            JSONObject obj = records.getJSONObject(i);
            assertTrue(obj.has("thing"));
            assertEquals(obj.getString("$distinct_id"), "PEOPLE DISTINCT ID");
        }
        JSONArray unseenRecords = mPersistentIdentity.waitingPeopleRecordsForSending();
        assertNull(unseenRecords);
    }

    public void testWaitingPeopleRecordsWithNoId() throws JSONException {
        SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        testPreferences.edit().remove("people_distinct_id").commit();
        JSONArray unseenRecords = mPersistentIdentity.waitingPeopleRecordsForSending();
        assertNull(unseenRecords);
    }

    public void testWaitingPeopleRecordsWithNoRecords() throws JSONException {
        SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        testPreferences.edit().remove("waiting_array").commit();
        JSONArray unseenRecords = mPersistentIdentity.waitingPeopleRecordsForSending();
        assertNull(unseenRecords);
    }

    public void testStoreWaitingPeopleRecord() throws JSONException {
        mPersistentIdentity.storeWaitingPeopleRecord(new JSONObject("{\"new1\": 1}"));
        mPersistentIdentity.storeWaitingPeopleRecord(new JSONObject("{\"new2\": 2}"));

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
        final Map<String, String> props = mPersistentIdentity.getReferrerProperties();
        assertEquals("REFERRER", props.get("referrer"));
        assertEquals("SOURCE VALUE", props.get("utm_source"));
        assertEquals("MEDIUM VALUE", props.get("utm_medium"));
        assertEquals("CAMPAIGN NAME VALUE", props.get("utm_campaign"));
        assertEquals("CONTENT VALUE", props.get("utm_content"));
        assertEquals("TERM VALUE", props.get("utm_term"));

        final Map<String, String> newPrefs = new HashMap<String, String>();
        newPrefs.put("referrer", "BJORK");
        newPrefs.put("mystery", "BOO!");
        newPrefs.put("utm_term", "NEW TERM");
        PersistentIdentity.writeReferrerPrefs(getContext(), TEST_REFERRER_PREFERENCES, newPrefs);

        final Map<String, String> propsAfterChange = mPersistentIdentity.getReferrerProperties();
        assertFalse(propsAfterChange.containsKey("utm_medium"));
        assertFalse(propsAfterChange.containsKey("utm_source"));
        assertFalse(propsAfterChange.containsKey("utm_campaign"));
        assertFalse(propsAfterChange.containsKey("utm_content"));
        assertEquals("BJORK", propsAfterChange.get("referrer"));
        assertEquals("NEW TERM", propsAfterChange.get("utm_term"));
        assertEquals("BOO!", propsAfterChange.get("mystery"));
    }

    public void testUnsetEventsId() {
        final SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        testPreferences.edit().clear().commit();
        final String eventsId = mPersistentIdentity.getEventsDistinctId();
        assertTrue(Pattern.matches("^[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}$", eventsId));

        final String autoId = testPreferences.getString("events_distinct_id", "NOPE");
        assertEquals(autoId, eventsId);

        mPersistentIdentity.setEventsDistinctId("TEST ID TO SET");
        final String heardId = mPersistentIdentity.getEventsDistinctId();
        assertEquals("TEST ID TO SET", heardId);

        final String storedId = testPreferences.getString("events_distinct_id", "NOPE");
        assertEquals("TEST ID TO SET", storedId);
    }

    public void testUnsetPeopleId() {
        final SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        testPreferences.edit().clear().commit();
        final String peopleId = mPersistentIdentity.getPeopleDistinctId();
        assertNull(peopleId);

        mPersistentIdentity.setPeopleDistinctId("TEST ID TO SET");
        final String heardId = mPersistentIdentity.getPeopleDistinctId();
        assertEquals("TEST ID TO SET", heardId);

        final String storedId = testPreferences.getString("people_distinct_id", "NOPE");
        assertEquals("TEST ID TO SET", storedId);
    }

    public void testPushId() {
        final String pushId = mPersistentIdentity.getPushId();
        assertEquals("PUSH ID", pushId);

        mPersistentIdentity.clearPushId();
        final String noId = mPersistentIdentity.getPushId();
        assertNull(noId);

        mPersistentIdentity.storePushId("STORED PUSH ID");
        final String storedId = mPersistentIdentity.getPushId();
        assertEquals("STORED PUSH ID", storedId);

        final SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        assertEquals("STORED PUSH ID", testPreferences.getString("push_id", "FAIL"));
    }

    private PersistentIdentity mPersistentIdentity;
    private static final String TEST_PREFERENCES = "TEST PERSISTENT PROPERTIES PREFS";
    private static final String TEST_REFERRER_PREFERENCES  = "TEST REFERRER PREFS";
}
