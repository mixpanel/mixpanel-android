package com.mixpanel.android.mpmetrics;

import android.test.AndroidTestCase;


public class TweaksTest extends AndroidTestCase {
    @Override
    public void setUp() {
        mTweaks = new Tweaks();
    }

    public void testNoSuchTweakPoll() {
        // Should return null for tweaks we know nothing about
        assertNull(mTweaks.getString("Some Tweak"));
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

    Tweaks mTweaks;
}
