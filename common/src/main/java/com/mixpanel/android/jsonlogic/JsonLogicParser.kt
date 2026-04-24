package com.mixpanel.android.jsonlogic

import androidx.annotation.RestrictTo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser that converts raw JSON into a typed [JsonLogicRule] tree.
 *
 * Supported operators (per Event Trigger alignment decision):
 * - Comparison: ===, !==, <, <=, >, >=
 * - Logic: and, or
 * - String/Array: in
 * - Data Access: var
 *
 * Example usage:
 * ```kotlin
 * val rule = JsonLogicParser.parse("""{"===":[1,1]}""")
 * ```
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object JsonLogicParser {

    /**
     * Parses a JSON string into a typed [JsonLogicRule].
     *
     * @param json The JSON string representing a JsonLogic rule
     * @return The parsed rule as a typed tree
     * @throws EvaluationException if the JSON is malformed or contains unsupported operations
     */
    @JvmStatic
    fun parse(json: String): JsonLogicRule {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{")) {
            throw InvalidExpressionException("parse", "input must be a JSON object: '$trimmed'")
        }
        return try {
            parseValue(JSONObject(trimmed))
        } catch (e: EvaluationException) {
            throw e
        } catch (_: Exception) {
            throw InvalidExpressionException("parse", "malformed JSON object: '$trimmed'")
        }
    }

    /**
     * Parses any JSON value into a [JsonLogicRule].
     * Internal use only - use [parse] for parsing JSON strings.
     */
    internal fun parseValue(value: Any?): JsonLogicRule {
        return when (value) {
            null, JSONObject.NULL -> LiteralRule(null)
            is Boolean -> LiteralRule(value)
            is Number -> LiteralRule(value)
            is String -> LiteralRule(value)
            is JSONArray -> parseArray(value)
            is JSONObject -> parseObject(value)
            else -> throw TypeMismatchException("value", "unsupported type: ${value::class.java}")
        }
    }

    private fun parseArray(array: JSONArray): JsonLogicRule {
        val elements = (0 until array.length()).map { i ->
            parseValue(array.get(i))
        }
        // Check if any element is a rule (not just a literal)
        val hasRules = elements.any { it !is LiteralRule }
        return if (hasRules) {
            ArrayRule(elements)
        } else {
            // Pure literal array
            LiteralRule(elements.map { (it as LiteralRule).value })
        }
    }

    private fun parseObject(obj: JSONObject): JsonLogicRule {
        if (obj.length() == 0) {
            return LiteralRule(emptyMap<String, Any?>())
        }
        if (obj.length() != 1) {
            throw InvalidExpressionException("rule", "must have exactly one operator, found: ${obj.keys().asSequence().toList()}")
        }

        val operator = obj.keys().next()
        val args = obj.get(operator)

        return parseOperator(operator, args)
    }

    private fun parseOperator(operator: String, args: Any?): JsonLogicRule {
        val operands = toOperandList(args)

        return when (operator) {
            // Comparison
            "===" -> requireBinary(operator, operands) { l, r -> StrictEqualsRule(l, r) }
            "!==" -> requireBinary(operator, operands) { l, r -> StrictNotEqualsRule(l, r) }
            ">" -> requireBinary(operator, operands) { l, r -> GreaterThanRule(l, r) }
            ">=" -> requireBinary(operator, operands) { l, r -> GreaterThanOrEqualRule(l, r) }
            "<" -> requireBinary(operator, operands) { l, r -> LessThanRule(l, r) }
            "<=" -> requireBinary(operator, operands) { l, r -> LessThanOrEqualRule(l, r) }

            // Logic
            "and" -> AndRule(operands)
            "or" -> OrRule(operands)

            // String/Array
            "in" -> requireBinary(operator, operands) { l, r -> InRule(l, r) }

            // Data access
            "var" -> parseVarRule(operands)

            else -> throw UnsupportedOperatorException(operator)
        }
    }

    private fun parseVarRule(operands: List<JsonLogicRule>): VarRule {
        return when {
            operands.isEmpty() -> VarRule(LiteralRule(""))
            operands.size == 1 -> VarRule(operands[0])
            else -> throw InvalidExpressionException(
                "var",
                "default values are not supported"
            )
        }
    }

    private fun toOperandList(args: Any?): List<JsonLogicRule> {
        return when (args) {
            null, JSONObject.NULL -> listOf(LiteralRule(null))
            is JSONArray -> (0 until args.length()).map { parseValue(args.get(it)) }
            else -> listOf(parseValue(args))
        }
    }

    private inline fun <T : JsonLogicRule> requireBinary(
        operator: String,
        operands: List<JsonLogicRule>,
        factory: (JsonLogicRule, JsonLogicRule) -> T
    ): T {
        if (operands.size != 2) {
            throw InvalidExpressionException(operator, "requires exactly 2 arguments, got ${operands.size}")
        }
        return factory(operands[0], operands[1])
    }
}
