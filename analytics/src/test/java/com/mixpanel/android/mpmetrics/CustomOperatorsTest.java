package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import io.github.jamsesso.jsonlogic.JsonLogic;

/**
 * Local unit tests for the {@code semver_compare} and {@code datetime_compare} JsonLogic operators.
 * Each golden vector is driven through a JsonLogic instance with the custom operators registered.
 */
public class CustomOperatorsTest {

    // Epoch-millisecond constants (UTC instants) used as datetime targets, matching the UI format.
    private static final long JUL16_MS = 1_784_160_000_000L; // 2026-07-16T00:00:00Z
    private static final long JAN1_MS = 1_767_225_600_000L;  // 2026-01-01T00:00:00Z
    private static final long DEC31_MS = 1_798_675_200_000L; // 2026-12-31T00:00:00Z
    private static final long JUL16_END_MS = 1_784_246_399_999L; // 2026-07-16T23:59:59.999Z
    private static final long LEAP_DAY_MS = 1_709_164_800_000L; // 2024-02-29T00:00:00Z
    private static final long JUL16_INDIA_MS = 1_784_140_200_000L; // 2026-07-16T00:00:00+05:30
    private static final long JUL16_PACIFIC_MS = 1_784_188_800_000L; // 2026-07-16T00:00:00-08:00

    private JsonLogic jsonLogic;

    @Before
    public void setUp() {
        jsonLogic = new JsonLogic();
        CustomOperators.register(jsonLogic);
    }

    private boolean eval(String rule, Map<String, Object> data) throws Exception {
        return Boolean.TRUE.equals(jsonLogic.apply(rule, data));
    }

    private static JSONObject varNode(String key) throws JSONException {
        return new JSONObject().put("var", key);
    }

    private static String semverRule(String key, String sym, String target) throws JSONException {
        return new JSONObject().put("semver_compare",
                new JSONArray().put(varNode(key)).put(sym).put(target)).toString();
    }

    private static String datetimeRule(String key, String sym, long target) throws JSONException {
        return new JSONObject().put("datetime_compare",
                new JSONArray().put(varNode(key)).put(sym).put(target)).toString();
    }

    private static String datetimeRuleTarget(String key, String sym, Object target) throws JSONException {
        return new JSONObject().put("datetime_compare",
                new JSONArray().put(varNode(key)).put(sym).put(target)).toString();
    }

    private static String customBetween(String op, String key, String lo, String hi) throws JSONException {
        return new JSONObject().put("and", new JSONArray()
                .put(new JSONObject().put(op, new JSONArray().put(varNode(key)).put(">=").put(lo)))
                .put(new JSONObject().put(op, new JSONArray().put(varNode(key)).put("<=").put(hi))))
                .toString();
    }

    private static String datetimeBetween(String key, long lo, long hi) throws JSONException {
        return new JSONObject().put("and", new JSONArray()
                .put(new JSONObject().put("datetime_compare", new JSONArray().put(varNode(key)).put(">=").put(lo)))
                .put(new JSONObject().put("datetime_compare", new JSONArray().put(varNode(key)).put("<=").put(hi))))
                .toString();
    }

    private static Map<String, Object> data(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    @Test
    public void testSemverCompareOperator() throws Exception {
        assertEquals("is, equal", true,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "1.2.3")));
        assertEquals("is, not equal", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "1.2.4")));
        assertEquals("is not", true,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "1.2.4")));
        assertEquals("less than, patch", true,
                eval(semverRule("app_version", "<", "1.2.3"), data("app_version", "1.2.2")));
        assertEquals("less than, false", false,
                eval(semverRule("app_version", "<", "1.2.3"), data("app_version", "1.2.3")));
        assertEquals("less or equal, boundary", true,
                eval(semverRule("app_version", "<=", "1.2.3"), data("app_version", "1.2.3")));
        assertEquals("greater than, minor", true,
                eval(semverRule("app_version", ">", "1.2.3"), data("app_version", "1.3.0")));
        assertEquals("greater or equal, boundary", true,
                eval(semverRule("app_version", ">=", "1.2.3"), data("app_version", "1.2.3")));
        assertEquals("double-digit ordering (not lexical)", true,
                eval(semverRule("app_version", ">", "1.9.0"), data("app_version", "1.10.0")));
        assertEquals("prerelease precedes release", true,
                eval(semverRule("app_version", "<", "1.0.0"), data("app_version", "1.0.0-alpha")));
        assertEquals("lenient v-prefix", true,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "v1.2.3")));
        assertEquals("lenient uppercase V-prefix", true,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "V1.2.3")));
        assertEquals("v-prefix keeps prerelease", true,
                eval(semverRule("app_version", "<", "1.0.0"), data("app_version", "v1.0.0-alpha")));
        assertEquals("v-prefix, not equal", true,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "v1.2.4")));
        assertEquals("v-prefix, at or below", true,
                eval(semverRule("app_version", "<=", "1.2.3"), data("app_version", "v1.2.3")));
        assertEquals("v-prefix, greater", true,
                eval(semverRule("app_version", ">", "1.2.3"), data("app_version", "v1.2.4")));
        assertEquals("v-prefix, at or above", true,
                eval(semverRule("app_version", ">=", "1.2.3"), data("app_version", "v1.2.3")));
        assertEquals("lenient minor-only target", true,
                eval(semverRule("app_version", "=", "1.2"), data("app_version", "1.2.0")));
        // Every symbol is asserted in both directions.
        assertEquals("is not, equal", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "1.2.3")));
        assertEquals("less or equal, above", false,
                eval(semverRule("app_version", "<=", "1.2.3"), data("app_version", "1.2.4")));
        assertEquals("greater than, below", false,
                eval(semverRule("app_version", ">", "1.2.3"), data("app_version", "1.2.2")));
        assertEquals("greater or equal, below", false,
                eval(semverRule("app_version", ">=", "1.2.3"), data("app_version", "1.2.2")));
        // Prerelease precedence, SemVer 2.0.0 section 11.
        assertEquals("prerelease alpha before beta", true,
                eval(semverRule("app_version", "<", "1.0.0-beta"), data("app_version", "1.0.0-alpha")));
        assertEquals("prerelease beta before rc1", true,
                eval(semverRule("app_version", "<", "1.0.0-rc1"), data("app_version", "1.0.0-beta")));
        assertEquals("prerelease rc1 before rc2", true,
                eval(semverRule("app_version", "<", "1.0.0-rc2"), data("app_version", "1.0.0-rc1")));
        assertEquals("more prerelease fields wins", true,
                eval(semverRule("app_version", "<", "1.0.0-alpha.1"), data("app_version", "1.0.0-alpha")));
        assertEquals("numeric identifier below alphanumeric", true,
                eval(semverRule("app_version", "<", "1.0.0-alpha.beta"), data("app_version", "1.0.0-alpha.1")));
        assertEquals("fewer fields below alphanumeric", true,
                eval(semverRule("app_version", "<", "1.0.0-alpha.beta"), data("app_version", "1.0.0-alpha")));
        assertEquals("numeric identifiers compare numerically", true,
                eval(semverRule("app_version", "<", "1.0.0-beta.11"), data("app_version", "1.0.0-beta.2")));
        assertEquals("dotted identifier ordering, letters", true,
                eval(semverRule("app_version", "<", "1.0.0-b.1"), data("app_version", "1.0.0-a.1")));
        assertEquals("dotted identifier ordering, digits", true,
                eval(semverRule("app_version", "<", "1.0.0-a.2"), data("app_version", "1.0.0-a.1")));
        assertEquals("identical prereleases are equal", true,
                eval(semverRule("app_version", "=", "1.0.0-rc1"), data("app_version", "1.0.0-rc1")));
        assertEquals("rc1 outranks dotted rc.1", true,
                eval(semverRule("app_version", ">", "1.0.0-rc.1"), data("app_version", "1.0.0-rc1")));
        assertEquals("core version dominates prerelease", true,
                eval(semverRule("app_version", ">", "1.9.9"), data("app_version", "2.0.0-alpha")));
        // A release outranks its own prerelease, asserted from both sides and under every symbol.
        assertEquals("release outranks its prerelease", true,
                eval(semverRule("app_version", ">", "1.0.0-alpha"), data("app_version", "1.0.0")));
        assertEquals("release at or above its prerelease", true,
                eval(semverRule("app_version", ">=", "1.0.0-rc1"), data("app_version", "1.0.0")));
        assertEquals("release differs from its prerelease", true,
                eval(semverRule("app_version", "!=", "1.0.0-alpha"), data("app_version", "1.0.0")));
        assertEquals("prerelease differs from its release", true,
                eval(semverRule("app_version", "!=", "1.0.0"), data("app_version", "1.0.0-alpha")));
        assertEquals("prerelease at or below its release", true,
                eval(semverRule("app_version", "<=", "1.0.0"), data("app_version", "1.0.0-alpha")));
        assertEquals("prerelease of a higher core still wins", true,
                eval(semverRule("app_version", ">", "0.9.9"), data("app_version", "1.0.0-alpha")));
        assertEquals("prerelease below the next patch", true,
                eval(semverRule("app_version", "<", "1.0.1"), data("app_version", "1.0.0-rc1")));
        // Prerelease identifier comparison, SemVer 2.0.0 section 11.4.
        assertEquals("numeric identifiers are not compared lexically", true,
                eval(semverRule("app_version", "<", "1.0.0-10"), data("app_version", "1.0.0-2")));
        assertEquals("numeric identifier ranks below alphanumeric", true,
                eval(semverRule("app_version", "<", "1.0.0-alpha"), data("app_version", "1.0.0-1")));
        assertEquals("hyphen inside an identifier sorts by ascii", true,
                eval(semverRule("app_version", "<", "1.0.0-alpha-1"), data("app_version", "1.0.0-alpha")));
        assertEquals("beta ranks below rc", true,
                eval(semverRule("app_version", "<", "1.0.0-rc.1"), data("app_version", "1.0.0-beta.11")));
        assertEquals("last prerelease ranks below the release", true,
                eval(semverRule("app_version", "<", "1.0.0"), data("app_version", "1.0.0-rc.1")));
        // Build metadata carries no precedence.
        assertEquals("build metadata ignored", true,
                eval(semverRule("app_version", "=", "1.0.0+build2"), data("app_version", "1.0.0+build1")));
        assertEquals("build metadata ignored with prerelease", true,
                eval(semverRule("app_version", "=", "1.0.0-alpha"), data("app_version", "1.0.0-alpha+build")));
        assertEquals("build metadata with hyphen ignored", true,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "1.2.3+build.1-2")));
        // Ignored means equal, so every symbol has to agree with that.
        assertEquals("build metadata leaves versions equal", false,
                eval(semverRule("app_version", "!=", "1.0.0+build2"), data("app_version", "1.0.0+build1")));
        assertEquals("build metadata is not less", false,
                eval(semverRule("app_version", "<", "1.0.0+build2"), data("app_version", "1.0.0+build1")));
        assertEquals("build metadata is not greater", false,
                eval(semverRule("app_version", ">", "1.0.0+build2"), data("app_version", "1.0.0+build1")));
        assertEquals("build metadata at or below", true,
                eval(semverRule("app_version", "<=", "1.0.0+build2"), data("app_version", "1.0.0+build1")));
        assertEquals("build metadata at or above", true,
                eval(semverRule("app_version", ">=", "1.0.0+build2"), data("app_version", "1.0.0+build1")));
        assertEquals("build metadata does not block ordering", true,
                eval(semverRule("app_version", "<", "1.0.1+build1"), data("app_version", "1.0.0+build9")));
        assertEquals("build metadata does not block reverse ordering", true,
                eval(semverRule("app_version", ">", "1.0.0+build9"), data("app_version", "1.0.1+build1")));
        // Partial versions keep their prerelease once zero-padded.
        assertEquals("partial version with prerelease", true,
                eval(semverRule("app_version", "=", "1.2.0-alpha"), data("app_version", "1.2-alpha")));
        assertEquals("partial prerelease below later minor", true,
                eval(semverRule("app_version", "<", "1.3.1"), data("app_version", "1.2-alpha")));
        assertEquals("partial prerelease below its release", true,
                eval(semverRule("app_version", "<", "1.2.0"), data("app_version", "1.2-alpha")));
        assertEquals("major-only with prerelease", true,
                eval(semverRule("app_version", "<", "1.0.0"), data("app_version", "1-rc1")));
        // An empty prerelease is invalid, so it is rejected rather than treated as the bare release.
        assertEquals("empty prerelease, no match", false,
                eval(semverRule("app_version", "=", "1.0.0"), data("app_version", "1.0.0-")));
        assertEquals("empty prerelease, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.0.0"), data("app_version", "1.0.0-")));
        assertEquals("empty prerelease on partial version, no match", false,
                eval(semverRule("app_version", "=", "1.2.0"), data("app_version", "1.2-")));
        assertEquals("empty prerelease on partial version, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.0"), data("app_version", "1.2-")));
        // Hyphens are legal inside a prerelease identifier, so these are NOT empty prereleases.
        assertEquals("trailing hyphen inside identifier", true,
                eval(semverRule("app_version", "<", "1.0.0"), data("app_version", "1.0.0-alpha-")));
        // SemVer 2.0.0 forbids leading zeros in the core, so these are rejected rather than normalized.
        assertEquals("leading zero in major, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "01.2.3")));
        assertEquals("leading zero in major, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "01.2.3")));
        assertEquals("leading zero in minor, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "1.02.3")));
        assertEquals("leading zero in minor, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "1.02.3")));
        assertEquals("leading zero in patch, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "1.2.03")));
        assertEquals("leading zero in patch, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "1.2.03")));
        assertEquals("leading zeros throughout, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "01.02.03")));
        assertEquals("leading zeros throughout, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "01.02.03")));
        // A numeric prerelease identifier may not carry a leading zero either (section 9).
        assertEquals("numeric prerelease with leading zero, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "1.2.3-01")));
        assertEquals("numeric prerelease with leading zero, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "1.2.3-01")));
        assertEquals("dotted numeric prerelease with leading zero, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "1.2.3-rc.01")));
        assertEquals("dotted numeric prerelease with leading zero, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "1.2.3-rc.01")));
        // An alphanumeric identifier may contain digits, so this one stays valid.
        assertEquals("alphanumeric prerelease with digits", true,
                eval(semverRule("app_version", "<", "1.2.3"), data("app_version", "1.2.3-rc01")));
        assertEquals("between, inside", true,
                eval(customBetween("semver_compare", "app_version", "1.2.3", "2.0.0"), data("app_version", "1.5.0")));
        assertEquals("between, low boundary inclusive", true,
                eval(customBetween("semver_compare", "app_version", "1.2.3", "2.0.0"), data("app_version", "1.2.3")));
        assertEquals("between, high boundary inclusive", true,
                eval(customBetween("semver_compare", "app_version", "1.2.3", "2.0.0"), data("app_version", "2.0.0")));
        assertEquals("between, below", false,
                eval(customBetween("semver_compare", "app_version", "1.2.3", "2.0.0"), data("app_version", "1.0.0")));
        assertEquals("between, above", false,
                eval(customBetween("semver_compare", "app_version", "1.2.3", "2.0.0"), data("app_version", "2.0.1")));
        // A prerelease sits below its own release, which decides both boundary cases.
        assertEquals("between, prerelease inside", true,
                eval(customBetween("semver_compare", "app_version", "1.2.3", "2.0.0"), data("app_version", "1.5.0-rc1")));
        assertEquals("between, prerelease below the high bound", true,
                eval(customBetween("semver_compare", "app_version", "1.2.3", "2.0.0"), data("app_version", "2.0.0-rc1")));
        assertEquals("between, prerelease of the low bound falls out", false,
                eval(customBetween("semver_compare", "app_version", "1.2.3", "2.0.0"), data("app_version", "1.2.3-rc1")));
        assertEquals("between, invalid version", false,
                eval(customBetween("semver_compare", "app_version", "1.2.3", "2.0.0"), data("app_version", "not-a-version")));
        assertEquals("between, single-point range", true,
                eval(customBetween("semver_compare", "app_version", "1.2.3", "1.2.3"), data("app_version", "1.2.3")));
        assertEquals("invalid actual, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "not-a-version")));
        assertEquals("non-string actual, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", 123)));
        assertEquals("missing property, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), new HashMap<>()));
        // A malformed version must never be padded or coerced into a real one. Both symbols are
        // asserted so that "accepted at all" is observable rather than masked by a single false.
        assertEquals("empty version, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "")));
        assertEquals("empty version, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "")));
        assertEquals("bare v, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "v")));
        assertEquals("bare v, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "v")));
        assertEquals("leading separator, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "-1.2.3")));
        assertEquals("leading separator, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "-1.2.3")));
        assertEquals("trailing dot, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "1.")));
        assertEquals("trailing dot, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "1.")));
        assertEquals("trailing dot after patch, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "1.2.3.")));
        assertEquals("trailing dot after patch, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "1.2.3.")));
        assertEquals("empty middle segment, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "1..2")));
        assertEquals("empty middle segment, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "1..2")));
        assertEquals("four components, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "1.2.3.4")));
        assertEquals("four components, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "1.2.3.4")));
        assertEquals("range prefix, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "^1.2.3")));
        assertEquals("range prefix, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "^1.2.3")));
        assertEquals("version inside text, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "abc1.2.3")));
        assertEquals("version inside text, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "abc1.2.3")));
        assertEquals("empty build metadata, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "1.2.3+")));
        assertEquals("empty build metadata, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "1.2.3+")));
        assertEquals("empty prerelease identifier, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "1.2.3-alpha..1")));
        assertEquals("empty prerelease identifier, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "1.2.3-alpha..1")));
        assertEquals("lone dot prerelease, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "1.2.3-.")));
        assertEquals("lone dot prerelease, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "1.2.3-.")));
        assertEquals("underscore in prerelease, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "1.2.3-ALPHA_BETA")));
        assertEquals("underscore in prerelease, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "1.2.3-ALPHA_BETA")));
        assertEquals("doubled v-prefix, no match", false,
                eval(semverRule("app_version", "=", "1.2.3"), data("app_version", "vv1.2.3")));
        assertEquals("doubled v-prefix, not-equal also false", false,
                eval(semverRule("app_version", "!=", "1.2.3"), data("app_version", "vv1.2.3")));
    }

    @Test
    public void testDatetimeCompareOperator() throws Exception {
        // Asymmetric contract: subject (runtime var) is a strict RFC3339 string, target is epoch ms.
        assertEquals("before, true", true,
                eval(datetimeRule("signup", "<", JUL16_MS), data("signup", "2026-07-15T00:00:00Z")));
        assertEquals("before, false", false,
                eval(datetimeRule("signup", "<", JUL16_MS), data("signup", "2026-07-16T00:00:00Z")));
        assertEquals("on (equal), true", true,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T00:00:00Z")));
        assertEquals("not on, true", true,
                eval(datetimeRule("signup", "!=", JUL16_MS), data("signup", "2026-07-17T00:00:00Z")));
        assertEquals("since (>=), boundary", true,
                eval(datetimeRule("signup", ">=", JUL16_MS), data("signup", "2026-07-16T00:00:00Z")));
        assertEquals("after (>), true", true,
                eval(datetimeRule("signup", ">", JUL16_MS), data("signup", "2026-07-17T00:00:00Z")));
        assertEquals("after (>), false", false,
                eval(datetimeRule("signup", ">", JUL16_MS), data("signup", "2026-07-15T00:00:00Z")));
        // Every symbol is asserted in both directions.
        assertEquals("at or before, boundary", true,
                eval(datetimeRule("signup", "<=", JUL16_MS), data("signup", "2026-07-16T00:00:00Z")));
        assertEquals("at or before, after", false,
                eval(datetimeRule("signup", "<=", JUL16_MS), data("signup", "2026-07-17T00:00:00Z")));
        assertEquals("on (equal), false", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-17T00:00:00Z")));
        assertEquals("not on, equal", false,
                eval(datetimeRule("signup", "!=", JUL16_MS), data("signup", "2026-07-16T00:00:00Z")));
        assertEquals("since (>=), before", false,
                eval(datetimeRule("signup", ">=", JUL16_MS), data("signup", "2026-07-15T00:00:00Z")));
        assertEquals("between, inside", true,
                eval(datetimeBetween("signup", JAN1_MS, DEC31_MS), data("signup", "2026-06-15T00:00:00Z")));
        assertEquals("between, low boundary inclusive", true,
                eval(datetimeBetween("signup", JAN1_MS, DEC31_MS), data("signup", "2026-01-01T00:00:00Z")));
        assertEquals("between, high boundary inclusive", true,
                eval(datetimeBetween("signup", JAN1_MS, DEC31_MS), data("signup", "2026-12-31T00:00:00Z")));
        assertEquals("between, before range", false,
                eval(datetimeBetween("signup", JAN1_MS, DEC31_MS), data("signup", "2025-12-31T00:00:00Z")));
        assertEquals("between, after range", false,
                eval(datetimeBetween("signup", JAN1_MS, DEC31_MS), data("signup", "2027-01-01T00:00:00Z")));
        // A leap day is a real date.
        assertEquals("leap day", true,
                eval(datetimeRule("signup", "=", LEAP_DAY_MS), data("signup", "2024-02-29T00:00:00Z")));
        // Time-zone offsets change the instant.
        assertEquals("offset with half-hour minutes", true,
                eval(datetimeRule("signup", "=", JUL16_INDIA_MS), data("signup", "2026-07-16T00:00:00+05:30")));
        assertEquals("rfc3339 subject with offset", true,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T02:00:00+02:00")));
        assertEquals("positive offset precedes utc midnight", true,
                eval(datetimeRule("signup", "<", JUL16_MS), data("signup", "2026-07-16T00:00:00+05:30")));
        assertEquals("negative offset", true,
                eval(datetimeRule("signup", "=", JUL16_PACIFIC_MS), data("signup", "2026-07-16T00:00:00-08:00")));
        assertEquals("negative offset follows utc midnight", true,
                eval(datetimeRule("signup", ">", JUL16_MS), data("signup", "2026-07-16T00:00:00-08:00")));
        assertEquals("zero offset equals Z", true,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T00:00:00+00:00")));
        // Sub-second precision is dropped, on both sides. The end-of-day rows are the window the UI
        // emits for a single date, whose upper bound carries .999.
        assertEquals("one-digit fraction", true,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T00:00:00.5Z")));
        assertEquals("three-digit fraction", true,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T00:00:00.500Z")));
        assertEquals("six-digit fraction", true,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T00:00:00.123456Z")));
        assertEquals("nine-digit fraction", true,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T00:00:00.999999999Z")));
        assertEquals("zero fraction", true,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T00:00:00.0Z")));
        assertEquals("fractional seconds truncated", true,
                eval(datetimeRule("signup", ">=", JUL16_MS), data("signup", "2026-07-16T00:00:00.500Z")));
        assertEquals("end-of-day target drops its .999", true,
                eval(datetimeRule("signup", "=", JUL16_END_MS), data("signup", "2026-07-16T23:59:59Z")));
        assertEquals("end-of-day target is an inclusive bound", true,
                eval(datetimeRule("signup", "<=", JUL16_END_MS), data("signup", "2026-07-16T23:59:59Z")));
        assertEquals("end-of-day, fractional subject too", true,
                eval(datetimeRule("signup", "=", JUL16_END_MS), data("signup", "2026-07-16T23:59:59.999Z")));
        assertEquals("end-of-day inclusive, fractional subject", true,
                eval(datetimeRule("signup", "<=", JUL16_END_MS), data("signup", "2026-07-16T23:59:59.999Z")));
        // Fractional on both sides: the shape the UI actually round-trips.
        // Trimming and lowercasing.
        assertEquals("lowercased subject with fraction", true,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16t00:00:00.500z")));
        assertEquals("lowercased subject with offset", true,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16t02:00:00+02:00")));
        assertEquals("whitespace-padded subject", true,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", " 2026-07-16T00:00:00Z ")));
        assertEquals("lowercased rfc3339 subject", true,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16t00:00:00z")));
        // Shape violations, asserted under both = and != so that "accepted at all" is observable.
        // RFC 3339 also permits 24:00:00 as end-of-day. Platforms disagree on it, so no vector
        // asserts it either way.
        assertEquals("one-digit month, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-7-16T00:00:00Z")));
        assertEquals("one-digit month, not-equal also false", false,
                eval(datetimeRule("signup", "!=", JUL16_MS), data("signup", "2026-7-16T00:00:00Z")));
        assertEquals("space separator, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16 00:00:00Z")));
        assertEquals("space separator, not-equal also false", false,
                eval(datetimeRule("signup", "!=", JUL16_MS), data("signup", "2026-07-16 00:00:00Z")));
        assertEquals("missing zone, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T00:00:00")));
        assertEquals("missing zone, not-equal also false", false,
                eval(datetimeRule("signup", "!=", JUL16_MS), data("signup", "2026-07-16T00:00:00")));
        assertEquals("empty fraction, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T00:00:00.Z")));
        assertEquals("empty fraction, not-equal also false", false,
                eval(datetimeRule("signup", "!=", JUL16_MS), data("signup", "2026-07-16T00:00:00.Z")));
        assertEquals("offset without colon, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T00:00:00+0200")));
        assertEquals("offset without colon, not-equal also false", false,
                eval(datetimeRule("signup", "!=", JUL16_MS), data("signup", "2026-07-16T00:00:00+0200")));
        assertEquals("short offset, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T00:00:00+02")));
        assertEquals("short offset, not-equal also false", false,
                eval(datetimeRule("signup", "!=", JUL16_MS), data("signup", "2026-07-16T00:00:00+02")));
        assertEquals("trailing junk, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T00:00:00Zextra")));
        assertEquals("trailing junk, not-equal also false", false,
                eval(datetimeRule("signup", "!=", JUL16_MS), data("signup", "2026-07-16T00:00:00Zextra")));
        assertEquals("basic format, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "20260716T000000Z")));
        assertEquals("basic format, not-equal also false", false,
                eval(datetimeRule("signup", "!=", JUL16_MS), data("signup", "20260716T000000Z")));
        assertEquals("zone after lowercase z, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T00:00:00z00:00")));
        assertEquals("zone after lowercase z, not-equal also false", false,
                eval(datetimeRule("signup", "!=", JUL16_MS), data("signup", "2026-07-16T00:00:00z00:00")));
        assertEquals("comma fractional separator, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T00:00:00,5Z")));
        assertEquals("comma fractional separator, not-equal also false", false,
                eval(datetimeRule("signup", "!=", JUL16_MS), data("signup", "2026-07-16T00:00:00,5Z")));
        // This platform hand-rolls its RFC3339 parsing, so impossible field values are rejected here
        // rather than by a date library. Not shared vectors: other platforms roll these forward.
        assertEquals("impossible calendar date, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-02-30T00:00:00Z")));
        assertEquals("impossible calendar date, not-equal also false", false,
                eval(datetimeRule("signup", "!=", JUL16_MS), data("signup", "2026-02-30T00:00:00Z")));
        assertEquals("month 13, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-13-01T00:00:00Z")));
        assertEquals("hour 25, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T25:00:00Z")));
        assertEquals("offset minutes out of range, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T00:00:00+99:99")));
        assertEquals("offset hours out of range, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T00:00:00+99:00")));
        assertEquals("numeric subject, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", JUL16_MS)));
        assertEquals("negative epoch-ms target resolves to -1s", true,
                eval(datetimeRule("signup", "=", -1500L), data("signup", "1969-12-31T23:59:59Z")));
        assertEquals("negative epoch-ms target, not equal", false,
                eval(datetimeRule("signup", "!=", -1500L), data("signup", "1969-12-31T23:59:59Z")));
        assertEquals("negative epoch-ms target, at or after", true,
                eval(datetimeRule("signup", ">=", -1500L), data("signup", "1969-12-31T23:59:59Z")));
        assertEquals("negative epoch-ms target, before", true,
                eval(datetimeRule("signup", "<", -1500L), data("signup", "1969-12-31T23:59:58Z")));
        assertEquals("negative epoch-ms target, after", true,
                eval(datetimeRule("signup", ">", -2500L), data("signup", "1969-12-31T23:59:59Z")));
        assertEquals("subject floors, it does not truncate", true,
                eval(datetimeRule("signup", "=", -2000L), data("signup", "1969-12-31T23:59:58.500Z")));
        assertEquals("subject floors, not to -1s", true,
                eval(datetimeRule("signup", "!=", -1000L), data("signup", "1969-12-31T23:59:58.500Z")));
        assertEquals("target beyond representable range, no match", false,
                eval(datetimeRuleTarget("signup", "=", 1e308), data("signup", "2026-07-16T00:00:00Z")));
        assertEquals("target beyond representable range, greater-than also false", false,
                eval(datetimeRuleTarget("signup", ">", 1e308), data("signup", "2026-07-16T00:00:00Z")));
        assertEquals("target beyond representable range, less-than also false", false,
                eval(datetimeRuleTarget("signup", "<", 1e308), data("signup", "2026-07-16T00:00:00Z")));
        assertEquals("bare date subject, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16")));
        assertEquals("bare date subject, not-equal also false", false,
                eval(datetimeRule("signup", "!=", JUL16_MS), data("signup", "2026-07-16")));
        assertEquals("zoneless datetime subject, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "2026-07-16T00:00:00")));
        assertEquals("non-datetime string, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), data("signup", "yesterday")));
        assertEquals("missing property, no match", false,
                eval(datetimeRule("signup", "=", JUL16_MS), new HashMap<>()));
    }
}
