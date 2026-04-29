package com.mixpanel.android.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class MPConstantsTest {

    @Test
    public void testURLConstants() {
        assertEquals("api.mixpanel.com", MPConstants.URL.DEFAULT_SERVER_HOST);
        assertEquals("https://api.mixpanel.com", MPConstants.URL.MIXPANEL_API);
        assertEquals("/track/", MPConstants.URL.EVENT);
        assertEquals("/engage/", MPConstants.URL.PEOPLE);
        assertEquals("/groups/", MPConstants.URL.GROUPS);
        assertEquals("/flags/", MPConstants.URL.FLAGS);
    }

    @Test
    public void testFlagsConstants() {
        assertEquals("flags", MPConstants.Flags.FLAGS_KEY);
        assertEquals("variant_key", MPConstants.Flags.VARIANT_KEY);
        assertEquals("variant_value", MPConstants.Flags.VARIANT_VALUE);
        assertEquals("experiment_id", MPConstants.Flags.EXPERIMENT_ID);
        assertEquals("is_experiment_active", MPConstants.Flags.IS_EXPERIMENT_ACTIVE);
        assertEquals("is_qa_tester", MPConstants.Flags.IS_QA_TESTER);
    }

    @Test
    public void testSessionReplayConstants() {
        assertEquals("com.mixpanel.properties.register", MPConstants.SessionReplay.REGISTER_ACTION);
        assertEquals("com.mixpanel.properties.unregister", MPConstants.SessionReplay.UNREGISTER_ACTION);
        assertEquals("$mp_replay_id", MPConstants.SessionReplay.REPLAY_ID_KEY);
    }
}
