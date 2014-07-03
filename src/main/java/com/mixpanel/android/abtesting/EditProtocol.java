package com.mixpanel.android.abtesting;


import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class EditProtocol {

    public class BadInstructionsException extends Exception {
        public BadInstructionsException(String message) {
            super(message);
        }

        public BadInstructionsException(String message, Exception e) {
            super(message, e);
        }
    }

    public ViewEdit readEdit(JSONObject source)
        throws BadInstructionsException {
        try {
            final int viewId = source.getInt("view_id");

            final JSONArray pathDesc = source.getJSONArray("path");
            final List<ViewEdit.PathElement> path = new ArrayList<ViewEdit.PathElement>();
            for(int i = 0; i < pathDesc.length(); i++) {
                final JSONObject targetView = pathDesc.getJSONObject(i);
                final String targetViewClass = targetView.getString("view_class");
                final int targetIndex = targetView.getInt("index");
                path.add(new ViewEdit.PathElement(targetViewClass, targetIndex));
            }

            final String methodName = source.getString("method");

            final JSONArray argsAndTypes = source.getJSONArray("args");
            final Object[] methodArgs = new Object[argsAndTypes.length()];
            final Class[] methodTypes = new Class[argsAndTypes.length()];

            for (int i = 0; i < argsAndTypes.length(); i++) {
                final JSONArray argPlusType = argsAndTypes.getJSONArray(i);
                final Object jsonArg = argPlusType.get(0);
                final String argType = argPlusType.getString(1);
                methodArgs[i] = convertArgument(jsonArg, argType);
                methodTypes[i] = methodArgs[i].getClass();
            }

            if (path.size() == 0) {
                throw new BadInstructionsException("Path selector was empty");
            }

            final ViewEdit.Caller caller = new ViewEdit.Caller(methodName, methodArgs, Void.TYPE);
            return new ViewEdit(viewId, path, caller);
        } catch (JSONException e) {
            throw new BadInstructionsException("Can't interpret instructions due to JSONException", e);
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
}
