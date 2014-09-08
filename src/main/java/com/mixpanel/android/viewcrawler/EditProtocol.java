package com.mixpanel.android.viewcrawler;

import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/* package */ class EditProtocol {

    public static class BadInstructionsException extends Exception {
        public BadInstructionsException(String message) {
            super(message);
        }

        public BadInstructionsException(String message, Exception e) {
            super(message, e);
        }
    }

    public PropertyDescription readPropertyDescription(Class targetClass, JSONObject propertyDesc)
            throws BadInstructionsException {
        try {
            final String propName = propertyDesc.getString("name");

            Caller accessor = null;
            if (propertyDesc.has("get")) {
                final JSONObject accessorConfig = propertyDesc.getJSONObject("get");
                final String accessorName = accessorConfig.getString("selector");
                final String accessorResultTypeName = accessorConfig.getJSONObject("result").getString("type");
                final Class accessorResultType = Class.forName(accessorResultTypeName);
                accessor = new Caller(accessorName, NO_PARAMS, accessorResultType);
            }

            final String mutatorName;
            if (propertyDesc.has("set")) {
                final JSONObject mutatorConfig = propertyDesc.getJSONObject("set");
                mutatorName = mutatorConfig.getString("selector");
            } else {
                mutatorName = null;
            }

            return new PropertyDescription(propName, targetClass, accessor, mutatorName);
        } catch (JSONException e) {
            throw new BadInstructionsException("Can't read property JSON", e);
        } catch (ClassNotFoundException e) {
            throw new BadInstructionsException("Can't read property JSON, relevant arg/return class not found", e);
        }
    }

    public ViewVisitor readEdit(JSONObject source, ViewVisitor.OnVisitedListener listener)
            throws BadInstructionsException {
        try {
            final JSONArray pathDesc = source.getJSONArray("path");
            final List<ViewVisitor.PathElement> path = new ArrayList<ViewVisitor.PathElement>();

            for (int i = 0; i < pathDesc.length(); i++) {
                final JSONObject targetView = pathDesc.getJSONObject(i);
                final String targetViewClass = targetView.getString("view_class");
                final int targetIndex = targetView.getInt("index");
                path.add(new ViewVisitor.PathElement(targetViewClass, targetIndex));
            }

            if (path.size() == 0) {
                throw new BadInstructionsException("Path selector was empty.");
            }

            if (source.has("property")) {
                final ViewVisitor.PathElement pathEnd = path.get(path.size() - 1);
                final String targetClassName = pathEnd.viewClassName;
                final Class targetClass;
                try {
                    targetClass = Class.forName(targetClassName);
                } catch (ClassNotFoundException e) {
                    throw new BadInstructionsException("Can't find class for visit path: " + targetClassName, e);
                }

                final PropertyDescription prop = readPropertyDescription(targetClass, source.getJSONObject("property"));

                final JSONArray argsAndTypes = source.getJSONArray("args");
                final Object[] methodArgs = new Object[argsAndTypes.length()];
                for (int i = 0; i < argsAndTypes.length(); i++) {
                    final JSONArray argPlusType = argsAndTypes.getJSONArray(i);
                    final Object jsonArg = argPlusType.get(0);
                    final String argType = argPlusType.getString(1);
                    methodArgs[i] = convertArgument(jsonArg, argType);
                }

                final Caller mutator = prop.makeMutator(methodArgs);
                if (null == mutator) {
                    throw new BadInstructionsException("Can't update a read-only property " + prop.name + " (add a mutator to make this work)");
                }

                return new ViewVisitor.PropertySetVisitor(path, mutator, prop.accessor);
            } else if (source.has("event_name")) {
                final String eventName = source.getString("event_name");
                final String eventType = source.getString("event_type");
                if ("click".equals(eventType)) {
                    return new ViewVisitor.AddListenerVisitor(path, eventName, listener);
                } else if ("detected".equals(eventType)) {
                    return new ViewVisitor.ViewDetectorVisitor(path, eventName, listener);
                } else {
                    throw new BadInstructionsException("Mixpanel can't track event type \"" + eventType + "\"");
                }
            }

            throw new BadInstructionsException("Instructions contained neither a method to call nor an event to track");
        } catch (JSONException e) {
            throw new BadInstructionsException("Can't interpret instructions due to JSONException", e);
        }
    }

    public ViewSnapshot readSnapshotConfig(JSONObject source)
            throws BadInstructionsException {
        final List<PropertyDescription> properties = new ArrayList<PropertyDescription>();

        try {
            final JSONArray classes = source.getJSONArray("classes");
            for (int classIx = 0; classIx < classes.length(); classIx++) {
                final JSONObject classDesc = classes.getJSONObject(classIx);
                final String targetClassName = classDesc.getString("name");
                final Class targetClass = Class.forName(targetClassName);

                final JSONArray propertyDescs = classDesc.getJSONArray("properties");
                for (int i = 0; i < propertyDescs.length(); i++) {
                    final JSONObject propertyDesc = propertyDescs.getJSONObject(i);
                    final PropertyDescription desc = readPropertyDescription(targetClass, propertyDesc);
                    properties.add(desc);
                }
            }

            return new ViewSnapshot(properties);
        } catch (JSONException e) {
            throw new BadInstructionsException("Can't read snapshot configuration", e);
        } catch (ClassNotFoundException e) {
            throw new BadInstructionsException("Can't resolve types for snapshot configuration", e);
        }
    }

    private Object convertArgument(Object jsonArgument, String type)
            throws BadInstructionsException {
        // Object is a Boolean, JSONArray, JSONObject, Number, String, or JSONObject.NULL
        try {
            if ("java.lang.CharSequence".equals(type)) { // Because we're assignable
                return (String) jsonArgument;
            } else if ("boolean".equals(type) || "java.lang.Boolean".equals(type)) {
                return (Boolean) jsonArgument;
            } else if ("int".equals(type) || "java.lang.Integer".equals(type)) {
                return ((Number) jsonArgument).intValue();
            } else if ("float".equals(type) || "java.lang.Float".equals(type)) {
                return ((Number) jsonArgument).floatValue();
            } else if ("android.graphics.Bitmap".equals(type)) {
                byte[] bytes = Base64.decode((String) jsonArgument, 0);
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } else {
                throw new BadInstructionsException("Don't know how to interpret type " + type + " (arg was " + jsonArgument + ")");
            }
        } catch (ClassCastException e) {
            throw new BadInstructionsException("Couldn't interpret <" + jsonArgument + "> as " + type);
        }
    }

    private static final Class[] NO_PARAMS = new Class[0];
} // EditProtocol
