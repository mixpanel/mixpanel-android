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
 *   <li>On click detected, capture baseline snapshot immediately (before the click
 *       handler runs), so any UI response is visible as a delta</li>
 *   <li>Attach listeners for UI changes (layout, scroll, window focus, new windows)
 *       — any change cancels detection early</li>
 *   <li>After timeout (default 500ms), compare final state to baseline</li>
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

    /**
     * Strategy interface for monitoring UI changes. Implementations capture a baseline
     * snapshot and later compare against it to determine if the UI changed.
     */
    interface UiChangeMonitor {
        /**
         * Captures the baseline UI state. Called synchronously before the click handler runs.
         *
         * @return true if the baseline was captured successfully.
         */
        boolean captureBaseline();

        /**
         * Compares current UI state against the baseline.
         *
         * @return true if the UI has changed since the baseline was captured.
         */
        boolean hasChanged();

        /**
         * Attaches listeners for early cancellation (e.g., layout/scroll changes).
         *
         * @param rootView The root view to attach listeners to.
         * @param session  The session to notify on change.
         */
        void attachListeners(@NonNull View rootView, @NonNull DetectionSession session);

        /**
         * Removes any listeners attached by {@link #attachListeners}.
         *
         * @param rootView The root view listeners were attached to.
         * @param session  The session that was being notified.
         */
        void detachListeners(@NonNull View rootView, @NonNull DetectionSession session);
    }

    private final long mTimeoutMs;
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
            // Choose the appropriate monitor based on click type
            UiChangeMonitor monitor;
            if (clickEvent.isComposeClick()) {
                View composeRoot = clickEvent.getComposeRoot();
                if (composeRoot == null) return;
                monitor = new ComposeUiChangeMonitor(composeRoot, rootView);
            } else {
                monitor = new XmlUiChangeMonitor(rootView);
            }

            mCurrentSession = new DetectionSession(clickEvent, rootView, monitor);
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

    // ==================== Shared Helpers ====================

    /**
     * Computes a hash of the visible XML view hierarchy content for change detection.
     * Includes view position, size, and text content.
     */
    private static int computeContentHash(@NonNull View view, int depth) {
        if (depth >= AutocaptureDefaults.MAX_RECURSION_DEPTH) return 0;
        if (view.getVisibility() != View.VISIBLE) return 0;

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
                hash = 31 * hash + computeContentHash(group.getChildAt(i), depth + 1);
            }
        }
        return hash;
    }

    // ==================== UiChangeMonitor Implementations ====================

    /**
     * Monitors XML view hierarchy for changes via view count and content hash comparison.
     */
    private static class XmlUiChangeMonitor implements UiChangeMonitor {
        private final WeakReference<View> mRootViewRef;
        private int mBaselineViewCount;
        private int mBaselineContentHash;

        XmlUiChangeMonitor(@NonNull View rootView) {
            mRootViewRef = new WeakReference<>(rootView);
        }

        @Override
        public boolean captureBaseline() {
            try {
                View rootView = mRootViewRef.get();
                if (rootView == null) return false;
                mBaselineViewCount = countViews(rootView, 0);
                mBaselineContentHash = DeadClickDetector.computeContentHash(rootView, 0);
                return true;
            } catch (Exception e) {
                MPLog.e(TAG, "Error capturing XML baseline", e);
                return false;
            }
        }

        @Override
        public boolean hasChanged() {
            View rootView = mRootViewRef.get();
            if (rootView == null) return true; // View gone = change
            int currentViewCount = countViews(rootView, 0);
            int currentContentHash = DeadClickDetector.computeContentHash(rootView, 0);
            return currentViewCount != mBaselineViewCount ||
                   currentContentHash != mBaselineContentHash;
        }

        @Override
        public void attachListeners(@NonNull View rootView, @NonNull DetectionSession session) {
            ViewTreeObserver observer = rootView.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.addOnGlobalLayoutListener(session);
                observer.addOnScrollChangedListener(session);
            }
        }

        @Override
        public void detachListeners(@NonNull View rootView, @NonNull DetectionSession session) {
            ViewTreeObserver observer = rootView.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnGlobalLayoutListener(session);
                observer.removeOnScrollChangedListener(session);
            }
        }

        private static int countViews(@NonNull View view, int depth) {
            if (depth >= AutocaptureDefaults.MAX_RECURSION_DEPTH) return 0;
            if (view.getVisibility() != View.VISIBLE) return 0;

            int count = 1;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    count += countViews(group.getChildAt(i), depth + 1);
                }
            }
            return count;
        }

    }

    /**
     * Monitors both Compose semantic tree and XML view hierarchy for changes.
     *
     * <p>In mixed Compose/XML layouts, a Compose button click may modify only XML views.
     * This monitor catches such cross-framework changes by:
     * <ul>
     *   <li>Attaching ViewTreeObserver listeners on the root view for early cancellation
     *       when XML layout or scroll changes occur</li>
     *   <li>Checking both the Compose semantic snapshot and XML content hash at timeout,
     *       so changes in either framework are detected</li>
     * </ul>
     */
    private static class ComposeUiChangeMonitor implements UiChangeMonitor {
        private final WeakReference<View> mComposeRootRef;
        private final WeakReference<View> mRootViewRef;
        @Nullable
        private ComposeSemanticHelper.SemanticSnapshot mComposeBaseline;
        private int mXmlBaselineContentHash;

        ComposeUiChangeMonitor(@NonNull View composeRoot, @NonNull View rootView) {
            mComposeRootRef = new WeakReference<>(composeRoot);
            mRootViewRef = new WeakReference<>(rootView);
        }

        @Override
        public boolean captureBaseline() {
            View composeRoot = mComposeRootRef.get();
            if (composeRoot == null) return false;
            mComposeBaseline = ComposeSemanticHelper.captureSnapshot(composeRoot);
            if (mComposeBaseline == null) return false;

            View rootView = mRootViewRef.get();
            if (rootView != null) {
                mXmlBaselineContentHash = DeadClickDetector.computeContentHash(rootView, 0);
            }
            return true;
        }

        @Override
        public boolean hasChanged() {
            // Check Compose semantic tree
            View composeRoot = mComposeRootRef.get();
            if (composeRoot == null) return true; // View gone = change
            ComposeSemanticHelper.SemanticSnapshot current =
                    ComposeSemanticHelper.captureSnapshot(composeRoot);
            boolean composeChanged = mComposeBaseline.hasChanged(current);

            // Check XML view hierarchy for cross-framework changes
            boolean xmlChanged = false;
            View rootView = mRootViewRef.get();
            if (rootView != null) {
                int currentHash = DeadClickDetector.computeContentHash(rootView, 0);
                xmlChanged = currentHash != mXmlBaselineContentHash;
            }

            boolean changed = composeChanged || xmlChanged;
            MPLog.d(TAG, "Compose dead click check - composeChanged: " + composeChanged
                    + ", xmlChanged: " + xmlChanged);
            return changed;
        }

        @Override
        public void attachListeners(@NonNull View rootView, @NonNull DetectionSession session) {
            // Attach ViewTreeObserver listeners on the root view to catch XML changes
            // (layout, scroll) for early cancellation in mixed Compose/XML layouts.
            ViewTreeObserver observer = rootView.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.addOnGlobalLayoutListener(session);
                observer.addOnScrollChangedListener(session);
            }
        }

        @Override
        public void detachListeners(@NonNull View rootView, @NonNull DetectionSession session) {
            ViewTreeObserver observer = rootView.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnGlobalLayoutListener(session);
                observer.removeOnScrollChangedListener(session);
            }
        }
    }

    // ==================== Detection Session ====================

    /**
     * Manages the detection lifecycle for a single click.
     * Delegates UI monitoring to a {@link UiChangeMonitor}.
     */
    class DetectionSession implements
            ViewTreeObserver.OnGlobalLayoutListener,
            ViewTreeObserver.OnScrollChangedListener {

        private final ClickEvent mClickEvent;
        private final WeakReference<View> mRootViewRef;
        private final UiChangeMonitor mMonitor;
        private boolean mCancelled = false;
        private final Runnable mCheckResultRunnable = this::checkResult;

        DetectionSession(@NonNull ClickEvent clickEvent, @NonNull View rootView,
                         @NonNull UiChangeMonitor monitor) {
            mClickEvent = clickEvent;
            mRootViewRef = new WeakReference<>(rootView);
            mMonitor = monitor;
        }

        void start() {
            // Schedule final check
            mHandler.postDelayed(mCheckResultRunnable, mTimeoutMs);

            View rootView = mRootViewRef.get();
            if (rootView == null) {
                cancel();
                return;
            }

            // Capture baseline immediately (before click handler runs)
            if (!mMonitor.captureBaseline()) {
                cancel();
                return;
            }

            // Attach listeners for early cancellation
            mMonitor.attachListeners(rootView, this);
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
            onUiChange("layout");
        }

        @Override
        public void onScrollChanged() {
            onUiChange("scroll");
        }

        private void checkResult() {
            if (mCancelled) return;

            try {
                if (!mMonitor.hasChanged()) {
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
            mHandler.removeCallbacks(mCheckResultRunnable);

            View rootView = mRootViewRef.get();
            if (rootView != null) {
                mMonitor.detachListeners(rootView, this);
            }

            if (mCurrentSession == this) {
                mCurrentSession = null;
            }
        }
    }
}
