package com.mixpanel.android.mpmetrics;

import android.support.annotation.IntDef;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In general, you won't need to interact with this class directly -
 * it's used internally to communicate changes in values to the tweaks you define with
 * {@link MixpanelAPI#stringTweak(String, String)}, {@link MixpanelAPI#booleanTweak(String, boolean)},
 * {@link MixpanelAPI#doubleTweak(String, double)}, {@link MixpanelAPI#longTweak(String, long)},
 * and other tweak-related interfaces on MixpanelAPI.
 *
 * Instances of tweaks aren't available to library user code.
 */
public class Tweaks {
    /**
     * This method is used internally to expose tweaks to the Mixpanel UI,
     * and will likely not be directly useful to code that imports the Mixpanel library.
     * The given listener's onTweakDeclared method will be called when a new tweak is declared.
     */
    public synchronized void addOnTweakDeclaredListener(OnTweakDeclaredListener listener) {
        if (null == listener) {
            throw new NullPointerException("listener cannot be null");
        }
        mTweakDeclaredListeners.add(listener);
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
     * Returns the descriptions of all tweaks that currently exist.
     *
     * The Mixpanel library uses this method internally to expose tweaks and their types to the UI. Most
     * users will not need to call this method directly.
     */
    public synchronized Map<String, TweakValue> getAllValues() {
        return new HashMap<String, TweakValue>(mTweakValues);
    }

    @IntDef({
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
    public static final @TweakType int BOOLEAN_TYPE = 1;

    /**
     * An internal description of the type of a tweak.
     * These values are used internally to expose
     * tweaks to the Mixpanel UI, and will likely not be directly useful to
     * code that imports the Mixpanel library.
     */
    public static final @TweakType int DOUBLE_TYPE = 2;

    /**
     * An internal description of the type of a tweak.
     * These values are used internally to expose
     * tweaks to the Mixpanel UI, and will likely not be directly useful to
     * code that imports the Mixpanel library.
     */
    public static final @TweakType int LONG_TYPE = 3;

    /**
     * An internal description of the type of a tweak.
     * These values are used internally to expose
     * tweaks to the Mixpanel UI, and will likely not be directly useful to
     * code that imports the Mixpanel library.
     */
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
            } catch (ClassCastException e) {
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

        private final Object value;
        private final Object defaultValue;
        private final Number minimum;
        private final Number maximum;
    }

    /**
     * This interface is used internally to expose tweaks to the Mixpanel UI,
     * and will likely not be directly useful to code that imports the Mixpanel library.
     */
    public interface OnTweakDeclaredListener {
        void onTweakDeclared();
    }

    /* package */ Tweaks() {
        mTweakValues = new HashMap<String, TweakValue>();
        mTweakDeclaredListeners = new ArrayList<OnTweakDeclaredListener>();
    }


    /* package */ Tweak<String> stringTweak(final String tweakName, final String defaultValue) {
        declareTweak(tweakName, defaultValue, STRING_TYPE);
        return new Tweak<String>() {
            @Override
            public String get() {
                final TweakValue tweakValue = getValue(tweakName);
                return tweakValue.getStringValue();
            }
        };
    }

    /* package */ Tweak<Double> doubleTweak(final String tweakName, final double defaultValue) {
        declareTweak(tweakName, defaultValue, DOUBLE_TYPE);
        return new Tweak<Double>() {
            @Override
            public Double get() {
                final TweakValue tweakValue = getValue(tweakName);
                final Number result = tweakValue.getNumberValue();
                return result.doubleValue();
            }
        };
    }

    /* package */  Tweak<Float> floatTweak(final String tweakName, final float defaultValue) {
        declareTweak(tweakName, defaultValue, DOUBLE_TYPE);
        return new Tweak<Float>() {
            @Override
            public Float get() {
                final TweakValue tweakValue = getValue(tweakName);
                final Number result = tweakValue.getNumberValue();
                return result.floatValue();
            }
        };
    }

    /* package */  Tweak<Long> longTweak(final String tweakName, final long defaultValue) {
        declareTweak(tweakName, defaultValue, LONG_TYPE);
        return new Tweak<Long>() {
            @Override
            public Long get() {
                final TweakValue tweakValue = getValue(tweakName);
                final Number result = tweakValue.getNumberValue();
                return result.longValue();
            }
        };
    }

    /* package */ Tweak<Integer> intTweak(final String tweakName, final int defaultValue) {
        declareTweak(tweakName, defaultValue, LONG_TYPE);
        return new Tweak<Integer>() {
            @Override
            public Integer get() {
                final TweakValue tweakValue = getValue(tweakName);
                final Number result = tweakValue.getNumberValue();
                return result.intValue();
            }
        };
    }

    /* package */ Tweak<Byte> byteTweak(final String tweakName, final byte defaultValue) {
        declareTweak(tweakName, defaultValue, LONG_TYPE);
        return new Tweak<Byte>() {
            @Override
            public Byte get() {
                final TweakValue tweakValue = getValue(tweakName);
                final Number result = tweakValue.getNumberValue();
                return result.byteValue();
            }
        };
    }

    /* package */ Tweak<Short> shortTweak(final String tweakName, final short defaultValue) {
        declareTweak(tweakName, defaultValue, LONG_TYPE);
        return new Tweak<Short>() {
            @Override
            public Short get() {
                final TweakValue tweakValue = getValue(tweakName);
                final Number result = tweakValue.getNumberValue();
                return result.shortValue();
            }
        };
    }

    /* package */ Tweak<Boolean> booleanTweak(final String tweakName, final boolean defaultValue) {
        declareTweak(tweakName, defaultValue, BOOLEAN_TYPE);
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

    private void declareTweak(String tweakName, Object defaultValue, @TweakType int tweakType) {
        if (mTweakValues.containsKey(tweakName)) {
            Log.w(LOGTAG, "Attempt to define a tweak \"" + tweakName + "\" twice with the same name");
            return;
        }

        final TweakValue value = new TweakValue(tweakType, defaultValue, null, null, defaultValue);
        mTweakValues.put(tweakName, value);
        final int listenerSize = mTweakDeclaredListeners.size();
        for (int i = 0; i < listenerSize; i++) {
            mTweakDeclaredListeners.get(i).onTweakDeclared();
        }
    }

    // All access to mTweakValues must be synchronized
    private final Map<String, TweakValue> mTweakValues;
    private final List<OnTweakDeclaredListener> mTweakDeclaredListeners;

    private static final String LOGTAG = "MixpanelAPI.Tweaks";
}
