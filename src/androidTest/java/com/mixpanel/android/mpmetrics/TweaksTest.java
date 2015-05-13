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
        mRegistrar = new RegistrarRuns();
        mTweaks = new Tweaks(new TestUtils.SynchronousHandler(), mRegistrar);
    }

    public void testNoSuchTweakPoll() {
        // Should return null for tweaks we know nothing about
        assertNull(mTweaks.getString("Some Tweak"));
    }

    public void testDeclareWasCalled() {
        assertTrue(mRegistrar.declareWasCalled);
    }

    public void testTweakWithDefault() {
        mTweaks.defineTweak("Some Tweak", "Default Value");
        assertEquals("Default Value", mTweaks.getString("Some Tweak"));
    }

    public void testNonStringTweakWithWrongType() {
        mTweaks.defineTweak("Some Tweak", 100.0);
        assertNull(mTweaks.getString("Some Tweak"));
    }

    public void testPrimitiveTweakWithWrongType() {
        mTweaks.defineTweak("Some Tweak", "String Default");
        assertEquals(0.0, mTweaks.getDouble("Some Tweak"));
    }

    public void testTweakFiresOnBinding() {
        final List<Object> found = new ArrayList<Object>();

        mTweaks.defineTweak("Some Tweak", "Default Value");
        mTweaks.bind("Some Tweak", null, new Tweaks.TweakChangeCallback() {
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

        mTweaks.defineTweak("Some Tweak", "Default Value");
        mTweaks.bind("Some Tweak", null, new Tweaks.TweakChangeCallback() {
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

    public void testRegistrarFound() {
        TweakableThing thing = new TweakableThing();
        mTweaks.registerForTweaks(thing);
        assertEquals(1, mRegistrar.wasRegistered.size());
        Pair<Tweaks, Object> found = mRegistrar.wasRegistered.get(0);
        assertEquals(mTweaks, found.first);
        assertEquals(thing, found.second);
    }

    Tweaks mTweaks;
    RegistrarRuns mRegistrar;
}
