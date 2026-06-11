package com.mixpanel.android.autocapture;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mixpanel.android.util.MPLog;

/**
 * Intercepts touch events from a Window by wrapping its Window.Callback.
 *
 * <p>Only processes ACTION_UP events with a single pointer (no multi-touch gestures).
 * Touch interception is non-blocking; the original callback always receives the event.
 */
final class TouchInterceptor implements Window.Callback {

    private static final String TAG = "MP.TouchInterceptor";

    private final Window mWindow;
    private final Window.Callback mOriginalCallback;
    private final TouchListener mTouchListener;

    /**
     * Listener interface for processed touch events.
     */
    interface TouchListener {
        /**
         * Called when a valid click is detected (single-pointer ACTION_UP).
         *
         * @param x          Screen X coordinate.
         * @param y          Screen Y coordinate.
         * @param decorView  The window's decor view.
         */
        void onTouchUp(float x, float y, @NonNull View decorView);
    }

    /**
     * Creates a TouchInterceptor and installs it on the window.
     *
     * @param window        The window to intercept touches from.
     * @param touchListener The listener to receive touch events.
     * @return The installed TouchInterceptor, or null if installation failed.
     */
    @Nullable
    static TouchInterceptor install(@NonNull Window window, @NonNull TouchListener touchListener) {
        try {
            Window.Callback originalCallback = window.getCallback();
            TouchInterceptor interceptor = new TouchInterceptor(window, originalCallback, touchListener);
            window.setCallback(interceptor);
            return interceptor;
        } catch (Exception e) {
            MPLog.e(TAG, "Failed to install TouchInterceptor", e);
            return null;
        }
    }

    /**
     * Uninstalls this interceptor from the window, restoring the original callback.
     */
    void uninstall() {
        try {
            // Only restore if we're still the current callback
            if (mWindow.getCallback() == this) {
                mWindow.setCallback(mOriginalCallback);
            }
        } catch (Exception e) {
            MPLog.e(TAG, "Failed to uninstall TouchInterceptor", e);
        }
    }

    private TouchInterceptor(
            @NonNull Window window,
            @Nullable Window.Callback originalCallback,
            @NonNull TouchListener touchListener) {
        mWindow = window;
        mOriginalCallback = originalCallback;
        mTouchListener = touchListener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        try {
            processTouchEvent(event);
        } catch (Exception e) {
            MPLog.e(TAG, "Error processing touch event", e);
        }

        // Always forward to original callback
        if (mOriginalCallback != null) {
            return mOriginalCallback.dispatchTouchEvent(event);
        }
        return false;
    }

    /**
     * Processes the touch event and notifies the listener if it's a valid click.
     */
    private void processTouchEvent(@NonNull MotionEvent event) {
        // Only process ACTION_UP events
        if (event.getActionMasked() != MotionEvent.ACTION_UP) {
            return;
        }

        // Only process single-pointer events (filter out multi-touch gestures)
        if (event.getPointerCount() != 1) {
            return;
        }

        View decorView = mWindow.getDecorView();
        if (decorView == null) {
            return;
        }

        // Use raw coordinates (screen space)
        float x = event.getRawX();
        float y = event.getRawY();

        mTouchListener.onTouchUp(x, y, decorView);
    }

    // Delegate all other Window.Callback methods to the original callback

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mOriginalCallback != null && mOriginalCallback.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        return mOriginalCallback != null && mOriginalCallback.dispatchKeyShortcutEvent(event);
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent event) {
        return mOriginalCallback != null && mOriginalCallback.dispatchTrackballEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return mOriginalCallback != null && mOriginalCallback.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        return mOriginalCallback != null && mOriginalCallback.dispatchPopulateAccessibilityEvent(event);
    }

    @Nullable
    @Override
    public View onCreatePanelView(int featureId) {
        return mOriginalCallback != null ? mOriginalCallback.onCreatePanelView(featureId) : null;
    }

    @Override
    public boolean onCreatePanelMenu(int featureId, @NonNull android.view.Menu menu) {
        return mOriginalCallback != null && mOriginalCallback.onCreatePanelMenu(featureId, menu);
    }

    @Override
    public boolean onPreparePanel(int featureId, @Nullable View view, @NonNull android.view.Menu menu) {
        return mOriginalCallback != null && mOriginalCallback.onPreparePanel(featureId, view, menu);
    }

    @Override
    public boolean onMenuOpened(int featureId, @NonNull android.view.Menu menu) {
        return mOriginalCallback != null && mOriginalCallback.onMenuOpened(featureId, menu);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, @NonNull android.view.MenuItem item) {
        return mOriginalCallback != null && mOriginalCallback.onMenuItemSelected(featureId, item);
    }

    @Override
    public void onWindowAttributesChanged(android.view.WindowManager.LayoutParams attrs) {
        if (mOriginalCallback != null) {
            mOriginalCallback.onWindowAttributesChanged(attrs);
        }
    }

    @Override
    public void onContentChanged() {
        if (mOriginalCallback != null) {
            mOriginalCallback.onContentChanged();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (mOriginalCallback != null) {
            mOriginalCallback.onWindowFocusChanged(hasFocus);
        }
    }

    @Override
    public void onAttachedToWindow() {
        if (mOriginalCallback != null) {
            mOriginalCallback.onAttachedToWindow();
        }
    }

    @Override
    public void onDetachedFromWindow() {
        if (mOriginalCallback != null) {
            mOriginalCallback.onDetachedFromWindow();
        }
    }

    @Override
    public void onPanelClosed(int featureId, @NonNull android.view.Menu menu) {
        if (mOriginalCallback != null) {
            mOriginalCallback.onPanelClosed(featureId, menu);
        }
    }

    @Override
    public boolean onSearchRequested() {
        return mOriginalCallback != null && mOriginalCallback.onSearchRequested();
    }

    // API 23+ method - delegate via helper class to avoid AnimalSniffer violation
    @Override
    public boolean onSearchRequested(android.view.SearchEvent searchEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mOriginalCallback != null) {
            return Api23Helper.onSearchRequested(mOriginalCallback, searchEvent);
        }
        return false;
    }

    @Nullable
    @Override
    public android.view.ActionMode onWindowStartingActionMode(android.view.ActionMode.Callback callback) {
        return mOriginalCallback != null ? mOriginalCallback.onWindowStartingActionMode(callback) : null;
    }

    // API 23+ method - delegate via helper class to avoid AnimalSniffer violation
    @Nullable
    @Override
    public android.view.ActionMode onWindowStartingActionMode(android.view.ActionMode.Callback callback, int type) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mOriginalCallback != null) {
            return Api23Helper.onWindowStartingActionMode(mOriginalCallback, callback, type);
        }
        return null;
    }

    /**
     * Helper class to isolate API 23+ method calls.
     * This class is only loaded on API 23+ devices, avoiding verification errors on older APIs.
     */
    @TargetApi(Build.VERSION_CODES.M)
    private static class Api23Helper {
        static boolean onSearchRequested(Window.Callback callback, android.view.SearchEvent searchEvent) {
            return callback.onSearchRequested(searchEvent);
        }

        static android.view.ActionMode onWindowStartingActionMode(
                Window.Callback callback, android.view.ActionMode.Callback actionModeCallback, int type) {
            return callback.onWindowStartingActionMode(actionModeCallback, type);
        }
    }

    @Override
    public void onActionModeStarted(android.view.ActionMode mode) {
        if (mOriginalCallback != null) {
            mOriginalCallback.onActionModeStarted(mode);
        }
    }

    @Override
    public void onActionModeFinished(android.view.ActionMode mode) {
        if (mOriginalCallback != null) {
            mOriginalCallback.onActionModeFinished(mode);
        }
    }
}
