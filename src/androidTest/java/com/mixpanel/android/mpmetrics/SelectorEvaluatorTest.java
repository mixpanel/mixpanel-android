package com.mixpanel.android.mpmetrics;

import android.test.AndroidTestCase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class SelectorEvaluatorTest extends AndroidTestCase  {
    public void testGetType() {
        assertEquals(SelectorEvaluator.PropertyType.Null, SelectorEvaluator.getType(null));
        assertEquals(SelectorEvaluator.PropertyType.Null, SelectorEvaluator.getType(JSONObject.NULL));
        assertEquals(SelectorEvaluator.PropertyType.String, SelectorEvaluator.getType("test"));
        assertEquals(SelectorEvaluator.PropertyType.Array, SelectorEvaluator.getType(new JSONArray()));
        assertEquals(SelectorEvaluator.PropertyType.Object, SelectorEvaluator.getType(new JSONObject()));
        assertEquals(SelectorEvaluator.PropertyType.Number, SelectorEvaluator.getType(new Double(0)));
        assertEquals(SelectorEvaluator.PropertyType.Number, SelectorEvaluator.getType(new Integer(0)));
        assertEquals(SelectorEvaluator.PropertyType.Number, SelectorEvaluator.getType(new Number() {
            @Override
            public int intValue() {
                return 0;
            }

            @Override
            public long longValue() {
                return 0;
            }

            @Override
            public float floatValue() {
                return 0;
            }

            @Override
            public double doubleValue() {
                return 0;
            }
        }));
        assertEquals(SelectorEvaluator.PropertyType.Boolean, SelectorEvaluator.getType(new Boolean(true)));
        assertEquals(SelectorEvaluator.PropertyType.Datetime, SelectorEvaluator.getType(new Date()));
        assertEquals(SelectorEvaluator.PropertyType.Unknown, SelectorEvaluator.getType(new Object()));
    }

    public void testToNumber() {
        assertNull(SelectorEvaluator.toNumber(null));
        assertNull(SelectorEvaluator.toNumber(JSONObject.NULL));
        assertNull(SelectorEvaluator.toNumber(new Date(0)));
        assertNull(SelectorEvaluator.toNumber(new Object()));
        assertEquals(1.0, SelectorEvaluator.toNumber(new Date(1)));
        assertEquals(1.0, SelectorEvaluator.toNumber(new Boolean(true)));
        assertEquals(0.0, SelectorEvaluator.toNumber(new Boolean(false)));
        assertEquals(1.1, SelectorEvaluator.toNumber(new Double(1.1)));
        assertEquals(new Double(1), SelectorEvaluator.toNumber(new Integer(1)));
        assertEquals(100.0, SelectorEvaluator.toNumber(new Number() {
            @Override
            public int intValue() {
                return -1;
            }

            @Override
            public long longValue() {
                return -1;
            }

            @Override
            public float floatValue() {
                return 10.0f;
            }

            @Override
            public double doubleValue() {
                return 100.0;
            }
        }));
        assertEquals(1.0, SelectorEvaluator.toNumber("1.0"));
        assertEquals(0.0, SelectorEvaluator.toNumber("abc"));
    }

    public void testToBoolean() throws JSONException {
        assertFalse(SelectorEvaluator.toBoolean(null));
        assertFalse(SelectorEvaluator.toBoolean(JSONObject.NULL));
        assertFalse(SelectorEvaluator.toBoolean(false));
        assertTrue(SelectorEvaluator.toBoolean(true));
        assertFalse(SelectorEvaluator.toBoolean(0.0));
        assertTrue(SelectorEvaluator.toBoolean(-1.0));
        assertFalse(SelectorEvaluator.toBoolean(""));
        assertTrue(SelectorEvaluator.toBoolean("0.0"));
        assertFalse(SelectorEvaluator.toBoolean(new JSONArray()));
        assertTrue(SelectorEvaluator.toBoolean(new JSONArray("[1,2,3]")));
        assertFalse(SelectorEvaluator.toBoolean(new Date(0)));
        assertTrue(SelectorEvaluator.toBoolean(new Date(1)));
        assertFalse(SelectorEvaluator.toBoolean(new JSONObject("{}")));
        assertTrue(SelectorEvaluator.toBoolean(new JSONObject("{\"prop\": 1.0}")));
        assertFalse(SelectorEvaluator.toBoolean(new Object()));
    }

    public void testEvaluateNumber() throws JSONException, ParseException {
        // Error conditions
        try {
            SelectorEvaluator.evaluateNumber(new JSONObject("{}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: number");
        }
        try {
            SelectorEvaluator.evaluateNumber(new JSONObject("{\"operator\": \"defined\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: number");
        }
        try {
            SelectorEvaluator.evaluateNumber(new JSONObject("{\"operator\": \"number\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: number");
        }
        try {
            SelectorEvaluator.evaluateNumber(new JSONObject("{\"operator\": \"number\", \"children\": []}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: number");
        }
        try {
            SelectorEvaluator.evaluateNumber(new JSONObject("{\"operator\": \"number\", \"children\": [{}, {}]}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: number");
        }

        // missing property
        JSONObject node = new JSONObject("{\"operator\": \"number\", \"children\": [{\"property\": \"event\", \"value\": \"prop\"}]}");
        assertNull(SelectorEvaluator.evaluateNumber(node, new JSONObject("{}")));

        // Datetime
        JSONObject withDateTimeCast = new JSONObject("{\"operator\": \"number\", \"children\": [" +
                "{\"operator\": \"datetime\", \"children\": [{\"property\": \"event\", \"value\": \"prop\"}]}" +
                "]}");
        final Date dt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse("2019-01-01T00:00:01");
        assertEquals(new Double(dt.getTime()), SelectorEvaluator.evaluateNumber(withDateTimeCast, new JSONObject("{\"prop\": \"2019-01-01T00:00:01\"}")));

        // Object
        assertNull(SelectorEvaluator.evaluateNumber(node, new JSONObject("{\"prop\": {}}")));

        // Boolean
        assertEquals(1.0, SelectorEvaluator.evaluateNumber(node, new JSONObject("{\"prop\": true}")));
        assertEquals(0.0, SelectorEvaluator.evaluateNumber(node, new JSONObject("{\"prop\": false}")));

        // Number
        assertEquals(59.0, SelectorEvaluator.evaluateNumber(node, new JSONObject("{\"prop\": 59}")));

        // String
        assertEquals(59.0, SelectorEvaluator.evaluateNumber(node, new JSONObject("{\"prop\": \"59\"}")));
        assertEquals(0.0, SelectorEvaluator.evaluateNumber(node, new JSONObject("{\"prop\": \"nan\"}")));
    }

    public void testEvaluateBoolean() throws JSONException {
        // Error conditions
        try {
            SelectorEvaluator.evaluateBoolean(new JSONObject("{}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: boolean");
        }
        try {
            SelectorEvaluator.evaluateBoolean(new JSONObject("{\"operator\": \"defined\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: boolean");
        }
        try {
            SelectorEvaluator.evaluateBoolean(new JSONObject("{\"operator\": \"boolean\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: boolean");
        }
        try {
            SelectorEvaluator.evaluateBoolean(new JSONObject("{\"operator\": \"boolean\", \"children\": []}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: boolean");
        }
        try {
            SelectorEvaluator.evaluateBoolean(new JSONObject("{\"operator\": \"boolean\", \"children\": [{}, {}]}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: boolean");
        }

        // missing property
        JSONObject node = new JSONObject("{\"operator\": \"boolean\", \"children\": [{\"property\": \"event\", \"value\": \"prop\"}]}");
        assertFalse(SelectorEvaluator.evaluateBoolean(node, new JSONObject("{}")));

        // Boolean
        assertTrue(SelectorEvaluator.evaluateBoolean(node, new JSONObject("{\"prop\": true}")));
        assertFalse(SelectorEvaluator.evaluateBoolean(node, new JSONObject("{\"prop\": false}")));

        // Number
        assertTrue(SelectorEvaluator.evaluateBoolean(node, new JSONObject("{\"prop\": 59}")));
        assertFalse(SelectorEvaluator.evaluateBoolean(node, new JSONObject("{\"prop\": 0}")));

        // String
        assertTrue(SelectorEvaluator.evaluateBoolean(node, new JSONObject("{\"prop\": \"abc\"}")));
        assertFalse(SelectorEvaluator.evaluateBoolean(node, new JSONObject("{\"prop\": \"\"}")));

        // Array
        assertTrue(SelectorEvaluator.evaluateBoolean(node, new JSONObject("{\"prop\": [1,2,3]}")));
        assertFalse(SelectorEvaluator.evaluateBoolean(node, new JSONObject("{\"prop\": []}")));

        // Dictionary
        assertTrue(SelectorEvaluator.evaluateBoolean(node, new JSONObject("{\"prop\": {\"1\": 1}}")));
        assertFalse(SelectorEvaluator.evaluateBoolean(node, new JSONObject("{\"prop\": {}}")));

        // Datetime
        JSONObject withDateTimeCast = new JSONObject("{\"operator\": \"boolean\", \"children\": [" +
                "{\"operator\": \"datetime\", \"children\": [{\"property\": \"event\", \"value\": \"prop\"}]}" +
                "]}");
        final String zeroDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date(0));
        final String nonZeroDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date(100000));
        assertTrue(SelectorEvaluator.evaluateBoolean(withDateTimeCast, new JSONObject("{\"prop\": \"" + nonZeroDate + "\"}")));
        assertFalse(SelectorEvaluator.evaluateBoolean(withDateTimeCast, new JSONObject("{\"prop\": \"" + zeroDate + "\"}")));
    }

    public void testEvaluateDatetime() throws JSONException {
        // Error conditions
        try {
            SelectorEvaluator.evaluateDateTime(new JSONObject("{}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: datetime");
        }
        try {
            SelectorEvaluator.evaluateDateTime(new JSONObject("{\"operator\": \"defined\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: datetime");
        }
        try {
            SelectorEvaluator.evaluateDateTime(new JSONObject("{\"operator\": \"datetime\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: datetime");
        }
        try {
            SelectorEvaluator.evaluateDateTime(new JSONObject("{\"operator\": \"datetime\", \"children\": []}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: datetime");
        }
        try {
            SelectorEvaluator.evaluateDateTime(new JSONObject("{\"operator\": \"datetime\", \"children\": [{}, {}]}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: datetime");
        }

        // missing property
        JSONObject node = new JSONObject("{\"operator\": \"datetime\", \"children\": [{\"property\": \"event\", \"value\": \"prop\"}]}");
        assertNull(SelectorEvaluator.evaluateDateTime(node, new JSONObject("{}")));

        // unsupported types
        assertNull(SelectorEvaluator.evaluateDateTime(node, new JSONObject("{\"prop\": true}")));

        // Number
        assertEquals(new Date(10), SelectorEvaluator.evaluateDateTime(node, new JSONObject("{\"prop\": 10}")));

        // String
        final String dtStr = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date(0));
        assertEquals(new Date(0), SelectorEvaluator.evaluateDateTime(node, new JSONObject("{\"prop\": \"" + dtStr + "\"}")));
        assertNull(SelectorEvaluator.evaluateDateTime(node, new JSONObject("{\"prop\": \"invalid date\"}")));

        // Datetime
        JSONObject withDateTimeCast = new JSONObject("{\"operator\": \"datetime\", \"children\": [" +
                "{\"operator\": \"datetime\", \"children\": [{\"property\": \"event\", \"value\": \"prop\"}]}" +
                "]}");
        final String nonZeroDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date(100000));
        assertEquals(new Date(100000), SelectorEvaluator.evaluateDateTime(withDateTimeCast, new JSONObject("{\"prop\": \"" + nonZeroDate + "\"}")));
    }

    public void testEvaluateList() throws JSONException {
        // Error conditions
        try {
            SelectorEvaluator.evaluateList(new JSONObject("{}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: list");
        }
        try {
            SelectorEvaluator.evaluateList(new JSONObject("{\"operator\": \"defined\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: list");
        }
        try {
            SelectorEvaluator.evaluateList(new JSONObject("{\"operator\": \"list\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: list");
        }
        try {
            SelectorEvaluator.evaluateList(new JSONObject("{\"operator\": \"list\", \"children\": []}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: list");
        }
        try {
            SelectorEvaluator.evaluateList(new JSONObject("{\"operator\": \"list\", \"children\": [{}, {}]}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: list");
        }

        JSONObject node = new JSONObject("{\"operator\": \"list\", \"children\": [{\"property\": \"event\", \"value\": \"prop\"}]}");

        // Array
        assertEquals(new JSONArray("[1,2,3]"), SelectorEvaluator.evaluateList(node, new JSONObject("{\"prop\": [1,2,3]}")));
        assertEquals(new JSONArray("[]"), SelectorEvaluator.evaluateList(node, new JSONObject("{\"prop\": []}")));

        // Unsupported types
        assertNull(SelectorEvaluator.evaluateList(node, new JSONObject("{\"prop\": 1}")));
        assertNull(SelectorEvaluator.evaluateList(node, new JSONObject("{\"prop\": \"\"}")));
        assertNull(SelectorEvaluator.evaluateList(node, new JSONObject("{\"prop\": {}}")));
    }

    public void testEvaluateString() throws JSONException {
        // Error conditions
        try {
            SelectorEvaluator.evaluateString(new JSONObject("{}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: string");
        }
        try {
            SelectorEvaluator.evaluateString(new JSONObject("{\"operator\": \"defined\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: string");
        }
        try {
            SelectorEvaluator.evaluateString(new JSONObject("{\"operator\": \"string\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: string");
        }
        try {
            SelectorEvaluator.evaluateString(new JSONObject("{\"operator\": \"string\", \"children\": []}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: string");
        }
        try {
            SelectorEvaluator.evaluateString(new JSONObject("{\"operator\": \"string\", \"children\": [{}, {}]}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for cast operator: string");
        }

        JSONObject node = new JSONObject("{\"operator\": \"string\", \"children\": [{\"property\": \"event\", \"value\": \"prop\"}]}");

        // Datetime
        JSONObject withDateTimeCast = new JSONObject("{\"operator\": \"string\", \"children\": [" +
                "{\"operator\": \"datetime\", \"children\": [{\"property\": \"event\", \"value\": \"prop\"}]}" +
                "]}");
        final String dtStr = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date(100000));
        assertEquals(dtStr, SelectorEvaluator.evaluateString(withDateTimeCast, new JSONObject("{\"prop\": \"" + dtStr + "\"}")));

        // Number
        assertEquals("100", SelectorEvaluator.evaluateString(node, new JSONObject("{\"prop\": 100}")));

        // Array
        assertEquals("[1,2,3]", SelectorEvaluator.evaluateString(node, new JSONObject("{\"prop\": [1,2,3]}")));

        // Dict
        assertEquals("{\"a\":1}", SelectorEvaluator.evaluateString(node, new JSONObject("{\"prop\": {\"a\": 1}}")));

        // null
        assertEquals("null", SelectorEvaluator.evaluateString(node, new JSONObject("{\"prop\": null}")));
        assertNull(SelectorEvaluator.evaluateString(node, new JSONObject("{}")));

        // Boolean
        assertEquals("true", SelectorEvaluator.evaluateString(node, new JSONObject("{\"prop\": true}")));
    }

    public void testEvaluateAnd() throws JSONException {
        // Error conditions
        try {
            SelectorEvaluator.evaluateAnd(new JSONObject("{}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: and");
        }
        try {
            SelectorEvaluator.evaluateAnd(new JSONObject("{\"operator\": \"defined\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: and");
        }
        try {
            SelectorEvaluator.evaluateAnd(new JSONObject("{\"operator\": \"and\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: and");
        }
        try {
            SelectorEvaluator.evaluateAnd(new JSONObject("{\"operator\": \"and\", \"children\": []}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: and");
        }
        try {
            SelectorEvaluator.evaluateAnd(new JSONObject("{\"operator\": \"and\", \"children\": [{}]}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: and");
        }

        JSONObject node = new JSONObject("{\"operator\": \"and\", \"children\": [{\"property\": \"event\", \"value\": \"prop1\"}," +
                "{\"property\": \"event\", \"value\": \"prop2\"}]}");

        assertFalse(SelectorEvaluator.evaluateAnd(node, new JSONObject("{\"prop1\": true, \"prop2\": false}")));
        assertFalse(SelectorEvaluator.evaluateAnd(node, new JSONObject("{\"prop1\": false, \"prop2\": false}")));
        assertFalse(SelectorEvaluator.evaluateAnd(node, new JSONObject("{\"prop1\": false, \"prop2\": true}")));
        assertTrue(SelectorEvaluator.evaluateAnd(node, new JSONObject("{\"prop1\": true, \"prop2\": true}")));
    }

    public void testEvaluateOr() throws JSONException {
        // Error conditions
        try {
            SelectorEvaluator.evaluateOr(new JSONObject("{}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: or");
        }
        try {
            SelectorEvaluator.evaluateOr(new JSONObject("{\"operator\": \"defined\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: or");
        }
        try {
            SelectorEvaluator.evaluateOr(new JSONObject("{\"operator\": \"or\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: or");
        }
        try {
            SelectorEvaluator.evaluateOr(new JSONObject("{\"operator\": \"or\", \"children\": []}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: or");
        }
        try {
            SelectorEvaluator.evaluateOr(new JSONObject("{\"operator\": \"or\", \"children\": [{}]}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: or");
        }

        JSONObject node = new JSONObject("{\"operator\": \"or\", \"children\": [{\"property\": \"event\", \"value\": \"prop1\"}," +
                "{\"property\": \"event\", \"value\": \"prop2\"}]}");

        assertTrue(SelectorEvaluator.evaluateOr(node, new JSONObject("{\"prop1\": true, \"prop2\": false}")));
        assertFalse(SelectorEvaluator.evaluateOr(node, new JSONObject("{\"prop1\": false, \"prop2\": false}")));
        assertTrue(SelectorEvaluator.evaluateOr(node, new JSONObject("{\"prop1\": false, \"prop2\": true}")));
        assertTrue(SelectorEvaluator.evaluateOr(node, new JSONObject("{\"prop1\": true, \"prop2\": true}")));
    }

    public void testEvaluateIn() throws JSONException {
        // Error conditions
        try {
            SelectorEvaluator.evaluateIn(new JSONObject("{}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: (not) in");
        }
        try {
            SelectorEvaluator.evaluateIn(new JSONObject("{\"operator\": \"defined\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: (not) in");
        }
        try {
            SelectorEvaluator.evaluateIn(new JSONObject("{\"operator\": \"in\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: (not) in");
        }
        try {
            SelectorEvaluator.evaluateIn(new JSONObject("{\"operator\": \"in\", \"children\": []}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: (not) in");
        }
        try {
            SelectorEvaluator.evaluateIn(new JSONObject("{\"operator\": \"in\", \"children\": [{}]}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: (not) in");
        }

        JSONObject node = new JSONObject("{\"operator\": \"in\", \"children\": [{\"property\": \"event\", \"value\": \"prop1\"}," +
                "{\"property\": \"event\", \"value\": \"prop2\"}]}");

        assertTrue(SelectorEvaluator.evaluateIn(node, new JSONObject("{\"prop1\": 1, \"prop2\": [1,2,3]}")));
        assertTrue(SelectorEvaluator.evaluateIn(node, new JSONObject("{\"prop1\": \"ab\", \"prop2\": \"dabc\"}")));
        assertFalse(SelectorEvaluator.evaluateIn(node, new JSONObject("{\"prop1\": 1, \"prop2\": [11,12,1111]}")));
        assertFalse(SelectorEvaluator.evaluateIn(node, new JSONObject("{\"prop1\": \"ab\", \"prop2\": \"cbad\"}")));

        node = new JSONObject("{\"operator\": \"not in\", \"children\": [{\"property\": \"event\", \"value\": \"prop1\"}," +
                "{\"property\": \"event\", \"value\": \"prop2\"}]}");

        assertFalse(SelectorEvaluator.evaluateIn(node, new JSONObject("{\"prop1\": 1, \"prop2\": [1,2,3]}")));
        assertFalse(SelectorEvaluator.evaluateIn(node, new JSONObject("{\"prop1\": \"ab\", \"prop2\": \"dabc\"}")));
        assertTrue(SelectorEvaluator.evaluateIn(node, new JSONObject("{\"prop1\": 1, \"prop2\": [11,12,1111]}")));
        assertTrue(SelectorEvaluator.evaluateIn(node, new JSONObject("{\"prop1\": \"ab\", \"prop2\": \"cbad\"}")));
    }

    public void testEvaluatePlus() throws JSONException {
        // Error conditions
        try {
            SelectorEvaluator.evaluatePlus(new JSONObject("{}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: +");
        }
        try {
            SelectorEvaluator.evaluatePlus(new JSONObject("{\"operator\": \"defined\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: +");
        }
        try {
            SelectorEvaluator.evaluatePlus(new JSONObject("{\"operator\": \"+\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: +");
        }
        try {
            SelectorEvaluator.evaluatePlus(new JSONObject("{\"operator\": \"+\", \"children\": []}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: +");
        }
        try {
            SelectorEvaluator.evaluatePlus(new JSONObject("{\"operator\": \"+\", \"children\": [{}]}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: +");
        }

        JSONObject node = new JSONObject("{\"operator\": \"+\", \"children\": [{\"property\": \"event\", \"value\": \"prop1\"}," +
                "{\"property\": \"event\", \"value\": \"prop2\"}]}");

        assertEquals(new Double(3), SelectorEvaluator.evaluatePlus(node, new JSONObject("{\"prop1\": 1, \"prop2\": 2}")));
        assertEquals("12", SelectorEvaluator.evaluatePlus(node, new JSONObject("{\"prop1\": \"1\", \"prop2\": \"2\"}")));
        assertNull(SelectorEvaluator.evaluatePlus(node, new JSONObject("{\"prop1\": 1, \"prop2\": [11,12,1111]}")));
    }

    public void testEvaluateArithmetic() throws JSONException {
        // Error conditions
        try {
            SelectorEvaluator.evaluateArithmetic(new JSONObject("{}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for arithmetic operator");
        }
        try {
            SelectorEvaluator.evaluateArithmetic(new JSONObject("{\"operator\": \"defined\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for arithmetic operator");
        }
        try {
            SelectorEvaluator.evaluateArithmetic(new JSONObject("{\"operator\": \"-\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for arithmetic operator");
        }
        try {
            SelectorEvaluator.evaluateArithmetic(new JSONObject("{\"operator\": \"-\", \"children\": []}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for arithmetic operator");
        }
        try {
            SelectorEvaluator.evaluateArithmetic(new JSONObject("{\"operator\": \"-\", \"children\": [{}]}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for arithmetic operator");
        }

        JSONObject node = new JSONObject("{\"operator\": \"-\", \"children\": [{\"property\": \"event\", \"value\": \"prop1\"}," +
                "{\"property\": \"event\", \"value\": \"prop2\"}]}");
        assertEquals(new Double(-1), SelectorEvaluator.evaluateArithmetic(node, new JSONObject("{\"prop1\": 1, \"prop2\": 2}")));
        node = new JSONObject("{\"operator\": \"*\", \"children\": [{\"property\": \"event\", \"value\": \"prop1\"}," +
                "{\"property\": \"event\", \"value\": \"prop2\"}]}");
        assertEquals(2.0, SelectorEvaluator.evaluateArithmetic(node, new JSONObject("{\"prop1\": 1, \"prop2\": 2}")));
        node = new JSONObject("{\"operator\": \"/\", \"children\": [{\"property\": \"event\", \"value\": \"prop1\"}," +
                "{\"property\": \"event\", \"value\": \"prop2\"}]}");
        assertEquals(0.5, SelectorEvaluator.evaluateArithmetic(node, new JSONObject("{\"prop1\": 1, \"prop2\": 2}")));
        node = new JSONObject("{\"operator\": \"%\", \"children\": [{\"property\": \"event\", \"value\": \"prop1\"}," +
                "{\"property\": \"event\", \"value\": \"prop2\"}]}");
        assertNull(SelectorEvaluator.evaluateArithmetic(node, new JSONObject("{\"prop1\": 1, \"prop2\": 0}")));
        assertEquals(0.0, SelectorEvaluator.evaluateArithmetic(node, new JSONObject("{\"prop1\": 0, \"prop2\": 1}")));
        assertEquals(1.0, SelectorEvaluator.evaluateArithmetic(node, new JSONObject("{\"prop1\": -1, \"prop2\": 2}")));
        assertEquals(-1.0, SelectorEvaluator.evaluateArithmetic(node, new JSONObject("{\"prop1\": -1, \"prop2\": -2}")));
        assertEquals(-1.0, SelectorEvaluator.evaluateArithmetic(node, new JSONObject("{\"prop1\": 1, \"prop2\": -2}")));
    }

    public void testEvaluateEquality() throws JSONException {
        // Error conditions
        try {
            SelectorEvaluator.evaluateEquality(new JSONObject("{}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for equality operator");
        }
        try {
            SelectorEvaluator.evaluateEquality(new JSONObject("{\"operator\": \"defined\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for equality operator");
        }
        try {
            SelectorEvaluator.evaluateEquality(new JSONObject("{\"operator\": \"=\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for equality operator");
        }
        try {
            SelectorEvaluator.evaluateEquality(new JSONObject("{\"operator\": \"=\", \"children\": []}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for equality operator");
        }
        try {
            SelectorEvaluator.evaluateEquality(new JSONObject("{\"operator\": \"=\", \"children\": [{}]}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for equality operator");
        }

        JSONObject node = new JSONObject("{\"operator\": \"==\", \"children\": [{\"property\": \"event\", \"value\": \"prop1\"}," +
                "{\"property\": \"event\", \"value\": \"prop2\"}]}");

        // Null
        assertTrue(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": null, \"prop2\": null}")));

        // Mixed types
        assertFalse(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": null, \"prop2\": \"null\"}")));

        // Number
        assertTrue(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": 1, \"prop2\": 1}")));
        assertFalse(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": 1, \"prop2\": 11}")));

        // Boolean
        assertTrue(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": true, \"prop2\": true}")));
        assertTrue(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": false, \"prop2\": false}")));
        assertFalse(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": true, \"prop2\": false}")));
        assertFalse(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": false, \"prop2\": true}")));

        // String
        assertTrue(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": \"false\", \"prop2\": \"false\"}")));
        assertFalse(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": \"true\", \"prop2\": \"false\"}")));

        // Datetime
        JSONObject withDateTimeCast = new JSONObject("{\"operator\": \"==\", \"children\": [" +
                "{\"operator\": \"datetime\", \"children\": [{\"property\": \"event\", \"value\": \"prop1\"}]}," +
                "{\"operator\": \"datetime\", \"children\": [{\"property\": \"event\", \"value\": \"prop2\"}]}" +
                "]}");
        assertTrue(SelectorEvaluator.evaluateEquality(withDateTimeCast, new JSONObject("{\"prop1\": 1, \"prop2\": 1}")));
        assertFalse(SelectorEvaluator.evaluateEquality(withDateTimeCast, new JSONObject("{\"prop1\": 1, \"prop2\": 11}")));

        // Dict
        assertTrue(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": {\"a\": 1, \"b\": 1}, \"prop2\": {\"b\": 1, \"a\": 1}}")));
        assertFalse(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": {\"a\": 1}, \"prop2\": {\"b\": 1}}")));

        node = new JSONObject("{\"operator\": \"!=\", \"children\": [{\"property\": \"event\", \"value\": \"prop1\"}," +
                "{\"property\": \"event\", \"value\": \"prop2\"}]}");

        // Null
        assertFalse(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": null, \"prop2\": null}")));

        // Mixed types
        assertTrue(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": null, \"prop2\": \"null\"}")));

        // Number
        assertFalse(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": 1, \"prop2\": 1}")));
        assertTrue(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": 1, \"prop2\": 11}")));

        // Boolean
        assertFalse(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": true, \"prop2\": true}")));
        assertFalse(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": false, \"prop2\": false}")));
        assertTrue(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": true, \"prop2\": false}")));
        assertTrue(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": false, \"prop2\": true}")));

        // String
        assertFalse(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": \"false\", \"prop2\": \"false\"}")));
        assertTrue(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": \"true\", \"prop2\": \"false\"}")));

        // Datetime
        withDateTimeCast = new JSONObject("{\"operator\": \"!=\", \"children\": [" +
                "{\"operator\": \"datetime\", \"children\": [{\"property\": \"event\", \"value\": \"prop1\"}]}," +
                "{\"operator\": \"datetime\", \"children\": [{\"property\": \"event\", \"value\": \"prop2\"}]}" +
                "]}");
        assertFalse(SelectorEvaluator.evaluateEquality(withDateTimeCast, new JSONObject("{\"prop1\": 1, \"prop2\": 1}")));
        assertTrue(SelectorEvaluator.evaluateEquality(withDateTimeCast, new JSONObject("{\"prop1\": 1, \"prop2\": 11}")));

        // Dict
        assertFalse(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": {\"a\": 1, \"b\": 1}, \"prop2\": {\"b\": 1, \"a\": 1}}")));
        assertTrue(SelectorEvaluator.evaluateEquality(node, new JSONObject("{\"prop1\": {\"a\": 1}, \"prop2\": {\"b\": 1}}")));
    }

    public void testEvaluateComparison() throws JSONException {
        // Error conditions
        try {
            SelectorEvaluator.evaluateComparison(new JSONObject("{}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for comparison operator");
        }
        try {
            SelectorEvaluator.evaluateComparison(new JSONObject("{\"operator\": \"defined\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for comparison operator");
        }
        try {
            SelectorEvaluator.evaluateComparison(new JSONObject("{\"operator\": \">\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for comparison operator");
        }
        try {
            SelectorEvaluator.evaluateComparison(new JSONObject("{\"operator\": \">\", \"children\": []}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for comparison operator");
        }
        try {
            SelectorEvaluator.evaluateComparison(new JSONObject("{\"operator\": \">\", \"children\": [{}]}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for comparison operator");
        }

        JSONObject node = new JSONObject("{\"operator\": \">\", \"children\": [{\"property\": \"event\", \"value\": \"prop1\"}," +
                "{\"property\": \"event\", \"value\": \"prop2\"}]}");
        assertFalse(SelectorEvaluator.evaluateComparison(node, new JSONObject("{\"prop1\": 1, \"prop2\": 1}")));
        assertFalse(SelectorEvaluator.evaluateComparison(node, new JSONObject("{\"prop1\": 1, \"prop2\": 2}")));
        assertTrue(SelectorEvaluator.evaluateComparison(node, new JSONObject("{\"prop1\": 2, \"prop2\": 1}")));

        node = new JSONObject("{\"operator\": \">=\", \"children\": [{\"property\": \"event\", \"value\": \"prop1\"}," +
                "{\"property\": \"event\", \"value\": \"prop2\"}]}");
        assertTrue(SelectorEvaluator.evaluateComparison(node, new JSONObject("{\"prop1\": 1, \"prop2\": 1}")));
        assertFalse(SelectorEvaluator.evaluateComparison(node, new JSONObject("{\"prop1\": 1, \"prop2\": 2}")));
        assertTrue(SelectorEvaluator.evaluateComparison(node, new JSONObject("{\"prop1\": 2, \"prop2\": 1}")));

        node = new JSONObject("{\"operator\": \"<\", \"children\": [{\"property\": \"event\", \"value\": \"prop1\"}," +
                "{\"property\": \"event\", \"value\": \"prop2\"}]}");
        assertFalse(SelectorEvaluator.evaluateComparison(node, new JSONObject("{\"prop1\": 1, \"prop2\": 1}")));
        assertTrue(SelectorEvaluator.evaluateComparison(node, new JSONObject("{\"prop1\": 1, \"prop2\": 2}")));
        assertFalse(SelectorEvaluator.evaluateComparison(node, new JSONObject("{\"prop1\": 2, \"prop2\": 1}")));

        node = new JSONObject("{\"operator\": \"<=\", \"children\": [{\"property\": \"event\", \"value\": \"prop1\"}," +
                "{\"property\": \"event\", \"value\": \"prop2\"}]}");
        assertTrue(SelectorEvaluator.evaluateComparison(node, new JSONObject("{\"prop1\": 1, \"prop2\": 1}")));
        assertTrue(SelectorEvaluator.evaluateComparison(node, new JSONObject("{\"prop1\": 1, \"prop2\": 2}")));
        assertFalse(SelectorEvaluator.evaluateComparison(node, new JSONObject("{\"prop1\": 2, \"prop2\": 1}")));

        assertNull(SelectorEvaluator.evaluateComparison(node, new JSONObject("{\"prop1\": [1,2,2], \"prop2\": 1}")));
    }

    public void testEvaluateDefined() throws JSONException {
        // Error conditions
        try {
            SelectorEvaluator.evaluateDefined(new JSONObject("{}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for (not) defined operator");
        }
        try {
            SelectorEvaluator.evaluateDefined(new JSONObject("{\"operator\": \"string\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for (not) defined operator");
        }
        try {
            SelectorEvaluator.evaluateDefined(new JSONObject("{\"operator\": \"defined\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for (not) defined operator");
        }
        try {
            SelectorEvaluator.evaluateDefined(new JSONObject("{\"operator\": \"defined\", \"children\": []}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for (not) defined operator");
        }
        try {
            SelectorEvaluator.evaluateDefined(new JSONObject("{\"operator\": \"defined\", \"children\": [{}, {}]}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for (not) defined operator");
        }

        JSONObject node = new JSONObject("{\"operator\": \"defined\", \"children\": [{\"property\": \"event\", \"value\": \"prop\"}]}");
        assertTrue(SelectorEvaluator.evaluateDefined(node, new JSONObject("{\"prop\": null}")));
        assertFalse(SelectorEvaluator.evaluateDefined(node, new JSONObject("{\"prop1\": null}")));
    }

    public void testEvaluateNot() throws JSONException {
        // Error conditions
        try {
            SelectorEvaluator.evaluateNot(new JSONObject("{}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: not");
        }
        try {
            SelectorEvaluator.evaluateNot(new JSONObject("{\"operator\": \"string\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: not");
        }
        try {
            SelectorEvaluator.evaluateNot(new JSONObject("{\"operator\": \"not\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: not");
        }
        try {
            SelectorEvaluator.evaluateNot(new JSONObject("{\"operator\": \"not\", \"children\": []}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: not");
        }
        try {
            SelectorEvaluator.evaluateNot(new JSONObject("{\"operator\": \"not\", \"children\": [{}, {}]}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid node for operator: not");
        }

        JSONObject node = new JSONObject("{\"operator\": \"not\", \"children\": [{\"property\": \"event\", \"value\": \"prop\"}]}");
        // Null or undefined returns True
        assertTrue(SelectorEvaluator.evaluateNot(node, new JSONObject("{\"prop\": null}")));
        assertTrue(SelectorEvaluator.evaluateNot(node, new JSONObject("{\"prop1\": false}")));
        // Boolean
        assertFalse(SelectorEvaluator.evaluateNot(node, new JSONObject("{\"prop\": true}")));
        assertTrue(SelectorEvaluator.evaluateNot(node, new JSONObject("{\"prop\": false}")));
        // Null for all other types
        assertNull(SelectorEvaluator.evaluateNot(node, new JSONObject("{\"prop\": []}")));
        assertNull(SelectorEvaluator.evaluateNot(node, new JSONObject("{\"prop\": {}}")));
        assertNull(SelectorEvaluator.evaluateNot(node, new JSONObject("{\"prop\": 1}")));
        assertNull(SelectorEvaluator.evaluateNot(node, new JSONObject("{\"prop\": \"abc\"}")));
    }

    public void testEvaluateOperand() throws JSONException {
        // Error conditions
        try {
            SelectorEvaluator.evaluateOperand(new JSONObject("{}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Missing required keys: property/value");
        }
        try {
            SelectorEvaluator.evaluateOperand(new JSONObject("{\"property\": \"event\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Missing required keys: property/value");
        }
        try {
            SelectorEvaluator.evaluateOperand(new JSONObject("{\"value\": \"prop\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Missing required keys: property/value");
        }
        try {
            SelectorEvaluator.evaluateOperand(new JSONObject("{\"property\": \"invalid\", \"value\": \"prop\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid operand: Invalid property type: invalid");
        }

        JSONObject node = new JSONObject("{\"property\": \"event\", \"value\": \"prop\"}");
        assertEquals(1, SelectorEvaluator.evaluateOperand(node, new JSONObject("{\"prop\": 1}")));

        node = new JSONObject("{\"property\": \"literal\", \"value\": \"prop\"}");
        assertEquals("prop", SelectorEvaluator.evaluateOperand(node, new JSONObject("{}")));

        node = new JSONObject("{\"property\": \"literal\", \"value\": \"now\"}");
        assertTrue(SelectorEvaluator.evaluateOperand(node, new JSONObject("{}")) instanceof Date);

        node = new JSONObject("{\"property\": \"literal\", \"value\": {\"window\":{\"unit\": \"day\", \"value\": 1}}}");
        assertTrue(SelectorEvaluator.evaluateOperand(node, new JSONObject("{}")) instanceof Date);
    }

    public void testEvaluateWindow() throws JSONException, ParseException {
        // Error conditions
        try {
            SelectorEvaluator.evaluateWindow(new JSONObject("{}"));
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid window specification for value key {}", e.getMessage());
        }
        try {
            SelectorEvaluator.evaluateWindow(new JSONObject("{\"window\": {}}"));
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid window specification for value key {\"window\":{}}", e.getMessage());
        }
        try {
            SelectorEvaluator.evaluateWindow(new JSONObject("{\"window\": {\"unit\": \"day\"}}"));
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid window specification for value key {\"window\":{\"unit\":\"day\"}}", e.getMessage());
        }
        try {
            SelectorEvaluator.evaluateWindow(new JSONObject("{\"window\": {\"value\": \"1\"}}"));
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid window specification for value key {\"window\":{\"value\":\"1\"}}", e.getMessage());
        }
        try {
            SelectorEvaluator.evaluateWindow(new JSONObject("{\"window\": {\"unit\": \"blah\", \"value\": \"1\"}}"));
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid unit specification for window blah", e.getMessage());
        }
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        final Date dt = format.parse("2019-01-02T12:00:00");
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(dt);
        SelectorEvaluator.setCalendar(calendar, true);

        assertEquals("2019-01-03T12:00:00", format.format(SelectorEvaluator.evaluateWindow(new JSONObject("{\"window\":{\"unit\": \"day\", \"value\": 1}}"))));
        assertEquals("2019-01-03T13:00:00", format.format(SelectorEvaluator.evaluateWindow(new JSONObject("{\"window\":{\"unit\": \"hour\", \"value\": 1}}"))));
        assertEquals("2019-01-10T13:00:00", format.format(SelectorEvaluator.evaluateWindow(new JSONObject("{\"window\":{\"unit\": \"week\", \"value\": 1}}"))));
        assertEquals("2019-02-09T13:00:00", format.format(SelectorEvaluator.evaluateWindow(new JSONObject("{\"window\":{\"unit\": \"month\", \"value\": 1}}"))));
        assertEquals("2019-01-10T13:00:00", format.format(SelectorEvaluator.evaluateWindow(new JSONObject("{\"window\":{\"unit\": \"month\", \"value\": -1}}"))));
        assertEquals("2019-01-03T13:00:00", format.format(SelectorEvaluator.evaluateWindow(new JSONObject("{\"window\":{\"unit\": \"week\", \"value\": -1}}"))));
        assertEquals("2019-01-03T12:00:00", format.format(SelectorEvaluator.evaluateWindow(new JSONObject("{\"window\":{\"unit\": \"hour\", \"value\": -1}}"))));
        assertEquals("2019-01-02T12:00:00", format.format(SelectorEvaluator.evaluateWindow(new JSONObject("{\"window\":{\"unit\": \"day\", \"value\": -1}}"))));
    }

    public void testEvaluateOperator() throws JSONException {
        // Error conditions
        try {
            SelectorEvaluator.evaluateOperator(new JSONObject("{}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Missing required keys: operator");
        }
        try {
            SelectorEvaluator.evaluateOperator(new JSONObject("{\"operator\": \"unknown\"}"), new JSONObject());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Unknown operator: unknown");
        }

        JSONObject node = new JSONObject("{\"operator\": \"and\", \"children\": [{\"property\": \"event\", \"value\": \"prop1\"}," +
                "{\"property\": \"event\", \"value\": \"prop2\"}]}");
        JSONObject prop = new JSONObject("{\"prop1\": true, \"prop2\": false}");
        assertFalse((boolean) SelectorEvaluator.evaluateOperator(node, prop));

        node.put("operator", "or");
        assertTrue((boolean) SelectorEvaluator.evaluateOperator(node, prop));

        node.put("operator", "in");
        prop.put("prop1", 1);
        prop.put("prop2", new JSONArray("[1,2,3]"));
        assertTrue((boolean) SelectorEvaluator.evaluateOperator(node, prop));
        node.put("operator", "not in");
        assertFalse((boolean) SelectorEvaluator.evaluateOperator(node, prop));

        prop.put("prop1", 1);
        prop.put("prop2", 2);
        node.put("operator", "+");
        assertEquals(3.0, (Double) SelectorEvaluator.evaluateOperator(node, prop));

        node.put("operator", "-");
        assertEquals(-1.0, (Double) SelectorEvaluator.evaluateOperator(node, prop));
        node.put("operator", "*");
        assertEquals(2.0, (Double) SelectorEvaluator.evaluateOperator(node, prop));
        node.put("operator", "/");
        assertEquals(0.5, (Double) SelectorEvaluator.evaluateOperator(node, prop));
        node.put("operator", "%");
        assertEquals(1.0, (Double) SelectorEvaluator.evaluateOperator(node, prop));
        node.put("operator", "==");
        assertFalse((boolean) SelectorEvaluator.evaluateOperator(node, prop));
        node.put("operator", "!=");
        assertTrue((boolean) SelectorEvaluator.evaluateOperator(node, prop));

        node.put("operator", ">");
        assertFalse((boolean) SelectorEvaluator.evaluateOperator(node, prop));
        node.put("operator", "<");
        assertTrue((boolean) SelectorEvaluator.evaluateOperator(node, prop));
        prop.put("prop2", 1);
        node.put("operator", ">");
        assertFalse((boolean) SelectorEvaluator.evaluateOperator(node, prop));
        node.put("operator", ">=");
        assertTrue((boolean) SelectorEvaluator.evaluateOperator(node, prop));
        node.put("operator", "<");
        assertFalse((boolean) SelectorEvaluator.evaluateOperator(node, prop));
        node.put("operator", "<=");
        assertTrue((boolean) SelectorEvaluator.evaluateOperator(node, prop));

        node = new JSONObject("{\"operator\": \"boolean\", \"children\": [{\"property\": \"event\", \"value\": \"prop\"}]}");
        prop = new JSONObject("{\"prop\": 1}");
        assertTrue((boolean) SelectorEvaluator.evaluateOperator(node, prop));
        node.put("operator", "datetime");
        assertTrue(SelectorEvaluator.evaluateOperator(node, prop) instanceof Date);
        node.put("operator", "list");
        prop.put("prop", new JSONArray("[1,2,3]"));
        assertEquals(new JSONArray("[1,2,3]"), SelectorEvaluator.evaluateOperator(node, prop));

        node.put("operator", "number");
        prop.put("prop", "1");
        assertEquals(1.0, SelectorEvaluator.evaluateOperator(node, prop));

        node.put("operator", "string");
        prop.put("prop", 1);
        assertEquals("1", SelectorEvaluator.evaluateOperator(node, prop).toString());

        node.put("operator", "defined");
        prop.remove("prop");
        assertFalse((boolean) SelectorEvaluator.evaluateOperator(node, prop));
        node.put("operator", "not defined");
        assertTrue((boolean) SelectorEvaluator.evaluateOperator(node, prop));

        node.put("operator", "not");
        prop.put("prop", false);
        assertTrue((boolean) SelectorEvaluator.evaluateOperator(node, prop));
    }

    public void testEvaluate() throws JSONException {
        JSONObject node = new JSONObject("{\"operator\": \"and\", " +
                "\"children\": [" +
                    "{\"operator\": \"not\", \"children\": [{\"property\": \"literal\", \"value\": false}]}," +
                    "{\"operator\": \"==\", \"children\": [" +
                        "{\"property\": \"event\", \"value\": \"prop\"}," +
                        "{\"operator\": \"number\", \"children\": [{\"property\": \"event\", \"value\": \"prop\"}]}" +
                    "]}" +
                "]}");
        JSONObject props = new JSONObject("{\"prop\": 1}");
        final SelectorEvaluator evaluator = new SelectorEvaluator(node);
        assertTrue(evaluator.evaluate(props));
    }
}
