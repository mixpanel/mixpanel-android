package com.mixpanel.android.autocapture;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mixpanel.android.mpmetrics.DeadClickOptions;
import com.mixpanel.android.util.MPLog;

import java.lang.ref.WeakReference;

/**
 * Detects dead clicks by monitoring UI changes after a click.
 *
 * <p>A dead click is detected when a user clicks on an element that appears to be interactive
 * but produces no UI response within the configured timeout period.
 *
 * <p>Detection strategy:
 * <ol>
 *   <li>On click detected, wait for baseline delay (default 150ms) for UI to settle</li>
 *   <li>Capture baseline snapshot (view count, content hash)</li>
 *   <li>Attach listeners for UI changes (layout, scroll, window focus, new windows)</li>
 *   <li>After timeout (default 500ms total), compare final state to baseline</li>
 *   <li>If no change detected, emit dead click event</li>
 * </ol>
 *
 * <p>Thread safety: This class is NOT thread-safe and should only be called from the main thread.
 */
final class DeadClickDetector {

    private static final String TAG = "MP.DeadClickDetector";

    /**
     * Listener for dead click detection results.
     */
    interface DeadClickListener {
        /**
         * Called when a dead click is detected.
         *
         * @param clickEvent The original click event that had no UI response.
         */
        void onDeadClickDetected(@NonNull ClickEvent clickEvent);
    }

    private final long mTimeoutMs;
    private final long mBaselineDelayMs;
    private final Handler mHandler;
    private final DeadClickListener mListener;

    // Current detection state
    @Nullable
    private DetectionSession mCurrentSession;

    /**
     * Creates a DeadClickDetector with the given options.
     *
     * @param options  The dead click configuration options.
     * @param listener The listener to receive dead click events.
     */
    DeadClickDetector(@NonNull DeadClickOptions options, @NonNull DeadClickListener listener) {
        mTimeoutMs = options.getTimeoutMs();
        mBaselineDelayMs = options.getBaselineDelayMs();
        mHandler = new Handler(Looper.getMainLooper());
        mListener = listener;
    }

    /**
     * Starts detection for a click event.
     *
     * <p>Only monitors clicks on interactive elements (clickable, long-clickable,
     * or known interactive types).
     *
     * @param clickEvent The click event to monitor.
     * @param rootView   The root view to monitor for changes.
     */
    void startDetection(@NonNull ClickEvent clickEvent, @NonNull View rootView) {
        // Cancel any existing detection
        cancelDetection();

        // Only monitor interactive elements
        if (!clickEvent.isInteractive) {
            return;
        }

        try {
            mCurrentSession = new DetectionSession(clickEvent, rootView);
            mCurrentSession.start();
        } catch (Exception e) {
            MPLog.e(TAG, "Error starting dead click detection", e);
            mCurrentSession = null;
        }
    }

    /**
     * Cancels any ongoing detection.
     * Call this when the activity is paused or a navigation occurs.
     */
    void cancelDetection() {
        if (mCurrentSession != null) {
            mCurrentSession.cancel();
            mCurrentSession = null;
        }
    }

    /**
     * Notifies the detector that a new window was added.
     * This is a UI change signal that cancels dead click detection.
     */
    void onWindowAdded() {
        if (mCurrentSession != null) {
            mCurrentSession.onUiChange("window_added");
        }
    }

    /**
     * Notifies the detector that window focus changed.
     * This is a UI change signal that cancels dead click detection.
     */
    void onWindowFocusChanged() {
        if (mCurrentSession != null) {
            mCurrentSession.onUiChange("focus_changed");
        }
    }

    /**
     * Manages the detection lifecycle for a single click.
     */
    private class DetectionSession implements
            ViewTreeObserver.OnGlobalLayoutListener,
            ViewTreeObserver.OnScrollChangedListener {

        private final ClickEvent mClickEvent;
        private final WeakReference<View> mRootViewRef;

        // Compose-specific: reference to compose root and click position
        private final boolean mIsComposeClick;
        @Nullable
        private final WeakReference<View> mComposeRootRef;

        private boolean mCancelled = false;
        private boolean mBaselineCaptured = false;
        private int mBaselineViewCount;
        private int mBaselineContentHash;

        // Compose-specific baseline: semantic hash at click position
        @Nullable
        private Integer mComposeSemanticBaseline;

        private final Runnable mCaptureBaselineRunnable = this::captureBaseline;
        private final Runnable mCheckResultRunnable = this::checkResult;

        DetectionSession(@NonNull ClickEvent clickEvent, @NonNull View rootView) {
            mClickEvent = clickEvent;
            mRootViewRef = new WeakReference<>(rootView);

            // Check if this is a Compose click
            mIsComposeClick = clickEvent.isComposeClick();
            View composeRoot = clickEvent.getComposeRoot();
            mComposeRootRef = composeRoot != null ? new WeakReference<>(composeRoot) : null;
        }

        void start() {
            // Schedule baseline capture after delay
            mHandler.postDelayed(mCaptureBaselineRunnable, mBaselineDelayMs);

            // Schedule final check
            mHandler.postDelayed(mCheckResultRunnable, mTimeoutMs);

            // Attach listeners for XML views (ViewTreeObserver doesn't detect Compose changes)
            if (!mIsComposeClick) {
                View rootView = mRootViewRef.get();
                if (rootView != null) {
                    ViewTreeObserver observer = rootView.getViewTreeObserver();
                    if (observer.isAlive()) {
                        observer.addOnGlobalLayoutListener(this);
                        observer.addOnScrollChangedListener(this);
                    }
                }
            }
        }

        void cancel() {
            if (mCancelled) return;
            mCancelled = true;
            cleanup();
        }

        void onUiChange(String reason) {
            if (mCancelled) return;
            MPLog.d(TAG, "UI change detected: " + reason + ", cancelling dead click detection");
            cancel();
        }

        @Override
        public void onGlobalLayout() {
            if (!mBaselineCaptured) {
                // UI change before baseline - expected settling, ignore
                return;
            }
            onUiChange("layout");
        }

        @Override
        public void onScrollChanged() {
            if (!mBaselineCaptured) {
                return;
            }
            onUiChange("scroll");
        }

        private void captureBaseline() {
            if (mCancelled) return;

            View rootView = mRootViewRef.get();
            if (rootView == null) {
                cancel();
                return;
            }

            try {
                if (mIsComposeClick) {
                    // Compose: capture semantic snapshot at click position
                    View composeRoot = mComposeRootRef != null ? mComposeRootRef.get() : null;
                    if (composeRoot != null) {
                        mComposeSemanticBaseline = captureComposeSnapshot(
                                composeRoot, mClickEvent.x, mClickEvent.y);
                        mBaselineCaptured = mComposeSemanticBaseline != null;
                        if (!mBaselineCaptured) {
                            MPLog.d(TAG, "Failed to capture Compose baseline, cancelling");
                            cancel();
                            return;
                        }
                    } else {
                        cancel();
                        return;
                    }
                } else {
                    // XML: use view tree approach
                    mBaselineViewCount = countViews(rootView);
                    mBaselineContentHash = computeContentHash(rootView);
                    mBaselineCaptured = true;
                }
            } catch (Exception e) {
                MPLog.e(TAG, "Error capturing baseline", e);
                cancel();
            }
        }

        /**
         * Captures Compose semantic snapshot via ComposeSemanticHelper.
         * Isolated method to handle NoClassDefFoundError if Compose is not available.
         */
        @Nullable
        private Integer captureComposeSnapshot(@NonNull View composeRoot, float x, float y) {
            try {
                return ComposeSemanticHelper.captureSnapshot(composeRoot, x, y);
            } catch (NoClassDefFoundError e) {
                MPLog.d(TAG, "Compose not available for snapshot");
                return null;
            }
        }

        /**
         * Checks Compose semantic state change via ComposeSemanticHelper.
         */
        private boolean hasComposeStateChanged(@NonNull View composeRoot, float x, float y, int baseline) {
            try {
                return ComposeSemanticHelper.hasStateChanged(composeRoot, x, y, baseline);
            } catch (NoClassDefFoundError e) {
                MPLog.d(TAG, "Compose not available for state comparison");
                // Assume changed to avoid false positive
                return true;
            }
        }

        private void checkResult() {
            if (mCancelled) return;

            try {
                View rootView = mRootViewRef.get();
                if (rootView == null) {
                    cleanup();
                    return;
                }

                boolean noChange;

                if (mIsComposeClick) {
                    // Compose: compare semantic snapshots
                    View composeRoot = mComposeRootRef != null ? mComposeRootRef.get() : null;
                    if (composeRoot == null || mComposeSemanticBaseline == null) {
                        cleanup();
                        return;
                    }

                    boolean stateChanged = hasComposeStateChanged(
                            composeRoot, mClickEvent.x, mClickEvent.y, mComposeSemanticBaseline);
                    noChange = !stateChanged;

                    MPLog.d(TAG, "Compose dead click check - state changed: " + stateChanged);
                } else {
                    // XML: compare view counts and content hashes
                    int currentViewCount = countViews(rootView);
                    int currentContentHash = computeContentHash(rootView);

                    noChange = (currentViewCount == mBaselineViewCount) &&
                               (currentContentHash == mBaselineContentHash);
                }

                if (noChange) {
                    // Dead click detected!
                    mListener.onDeadClickDetected(mClickEvent);
                }
            } catch (Exception e) {
                MPLog.e(TAG, "Error checking dead click result", e);
            } finally {
                cleanup();
            }
        }

        private void cleanup() {
            mHandler.removeCallbacks(mCaptureBaselineRunnable);
            mHandler.removeCallbacks(mCheckResultRunnable);

            // Only remove view tree listeners for XML views
            if (!mIsComposeClick) {
                View rootView = mRootViewRef.get();
                if (rootView != null) {
                    ViewTreeObserver observer = rootView.getViewTreeObserver();
                    if (observer.isAlive()) {
                        observer.removeOnGlobalLayoutListener(this);
                        observer.removeOnScrollChangedListener(this);
                    }
                }
            }

            if (mCurrentSession == this) {
                mCurrentSession = null;
            }
        }

        /**
         * Counts the total number of views in the hierarchy.
         */
        private int countViews(@NonNull View view) {
            if (view.getVisibility() != View.VISIBLE) {
                return 0;
            }

            int count = 1;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    count += countViews(group.getChildAt(i));
                }
            }
            return count;
        }

        /**
         * Computes a hash of the visible content for change detection.
         *
         * <p>This is a lightweight approximation that considers:
         * - View visibility states
         * - View bounds
         * - Text content (if TextView)
         */
        private int computeContentHash(@NonNull View view) {
            if (view.getVisibility() != View.VISIBLE) {
                return 0;
            }

            int hash = 17;
            hash = 31 * hash + view.getLeft();
            hash = 31 * hash + view.getTop();
            hash = 31 * hash + view.getWidth();
            hash = 31 * hash + view.getHeight();

            if (view instanceof android.widget.TextView) {
                CharSequence text = ((android.widget.TextView) view).getText();
                if (text != null) {
                    hash = 31 * hash + text.hashCode();
                }
            }

            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    hash = 31 * hash + computeContentHash(group.getChildAt(i));
                }
            }

            return hash;
        }
    }
}
