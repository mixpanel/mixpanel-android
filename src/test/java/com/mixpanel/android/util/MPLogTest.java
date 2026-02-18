package com.mixpanel.android.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class MPLogTest {

    private int savedLevel;

    @Before
    public void setUp() {
        savedLevel = MPLog.getLevel();
        ShadowLog.reset();
    }

    @After
    public void tearDown() {
        MPLog.setLevel(savedLevel);
    }

    @Test
    public void testDefaultLevel() {
        // Reset to default by creating fresh state
        MPLog.setLevel(MPLog.WARN);
        assertEquals(MPLog.WARN, MPLog.getLevel());
    }

    @Test
    public void testConstants() {
        assertEquals(2, MPLog.VERBOSE);
        assertEquals(3, MPLog.DEBUG);
        assertEquals(4, MPLog.INFO);
        assertEquals(5, MPLog.WARN);
        assertEquals(6, MPLog.ERROR);
        assertEquals(Integer.MAX_VALUE, MPLog.NONE);
    }

    @Test
    public void testSetAndGetLevel() {
        MPLog.setLevel(MPLog.VERBOSE);
        assertEquals(MPLog.VERBOSE, MPLog.getLevel());

        MPLog.setLevel(MPLog.DEBUG);
        assertEquals(MPLog.DEBUG, MPLog.getLevel());

        MPLog.setLevel(MPLog.INFO);
        assertEquals(MPLog.INFO, MPLog.getLevel());

        MPLog.setLevel(MPLog.ERROR);
        assertEquals(MPLog.ERROR, MPLog.getLevel());

        MPLog.setLevel(MPLog.NONE);
        assertEquals(MPLog.NONE, MPLog.getLevel());
    }

    @Test
    public void testVerboseLogging() {
        MPLog.setLevel(MPLog.VERBOSE);
        ShadowLog.reset();
        MPLog.v("TestTag", "verbose message");
        assertFalse("Verbose message should appear in logs", ShadowLog.getLogs().isEmpty());
        assertEquals("TestTag", ShadowLog.getLogs().get(0).tag);
        assertEquals("verbose message", ShadowLog.getLogs().get(0).msg);
    }

    @Test
    public void testDebugLogging() {
        MPLog.setLevel(MPLog.VERBOSE);
        ShadowLog.reset();
        MPLog.d("TestTag", "debug message");
        assertFalse("Debug message should appear in logs", ShadowLog.getLogs().isEmpty());
        assertEquals("TestTag", ShadowLog.getLogs().get(0).tag);
        assertEquals("debug message", ShadowLog.getLogs().get(0).msg);
    }

    @Test
    public void testInfoLogging() {
        MPLog.setLevel(MPLog.VERBOSE);
        ShadowLog.reset();
        MPLog.i("TestTag", "info message");
        assertFalse("Info message should appear in logs", ShadowLog.getLogs().isEmpty());
        assertEquals("TestTag", ShadowLog.getLogs().get(0).tag);
        assertEquals("info message", ShadowLog.getLogs().get(0).msg);
    }

    @Test
    public void testWarnLogging() {
        MPLog.setLevel(MPLog.VERBOSE);
        ShadowLog.reset();
        MPLog.w("TestTag", "warn message");
        assertFalse("Warn message should appear in logs", ShadowLog.getLogs().isEmpty());
        assertEquals("TestTag", ShadowLog.getLogs().get(0).tag);
        assertEquals("warn message", ShadowLog.getLogs().get(0).msg);
    }

    @Test
    public void testErrorLogging() {
        MPLog.setLevel(MPLog.VERBOSE);
        ShadowLog.reset();
        MPLog.e("TestTag", "error message");
        assertFalse("Error message should appear in logs", ShadowLog.getLogs().isEmpty());
        assertEquals("TestTag", ShadowLog.getLogs().get(0).tag);
        assertEquals("error message", ShadowLog.getLogs().get(0).msg);
    }

    @Test
    public void testLevelFiltering() {
        MPLog.setLevel(MPLog.ERROR);
        ShadowLog.reset();
        MPLog.v("TestTag", "should be filtered");
        MPLog.d("TestTag", "should be filtered");
        MPLog.i("TestTag", "should be filtered");
        MPLog.w("TestTag", "should be filtered");
        assertTrue("Filtered messages should not appear", ShadowLog.getLogs().isEmpty());
        MPLog.e("TestTag", "should pass");
        assertFalse("ERROR should pass through", ShadowLog.getLogs().isEmpty());
        assertEquals("TestTag", ShadowLog.getLogs().get(0).tag);
    }

    @Test
    public void testNoneLevel() {
        MPLog.setLevel(MPLog.NONE);
        ShadowLog.reset();
        // All methods should be filtered
        MPLog.v("TestTag", "filtered");
        MPLog.d("TestTag", "filtered");
        MPLog.i("TestTag", "filtered");
        MPLog.w("TestTag", "filtered");
        MPLog.e("TestTag", "filtered");
        assertTrue("All messages should be filtered at NONE level", ShadowLog.getLogs().isEmpty());
    }
}
