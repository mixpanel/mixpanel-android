package com.mixpanel.android.viewcrawler;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

import com.mixpanel.android.mpmetrics.MPConfig;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

@TargetApi(14)
/* package */ abstract class ViewVisitor {

    public interface OnVisitedListener {
        public void OnVisited(String eventName);
    }

    public static class PathElement {
        public PathElement(String vClass, int ix, int vId, String vTag) {
            viewClassName = vClass;
            index = ix;
            viewId = vId;
            tag = vTag;
        }

        public String toString() {
            return "{\"viewClassName\": " + viewClassName + ", \"index\": " + index + "}";
        }

        public final String viewClassName;
        public final int index;
        public final int viewId;
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

        private final Caller mMutator;
        private final Caller mAccessor;
    }

    public static class AddListenerVisitor extends ViewVisitor {
        public AddListenerVisitor(List<PathElement> path, String eventName, OnVisitedListener listener) {
            super(path);
            mEventName = eventName;
            mListener = listener;
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

        private View.AccessibilityDelegate getOldDelegate(View v) {
            View.AccessibilityDelegate ret = null;
            try {
                Class klass = v.getClass();
                Method m = klass.getMethod("getAccessibilityDelegate");
                ret = (View.AccessibilityDelegate) m.invoke(v);
            } catch (NoSuchMethodException e) {
                if (MPConfig.DEBUG) {
                    Log.d(LOGTAG, "View has no getAccessibilityDelegate method - we may be overwriting an existing delegate");
                }
            } catch (IllegalAccessException e) {
                if (MPConfig.DEBUG) {
                    Log.d(LOGTAG, "View does not have a public getAccessibilityDelegate method - overwriting any existing delegate");
                }
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
                if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                    mListener.OnVisited(mEventName);
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
                mListener.OnVisited(mEventName);
            }

            mSeen = (found != null);
        }

        private boolean mSeen;
        private final OnVisitedListener mListener;
        private final String mEventName;
    }

    public ViewVisitor(List<PathElement> path) {
        mPath = path;
    }

    public void visit(View rootView) {
        if (mPath.isEmpty()) {
            return;
        }

        final PathElement rootPathElement = mPath.get(0);
        final List<PathElement> childPath = mPath.subList(1, mPath.size());

        if (rootPathElement.index <= 0 &&
                matches(rootPathElement, rootView)) {

            if (childPath.isEmpty()) {
                accumulate(rootView);
            } else if (rootView instanceof ViewGroup) {
                final ViewGroup rootParent = (ViewGroup) rootView;
                findTargetsInChildren(rootParent, childPath);
            }
        }
    }

    protected abstract void accumulate(View found);

    private void findTargetsInChildren(ViewGroup parent, List<PathElement> path) {
        final PathElement matchElement = path.get(0);
        final List<PathElement> nextPath = path.subList(1, path.size());
        final int matchIndex = matchElement.index;

        final int childCount = parent.getChildCount();
        int matchCount = 0;
        for (int i = 0; i < childCount; i++) {
            final View child = parent.getChildAt(i);
            if (matches(matchElement, child)) {
                if (matchCount == matchIndex || -1 == matchIndex) {
                    if (nextPath.isEmpty()) {
                        accumulate(child);
                    } else if (child instanceof ViewGroup) {
                        final ViewGroup childGroup = (ViewGroup) child;
                        findTargetsInChildren(childGroup, nextPath);
                    }
                }

                matchCount++;
                if (matchIndex >= 0 && matchCount > matchIndex) {
                    return;
                }
            }
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
            if (! matchTag.equals(subjectTag.toString())) {
                return false;
            }
        }

        return true;
    }

    private final List<PathElement> mPath;

    private static final String LOGTAG = "MixpanelAPI.ViewVisitor";
}
