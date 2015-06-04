package com.mixpanel.android.mpmetrics;

import android.support.annotation.IntDef;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;

/**
 * Tweaks are a mechanism for declaring and receiving dynamic values that you can change and deploy
 * to users through Mixpanel. You can declare and access tweaks in your code, and then deploy new
 * values to some or all of your users using a Mixpanel A/B test.
 */
public class Tweaks {

    @IntDef({
        UNKNOWN_TYPE,
        BOOLEAN_TYPE,
        DOUBLE_TYPE,
        LONG_TYPE,
        STRING_TYPE
    })

    @Retention(RetentionPolicy.SOURCE)
    private @interface TweakType {}

    /**
     * An internal description of the type of a tweak.
     * These values are used internally to expose
     * tweaks to the Mixpanel UI, and will likely not be directly useful to
     * code that imports the Mixpanel library.
     */
    public static final @TweakType int UNKNOWN_TYPE = 0;
    public static final @TweakType int BOOLEAN_TYPE = 1;
    public static final @TweakType int DOUBLE_TYPE = 2;
    public static final @TweakType int LONG_TYPE = 3;
    public static final @TweakType int STRING_TYPE = 4;

    /**
     * Represents the value and definition of a tweak known to the system. This class
     * is used internally to expose tweaks to the Mixpanel UI,
     * and will likely not be directly useful to code that imports the Mixpanel library.
     */
    public static class TweakValue {
        private TweakValue(@TweakType int aType, Object aDefaultValue, Number aMin, Number aMax, Object value) {
            type = aType;
            defaultValue = aDefaultValue;
            minimum = aMin;
            maximum = aMax;
            this.value = value;
        }

        public TweakValue updateValue(Object newValue) {
            return new TweakValue(type, defaultValue, minimum, maximum, newValue);
        }

        public String getStringValue() {
            String ret = null;

            try {
                ret = (String) defaultValue;
            } catch (ClassCastException e2) {
                ; // ok
            }

            try {
                ret = (String) value;
            } catch (ClassCastException e) {
                ; // ok
            }

            return ret;
        }

        public Number getNumberValue() {
            Number ret = 0;

            if (null != defaultValue) {
                try {
                    ret = (Number) defaultValue;
                } catch (ClassCastException e){
                    ; // ok
                }
            }

            if (null != value) {
                try {
                    ret = (Number) value;
                } catch (ClassCastException e) {
                    ; // ok
                }
            }

            return ret;
        }

        public Boolean getBooleanValue() {
            Boolean ret = false;

            if (null != defaultValue) {
                try {
                    ret = (Boolean) defaultValue;
                } catch (ClassCastException e) {
                    ; // ok
                }
            }

            if (null != value) {
                try {
                    ret = (Boolean) value;
                } catch (ClassCastException e) {
                    ; // ok
                }
            }

            return ret;
        }

        public final @TweakType int type;
        public final Object defaultValue;
        public final Number minimum;
        public final Number maximum;
        public final Object value;
    }

    public Tweak<String> stringTweak(final String tweakName, final String defaultValue) {
        defineTweak(tweakName, defaultValue);
        return new Tweak<String>() {
            @Override
            public String get() {
                final TweakValue tweakValue = getValue(tweakName);
                return tweakValue.getStringValue();
            }
        };
    }

    public Tweak<Double> doubleTweak(final String tweakName, final double defaultValue) {
        defineTweak(tweakName, defaultValue);
        return new Tweak<Double>() {
            @Override
            public Double get() {
                final TweakValue tweakValue = getValue(tweakName);
                final Number result = tweakValue.getNumberValue();
                return result.doubleValue();
            }
        };
    }

    public Tweak<Float> floatTweak(final String tweakName, final float defaultValue) {
        defineTweak(tweakName, defaultValue);
        return new Tweak<Float>() {
            @Override
            public Float get() {
                final TweakValue tweakValue = getValue(tweakName);
                final Number result = tweakValue.getNumberValue();
                return result.floatValue();
            }
        };
    }

    public Tweak<Long> longTweak(final String tweakName, final long defaultValue) {
        defineTweak(tweakName, defaultValue);
        return new Tweak<Long>() {
            @Override
            public Long get() {
                final TweakValue tweakValue = getValue(tweakName);
                final Number result = tweakValue.getNumberValue();
                return result.longValue();
            }
        };
    }

    public Tweak<Integer> intTweak(final String tweakName, final int defaultValue) {
        defineTweak(tweakName, defaultValue);
        return new Tweak<Integer>() {
            @Override
            public Integer get() {
                final TweakValue tweakValue = getValue(tweakName);
                final Number result = tweakValue.getNumberValue();
                return result.intValue();
            }
        };
    }

    public Tweak<Byte> byteTweak(final String tweakName, final byte defaultValue) {
        defineTweak(tweakName, defaultValue);
        return new Tweak<Byte>() {
            @Override
            public Byte get() {
                final TweakValue tweakValue = getValue(tweakName);
                final Number result = tweakValue.getNumberValue();
                return result.byteValue();
            }
        };
    }

    public Tweak<Short> shortTweak(final String tweakName, final short defaultValue) {
        defineTweak(tweakName, defaultValue);
        return new Tweak<Short>() {
            @Override
            public Short get() {
                final TweakValue tweakValue = getValue(tweakName);
                final Number result = tweakValue.getNumberValue();
                return result.shortValue();
            }
        };
    }

    public Tweak<Boolean> booleanTweak(final String tweakName, final boolean defaultValue) {
        defineTweak(tweakName, defaultValue);
        return new Tweak<Boolean>() {
            @Override
            public Boolean get() {
                final TweakValue tweakValue = getValue(tweakName);
                return tweakValue.getBooleanValue();
            }
        };
    }

    private synchronized TweakValue getValue(String tweakName) {
        return mTweakValues.get(tweakName);
    }

    private synchronized void defineTweak(String tweakName, Object defaultValue) {
        if (mTweakValues.containsKey(tweakName)) {
            Log.w(LOGTAG, "Attempt to define a tweak \"" + tweakName + "\" twice with the same name");
            return;
        }

        final @TweakType int tweakType = determineType(defaultValue);
        final TweakValue value = new TweakValue(tweakType, defaultValue, null, null, defaultValue);
        mTweakValues.put(tweakName, value);
    }

    /**
     * Manually set the value of a tweak. Most users of the library will not need to call this
     * directly - instead, the library will call set when new values of the tweak are published.
     */
    public synchronized void set(String tweakName, Object value) {
        if (!mTweakValues.containsKey(tweakName)) {
            Log.w(LOGTAG, "Attempt to set a tweak \"" + tweakName + "\" which has never been defined.");
            return;
        }

        final TweakValue container = mTweakValues.get(tweakName);
        final TweakValue updated = container.updateValue(value);
        mTweakValues.put(tweakName, updated);
    }

    /**
     * Returns the descriptions of all tweaks currently introduced with {@link #defineTweak(String, Object)}.
     *
     * The Mixpanel library uses this method internally to expose tweaks and their types to the UI. Most
     * users will not need to call this method directly.
     */
    public synchronized Map<String, TweakValue> getAllValues() {
        return new HashMap<String, TweakValue>(mTweakValues);
    }

    /* package */ Tweaks() {
        mTweakValues = new HashMap<String, TweakValue>();
    }

    private @TweakType int determineType(Object thing) {
        if (thing instanceof String) {
            return STRING_TYPE;
        }

        if (thing instanceof Double || thing instanceof Float) {
            return DOUBLE_TYPE;
        }

        if (thing instanceof Long || thing instanceof Integer || thing instanceof Short || thing instanceof Byte) {
            return LONG_TYPE;
        }

        if (thing instanceof Boolean) {
            return BOOLEAN_TYPE;
        }

        return UNKNOWN_TYPE;
    }

    // All access to mTweakValues must be synchronized
    private final Map<String, TweakValue> mTweakValues;

    private static final String LOGTAG = "MixpanelAPI.Tweaks";
}
