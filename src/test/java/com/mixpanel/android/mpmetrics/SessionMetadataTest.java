package com.mixpanel.android.mpmetrics;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SessionMetadataTest {

    private SessionMetadata mSessionMetadata;

    @Before
    public void setUp() {
        mSessionMetadata = new SessionMetadata();
    }

    @Test
    public void testGetMetadataForEventContainsValidFields() throws Exception {
        JSONObject metadata = mSessionMetadata.getMetadataForEvent();

        // $mp_event_id should be a valid hex string from Long.toHexString()
        String eventId = metadata.getString("$mp_event_id");
        assertValidHexString(eventId, "$mp_event_id");

        // $mp_session_id should be a valid hex string
        String sessionId = metadata.getString("$mp_session_id");
        assertValidHexString(sessionId, "$mp_session_id");

        // $mp_session_seq_id should start at 0
        assertEquals(0, metadata.getLong("$mp_session_seq_id"));

        // $mp_session_start_sec should be a recent epoch timestamp
        long startSec = metadata.getLong("$mp_session_start_sec");
        long nowSec = System.currentTimeMillis() / 1000;
        assertTrue("$mp_session_start_sec should be recent", startSec <= nowSec && startSec >= nowSec - 1);
    }

    @Test
    public void testGetMetadataForPeopleContainsValidFields() throws Exception {
        JSONObject metadata = mSessionMetadata.getMetadataForPeople();

        String eventId = metadata.getString("$mp_event_id");
        assertValidHexString(eventId, "$mp_event_id");

        String sessionId = metadata.getString("$mp_session_id");
        assertValidHexString(sessionId, "$mp_session_id");

        assertEquals(0, metadata.getLong("$mp_session_seq_id"));

        long startSec = metadata.getLong("$mp_session_start_sec");
        long nowSec = System.currentTimeMillis() / 1000;
        assertTrue("$mp_session_start_sec should be recent", startSec <= nowSec && startSec >= nowSec - 1);
    }

    @Test
    public void testEventCounterIncrements() throws Exception {
        JSONObject first = mSessionMetadata.getMetadataForEvent();
        JSONObject second = mSessionMetadata.getMetadataForEvent();
        JSONObject third = mSessionMetadata.getMetadataForEvent();

        assertEquals(0, first.getLong("$mp_session_seq_id"));
        assertEquals(1, second.getLong("$mp_session_seq_id"));
        assertEquals(2, third.getLong("$mp_session_seq_id"));
    }

    @Test
    public void testPeopleCounterIncrements() throws Exception {
        JSONObject first = mSessionMetadata.getMetadataForPeople();
        JSONObject second = mSessionMetadata.getMetadataForPeople();

        assertEquals(0, first.getLong("$mp_session_seq_id"));
        assertEquals(1, second.getLong("$mp_session_seq_id"));
    }

    @Test
    public void testEventAndPeopleCountersAreIndependent() throws Exception {
        JSONObject event1 = mSessionMetadata.getMetadataForEvent();
        JSONObject event2 = mSessionMetadata.getMetadataForEvent();
        JSONObject people1 = mSessionMetadata.getMetadataForPeople();
        JSONObject event3 = mSessionMetadata.getMetadataForEvent();
        JSONObject people2 = mSessionMetadata.getMetadataForPeople();

        assertEquals(0, event1.getLong("$mp_session_seq_id"));
        assertEquals(1, event2.getLong("$mp_session_seq_id"));
        assertEquals(0, people1.getLong("$mp_session_seq_id"));
        assertEquals(2, event3.getLong("$mp_session_seq_id"));
        assertEquals(1, people2.getLong("$mp_session_seq_id"));
    }

    @Test
    public void testSessionIdConsistentAcrossCalls() throws Exception {
        JSONObject event = mSessionMetadata.getMetadataForEvent();
        JSONObject people = mSessionMetadata.getMetadataForPeople();

        assertEquals(
                event.getString("$mp_session_id"),
                people.getString("$mp_session_id")
        );
    }

    @Test
    public void testEventIdUnique() throws Exception {
        JSONObject first = mSessionMetadata.getMetadataForEvent();
        JSONObject second = mSessionMetadata.getMetadataForEvent();

        assertNotEquals(
                first.getString("$mp_event_id"),
                second.getString("$mp_event_id")
        );
    }

    @Test
    public void testSessionStartEpochIsReasonable() throws Exception {
        long beforeSec = System.currentTimeMillis() / 1000;
        SessionMetadata sm = new SessionMetadata();
        long afterSec = System.currentTimeMillis() / 1000;

        JSONObject metadata = sm.getMetadataForEvent();
        long startSec = metadata.getLong("$mp_session_start_sec");

        assertTrue(startSec >= beforeSec);
        assertTrue(startSec <= afterSec);
    }

    @Test
    public void testInitSessionResets() throws Exception {
        mSessionMetadata.getMetadataForEvent();
        mSessionMetadata.getMetadataForEvent();

        String oldSessionId = mSessionMetadata.getMetadataForEvent().getString("$mp_session_id");

        mSessionMetadata.initSession();

        JSONObject afterReset = mSessionMetadata.getMetadataForEvent();
        assertEquals(0, afterReset.getLong("$mp_session_seq_id"));
        // Session ID should change after init
        assertNotEquals(oldSessionId, afterReset.getString("$mp_session_id"));
    }

    @Test
    public void testGetMetadataForPeopleIncrementsIndependently() throws Exception {
        // Exercise the isEvent=false branch of getNewMetadata explicitly
        // ensuring mPeopleCounter (not mEventsCounter) is used and incremented
        JSONObject people1 = mSessionMetadata.getMetadataForPeople();
        JSONObject people2 = mSessionMetadata.getMetadataForPeople();
        JSONObject people3 = mSessionMetadata.getMetadataForPeople();

        assertEquals(0, people1.getLong("$mp_session_seq_id"));
        assertEquals(1, people2.getLong("$mp_session_seq_id"));
        assertEquals(2, people3.getLong("$mp_session_seq_id"));

        // Verify event counter was not affected
        JSONObject event1 = mSessionMetadata.getMetadataForEvent();
        assertEquals(0, event1.getLong("$mp_session_seq_id"));
    }

    @Test
    public void testEventIdIsUniqueHexPerCall() throws Exception {
        JSONObject first = mSessionMetadata.getMetadataForEvent();
        JSONObject second = mSessionMetadata.getMetadataForEvent();

        String id1 = first.getString("$mp_event_id");
        String id2 = second.getString("$mp_event_id");

        assertValidHexString(id1, "first $mp_event_id");
        assertValidHexString(id2, "second $mp_event_id");
        assertNotEquals("Each event should get a unique ID", id1, id2);
    }

    @Test
    public void testSessionIdIsConsistentHex() throws Exception {
        JSONObject event = mSessionMetadata.getMetadataForEvent();
        JSONObject people = mSessionMetadata.getMetadataForPeople();

        String sessionFromEvent = event.getString("$mp_session_id");
        String sessionFromPeople = people.getString("$mp_session_id");

        assertValidHexString(sessionFromEvent, "$mp_session_id from event");
        assertEquals("Session ID should be the same across event and people calls",
                sessionFromEvent, sessionFromPeople);
    }

    private static void assertValidHexString(String value, String fieldName) {
        assertNotNull(fieldName + " should not be null", value);
        assertFalse(fieldName + " should not be empty", value.isEmpty());
        assertTrue(fieldName + " should be a valid hex string, got: " + value,
                value.matches("[0-9a-f]+"));
    }
}
