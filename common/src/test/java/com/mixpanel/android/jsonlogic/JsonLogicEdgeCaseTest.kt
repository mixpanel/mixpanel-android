package com.mixpanel.android.jsonlogic

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Edge case tests for JsonLogic implementation.
 * Tests scenarios not covered by the official test suite.
 *
 * Supported operators: ===, !==, <, <=, >, >=, in, var, and, or
 */
class JsonLogicEdgeCaseTest {

    @Test(expected = InvalidExpressionException::class)
    fun `var - throws for dot in property name`() {
        val data = JSONObject("""{"user":{"name":"John"}}""")
        val rule = JsonLogicParser.parse("""{"var":"user.name"}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = InvalidExpressionException::class)
    fun `var - throws for multiple dots in property name`() {
        val data = JSONObject("""{"a":{"b":{"c":42}}}""")
        val rule = JsonLogicParser.parse("""{"var":"a.b.c"}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test
    fun `var - accesses property with numeric string key`() {
        val data = JSONObject("""{"0":"first","1":"sechel, ond"}""")
        val rule = JsonLogicParser.parse("""{"var":"0"}""")
        val result = JsonLogicEvaluator.evaluate(rule, data)
        assertEquals("first", result)
    }

    @Test
    fun `var - accesses property with dollar sign in name`() {
        val data = JSONObject("""{"${"$"}tier":"premium"}""")
        val rule = JsonLogicParser.parse("""{"var":"${"$"}tier"}""")
        val result = JsonLogicEvaluator.evaluate(rule, data)
        assertEquals("premium", result)
    }

    @Test(expected = InvalidExpressionException::class)
    fun `var - throws for default value syntax`() {
        JsonLogicParser.parse("""{"var": ["missing", 0]}""")
    }

    @Test(expected = InvalidExpressionException::class)
    fun `var - throws for empty path`() {
        val data = JSONObject("""{"a":1}""")
        val rule = JsonLogicParser.parse("""{"var":""}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = InvalidExpressionException::class)
    fun `var - throws for null path`() {
        val data = JSONObject("""{"a":1}""")
        val rule = JsonLogicParser.parse("""{"var":null}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = InvalidExpressionException::class)
    fun `var - throws for empty array path`() {
        val data = JSONObject("""{"a":1}""")
        val rule = JsonLogicParser.parse("""{"var":[]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    // =========================================================================
    // 'in' operator - string needle only, array must contain only strings
    // =========================================================================

    @Test
    fun `in - matches string in array`() {
        val data = JSONObject("""{"tier":"b"}""")
        val rule = JsonLogicParser.parse("""{"in": [{"var": "tier"}, ["a", "b", "c"]]}""")
        val result = JsonLogicEvaluator.evaluate(rule, data)
        assertEquals(true, result)
    }

    @Test
    fun `in - returns false when string not in array`() {
        val data = JSONObject("""{"tier":"x"}""")
        val rule = JsonLogicParser.parse("""{"in": [{"var": "tier"}, ["a", "b", "c"]]}""")
        val result = JsonLogicEvaluator.evaluate(rule, data)
        assertEquals(false, result)
    }

    @Test(expected = TypeMismatchException::class)
    fun `in - throws when array contains non-string elements`() {
        val data = JSONObject("""{"tier":"1"}""")
        val rule = JsonLogicParser.parse("""{"in": [{"var": "tier"}, [1, 2, 3]]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `in - throws when array contains mixed types`() {
        val data = JSONObject("""{"tier":"x"}""")
        val rule = JsonLogicParser.parse("""{"in": [{"var": "tier"}, ["a", 1, "b"]]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `in - throws when array contains null`() {
        val data = JSONObject("""{"tier":"a"}""")
        val rule = JsonLogicParser.parse("""{"in": [{"var": "tier"}, ["a", null]]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test
    fun `in - returns false for empty array`() {
        val data = JSONObject("""{"tier":"a"}""")
        val rule = JsonLogicParser.parse("""{"in": [{"var": "tier"}, []]}""")
        val result = JsonLogicEvaluator.evaluate(rule, data)
        assertEquals(false, result)
    }

    @Test
    fun `in - matches substring in string`() {
        val data = JSONObject("""{"city":"Louisville"}""")
        val rule = JsonLogicParser.parse("""{"in": ["Lou", {"var": "city"}]}""")
        val result = JsonLogicEvaluator.evaluate(rule, data)
        assertEquals(true, result)
    }

    @Test
    fun `in - returns false when substring not in string`() {
        val data = JSONObject("""{"city":"Louisville"}""")
        val rule = JsonLogicParser.parse("""{"in": ["xyz", {"var": "city"}]}""")
        val result = JsonLogicEvaluator.evaluate(rule, data)
        assertEquals(false, result)
    }

    @Test(expected = TypeMismatchException::class)
    fun `in - throws for number needle`() {
        val data = JSONObject("""{"id":2}""")
        val rule = JsonLogicParser.parse("""{"in": [{"var": "id"}, ["1", "2", "3"]]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `in - throws for boolean needle`() {
        val data = JSONObject("""{"active":true}""")
        val rule = JsonLogicParser.parse("""{"in": [{"var": "active"}, ["true", "false"]]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `in - throws for null needle`() {
        val data = JSONObject("""{"value":null}""")
        val rule = JsonLogicParser.parse("""{"in": [{"var": "value"}, ["a", "b"]]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    // =========================================================================
    // Strict equality - operands must be the same type
    // =========================================================================

    @Test
    fun `=== - returns true for matching nulls`() {
        val data = JSONObject("""{"value":null}""")
        val rule = JsonLogicParser.parse("""{"===": [{"var": "value"}, null]}""")
        val result = JsonLogicEvaluator.evaluate(rule, data)
        assertEquals(true, result)
    }

    @Test
    fun `=== - returns true for matching numbers`() {
        val data = JSONObject("""{"count":42}""")
        val rule = JsonLogicParser.parse("""{"===": [{"var": "count"}, 42]}""")
        val result = JsonLogicEvaluator.evaluate(rule, data)
        assertEquals(true, result)
    }

    @Test
    fun `!== - returns false for matching numbers`() {
        val data = JSONObject("""{"count":1}""")
        val rule = JsonLogicParser.parse("""{"!==": [{"var": "count"}, 1]}""")
        val result = JsonLogicEvaluator.evaluate(rule, data)
        assertEquals(false, result)
    }

    @Test
    fun `!== - returns true for different strings`() {
        val data = JSONObject("""{"greeting":"hello"}""")
        val rule = JsonLogicParser.parse("""{"!==": [{"var": "greeting"}, "world"]}""")
        val result = JsonLogicEvaluator.evaluate(rule, data)
        assertEquals(true, result)
    }

    @Test(expected = TypeMismatchException::class)
    fun `=== - throws for number vs string`() {
        val data = JSONObject("""{"count":1}""")
        val rule = JsonLogicParser.parse("""{"===": [{"var": "count"}, "1"]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `=== - throws for boolean vs string`() {
        val data = JSONObject("""{"active":true}""")
        val rule = JsonLogicParser.parse("""{"===": [{"var": "active"}, "true"]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `=== - throws for boolean vs number`() {
        val data = JSONObject("""{"active":true}""")
        val rule = JsonLogicParser.parse("""{"===": [{"var": "active"}, 1]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `=== - throws for null vs number`() {
        val data = JSONObject("""{"value":null}""")
        val rule = JsonLogicParser.parse("""{"===": [{"var": "value"}, 0]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `=== - throws for null vs string`() {
        val data = JSONObject("""{"value":null}""")
        val rule = JsonLogicParser.parse("""{"===": [{"var": "value"}, ""]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `!== - throws for number vs string`() {
        val data = JSONObject("""{"count":1}""")
        val rule = JsonLogicParser.parse("""{"!==": [{"var": "count"}, "1"]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `!== - throws for null vs number`() {
        val data = JSONObject("""{"value":null}""")
        val rule = JsonLogicParser.parse("""{"!==": [{"var": "value"}, 0]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `!== - throws for boolean vs string`() {
        val data = JSONObject("""{"active":true}""")
        val rule = JsonLogicParser.parse("""{"!==": [{"var": "active"}, "true"]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    // =========================================================================
    // Array comparison is not supported
    // =========================================================================

    @Test(expected = TypeMismatchException::class)
    fun `=== - throws for array comparison`() {
        val data = JSONObject("""{"list":[1]}""")
        val rule = JsonLogicParser.parse("""{"===": [{"var": "list"}, [1]]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    // =========================================================================
    // Complex rule tests
    // =========================================================================

    @Test
    fun `and - complex rule with nested operations`() {
        val data = JSONObject("""{"age":25,"premium":true}""")
        val rule = JsonLogicParser.parse(
            """{"and": [{">=": [{"var": "age"}, 18]}, {"var": "premium"}]}"""
        )
        val result = JsonLogicEvaluator.evaluate(rule, data)
        assertEquals(true, result)
    }

    @Test
    fun `and - returns true when all operands true`() {
        val data = JSONObject("""{"a":1,"b":2}""")
        val rule = JsonLogicParser.parse("""{"and": [{"===": [{"var": "a"}, 1]}, {"===": [{"var": "b"}, 2]}]}""")
        assertEquals(true, JsonLogicEvaluator.evaluate(rule, data))
    }

    @Test
    fun `and - returns false when any operand false`() {
        val data = JSONObject("""{"a":1,"b":2}""")
        val rule = JsonLogicParser.parse("""{"and": [{"===": [{"var": "a"}, 1]}, {"===": [{"var": "b"}, 3]}]}""")
        assertEquals(false, JsonLogicEvaluator.evaluate(rule, data))
    }

    @Test
    fun `or - returns true when any operand true`() {
        val data = JSONObject("""{"a":1,"b":2}""")
        val rule = JsonLogicParser.parse("""{"or": [{"===": [{"var": "a"}, 9]}, {"===": [{"var": "b"}, 2]}]}""")
        assertEquals(true, JsonLogicEvaluator.evaluate(rule, data))
    }

    @Test
    fun `or - returns false when all operands false`() {
        val data = JSONObject("""{"a":1,"b":2}""")
        val rule = JsonLogicParser.parse("""{"or": [{"===": [{"var": "a"}, 9]}, {"===": [{"var": "b"}, 9]}]}""")
        assertEquals(false, JsonLogicEvaluator.evaluate(rule, data))
    }

    @Test(expected = TypeMismatchException::class)
    fun `and - throws for number literal operand`() {
        val data = JSONObject("""{"active":true}""")
        val rule = JsonLogicParser.parse("""{"and": [{"var": "active"}, 1]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `or - throws for string literal operand`() {
        val data = JSONObject("""{"active":false}""")
        val rule = JsonLogicParser.parse("""{"or": [{"var": "active"}, "hello"]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `and - throws for var returning non-boolean`() {
        val data = JSONObject("""{"active":true,"count":5}""")
        val rule = JsonLogicParser.parse("""{"and": [{"var": "active"}, {"var": "count"}]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `or - throws for null operand`() {
        val data = JSONObject("""{"active":false}""")
        val rule = JsonLogicParser.parse("""{"or": [{"var": "active"}, null]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = InvalidExpressionException::class)
    fun `and - throws for empty operands`() {
        val rule = JsonLogicParser.parse("""{"and": []}""")
        JsonLogicEvaluator.evaluate(rule, JSONObject("{}"))
    }

    @Test(expected = InvalidExpressionException::class)
    fun `or - throws for empty operands`() {
        val rule = JsonLogicParser.parse("""{"or": []}""")
        JsonLogicEvaluator.evaluate(rule, JSONObject("{}"))
    }

    @Test(expected = InvalidExpressionException::class)
    fun `less than - throws for 3 args`() {
        // 3-arg between checks not supported - only 2-arg comparisons allowed
        JsonLogicParser.parse("""{"<": [1, 5, 10]}""")
    }

    @Test(expected = InvalidExpressionException::class)
    fun `less than or equal - throws for 3 args`() {
        // 3-arg between checks not supported - only 2-arg comparisons allowed
        JsonLogicParser.parse("""{"<=": [1, 1, 10]}""")
    }

    // =========================================================================
    // Comparison operators only support numbers
    // =========================================================================

    @Test(expected = TypeMismatchException::class)
    fun `greater than - throws for string operand`() {
        val data = JSONObject("""{"age":"10"}""")
        val rule = JsonLogicParser.parse("""{">": [{"var": "age"}, 5]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `less than - throws for string operand`() {
        val data = JSONObject("""{"age":5}""")
        val rule = JsonLogicParser.parse("""{"<": [{"var": "age"}, "10"]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `greater than or equal - throws for string operands`() {
        val data = JSONObject("""{"name":"abc"}""")
        val rule = JsonLogicParser.parse("""{">=": [{"var": "name"}, "def"]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `less than or equal - throws for string operands`() {
        val data = JSONObject("""{"value":"1"}""")
        val rule = JsonLogicParser.parse("""{"<=": [{"var": "value"}, "2"]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `greater than - throws for null operand`() {
        val data = JSONObject("""{"value":null}""")
        val rule = JsonLogicParser.parse("""{">": [{"var": "value"}, 5]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    @Test(expected = TypeMismatchException::class)
    fun `less than - throws for null operand`() {
        val data = JSONObject("""{"age":5,"limit":null}""")
        val rule = JsonLogicParser.parse("""{"<": [{"var": "age"}, {"var": "limit"}]}""")
        JsonLogicEvaluator.evaluate(rule, data)
    }

    // =========================================================================
    // Unsupported Operators - These tests prevent accidental reintroduction
    // =========================================================================
    //
    // Per product decision (see docs/OPERATOR_REDUCTION_PLAN.md), only 10 operators
    // are supported: ===, !==, <, <=, >, >=, in, var, and, or
    //
    // The following 21 operators were intentionally removed to align with server
    // capabilities and avoid implementation divergence across platforms.
    // These tests ensure they remain unsupported.
    //
    // Decision thread: https://mixpanel.slack.com/archives/C0AT05NLRAL/p1776180631331609
    // =========================================================================

    // --- Comparison operators (loose equality removed, strict equality kept) ---

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - loose equals throws`() {
        JsonLogicParser.parse("""{"==":[1, "1"]}""")
    }

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - loose not equals throws`() {
        JsonLogicParser.parse("""{"!=":[1, 2]}""")
    }

    // --- Logic operators (only and/or kept) ---

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - not operator throws`() {
        JsonLogicParser.parse("""{"!":[true]}""")
    }

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - double bang throws`() {
        JsonLogicParser.parse("""{"!!":[1]}""")
    }

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - if operator throws`() {
        JsonLogicParser.parse("""{"if":[true, 1, 2]}""")
    }

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - ternary operator throws`() {
        JsonLogicParser.parse("""{"?:":[true, 1, 2]}""")
    }

    // --- Arithmetic operators (all removed) ---

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - addition throws`() {
        JsonLogicParser.parse("""{"+":[1, 2]}""")
    }

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - subtraction throws`() {
        JsonLogicParser.parse("""{"-":[3, 1]}""")
    }

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - multiplication throws`() {
        JsonLogicParser.parse("""{"*":[2, 3]}""")
    }

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - division throws`() {
        JsonLogicParser.parse("""{"/":[6, 2]}""")
    }

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - modulo throws`() {
        JsonLogicParser.parse("""{"%":[5, 2]}""")
    }

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - min throws`() {
        JsonLogicParser.parse("""{"min":[1, 2, 3]}""")
    }

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - max throws`() {
        JsonLogicParser.parse("""{"max":[1, 2, 3]}""")
    }

    // --- String operators (all removed except 'in') ---

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - cat throws`() {
        JsonLogicParser.parse("""{"cat":["a", "b"]}""")
    }

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - substr throws`() {
        JsonLogicParser.parse("""{"substr":["hello", 0, 2]}""")
    }

    // --- Array operators (all removed) ---

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - map throws`() {
        JsonLogicParser.parse("""{"map":[[1,2,3], {"var":""}]}""")
    }

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - filter throws`() {
        JsonLogicParser.parse("""{"filter":[[1,2,3], {"var":""}]}""")
    }

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - reduce throws`() {
        JsonLogicParser.parse("""{"reduce":[[1,2,3], {"var":"current"}, 0]}""")
    }

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - all throws`() {
        JsonLogicParser.parse("""{"all":[[1,2,3], {"var":""}]}""")
    }

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - some throws`() {
        JsonLogicParser.parse("""{"some":[[1,2,3], {"var":""}]}""")
    }

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - none throws`() {
        JsonLogicParser.parse("""{"none":[[1,2,3], {"var":""}]}""")
    }

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - merge throws`() {
        JsonLogicParser.parse("""{"merge":[[1,2], [3,4]]}""")
    }

    // --- Data access operators (only 'var' kept) ---

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - missing throws`() {
        JsonLogicParser.parse("""{"missing":["a", "b"]}""")
    }

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - missing_some throws`() {
        JsonLogicParser.parse("""{"missing_some":[1, ["a", "b"]]}""")
    }

    // --- Misc operators (all removed) ---

    @Test(expected = UnsupportedOperatorException::class)
    fun `unsupported - log throws`() {
        JsonLogicParser.parse("""{"log":"test"}""")
    }

    // =========================================================================
    // Parse - only JSON objects are accepted
    // =========================================================================

    @Test(expected = InvalidExpressionException::class)
    fun `parse - throws for boolean literal`() {
        JsonLogicParser.parse("true")
    }

    @Test(expected = InvalidExpressionException::class)
    fun `parse - throws for number literal`() {
        JsonLogicParser.parse("42")
    }

    @Test(expected = InvalidExpressionException::class)
    fun `parse - throws for string literal`() {
        JsonLogicParser.parse("\"hello\"")
    }

    @Test(expected = InvalidExpressionException::class)
    fun `parse - throws for null literal`() {
        JsonLogicParser.parse("null")
    }

    @Test(expected = InvalidExpressionException::class)
    fun `parse - throws for array`() {
        JsonLogicParser.parse("""["a", "b"]""")
    }
}
