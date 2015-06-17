package com.mixpanel.android.viewcrawler;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.mixpanel.android.mpmetrics.MPConfig;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;

@TargetApi(MPConfig.UI_FEATURES_MIN_API)
/* package */ abstract class ViewVisitor implements Pathfinder.Accumulator {

    /**
     * OnEvent will be fired when whatever the ViewVisitor installed fires
     * (For example, if the ViewVisitor installs watches for clicks, then OnEvent will be called
     * on click)
     */
    public interface OnEventListener {
        void OnEvent(View host, String eventName, boolean debounce);
    }

    public interface OnLayoutErrorListener {
        void onLayoutError(LayoutErrorMessage e);
    }

    public static class LayoutErrorMessage {
        public LayoutErrorMessage(String errorType, String name) {
            mErrorType = errorType;
            mName = name;
        }

        public String getErrorType() {
            return mErrorType;
        }

        public String getName() {
            return mName;
        }

        private final String mErrorType;
        private final String mName;
    }

    /**
     * Attempts to apply mutator to every matching view. Use this to update properties
     * in the view hierarchy. If accessor is non-null, it will be used to attempt to
     * prevent calls to the mutator if the property already has the intended value.
     */
    public static class PropertySetVisitor extends ViewVisitor {
        public PropertySetVisitor(List<Pathfinder.PathElement> path, Caller mutator, Caller accessor) {
            super(path);
            mMutator = mutator;
            mAccessor = accessor;
            mOriginalValueHolder = new Object[1];
            mOriginalValues = new WeakHashMap<View, Object>();
        }

        @Override
        public void cleanup() {
            for (Map.Entry<View, Object> original:mOriginalValues.entrySet()) {
                final View changedView = original.getKey();
                final Object originalValue = original.getValue();
                if (null != originalValue) {
                    mOriginalValueHolder[0] = originalValue;
                    mMutator.applyMethodWithArguments(changedView, mOriginalValueHolder);
                }
            }
        }

        @Override
        public void accumulate(View found) {
            if (null != mAccessor) {
                final Object[] setArgs = mMutator.getArgs();
                if (1 == setArgs.length) {
                    final Object desiredValue = setArgs[0];
                    final Object currentValue = mAccessor.applyMethod(found);

                    if (desiredValue == currentValue) {
                        return;
                    }

                    if (null != desiredValue) {
                        if (desiredValue instanceof Bitmap && currentValue instanceof Bitmap) {
                            final Bitmap desiredBitmap = (Bitmap) desiredValue;
                            final Bitmap currentBitmap = (Bitmap) currentValue;
                            if (desiredBitmap.sameAs(currentBitmap)) {
                                return;
                            }
                        } else if (desiredValue instanceof BitmapDrawable && currentValue instanceof BitmapDrawable) {
                            final Bitmap desiredBitmap = ((BitmapDrawable) desiredValue).getBitmap();
                            final Bitmap currentBitmap = ((BitmapDrawable) currentValue).getBitmap();
                            if (desiredBitmap != null && desiredBitmap.sameAs(currentBitmap)) {
                                return;
                            }
                        } else if (desiredValue.equals(currentValue)) {
                            return;
                        }
                    }

                    if (currentValue instanceof Bitmap ||
                            currentValue instanceof BitmapDrawable ||
                            mOriginalValues.containsKey(found)) {
                        ; // Cache exactly one non-image original value
                    } else {
                        mOriginalValueHolder[0] = currentValue;
                        if (mMutator.argsAreApplicable(mOriginalValueHolder)) {
                            mOriginalValues.put(found, currentValue);
                        } else {
                            mOriginalValues.put(found, null);
                        }
                    }
                }
            }

            mMutator.applyMethod(found);
        }

        protected String name() {
            return "Property Mutator";
        }

        private final Caller mMutator;
        private final Caller mAccessor;
        private final WeakHashMap<View, Object> mOriginalValues;
        private final Object[] mOriginalValueHolder;
    }

    private static class CycleDetector {

        /**
         * This function detects circular dependencies for all the views under the parent
         * of the updated view. The basic idea is to consider the views as a directed
         * graph and perform a DFS on all the nodes in the graph. If the current node is
         * in the DFS stack already, there must be a circle in the graph. To speed up the
         * search, all the parsed nodes will be removed from the graph.
         */
        public boolean hasCycle(TreeMap<View, List<View>> dependencyGraph) {
            final List<View> dfsStack = new ArrayList<View>();
            while (!dependencyGraph.isEmpty()) {
                View currentNode = dependencyGraph.firstKey();
                if (!detectSubgraphCycle(dependencyGraph, currentNode, dfsStack)) {
                    return false;
                }
            }

            return true;
        }

        private boolean detectSubgraphCycle(TreeMap<View, List<View>> dependencyGraph,
                                            View currentNode, List<View> dfsStack) {
            if (dfsStack.contains(currentNode)) {
                return false;
            }

            if (dependencyGraph.containsKey(currentNode)) {
                final List<View> dependencies = dependencyGraph.remove(currentNode);
                dfsStack.add(currentNode);

                int size = dependencies.size();
                for (int i = 0; i < size; i++) {
                    if (!detectSubgraphCycle(dependencyGraph, dependencies.get(i), dfsStack)) {
                        return false;
                    }
                }

                dfsStack.remove(currentNode);
            }

            return true;
        }
    }

    public static class LayoutUpdateVisitor extends ViewVisitor {
        public LayoutUpdateVisitor(List<Pathfinder.PathElement> path, List<LayoutRule> args,
                                   String name, OnLayoutErrorListener onLayoutErrorListener) {
            super(path);
            mOriginalValues = new WeakHashMap<View, int[]>();
            mArgs = args;
            mName = name;
            mAlive = true;
            mOnLayoutErrorListener = onLayoutErrorListener;
            mCycleDetector = new CycleDetector();
        }

        @Override
        public void cleanup() {
            // TODO find a way to optimize this.. remove this visitor and trigger a re-layout??
            for (Map.Entry<View, int[]> original:mOriginalValues.entrySet()) {
                final View changedView = original.getKey();
                final int[] originalValue = original.getValue();
                final RelativeLayout.LayoutParams originalParams = (RelativeLayout.LayoutParams) changedView.getLayoutParams();
                for (int i = 0; i < originalValue.length; i++) {
                    originalParams.addRule(i, originalValue[i]);
                }
                changedView.setLayoutParams(originalParams);
            }
            mAlive = false;
        }

        @Override
        public void visit(View rootView) {
            // this check is necessary - if the layout change is invalid, accumulate will send an error message
            // to the Web UI; before Web UI removes such change, this visit may get called by Android again and
            // thus send another error message to Web UI which leads to lots of weird problems
            if (mAlive) {
                getPathfinder().findTargetsInRoot(rootView, getPath(), this);
            }
        }

        // layout changes are performed on the children of found according to the LayoutRule
        @Override
        public void accumulate(View found) {
            ViewGroup parent = (ViewGroup) found;
            SparseArray<View> idToChild = new SparseArray<View>();

            int count = parent.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = parent.getChildAt(i);
                int childId = child.getId();
                if (childId > 0) {
                    idToChild.put(childId, child);
                }
            }

            int size = mArgs.size();
            for (int i = 0; i < size; i++) {
                LayoutRule layoutRule = mArgs.get(i);
                final View currentChild = idToChild.get(layoutRule.viewId);
                if (null == currentChild) {
                    continue;
                }

                RelativeLayout.LayoutParams currentParams = (RelativeLayout.LayoutParams) currentChild.getLayoutParams();
                final int[] currentRules = currentParams.getRules().clone();

                if (currentRules[layoutRule.verb] == layoutRule.anchor) {
                    continue;
                }

                if (mOriginalValues.containsKey(currentChild)) {
                    ; // Cache exactly one set of rules per child view
                } else {
                    mOriginalValues.put(currentChild, currentRules);
                }

                currentParams.addRule(layoutRule.verb, layoutRule.anchor);

                final Set<Integer> rules;
                if (mHorizontalRules.contains(layoutRule.verb)) {
                    rules = mHorizontalRules;
                } else if (mVerticalRules.contains(layoutRule.verb)) {
                    rules = mVerticalRules;
                } else {
                    rules = null;
                }

                if (rules != null && !verifyLayout(rules, idToChild)) {
                    cleanup();
                    mOnLayoutErrorListener.onLayoutError(new LayoutErrorMessage("circular_dependency", mName));
                    return;
                }

                currentChild.setLayoutParams(currentParams);
            }
        }

        private boolean verifyLayout(Set<Integer> rules, SparseArray<View> idToChild) {
            // We don't really care about the order, as long as it's always the same.
            final TreeMap<View, List<View>> dependencyGraph = new TreeMap<View, List<View>>(new Comparator<View>() {
                @Override
                public int compare(final View lhs, final View rhs) {
                    if (lhs == rhs) {
                        return 0;
                    } else if (null == lhs) {
                        return -1;
                    } else if (null == rhs){
                        return 1;
                    } else {
                        return rhs.hashCode() - lhs.hashCode();
                    }
                }
            });
            int size = idToChild.size();
            for (int i = 0; i < size; i++) {
                final View child = idToChild.valueAt(i);
                final RelativeLayout.LayoutParams childLayoutParams = (RelativeLayout.LayoutParams) child.getLayoutParams();
                int[] layoutRules = childLayoutParams.getRules();

                final List<View> dependencies = new ArrayList<View>();
                for (int rule : rules) {
                    int dependencyId = layoutRules[rule];
                    if (dependencyId > 0 && dependencyId != child.getId()) {
                        dependencies.add(idToChild.get(dependencyId));
                    }
                }

                dependencyGraph.put(child, dependencies);
            }

            return mCycleDetector.hasCycle(dependencyGraph);
        }

        protected String name() { return "Layout Update"; }

        private final WeakHashMap<View, int[]> mOriginalValues;
        private final List<LayoutRule> mArgs;
        private final String mName;
        private static final Set<Integer> mHorizontalRules = new HashSet<Integer>(Arrays.asList(
                RelativeLayout.LEFT_OF, RelativeLayout.RIGHT_OF,
                RelativeLayout.ALIGN_LEFT, RelativeLayout.ALIGN_RIGHT
        ));
        private static final Set<Integer> mVerticalRules = new HashSet<Integer>(Arrays.asList(
                RelativeLayout.ABOVE, RelativeLayout.BELOW,
                RelativeLayout.ALIGN_BASELINE, RelativeLayout.ALIGN_TOP,
                RelativeLayout.ALIGN_BOTTOM
        ));
        private boolean mAlive;
        private final OnLayoutErrorListener mOnLayoutErrorListener;
        private final CycleDetector mCycleDetector;
    }

    public static class LayoutRule {
        public LayoutRule(int vi, int v, int a) {
            viewId = vi;
            verb = v;
            anchor = a;
        }

        public final int viewId;
        public final int verb;
        public final int anchor;
    }

    /**
     * Adds an accessibility event, which will fire OnEvent, to every matching view.
     */
    public static class AddAccessibilityEventVisitor extends EventTriggeringVisitor {
        public AddAccessibilityEventVisitor(List<Pathfinder.PathElement> path, int accessibilityEventType, String eventName, OnEventListener listener) {
            super(path, eventName, listener, false);
            mEventType = accessibilityEventType;
            mWatching = new WeakHashMap<View, TrackingAccessibilityDelegate>();
        }

        @Override
        public void cleanup() {
            for (final Map.Entry<View, TrackingAccessibilityDelegate> entry:mWatching.entrySet()) {
                final View v = entry.getKey();
                final TrackingAccessibilityDelegate toCleanup = entry.getValue();
                final View.AccessibilityDelegate currentViewDelegate = getOldDelegate(v);
                if (currentViewDelegate == toCleanup) {
                    v.setAccessibilityDelegate(toCleanup.getRealDelegate());
                } else if (currentViewDelegate instanceof TrackingAccessibilityDelegate) {
                    final TrackingAccessibilityDelegate newChain = (TrackingAccessibilityDelegate) currentViewDelegate;
                    newChain.removeFromDelegateChain(toCleanup);
                } else {
                    // Assume we've been replaced, zeroed out, or for some other reason we're already gone.
                    // (This isn't too weird, for example, it's expected when views get recycled)
                }
            }
            mWatching.clear();
        }

        @Override
        public void accumulate(View found) {
            final View.AccessibilityDelegate realDelegate = getOldDelegate(found);
            if (realDelegate instanceof TrackingAccessibilityDelegate) {
                final TrackingAccessibilityDelegate currentTracker = (TrackingAccessibilityDelegate) realDelegate;
                if (currentTracker.willFireEvent(getEventName())) {
                    return; // Don't double track
                }
            }

            // We aren't already in the tracking call chain of the view
            final TrackingAccessibilityDelegate newDelegate = new TrackingAccessibilityDelegate(realDelegate);
            found.setAccessibilityDelegate(newDelegate);
            mWatching.put(found, newDelegate);
        }

        @Override
        protected String name() {
            return getEventName() + " event when (" + mEventType + ")";
        }

        private View.AccessibilityDelegate getOldDelegate(View v) {
            View.AccessibilityDelegate ret = null;
            try {
                Class<?> klass = v.getClass();
                Method m = klass.getMethod("getAccessibilityDelegate");
                ret = (View.AccessibilityDelegate) m.invoke(v);
            } catch (NoSuchMethodException e) {
                // In this case, we just overwrite the original.
            } catch (IllegalAccessException e) {
                // In this case, we just overwrite the original.
            } catch (InvocationTargetException e) {
                Log.w(LOGTAG, "getAccessibilityDelegate threw an exception when called.", e);
            }

            return ret;
        }

        private class TrackingAccessibilityDelegate extends View.AccessibilityDelegate {
            public TrackingAccessibilityDelegate(View.AccessibilityDelegate realDelegate) {
                mRealDelegate = realDelegate;
            }

            public View.AccessibilityDelegate getRealDelegate() {
                return mRealDelegate;
            }

            public boolean willFireEvent(final String eventName) {
                if (getEventName() == eventName) {
                    return true;
                } else if (mRealDelegate instanceof TrackingAccessibilityDelegate) {
                    return ((TrackingAccessibilityDelegate) mRealDelegate).willFireEvent(eventName);
                } else {
                    return false;
                }
            }

            public void removeFromDelegateChain(final TrackingAccessibilityDelegate other) {
                if (mRealDelegate == other) {
                    mRealDelegate = other.getRealDelegate();
                } else if (mRealDelegate instanceof TrackingAccessibilityDelegate) {
                    final TrackingAccessibilityDelegate child = (TrackingAccessibilityDelegate) mRealDelegate;
                    child.removeFromDelegateChain(other);
                } else {
                    // We can't see any further down the chain, just return.
                }
            }

            @Override
            public void sendAccessibilityEvent(View host, int eventType) {
                if (eventType == mEventType) {
                    fireEvent(host);
                }

                if (null != mRealDelegate) {
                    mRealDelegate.sendAccessibilityEvent(host, eventType);
                }
            }

            private View.AccessibilityDelegate mRealDelegate;
        }

        private final int mEventType;
        private final WeakHashMap<View, TrackingAccessibilityDelegate> mWatching;
    }

    /**
     * Installs a TextWatcher in each matching view. Does nothing if matching views are not TextViews.
     */
    public static class AddTextChangeListener extends EventTriggeringVisitor {
        public AddTextChangeListener(List<Pathfinder.PathElement> path, String eventName, OnEventListener listener) {
            super(path, eventName, listener, true);
            mWatching = new HashMap<TextView, TextWatcher>();
        }

        @Override
        public void cleanup() {
            for (final Map.Entry<TextView, TextWatcher> entry:mWatching.entrySet()) {
                final TextView v = entry.getKey();
                final TextWatcher watcher = entry.getValue();
                v.removeTextChangedListener(watcher);
            }

            mWatching.clear();
        }

        @Override
        public void accumulate(View found) {
            if (found instanceof TextView) {
                final TextView foundTextView = (TextView) found;
                final TextWatcher watcher = new TrackingTextWatcher(foundTextView);
                final TextWatcher oldWatcher = mWatching.get(foundTextView);
                if (null != oldWatcher) {
                    foundTextView.removeTextChangedListener(oldWatcher);
                }
                foundTextView.addTextChangedListener(watcher);
                mWatching.put(foundTextView, watcher);
            }
        }

        @Override
        protected String name() {
            return getEventName() + " on Text Change";
        }

        private class TrackingTextWatcher implements TextWatcher {
            public TrackingTextWatcher(View boundTo) {
                mBoundTo = boundTo;
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                ; // Nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                ; // Nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                fireEvent(mBoundTo);
            }

            private final View mBoundTo;
        }

        private final Map<TextView, TextWatcher> mWatching;
    }

    /**
     * Monitors the view tree for the appearance of matching views where there were not
     * matching views before. Fires only once per traversal.
     */
    public static class ViewDetectorVisitor extends EventTriggeringVisitor {
        public ViewDetectorVisitor(List<Pathfinder.PathElement> path, String eventName, OnEventListener listener) {
            super(path, eventName, listener, false);
            mSeen = false;
        }

        @Override
        public void cleanup() {
            ; // Do nothing, we don't have anything to leak :)
        }

        @Override
        public void accumulate(View found) {
            if (found != null && !mSeen) {
                fireEvent(found);
            }

            mSeen = (found != null);
        }

        @Override
        protected String name() {
            return getEventName() + " when Detected";
        }

        private boolean mSeen;
    }

    private static abstract class EventTriggeringVisitor extends ViewVisitor {
        public EventTriggeringVisitor(List<Pathfinder.PathElement> path, String eventName, OnEventListener listener, boolean debounce) {
            super(path);
            mListener = listener;
            mEventName = eventName;
            mDebounce = debounce;
        }

        protected void fireEvent(View found) {
            mListener.OnEvent(found, mEventName, mDebounce);
        }

        protected String getEventName() {
            return mEventName;
        }

        private final OnEventListener mListener;
        private final String mEventName;
        private final boolean mDebounce;
    }

    /**
     * Scans the View hierarchy below rootView, applying it's operation to each matching child view.
     */
    public void visit(View rootView) {
        mPathfinder.findTargetsInRoot(rootView, mPath, this);
    }

    /**
     * Removes listeners and frees resources associated with the visitor. Once cleanup is called,
     * the ViewVisitor should not be used again.
     */
    public abstract void cleanup();

    protected ViewVisitor(List<Pathfinder.PathElement> path) {
        mPath = path;
        mPathfinder = new Pathfinder();
    }

    protected List<Pathfinder.PathElement> getPath() {
        return mPath;
    }

    protected Pathfinder getPathfinder() {
        return mPathfinder;
    }

    protected abstract String name();

    private final List<Pathfinder.PathElement> mPath;
    private final Pathfinder mPathfinder;

    private static final String LOGTAG = "MixpanelAPI.ViewVisitor";
}
