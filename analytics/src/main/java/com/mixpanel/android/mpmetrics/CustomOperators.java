package com.mixpanel.android.mpmetrics;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.semver4j.Semver;

import io.github.jamsesso.jsonlogic.JsonLogic;
import io.github.jamsesso.jsonlogic.evaluator.expressions.PreEvaluatedArgumentsExpression;

final class CustomOperators {

    // Using the official semantic versioning 2.0.0 regular expression to handle cross-platform validation
    // differences on other SDK's. For example, some platforms allow leading zeros even though it is not valid
    // as part of the Semver 2.0.0 spec. See https://semver.org/
    private static final Pattern SEMVER = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?"
                    + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");

    // Strict RFC3339 guard for datetime strings.
    private static final Pattern RFC3339 = Pattern.compile(
            "^(\\d{4})-(\\d{2})-(\\d{2})[Tt](\\d{2}):(\\d{2}):(\\d{2})(\\.\\d+)?([Zz]|[+-]\\d{2}:\\d{2})$");

    // SemVer 2.0.0 requires major.minor.patch; partial versions are zero-padded to this.
    private static final int SEMVER_PARTS = 3;

    // Epoch milliseconds are compared as a long, so anything at or beyond this is out of range.
    private static final double MAX_EPOCH_MS = (double) Long.MAX_VALUE;

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
        String actualVersion = normalizeSemver((String) args.get(0));
        String targetVersion = normalizeSemver((String) args.get(2));
        if (!SEMVER.matcher(actualVersion).matches() || !SEMVER.matcher(targetVersion).matches()) {
            return false;
        }
        Semver actual = Semver.parse(actualVersion);
        Semver target = Semver.parse(targetVersion);
        if (actual == null || target == null) {
            return false;
        }
        long cmp = actual.compareTo(target);
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

    private static String normalizeSemver(String raw) {
        String stripped = raw.trim();
        if (stripped.startsWith("v") || stripped.startsWith("V")) {
            stripped = stripped.substring(1);
        }

        int suffixStart = stripped.length();
        for (String separator : new String[] {"-", "+"}) {
            int index = stripped.indexOf(separator);
            if (index != -1 && index < suffixStart) {
                suffixStart = index;
            }
        }

        String core = stripped.substring(0, suffixStart);
        String suffix = stripped.substring(suffixStart);

        String[] segments = core.split("\\.", -1);
        // Reject anything that is not 1-3 all-digit segments, so an empty or malformed core is never
        // padded into a real version such as "0.0.0".
        if (core.isEmpty() || segments.length > SEMVER_PARTS) {
            return stripped;
        }
        for (String segment : segments) {
            if (segment.isEmpty() || !isAllDigits(segment)) {
                return stripped;
            }
        }
        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < SEMVER_PARTS; i++) {
            if (i > 0) {
                normalized.append('.');
            }
            normalized.append(i < segments.length ? segments[i] : "0");
        }
        return normalized + suffix;
    }

    private static boolean isAllDigits(String segment) {
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean comparatorMatches(long cmp, String symbol) {
        switch (symbol) {
            case "=":
                return cmp == 0;
            case "!=":
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
            return rfc3339ToUnixSeconds((String) value);
        }
        return null;
    }

    private static Long convertUnixMillisecondsToSeconds(Object value) {
        if (!(value instanceof Number)) {
            return null;
        }
        double millis = ((Number) value).doubleValue();
        // A value long cannot represent is not a real timestamp; narrowing one would saturate into a
        // finite bound and let a nonsense target define a rollout window.
        if (Double.isNaN(millis) || millis >= MAX_EPOCH_MS || millis <= -MAX_EPOCH_MS) {
            return null;
        }
        return (long) millis / 1000L;
    }

    private static Long rfc3339ToUnixSeconds(String raw) {
        String normalized = raw.trim().toUpperCase();
        Matcher m = RFC3339.matcher(normalized);
        if (!m.matches()) {
            return null;
        }
        int year = Integer.parseInt(m.group(1));
        int month = Integer.parseInt(m.group(2));
        int day = Integer.parseInt(m.group(3));
        int hour = Integer.parseInt(m.group(4));
        int minute = Integer.parseInt(m.group(5));
        int second = Integer.parseInt(m.group(6));

        long offsetSeconds = 0L;
        String offset = m.group(8);
        if (!"Z".equals(offset)) {
            int offHours = Integer.parseInt(offset.substring(1, 3));
            int offMinutes = Integer.parseInt(offset.substring(4, 6));
            // The pattern only guarantees two digits either side of the colon, so the values still have
            // to be real clock offsets.
            if (offHours > 23 || offMinutes > 59) {
                return null;
            }
            int sign = offset.charAt(0) == '-' ? -1 : 1;
            offsetSeconds = sign * (offHours * 3600L + offMinutes * 60L);
        }

        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        cal.clear();
        // A lenient calendar rolls an impossible date such as 2026-02-30 forward into a real instant, so
        // a malformed property would match a date rule instead of failing closed.
        cal.setLenient(false);
        cal.set(year, month - 1, day, hour, minute, second);
        long wallSeconds;
        try {
            wallSeconds = cal.getTimeInMillis() / 1000L;
        } catch (IllegalArgumentException e) {
            return null;
        }
        return wallSeconds - offsetSeconds;
    }
}
