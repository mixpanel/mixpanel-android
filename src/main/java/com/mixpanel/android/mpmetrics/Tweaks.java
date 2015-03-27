package com.mixpanel.android.mpmetrics;

import android.os.Handler;
import android.support.annotation.IntDef;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public Tweaks(Handler callbackHandler, String tweakClassName) {
        mTweaks = new HashMap<String, TweakValue>();
        mUiHandler = callbackHandler;
        mTweakClassName = tweakClassName;
    }

    public String getString(String tweakName) {
        String ret = null;
        try {
            ret = (String) get(tweakName);
        } catch (ClassCastException e) {
            ;
        }

        return ret;
    }

    public double getDouble(String tweakName) {
        double ret = 0.0;
        try {
            ret = (Double) get(tweakName);
        } catch (ClassCastException e) {
            ;
        }

        return ret;
    }

    public long getLong(String tweakName) {
        long ret = 0;
        try {
            ret = (Long) get(tweakName);
        } catch (ClassCastException e) {
            ;
        }

        return ret;
    }

    public boolean getBoolean(String tweakName) {
        boolean ret = false;
        try {
            ret = (Boolean) get(tweakName);
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

    public synchronized void bind(String tweakName, Object gcScope, TweakChangeCallback callback) {
        if (null == gcScope) {
            gcScope = this;
        }

        if (!mTweaks.containsKey(tweakName)) {
            Log.w(LOGTAG, "Attempt to bind to a tweak \"" + tweakName + "\" which doesn't exist");
            return;
        }

        final TweakValue value = mTweaks.get(tweakName);

        List<TweakChangeCallback> callbackList = value.bindings.get(gcScope);
        if (null == callbackList) {
            callbackList = new ArrayList<TweakChangeCallback>();
        }

        callbackList.add(callback);
        value.bindings.put(gcScope, callbackList);
        runCallback(callback, get(tweakName));
    }

    public synchronized void set(String tweakName, Object value) {
        if (!mTweaks.containsKey(tweakName)) {
            Log.w(LOGTAG, "Attempt to set a tweak \"" + tweakName + "\" which has never been defined.");
            return;
        }

        final TweakValue container = mTweaks.get(tweakName);
        container.setValue(value);

        final Collection<List<TweakChangeCallback>> callbackLists = container.bindings.values();
        for(List<TweakChangeCallback> descList:callbackLists) {
            final int size = descList.size();
            for (int i = 0; i < size; i++) {
                final TweakChangeCallback callback = descList.get(i);
                runCallback(callback, value);
            }
        }

    }

    public void set(Map<String, Object> tweakUpdates) {
        for(String tweakName : tweakUpdates.keySet()) {
            set(tweakName, tweakUpdates.get(tweakName));
        }
    }

    public void registerForTweaks(Object registrant) {
        synchronized (sRegistrars) {
            Class klass = registrant.getClass();
            while (klass != Object.class) {
                if (! sRegistrars.containsKey(klass)) {
                    final ClassLoader loader = klass.getClassLoader();
                    final Package registrantPackage = klass.getPackage();

                    try {
                        final Class found = loader.loadClass(registrantPackage.getName() + "." + mTweakClassName);
                        final Field instanceField = found.getField("TWEAK_REGISTRAR");
                        final TweakRegistrar registrar = (TweakRegistrar) instanceField.get(null);
                        sRegistrars.put(klass, registrar);
                    } catch (ClassNotFoundException e) {
                        ; // Ok, no such class.
                    } catch (NoSuchFieldException e) {
                        Log.w(LOGTAG, "Found a class named $$TWEAK_REGISTRAR in package " + registrantPackage.getName() + " but did not find an INSTANCE member.\n");
                        Log.i(LOGTAG, "    There may be a bug in the Tweaks Annotation processor, or otherwise an issue with the generated $$TWEAK_REGISTRAR class.");
                    } catch (IllegalAccessException e) {
                        Log.w(LOGTAG, "Found a class named $$TWEAK_REGISTRAR in package " + registrantPackage.getName() + " but INSTANCE member is not public or not static.\n");
                        Log.i(LOGTAG, "    There may be a bug in the Tweaks Annotation processor, or otherwise an issue with the generated $$TWEAK_REGISTRAR class.");
                    } catch (ClassCastException e) {
                        Log.w(LOGTAG, "Found a class named $$TWEAK_REGISTRAR in package " + registrantPackage.getName() + " but INSTANCE member can't be cast to a TweakRegistrar.\n");
                        Log.i(LOGTAG, "    There may be a bug in the Tweaks Annotation processor, or otherwise an issue with the generated $$TWEAK_REGISTRAR class.");
                    }
                }

                if (sRegistrars.containsKey(klass)) {
                    final TweakRegistrar registrar = sRegistrars.get(klass);
                    registrar.registerObjectForTweaks(this, registrant);
                }

                klass = klass.getSuperclass();
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

        if (thing instanceof Double) {
            return DOUBLE_TYPE;
        }

        if (thing instanceof Long) {
            return LONG_TYPE;
        }

        if (thing instanceof Boolean) {
            return BOOLEAN_TYPE;
        }

        return UNKNOWN_TYPE;
    }

    private static class TweakValue {

        public final WeakHashMap<Object, List<TweakChangeCallback>> bindings;
        public final TweakDescription description;

        public TweakValue(TweakDescription aDescription) {
            description = aDescription;
            bindings = new WeakHashMap<Object, List<TweakChangeCallback>>();
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
    private final String mTweakClassName;

    // Access must be synchronized
    private static final Map<Class, TweakRegistrar> sRegistrars = new HashMap<Class, TweakRegistrar>();

    private static final String LOGTAG = "MixpanelAPI.Tweaks";
}
