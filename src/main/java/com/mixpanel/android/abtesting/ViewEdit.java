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
            viewClass = vClass;
            index = ix;
        }

        public String toString() {
            return "{\"viewClass\": " + viewClass + ", \"index\": " + index + "}";
        }

        public final String viewClass;
        public final int index;
    }

    public static class PropertySetEdit extends ViewEdit {
        public PropertySetEdit(int viewId, List<PathElement> path, Caller caller) {
            super(viewId, path);
            mCaller = caller;
        }

        public void applyEdit(View target) {
            mCaller.applyMethod(target);
        }

        private final Caller mCaller;
    }

    public static class AddListenerEdit extends ViewEdit {
        public AddListenerEdit(int viewId, List<PathElement> path, String eventName, MixpanelAPI mixpanel) {
            super(viewId, path);
            mEventName = eventName;
            mMixpanel = mixpanel;
        }

        public void applyEdit(View target) {
            final View.AccessibilityDelegate realDelegate = getOldDelegate(target);
            target.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void sendAccessibilityEvent(View host, int eventType) {
                    Log.d(LOGTAG, "EVENT SEEN: " + mEventName);
                    if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                        mMixpanel.track(mEventName, null);
                    }

                    if (null != realDelegate) {
                        realDelegate.sendAccessibilityEvent(host, eventType);
                    }
                }
            });
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

        private final String mEventName;
        private final MixpanelAPI mMixpanel;
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

    public View findTarget(View rootView) {
        if (mViewId != -1) {
            return rootView.findViewById(mViewId);
        } else {
            return findTargetOnPath(rootView, mPath, 0);
        }
    }

    protected abstract void applyEdit(View targetView);

    private View findTargetOnPath(View curView, List<PathElement> path, int curIndex) {
        if (path.isEmpty()) {
            return null; // The empty path matches nothing in this model
        }

        final PathElement targetView = path.get(0);
        final String targetViewClass = targetView.viewClass;
        final int targetIndex = targetView.index;

        if (targetViewClass.equals(curView.getClass().getCanonicalName()) && targetIndex == curIndex) {
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
        }

        return null;
    }

    private int mViewId = -1;
    private final List<PathElement> mPath;

    private static final String LOGTAG = "Mixpanel.Introspector.ViewEdit";
}
