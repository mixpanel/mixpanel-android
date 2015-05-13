package com.mixpanel.android.mpmetrics;

import android.os.Handler;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tweaks allows applications to specify dynamic variables that can be modified in the Mixpanel UI
 * and delivered to your application.
 *
 * Example (assignment):
 *              String welcomeMsg = getTweaks().getString("welcome message", "Welcome to the app!");
 *
 *
 * Example (callback):
 *              final Button mButton = (Button) findViewById(R.id.button2);
 *              getTweaks().bind("Start button", "Click to start!", new ABTesting.TweakChangeCallback() {
 *                  @Override
 *                  public void onChange(Object o) {
 *                      mButton.setText((String) o);
 *                  }
 *              });
 *
 * Tweaks will be accessed from multiple threads, and must be thread safe.
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

    public static final @TweakType int UNKNOWN_TYPE = 0;
    public static final @TweakType int BOOLEAN_TYPE = 1;
    public static final @TweakType int DOUBLE_TYPE = 2;
    public static final @TweakType int LONG_TYPE = 3;
    public static final @TweakType int STRING_TYPE = 4;

    public interface TweakChangeCallback {
        public void onChange(Object value);
    }

    public interface TweakRegistrar {
        public void declareTweaks(Tweaks t);
        public void registerObjectForTweaks(Tweaks t, Object registrant);
    }

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

    public static TweakRegistrar findRegistrar(String packageName) {
        Tweaks.TweakRegistrar ret = null;
        try {
            final ClassLoader loader = TweakRegistrar.class.getClassLoader();
            final Class found = loader.loadClass(packageName + ".$$TWEAK_REGISTRAR");
            final Field instanceField = found.getField("TWEAK_REGISTRAR");
            ret = (Tweaks.TweakRegistrar) instanceField.get(null);
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

    public Tweaks(Handler callbackHandler, @Nullable TweakRegistrar registrar) {
        mTweaks = new HashMap<String, TweakValue>();
        mUiHandler = callbackHandler;
        mRegistrar = registrar;

        if (null != mRegistrar) {
            synchronized (mRegistrar) {
                mRegistrar.declareTweaks(this);
            }
        }
    }

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

    public synchronized Object get(String tweakName) {
        Object ret = null;
        final TweakValue value = mTweaks.get(tweakName);
        if (null != value) {
            ret = value.getValue();
        }
        return ret;
    }

    public synchronized Map<String, TweakDescription> getDescriptions() {
        final Map<String, TweakDescription> ret = new HashMap<String, TweakDescription>();
        for (Map.Entry<String, TweakValue> entry:mTweaks.entrySet()) {
            ret.put(entry.getKey(), entry.getValue().description);
        }
        return ret;
    }

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

    /* Only one callback will be stored per scope. When scope is garbage collected, references to callback will be removed */
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

    public void registerForTweaks(Object registrant) {
        if (null != mRegistrar) {
            synchronized (mRegistrar) {
                mRegistrar.registerObjectForTweaks(this, registrant);
            }
        }
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
