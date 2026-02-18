package com.mixpanel.android.util;

import com.mixpanel.android.mpmetrics.MixpanelFlagVariant;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class JsonUtilsTest {

    // parseJsonValue tests

    @Test
    public void testParseJsonValueNull() throws JSONException {
        assertNull(JsonUtils.parseJsonValue(null));
    }

    @Test
    public void testParseJsonValueJSONNull() throws JSONException {
        assertNull(JsonUtils.parseJsonValue(JSONObject.NULL));
    }

    @Test
    public void testParseJsonValueString() throws JSONException {
        assertEquals("hello", JsonUtils.parseJsonValue("hello"));
    }

    @Test
    public void testParseJsonValueBoolean() throws JSONException {
        assertEquals(true, JsonUtils.parseJsonValue(true));
        assertEquals(false, JsonUtils.parseJsonValue(false));
    }

    @Test
    public void testParseJsonValueInteger() throws JSONException {
        assertEquals(42, JsonUtils.parseJsonValue(42));
    }

    @Test
    public void testParseJsonValueLong() throws JSONException {
        assertEquals(123456789L, JsonUtils.parseJsonValue(123456789L));
    }

    @Test
    public void testParseJsonValueDouble() throws JSONException {
        assertEquals(3.14, JsonUtils.parseJsonValue(3.14));
    }

    @Test
    public void testParseJsonValueFloat() throws JSONException {
        assertEquals(2.5f, JsonUtils.parseJsonValue(2.5f));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testParseJsonValueJSONObject() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("key", "value");
        obj.put("num", 42);

        Object result = JsonUtils.parseJsonValue(obj);
        assertTrue(result instanceof Map);
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals("value", map.get("key"));
        assertEquals(42, map.get("num"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testParseJsonValueJSONArray() throws JSONException {
        JSONArray arr = new JSONArray();
        arr.put("a");
        arr.put(1);
        arr.put(true);

        Object result = JsonUtils.parseJsonValue(arr);
        assertTrue(result instanceof List);
        List<Object> list = (List<Object>) result;
        assertEquals(3, list.size());
        assertEquals("a", list.get(0));
        assertEquals(1, list.get(1));
        assertEquals(true, list.get(2));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testParseJsonValueNestedObjects() throws JSONException {
        JSONObject inner = new JSONObject();
        inner.put("nested", "value");

        JSONObject outer = new JSONObject();
        outer.put("child", inner);

        Object result = JsonUtils.parseJsonValue(outer);
        assertTrue(result instanceof Map);
        Map<String, Object> outerMap = (Map<String, Object>) result;
        assertTrue(outerMap.get("child") instanceof Map);
        Map<String, Object> innerMap = (Map<String, Object>) outerMap.get("child");
        assertEquals("value", innerMap.get("nested"));
    }

    @Test(expected = JSONException.class)
    public void testParseJsonValueUnsupportedType() throws JSONException {
        JsonUtils.parseJsonValue(new Object());
    }

    // parseFlagsResponse tests

    @Test
    public void testParseFlagsResponseNull() {
        Map<String, MixpanelFlagVariant> result = JsonUtils.parseFlagsResponse(null);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseFlagsResponseEmptyObject() throws JSONException {
        JSONObject response = new JSONObject();
        Map<String, MixpanelFlagVariant> result = JsonUtils.parseFlagsResponse(response);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseFlagsResponseWithNullFlagsKey() throws JSONException {
        JSONObject response = new JSONObject();
        response.put("flags", JSONObject.NULL);
        Map<String, MixpanelFlagVariant> result = JsonUtils.parseFlagsResponse(response);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseFlagsResponseSingleFlag() throws JSONException {
        JSONObject flagDef = new JSONObject();
        flagDef.put("variant_key", "control");
        flagDef.put("variant_value", "blue");

        JSONObject flags = new JSONObject();
        flags.put("button_color", flagDef);

        JSONObject response = new JSONObject();
        response.put("flags", flags);

        Map<String, MixpanelFlagVariant> result = JsonUtils.parseFlagsResponse(response);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("button_color"));
        assertEquals("control", result.get("button_color").key);
        assertEquals("blue", result.get("button_color").value);
    }

    @Test
    public void testParseFlagsResponseWithBooleanValue() throws JSONException {
        JSONObject flagDef = new JSONObject();
        flagDef.put("variant_key", "enabled");
        flagDef.put("variant_value", true);

        JSONObject flags = new JSONObject();
        flags.put("feature_toggle", flagDef);

        JSONObject response = new JSONObject();
        response.put("flags", flags);

        Map<String, MixpanelFlagVariant> result = JsonUtils.parseFlagsResponse(response);
        assertEquals(1, result.size());
        assertEquals(true, result.get("feature_toggle").value);
    }

    @Test
    public void testParseFlagsResponseWithNullVariantValue() throws JSONException {
        JSONObject flagDef = new JSONObject();
        flagDef.put("variant_key", "control");
        flagDef.put("variant_value", JSONObject.NULL);

        JSONObject flags = new JSONObject();
        flags.put("test_flag", flagDef);

        JSONObject response = new JSONObject();
        response.put("flags", flags);

        Map<String, MixpanelFlagVariant> result = JsonUtils.parseFlagsResponse(response);
        assertEquals(1, result.size());
        assertNull(result.get("test_flag").value);
    }

    @Test
    public void testParseFlagsResponseMissingVariantKey() throws JSONException {
        JSONObject flagDef = new JSONObject();
        flagDef.put("variant_value", "blue");
        // missing variant_key

        JSONObject flags = new JSONObject();
        flags.put("incomplete_flag", flagDef);

        JSONObject response = new JSONObject();
        response.put("flags", flags);

        Map<String, MixpanelFlagVariant> result = JsonUtils.parseFlagsResponse(response);
        assertTrue(result.isEmpty()); // flag should be skipped
    }

    @Test
    public void testParseFlagsResponseMultipleFlags() throws JSONException {
        JSONObject flag1 = new JSONObject();
        flag1.put("variant_key", "a");
        flag1.put("variant_value", 1);

        JSONObject flag2 = new JSONObject();
        flag2.put("variant_key", "b");
        flag2.put("variant_value", "two");

        JSONObject flags = new JSONObject();
        flags.put("flag_1", flag1);
        flags.put("flag_2", flag2);

        JSONObject response = new JSONObject();
        response.put("flags", flags);

        Map<String, MixpanelFlagVariant> result = JsonUtils.parseFlagsResponse(response);
        assertEquals(2, result.size());
        assertEquals("a", result.get("flag_1").key);
        assertEquals(1, result.get("flag_1").value);
        assertEquals("b", result.get("flag_2").key);
        assertEquals("two", result.get("flag_2").value);
    }

    @Test
    public void testParseFlagsResponseWithExperimentFields() throws JSONException {
        JSONObject flagDef = new JSONObject();
        flagDef.put("variant_key", "variant_a");
        flagDef.put("variant_value", "enabled");
        flagDef.put("experiment_id", "exp_123");
        flagDef.put("is_experiment_active", true);
        flagDef.put("is_qa_tester", false);

        JSONObject flags = new JSONObject();
        flags.put("my_flag", flagDef);

        JSONObject response = new JSONObject();
        response.put("flags", flags);

        Map<String, MixpanelFlagVariant> result = JsonUtils.parseFlagsResponse(response);
        MixpanelFlagVariant variant = result.get("my_flag");
        assertNotNull(variant);
        assertEquals("variant_a", variant.key);
        assertEquals("enabled", variant.value);
        assertEquals("exp_123", variant.experimentID);
        assertEquals(true, variant.isExperimentActive);
        assertEquals(false, variant.isQATester);
    }

    @Test
    public void testParseFlagsResponseNullFlagDefinition() throws JSONException {
        JSONObject flags = new JSONObject();
        flags.put("null_flag", JSONObject.NULL);

        JSONObject response = new JSONObject();
        response.put("flags", flags);

        Map<String, MixpanelFlagVariant> result = JsonUtils.parseFlagsResponse(response);
        assertTrue(result.isEmpty()); // null flag should be skipped
    }

    @Test
    public void testParseFlagsResponseMissingVariantValue() throws JSONException {
        JSONObject flagDef = new JSONObject();
        flagDef.put("variant_key", "control");
        // no variant_value key at all

        JSONObject flags = new JSONObject();
        flags.put("no_value_flag", flagDef);

        JSONObject response = new JSONObject();
        response.put("flags", flags);

        Map<String, MixpanelFlagVariant> result = JsonUtils.parseFlagsResponse(response);
        assertEquals(1, result.size());
        assertNull(result.get("no_value_flag").value); // should default to null
    }
}
