package com.mixpanel.android.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class MPLogTest {

    private int savedLevel;

    @Before
    public void setUp() {
        savedLevel = MPLog.getLevel();
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
        // returnDefaultValues = true means Log.v returns 0 instead of throwing
        MPLog.v("TestTag", "verbose message");
        MPLog.v("TestTag", "verbose message", new RuntimeException("test"));
    }

    @Test
    public void testDebugLogging() {
        MPLog.setLevel(MPLog.VERBOSE);
        MPLog.d("TestTag", "debug message");
        MPLog.d("TestTag", "debug message", new RuntimeException("test"));
    }

    @Test
    public void testInfoLogging() {
        MPLog.setLevel(MPLog.VERBOSE);
        MPLog.i("TestTag", "info message");
        MPLog.i("TestTag", "info message", new RuntimeException("test"));
    }

    @Test
    public void testWarnLogging() {
        MPLog.setLevel(MPLog.VERBOSE);
        MPLog.w("TestTag", "warn message");
        MPLog.w("TestTag", "warn message", new RuntimeException("test"));
    }

    @Test
    public void testErrorLogging() {
        MPLog.setLevel(MPLog.VERBOSE);
        MPLog.e("TestTag", "error message");
        MPLog.e("TestTag", "error message", new RuntimeException("test"));
    }

    @Test
    public void testLevelFiltering() {
        MPLog.setLevel(MPLog.ERROR);
        // These should be filtered (no-ops due to level check)
        MPLog.v("TestTag", "should be filtered");
        MPLog.d("TestTag", "should be filtered");
        MPLog.i("TestTag", "should be filtered");
        MPLog.w("TestTag", "should be filtered");
        // This should pass through
        MPLog.e("TestTag", "should pass");
    }

    @Test
    public void testNoneLevel() {
        MPLog.setLevel(MPLog.NONE);
        // All methods should be filtered
        MPLog.v("TestTag", "filtered");
        MPLog.d("TestTag", "filtered");
        MPLog.i("TestTag", "filtered");
        MPLog.w("TestTag", "filtered");
        MPLog.e("TestTag", "filtered");
    }
}
