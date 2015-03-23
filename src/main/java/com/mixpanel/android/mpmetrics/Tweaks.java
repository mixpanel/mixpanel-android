package com.mixpanel.android.mpmetrics;

import android.os.Handler;
import android.util.Log;

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
    public interface TweakChangeCallback {
        public void onChange(Object value);
    }

    public interface TweakRegistrar {
        public void registerObjectForTweaks(Tweaks t, Object registrant);
    }

    public Tweaks(Handler callbackHandler, String tweakClassName) {
        mTweaks = new HashMap<String, Object>();
        mBindings = new HashMap<String, WeakHashMap<Object, List<TweakChangeCallback>>>();
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
        return mTweaks.get(tweakName);
    }

    public synchronized Map<String, Object> getAll() {
        return new HashMap<String, Object>(mTweaks);
    }

    public void bind(String tweakName, Object defaultValue, TweakChangeCallback callback) {
        bind(tweakName, defaultValue, this, callback);
    }

    public synchronized void bind(String tweakName, Object defaultValue, Object gcScope, TweakChangeCallback callback) {
        if (!mBindings.containsKey(tweakName)) {
            mBindings.put(tweakName, new WeakHashMap<Object, List<TweakChangeCallback>>());
        }
        final WeakHashMap<Object, List<TweakChangeCallback>> scopes = mBindings.get(tweakName);

        if (!mTweaks.containsKey(tweakName)) {
            mTweaks.put(tweakName, defaultValue);
        }

        if (null != callback) {
            List<TweakChangeCallback> callbackList = scopes.get(gcScope);
            if (null == callbackList) {
                callbackList = new ArrayList<TweakChangeCallback>();
            }
            callbackList.add(callback);
            scopes.put(gcScope, callbackList);

            runCallback(callback, get(tweakName));
        }
    }

    public synchronized void set(String tweakName, Object value) {
        mTweaks.put(tweakName, value);
        if (mBindings.containsKey(tweakName)) {
            final Collection<List<TweakChangeCallback>> callbackLists = mBindings.get(tweakName).values();
            for(List<TweakChangeCallback> callbackList:callbackLists) {
                final int size = callbackList.size();
                for (int i = 0; i < size; i++) {
                    final TweakChangeCallback callback = callbackList.get(i);
                    runCallback(callback, value);
                }
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

    private final Map<String, Object> mTweaks;
    private final Map<String, WeakHashMap<Object, List<TweakChangeCallback>>> mBindings;
    private final Handler mUiHandler;
    private final String mTweakClassName;

    // Access must be synchronized
    private static final Map<Class, TweakRegistrar> sRegistrars = new HashMap<Class, TweakRegistrar>();

    private static final String LOGTAG = "MixpanelAPI.Tweaks";
}
