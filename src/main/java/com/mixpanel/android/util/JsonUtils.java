package com.mixpanel.android.util; // Or com.mixpanel.android.mpmetrics if preferred

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mixpanel.android.mpmetrics.MixpanelFlagVariant;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Utility class for JSON operations, particularly for handling arbitrary value types
 * encountered in feature flags.
 */
public class JsonUtils {

    private static final String LOGTAG = "MixpanelAPI.JsonUtils"; // Re-use Mixpanel log tag convention

    /**
     * Parses a JSON value obtained from org.json (like JSONObject.get() or JSONArray.get())
     * into a standard Java Object (String, Boolean, Number, {@code List<Object>}, {@code Map<String, Object>}, or null).
     * Handles JSONObject.NULL correctly.
     *
     * @param jsonValue The object retrieved from org.json library.
     * @return The corresponding standard Java object, or null if the input was JSONObject.NULL.
     * @throws JSONException if the input is an unsupported type or if nested parsing fails.
     */
    @Nullable
    public static Object parseJsonValue(@Nullable Object jsonValue) throws JSONException {
        if (jsonValue == null || jsonValue == JSONObject.NULL) {
            return null;
        }

        if (jsonValue instanceof Boolean ||
                jsonValue instanceof String ||
                jsonValue instanceof Integer ||
                jsonValue instanceof Long ||
                jsonValue instanceof Double ||
                jsonValue instanceof Float) {
            // Primitives (including Numbers) are returned directly
            return jsonValue;
        }
        // Handle numbers that might not be boxed primitives? (Shouldn't happen with org.json?)
        if (jsonValue instanceof Number) {
            return jsonValue;
        }


        if (jsonValue instanceof JSONObject) {
            return jsonObjectToMap((JSONObject) jsonValue);
        }

        if (jsonValue instanceof JSONArray) {
            return jsonArrayToList((JSONArray) jsonValue);
        }

        // If we got here, the type is unexpected
        MPLog.w(LOGTAG, "Could not parse JSON value of type: " + jsonValue.getClass().getSimpleName());
        throw new JSONException("Unsupported JSON type encountered: " + jsonValue.getClass().getSimpleName());
    }

    /**
     * Converts a JSONObject to a Map<String, Object>, recursively parsing nested values.
     *
     * @param jsonObject The JSONObject to convert.
     * @return A Map representing the JSONObject.
     * @throws JSONException if parsing fails.
     */
    @NonNull
    private static Map<String, Object> jsonObjectToMap(@NonNull JSONObject jsonObject) throws JSONException {
        Map<String, Object> map = new HashMap<>();
        Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = jsonObject.get(key);
            map.put(key, parseJsonValue(value)); // Recursively parse nested values
        }
        return map;
    }

    /**
     * Converts a JSONArray to a List<Object>, recursively parsing nested values.
     *
     * @param jsonArray The JSONArray to convert.
     * @return A List representing the JSONArray.
     * @throws JSONException if parsing fails.
     */
    @NonNull
    private static List<Object> jsonArrayToList(@NonNull JSONArray jsonArray) throws JSONException {
        List<Object> list = new ArrayList<>(jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            Object value = jsonArray.get(i);
            list.add(parseJsonValue(value)); // Recursively parse nested values
        }
        return list;
    }


    /**
     * Parses the "flags" object from a /flags API response JSONObject.
     *
     * @param responseJson The root JSONObject from the API response.
     * @return A Map where keys are feature flag names (String) and values are FeatureFlagData objects.
     * Returns an empty map if parsing fails or the "flags" key is missing/invalid.
     */
    @NonNull
    public static Map<String, MixpanelFlagVariant> parseFlagsResponse(@Nullable JSONObject responseJson) {
        Map<String, MixpanelFlagVariant> flagsMap = new HashMap<>();
        if (responseJson == null) {
            MPLog.e(LOGTAG, "Cannot parse null flags response");
            return flagsMap;
        }

        JSONObject flagsObject = null;
        try {
            if (responseJson.has(MPConstants.Flags.FLAGS_KEY) && !responseJson.isNull(MPConstants.Flags.FLAGS_KEY)) {
                flagsObject = responseJson.getJSONObject(MPConstants.Flags.FLAGS_KEY);
            } else {
                MPLog.w(LOGTAG, "Flags response JSON does not contain 'flags' key or it's null.");
                return flagsMap; // No flags found
            }

            Iterator<String> keys = flagsObject.keys();
            while (keys.hasNext()) {
                String featureName = keys.next();
                try {
                    if (flagsObject.isNull(featureName)) {
                        MPLog.w(LOGTAG, "Flag definition is null for key: " + featureName);
                        continue; // Skip null flag definitions
                    }
                    JSONObject flagDefinition = flagsObject.getJSONObject(featureName);

                    String variantKey = null;
                    if (flagDefinition.has(MPConstants.Flags.VARIANT_KEY) && !flagDefinition.isNull(MPConstants.Flags.VARIANT_KEY)) {
                        variantKey = flagDefinition.getString(MPConstants.Flags.VARIANT_KEY);
                    } else {
                        MPLog.w(LOGTAG, "Flag definition missing 'variant_key' for key: " + featureName);
                        continue; // Skip flags without a variant key
                    }

                    Object variantValue = null;
                    if (flagDefinition.has(MPConstants.Flags.VARIANT_VALUE)) { // Check presence before getting
                        Object rawValue = flagDefinition.get(MPConstants.Flags.VARIANT_VALUE); // Get raw value (could be JSONObject.NULL)
                        variantValue = parseJsonValue(rawValue); // Parse it properly
                    } else {
                        MPLog.w(LOGTAG, "Flag definition missing 'variant_value' for key: " + featureName + ". Assuming null value.");
                    }

                    MixpanelFlagVariant flagData = new MixpanelFlagVariant(variantKey, variantValue);
                    flagsMap.put(featureName, flagData);

                } catch (JSONException e) {
                    MPLog.e(LOGTAG, "Error parsing individual flag definition for key: " + featureName, e);
                    // Continue parsing other flags
                }
            }
        } catch (JSONException e) {
            MPLog.e(LOGTAG, "Error parsing outer 'flags' object in response", e);
        }

        return flagsMap;
    }
}