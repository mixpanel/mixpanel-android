package com.mixpanel.android.mpmetrics;

import org.junit.Test;

import static org.junit.Assert.*;

public class AutomaticEventsTest {

    @Test
    public void testEventConstants() {
        assertEquals("$ae_first_open", AutomaticEvents.FIRST_OPEN);
        assertEquals("$ae_session", AutomaticEvents.SESSION);
        assertEquals("$ae_session_length", AutomaticEvents.SESSION_LENGTH);
        assertEquals("$ae_total_app_sessions", AutomaticEvents.TOTAL_SESSIONS);
        assertEquals("$ae_total_app_session_length", AutomaticEvents.TOTAL_SESSIONS_LENGTH);
        assertEquals("$ae_updated", AutomaticEvents.APP_UPDATED);
        assertEquals("$ae_updated_version", AutomaticEvents.VERSION_UPDATED);
        assertEquals("$ae_crashed", AutomaticEvents.APP_CRASHED);
        assertEquals("$ae_crashed_reason", AutomaticEvents.APP_CRASHED_REASON);
    }
}
