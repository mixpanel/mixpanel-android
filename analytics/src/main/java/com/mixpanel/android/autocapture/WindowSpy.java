package com.mixpanel.android.autocapture;

import android.view.View;

import androidx.annotation.NonNull;

import com.mixpanel.android.util.MPLog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import curtains.Curtains;
import curtains.OnRootViewsChangedListener;

/**
 * Adapter over Square's Curtains library for tracking root views (windows).
 *
 * <p>Delegates to {@link Curtains#getOnRootViewsChangedListeners()} for window
 * add/remove notifications. This enables autocapture to intercept touch events
 * on all windows including dialogs, popups, menus, and spinners.
 *
 * <p>Using Curtains instead of custom reflection ensures compatibility with
 * other libraries that also observe root views (e.g., Mixpanel Session Replay).
 */
final class WindowSpy {

    private static final String TAG = "MP.WindowSpy";

    private static final List<OnRootViewChangedListener> sListeners = new CopyOnWriteArrayList<>();
    private static volatile boolean sInstalled = false;
    private static OnRootViewsChangedListener sCurtainsListener;

    /**
     * Listener interface for root view changes.
     */
    interface OnRootViewChangedListener {
        /**
         * Called when a root view is added or removed.
         *
         * @param view  The root view that changed.
         * @param added {@code true} if the view was added, {@code false} if removed.
         */
        void onRootViewChanged(@NonNull View view, boolean added);
    }

    /**
     * Installs the WindowSpy hook via Curtains.
     *
     * <p>This method is safe to call multiple times; subsequent calls are no-ops.
     * Curtains handles the underlying WindowManagerGlobal reflection and is
     * compatible with other Curtains consumers (e.g., Session Replay SDK).
     */
    static void install() {
        if (sInstalled) {
            return;
        }

        synchronized (WindowSpy.class) {
            if (sInstalled) {
                return;
            }

            try {
                sCurtainsListener = (view, added) -> notifyListeners(view, added);
                Curtains.getOnRootViewsChangedListeners().add(sCurtainsListener);
                sInstalled = true;
                MPLog.d(TAG, "WindowSpy installed via Curtains");
            } catch (Exception e) {
                // Defensive: Curtains relies on WindowManagerGlobal.mViews reflection
                // which has been stable since API 17 but the library is no longer
                // actively maintained. If a future Android version restricts this
                // internal API, we degrade gracefully — activity windows still work
                // via lifecycle callbacks, only dialogs/popups lose coverage.
                MPLog.e(TAG, "Failed to install WindowSpy via Curtains, "
                        + "only Activity windows will be tracked", e);
            }
        }
    }

    /**
     * Uninstalls the WindowSpy hook, removing the Curtains listener.
     */
    static void uninstall() {
        synchronized (WindowSpy.class) {
            if (sCurtainsListener != null) {
                try {
                    Curtains.getOnRootViewsChangedListeners().remove(sCurtainsListener);
                } catch (Exception e) {
                    MPLog.e(TAG, "Failed to remove Curtains listener", e);
                }
                sCurtainsListener = null;
            }
            sInstalled = false;
        }
    }

    /**
     * Adds a listener to be notified of root view changes.
     *
     * @param listener The listener to add.
     */
    static void addListener(@NonNull OnRootViewChangedListener listener) {
        if (!sListeners.contains(listener)) {
            sListeners.add(listener);
        }
    }

    /**
     * Removes a previously added listener.
     *
     * @param listener The listener to remove.
     */
    static void removeListener(@NonNull OnRootViewChangedListener listener) {
        sListeners.remove(listener);
    }

    /**
     * Notifies all registered listeners of a root view change.
     */
    private static void notifyListeners(@NonNull View view, boolean added) {
        for (OnRootViewChangedListener listener : sListeners) {
            try {
                listener.onRootViewChanged(view, added);
            } catch (Exception e) {
                MPLog.e(TAG, "Error notifying WindowSpy listener", e);
            }
        }
    }

    private WindowSpy() {
        // Prevent instantiation
    }
}
