package com.mixpanel.android.abtesting;

import android.test.AndroidTestCase;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by josh on 6/27/14.
 */
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

    public void testCallback() throws Exception {
        final HashMap<String, Object> vals = new HashMap<String, Object>();
        mTweaks.bind("callbacktest", "first val", new Tweaks.TweakChangeCallback() {
            @Override
            public void onChange(Object value) {
                vals.put((String) value, value);
            }
        });
        assertTrue(vals.containsKey("first val"));
        mTweaks.set("callbacktest", "new value");
        assertTrue(vals.containsKey("new value"));
    }

    private Tweaks mTweaks;
}
