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
import java.util.HashMap;
import java.util.Map;


public class ViewEdit implements Runnable {

    public class BadInstructionsException extends Exception {
        private BadInstructionsException(String message) {
            super(message);
        }

        private BadInstructionsException(String message, Throwable exception) {
            super(message, exception);
        }
    }

    /*
     * Apparently this is the actual state of the actual art.
     */
    public static Method getCompatibleMethod(Class klass, String methodName, Class[] args, Class result) {
        for (Method method : klass.getMethods()) {
            final String foundName = method.getName();
            final Class[] params = method.getParameterTypes();

            if (!foundName.equals(methodName) || params.length != args.length) {
                continue;
            }

            final Class resultType = method.getReturnType();
            if (! result.isAssignableFrom(resultType)) { // TODO need a test Void.TYPE.isAssignableFrom(Void.TYPE)
                continue;
            }

            boolean assignable = true;
            for (int i = 0; i < params.length && assignable; i++) {
                Class argumentType = args[i];

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
                return method;
            }
        }

        return null;
    }

    public ViewEdit(JSONObject source, View rootView) throws BadInstructionsException {
        mSource = source;
        mRootView = rootView;
        try {
            mPath = source.getJSONArray("path");
            mViewId = mSource.getInt("view_id");
            mMethodName = mSource.getString("method");

            final JSONArray argsAndTypes = mSource.getJSONArray("args");
            mMethodArgs = new Object[argsAndTypes.length()];
            mMethodTypes = new Class[argsAndTypes.length()];

            for (int i = 0; i < argsAndTypes.length(); i++) {
                final JSONArray argPlusType = argsAndTypes.getJSONArray(i);
                final Object jsonArg = argPlusType.get(0);
                final String argType = argPlusType.getString(1);
                mMethodArgs[i] = convertArgument(jsonArg, argType);
                mMethodTypes[i] = mMethodArgs[i].getClass();
            }

            if (mPath.length() == 0) {
                throw new BadInstructionsException("Path selector was empty");
            }
        } catch (JSONException e) {
            throw new BadInstructionsException("Can't interpret instructions due to JSONException", e);
        }
    }

    private View findTarget(View curView, JSONArray path, int curIndex) {
        try {
            path = new JSONArray(path.toString()); // Need to copy since we don't want remove(0) here to affect the parent calls
            JSONObject targetView = path.getJSONObject(0);
            String targetViewClass = targetView.getString("view_class");
            int targetIndex = targetView.getInt("index");
            path.remove(0);

            if (targetViewClass.equals(curView.getClass().getCanonicalName()) && targetIndex == curIndex) {
                if (path.length() == 0) {
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

                    View target = findTarget(viewGroup.getChildAt(i), path, index);
                    if (target != null) {
                        return target;
                    }
                }
            }

            return null;
        } catch (JSONException e) {
            Log.e(LOGTAG, "Exception finding target view", e);
            return null;
        }
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

    @Override
    public void run() {
        View target;
        if (mViewId != -1) {
            Log.i(LOGTAG, "Looking for view with ID " + mViewId);
            target = mRootView.findViewById(mViewId);
        } else {
            target = findTarget(mRootView, mPath, 1);
        }

        try {
            final Class viewClass = target.getClass();
            final Method editMethod = getCompatibleMethod(viewClass, mMethodName, mMethodTypes, Void.TYPE);

            if (editMethod != null) {
                editMethod.invoke(target, mMethodArgs);
            } else {
            }
        } catch (IllegalAccessException e) {
            Log.e(LOGTAG, "Exception thrown during edit", e);
        } catch (InvocationTargetException e) {
            Log.e(LOGTAG, "Exception thrown during edit", e);
        }
    }


    private int mViewId = -1;
    private final JSONArray mPath;
    private final JSONObject mSource;
    private final View mRootView;
    private final String mMethodName;
    private final Object[] mMethodArgs;
    private final Class[] mMethodTypes;

    private static final String LOGTAG = "Mixpanel.Introspector.ViewEdit";
}
