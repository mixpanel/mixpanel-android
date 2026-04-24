package com.mixpanel.android.jsonlogic

import androidx.annotation.RestrictTo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Evaluates typed [JsonLogicRule] trees against JSON data.
 *
 * The evaluator walks the typed rule tree without string-matching on operator names.
 *
 * Supported operators: `===`, `!==`, `<`, `<=`, `>`, `>=`, `and`, `or`, `in`, `var`
 *
 * ## Operator Assumptions
 *
 * ### Strict Equality (`===`, `!==`)
 * - `null` can only equal `null`; comparing `null` with non-null throws [TypeMismatchException]
 * - Array comparison is not supported; throws [TypeMismatchException]
 * - Numbers are compared by value regardless of subtype (`Int 1 === Long 1` is `true`)
 * - Non-null, non-number operands must be the same type; otherwise throws [TypeMismatchException]
 *
 * ### Numeric Comparison (`>`, `<`, `>=`, `<=`)
 * - Both operands must be numbers; non-numeric operands throw [TypeMismatchException]
 * - `NaN` values are not supported; throws [TypeMismatchException]
 *
 * ### Logic (`and`, `or`)
 * - Requires at least 1 operand; empty operands throw [InvalidExpressionException]
 * - All operands must evaluate to `Boolean`; non-boolean results throw [TypeMismatchException]
 * - All operands are evaluated (no short-circuit) to ensure type safety
 *
 * ### Membership/Substring (`in`)
 * - Needle must be a `String`; non-string needles throw [TypeMismatchException]
 * - Haystack must be a `String` or array; other types throw [TypeMismatchException]
 * - For string haystack: performs substring check
 * - For array haystack: checks membership using strict equality (all elements validated)
 *
 * ### Data Access (`var`)
 * - Property name is required; empty path throws [InvalidExpressionException]
 * - Dots in property names are not allowed; throws [InvalidExpressionException]
 * - Returns `null` if the property does not exist
 *
 * ## Example
 * ```kotlin
 * val rule = JsonLogicParser.parse("""{"===":[{"var":"a"},1]}""")
 * val data = JSONObject("""{"a":1}""")
 * val result = JsonLogicEvaluator.evaluate(rule, data) // returns true
 * ```
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object JsonLogicEvaluator {

    /**
     * Evaluates a JsonLogic rule against event properties.
     *
     * The return type is [Any?] because JsonLogic is dynamically typed and different
     * operations return different types:
     * - Comparison ops (`===`, `!==`, `>`, `<`, `>=`, `<=`) return [Boolean]
     * - Logic ops (`and`, `or`) return [Boolean]
     * - String/Array ops (`in`) return [Boolean]
     * - Data access (`var`) returns whatever type exists in the data
     * - Literal values return their value (String, Number, Boolean, null)
     * - Array rules return a [List] of evaluated elements
     *
     * ```kotlin
     * val rule = JsonLogicParser.parse("""{"and": [{"===": [{"var": "a"}, 1]}, {"in": ["x", "xyz"]}]}""")
     * val data = JSONObject("""{"a": 1}""")
     * val result = JsonLogicEvaluator.evaluate(rule, data) // returns true
     * ```
     *
     * @param rule The typed rule to evaluate
     * @param data The event properties as a JSONObject
     * @return The evaluation result, type depends on the rule operation
     * @throws TypeMismatchException When operand types are incompatible (e.g., comparing
     *         different types with `===`, non-boolean operands for `and`/`or`, non-numeric
     *         operands for `>`, `<`, `>=`, `<=`, non-string needle for `in`, or
     *         non-string/non-array haystack for `in`)
     * @throws InvalidExpressionException When expression structure is invalid (e.g., empty
     *         `and`/`or` operands, or empty `var` path)
     */
    @JvmStatic
    fun evaluate(rule: JsonLogicRule, data: JSONObject): Any? {
        return when (rule) {
            is LiteralRule -> rule.value
            is ArrayRule -> rule.elements.map { evaluate(it, data) }

            // Comparison
            is StrictEqualsRule -> strictEquals(evaluate(rule.left, data), evaluate(rule.right, data))
            is StrictNotEqualsRule -> !strictEquals(evaluate(rule.left, data), evaluate(rule.right, data))
            is GreaterThanRule -> compareValues(evaluate(rule.left, data), evaluate(rule.right, data)) > 0
            is GreaterThanOrEqualRule -> compareValues(evaluate(rule.left, data), evaluate(rule.right, data)) >= 0
            is LessThanRule -> compareValues(evaluate(rule.left, data), evaluate(rule.right, data)) < 0
            is LessThanOrEqualRule -> compareValues(evaluate(rule.left, data), evaluate(rule.right, data)) <= 0

            // Logic
            is AndRule -> evaluateAnd(rule.operands, data)
            is OrRule -> evaluateOr(rule.operands, data)

            // String/Array
            is InRule -> evaluateIn(rule, data)

            // Data access
            is VarRule -> evaluateVar(rule, data)
        }
    }

    // =========================================================================
    // Comparison helpers
    // =========================================================================

    /**
     * Strict equality (===) - operands must be the same type.
     * Throws TypeMismatchException if types don't match.
     */
    private fun strictEquals(a: Any?, b: Any?): Boolean {
        // null can only compare with null
        if (a == null && b == null) return true
        if (a == null || b == null) {
            throw TypeMismatchException("===", "operands must be the same type")
        }

        // Arrays: strict equality is not supported - throw exception
        if (a is List<*> || b is List<*>) {
            throw TypeMismatchException("===", "does not support array comparison")
        }

        // Numbers: compare values (Int 1 === Long 1 should be true)
        if (a is Number && b is Number) {
            return a.toDouble() == b.toDouble()
        }

        // Must be same type for other comparisons
        if (a::class != b::class) {
            throw TypeMismatchException("===", "operands must be the same type")
        }
        return a == b
    }

    /**
     * Compares two values numerically for relational operators (>, <, >=, <=).
     * Only numbers are supported - strings and null will throw IllegalArgumentException.
     */
    private fun compareValues(a: Any?, b: Any?): Int {
        // Only numbers are allowed in comparison operators
        if (a !is Number || b !is Number) {
            throw TypeMismatchException(">, <, >=, <=", "only support numbers")
        }
        val numA = a.toDouble()
        val numB = b.toDouble()
        // NaN comparison throws as well
        if (numA.isNaN() || numB.isNaN()) {
            throw TypeMismatchException(">, <, >=, <=", "do not support NaN")
        }
        return numA.compareTo(numB)
    }

    // =========================================================================
    // Logic helpers
    // =========================================================================

    private fun evaluateAnd(operands: List<JsonLogicRule>, data: JSONObject): Boolean {
        if (operands.isEmpty()) {
            throw InvalidExpressionException("and", "requires at least 1 argument")
        }
        // Evaluate all operands and validate they are boolean.
        // We check ALL operands even after finding a false value to ensure type safety.
        val results = operands.map { operand ->
            val result = evaluate(operand, data)
            if (result !is Boolean) {
                throw TypeMismatchException("and", "operands must be boolean expressions")
            }
            result
        }
        return results.all { it }
    }

    private fun evaluateOr(operands: List<JsonLogicRule>, data: JSONObject): Boolean {
        if (operands.isEmpty()) {
            throw InvalidExpressionException("or", "requires at least 1 argument")
        }
        // Evaluate all operands and validate they are boolean.
        // We check ALL operands even after finding a true value to ensure type safety.
        val results = operands.map { operand ->
            val result = evaluate(operand, data)
            if (result !is Boolean) {
                throw TypeMismatchException("or", "operands must be boolean expressions")
            }
            result
        }
        return results.any { it }
    }

    // =========================================================================
    // String/Array helpers
    // =========================================================================

    private fun evaluateIn(rule: InRule, data: JSONObject): Boolean {
        val needle = evaluate(rule.needle, data)

        // Only string needles are supported for the 'in' operator
        if (needle !is String) {
            throw TypeMismatchException("in", "requires a string needle")
        }

        return when (val haystack = evaluate(rule.haystack, data)) {
            // Substring check
            is String -> haystack.contains(needle)
            // Array membership: all elements must be strings (validated via strictEquals).
            // We check ALL elements even after finding a match to ensure type safety.
            is List<*> -> {
                val matches = haystack.map { strictEquals(needle, it) }
                matches.any { it }
            }
            else -> throw TypeMismatchException("in", "requires a string or array haystack")
        }
    }

    // =========================================================================
    // Data access helpers
    // =========================================================================

    private fun evaluateVar(rule: VarRule, data: JSONObject): Any? {
        val path = when (val pathValue = evaluate(rule.path, data)) {
            null -> ""
            else -> pathValue.toString()
        }

        if (path.isEmpty()) {
            throw InvalidExpressionException("var", "property name is required")
        }

        if (path.contains(".")) {
            throw InvalidExpressionException("var", "dots in property names are not supported - '$path'")
        }

        return resolveProperty(data, path)
    }

    private fun resolveProperty(data: JSONObject, key: String): Any? {
        return if (data.has(key)) normalizeJsonValue(data.get(key)) else null
    }

    private fun normalizeJsonValue(value: Any?): Any? {
        return when (value) {
            JSONObject.NULL -> null
            is JSONObject -> value
            is JSONArray -> (0 until value.length()).map { normalizeJsonValue(value.get(it)) }
            else -> value
        }
    }
}
