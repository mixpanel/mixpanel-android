package com.mixpanel.android.mpmetrics;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

class SelectorEvaluator {
    private static final String ENGAGE_DATE_FORMAT_STRING = "yyyy-MM-dd'T'HH:mm:ss";
    // Key words
    private static final String OPERATOR_KEY = "operator";
    private static final String CHILDREN_KEY = "children";
    private static final String PROPERTY_KEY = "property";
    private static final String VALUE_KEY = "value";
    private static final String EVENT_KEY = "event";
    private static final String LITERAL_KEY = "literal";
    private static final String WINDOW_KEY = "window";
    private static final String UNIT_KEY = "unit";
    private static final String HOUR_KEY = "hour";
    private static final String DAY_KEY = "day";
    private static final String WEEK_KEY = "week";
    private static final String MONTH_KEY = "month";
    // Typecast operators
    private static final String BOOLEAN_OPERATOR = "boolean";
    private static final String DATETIME_OPERATOR = "datetime";
    private static final String LIST_OPERATOR = "list";
    private static final String NUMBER_OPERATOR = "number";
    private static final String STRING_OPERATOR = "string";
    // Binary operators
    private static final String AND_OPERATOR = "and";
    private static final String OR_OPERATOR = "or";
    private static final String IN_OPERATOR = "in";
    private static final String NOT_IN_OPERATOR = "not in";
    private static final String PLUS_OPERATOR = "+";
    private static final String MINUS_OPERATOR = "-";
    private static final String MUL_OPERATOR = "*";
    private static final String DIV_OPERATOR = "/";
    private static final String MOD_OPERATOR = "%";
    private static final String EQUALS_OPERATOR = "==";
    private static final String NOT_EQUALS_OPERATOR = "!=";
    private static final String GREATER_THAN_OPERATOR = ">";
    private static final String GREATER_THAN_EQUAL_OPERATOR = ">=";
    private static final String LESS_THAN_OPERATOR = "<";
    private static final String LESS_THAN_EQUAL_OPERATOR = "<=";
    // Unary operators
    private static final String NOT_OPERATOR = "not";
    private static final String DEFINED_OPERATOR = "defined";
    private static final String NOT_DEFINED_OPERATOR = "not defined";
    private static final String NOW_LITERAL = "now";

    enum PropertyType {
        Array, Boolean, Datetime, Null, Number, Object, String, Unknown
    }

    SelectorEvaluator(JSONObject selector) throws IllegalArgumentException {
        if (!selector.has(OPERATOR_KEY) || !selector.has(CHILDREN_KEY)) {
            throw new IllegalArgumentException("Missing required keys: " + OPERATOR_KEY + " " + CHILDREN_KEY);
        }
        mSelector = selector;
    }

    static PropertyType getType(Object value) {
        if (value == null || value.equals(JSONObject.NULL)) {
            return PropertyType.Null;
        }
        if (value instanceof String) {
            return PropertyType.String;
        }
        if (value instanceof JSONArray) {
            return PropertyType.Array;
        }
        if (value instanceof JSONObject) {
            return PropertyType.Object;
        }
        if (value instanceof Double || value instanceof Integer || value instanceof Number) {
            return PropertyType.Number;
        }
        if (value instanceof Boolean) {
            return PropertyType.Boolean;
        }
        if (value instanceof Date) {
            return PropertyType.Datetime;
        }

        return PropertyType.Unknown;
    }

    // Typecast operators
    static Double toNumber(Object value) {
        switch (getType(value)) {
            case Null:
                return null;
            case Datetime:
                final Date dt = (Date) value;
                return dt.getTime() > 0 ? new Double(dt.getTime()) : null;
            case Boolean:
                final Boolean b = (Boolean) value;
                return b ? 1.0 : 0.0;
            case Number:
                if (value instanceof Double) {
                    return (Double) value;
                }
                if (value instanceof Integer) {
                    return ((Integer) value).doubleValue();
                }
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
            case String:
                try {
                    return Double.parseDouble((String) value);
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            default:
                return null;
        }
    }

    static Boolean toBoolean(Object value) {
        switch (getType(value)) {
            case Null:
                return false;
            case Boolean:
                return (Boolean) value;
            case Number:
                return toNumber(value) != 0.0;
            case String:
                return ((String) value).length() > 0;
            case Array:
                return ((JSONArray) value).length() > 0;
            case Datetime:
                return ((Date) value).getTime() > 0;
            case Object:
                return ((JSONObject) value).length() > 0;
            default:
                return false;
        }
    }

    static Double evaluateNumber(JSONObject node, JSONObject properties) throws JSONException {
        if (!node.has(OPERATOR_KEY) || !node.getString(OPERATOR_KEY).equals(NUMBER_OPERATOR) ||
                node.optJSONArray(CHILDREN_KEY) == null || node.getJSONArray(CHILDREN_KEY).length() != 1) {
            throw new IllegalArgumentException("Invalid node for cast operator: " + NUMBER_OPERATOR);
        }

        return toNumber(evaluateNode(node.getJSONArray(CHILDREN_KEY).getJSONObject(0), properties));
    }

    static Boolean evaluateBoolean(JSONObject node, JSONObject properties) throws JSONException {
        if (!node.has(OPERATOR_KEY) || !node.getString(OPERATOR_KEY).equals(BOOLEAN_OPERATOR) ||
                node.optJSONArray(CHILDREN_KEY) == null || node.getJSONArray(CHILDREN_KEY).length() != 1) {
            throw new IllegalArgumentException("Invalid node for cast operator: " + BOOLEAN_OPERATOR);
        }

        return toBoolean(evaluateNode(node.getJSONArray(CHILDREN_KEY).getJSONObject(0), properties));
    }

    static Date evaluateDateTime(JSONObject node, JSONObject properties) throws JSONException {
        if (!node.has(OPERATOR_KEY) || !node.getString(OPERATOR_KEY).equals(DATETIME_OPERATOR) ||
                node.optJSONArray(CHILDREN_KEY) == null || node.getJSONArray(CHILDREN_KEY).length() != 1) {
            throw new IllegalArgumentException("Invalid node for cast operator: " + DATETIME_OPERATOR);
        }

        final Object value = evaluateNode(node.getJSONArray(CHILDREN_KEY).getJSONObject(0), properties);
        switch (getType(value)) {
            case Number:
                return new Date(toNumber(value).longValue());
            case String:
                try {
                    return (new SimpleDateFormat(ENGAGE_DATE_FORMAT_STRING, Locale.US)).parse((String) value);
                } catch (ParseException e) {
                    return null;
                }
            case Datetime:
                return (Date) value;
            default:
                return null;
        }
    }

    static JSONArray evaluateList(JSONObject node, JSONObject properties) throws JSONException {
        if (!node.has(OPERATOR_KEY) || !node.getString(OPERATOR_KEY).equals(LIST_OPERATOR) ||
                node.optJSONArray(CHILDREN_KEY) == null || node.getJSONArray(CHILDREN_KEY).length() != 1) {
            throw new IllegalArgumentException("Invalid node for cast operator: " + LIST_OPERATOR);
        }

        final Object value = evaluateNode(node.getJSONArray(CHILDREN_KEY).getJSONObject(0), properties);
        if (getType(value) == PropertyType.Array) {
            return (JSONArray) value;
        }

        return null;
    }

    static String evaluateString(JSONObject node, JSONObject properties) throws JSONException {
        if (!node.has(OPERATOR_KEY) || !node.getString(OPERATOR_KEY).equals(STRING_OPERATOR) ||
                node.optJSONArray(CHILDREN_KEY) == null || node.getJSONArray(CHILDREN_KEY).length() != 1) {
            throw new IllegalArgumentException("Invalid node for cast operator: " + STRING_OPERATOR);
        }
        final Object value = evaluateNode(node.getJSONArray(CHILDREN_KEY).getJSONObject(0), properties);
        if (getType(value) == PropertyType.Datetime) {
            return new SimpleDateFormat(ENGAGE_DATE_FORMAT_STRING, Locale.US).format((Date) value);
        }

        return value != null ? value.toString() : null;
    }

    // Binary Operators
    static Boolean evaluateAnd(JSONObject node, JSONObject properties) throws JSONException {
        if (!node.has(OPERATOR_KEY) || !node.getString(OPERATOR_KEY).equals(AND_OPERATOR) ||
                node.optJSONArray(CHILDREN_KEY) == null || node.getJSONArray(CHILDREN_KEY).length() != 2) {
            throw new IllegalArgumentException("Invalid node for operator: " + AND_OPERATOR);
        }

        JSONArray children = node.getJSONArray(CHILDREN_KEY);
        return toBoolean(evaluateNode(children.getJSONObject(0), properties)) &&
                toBoolean(evaluateNode(children.getJSONObject(1), properties));
    }

    static Boolean evaluateOr(JSONObject node, JSONObject properties) throws JSONException {
        if (!node.has(OPERATOR_KEY) || !node.getString(OPERATOR_KEY).equals(OR_OPERATOR) ||
                node.optJSONArray(CHILDREN_KEY) == null || node.getJSONArray(CHILDREN_KEY).length() != 2) {
            throw new IllegalArgumentException("Invalid node for operator: " + OR_OPERATOR);
        }

        JSONArray children = node.getJSONArray(CHILDREN_KEY);
        return toBoolean(evaluateNode(children.getJSONObject(0), properties)) ||
                toBoolean(evaluateNode(children.getJSONObject(1), properties));
    }

    static Boolean evaluateIn(JSONObject node, JSONObject properties) throws JSONException {
        if (!node.has(OPERATOR_KEY) || !(node.getString(OPERATOR_KEY).equals(IN_OPERATOR) ||
                        node.getString(OPERATOR_KEY).equals(NOT_IN_OPERATOR)) ||
                node.optJSONArray(CHILDREN_KEY) == null || node.getJSONArray(CHILDREN_KEY).length() != 2) {
            throw new IllegalArgumentException("Invalid node for operator: (not) " + IN_OPERATOR);
        }
        JSONArray children = node.getJSONArray(CHILDREN_KEY);
        final Object l = evaluateNode(children.getJSONObject(0), properties);
        final Object r = evaluateNode(children.getJSONObject(1), properties);

        Boolean v = false;
        final String ls = l.toString();
        switch (getType(r)) {
            case Array:
                final JSONArray arr = (JSONArray) r;
                for (int i = 0; i < arr.length(); i++) {
                    if (ls.equals(arr.getString(i))) {
                        v = true;
                        break;
                    }
                }
                break;
            case String:
                v = ((String) r).contains(ls);
                break;
        }

        return node.getString(OPERATOR_KEY).equals(IN_OPERATOR) ? v : !v;
    }

    static Object evaluatePlus(JSONObject node, JSONObject properties) throws JSONException {
        if (!node.has(OPERATOR_KEY) || !node.getString(OPERATOR_KEY).equals(PLUS_OPERATOR) ||
                node.optJSONArray(CHILDREN_KEY) == null || node.getJSONArray(CHILDREN_KEY).length() != 2) {
            throw new IllegalArgumentException("Invalid node for operator: " + PLUS_OPERATOR);
        }

        JSONArray children = node.getJSONArray(CHILDREN_KEY);
        final Object l = evaluateNode(children.getJSONObject(0), properties);
        final Object r = evaluateNode(children.getJSONObject(1), properties);

        if (getType(l) == PropertyType.Number && getType(r) == PropertyType.Number) {
            return toNumber(l) + toNumber(r);
        }
        if (getType(l) == PropertyType.String && getType(r) == PropertyType.String) {
            return l + ((String) r);
        }

        return null;
    }

    static Double evaluateArithmetic(JSONObject node, JSONObject properties) throws JSONException {
        if (!node.has(OPERATOR_KEY) || !(node.getString(OPERATOR_KEY).equals(MINUS_OPERATOR) ||
                node.getString(OPERATOR_KEY).equals(MUL_OPERATOR) ||
                node.getString(OPERATOR_KEY).equals(DIV_OPERATOR) ||
                node.getString(OPERATOR_KEY).equals(MOD_OPERATOR)) ||
                node.optJSONArray(CHILDREN_KEY) == null || node.getJSONArray(CHILDREN_KEY).length() != 2) {
            throw new IllegalArgumentException("Invalid node for arithmetic operator");
        }

        JSONArray children = node.getJSONArray(CHILDREN_KEY);
        final Object l = evaluateNode(children.getJSONObject(0), properties);
        final Object r = evaluateNode(children.getJSONObject(1), properties);

        if (getType(l) == PropertyType.Number && getType(r) == PropertyType.Number) {
            final double ld = toNumber(l);
            final double rd = toNumber(r);
            switch (node.getString(OPERATOR_KEY)) {
                case MINUS_OPERATOR:
                    return ld-rd;
                case MUL_OPERATOR:
                    return ld*rd;
                case DIV_OPERATOR:
                    if (rd != 0.0) {
                        return ld/rd;
                    }
                    return null;
                case MOD_OPERATOR:
                    if (rd == 0.0) {
                        return null;
                    }
                    if (ld == 0.0) {
                        return 0.0;
                    }
                    if ((ld < 0 && rd > 0) || (ld > 0 && rd < 0)) {
                        return -(Math.floor(ld/rd) * rd-ld);
                    }
                    return ld % rd;
            }
        }

        return null;
    }

    private static boolean equals(Object l, Object r) {
        if (getType(l) == getType(r)) {
            switch (getType(l)) {
                case Null:
                    return true;
                case Number:
                    return toNumber(l).equals(toNumber(r));
                case Boolean:
                    return toBoolean(l).equals(toBoolean(r));
                case Datetime:
                case String:
                case Array:
                    return l.equals(r);
            }
        }

        return false;
    }

    static Boolean evaluateEquality(JSONObject node, JSONObject properties) throws JSONException {
        if (!node.has(OPERATOR_KEY) || !(node.getString(OPERATOR_KEY).equals(EQUALS_OPERATOR) ||
                node.getString(OPERATOR_KEY).equals(NOT_EQUALS_OPERATOR)) ||
                node.optJSONArray(CHILDREN_KEY) == null || node.getJSONArray(CHILDREN_KEY).length() != 2) {
            throw new IllegalArgumentException("Invalid node for equality operator");
        }

        JSONArray children = node.getJSONArray(CHILDREN_KEY);
        final Object l = evaluateNode(children.getJSONObject(0), properties);
        final Object r = evaluateNode(children.getJSONObject(1), properties);
        Boolean v = false;
        if (getType(l) == getType(r)) {
            switch (getType(l)) {
                case Object:
                    final JSONObject lo = (JSONObject) l;
                    final JSONObject ro = (JSONObject) r;

                    if (lo.length() == ro.length()) {
                        v = true;
                        String k;
                        Iterator<String> keys = lo.keys();
                        while(keys.hasNext()) {
                            k = keys.next();
                            if (!equals(lo.get(k), ro.opt(k))) {
                                v = false;
                                break;
                            }
                        }
                    }
                    break;
                default:
                    v = equals(l, r);
            }
        }

        return node.getString(OPERATOR_KEY).equals(NOT_EQUALS_OPERATOR) ? !v : v;
    }

    static Boolean evaluateComparison(JSONObject node, JSONObject properties) throws JSONException {
        if (!node.has(OPERATOR_KEY) || !(node.getString(OPERATOR_KEY).equals(GREATER_THAN_OPERATOR) ||
                node.getString(OPERATOR_KEY).equals(GREATER_THAN_EQUAL_OPERATOR) ||
                node.getString(OPERATOR_KEY).equals(LESS_THAN_OPERATOR) ||
                node.getString(OPERATOR_KEY).equals(LESS_THAN_EQUAL_OPERATOR)) ||
                node.optJSONArray(CHILDREN_KEY) == null || node.getJSONArray(CHILDREN_KEY).length() != 2) {
            throw new IllegalArgumentException("Invalid node for comparison operator");
        }

        JSONArray children = node.getJSONArray(CHILDREN_KEY);
        final Object l = evaluateNode(children.getJSONObject(0), properties);
        final Object r = evaluateNode(children.getJSONObject(1), properties);
        Boolean v = false;
        if (getType(l) == getType(r) && getType(l) == PropertyType.Number) {
            final Double ld = toNumber(l);
            final Double rd = toNumber(r);
            switch (node.getString(OPERATOR_KEY)) {
                case GREATER_THAN_OPERATOR:
                    return ld > rd;
                case GREATER_THAN_EQUAL_OPERATOR:
                    return ld >= rd;
                case LESS_THAN_OPERATOR:
                    return ld < rd;
                case LESS_THAN_EQUAL_OPERATOR:
                    return ld <= rd;
            }
        }

        return null;
    }

    static Boolean evaluateDefined(JSONObject node, JSONObject properties) throws JSONException {
        if (!node.has(OPERATOR_KEY) || !(node.getString(OPERATOR_KEY).equals(DEFINED_OPERATOR) ||
                node.getString(OPERATOR_KEY).equals(NOT_DEFINED_OPERATOR)) ||
                node.optJSONArray(CHILDREN_KEY) == null || node.getJSONArray(CHILDREN_KEY).length() != 1) {
            throw new IllegalArgumentException("Invalid node for (not) defined operator");
        }

        final boolean v = evaluateNode(node.getJSONArray(CHILDREN_KEY).getJSONObject(0),
                properties) == null ? false : true;
        return node.getString(OPERATOR_KEY).equals(DEFINED_OPERATOR) ? v : !v;
    }

    static Boolean evaluateNot(JSONObject node, JSONObject properties) throws JSONException {
        if (!node.has(OPERATOR_KEY) || !node.getString(OPERATOR_KEY).equals(NOT_OPERATOR) ||
                node.optJSONArray(CHILDREN_KEY) == null || node.getJSONArray(CHILDREN_KEY).length() != 1) {
            throw new IllegalArgumentException("Invalid node for operator: " + NOT_OPERATOR);
        }

        final Object v = evaluateNode(node.getJSONArray(CHILDREN_KEY).getJSONObject(0), properties);
        switch (getType(v)) {
            case Boolean:
                return !toBoolean(v);
            case Null:
                return true;
        }
        return null;
    }

    static Date evaluateWindow(JSONObject node) throws JSONException {
        final JSONObject window = node.optJSONObject(WINDOW_KEY);
        if (window == null || !window.has(VALUE_KEY) || !window.has(UNIT_KEY)) {
            throw new IllegalArgumentException("Invalid window specification for value key " + node.toString());
        }

        Calendar calendar;
        if (sCalendar == null) {
            calendar = Calendar.getInstance();
            calendar.setTime(new Date());
        } else {
            calendar = sCalendar;
        }

        switch (window.getString(UNIT_KEY)) {
            case HOUR_KEY:
                calendar.add(Calendar.HOUR, window.getInt(VALUE_KEY));
                break;
            case DAY_KEY:
                calendar.add(Calendar.DAY_OF_YEAR, window.getInt(VALUE_KEY));
                break;
            case WEEK_KEY:
                calendar.add(Calendar.DAY_OF_YEAR, 7*window.getInt(VALUE_KEY));
                break;
            case MONTH_KEY:
                calendar.add(Calendar.DAY_OF_YEAR, 30*window.getInt(VALUE_KEY));
                break;
            default:
                throw new IllegalArgumentException("Invalid unit specification for window " + window.getString(UNIT_KEY));
        }

        return calendar.getTime();
    }

    static Object evaluateOperand(JSONObject node, JSONObject properties) throws JSONException {
        if (!node.has(PROPERTY_KEY) || !node.has(VALUE_KEY)) {
            throw new IllegalArgumentException("Missing required keys: " + PROPERTY_KEY + "/" + VALUE_KEY);
        }

        switch (node.getString(PROPERTY_KEY)) {
            case EVENT_KEY:
                return properties.opt(node.getString(VALUE_KEY));
            case LITERAL_KEY:
                if (getType(node.get(VALUE_KEY)) == PropertyType.String &&
                        node.getString(VALUE_KEY).equalsIgnoreCase(NOW_LITERAL)) {
                        return new Date();
                }
                final Object value = node.get(VALUE_KEY);
                switch (getType(value)) {
                    case Object:
                        return evaluateWindow((JSONObject) value);
                    default:
                        return value;
                }
            default:
                throw new IllegalArgumentException("Invalid operand: Invalid property type: " + node.getString(PROPERTY_KEY));
        }
    }

    static Object evaluateOperator(JSONObject node, JSONObject properties) throws JSONException {
        if (!node.has(OPERATOR_KEY)) {
            throw new IllegalArgumentException("Missing required keys: " + OPERATOR_KEY);
        }

        switch (node.getString(OPERATOR_KEY)) {
            case AND_OPERATOR:
                return evaluateAnd(node, properties);
            case OR_OPERATOR:
                return evaluateOr(node, properties);
            case IN_OPERATOR:
            case NOT_IN_OPERATOR:
                return evaluateIn(node, properties);
            case PLUS_OPERATOR:
                return evaluatePlus(node, properties);
            case MINUS_OPERATOR:
            case MUL_OPERATOR:
            case DIV_OPERATOR:
            case MOD_OPERATOR:
                return evaluateArithmetic(node, properties);
            case EQUALS_OPERATOR:
            case NOT_EQUALS_OPERATOR:
                return evaluateEquality(node, properties);
            case GREATER_THAN_OPERATOR:
            case GREATER_THAN_EQUAL_OPERATOR:
            case LESS_THAN_OPERATOR:
            case LESS_THAN_EQUAL_OPERATOR:
                return evaluateComparison(node, properties);
            case BOOLEAN_OPERATOR:
                return evaluateBoolean(node, properties);
            case DATETIME_OPERATOR:
                return evaluateDateTime(node, properties);
            case LIST_OPERATOR:
                return evaluateList(node, properties);
            case NUMBER_OPERATOR:
                return evaluateNumber(node, properties);
            case STRING_OPERATOR:
                return evaluateString(node, properties);
            case DEFINED_OPERATOR:
            case NOT_DEFINED_OPERATOR:
                return evaluateDefined(node, properties);
            case NOT_OPERATOR:
                return evaluateNot(node, properties);
            default:
                throw new IllegalArgumentException("Unknown operator: " + node.getString(OPERATOR_KEY));
        }
    }

    private static Object evaluateNode(JSONObject node, JSONObject properties) throws JSONException {
        if (node.has(PROPERTY_KEY)) {
            return evaluateOperand(node, properties);
        }

        return evaluateOperator(node, properties);
    }

    public boolean evaluate(JSONObject properties) throws JSONException {
        return toBoolean(evaluateNode(mSelector, properties));
    }

    private final JSONObject mSelector;
    private static Calendar sCalendar; // For testing purposes only!

    static void setCalendar(Calendar calendar, boolean isTestMode) {
        if (isTestMode) {
            sCalendar = calendar;
        }
    }
}
