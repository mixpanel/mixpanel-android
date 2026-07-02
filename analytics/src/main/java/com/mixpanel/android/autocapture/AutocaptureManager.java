package com.mixpanel.android.autocapture;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mixpanel.android.mpmetrics.AutocaptureOptions;
import com.mixpanel.android.util.MPLog;

import org.json.JSONObject;

import curtains.Curtains;
import curtains.OnTouchEventListener;

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

    // Track Curtains touch listeners per window to avoid duplicates and enable cleanup
    private final Map<Window, OnTouchEventListener> mWindowListeners = new WeakHashMap<>();

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

            // Install WindowSpy (Curtains-backed) for dialog/popup tracking
            WindowSpy.install();
            WindowSpy.addListener(this);

            // Retroactively attach to already-visible root views.
            // registerActivityLifecycleCallbacks does not replay onActivityResumed
            // for already-resumed activities, and WindowSpy's initial copy of existing
            // views doesn't trigger add notifications. This ensures deferred SDK init
            // captures the current screen.
            for (View rootView : Curtains.getRootViews()) {
                onRootViewChanged(rootView, true);
            }

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

            // Remove all Curtains touch listeners
            for (Map.Entry<Window, OnTouchEventListener> entry :
                    new java.util.ArrayList<>(mWindowListeners.entrySet())) {
                CurtainsHelper.removeTouchListener(entry.getKey(), entry.getValue());
            }
            mWindowListeners.clear();

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

                // Try to attach touch listener via Curtains
                Window window = CurtainsHelper.getWindow(view);
                if (window != null && !mWindowListeners.containsKey(window)) {
                    attachTouchListener(window);
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

            // Attach touch listener to activity window
            Window window = activity.getWindow();
            if (window != null && !mWindowListeners.containsKey(window)) {
                attachTouchListener(window);
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
            // Clean up touch listener for this activity's window
            Window window = activity.getWindow();
            if (window != null) {
                OnTouchEventListener listener = mWindowListeners.remove(window);
                if (listener != null) {
                    CurtainsHelper.removeTouchListener(window, listener);
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

    /**
     * Attaches a Curtains-based touch listener to a window.
     * The listener filters for taps (touch slop + duration) and forwards to onTouchUp.
     */
    private void attachTouchListener(@NonNull Window window) {
        OnTouchEventListener listener = CurtainsHelper.installTouchListener(window, this);
        mWindowListeners.put(window, listener);
        MPLog.d(TAG, "Attached touch listener to window: " + window);
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
}
