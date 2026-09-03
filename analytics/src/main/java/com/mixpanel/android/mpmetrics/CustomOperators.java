package com.mixpanel.android.mpmetrics;

import java.util.List;

import com.mixpanel.android.customoperators.Rfc3339;
import com.mixpanel.android.customoperators.SemanticVersion;

import io.github.jamsesso.jsonlogic.JsonLogic;
import io.github.jamsesso.jsonlogic.evaluator.expressions.PreEvaluatedArgumentsExpression;

/**
 * Binds the semver and datetime custom operators to the JsonLogic engine. The comparison itself
 * lives in the common module so it can back either engine.
 */
final class CustomOperators {

    private CustomOperators() {}

    // Registers custom operators into the JSONLogic engine.
    // 1. Semantic Versioning 2.0.0 comparison
    // 2. RFC3339 datetime comparison
    static void register(JsonLogic jsonLogic) {
        jsonLogic.addOperation(new PreEvaluatedArgumentsExpression() {
            @Override
            public String key() {
                return "semver_compare";
            }

            @Override
            public Object evaluate(List arguments, Object data, String jsonPath) {
                return semverCompare(arguments);
            }
        });
        jsonLogic.addOperation(new PreEvaluatedArgumentsExpression() {
            @Override
            public String key() {
                return "datetime_compare";
            }

            @Override
            public Object evaluate(List arguments, Object data, String jsonPath) {
                return datetimeCompare(arguments);
            }
        });
    }

    // Implements a custom operation for semantic versioning comparison that conforms to the semver
    // 2.0.0 standard. Prior to comparison, any leading version prefix is stripped.
    private static Object semverCompare(List<?> args) {
        if (args.size() != 3) {
            return false;
        }
        if (!(args.get(1) instanceof String)) {
            return false;
        }
        if (!(args.get(0) instanceof String) || !(args.get(2) instanceof String)) {
            return false;
        }
        Integer cmp = SemanticVersion.compare((String) args.get(0), (String) args.get(2));
        if (cmp == null) {
            return false;
        }
        return comparatorMatches(cmp, (String) args.get(1));
    }

    // Implements a custom operation for datetime comparison. The target value stored on the feature
    // flag is the millisecond epoch, whereas the actual value provided at evaluation time must be
    // RFC-3339 formatted.
    private static Object datetimeCompare(List<?> args) {
        if (args.size() != 3) {
            return false;
        }
        if (!(args.get(1) instanceof String)) {
            return false;
        }
        Long actual = convertRfc3339ToUnixSeconds(args.get(0));
        Long target = convertUnixMillisecondsToSeconds(args.get(2));
        if (actual == null || target == null) {
            return false;
        }
        long cmp = actual - target;
        return comparatorMatches(cmp, (String) args.get(1));
    }

    private static boolean comparatorMatches(long cmp, String symbol) {
        switch (symbol) {
            case "===":
                return cmp == 0;
            case "!==":
                return cmp != 0;
            case "<":
                return cmp < 0;
            case "<=":
                return cmp <= 0;
            case ">":
                return cmp > 0;
            case ">=":
                return cmp >= 0;
            default:
                return false;
        }
    }

    private static Long convertRfc3339ToUnixSeconds(Object value) {
        if (value instanceof String) {
            return Rfc3339.toUnixSeconds((String) value);
        }
        return null;
    }

    private static Long convertUnixMillisecondsToSeconds(Object value) {
        if (!(value instanceof Number)) {
            return null;
        }
        return Rfc3339.epochMillisToUnixSeconds(((Number) value).doubleValue());
    }
}
