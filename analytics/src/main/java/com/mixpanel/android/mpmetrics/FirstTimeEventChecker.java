package com.mixpanel.android.mpmetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mixpanel.android.util.MPLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

import io.github.jamsesso.jsonlogic.JsonLogic;

/**
 * Utility class for evaluating first-time event matching conditions.
 * Handles property filter evaluation using JsonLogic with case-sensitive matching.
 */
class FirstTimeEventChecker {
    private static final String LOGTAG = "MixpanelAPI.FirstTimeEventChecker";

    /**
     * Evaluates whether event properties match the given JsonLogic property filters.
     * Property names and values are matched case-sensitively.
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

            // Build data object for JsonLogic evaluation
            // NOTE: JsonLogic library expects native Java Map, not org.json.JSONObject
            java.util.Map<String, Object> dataMap = jsonObjectToMap(userProps);

            // Evaluate using JsonLogic
            // NOTE: JsonLogic.apply() expects rules as String and data as Map/Object (not JSONObject!)
            JsonLogic jsonLogic = new JsonLogic();
            String rulesString = filters.toString();
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

}
