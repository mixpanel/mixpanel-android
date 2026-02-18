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
    public void testGetMetadataForEventContainsRequiredKeys() {
        JSONObject metadata = mSessionMetadata.getMetadataForEvent();
        assertTrue(metadata.has("$mp_event_id"));
        assertTrue(metadata.has("$mp_session_id"));
        assertTrue(metadata.has("$mp_session_seq_id"));
        assertTrue(metadata.has("$mp_session_start_sec"));
    }

    @Test
    public void testGetMetadataForPeopleContainsRequiredKeys() {
        JSONObject metadata = mSessionMetadata.getMetadataForPeople();
        assertTrue(metadata.has("$mp_event_id"));
        assertTrue(metadata.has("$mp_session_id"));
        assertTrue(metadata.has("$mp_session_seq_id"));
        assertTrue(metadata.has("$mp_session_start_sec"));
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
}
