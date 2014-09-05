package com.mixpanel.android.viewcrawler;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

import com.mixpanel.android.mpmetrics.MPConfig;
import com.mixpanel.android.mpmetrics.MixpanelAPI;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@TargetApi(14)
/* package */ abstract class ViewVisitor {

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
        public AddListenerVisitor(List<PathElement> path, String eventName, MixpanelAPI mixpanel) {
            super(path);
            mEventName = eventName;
            mMixpanel = mixpanel;
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
                    mMixpanel.track(mEventName, null);
                }

                if (null != mRealDelegate) {
                    mRealDelegate.sendAccessibilityEvent(host, eventType);
                }
            }

            private final String mEventName;
            private final View.AccessibilityDelegate mRealDelegate;
        }

        private final String mEventName;
        private final MixpanelAPI mMixpanel;
    }

    // ViewDetectors are STATEFUL. They only work if you use the same detector to detect
    // Views appearing and disappearing.
    public static class ViewDetectorVisitor extends ViewVisitor {
        public ViewDetectorVisitor(List<PathElement> path, String eventName, MixpanelAPI mixpanelAPI) {
            super(path);

            mSeen = false;
            mMixpanelAPI = mixpanelAPI;
            mEventName = eventName;
        }

        public void visit(View rootView) {
            final View target = findTarget(rootView);
            if (target != null && !mSeen) {
                mMixpanelAPI.track(mEventName, null);
            }

            mSeen = (target != null);
        }

        private boolean mSeen;
        private final MixpanelAPI mMixpanelAPI;
        private final String mEventName;
    }

    public ViewVisitor(List<PathElement> path) {
        mPath = path;
    }

    public abstract void visit(View rootView);

    protected View findTarget(View rootView) {
        return findTargetOnPath(rootView, mPath, 0);
    }

    private View findTargetOnPath(View curView, List<PathElement> path, int curIndex) {
        if (path.isEmpty()) {
            return null; // The empty path matches nothing in this model
        }

        final PathElement targetView = path.get(0);
        final String targetViewClass = targetView.viewClassName;
        final String currentViewName = curView.getClass().getCanonicalName();
        if (! targetViewClass.equals(currentViewName)) {
            return null;
        }

        final int targetIndex = targetView.index;
        if (targetIndex != curIndex) {
            return null;
        }

        final List<PathElement> childPath = path.subList(1, path.size());
        if (childPath.size() == 0) {
            return curView;
        }

        if (!(curView instanceof ViewGroup)) {
            return null;
        }

        final Map<String, Integer> viewIndex = new HashMap<String, Integer>();
        ViewGroup viewGroup = (ViewGroup) curView;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            final View child = viewGroup.getChildAt(i);
            int index = 0;
            if (viewIndex.containsKey(child.getClass().getCanonicalName())) {
                index = viewIndex.get(child.getClass().getCanonicalName()) + 1;
            }
            viewIndex.put(child.getClass().getCanonicalName(), index);

            View target = findTargetOnPath(viewGroup.getChildAt(i), childPath, index);
            if (target != null) {
                return target;
            }
        }

        return null;
    }

    private static abstract class ViewEditor extends ViewVisitor {
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

    private final List<PathElement> mPath;

    private static final String LOGTAG = "MixpanelAPI.ViewVisitor";
}
