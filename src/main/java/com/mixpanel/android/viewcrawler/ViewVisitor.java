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

    public interface OnEventListener {
        public void OnEvent(View host, String eventName, boolean debounce);
    }

    public static class PathElement {
        public PathElement(String vClass, int ix, int vId, int fId, String vTag) {
            viewClassName = vClass;
            index = ix;
            viewId = vId;
            findId = fId;
            tag = vTag;
        }

        public String toString() {
            try {
                final JSONObject ret = new JSONObject();
                if (null != viewClassName) {
                    ret.put("viewClassName", viewClassName);
                }
                if (index > -1) {
                    ret.put("index", index);
                }
                if (viewId > -1) {
                    ret.put("viewId", viewId);
                }
                if (findId > -1) {
                    ret.put("findId", findId);
                }
                if (null != tag) {
                    ret.put("tag", tag);
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
        public final String tag;
    }

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
                final TrackingAccessibilityDelegate watcherDelegate = entry.getValue();
                final View.AccessibilityDelegate realDelegate = watcherDelegate.getRealDelegate();
                final View.AccessibilityDelegate currentDelegate = getOldDelegate(v);
                if (currentDelegate == watcherDelegate) {
                    // Just remove ourselves from the call chain
                    v.setAccessibilityDelegate(realDelegate);
                } else if (currentDelegate instanceof TrackingAccessibilityDelegate){
                    final TrackingAccessibilityDelegate currentChain = (TrackingAccessibilityDelegate) currentDelegate;
                    currentChain.removeFromDelegateChain(watcherDelegate);
                } else {
                    // In this case, we've been replaced by another delegate or removed by existing code.
                    // Best not to meddle with the existing delegate.
                }
            }
        }

        @Override
        protected void accumulate(View found) {
            if (mWatching.containsKey(found)) {
                ; // Do nothing, we're already installed
            } else {
                View.AccessibilityDelegate realDelegate = getOldDelegate(found);
                View.AccessibilityDelegate newDelegate = new TrackingAccessibilityDelegate(realDelegate);
                found.setAccessibilityDelegate(newDelegate);
            }
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
            if (mWatching.containsKey(found)) {
                ; // Do nothing
            } else if (found instanceof TextView) {
                final TextView foundTextView = (TextView) found;
                final TextWatcher watcher = new TrackingTextWatcher(foundTextView);
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

    // ViewDetectors are STATEFUL. They only work if you use the same detector to detect
    // Views appearing and disappearing.
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

    public ViewVisitor(List<PathElement> path) {
        mPath = path;
    }

    public void visit(View rootView) {
        findTargetsInRoot(rootView, mPath);
    }

    protected abstract void accumulate(View found);
    protected abstract String name();
    public abstract void cleanup();

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
        if (findElement.findId > -1) {
            // This could still return subject, which is ok.
            return subject.findViewById(findElement.findId);
        } else {
            return subject;
        }
    }

    private boolean matches(PathElement matchElement, View subject) {
        final String matchClassName = matchElement.viewClassName;

        if (null != matchClassName) {
            Class klass = subject.getClass();
            while (true) {
                if (klass.getCanonicalName().equals(matchClassName)) {
                    break;
                }

                if (klass == Object.class) {
                    return false;
                }

                klass = klass.getSuperclass();
            }
        }

        final int matchId = matchElement.viewId;
        if (-1 != matchId) {
            final int subjectId = subject.getId();
            if (subjectId != matchId) {
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

    private final List<PathElement> mPath;

    private static final String LOGTAG = "MixpanelAPI.ViewVisitor";
}
