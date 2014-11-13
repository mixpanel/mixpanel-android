package com.mixpanel.android.viewcrawler;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

import com.mixpanel.android.mpmetrics.MPConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

@TargetApi(14)
/* package */ abstract class ViewVisitor {

    public interface OnVisitedListener {
        public void OnVisited(View host, String eventName);
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

    public static class AddListenerVisitor extends ViewVisitor {
        public AddListenerVisitor(List<PathElement> path, int accessibilityEventType, String eventName, OnVisitedListener listener) {
            super(path);
            mEventName = eventName;
            mListener = listener;
            mEventType = accessibilityEventType;
        }

        @Override
        public void accumulate(View found) {
            View.AccessibilityDelegate realDelegate = getOldDelegate(found);
            if (realDelegate instanceof TrackingAccessibilityDelegate) {
                final TrackingAccessibilityDelegate oldTrackingDelegate = (TrackingAccessibilityDelegate) realDelegate;
                final String oldEventName = oldTrackingDelegate.getEventName();
                if (null != mEventName && mEventName.equals(oldEventName)) {
                    return; // Don't reset the same event
                } else {
                    realDelegate = null; // Don't allow multiple event handlers on the same view
                }
            }

            View.AccessibilityDelegate newDelegate = new TrackingAccessibilityDelegate(mEventName, realDelegate);
            found.setAccessibilityDelegate(newDelegate);
        }

        protected String name() {
            return mEventName + " event when (" + mEventType + ")";
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
            public TrackingAccessibilityDelegate(String eventName, View.AccessibilityDelegate realDelegate) {
                mEventName = eventName;
                mRealDelegate = realDelegate;
            }

            public String getEventName() {
                return mEventName;
            }

            @Override
            public void sendAccessibilityEvent(View host, int eventType) {
                if (eventType == mEventType) {
                    mListener.OnVisited(host, mEventName);
                }

                if (null != mRealDelegate) {
                    mRealDelegate.sendAccessibilityEvent(host, eventType);
                }
            }

            private final String mEventName;
            private final View.AccessibilityDelegate mRealDelegate;
        }

        private final String mEventName;
        private final OnVisitedListener mListener;
        private final int mEventType;
    }

    // ViewDetectors are STATEFUL. They only work if you use the same detector to detect
    // Views appearing and disappearing.
    public static class ViewDetectorVisitor extends ViewVisitor {
        public ViewDetectorVisitor(List<PathElement> path, String eventName, OnVisitedListener listener) {
            super(path);

            mSeen = false;
            mListener = listener;
            mEventName = eventName;
        }

        protected void accumulate(View found) {
            if (found != null && !mSeen) { // TODO THIS BREAKS ON MULTIPLE VISITS! PROBABLY?
                mListener.OnVisited(found, mEventName);
            }

            mSeen = (found != null);
        }

        protected String name() {
            return mEventName + " when Detected";
        }

        private boolean mSeen;
        private final OnVisitedListener mListener;
        private final String mEventName;
    }

    public ViewVisitor(List<PathElement> path) {
        mPath = path;
    }

    public void visit(View rootView) {
        findTargetsInRoot(rootView, mPath);
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
