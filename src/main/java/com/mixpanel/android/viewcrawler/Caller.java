package com.mixpanel.android.viewcrawler;

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

    public Object[] getArgs() {
        return mMethodArgs;
    }

    public Object applyMethod(View target) {
        final Class klass = target.getClass();

        for (Method method : klass.getMethods()) {
            final String foundName = method.getName();
            final Class[] params = method.getParameterTypes();

            if (!foundName.equals(mMethodName) || params.length != mMethodArgs.length) {
                continue;
            }

            final Class assignType = assignableArgType(mMethodResultType);
            final Class resultType = assignableArgType(method.getReturnType());
            if (! assignType.isAssignableFrom(resultType)) {
                continue;
            }

            boolean assignable = true;
            for (int i = 0; i < params.length && assignable; i++) {
                final Class argumentType = assignableArgType(mMethodTypes[i]);
                final Class paramType = assignableArgType(params[i]);
                assignable = paramType.isAssignableFrom(argumentType);
            }

            if (! assignable) {
                continue;
            }

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

        return null;
    }

    private static Class assignableArgType(Class type) {
        // a.isAssignableFrom(b) only tests if b is a
        // subclass of a. It does not handle the autoboxing case,
        // i.e. when a is an int and b is an Integer, so we have
        // to make the Object types primitive types. When the
        // function is finally invoked, autoboxing will take
        // care of the the cast.
        if (type == Integer.class) {
            type = int.class;
        } else if (type == Float.class) {
            type = float.class;
        } else if (type == Double.class) {
            type = double.class;
        } else if (type == Boolean.class) {
            type = boolean.class;
        }

        return type;
    }

    private final String mMethodName;
    private final Object[] mMethodArgs;
    private final Class[] mMethodTypes;
    private final Class mMethodResultType;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelABTest.Caller";
}
