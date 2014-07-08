package com.mixpanel.android.abtesting;

import android.util.Log;
import android.view.View;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/* package */ class Caller {
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

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelABTest.Caller";
}
