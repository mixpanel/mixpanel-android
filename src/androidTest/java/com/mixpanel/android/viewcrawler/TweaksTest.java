package com.mixpanel.android.viewcrawler;

import android.test.AndroidTestCase;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class TweaksTest extends AndroidTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mTweaks = new Tweaks();
    }

    public void testGet() throws Exception {
        String val = (String) mTweaks.get("test", "testval");
        mTweaks.set("test", "testval2");
        String val2 = (String) mTweaks.get("test", "testval");
        assertEquals("testval", val);
        assertEquals("testval2", val2);
    }

    public void testInitialValueCallback() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        final HashMap<String, Object> vals = new HashMap<String, Object>();
        mTweaks.bind("callbacktest", "first val", new Tweaks.TweakChangeCallback() {
            @Override
            public void onChange(Object value) {
                vals.put((String) value, value);
                latch.countDown();
            }
        });

        latch.await();
        assertTrue(vals.containsKey("first val"));
    }

    public void testCallback() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);

        final HashMap<String, Object> vals = new HashMap<String, Object>();
        mTweaks.bind("callbacktest", "first val", new Tweaks.TweakChangeCallback() {
            @Override
            public void onChange(Object value) {
                vals.put((String) value, value);
                latch.countDown();
            }
        });

        mTweaks.set("callbacktest", "new value");

        latch.await();
        assertTrue(vals.containsKey("new value"));
    }

    private Tweaks mTweaks;
}
