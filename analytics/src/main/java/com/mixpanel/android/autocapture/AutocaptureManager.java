package com.mixpanel.android.autocapture;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mixpanel.android.mpmetrics.AutocaptureOptions;
import com.mixpanel.android.util.MPLog;

import org.json.JSONObject;

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
     * Attempts to get the Window from a View.
     *
     * <p>Works for decor views that have a PhoneWindow attached.
     */
    @Nullable
    private Window getWindowFromView(@NonNull View view) {
        try {
            // Try to get window via reflection (decor view has mWindow field on some versions)
            // For most cases, we rely on WindowSpy notifying us when the Activity is resumed
            // and we attach via activity.getWindow() directly

            // Check if this is a decor view from an Activity
            Context context = view.getContext();
            if (context instanceof Activity) {
                return ((Activity) context).getWindow();
            }

            // For dialogs and other windows, we get notified via ActivityLifecycleCallbacks
            // or WindowSpy when the root view is added
        } catch (Exception e) {
            MPLog.d(TAG, "Could not get window from view", e);
        }
        return null;
    }
}
