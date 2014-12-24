package com.mixpanel.android.viewcrawler;

import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.mixpanel.android.mpmetrics.ResourceIds;
import com.mixpanel.android.util.JSONUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/* package */ class EditProtocol {

    public static class BadInstructionsException extends Exception {
		private static final long serialVersionUID = -4062004792184145311L;

		public BadInstructionsException(String message) {
            super(message);
        }

        public BadInstructionsException(String message, Exception e) {
            super(message, e);
        }
    }

    public static class InapplicableInstructionsException extends BadInstructionsException {
		private static final long serialVersionUID = 3977056710817909104L;

		public InapplicableInstructionsException(String message) {
            super(message);
        }
    }

    public EditProtocol(ResourceIds resourceIds) {
        mResourceIds = resourceIds;
    }

    public ViewVisitor readEventBinding(JSONObject source, ViewVisitor.OnEventListener listener) throws BadInstructionsException {
        try {
            final String eventName = source.getString("event_name");
            final String eventType = source.getString("event_type");

            final JSONArray pathDesc = source.getJSONArray("path");
            final List<Pathfinder.PathElement> path = readPath(pathDesc, mResourceIds);

            if (path.size() == 0) {
                throw new InapplicableInstructionsException("event '" + eventName + "' will not be bound to any element in the UI.");
            }

            if ("click".equals(eventType)) {
                return new ViewVisitor.AddAccessibilityEventVisitor(
                    path,
                    AccessibilityEvent.TYPE_VIEW_CLICKED,
                    eventName,
                    listener
                );
            } else if ("selected".equals(eventType)) {
                return new ViewVisitor.AddAccessibilityEventVisitor(
                    path,
                    AccessibilityEvent.TYPE_VIEW_SELECTED,
                    eventName,
                    listener
                );
            } else if ("text_changed".equals(eventType)) {
                return new ViewVisitor.AddTextChangeListener(path, eventName, listener);
            } else if ("detected".equals(eventType)) {
                return new ViewVisitor.ViewDetectorVisitor(path, eventName, listener);
            } else {
                throw new BadInstructionsException("Mixpanel can't track event type \"" + eventType + "\"");
            }
        } catch (final JSONException e) {
            throw new BadInstructionsException("Can't interpret instructions due to JSONException", e);
        }
    }

    public ViewVisitor readEdit(JSONObject source) throws BadInstructionsException {
        try {
            final JSONArray pathDesc = source.getJSONArray("path");
            final List<Pathfinder.PathElement> path = readPath(pathDesc, mResourceIds);

            if (path.size() == 0) {
                throw new InapplicableInstructionsException("Edit will not be bound to any element in the UI.");
            }

            final Pathfinder.PathElement pathEnd = path.get(path.size() - 1);
            final String targetClassName = pathEnd.viewClassName;
            final Class<?> targetClass;
            try {
                targetClass = Class.forName(targetClassName);
            } catch (final ClassNotFoundException e) {
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
        } catch (final NoSuchMethodException e) {
            throw new BadInstructionsException("Can't create property mutator", e);
        } catch (final JSONException e) {
            throw new BadInstructionsException("Can't interpret instructions due to JSONException", e);
        }
    }

    public ViewSnapshot readSnapshotConfig(JSONObject source) throws BadInstructionsException {
        final List<PropertyDescription> properties = new ArrayList<PropertyDescription>();

        try {
            final JSONObject config = source.getJSONObject("config");
            final JSONArray classes = config.getJSONArray("classes");
            for (int classIx = 0; classIx < classes.length(); classIx++) {
                final JSONObject classDesc = classes.getJSONObject(classIx);
                final String targetClassName = classDesc.getString("name");
                final Class<?> targetClass = Class.forName(targetClassName);

                final JSONArray propertyDescs = classDesc.getJSONArray("properties");
                for (int i = 0; i < propertyDescs.length(); i++) {
                    final JSONObject propertyDesc = propertyDescs.getJSONObject(i);
                    final PropertyDescription desc = readPropertyDescription(targetClass, propertyDesc);
                    properties.add(desc);
                }
            }

            return new ViewSnapshot(properties, mResourceIds);
        } catch (JSONException e) {
            throw new BadInstructionsException("Can't read snapshot configuration", e);
        } catch (final ClassNotFoundException e) {
            throw new BadInstructionsException("Can't resolve types for snapshot configuration", e);
        }
    }

    // Package access FOR TESTING ONLY
    /* package */ List<Pathfinder.PathElement> readPath(JSONArray pathDesc, ResourceIds idNameToId) throws JSONException {
        final List<Pathfinder.PathElement> path = new ArrayList<Pathfinder.PathElement>();

        for (int i = 0; i < pathDesc.length(); i++) {
            final JSONObject targetView = pathDesc.getJSONObject(i);

            final String prefixCode = JSONUtils.optionalStringKey(targetView, "prefix");
            final String targetViewClass = JSONUtils.optionalStringKey(targetView, "view_class");
            final int targetIndex = targetView.optInt("index", -1);
            final String targetDescription = JSONUtils.optionalStringKey(targetView, "contentDescription");
            final int targetExplicitId = targetView.optInt("id", -1);
            final String targetIdName = JSONUtils.optionalStringKey(targetView, "mp_id_name");
            final String targetTag = JSONUtils.optionalStringKey(targetView, "tag");

            final int prefix;
            if ("shortest".equals(prefixCode)) {
                prefix = Pathfinder.PathElement.SHORTEST_PREFIX;
            } else if (null == prefixCode) {
                prefix = Pathfinder.PathElement.ZERO_LENGTH_PREFIX;
            } else {
                Log.w(LOGTAG, "Unrecognized prefix type \"" + prefixCode + "\". No views will be matched");
                return NEVER_MATCH_PATH;
            }

            final int targetId;

            final Integer targetIdOrNull = reconcileIdsInPath(targetExplicitId, targetIdName, idNameToId);
            if (null == targetIdOrNull) {
                return NEVER_MATCH_PATH;
            } else {
                targetId = targetIdOrNull.intValue();
            }

            path.add(new Pathfinder.PathElement(prefix, targetViewClass, targetIndex, targetId, targetDescription, targetTag));
        }

        return path;
    }

    // May return null (and log a warning) if arguments cannot be reconciled
    private Integer reconcileIdsInPath(int explicitId, String idName, ResourceIds idNameToId) {
        final int idFromName;
        if (null != idName) {
            if (idNameToId.knownIdName(idName)) {
                idFromName = idNameToId.idFromName(idName);
            } else {
                Log.w(LOGTAG,
                        "Path element contains an id name not known to the system. No views will be matched.\n" +
                                "Make sure that you're not stripping your packages R class out with proguard.\n" +
                                "id name was \"" + idName + "\""
                );
                return null;
            }
        } else {
            idFromName = -1;
        }

        if (-1 != idFromName && -1 != explicitId && idFromName != explicitId) {
            Log.e(LOGTAG, "Path contains both a named and an explicit id, and they don't match. No views will be matched.");
            return null;
        }

        if (-1 != idFromName) {
            return idFromName;
        }

        return explicitId;
    }

    private PropertyDescription readPropertyDescription(Class<?> targetClass, JSONObject propertyDesc) throws BadInstructionsException {
        try {
            final String propName = propertyDesc.getString("name");

            Caller accessor = null;
            if (propertyDesc.has("get")) {
                final JSONObject accessorConfig = propertyDesc.getJSONObject("get");
                final String accessorName = accessorConfig.getString("selector");
                final String accessorResultTypeName = accessorConfig.getJSONObject("result").getString("type");
                final Class<?> accessorResultType = Class.forName(accessorResultTypeName);
                accessor = new Caller(targetClass, accessorName, NO_PARAMS, accessorResultType);
            }

            final String mutatorName;
            if (propertyDesc.has("set")) {
                final JSONObject mutatorConfig = propertyDesc.getJSONObject("set");
                mutatorName = mutatorConfig.getString("selector");
            } else {
                mutatorName = null;
            }

            return new PropertyDescription(propName, targetClass, accessor, mutatorName);
        } catch (final NoSuchMethodException e) {
            throw new BadInstructionsException("Can't create property reader", e);
        } catch (final JSONException e) {
            throw new BadInstructionsException("Can't read property JSON", e);
        } catch (final ClassNotFoundException e) {
            throw new BadInstructionsException("Can't read property JSON, relevant arg/return class not found", e);
        }
    }

    private Object convertArgument(Object jsonArgument, String type) throws BadInstructionsException {
        // Object is a Boolean, JSONArray, JSONObject, Number, String, or JSONObject.NULL
        try {
            if ("java.lang.CharSequence".equals(type)) { // Because we're assignable
                return jsonArgument;
            } else if ("boolean".equals(type) || "java.lang.Boolean".equals(type)) {
                return jsonArgument;
            } else if ("int".equals(type) || "java.lang.Integer".equals(type)) {
                return ((Number) jsonArgument).intValue();
            } else if ("float".equals(type) || "java.lang.Float".equals(type)) {
                return ((Number) jsonArgument).floatValue();
            } else if ("android.graphics.Bitmap".equals(type)) {
                final byte[] bytes = Base64.decode((String) jsonArgument, 0);
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } else {
                throw new BadInstructionsException("Don't know how to interpret type " + type + " (arg was " + jsonArgument + ")");
            }
        } catch (final ClassCastException e) {
            throw new BadInstructionsException("Couldn't interpret <" + jsonArgument + "> as " + type);
        }
    }

    private final ResourceIds mResourceIds;

    private static final Class<?>[] NO_PARAMS = new Class[0];
    private static final List<Pathfinder.PathElement> NEVER_MATCH_PATH = Collections.<Pathfinder.PathElement>emptyList();

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.EditProtocol";
} // EditProtocol
