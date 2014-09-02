package com.mixpanel.android.abtesting;

import android.annotation.TargetApi;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

import com.mixpanel.android.mpmetrics.MixpanelAPI;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@TargetApi(14)
/* package */ abstract class ViewEdit {

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

    public static class PropertySetEdit extends ViewEdit {
        public PropertySetEdit(int viewId, List<PathElement> path, Caller mutator, Caller accessor) {
            super(viewId, path);
            mMutator = mutator;
            mAccessor = accessor;
        }

        public void applyEdit(View target) {
            // TODO the following strategy is pretty gross for Bitmaps. We may need to special case their editors
            if (null != mAccessor) {
                final Object[] setArgs = mMutator.getArgs();
                if (1 == setArgs.length) {
                    final Object desiredValue = setArgs[0];
                    final Object currentValue = mAccessor.applyMethod(target);

                    if (desiredValue == currentValue) {
                        return;
                    } else if (null != desiredValue && desiredValue.equals(currentValue)) {
                        return;
                    }
                }
            }

            mMutator.applyMethod(target);
        }

        private final Caller mMutator;
        private final Caller mAccessor;
    }

    public static class AddListenerEdit extends ViewEdit {
        public AddListenerEdit(int viewId, List<PathElement> path, String eventName, MixpanelAPI mixpanel) {
            super(viewId, path);
            mEventName = eventName;
            mMixpanel = mixpanel;
        }

        // TODO needs test for duplicate tracking prevention...
        // TODO what about tracking two different events on the same target?
        public void applyEdit(View target) {
            final View.AccessibilityDelegate realDelegate = getOldDelegate(target);
            if (realDelegate instanceof TrackingAccessibilityDelegate) {
                return;
            }

            View.AccessibilityDelegate newDelegate = new TrackingAccessibilityDelegate(mEventName, realDelegate);
            target.setAccessibilityDelegate(newDelegate);
        }

        // TODO must API LEVEL check here (Method appears in API 19)
        private View.AccessibilityDelegate getOldDelegate(View v) {
            View.AccessibilityDelegate ret = null;
            try {
                Class klass = v.getClass();
                Method m = klass.getMethod("getAccessibilityDelegate");
                ret = (View.AccessibilityDelegate) m.invoke(v);
            } catch (NoSuchMethodException e) {
                Log.d(LOGTAG, "View has no getAccessibilityDelegate method - clobbering existing delegate");
            } catch (IllegalAccessException e) {
                Log.d(LOGTAG, "View does not have a public getAccessibilityDelegate method - clobbering existing delegate");
            } catch (InvocationTargetException e) {
                Log.e(LOGTAG, "getAccessibilityDelegate threw an apparently impossible exception", e);
            }

            return ret;
        }

        private class TrackingAccessibilityDelegate extends View.AccessibilityDelegate {
            public TrackingAccessibilityDelegate(String eventName, View.AccessibilityDelegate realDelegate) {
                mEventName = eventName;
                mRealDelegate = realDelegate;
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

    // TODO this is stateful, not clear whether that's the right thing...
    // (Consider moving state to the EditBinding)
    public static class ViewDetectorEdit extends ViewEdit {
        public ViewDetectorEdit(int viewId, List<PathElement> path, String eventName, MixpanelAPI mixpanelAPI) {
            super(viewId, path);

            mSeen = false;
            mMixpanelAPI = mixpanelAPI;
            mEventName = eventName;
        }

        public void edit(View rootView) {
            final View target = findTarget(rootView);
            if (target != null && !mSeen) {
                mMixpanelAPI.track(mEventName, null);
            }

            mSeen = (target != null);
        }

        @Override
        protected void applyEdit(View targetView) { // TODO CODE SMELL.
            throw new UnsupportedOperationException("A View Detector should never attempt to apply an edit.");
        }

        private boolean mSeen;
        private final MixpanelAPI mMixpanelAPI;
        private final String mEventName;
    }

    public ViewEdit(int viewId, List<PathElement> path) {
        mViewId = viewId;
        mPath = path;
    }

    /** Call ONLY on the UI thread **/
    public void edit(View rootView) {
        final View target = findTarget(rootView);
        if (target != null) {
            applyEdit(target);
        } else {
            Log.i(LOGTAG, "Could not find target with id " + mViewId + " or path " + mPath.toString());
        }
    }

    protected abstract void applyEdit(View targetView);

    protected View findTarget(View rootView) {
        if (mViewId != -1) {
            // TODO BUGGY IN TWO WAYS
            // - Will do screwy stuff when we have duplicate ids
            // - Will never match multiple views
            return rootView.findViewById(mViewId);
        }
        // ELSE

        return findTargetOnPath(rootView, mPath, 0);
    }

    private View findTargetOnPath(View curView, List<PathElement> path, int curIndex) {
        if (path.isEmpty()) {
            Log.d(LOGTAG, "Empty path doesn't match anything!");
            return null; // The empty path matches nothing in this model
        }

        final PathElement targetView = path.get(0);
        final String targetViewClass = targetView.viewClassName;
        final String currentViewName = curView.getClass().getCanonicalName();
        if (! targetViewClass.equals(currentViewName)) {
            Log.d(LOGTAG, "Looking for " + targetViewClass + " found non-matching class " + currentViewName);
            return null;
        }

        final int targetIndex = targetView.index;
        if (targetIndex != curIndex) {
            Log.d(LOGTAG, "Not my index");
            return null;
        }

        final List<PathElement> childPath = path.subList(1, path.size());
        if (childPath.size() == 0) {
            Log.d(LOGTAG, "That's the last element! We're good!");
            return curView;
        }

        if (!(curView instanceof ViewGroup)) {
            Log.d(LOGTAG, "Found the view, but it isn't a group so it can't have children? Probably a bad path...");
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


    private int mViewId = -1;
    private final List<PathElement> mPath;

    private static final String LOGTAG = "Mixpanel.Introspector.ViewEdit";
}
