package com.mixpanel.android.mpmetrics;

import android.os.Handler;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tweaks are a mechanism for declaring and receiving dynamic values that you can change and deploy
 * to users through Mixpanel. You can declare and access tweaks in your code, and then deploy new
 * values to some or all of your users using a Mixpanel A/B test.
 *
 * You can declare a tweak with {@link #defineTweak(String, Object)}. Later you can access the value
 * of the tweak using {@link #getBoolean(String)}, {@link #getLong(String)},
 * {@link #getString(String)}, or {@link #getDouble(String)}.
 * Under ordinary circumstances, the value returned from the <em>get...</em> method will be the same
 * as the value you provided. However, if you've deployed a new value from the Mixpanel UI, the
 * value you deployed will be returned instead. You can use tweaks to enable and disable features,
 * change constants, or otherwise "tweak" your application in the field.
 *
 * For example, you may want to add an advertisement to your application, but make sure it doesn't hurt
 * your retention before rolling it out to all of your users. Early in your application's lifecycle, you can call
 * <pre>
 * {@code
 *     MixpanelAPI mixpanel = MixpanelAPI.getInstance(...);
 *     mixpanel.getTweaks().defineTweak("Show Advertisement", false);
 * }
 * </pre>
 *
 * Later, you can call
 *
 * <pre>
 * {@code
 *     if (mixpanel.getTweaks().getBoolean("Show Advertisement")) {
 *         showTheAd();
 *     }
 * }
 * </pre>
 *
 * When we defined "Show Advertisement", we gave a default value of false, so to start out, we won't
 * show the ad. However, when you log in to the Mixpanel A/B test editor, you'll be able to see
 * "Show Advertisement", and change it's value to true for some or all of your users.
 *
 *
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
    public @interface TweakType {}

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
     * You can use this interface together with {@link #bind(String, Object, TweakChangeCallback)} to
     * get callbacks when a new tweak value is received from Mixpanel.
     */
    public interface TweakChangeCallback {
        /**
         * onChange will be called at least once for each value of the tweak discovered.
         * It will be called on the main UI thread. onChange may be called multiple times,
         * both with values from Mixpanel or with the default tweak value.
         */
        void onChange(Object value);
    }

    /**
     * Represents a tweak (but not a tweak value) known to the system. This class
     * is used internally to expose tweaks to the Mixpanel UI,
     * and will likely not be directly useful to code that imports the Mixpanel library.
     */
    public static class TweakDescription {
        public TweakDescription(@TweakType int aType, Object aDefaultValue, Number aMin, Number aMax) {
            type = aType;
            defaultValue = aDefaultValue;
            minimum = aMin;
            maximum = aMax;
        }

        public final @TweakType int type;
        public final Number minimum;
        public final Number maximum;
        public final Object defaultValue;
    }

    /**
     * Get the value of the given tweak.
     *
     * @return Will return a string if the current tweak value can be cast to a string,
     * otherwise will return null.
     */
    public String getString(String tweakName) {
        String ret = null;
        try {
            final Object raw = get(tweakName);
            ret = (String) raw;
        } catch (ClassCastException e) {
            ;
        }

        return ret;
    }

    /**
     * Get the value of the given tweak.
     *
     * @return Will return a double if the current value of the tweak is numeric, otherwise
     * will return 0.0;
     */
    public double getDouble(String tweakName) {
        double ret = 0.0;
        try {
            final Object raw = get(tweakName);
            ret = ((Number) raw).doubleValue();
        } catch (ClassCastException e) {
            ;
        }

        return ret;
    }

    /**
     * Get the value of the given tweak.
     *
     * @return Will return a long if the current value of the tweak is numeric, otherwise
     * will return 0;
     */
    public long getLong(String tweakName) {
        long ret = 0;
        try {
            final Object raw = get(tweakName);
            ret = ((Number) raw).longValue();
        } catch (ClassCastException e) {
            ;
        }

        return ret;
    }

    /**
     * Get the value of the given tweak.
     *
     * @return Will return a boolean if the current value of the tweak is numeric, otherwise
     * will return false;
     */
    public boolean getBoolean(String tweakName) {
        boolean ret = false;
        try {
            final Object raw = get(tweakName);
            ret = (Boolean) raw;
        } catch (ClassCastException e) {
            ;
        }

        return ret;
    }

    /**
     * Get the value of the given tweak.
     *
     * @return Will return the value of the given tweak.
     */
    public synchronized Object get(String tweakName) {
        Object ret = null;
        final TweakValue value = mTweaks.get(tweakName);
        if (null != value) {
            ret = value.getValue();
        }
        return ret;
    }

    /**
     * Declare a new tweak and provide its default value.
     *
     * Tweaks are only visible in the Mixpanel UI after defineTweak has been called, so
     * it's good to define them early in the lifetime of your app.
     *
     * @param defaultValue
     */
    public synchronized void defineTweak(String tweakName, Object defaultValue) {
        if (mTweaks.containsKey(tweakName)) {
            Log.w(LOGTAG, "Attempt to define a tweak \"" + tweakName + "\" twice with the same name");
            return;
        }

        final @TweakType int tweakType = determineType(defaultValue);
        final TweakDescription description = new TweakDescription(tweakType, defaultValue, null, null);
        final TweakValue value = new TweakValue(description);
        mTweaks.put(tweakName, value);
    }

    /**
     * Install a callback for a given tweak. Only a single callback is stored for each tweak
     * name - multiple calls to bind will overwrite previous callbacks.
     *
     * @param tweakName the name of the tweak to observe. When this tweak's value is updated, the callback will be called.
     * @param scope if non-null, the callback will be associated with a weak reference to the given scope. You can use scope to prevent leaking callbacks when interested objects are garbage collected.
     * @param callback a {@link com.mixpanel.android.mpmetrics.Tweaks.TweakChangeCallback}. When the given tweak's value changes, the {@link com.mixpanel.android.mpmetrics.Tweaks.TweakChangeCallback#onChange(Object)} method will be called.
     */
    public synchronized void bind(String tweakName, Object scope, TweakChangeCallback callback) {
        if (null == scope) {
            scope = this;
        }

        if (!mTweaks.containsKey(tweakName)) {
            Log.w(LOGTAG, "Attempt to bind to a tweak \"" + tweakName + "\" which doesn't exist");
            return;
        }

        final TweakValue value = mTweaks.get(tweakName);
        value.bindings.put(scope, callback);
        runCallback(callback, get(tweakName));
    }

    /**
     * Manually set the value of a tweak. Most users of the library will not need to call this
     * directly - instead, the library will call set when new values of the tweak are published.
     */
    public synchronized void set(String tweakName, Object value) {
        if (!mTweaks.containsKey(tweakName)) {
            Log.w(LOGTAG, "Attempt to set a tweak \"" + tweakName + "\" which has never been defined.");
            return;
        }

        final TweakValue container = mTweaks.get(tweakName);
        container.setValue(value);

        final Collection<TweakChangeCallback> callbackLists = container.bindings.values();
        for(final TweakChangeCallback callback:callbackLists) {
            runCallback(callback, value);
        }
    }

    /**
     * Returns the descriptions of all tweaks currently introduced with {@link #defineTweak(String, Object)}.
     *
     * The Mixpanel library uses this method internally to expose tweaks and their types to the UI. Most
     * users will not need to call this method directly.
     */
    public synchronized Map<String, TweakDescription> getDescriptions() {
        final Map<String, TweakDescription> ret = new HashMap<String, TweakDescription>();
        for (Map.Entry<String, TweakValue> entry : mTweaks.entrySet()) {
            ret.put(entry.getKey(), entry.getValue().description);
        }
        return ret;
    }

    /* package */ Tweaks(Handler callbackHandler, @Nullable TweakRegistrar registrar) {
        mTweaks = new HashMap<String, TweakValue>();
        mUiHandler = callbackHandler;
        mRegistrar = registrar;

        if (null != mRegistrar) {
            synchronized (mRegistrar) {
                mRegistrar.declareTweaks(this);
            }
        }
    }

    /* package */ void registerForTweaks(Object registrant) {
        if (null != mRegistrar) {
            synchronized (mRegistrar) {
                mRegistrar.registerObjectForTweaks(this, registrant);
            }
        }
    }

    /* package */ static TweakRegistrar findRegistrar(String packageName) {
        TweakRegistrar ret = null;
        try {
            final ClassLoader loader = TweakRegistrar.class.getClassLoader();
            final Class found = loader.loadClass(packageName + ".$$TWEAK_REGISTRAR");
            final Field instanceField = found.getField("TWEAK_REGISTRAR");
            ret = (TweakRegistrar) instanceField.get(null);
        } catch (ClassNotFoundException e) {
            // Ok, no need for
        } catch (NoSuchFieldException e) {
            Log.w(LOGTAG, "Found apparent TweakRegistrar class, but with no TWEAK_REGISTRAR member", e);
        } catch (IllegalAccessException e) {
            Log.w(LOGTAG, "Found apparent TweakRegistrar class but TWEAK_REGISTRAR wasn't public and/or static", e);
        } catch (ClassCastException e) {
            Log.w(LOGTAG, "Found apparent TweakRegistrar class, but TWEAK_REGISTRAR was not a TweakRegistrar", e);
        }

        return ret;
    }

    private void runCallback(final TweakChangeCallback callback, final Object value) {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onChange(value);
            }
        });
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

    private static class TweakValue {

        public final WeakHashMap<Object, TweakChangeCallback> bindings;
        public final TweakDescription description;

        public TweakValue(TweakDescription aDescription) {
            description = aDescription;
            bindings = new WeakHashMap<Object, TweakChangeCallback>();
            mValue = aDescription.defaultValue;
        }

        public Object getValue() {
            return mValue;
        }

        public void setValue(Object value) {
            mValue = value;
        }

        private Object mValue;
    }

    private final Map<String, TweakValue> mTweaks;
    private final Handler mUiHandler;
    private final TweakRegistrar mRegistrar;

    // Access must be synchronized
    private static final Map<Package, TweakRegistrar> sRegistrars = new HashMap<Package, TweakRegistrar>();

    private static final String LOGTAG = "MixpanelAPI.Tweaks";
}
