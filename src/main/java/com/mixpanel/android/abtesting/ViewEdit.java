package com.mixpanel.android.abtesting;

import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ViewEdit {
    public class BadInstructionsException extends Exception {
        public BadInstructionsException(String message) {
            super(message);
        }

        public BadInstructionsException(String message, Exception e) {
            super(message, e);
        }
    }

    public ViewEdit(JSONObject source) throws BadInstructionsException {
        mSource = source;
        try {
            mViewId = mSource.getInt("view_id");

            final JSONArray path = source.getJSONArray("path");
            mPath = new ArrayList<PathElement>();
            for(int i = 0; i < path.length(); i++) {
                final JSONObject targetView = path.getJSONObject(i);
                final String targetViewClass = targetView.getString("view_class");
                final int targetIndex = targetView.getInt("index");
                mPath.add(new PathElement(targetViewClass, targetIndex));
            }

            mMethodName = mSource.getString("method");

            final JSONArray argsAndTypes = mSource.getJSONArray("args");
            mMethodArgs = new Object[argsAndTypes.length()];
            mMethodTypes = new Class[argsAndTypes.length()];
            mMethodResultType = Void.TYPE; // TODO temporary, need a way around this for the interrogator

            for (int i = 0; i < argsAndTypes.length(); i++) {
                final JSONArray argPlusType = argsAndTypes.getJSONArray(i);
                final Object jsonArg = argPlusType.get(0);
                final String argType = argPlusType.getString(1);
                mMethodArgs[i] = convertArgument(jsonArg, argType);
                mMethodTypes[i] = mMethodArgs[i].getClass();
            }

            if (mPath.size() == 0) {
                throw new BadInstructionsException("Path selector was empty");
            }
        } catch (JSONException e) {
            throw new BadInstructionsException("Can't interpret instructions due to JSONException", e);
        }
    }

    /** Call ONLY on the UI thread **/
    public void edit(View rootView) {
        final View target = findTarget(rootView);
        applyMethod(target);
    }

    public View findTarget(View rootView) {
        if (mViewId != -1) {
            return rootView.findViewById(mViewId);
        } else {
            return findTargetOnPath(rootView, mPath, 0);
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
                int index = 1;
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

    private Object convertArgument(Object jsonArgument, String type)
            throws BadInstructionsException {
        // Object is a Boolean, JSONArray, JSONObject, Number, String, or JSONObject.NULL
        try {
            if ("java.lang.CharSequence".equals(type)) { // Because we're assignable
                return (String) jsonArgument;
            } else if ("boolean".equals(type)) {
                return (Boolean) jsonArgument;
            } else if ("int".equals(type)) {
                return ((Number) jsonArgument).intValue();
            } else if ("float".equals(type)) {
                return ((Number) jsonArgument).floatValue();
            } else if ("B64Drawable".equals(type)) {
                byte[] bytes = Base64.decode((String) jsonArgument, 0);
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } else if ("hexstring".equals(type)) {
                int ret = new BigInteger((String) jsonArgument, 16).intValue();
                return ret;
            } else {
                throw new BadInstructionsException("Don't know how to interpret type " + type + " (arg was " + jsonArgument + ")");
            }
        } catch (ClassCastException e) {
            throw new BadInstructionsException("Couldn't interpret <" + jsonArgument + "> as " + type);
        }
    }

    private static class PathElement {
        public PathElement(String vClass, int ix) {
            viewClass = vClass;
            index = ix;
        }

        public final String viewClass;
        public final int index;
    }

    private int mViewId = -1;
    private final List<PathElement> mPath;
    private final JSONObject mSource;
    private final String mMethodName;
    private final Object[] mMethodArgs;
    private final Class[] mMethodTypes;
    private final Class mMethodResultType;

    private static final String LOGTAG = "Mixpanel.Introspector.ViewEdit";
}
