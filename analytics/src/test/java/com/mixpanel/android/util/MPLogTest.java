package com.mixpanel.android.util;

import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

import java.util.List;

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
    public void testConstants() {
        assertEquals(Log.VERBOSE, MPLog.VERBOSE);
        assertEquals(Log.DEBUG, MPLog.DEBUG);
        assertEquals(Log.INFO, MPLog.INFO);
        assertEquals(Log.WARN, MPLog.WARN);
        assertEquals(Log.ERROR, MPLog.ERROR);
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
        MPLog.v("VerboseTag", "verbose message");

        List<ShadowLog.LogItem> logs = ShadowLog.getLogs();
        assertEquals("Expected exactly one log entry", 1, logs.size());
        assertEquals(Log.VERBOSE, logs.get(0).type);
        assertEquals("VerboseTag", logs.get(0).tag);
        assertEquals("verbose message", logs.get(0).msg);
        assertNull(logs.get(0).throwable);
    }

    @Test
    public void testDebugLogging() {
        MPLog.setLevel(MPLog.VERBOSE);
        MPLog.d("DebugTag", "debug message");

        List<ShadowLog.LogItem> logs = ShadowLog.getLogs();
        assertEquals("Expected exactly one log entry", 1, logs.size());
        assertEquals(Log.DEBUG, logs.get(0).type);
        assertEquals("DebugTag", logs.get(0).tag);
        assertEquals("debug message", logs.get(0).msg);
        assertNull(logs.get(0).throwable);
    }

    @Test
    public void testInfoLogging() {
        MPLog.setLevel(MPLog.VERBOSE);
        MPLog.i("InfoTag", "info message");

        List<ShadowLog.LogItem> logs = ShadowLog.getLogs();
        assertEquals("Expected exactly one log entry", 1, logs.size());
        assertEquals(Log.INFO, logs.get(0).type);
        assertEquals("InfoTag", logs.get(0).tag);
        assertEquals("info message", logs.get(0).msg);
        assertNull(logs.get(0).throwable);
    }

    @Test
    public void testWarnLogging() {
        MPLog.setLevel(MPLog.VERBOSE);
        MPLog.w("WarnTag", "warn message");

        List<ShadowLog.LogItem> logs = ShadowLog.getLogs();
        assertEquals("Expected exactly one log entry", 1, logs.size());
        assertEquals(Log.WARN, logs.get(0).type);
        assertEquals("WarnTag", logs.get(0).tag);
        assertEquals("warn message", logs.get(0).msg);
        assertNull(logs.get(0).throwable);
    }

    @Test
    public void testErrorLogging() {
        MPLog.setLevel(MPLog.VERBOSE);
        MPLog.e("ErrorTag", "error message");

        List<ShadowLog.LogItem> logs = ShadowLog.getLogs();
        assertEquals("Expected exactly one log entry", 1, logs.size());
        assertEquals(Log.ERROR, logs.get(0).type);
        assertEquals("ErrorTag", logs.get(0).tag);
        assertEquals("error message", logs.get(0).msg);
        assertNull(logs.get(0).throwable);
    }

    @Test
    public void testVerboseLoggingWithThrowable() {
        MPLog.setLevel(MPLog.VERBOSE);
        Throwable t = new RuntimeException("verbose error");
        MPLog.v("VTag", "verbose throwable", t);

        List<ShadowLog.LogItem> logs = ShadowLog.getLogs();
        assertEquals(1, logs.size());
        assertEquals(Log.VERBOSE, logs.get(0).type);
        assertEquals("VTag", logs.get(0).tag);
        assertEquals("verbose throwable", logs.get(0).msg);
        assertSame(t, logs.get(0).throwable);
    }

    @Test
    public void testDebugLoggingWithThrowable() {
        MPLog.setLevel(MPLog.VERBOSE);
        Throwable t = new RuntimeException("debug error");
        MPLog.d("DTag", "debug throwable", t);

        List<ShadowLog.LogItem> logs = ShadowLog.getLogs();
        assertEquals(1, logs.size());
        assertEquals(Log.DEBUG, logs.get(0).type);
        assertEquals("DTag", logs.get(0).tag);
        assertEquals("debug throwable", logs.get(0).msg);
        assertSame(t, logs.get(0).throwable);
    }

    @Test
    public void testInfoLoggingWithThrowable() {
        MPLog.setLevel(MPLog.VERBOSE);
        Throwable t = new RuntimeException("info error");
        MPLog.i("ITag", "info throwable", t);

        List<ShadowLog.LogItem> logs = ShadowLog.getLogs();
        assertEquals(1, logs.size());
        assertEquals(Log.INFO, logs.get(0).type);
        assertEquals("ITag", logs.get(0).tag);
        assertEquals("info throwable", logs.get(0).msg);
        assertSame(t, logs.get(0).throwable);
    }

    @Test
    public void testWarnLoggingWithThrowable() {
        MPLog.setLevel(MPLog.VERBOSE);
        Throwable t = new RuntimeException("warn error");
        MPLog.w("WTag", "warn throwable", t);

        List<ShadowLog.LogItem> logs = ShadowLog.getLogs();
        assertEquals(1, logs.size());
        assertEquals(Log.WARN, logs.get(0).type);
        assertEquals("WTag", logs.get(0).tag);
        assertEquals("warn throwable", logs.get(0).msg);
        assertSame(t, logs.get(0).throwable);
    }

    @Test
    public void testErrorLoggingWithThrowable() {
        MPLog.setLevel(MPLog.VERBOSE);
        Throwable t = new RuntimeException("error error");
        MPLog.e("ETag", "error throwable", t);

        List<ShadowLog.LogItem> logs = ShadowLog.getLogs();
        assertEquals(1, logs.size());
        assertEquals(Log.ERROR, logs.get(0).type);
        assertEquals("ETag", logs.get(0).tag);
        assertEquals("error throwable", logs.get(0).msg);
        assertSame(t, logs.get(0).throwable);
    }

    @Test
    public void testLevelFiltering_onlyErrorPassesAtErrorLevel() {
        MPLog.setLevel(MPLog.ERROR);

        MPLog.v("FilterTag", "verbose filtered");
        MPLog.d("FilterTag", "debug filtered");
        MPLog.i("FilterTag", "info filtered");
        MPLog.w("FilterTag", "warn filtered");

        assertEquals("v/d/i/w should all be filtered at ERROR level", 0, ShadowLog.getLogs().size());

        MPLog.e("FilterTag", "error passes");
        assertEquals("Only error should pass", 1, ShadowLog.getLogs().size());
        assertEquals(Log.ERROR, ShadowLog.getLogs().get(0).type);
        assertEquals("error passes", ShadowLog.getLogs().get(0).msg);
    }

    @Test
    public void testLevelFiltering_warnAndAbovePassAtWarnLevel() {
        MPLog.setLevel(MPLog.WARN);

        MPLog.v("FilterTag", "verbose filtered");
        MPLog.d("FilterTag", "debug filtered");
        MPLog.i("FilterTag", "info filtered");
        assertEquals("v/d/i should be filtered at WARN level", 0, ShadowLog.getLogs().size());

        MPLog.w("FilterTag", "warn passes");
        MPLog.e("FilterTag", "error passes");
        assertEquals("warn and error should pass", 2, ShadowLog.getLogs().size());
        assertEquals(Log.WARN, ShadowLog.getLogs().get(0).type);
        assertEquals(Log.ERROR, ShadowLog.getLogs().get(1).type);
    }

    @Test
    public void testLevelFiltering_allPassAtVerboseLevel() {
        MPLog.setLevel(MPLog.VERBOSE);

        MPLog.v("AllTag", "v");
        MPLog.d("AllTag", "d");
        MPLog.i("AllTag", "i");
        MPLog.w("AllTag", "w");
        MPLog.e("AllTag", "e");

        List<ShadowLog.LogItem> logs = ShadowLog.getLogs();
        assertEquals("All 5 levels should pass at VERBOSE", 5, logs.size());
        assertEquals(Log.VERBOSE, logs.get(0).type);
        assertEquals(Log.DEBUG, logs.get(1).type);
        assertEquals(Log.INFO, logs.get(2).type);
        assertEquals(Log.WARN, logs.get(3).type);
        assertEquals(Log.ERROR, logs.get(4).type);
    }

    @Test
    public void testNoneLevel_nothingPasses() {
        MPLog.setLevel(MPLog.NONE);

        MPLog.v("NoneTag", "v");
        MPLog.d("NoneTag", "d");
        MPLog.i("NoneTag", "i");
        MPLog.w("NoneTag", "w");
        MPLog.e("NoneTag", "e");

        assertEquals("Nothing should pass at NONE level", 0, ShadowLog.getLogs().size());
    }

    @Test
    public void testThrowableOverloadsFilteredByLevel() {
        MPLog.setLevel(MPLog.ERROR);
        Throwable t = new RuntimeException("should not appear");

        MPLog.v("ThrowTag", "v", t);
        MPLog.d("ThrowTag", "d", t);
        MPLog.i("ThrowTag", "i", t);
        MPLog.w("ThrowTag", "w", t);

        assertEquals("Throwable overloads should also be filtered", 0, ShadowLog.getLogs().size());

        MPLog.e("ThrowTag", "e", t);
        assertEquals(1, ShadowLog.getLogs().size());
        assertEquals(Log.ERROR, ShadowLog.getLogs().get(0).type);
        assertSame(t, ShadowLog.getLogs().get(0).throwable);
    }

    @Test
    public void testEachLevelBoundary() {
        // At each level, the call at exactly that level should pass, one below should not
        int[] levels = {MPLog.VERBOSE, MPLog.DEBUG, MPLog.INFO, MPLog.WARN, MPLog.ERROR};

        for (int level : levels) {
            MPLog.setLevel(level);
            ShadowLog.reset();

            // Call all levels and count how many pass
            MPLog.v("BoundaryTag", "v");
            MPLog.d("BoundaryTag", "d");
            MPLog.i("BoundaryTag", "i");
            MPLog.w("BoundaryTag", "w");
            MPLog.e("BoundaryTag", "e");

            List<ShadowLog.LogItem> logs = ShadowLog.getLogs();

            // The first log entry should be at exactly the set level
            assertFalse("At level " + level + ", at least one message should pass", logs.isEmpty());
            assertEquals("First log entry should match the minimum level", level, logs.get(0).type);

            // Every logged entry should be >= the set level
            for (ShadowLog.LogItem item : logs) {
                assertTrue(
                    "Log type " + item.type + " should be >= level " + level,
                    item.type >= level);
            }
        }
    }
}
