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
            // Lowercase event properties (both keys and values)
            JSONObject lowercasedProps = lowercaseKeysAndValues(eventProps);

            // Lowercase only leaf values in filter rules (preserve operators/keys)
            JSONObject lowercasedFilters = lowercaseLeafValues(filters);

            // Build data object for JsonLogic evaluation
            JSONObject data = new JSONObject();
            data.put("properties", lowercasedProps);

            // Evaluate using JsonLogic
            JsonLogic jsonLogic = new JsonLogic();
            Object result = jsonLogic.apply(lowercasedFilters.toString(), data.toString());

            // Convert result to boolean
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            // JsonLogic evaluation failed - treat as non-match (fail safe)
            MPLog.e(LOGTAG, "Property filter evaluation failed: " + e.getMessage());
            return false;
        }
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
