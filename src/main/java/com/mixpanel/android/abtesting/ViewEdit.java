package com.mixpanel.android.abtesting;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/* package */ class ViewEdit {

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

    public ViewEdit(int viewId, List<PathElement> path, Caller caller) {
        mViewId = viewId;
        mPath = path;
        mCaller = caller;
    }

    /** Call ONLY on the UI thread **/
    public void edit(View rootView) {
        final View target = findTarget(rootView);
        if (target != null) {
            mCaller.applyMethod(target);
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
    private final Caller mCaller;

    private static final String LOGTAG = "Mixpanel.Introspector.ViewEdit";
}
