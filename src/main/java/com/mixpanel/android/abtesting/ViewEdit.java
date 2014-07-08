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

    public static class Caller {
        public Caller(String methodName, Object[] methodArgs, Class resultType) {
            mMethodName = methodName;
            mMethodArgs = methodArgs;
            mMethodResultType = resultType;

            mMethodTypes = new Class[mMethodArgs.length];
            for (int i = 0; i < mMethodArgs.length; i++) {
                mMethodTypes[i] = mMethodArgs[i].getClass();
            }
        }

        public Object applyMethod(View target) {
            final Class klass = target.getClass();

            for (Method method : klass.getMethods()) {
                final String foundName = method.getName();
                final Class[] params = method.getParameterTypes();

                if (!foundName.equals(mMethodName) || params.length != mMethodArgs.length) {
                    continue;
                }

                final Class resultType = method.getReturnType();
                if (! mMethodResultType.isAssignableFrom(resultType)) {
                    continue;
                }

                boolean assignable = true;
                for (int i = 0; i < params.length && assignable; i++) {
                    Class argumentType = mMethodTypes[i];

                    // a.isAssignableFrom(b) only tests if b is a
                    // subclass of a. It does not handle the autoboxing case, i.e. when a is an int and
                    // b is an Integer, so we have to make the Object types primitive types. When the
                    // function is finally invoked, autoboxing will take care of the the cast.
                    if (argumentType == Integer.class) {
                        argumentType = int.class;
                    } else if (argumentType == Float.class) {
                        argumentType = float.class;
                    } else if (argumentType == Double.class) {
                        argumentType = double.class;
                    } else if (argumentType == Boolean.class) {
                        argumentType = boolean.class;
                    }

                    assignable = params[i].isAssignableFrom(argumentType);
                }

                if (assignable) {
                    try {
                        return method.invoke(target, mMethodArgs);
                    } catch (IllegalAccessException e) {
                        Log.e(LOGTAG, "Can't invoke method " + method.getName(), e);
                        // Don't return, keep trying
                    } catch (InvocationTargetException e) {
                        Log.e(LOGTAG, "Method " + method.getName() + " threw an exception", e);
                        return null;
                    }
                }
            }

            return null;
        }

        private final String mMethodName;
        private final Object[] mMethodArgs;
        private final Class[] mMethodTypes;
        private final Class mMethodResultType;
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
