package com.mixpanel.android.mpmetrics;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class SynchronizedReferenceTest {

    @Test
    public void testDefaultIsNull() {
        SynchronizedReference<String> ref = new SynchronizedReference<>();
        assertNull(ref.get());
    }

    @Test
    public void testSetAndGet() {
        SynchronizedReference<String> ref = new SynchronizedReference<>();
        ref.set("hello");
        assertEquals("hello", ref.get());
    }

    @Test
    public void testGetAndClear() {
        SynchronizedReference<String> ref = new SynchronizedReference<>();
        ref.set("value");

        String result = ref.getAndClear();
        assertEquals("value", result);
        assertNull(ref.get());
    }

    @Test
    public void testGetAndClearWhenNull() {
        SynchronizedReference<String> ref = new SynchronizedReference<>();
        assertNull(ref.getAndClear());
    }

    @Test
    public void testOverwrite() {
        SynchronizedReference<Integer> ref = new SynchronizedReference<>();
        ref.set(1);
        assertEquals(Integer.valueOf(1), ref.get());

        ref.set(2);
        assertEquals(Integer.valueOf(2), ref.get());
    }

    @Test
    public void testSetNull() {
        SynchronizedReference<String> ref = new SynchronizedReference<>();
        ref.set("value");
        ref.set(null);
        assertNull(ref.get());
    }

    @Test
    public void testWithDifferentTypes() {
        SynchronizedReference<Object> ref = new SynchronizedReference<>();
        ref.set("string");
        assertEquals("string", ref.get());

        ref.set(42);
        assertEquals(42, ref.get());

        ref.set(true);
        assertEquals(true, ref.get());
    }

    @Test
    public void testThreadSafety() throws InterruptedException {
        final int THREAD_COUNT = 10;
        final int ITERATIONS = 100;
        SynchronizedReference<Integer> ref = new SynchronizedReference<>();
        ref.set(0);

        final AtomicInteger completedOperations = new AtomicInteger(0);
        final AtomicBoolean corruptionDetected = new AtomicBoolean(false);

        Thread[] threads = new Thread[THREAD_COUNT];
        for (int i = 0; i < threads.length; i++) {
            final int val = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < ITERATIONS; j++) {
                    ref.set(val);
                    Integer got = ref.get();
                    if (got != null && (got < 0 || got >= THREAD_COUNT)) {
                        corruptionDetected.set(true);
                    }
                    Integer cleared = ref.getAndClear();
                    if (cleared != null && (cleared < 0 || cleared >= THREAD_COUNT)) {
                        corruptionDetected.set(true);
                    }
                    ref.set(val);
                    completedOperations.addAndGet(4);
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        assertEquals("All operations should complete",
            THREAD_COUNT * ITERATIONS * 4, completedOperations.get());
        assertFalse("No corrupted values should be observed during concurrent access",
            corruptionDetected.get());
        Integer finalValue = ref.get();
        assertTrue("Final value should be null or a valid thread value (0-9)",
            finalValue == null || (finalValue >= 0 && finalValue < THREAD_COUNT));
    }
}
