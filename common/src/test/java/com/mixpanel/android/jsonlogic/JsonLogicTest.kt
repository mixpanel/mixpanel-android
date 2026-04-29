package com.mixpanel.android.jsonlogic

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Comprehensive test suite for JsonLogic implementation.
 * Loads test cases from tests.json.
 */
@RunWith(Parameterized::class)
class JsonLogicTest(
    private val testName: String,
    private val ruleJson: String,
    private val dataJson: String,
    private val expectedJson: String
) {

    companion object {
        private val TEST_FILES = listOf("tests.json")

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> {
            val testCases = mutableListOf<Array<Any>>()

            for (testFile in TEST_FILES) {
                loadTestsFromFile(testFile, testCases)
            }

            return testCases
        }

        private fun loadTestsFromFile(filename: String, testCases: MutableList<Array<Any>>) {
            var currentSection = filename.removeSuffix(".json")

            val inputStream = JsonLogicTest::class.java.classLoader?.getResourceAsStream(filename)
                ?: error("$filename not found in test resources")

            val content = inputStream.bufferedReader().use { it.readText() }
            val tests = JSONArray(content)

            for (i in 0 until tests.length()) {
                val item = tests.get(i)

                // Skip string entries (they are section comments)
                if (item is String) {
                    val sectionName = item.removePrefix("# ").trim()
                    // Skip divider lines (lines with only = characters)
                    if (sectionName.isNotEmpty() && !sectionName.all { it == '=' }) {
                        currentSection = sectionName
                    }
                    continue
                }

                if (item is JSONArray && item.length() >= 3) {
                    val rule = item.get(0)
                    val data = item.get(1)
                    val expected = item.get(2)

                    val testName = "file: $filename, group: $currentSection, rule: ${toJsonString(rule)}, result: ${toJsonString(expected)}"
                    testCases.add(
                        arrayOf(
                            testName,
                            toJsonString(rule),
                            toJsonString(data),
                            toJsonString(expected)
                        )
                    )
                }
            }
        }

        private fun toJsonString(value: Any?): String {
            return when (value) {
                null, JSONObject.NULL -> "null"
                is String -> "\"$value\""
                is JSONObject -> value.toString()
                is JSONArray -> value.toString()
                else -> value.toString()
            }
        }
    }

    @Test
    fun testJsonLogicRule() {
        val rule = JsonLogicParser.parse(ruleJson)
        val data = parseData(dataJson)
        val result = JsonLogicEvaluator.evaluate(rule, data)
        val expected = parseExpected(expectedJson)

        assertTrue(
            "Expected: $expected (${expected?.let { it::class.simpleName }}), " +
                "Got: $result (${result?.let { it::class.simpleName }})",
            valuesEqual(result, expected)
        )
    }

    private fun parseData(json: String): JSONObject {
        require(json.startsWith("{")) { "Data must be a JSON object, got: $json" }
        return JSONObject(json)
    }

    private fun parseExpected(json: String): Any? {
        return when {
            json == "null" -> null
            json == "true" -> true
            json == "false" -> false
            json.startsWith("\"") -> json.removeSurrounding("\"")
            json.startsWith("{") -> JSONObject(json)
            json.startsWith("[") -> parseJsonArray(JSONArray(json))
            else -> parseNumber(json)
        }
    }

    private fun parseJsonArray(arr: JSONArray): List<Any?> {
        return (0 until arr.length()).map { i ->
            val item = arr.get(i)
            when (item) {
                JSONObject.NULL -> null
                is JSONArray -> parseJsonArray(item)
                else -> item
            }
        }
    }

    private fun parseNumber(s: String): Number {
        return if (s.contains('.')) s.toDouble() else s.toLong()
    }

    private fun valuesEqual(actual: Any?, expected: Any?): Boolean {
        if (actual == null && expected == null) return true
        if (actual == null || expected == null) return false

        // Compare numbers with tolerance
        if (actual is Number && expected is Number) {
            val diff = kotlin.math.abs(actual.toDouble() - expected.toDouble())
            return diff < 0.0001
        }

        // Compare booleans
        if (actual is Boolean && expected is Boolean) {
            return actual == expected
        }

        // Compare strings
        if (actual is String && expected is String) {
            return actual == expected
        }

        // Compare lists
        if (actual is List<*> && expected is List<*>) {
            if (actual.size != expected.size) return false
            return actual.zip(expected).all { (a, e) -> valuesEqual(a, e) }
        }

        // Compare JSONArray to List
        if (actual is JSONArray && expected is List<*>) {
            if (actual.length() != expected.size) return false
            return expected.indices.all { i ->
                valuesEqual(normalizeJsonValue(actual.get(i)), expected[i])
            }
        }

        // Compare maps/objects
        if (actual is Map<*, *> && expected is JSONObject) {
            if (actual.size != expected.length()) return false
            return actual.all { (k, v) ->
                expected.has(k.toString()) && valuesEqual(v, normalizeJsonValue(expected.get(k.toString())))
            }
        }

        return actual == expected
    }

    private fun normalizeJsonValue(value: Any?): Any? {
        return when (value) {
            JSONObject.NULL -> null
            is JSONArray -> parseJsonArray(value)
            else -> value
        }
    }
}
