package com.mixpanel.android.autocapture;

import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;

import com.mixpanel.android.util.MPLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Utility class for tracking root views (windows) in the application.
 *
 * <p>WindowSpy hooks into Android's internal WindowManagerGlobal to detect when
 * windows are added or removed. This enables autocapture to intercept touch events
 * on all windows including dialogs, popups, menus, and spinners.
 *
 * <p>Without WindowSpy, only Activity windows would be captured via ActivityLifecycleCallbacks.
 *
 * <p>This implementation is inspired by Square's Curtains library but kept minimal
 * to avoid external dependencies and version conflicts.
 */
final class WindowSpy {

    private static final String TAG = "MP.WindowSpy";

    private static volatile boolean sInstalled = false;
    private static final Object sLock = new Object();
    private static final List<OnRootViewChangedListener> sListeners = new CopyOnWriteArrayList<>();

    // Cached references to WindowManagerGlobal internals
    private static Object sWmgInstance;
    private static ArrayList<View> sOriginalViews;

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
     * Installs the WindowSpy hook into WindowManagerGlobal.
     *
     * <p>This method is safe to call multiple times; subsequent calls are no-ops.
     * If installation fails (e.g., due to reflection restrictions), the spy
     * gracefully degrades and only Activity windows will be tracked.
     */
    static void install() {
        if (sInstalled) {
            return;
        }

        synchronized (sLock) {
            if (sInstalled) {
                return;
            }

            // Requires API 19+ for WindowManagerGlobal
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                MPLog.d(TAG, "WindowSpy not supported below API 19");
                sInstalled = true; // Mark as installed to avoid retries
                return;
            }

            try {
                // Access WindowManagerGlobal singleton
                Class<?> wmgClass = Class.forName("android.view.WindowManagerGlobal");
                Method getInstance = wmgClass.getMethod("getInstance");
                sWmgInstance = getInstance.invoke(null);

                // Get the mViews field (holds all root views)
                Field mViewsField = wmgClass.getDeclaredField("mViews");
                mViewsField.setAccessible(true);

                @SuppressWarnings("unchecked")
                ArrayList<View> originalList = (ArrayList<View>) mViewsField.get(sWmgInstance);

                // Replace with delegating list that notifies on add/remove
                DelegatingViewList delegatingList = new DelegatingViewList(originalList);
                mViewsField.set(sWmgInstance, delegatingList);

                // Store reference to the LIVE delegating list (not the original)
                sOriginalViews = delegatingList;

                sInstalled = true;
                MPLog.d(TAG, "WindowSpy installed successfully");

            } catch (Exception e) {
                MPLog.e(TAG, "Failed to install WindowSpy, only Activity windows will be tracked", e);
                sInstalled = true; // Mark as installed to avoid retries
            }
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
     * Returns a copy of the current root views.
     *
     * <p>The returned list is a snapshot; changes to the actual root views
     * will not be reflected in this list.
     *
     * @return A list of current root views, or an empty list if WindowSpy is not installed.
     */
    @NonNull
    static List<View> getRootViews() {
        if (sOriginalViews != null) {
            synchronized (sOriginalViews) {
                return new ArrayList<>(sOriginalViews);
            }
        }
        return new ArrayList<>();
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

    /**
     * ArrayList subclass that intercepts add/remove operations to notify listeners.
     */
    private static class DelegatingViewList extends ArrayList<View> {

        DelegatingViewList(ArrayList<View> source) {
            super(source);
        }

        @Override
        public boolean add(View view) {
            boolean result = super.add(view);
            if (result && view != null) {
                notifyListeners(view, true);
            }
            return result;
        }

        @Override
        public void add(int index, View view) {
            super.add(index, view);
            if (view != null) {
                notifyListeners(view, true);
            }
        }

        @Override
        public View remove(int index) {
            View view = super.remove(index);
            if (view != null) {
                notifyListeners(view, false);
            }
            return view;
        }

        @Override
        public boolean remove(Object o) {
            boolean result = super.remove(o);
            if (result && o instanceof View) {
                notifyListeners((View) o, false);
            }
            return result;
        }
    }

    private WindowSpy() {
        // Prevent instantiation
    }
}
