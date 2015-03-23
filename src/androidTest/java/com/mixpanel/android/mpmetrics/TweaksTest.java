package com.mixpanel.android.mpmetrics;

import android.test.AndroidTestCase;
import android.util.Pair;

import com.mixpanel.android.mpmetrics.test_autotweak_package.RegistrarRuns;
import com.mixpanel.android.mpmetrics.test_autotweak_package.TweakableThing;

import java.util.ArrayList;
import java.util.List;


public class TweaksTest extends AndroidTestCase {
    @Override
    public void setUp() {
        mTweaks = new Tweaks(new TestUtils.SynchronousHandler(), "RegistrarRuns");
    }

    public void testNoSuchTweakPoll() {
        // Should return null for tweaks we know nothing about
        assertNull(mTweaks.getString("Some Tweak"));
    }

    public void testTweakWithDefault() {
        mTweaks.bind("Some Tweak", "Default Value", null);
        assertEquals("Default Value", mTweaks.getString("Some Tweak"));
    }

    public void testNonStringTweakWithWrongType() {
        mTweaks.bind("Some Tweak", 100.0, null);
        assertNull(mTweaks.getString("Some Tweak"));
    }

    public void testPrimitiveTweakWithWrongType() {
        mTweaks.bind("Some Tweak", "String Default", null);
        assertEquals(0.0, mTweaks.getDouble("Some Tweak"));
    }

    public void testTweakFiresOnBinding() {
        final List<Object> found = new ArrayList<Object>();

        mTweaks.bind("Some Tweak", "Default Value", new Tweaks.TweakChangeCallback() {
            @Override
            public void onChange(Object value) {
                found.add(value);
            }
        });

        assertEquals(1, found.size());
        assertEquals("Default Value", found.get(0));
    }

    public void testTweakFiresOnUpdate() {
        final List<Object> found = new ArrayList<Object>();

        mTweaks.bind("Some Tweak", "Default Value", new Tweaks.TweakChangeCallback() {
            @Override
            public void onChange(Object value) {
                found.add(value);
            }
        });

        mTweaks.set("Some Tweak", "Hello");
        assertEquals(2, found.size());
        assertEquals("Default Value", found.get(0));
        assertEquals("Hello", found.get(1));
    }

    public void testNoRegistrarFound() {
        List l = new ArrayList();
        mTweaks.registerForTweaks(l);
        assertEquals(0, RegistrarRuns.TWEAK_REGISTRAR.wasRegistered.size());
    }

    public void testRegistrarFound() {
        TweakableThing thing = new TweakableThing();
        mTweaks.registerForTweaks(thing);
        assertEquals(1, RegistrarRuns.TWEAK_REGISTRAR.wasRegistered.size());
        Pair<Tweaks, Object> found = RegistrarRuns.TWEAK_REGISTRAR.wasRegistered.get(0);
        assertEquals(mTweaks, found.first);
        assertEquals(thing, found.second);
    }

    Tweaks mTweaks;
}
