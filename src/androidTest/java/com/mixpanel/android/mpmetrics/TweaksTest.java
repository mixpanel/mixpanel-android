package com.mixpanel.android.mpmetrics;

import android.test.AndroidTestCase;

import java.util.Map;


public class TweaksTest extends AndroidTestCase {
    @Override
    public void setUp() {
        mTweaks = new Tweaks();
        mTweakDeclaredCount = 0;
        mTweaks.addOnTweakDeclaredListener(new Tweaks.OnTweakDeclaredListener() {
            @Override
            public void onTweakDeclared() {
                mTweakDeclaredCount++;
            }
        });
    }

    public void testDontCallUntilDeclared() {
        assertEquals(0, mTweakDeclaredCount);
    }

    public void testStringTweak() {
        final Tweak<String> tweak = mTweaks.stringTweak("subject", "default");
        assertEquals("default", tweak.get());
        assertEquals(1, mTweakDeclaredCount);
        mTweaks.set("subject", "changed");
        assertEquals("changed", tweak.get());
        assertEquals(1, mTweakDeclaredCount);
    }

    public void testFloatTweak() {
        float defaultValue = 1.0f;
        float changedValue = 2.0f;
        final Tweak<Float> tweak = mTweaks.floatTweak("subject", defaultValue);
        assertEquals(defaultValue, (float) tweak.get());
        assertEquals(1, mTweakDeclaredCount);
        mTweaks.set("subject", changedValue);
        assertEquals(changedValue, (float) tweak.get());
        assertEquals(1, mTweakDeclaredCount);
    }

    public void testDoubleTweak() {
        double defaultValue = 1.0d;
        double changedValue = 2.0d;
        final Tweak<Double> tweak = mTweaks.doubleTweak("subject", defaultValue);
        assertEquals(defaultValue, (double) tweak.get());
        assertEquals(1, mTweakDeclaredCount);
        mTweaks.set("subject", changedValue);
        assertEquals(changedValue, (double) tweak.get());
        assertEquals(1, mTweakDeclaredCount);
    }

    public void testByteTweak() {
        byte defaultValue = 1;
        byte changedValue = 2;
        final Tweak<Byte> tweak = mTweaks.byteTweak("subject", defaultValue);
        assertEquals(defaultValue, (byte) tweak.get());
        assertEquals(1, mTweakDeclaredCount);
        mTweaks.set("subject", changedValue);
        assertEquals(changedValue, (byte) tweak.get());
        assertEquals(1, mTweakDeclaredCount);
    }

    public void testShortTweak() {
        short defaultValue = 1;
        short changedValue = 2;
        final Tweak<Short> tweak = mTweaks.shortTweak("subject", defaultValue);
        assertEquals(defaultValue, (short) tweak.get());
        assertEquals(1, mTweakDeclaredCount);
        mTweaks.set("subject", changedValue);
        assertEquals(changedValue, (short) tweak.get());
        assertEquals(1, mTweakDeclaredCount);
    }

    public void testIntTweak() {
        int defaultValue = 1;
        int changedValue = 2;
        final Tweak<Integer> tweak = mTweaks.intTweak("subject", defaultValue);
        assertEquals(defaultValue, (int) tweak.get());
        assertEquals(1, mTweakDeclaredCount);
        mTweaks.set("subject", changedValue);
        assertEquals(changedValue, (int) tweak.get());
        assertEquals(1, mTweakDeclaredCount);
    }

    public void testLongTweak() {
        long defaultValue = 1;
        long changedValue = 2;
        final Tweak<Long> tweak = mTweaks.longTweak("subject", defaultValue);
        assertEquals(defaultValue, (long) tweak.get());
        assertEquals(1, mTweakDeclaredCount);
        mTweaks.set("subject", changedValue);
        assertEquals(changedValue, (long) tweak.get());
        assertEquals(1, mTweakDeclaredCount);
    }

    public void testBooleanTweak() {
        boolean defaultValue = true;
        boolean changedValue = false;
        final Tweak<Boolean> tweak = mTweaks.booleanTweak("subject", defaultValue);
        assertEquals(defaultValue, (boolean) tweak.get());
        assertEquals(1, mTweakDeclaredCount);
        mTweaks.set("subject", changedValue);
        assertEquals(changedValue, (boolean) tweak.get());
        assertEquals(1, mTweakDeclaredCount);
    }

    public void testStringBadType() {
        final Tweak<String> tweak = mTweaks.stringTweak("subject", "default");
        mTweaks.set("subject", 32);
        assertEquals("default", tweak.get());
    }

    public void testFloatBadType() {
        float defaultValue = 1.0f;
        String badValue = "BAD";
        final Tweak<Float> tweak = mTweaks.floatTweak("subject", defaultValue);
        mTweaks.set("subject", badValue);
        assertEquals(defaultValue, tweak.get());
    }

    public void testDoubleBadType() {
        double defaultValue = 1.0d;
        String badValue = "BAD";
        final Tweak<Double> tweak = mTweaks.doubleTweak("subject", defaultValue);
        mTweaks.set("subject", badValue);
        assertEquals(defaultValue, tweak.get());
    }

    public void testByteBadType() {
        byte defaultValue = 1;
        String badValue = "BAD";
        final Tweak<Byte> tweak = mTweaks.byteTweak("subject", defaultValue);
        mTweaks.set("subject", badValue);
        assertEquals(1, (byte) tweak.get());
    }

    public void testShortBadType() {
        short defaultValue = 1;
        String badValue = "BAD";
        final Tweak<Short> tweak = mTweaks.shortTweak("subject", defaultValue);
        mTweaks.set("subject", badValue);
        assertEquals(defaultValue, (short) tweak.get());
    }

    public void testIntBadType() {
        int defaultValue = 1;
        String badValue = "BAD";
        final Tweak<Integer> tweak = mTweaks.intTweak("subject", defaultValue);
        mTweaks.set("subject", badValue);
        assertEquals(defaultValue, (int) tweak.get());
    }

    public void testLongBadType() {
        long defaultValue = 1;
        String badValue = "BAD";
        final Tweak<Long> tweak = mTweaks.longTweak("subject", defaultValue);
        mTweaks.set("subject", badValue);
        assertEquals(defaultValue, (long) tweak.get());
    }

    public void testBooleanBadType() {
        boolean defaultValue = true;
        String badValue = "BAD";
        final Tweak<Boolean> tweak = mTweaks.booleanTweak("subject", defaultValue);
        mTweaks.set("subject", badValue);
        assertEquals(defaultValue, (boolean) tweak.get());
    }

    public void testStringNullDefault() {
        final Tweak<String> tweak = mTweaks.stringTweak("subject", null);
        assertNull(tweak.get());
    }

    public void testSetStringNull() {
        final String defaultValue = "default";
        final Tweak<String> tweak = mTweaks.stringTweak("subject", defaultValue);
        mTweaks.set("subject", null);
        assertNull(tweak.get());
    }

    public void testSetBooleanNull() {
        final boolean defaultValue = true;
        final Tweak<Boolean> tweak = mTweaks.booleanTweak("subject", defaultValue);
        mTweaks.set("subject", null);
        assertEquals(defaultValue, (boolean) tweak.get());
    }

    public void testSetDoubleNull() {
        final double defaultValue = 1.0d;
        final Tweak<Double> tweak = mTweaks.doubleTweak("subject", defaultValue);
        mTweaks.set("subject", null);
        assertEquals(defaultValue, tweak.get());
    }

    public void testSetFloatNull() {
        final float defaultValue = 1.0f;
        final Tweak<Float> tweak = mTweaks.floatTweak("subject", defaultValue);
        mTweaks.set("subject", null);
        assertEquals(defaultValue, tweak.get());
    }

    public void testSetByteNull() {
        final byte defaultValue = 1;
        final Tweak<Byte> tweak = mTweaks.byteTweak("subject", defaultValue);
        mTweaks.set("subject", null);
        assertEquals(defaultValue, (byte) tweak.get());
    }

    public void testSetShortNull() {
        final short defaultValue = 1;
        final Tweak<Short> tweak = mTweaks.shortTweak("subject", defaultValue);
        mTweaks.set("subject", null);
        assertEquals(defaultValue, (short) tweak.get());
    }

    public void testSetIntNull() {
        final int defaultValue = 1;
        final Tweak<Integer> tweak = mTweaks.intTweak("subject", defaultValue);
        mTweaks.set("subject", null);
        assertEquals(defaultValue, (int) tweak.get());
    }

    public void testSetLongNull() {
        final long defaultValue = 1;
        final Tweak<Long> tweak = mTweaks.longTweak("subject", defaultValue);
        mTweaks.set("subject", null);
        assertEquals(defaultValue, (long) tweak.get());
    }

    public void testIntegralOverflow() {
        final byte defaultValue = 1;
        final Tweak<Byte> tweak = mTweaks.byteTweak("subject", defaultValue);
        mTweaks.set("subject", Long.MAX_VALUE);
        assertTrue(0 != tweak.get());
    }

    public void testNumberCoercion() {
        final Tweak<Byte> byteTweak = mTweaks.byteTweak("subject", (byte) 1);
        mTweaks.set("subject", 1.0d);
        assertEquals(1, (byte) byteTweak.get());

        final Tweak<Float> floatTweak = mTweaks.floatTweak("subject", 1.0f);
        mTweaks.set("subject", 1);
        assertEquals(1.0f, floatTweak.get());
    }

    public void testDuplicateTweakDeclared() {

        // First tweak wins for default values. Since strings can't be coerced into numbers
        // the numbers should be the global defaults (NOT the defaults set in the calls)
        final Tweak<String> stringTweak1 = mTweaks.stringTweak("subject1", "Yup");
        final Tweak<Byte> byteTweak1 = mTweaks.byteTweak("subject1", (byte) 1);
        final Tweak<Float> floatTweak1 = mTweaks.floatTweak("subject1", 3.0f);
        final Tweak<Boolean> booleanTweak1 = mTweaks.booleanTweak("subject1", true);

        assertEquals("Yup", stringTweak1.get());
        assertEquals(0, (byte) byteTweak1.get());
        assertEquals(0.0f, floatTweak1.get());
        assertEquals(false, (boolean) booleanTweak1.get());

        // Can't coerce 22 into a string, so use global string default
        final Tweak<Integer> intTweak2 = mTweaks.intTweak("subject2", 22);
        final Tweak<String> stringTweak2 = mTweaks.stringTweak("subject2", "Oh Yeah!");
        final Tweak<Boolean> booleanTweak = mTweaks.booleanTweak("subject2", true);

        assertEquals(22, (int) intTweak2.get());
        assertNull(stringTweak2.get());

        // We expect a default of 22 and a value of "Now a string"
        mTweaks.set("subject2", "Now a string");

        assertEquals("Now a string", stringTweak2.get());
        assertEquals(22, (int) intTweak2.get());
        assertEquals(false, (boolean) booleanTweak.get());

        mTweaks.set("subject2", true);

        assertEquals(22, (int) intTweak2.get());
        assertEquals(null, stringTweak2.get());
        assertEquals(true, (boolean) booleanTweak.get());
    }

    public void testAllTweakValues() {
        final Tweak<String> stringTweak1 = mTweaks.stringTweak("a", "Yup");
        final Tweak<Byte> byteTweak1 = mTweaks.byteTweak("b", (byte) 7);
        final Tweak<Float> floatTweak1 = mTweaks.floatTweak("c", 3.0f);
        final Tweak<Boolean> booleanTweak1 = mTweaks.booleanTweak("d", true);

        final Map<String, Tweaks.TweakValue> allValues1 = mTweaks.getAllValues();

        mTweaks.set("a", "Nope");
        mTweaks.set("b", 14);
        mTweaks.set("c", 22.0f);
        mTweaks.set("d", false);

        final Map<String, Tweaks.TweakValue> allValues2 = mTweaks.getAllValues();
        final Tweaks.TweakValue a1 = allValues1.get("a");
        final Tweaks.TweakValue b1 = allValues1.get("b");
        final Tweaks.TweakValue c1 = allValues1.get("c");
        final Tweaks.TweakValue d1 = allValues1.get("d");

        final Tweaks.TweakValue a2 = allValues2.get("a");
        final Tweaks.TweakValue b2 = allValues2.get("b");
        final Tweaks.TweakValue c2 = allValues2.get("c");
        final Tweaks.TweakValue d2 = allValues2.get("d");

        assertEquals(Tweaks.STRING_TYPE, a1.type);
        assertEquals(Tweaks.LONG_TYPE, b1.type);
        assertEquals(Tweaks.DOUBLE_TYPE, c1.type);
        assertEquals(Tweaks.BOOLEAN_TYPE, d1.type);

        // Returns should never change, but new calls to allValues should yield new results
        assertEquals("Yup", a1.getStringValue());
        assertEquals("Nope", a2.getStringValue());
        assertEquals((byte) 7, b1.getNumberValue());
        assertEquals(14, b2.getNumberValue());
        assertEquals(3.0f, c1.getNumberValue());
        assertEquals(22.0f, c2.getNumberValue());
        assertEquals(true, (boolean) d1.getBooleanValue());
        assertEquals(false, (boolean) d2.getBooleanValue());
    }

    Tweaks mTweaks;
    int mTweakDeclaredCount;
}
