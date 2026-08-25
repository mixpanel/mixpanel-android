package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import io.github.jamsesso.jsonlogic.JsonLogic;

/**
 * Local unit tests for the {@code semver_compare} and {@code datetime_compare} JsonLogic operators.
 * The golden vectors are the cross-SDK contract; the canonical copy and its README live in the
 * analytics monorepo.
 *
 * <p>Each case is a {@code [subject, operator, target, expected]} row that the test wraps in a rule
 * and an event, then drives through a JsonLogic instance with the custom operators registered, so
 * registration is covered alongside the comparison itself.
 */
@RunWith(Parameterized.class)
public class CustomOperatorsTest {

    /**
     * The property key the vectors are evaluated against. It is plumbing the test supplies, so any
     * name works as long as the rule and the event agree on it.
     */
    private static final String VECTOR_KEY = "value";

    private final String name;
    private final String rule;
    private final Map<String, Object> data;
    private final boolean want;

    public CustomOperatorsTest(String name, String rule, Map<String, Object> data, boolean want) {
        this.name = name;
        this.rule = rule;
        this.data = data;
        this.want = want;
    }

    /** Reads a golden-vector file. String entries are headings, array entries are cases. */
    private static void loadVectors(String operator, List<Object[]> cases) {
        String fileName = operator + "_compare_tests.json";
        JSONArray entries = new JSONArray(readResource(fileName));

        String section = "";
        for (int i = 0; i < entries.length(); i++) {
            Object entry = entries.get(i);
            if (entry instanceof String) {
                section = (String) entry;
                continue;
            }

            JSONArray row = (JSONArray) entry;
            String symbol = row.getString(1);
            Object target = row.get(2);

            JSONObject rule = new JSONObject().put(
                    operator + "_compare",
                    new JSONArray().put(new JSONObject().put("var", VECTOR_KEY)).put(symbol).put(target));

            // A null subject means the property is not set. org.json decodes JSON null to the
            // JSONObject.NULL sentinel rather than a Java null, so isNull is the only correct test.
            Map<String, Object> data = new HashMap<>();
            if (!row.isNull(0)) {
                data.put(VECTOR_KEY, row.get(0));
            }

            String name = i + " " + section + ": " + row.get(0) + " " + symbol + " " + target;
            cases.add(new Object[] {name, rule.toString(), data, row.getBoolean(3)});
        }
    }

    private static String readResource(String fileName) {
        InputStream stream = CustomOperatorsTest.class.getClassLoader().getResourceAsStream(fileName);
        assertNotNull(fileName + " is missing from the test resources", stream);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
             Scanner scanner = new Scanner(reader).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read " + fileName, e);
        }
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> cases() {
        List<Object[]> all = new ArrayList<>();
        loadVectors("semver", all);
        loadVectors("datetime", all);
        return all;
    }

    @Test
    public void goldenVector() throws Exception {
        // An absent property and one holding a null both fail closed, so the expectation alone
        // cannot tell them apart; pin the spelling the vectors mean.
        assertFalse(name + " must omit an unset property rather than store a null",
                data.containsValue(JSONObject.NULL));

        JsonLogic jsonLogic = new JsonLogic();
        CustomOperators.register(jsonLogic);
        assertEquals(name, want, Boolean.TRUE.equals(jsonLogic.apply(rule, data)));
    }
}
