package com.mixpanel.android.mpmetrics;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class FlagsConfigTest {

    @Test
    public void testDefaultConstructor() {
        FlagsConfig config = new FlagsConfig();
        assertFalse(config.enabled);
        assertNotNull(config.context);
        assertEquals(0, config.context.length());
    }

    @Test
    public void testEnabledConstructor() {
        FlagsConfig config = new FlagsConfig(true);
        assertTrue(config.enabled);
        assertNotNull(config.context);
        assertEquals(0, config.context.length());
    }

    @Test
    public void testDisabledConstructor() {
        FlagsConfig config = new FlagsConfig(false);
        assertFalse(config.enabled);
    }

    @Test
    public void testFullConstructor() throws Exception {
        JSONObject context = new JSONObject();
        context.put("user_type", "beta");
        context.put("version", 2);

        FlagsConfig config = new FlagsConfig(true, context);
        assertTrue(config.enabled);
        assertNotNull(config.context);
        assertEquals("beta", config.context.getString("user_type"));
        assertEquals(2, config.context.getInt("version"));
    }

    @Test
    public void testFullConstructorWithEmptyContext() {
        FlagsConfig config = new FlagsConfig(true, new JSONObject());
        assertTrue(config.enabled);
        assertEquals(0, config.context.length());
    }
}
