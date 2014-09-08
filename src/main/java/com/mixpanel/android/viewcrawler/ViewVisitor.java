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
        public PathElement(String vClass, int ix) {
            viewClassName = vClass;
            index = ix;
        }

        public String toString() {
            return "{\"viewClassName\": " + viewClassName + ", \"index\": " + index + "}";
        }

        public final String viewClassName;
        public final int index;
    }

    public static abstract class ViewEditor extends ViewVisitor {
        public ViewEditor(List<PathElement> path) {
            super(path);
        }

        @Override
        public void visit(View rootView) {
            final View target = findTarget(rootView);
            if (target != null) {
                applyEdit(target);
            }
        }

        protected abstract void applyEdit(View targetView);
    }

    public static class PropertySetVisitor extends ViewEditor {
        public PropertySetVisitor(List<PathElement> path, Caller mutator, Caller accessor) {
            super(path);
            mMutator = mutator;
            mAccessor = accessor;
        }

        @Override
        public void applyEdit(View target) {
            if (null != mAccessor) {
                final Object[] setArgs = mMutator.getArgs();
                if (1 == setArgs.length) {
                    final Object desiredValue = setArgs[0];
                    final Object currentValue = mAccessor.applyMethod(target);

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

            mMutator.applyMethod(target);
        }

        private final Caller mMutator;
        private final Caller mAccessor;
    }

    public static class AddListenerVisitor extends ViewEditor {
        public AddListenerVisitor(List<PathElement> path, String eventName, OnVisitedListener listener) {
            super(path);
            mEventName = eventName;
            mListener = listener;
        }

        @Override
        public void applyEdit(View target) {
            View.AccessibilityDelegate realDelegate = getOldDelegate(target);
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
            target.setAccessibilityDelegate(newDelegate);
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

        public void visit(View rootView) {
            final View target = findTarget(rootView);
            if (target != null && !mSeen) {
                mListener.OnVisited(mEventName);
            }

            mSeen = (target != null);
        }

        private boolean mSeen;
        private final OnVisitedListener mListener;
        private final String mEventName;
    }

    public ViewVisitor(List<PathElement> path) {
        mPath = path;
    }

    public abstract void visit(View rootView);

    protected View findTarget(View rootView) {
        if (mPath.isEmpty()) {
            return null;
        }

        final PathElement rootPathElement = mPath.get(0);
        final List<PathElement> childPath = mPath.subList(1, mPath.size());

        if (rootPathElement.index == 0 &&
            matchesClassName(rootPathElement.viewClassName, rootView)) {

            if (childPath.isEmpty()) {
                return rootView;
            }

            if (rootView instanceof ViewGroup) {
                final ViewGroup rootParent = (ViewGroup) rootView;
                return findTargetInChildren(rootParent, childPath);
            }
        }

        return null;
    }

    private View findTargetInChildren(ViewGroup parent, List<PathElement> path) {
        final PathElement matchElement = path.get(0);
        final List<PathElement> nextPath = path.subList(1, path.size());
        final String matchClass = matchElement.viewClassName;
        final int matchIndex = matchElement.index;

        final int childCount = parent.getChildCount();
        int matchCount = 0;
        for (int i = 0; i < childCount; i++) {
            final View child = parent.getChildAt(i);
            if (matchesClassName(matchClass, child)) {
                if (matchCount == matchIndex) {
                    if (nextPath.isEmpty()) {
                        return child;
                    } else if (child instanceof ViewGroup) {
                        final ViewGroup childGroup = (ViewGroup) child;
                        return findTargetInChildren(childGroup, nextPath);
                    }
                } else {
                    matchCount++;
                }
            }
        }

        return null;
    }

    private boolean matchesClassName(String className, Object o) {
        Class klass = o.getClass();
        while (true) {
            if (klass.getCanonicalName().equals(className)) {
                return true;
            }

            if (klass == Object.class) {
                return false;
            } else {
                klass = klass.getSuperclass();
            }
        }
    }

    private final List<PathElement> mPath;

    private static final String LOGTAG = "MixpanelAPI.ViewVisitor";
}
