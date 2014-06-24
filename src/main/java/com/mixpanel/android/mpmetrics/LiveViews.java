package com.mixpanel.android.mpmetrics;

import android.app.Activity;

import com.mixpanel.android.introspector.EditInstructions;

public class LiveViews {

    public synchronized void registerActivity(final Activity a) {
        // TODO register activities, add OnGlobalLayoutListeners to DecorView
    }

    public synchronized void unregisterActivity(final Activity a) {
        // TODO unregister activities, remove OnGlobalLayoutListeners to DecorView
    }

    public synchronized void applyEdit(final EditInstructions edit) {
        // TODO the right stuff
    }

    public synchronized void removeEdit() {
        // TODO remove the edit
    }
}
