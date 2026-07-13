package com.mixpanel.android.autocapture;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

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
            UiChangeMonitor monitor = new UnifiedUiChangeMonitor(rootView, clickEvent);
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

    // ==================== UiChangeMonitor Implementation ====================

    /**
     * Unified monitor that detects UI changes across both XML and Compose frameworks.
     *
     * <p>Automatically detects the UI framework composition at baseline time and adapts
     * its detection strategy:
     *
     * <ul>
     *   <li><b>Pure XML:</b> Full structural hash (position, size, class, text, state)
     *       with ViewTreeObserver early cancellation. Maximum sensitivity.</li>
     *   <li><b>Pure Compose:</b> Compose semantic snapshot only (text, contentDescription).
     *       No ViewTreeObserver (Compose ripple causes false positives).</li>
     *   <li><b>Mixed framework:</b> Content-only XOR hash for XML views (immune to
     *       Compose ripple structural changes) + Compose semantic snapshot. Together
     *       these cover the complete UI surface. No ViewTreeObserver.</li>
     * </ul>
     *
     * <p>For mixed-framework screens, the XOR hash detects text and toggle state changes
     * in XML views while ignoring structural changes caused by Compose animations.
     * The semantic snapshot covers all Compose text and contentDescription changes.
     */
    private static class UnifiedUiChangeMonitor implements UiChangeMonitor {

        private final WeakReference<View> mRootViewRef;

        // Framework detection flags, determined once at baseline time
        private boolean mHasCompose;

        // XML baseline — always captured
        // Pure XML: structural hash (position, size, class, text, state)
        // Mixed: content-only XOR hash (text and toggle state only)
        private int mBaselineXmlViewCount;
        private int mBaselineXmlHash;

        // Compose baseline — captured only when Compose is present
        @Nullable
        private WeakReference<View> mComposeRootRef;
        @Nullable
        private ComposeSemanticHelper.SemanticSnapshot mComposeBaseline;

        UnifiedUiChangeMonitor(@NonNull View rootView, @NonNull ClickEvent clickEvent) {
            mRootViewRef = new WeakReference<>(rootView);

            // Resolve Compose root:
            // - For Compose clicks, the ClickEvent already has the reference
            // - For XML clicks, search the view tree for a Compose root
            View composeRoot = clickEvent.getComposeRoot();
            if (composeRoot == null) {
                composeRoot = findComposeRoot(rootView);
            }
            mHasCompose = composeRoot != null;
            mComposeRootRef = composeRoot != null ? new WeakReference<>(composeRoot) : null;
        }

        @Override
        public boolean captureBaseline() {
            try {
                View rootView = mRootViewRef.get();
                if (rootView == null) return false;

                // Capture XML baseline
                if (mHasCompose) {
                    mBaselineXmlHash = captureXmlContentHash(rootView);
                } else {
                    int[] snapshot = captureStructuralSnapshot(rootView);
                    mBaselineXmlViewCount = snapshot[IDX_COUNT];
                    mBaselineXmlHash = snapshot[IDX_HASH];
                }

                // Capture Compose baseline if present
                if (mHasCompose) {
                    View composeRoot = mComposeRootRef != null ? mComposeRootRef.get() : null;
                    if (composeRoot == null) return false;
                    mComposeBaseline = ComposeSemanticHelper.captureSnapshot(composeRoot);
                    if (mComposeBaseline == null) return false;
                }

                return true;
            } catch (NoClassDefFoundError e) {
                // Compose classes not available at runtime — fall back to XML-only
                mHasCompose = false;
                mComposeRootRef = null;
                mComposeBaseline = null;
                return captureBaseline();
            } catch (Exception e) {
                MPLog.e(TAG, "Error capturing baseline", e);
                return false;
            }
        }

        @Override
        public boolean hasChanged() {
            View rootView = mRootViewRef.get();
            if (rootView == null) return true; // View gone = change

            // Check XML changes
            if (hasXmlChanged(rootView)) return true;

            // Check Compose changes
            if (mHasCompose && hasComposeChanged()) return true;

            return false;
        }

        @Override
        public void attachListeners(@NonNull View rootView, @NonNull DetectionSession session) {
            // ViewTreeObserver early cancellation is only safe for pure XML screens.
            // When Compose is present, ripple animations trigger layout changes that
            // would cause false cancellation of dead click detection.
            if (!mHasCompose) {
                ViewTreeObserver observer = rootView.getViewTreeObserver();
                if (observer.isAlive()) {
                    observer.addOnGlobalLayoutListener(session);
                    observer.addOnScrollChangedListener(session);
                }
            }
        }

        @Override
        public void detachListeners(@NonNull View rootView, @NonNull DetectionSession session) {
            if (!mHasCompose) {
                ViewTreeObserver observer = rootView.getViewTreeObserver();
                if (observer.isAlive()) {
                    observer.removeOnGlobalLayoutListener(session);
                    observer.removeOnScrollChangedListener(session);
                }
            }
        }

        // -------------------- XML Change Detection --------------------

        /**
         * Checks whether XML views have changed since baseline.
         * Uses structural hash for pure XML, content-only hash for mixed screens.
         */
        private boolean hasXmlChanged(@NonNull View rootView) {
            if (mHasCompose) {
                // Mixed screen: content-only comparison (immune to Compose ripple)
                int currentHash = captureXmlContentHash(rootView);
                return currentHash != mBaselineXmlHash;
            } else {
                // Pure XML: full structural comparison
                int[] snapshot = captureStructuralSnapshot(rootView);
                return snapshot[IDX_COUNT] != mBaselineXmlViewCount ||
                        snapshot[IDX_HASH] != mBaselineXmlHash;
            }
        }

        /**
         * Captures view count and structural hash in a single tree walk.
         * Includes position, size, class name, text, and control state.
         * Used for pure XML screens where maximum detection sensitivity is desired.
         *
         * @return int[]{viewCount, contentHash}
         */
        private static int[] captureStructuralSnapshot(@NonNull View view) {
            return captureStructuralSnapshotRecursive(view, 0);
        }

        private static int[] captureStructuralSnapshotRecursive(@NonNull View view, int depth) {
            if (depth >= AutocaptureDefaults.MAX_RECURSION_DEPTH) return new int[]{0, 0};
            if (view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) return new int[]{0, 0};

            int count = 1;
            int hash = 17;

            // Position and size
            hash = 31 * hash + view.getLeft();
            hash = 31 * hash + view.getTop();
            hash = 31 * hash + view.getWidth();
            hash = 31 * hash + view.getHeight();

            // Class name
            hash = 31 * hash + view.getClass().getSimpleName().hashCode();

            // Text content (for TextViews, which includes Button)
            if (view instanceof TextView) {
                CharSequence text = ((TextView) view).getText();
                if (text != null) {
                    hash = 31 * hash + text.hashCode();
                }
            }

            // Control state
            if (view instanceof android.widget.CompoundButton) {
                hash = 31 * hash + (((android.widget.CompoundButton) view).isChecked() ? 1 : 0);
            }
            if (view.isEnabled()) {
                hash = 31 * hash + 1;
            }

            // Recurse into children
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    int[] child = captureStructuralSnapshotRecursive(group.getChildAt(i), depth + 1);
                    count += child[IDX_COUNT];
                    hash = 31 * hash + child[IDX_HASH];
                }
            }

            return new int[]{count, hash};
        }

        private static final int IDX_COUNT = 0;
        private static final int IDX_HASH = 1;

        /**
         * Captures a content-only hash of XML views using XOR accumulation.
         * Only hashes text content (TextView) and toggle state (CompoundButton).
         *
         * <p>XOR accumulation makes the hash immune to tree structure changes
         * (node additions/removals from Compose ripple animations) while still
         * detecting meaningful content changes in XML views.
         *
         * <p>Used for mixed-framework screens where structural changes from Compose
         * animations would cause false positives with the full structural hash.
         */
        private static int captureXmlContentHash(@NonNull View view) {
            return captureXmlContentHashRecursive(view, 0);
        }

        private static int captureXmlContentHashRecursive(@NonNull View view, int depth) {
            if (depth >= AutocaptureDefaults.MAX_RECURSION_DEPTH) return 0;
            if (view.getVisibility() != View.VISIBLE) return 0;

            int hash = 0;

            // Text content
            if (view instanceof TextView) {
                CharSequence text = ((TextView) view).getText();
                if (text != null) {
                    hash ^= text.hashCode();
                }
            }

            // Toggle state (use distinct constants to differentiate checked/unchecked)
            if (view instanceof android.widget.CompoundButton) {
                hash ^= ((android.widget.CompoundButton) view).isChecked() ? 0x55555555 : 0xAAAAAAAA;
            }

            // Recurse into children
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    hash ^= captureXmlContentHashRecursive(group.getChildAt(i), depth + 1);
                }
            }

            return hash;
        }

        // -------------------- Compose Change Detection --------------------

        /**
         * Checks whether the Compose semantic tree has changed since baseline.
         */
        private boolean hasComposeChanged() {
            View composeRoot = mComposeRootRef != null ? mComposeRootRef.get() : null;
            if (composeRoot == null) return true; // View gone = change

            try {
                ComposeSemanticHelper.SemanticSnapshot current =
                        ComposeSemanticHelper.captureSnapshot(composeRoot);
                boolean changed = mComposeBaseline != null && mComposeBaseline.hasChanged(current);
                MPLog.d(TAG, "Compose dead click check - changed: " + changed);
                return changed;
            } catch (NoClassDefFoundError e) {
                // Compose classes no longer available — treat as changed to be safe
                return true;
            }
        }

        // -------------------- Compose Root Discovery --------------------

        /**
         * Searches the view tree for a Compose root (a view implementing RootForTest).
         * Used for XML clicks in mixed-framework screens to locate the Compose root
         * for semantic baseline capture.
         *
         * @return The Compose root view, or null if no Compose is present.
         */
        @Nullable
        private static View findComposeRoot(@NonNull View view) {
            try {
                return findComposeRootRecursive(view, 0);
            } catch (NoClassDefFoundError e) {
                // Compose not available at runtime
                return null;
            }
        }

        @Nullable
        private static View findComposeRootRecursive(@NonNull View view, int depth) {
            if (depth >= AutocaptureDefaults.MAX_RECURSION_DEPTH) return null;

            if (ComposeSemanticHelper.isComposeRoot(view)) {
                return view;
            }

            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View result = findComposeRootRecursive(group.getChildAt(i), depth + 1);
                    if (result != null) return result;
                }
            }

            return null;
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
