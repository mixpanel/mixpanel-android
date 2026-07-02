package com.mixpanel.android.autocapture;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mixpanel.android.mpmetrics.AutocaptureOptions;
import com.mixpanel.android.util.MPLog;

import org.json.JSONObject;

import android.view.MotionEvent;
import android.view.ViewConfiguration;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Main coordinator for autocapture functionality.
 *
 * <p>Manages the lifecycle of touch interception, semantic extraction, and event detection
 * (click, rage click, dead click). Integrates with the application lifecycle to attach
 * and detach interceptors appropriately.
 *
 * <p>Thread safety: All public methods must be called from the main thread.
 */
public final class AutocaptureManager implements
        Application.ActivityLifecycleCallbacks,
        WindowSpy.OnRootViewChangedListener,
        TouchInterceptor.TouchListener,
        DeadClickDetector.DeadClickListener {

    private static final String TAG = "MP.AutocaptureManager";

    /**
     * Interface for emitting tracked events.
     */
    public interface EventEmitter {
        /**
         * Emits a tracked event.
         *
         * @param eventName  The event name (e.g., "$mp_click").
         * @param properties The event properties.
         */
        void emit(@NonNull String eventName, @NonNull JSONObject properties);
    }

    private final Context mContext;
    private final AutocaptureOptions mOptions;
    private final EventEmitter mEmitter;

    @Nullable
    private RageClickTracker mRageClickTracker;
    @Nullable
    private DeadClickDetector mDeadClickDetector;

    // Track interceptors per window to avoid duplicate installations
    private final Map<Window, TouchInterceptor> mWindowInterceptors = new WeakHashMap<>();

    // Track the current activity for lifecycle management
    @Nullable
    private WeakReference<Activity> mCurrentActivityRef;

    private boolean mStarted = false;

    /**
     * Creates an AutocaptureManager.
     *
     * @param context The application context.
     * @param options The autocapture configuration options.
     * @param emitter The event emitter for tracked events.
     */
    public AutocaptureManager(
            @NonNull Context context,
            @NonNull AutocaptureOptions options,
            @NonNull EventEmitter emitter) {
        mContext = context.getApplicationContext();
        mOptions = options;
        mEmitter = emitter;

        // Initialize trackers based on options
        if (mOptions.getRageClickOptions().isEnabled()) {
            mRageClickTracker = new RageClickTracker(mOptions.getRageClickOptions(), mContext);
        }

        if (mOptions.getDeadClickOptions().isEnabled()) {
            mDeadClickDetector = new DeadClickDetector(mOptions.getDeadClickOptions(), this);
        }
    }

    /**
     * Starts autocapture.
     *
     * <p>Registers lifecycle callbacks and installs window tracking.
     * This should be called once during SDK initialization.
     */
    public void start() {
        if (mStarted) {
            return;
        }

        try {
            // Register activity lifecycle callbacks
            if (mContext instanceof Application) {
                ((Application) mContext).registerActivityLifecycleCallbacks(this);
            }

            // Install WindowSpy for dialog/popup tracking
            WindowSpy.install();
            WindowSpy.addListener(this);

            mStarted = true;
            MPLog.d(TAG, "Autocapture started");

        } catch (Exception e) {
            MPLog.e(TAG, "Failed to start autocapture", e);
        }
    }

    /**
     * Stops autocapture.
     *
     * <p>Unregisters lifecycle callbacks and removes all interceptors.
     */
    public void stop() {
        if (!mStarted) {
            return;
        }

        try {
            // Unregister activity lifecycle callbacks
            if (mContext instanceof Application) {
                ((Application) mContext).unregisterActivityLifecycleCallbacks(this);
            }

            // Remove WindowSpy listener
            WindowSpy.removeListener(this);

            // Uninstall all interceptors (copy to avoid ConcurrentModificationException from WeakHashMap)
            java.util.List<TouchInterceptor> interceptors = new java.util.ArrayList<>(mWindowInterceptors.values());
            for (TouchInterceptor interceptor : interceptors) {
                if (interceptor != null) {
                    interceptor.uninstall();
                }
            }
            mWindowInterceptors.clear();

            // Cancel pending detection
            if (mDeadClickDetector != null) {
                mDeadClickDetector.cancelDetection();
            }

            // Clear rage click history
            if (mRageClickTracker != null) {
                mRageClickTracker.clear();
            }

            mStarted = false;
            MPLog.d(TAG, "Autocapture stopped");

        } catch (Exception e) {
            MPLog.e(TAG, "Error stopping autocapture", e);
        }
    }

    // ==================== TouchInterceptor.TouchListener ====================

    @Override
    public void onTouchUp(float x, float y, @NonNull View decorView) {
        try {
            processTouchEvent(x, y, decorView);
        } catch (Exception e) {
            MPLog.e(TAG, "Error processing touch event", e);
        }
    }

    private void processTouchEvent(float x, float y, @NonNull View decorView) {
        // Extract semantics from the touched view
        ClickEvent.Builder builder = SemanticExtractor.extract(decorView, x, y);
        if (builder == null) {
            // No view found at position
            return;
        }

        ClickEvent clickEvent = builder.build();

        // Track basic click
        if (mOptions.getClickOptions().isEnabled()) {
            emitEvent(AutocaptureDefaults.EVENT_CLICK, clickEvent);
        }

        // Check for rage click
        if (mRageClickTracker != null) {
            ClickEvent rageClick = mRageClickTracker.recordClick(clickEvent);
            if (rageClick != null) {
                emitEvent(AutocaptureDefaults.EVENT_RAGE_CLICK, rageClick);
            }
        }

        // Start dead click detection
        if (mDeadClickDetector != null) {
            mDeadClickDetector.startDetection(clickEvent, decorView);
        }
    }

    // ==================== DeadClickDetector.DeadClickListener ====================

    @Override
    public void onDeadClickDetected(@NonNull ClickEvent clickEvent) {
        try {
            emitEvent(AutocaptureDefaults.EVENT_DEAD_CLICK, clickEvent);
        } catch (Exception e) {
            MPLog.e(TAG, "Error emitting dead click event", e);
        }
    }

    // ==================== WindowSpy.OnRootViewChangedListener ====================

    @Override
    public void onRootViewChanged(@NonNull View view, boolean added) {
        try {
            if (added) {
                // Notify dead click detector - new window is a UI change
                if (mDeadClickDetector != null) {
                    mDeadClickDetector.onWindowAdded();
                }

                // Try to attach interceptor to the new window
                Window window = getWindowFromView(view);
                if (window != null && !mWindowInterceptors.containsKey(window)) {
                    attachInterceptor(window);
                } else if (window == null) {
                    // No Window object (e.g., PopupWindow, DropdownMenu, Spinner popup).
                    // Attach a touch listener directly on the root view as a fallback.
                    attachRootViewTouchListener(view);
                }
            } else {
                // Window removed (dialog/sheet dismissed) — this is a UI change
                if (mDeadClickDetector != null) {
                    mDeadClickDetector.cancelDetection();
                }
            }
        } catch (Exception e) {
            MPLog.e(TAG, "Error handling root view change", e);
        }
    }

    // ==================== Activity Lifecycle Callbacks ====================

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        // No action needed
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        // No action needed
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        try {
            mCurrentActivityRef = new WeakReference<>(activity);

            // Attach interceptor to activity window
            Window window = activity.getWindow();
            if (window != null && !mWindowInterceptors.containsKey(window)) {
                attachInterceptor(window);
            }
        } catch (Exception e) {
            MPLog.e(TAG, "Error in onActivityResumed", e);
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        try {
            // Cancel dead click detection - activity transition is a UI change
            if (mDeadClickDetector != null) {
                mDeadClickDetector.cancelDetection();
            }

            // Clear rage click history on activity change
            if (mRageClickTracker != null) {
                mRageClickTracker.clear();
            }
        } catch (Exception e) {
            MPLog.e(TAG, "Error in onActivityPaused", e);
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        // No action needed
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        // No action needed
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        try {
            // Clean up interceptor for this activity's window
            Window window = activity.getWindow();
            if (window != null) {
                TouchInterceptor interceptor = mWindowInterceptors.remove(window);
                if (interceptor != null) {
                    interceptor.uninstall();
                }
            }
        } catch (Exception e) {
            MPLog.e(TAG, "Error in onActivityDestroyed", e);
        }
    }

    // ==================== Private Helpers ====================

    /**
     * Attaches a touch listener to a root view that has no Window (e.g., Toast overlays).
     *
     * <p>This is a best-effort fallback for windowless root views. It intercepts taps
     * (ACTION_DOWN + ACTION_UP within touch slop and duration threshold) without consuming
     * them. Note: PopupWindow-based views (DropdownMenu, Spinner popups, PopupMenu) are
     * not supported — their child views consume touches before they reach this listener.
     */
    @SuppressWarnings("ClickableViewAccessibility")
    private void attachRootViewTouchListener(@NonNull View rootView) {
        int touchSlop = ViewConfiguration.get(rootView.getContext()).getScaledTouchSlop();
        final int touchSlopSq = touchSlop * touchSlop;
        final float[] down = new float[3]; // downX, downY, downTime

        rootView.setOnTouchListener((v, event) -> {
            try {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    down[0] = event.getRawX();
                    down[1] = event.getRawY();
                    down[2] = event.getEventTime();
                } else if (action == MotionEvent.ACTION_UP
                        && event.getPointerCount() == 1) {
                    long duration = event.getEventTime() - (long) down[2];
                    float dx = event.getRawX() - down[0];
                    float dy = event.getRawY() - down[1];
                    if (down[2] > 0 && duration <= 800
                            && (dx * dx + dy * dy) <= touchSlopSq) {
                        onTouchUp(event.getRawX(), event.getRawY(), rootView);
                    }
                }
            } catch (Exception e) {
                MPLog.e(TAG, "Error in root view touch listener", e);
            }
            return false; // Don't consume - let the view handle the touch normally
        });
        MPLog.d(TAG, "Attached touch listener to windowless root view: "
                + rootView.getClass().getSimpleName());
    }

    private void attachInterceptor(@NonNull Window window) {
        TouchInterceptor interceptor = TouchInterceptor.install(window, this);
        if (interceptor != null) {
            mWindowInterceptors.put(window, interceptor);
            MPLog.d(TAG, "Attached interceptor to window: " + window);
        }
    }

    private void emitEvent(@NonNull String eventName, @NonNull ClickEvent clickEvent) {
        try {
            JSONObject properties = clickEvent.toProperties();
            mEmitter.emit(eventName, properties);
            MPLog.d(TAG, "Emitted " + eventName + " event");
        } catch (Exception e) {
            MPLog.e(TAG, "Error emitting event: " + eventName, e);
        }
    }

    /**
     * Attempts to get the Window from a root View.
     *
     * <p>Handles Activity windows, dialog windows, and other overlay windows by:
     * 1. Unwrapping ContextWrapper chain to find an Activity
     * 2. Using reflection to access the Window field on decor views (for dialogs/popups)
     */
    @Nullable
    private Window getWindowFromView(@NonNull View view) {
        try {
            // First, try reflection on the view to get its Window directly.
            // DecorView and similar root views often have a reference to their Window.
            Window reflectedWindow = getWindowViaReflection(view);
            if (reflectedWindow != null) {
                return reflectedWindow;
            }

            // Unwrap ContextWrapper chain to find an Activity
            Context context = view.getContext();
            while (context instanceof ContextWrapper) {
                if (context instanceof Activity) {
                    return ((Activity) context).getWindow();
                }
                context = ((ContextWrapper) context).getBaseContext();
            }
        } catch (Exception e) {
            MPLog.d(TAG, "Could not get window from view", e);
        }
        return null;
    }

    /**
     * Uses reflection to get the Window from a decor view.
     *
     * <p>Android's DecorView (and PopupWindow's PopupDecorView) hold a reference
     * to their Window via internal fields. This enables interceptor installation
     * on dialog, popup, and bottom sheet windows detected by WindowSpy.
     */
    @Nullable
    private static Window getWindowViaReflection(@NonNull View view) {
        try {
            // Try common field names for the Window reference on decor views
            Class<?> clazz = view.getClass();
            while (clazz != null && clazz != View.class) {
                try {
                    java.lang.reflect.Field windowField = clazz.getDeclaredField("mWindow");
                    windowField.setAccessible(true);
                    Object value = windowField.get(view);
                    if (value instanceof Window) {
                        return (Window) value;
                    }
                } catch (NoSuchFieldException ignored) {
                    // Try parent class
                }
                clazz = clazz.getSuperclass();
            }

            // Also try getWindow() method if available (some custom view types)
            try {
                java.lang.reflect.Method getWindow = view.getClass().getMethod("getWindow");
                Object value = getWindow.invoke(view);
                if (value instanceof Window) {
                    return (Window) value;
                }
            } catch (NoSuchMethodException ignored) {
                // Not available
            }
        } catch (Exception e) {
            MPLog.d(TAG, "Reflection failed for window lookup", e);
        }
        return null;
    }
}
