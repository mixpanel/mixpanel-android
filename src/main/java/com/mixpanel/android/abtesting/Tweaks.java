package com.mixpanel.android.abtesting;

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
 */
public class Tweaks {
    Map<String, Object> tweaks = new HashMap<String, Object>();
    Map<String, List<TweakChangeCallback>> bindings = new HashMap<String, List<TweakChangeCallback>>();

    public String getString(String tweakName, String defaultValue) {
        return (String) get(tweakName, defaultValue);
    }

    public Integer getInteger(String tweakName, Integer defaultValue) {
        return (Integer) get(tweakName, defaultValue);
    }

    public Long getLong(String tweakName, Long defaultValue) {
        return (Long) get(tweakName, defaultValue);
    }

    public Float getFloat(String tweakName, Float defaultValue) {
        return (Float) get(tweakName, defaultValue);
    }

    public Double getDouble(String tweakName, Double defaultValue) {
        return (Double) get(tweakName, defaultValue);
    }

    public Object get(String tweakName, Object defaultValue) {
        Object value;
        synchronized (tweaks) {
            if (!tweaks.containsKey(tweakName)) {
                set(tweakName, defaultValue, true);
            }
            value = tweaks.get(tweakName);
        }
        return value;
    }

    public Map<String, Object> getAll() {
        HashMap<String, Object> copy;
        synchronized (tweaks) {
            copy = new HashMap<String, Object>(tweaks);
        }
        return copy;
    }

    public void bind(String tweakName, Object defaultValue, TweakChangeCallback callback) {
        synchronized (bindings) {
            if (!bindings.containsKey(tweakName)) {
                bindings.put(tweakName, new ArrayList<TweakChangeCallback>());
            }
            bindings.get(tweakName).add(callback);
        }
        callback.onChange(get(tweakName, defaultValue));
    }

    public void set(String tweakName, Object value) {
        set(tweakName, value, false);
    }

    public void set(Map<String, Object> tweakUpdates) {
        for(String tweakName : tweakUpdates.keySet()) {
            set(tweakName, tweakUpdates.get(tweakName));
        }
    }

    private void set(String tweakName, Object value, boolean isDefault) {
        synchronized (tweaks) {
            tweaks.put(tweakName, value);
        }

        synchronized (bindings) {
            if (!isDefault && bindings.containsKey(tweakName)) {
                for(TweakChangeCallback changeCallback : bindings.get(tweakName)) {
                    changeCallback.onChange(value); // todo: should we run this on the UIThread?
                }
            }
        }
    }

    public interface TweakChangeCallback {
        public void onChange(Object value);
    }
}
