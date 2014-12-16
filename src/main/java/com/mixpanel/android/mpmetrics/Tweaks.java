package com.mixpanel.android.mpmetrics;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tweaks allows applications to specify dynamic variables that can be modified in the Mixpanel UI
 * and reflected in the app.
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

    /* package */ interface TweakChangeCallback { // TO BE MADE PUBLIC WHEN THESE FEATURES ARE EXPOSED
        public void onChange(Object value);
    }

    public Tweaks() {
        mTweaks = new HashMap<String, Object>();
        mBindings = new HashMap<String, List<TweakChangeCallback>>();
        mUiHandler = new Handler(Looper.getMainLooper());
    }

    public String getString(String tweakName, String defaultValue) {
        try {
            return (String) get(tweakName, defaultValue);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    public Integer getInteger(String tweakName, Integer defaultValue) {
        try {
            return (Integer) get(tweakName, defaultValue);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    public Long getLong(String tweakName, Long defaultValue) {
        try {
            return (Long) get(tweakName, defaultValue);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    public Float getFloat(String tweakName, Float defaultValue) {
        try {
            return (Float) get(tweakName, defaultValue);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    public Double getDouble(String tweakName, Double defaultValue) {
        try {
            return (Double) get(tweakName, defaultValue);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    public synchronized Object get(String tweakName, Object defaultValue) {
        if (!mTweaks.containsKey(tweakName)) {
            set(tweakName, defaultValue, true);
        }
        return mTweaks.get(tweakName);
    }

    public synchronized Map<String, Object> getAll() {
        return new HashMap<String, Object>(mTweaks);
    }

    public synchronized void bind(String tweakName, Object defaultValue, TweakChangeCallback callback) {
        if (!mBindings.containsKey(tweakName)) {
            mBindings.put(tweakName, new ArrayList<TweakChangeCallback>());
        }
        mBindings.get(tweakName).add(callback);
        runCallback(callback, get(tweakName, defaultValue));
    }

    public void set(String tweakName, Object value) {
        set(tweakName, value, false);
    }

    public void set(Map<String, Object> tweakUpdates) {
        for(String tweakName : tweakUpdates.keySet()) {
            set(tweakName, tweakUpdates.get(tweakName));
        }
    }

    private synchronized void set(String tweakName, Object value, boolean isDefault) {
        mTweaks.put(tweakName, value);

        if (!isDefault && mBindings.containsKey(tweakName)) {
            for(TweakChangeCallback changeCallback : mBindings.get(tweakName)) {
                runCallback(changeCallback, value);
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
    private final Map<String, List<TweakChangeCallback>> mBindings;
    private final Handler mUiHandler;
}
