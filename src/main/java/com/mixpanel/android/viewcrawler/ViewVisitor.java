package com.mixpanel.android.viewcrawler;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.mixpanel.android.mpmetrics.MPConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@TargetApi(MPConfig.UI_FEATURES_MIN_API)
/* package */ abstract class ViewVisitor {

    /**
     * OnEvent will be fired when whatever the ViewVisitor installed fires
     * (For example, if the ViewVisitor installs watches for clicks, then OnEvent will be called
     * on click)
     */
    public interface OnEventListener {
        public void OnEvent(View host, String eventName, boolean debounce);
    }

    public static class PathElement {
        public PathElement(String vClass, int ix, int vId, int fId, String cDesc, String vTag, String fClass) {
            viewClassName = vClass;
            index = ix;
            viewId = vId;
            findId = fId;
            findClassName = fClass;
            contentDescription = cDesc;
            tag = vTag;
        }

        public String toString() {
            try {
                final JSONObject ret = new JSONObject();
                if (null != viewClassName) {
                    ret.put("view_class", viewClassName);
                }
                if (index > -1) {
                    ret.put("index", index);
                }
                if (viewId > -1) {
                    ret.put("id", viewId);
                }
                if (findId > -1) {
                    ret.put("**/id", findId);
                }
                if (null != contentDescription) {
                    ret.put("contentDescription", contentDescription);
                }
                if (null != tag) {
                    ret.put("tag", tag);
                }
                if (null != findClassName) {
                    ret.put("**/view_class", findClassName);
                }
                return ret.toString();
            } catch (JSONException e) {
                throw new RuntimeException("Can't serialize PathElement to String", e);
            }
        }

        public final String viewClassName;
        public final int index;
        public final int viewId;
        public final int findId;
        public final String contentDescription;
        public final String tag;
        public final String findClassName;
    }

    /**
     * Attempts to apply mutator to every matching view. Use this to update properties
     * in the view hierarchy. If accessor is non-null, it will be used to attempt to
     * prevent calls to the mutator if the property already has the intended value.
     */
    public static class PropertySetVisitor extends ViewVisitor {
        public PropertySetVisitor(List<PathElement> path, Caller mutator, Caller accessor) {
            super(path);
            mMutator = mutator;
            mAccessor = accessor;
        }

        @Override
        public void cleanup() {
            // Do nothing, we don't have any references and we haven't installed any listeners.
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
                        } else if (desiredValue.equals(currentValue)) {
                            return;
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
    }

    /**
     * Adds an accessibility event, which will fire OnEvent, to every matching view.
     */
    public static class AddAccessibilityEventVisitor extends EventTriggeringVisitor {
        public AddAccessibilityEventVisitor(List<PathElement> path, int accessibilityEventType, String eventName, OnEventListener listener) {
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
        protected void accumulate(View found) {
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
                Class klass = v.getClass();
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
        public AddTextChangeListener(List<PathElement> path, String eventName, OnEventListener listener) {
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
        protected void accumulate(View found) {
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
        public ViewDetectorVisitor(List<PathElement> path, String eventName, OnEventListener listener) {
            super(path, eventName, listener, false);
            mSeen = false;
        }

        @Override
        public void cleanup() {
            ; // Do nothing, we don't have anything to leak :)
        }

        @Override
        protected void accumulate(View found) {
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
        public EventTriggeringVisitor(List<PathElement> path, String eventName, OnEventListener listener, boolean debounce) {
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
        findTargetsInRoot(rootView, mPath);
    }

    /**
     * Removes listeners and frees resources associated with the visitor. Once cleanup is called,
     * the ViewVisitor should not be used again.
     */
    public abstract void cleanup();

    protected ViewVisitor(List<PathElement> path) {
        mPath = path;
    }

    protected abstract void accumulate(View found);
    protected abstract String name();

    private void findTargetsInRoot(View givenRootView, List<PathElement> path) {
        if (path.isEmpty()) {
            return;
        }

        final PathElement rootPathElement = path.get(0);
        final List<PathElement> childPath = path.subList(1, path.size());
        final View rootView = findFrom(rootPathElement, givenRootView);

        if (null != rootView &&
                rootPathElement.index <= 0 &&
                matches(rootPathElement, rootView)) {
            findTargetsInMatchedView(rootView, childPath);
        }
    }

    private void findTargetsInMatchedView(View alreadyMatched, List<PathElement> remainingPath) {
        // When this is run, alreadyMatched has already been matched to a path prefix.
        // path is a possibly empty "remaining path" suffix left over after the match

        if (remainingPath.isEmpty()) {
            // Nothing left to match- we're found!
            accumulate(alreadyMatched);
            return;
        }

        if (!(alreadyMatched instanceof ViewGroup)) {
            // Matching a non-empty path suffix is impossible, because we have no children
            return;
        }

        final ViewGroup parent = (ViewGroup) alreadyMatched;
        final PathElement matchElement = remainingPath.get(0);
        final List<PathElement> nextPath = remainingPath.subList(1, remainingPath.size());
        final int matchIndex = matchElement.index;

        final int childCount = parent.getChildCount();
        int matchCount = 0;
        for (int i = 0; i < childCount; i++) {
            final View givenChild = parent.getChildAt(i);
            final View child = findFrom(matchElement, givenChild);

            if (null != child && matches(matchElement, child)) {
                if (matchCount == matchIndex || -1 == matchIndex) {
                    findTargetsInMatchedView(child, nextPath);
                }

                matchCount++;
                if (matchIndex >= 0 && matchCount > matchIndex) {
                    return;
                }
            }
        }
    }

    private View findFrom(PathElement findElement, View subject) {
        if (-1 == findElement.findId || findElement.findId == subject.getId()) {
            if (null == findElement.findClassName || hasClassName(subject, findElement.findClassName)) {
                return subject;
            }
        }

        if (subject instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) subject;
            final int childCount = group.getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View child = group.getChildAt(i);
                final View result = findFrom(findElement, child);
                if (null != result) {
                    return result;
                }
            }
        }

        return null;
    }

    private boolean matches(PathElement matchElement, View subject) {
        if (null != matchElement.viewClassName) {
            if (!hasClassName(subject, matchElement.viewClassName)) {
                return false;
            }
        }

        final int matchId = matchElement.viewId;
        if (-1 != matchId) {
            final int subjectId = subject.getId();
            if (subjectId != matchId) {
                return false;
            }
        }

        final String matchContentDescription = matchElement.contentDescription;
        if (null != matchContentDescription) {
            final CharSequence description = subject.getContentDescription();
            if (null == description || ! matchContentDescription.equals(description)) {
                return false;
            }
        }

        final String matchTag = matchElement.tag;
        if (null != matchTag) {
            final Object subjectTag = subject.getTag();
            if (null == subjectTag || ! matchTag.equals(subjectTag.toString())) {
                return false;
            }
        }

        return true;
    }

    private static boolean hasClassName(Object o, String className) {
        Class klass = o.getClass();
        while (true) {
            if (klass.getCanonicalName().equals(className)) {
                return true;
            }

            if (klass == Object.class) {
                return false;
            }

            klass = klass.getSuperclass();
        }
    }

    private final List<PathElement> mPath;

    private static final String LOGTAG = "MixpanelAPI.ViewVisitor";
}
