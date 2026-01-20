package com.mixpanel.android.mpmetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mixpanel.android.util.MPLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;

import io.github.jamsesso.jsonlogic.JsonLogic;

/**
 * Utility class for evaluating first-time event matching conditions.
 * Handles property filter evaluation using JsonLogic with case-insensitive matching.
 */
class FirstTimeEventChecker {
    private static final String LOGTAG = "MixpanelAPI.FirstTimeEventChecker";

    /**
     * Transforms string values during JSON traversal.
     */
    interface StringTransformer {
        String transform(String value) throws JSONException;
    }

    /**
     * Transforms object keys during JSON traversal.
     */
    interface KeyTransformer {
        String transform(String key) throws JSONException;
    }

    /**
     * Determines whether to transform values in a given object.
     */
    interface TransformStrategy {
        boolean shouldTransformInObject(JSONObject obj) throws JSONException;
    }

    // Static transformer instances
    private static final StringTransformer LOWERCASE_TRANSFORMER = new StringTransformer() {
        @Override
        public String transform(String value) {
            return value.toLowerCase(Locale.ROOT);
        }
    };

    private static final KeyTransformer LOWERCASE_KEY_TRANSFORMER = new KeyTransformer() {
        @Override
        public String transform(String key) {
            return key.toLowerCase(Locale.ROOT);
        }
    };

    private static final TransformStrategy TRANSFORM_ALL = new TransformStrategy() {
        @Override
        public boolean shouldTransformInObject(JSONObject obj) {
            return true;
        }
    };

    private static final TransformStrategy TRANSFORM_LEAF = new TransformStrategy() {
        @Override
        public boolean shouldTransformInObject(JSONObject obj) throws JSONException {
            return isLeafObject(obj);
        }
    };

    /**
     * Evaluates whether event properties match the given JsonLogic property filters.
     * Both event properties and filter leaf values are lowercased for case-insensitive matching.
     *
     * @param eventProps Event properties to evaluate
     * @param filters    JsonLogic filter rules (can be null)
     * @return true if properties match the filters (or if filters are null), false otherwise
     */
    static boolean propertyFiltersMatch(@NonNull JSONObject eventProps, @Nullable JSONObject filters) {
        // Null or empty filters means no property filtering - always match
        if (filters == null || filters.length() == 0) {
            return true;
        }

        try {
            // Filter out internal SDK properties (time, distinct_id, $*, etc.)
            // Property filters should only match against user-provided properties
            JSONObject userProps = extractUserProperties(eventProps);

            // Lowercase event properties (both keys and values)
            JSONObject lowercasedProps = lowercaseKeysAndValues(userProps);

            // Lowercase only leaf values in filter rules (preserve operators/keys)
            JSONObject lowercasedFilters = lowercaseLeafValues(filters);

            // Build data object for JsonLogic evaluation
            // NOTE: JsonLogic library expects native Java Map, not org.json.JSONObject
            java.util.Map<String, Object> dataMap = new java.util.HashMap<>();
            dataMap.put("properties", jsonObjectToMap(lowercasedProps));

            // Evaluate using JsonLogic
            // NOTE: JsonLogic.apply() expects rules as String and data as Map/Object (not JSONObject!)
            JsonLogic jsonLogic = new JsonLogic();
            String rulesString = lowercasedFilters.toString();
            Object result = jsonLogic.apply(rulesString, dataMap);

            // Convert result to boolean
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            // JsonLogic evaluation failed - treat as non-match (fail safe)
            MPLog.e(LOGTAG, "Property filter evaluation failed: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Extracts user-provided properties from event properties, filtering out internal SDK properties.
     * Internal properties include: time, distinct_id, and any properties starting with $.
     *
     * @param eventProps The full event properties including internal SDK properties
     * @return A new JSONObject containing only user-provided properties
     */
    @NonNull
    private static JSONObject extractUserProperties(@NonNull JSONObject eventProps) throws JSONException {
        JSONObject userProps = new JSONObject();
        Iterator<String> keys = eventProps.keys();

        while (keys.hasNext()) {
            String key = keys.next();

            // Skip internal SDK properties
            if (key.equals("time") ||
                key.equals("distinct_id") ||
                key.startsWith("$")) {
                continue;
            }

            // Include user property
            userProps.put(key, eventProps.get(key));
        }

        return userProps;
    }

    /**
     * Converts a JSONObject to a Java Map for JsonLogic library.
     * The json-logic-java library expects native Java Map objects, not org.json.JSONObject.
     *
     * @param json The JSONObject to convert
     * @return A Map representation of the JSON object
     */
    @NonNull
    private static java.util.Map<String, Object> jsonObjectToMap(@NonNull JSONObject json) throws JSONException {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        Iterator<String> keys = json.keys();

        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.get(key);

            // Recursively convert nested JSON objects and arrays
            if (value instanceof JSONObject) {
                map.put(key, jsonObjectToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                map.put(key, jsonArrayToList((JSONArray) value));
            } else {
                map.put(key, value);
            }
        }

        return map;
    }

    /**
     * Converts a JSONArray to a Java List for JsonLogic library.
     *
     * @param array The JSONArray to convert
     * @return A List representation of the JSON array
     */
    @NonNull
    private static java.util.List<Object> jsonArrayToList(@NonNull JSONArray array) throws JSONException {
        java.util.List<Object> list = new java.util.ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);

            // Recursively convert nested JSON objects and arrays
            if (value instanceof JSONObject) {
                list.add(jsonObjectToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                list.add(jsonArrayToList((JSONArray) value));
            } else {
                list.add(value);
            }
        }

        return list;
    }

    /**
     * Recursively lowercases all string keys and values in a JSONObject or JSONArray.
     * Used for event properties to enable case-insensitive matching.
     *
     * @param obj The object to transform
     * @return A new JSONObject/JSONArray with all strings lowercased
     */
    @NonNull
    static JSONObject lowercaseKeysAndValues(@NonNull JSONObject obj) throws JSONException {
        return transformObject(obj, LOWERCASE_KEY_TRANSFORMER, LOWERCASE_TRANSFORMER, TRANSFORM_ALL);
    }

    /**
     * Recursively lowercases only leaf node string values in a JSONObject or JSONArray.
     * Preserves keys and operators (for JsonLogic rules like "and", ">", etc.).
     * Used for JsonLogic filter rules to enable case-insensitive value matching.
     *
     * @param obj The object to transform
     * @return A new JSONObject/JSONArray with leaf string values lowercased
     */
    @NonNull
    static JSONObject lowercaseLeafValues(@NonNull JSONObject obj) throws JSONException {
        return transformObject(obj, null, LOWERCASE_TRANSFORMER, TRANSFORM_LEAF);
    }

    /**
     * Generic recursive transformer for JSONObject.
     *
     * @param obj The object to transform
     * @param keyTransform How to transform keys (null = keep unchanged)
     * @param stringTransform How to transform string values
     * @param strategy When to apply transformations
     * @return Transformed JSONObject
     */
    @NonNull
    private static JSONObject transformObject(
            @NonNull JSONObject obj,
            @Nullable KeyTransformer keyTransform,
            @NonNull StringTransformer stringTransform,
            @NonNull TransformStrategy strategy) throws JSONException {

        JSONObject result = new JSONObject();
        Iterator<String> keys = obj.keys();

        while (keys.hasNext()) {
            String key = keys.next();
            String transformedKey = (keyTransform != null) ? keyTransform.transform(key) : key;
            Object value = obj.get(key);

            if (value instanceof String) {
                // Check if we should transform strings in this object
                if (strategy.shouldTransformInObject(obj)) {
                    result.put(transformedKey, stringTransform.transform((String) value));
                } else {
                    result.put(transformedKey, value);
                }
            } else if (value instanceof JSONObject) {
                result.put(transformedKey, transformObject(
                        (JSONObject) value, keyTransform, stringTransform, strategy));
            } else if (value instanceof JSONArray) {
                result.put(transformedKey, transformArray(
                        (JSONArray) value, keyTransform, stringTransform, strategy));
            } else {
                // Numbers, booleans, null - keep as-is
                result.put(transformedKey, value);
            }
        }

        return result;
    }

    /**
     * Generic recursive transformer for JSONArray.
     *
     * @param arr The array to transform
     * @param keyTransform How to transform keys (null = keep unchanged)
     * @param stringTransform How to transform string values
     * @param strategy When to apply transformations
     * @return Transformed JSONArray
     */
    @NonNull
    private static JSONArray transformArray(
            @NonNull JSONArray arr,
            @Nullable KeyTransformer keyTransform,
            @NonNull StringTransformer stringTransform,
            @NonNull TransformStrategy strategy) throws JSONException {

        JSONArray result = new JSONArray();

        for (int i = 0; i < arr.length(); i++) {
            Object value = arr.get(i);

            if (value instanceof String) {
                result.put(stringTransform.transform((String) value));
            } else if (value instanceof JSONObject) {
                result.put(transformObject(
                        (JSONObject) value, keyTransform, stringTransform, strategy));
            } else if (value instanceof JSONArray) {
                result.put(transformArray(
                        (JSONArray) value, keyTransform, stringTransform, strategy));
            } else {
                result.put(value);
            }
        }

        return result;
    }

    /**
     * Checks if a JSONObject is a leaf object (contains no nested objects or arrays).
     */
    private static boolean isLeafObject(@NonNull JSONObject obj) throws JSONException {
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            Object value = obj.get(keys.next());
            if (value instanceof JSONObject || value instanceof JSONArray) {
                return false;
            }
        }
        return true;
    }
}
