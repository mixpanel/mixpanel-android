package com.mixpanel.android.mpmetrics;

import org.junit.Test;

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
        SynchronizedReference<Integer> ref = new SynchronizedReference<>();
        ref.set(0);

        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            final int val = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    ref.set(val);
                    ref.get();
                    ref.getAndClear();
                    ref.set(val);
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        // No exception = thread safe
    }
}
