package com.mixpanel.android.jsonlogic

import androidx.annotation.RestrictTo

/**
 * Sealed hierarchy representing all supported JsonLogic operations.
 * Each node holds its operands as typed children, making the full rule tree strongly typed after parsing.
 *
 * Supported operators (per Event Trigger alignment decision):
 * - Comparison: ===, !==, <, <=, >, >=
 * - Logic: and, or
 * - String/Array: in
 * - Data Access: var
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
sealed interface JsonLogicRule

/**
 * A literal value (string, number, boolean, null, or array of literals).
 */
data class LiteralRule(val value: Any?) : JsonLogicRule

// =============================================================================
// Comparison Operations
// =============================================================================

/** Strict equality (===) - no type coercion */
data class StrictEqualsRule(val left: JsonLogicRule, val right: JsonLogicRule) : JsonLogicRule

/** Strict inequality (!==) */
data class StrictNotEqualsRule(val left: JsonLogicRule, val right: JsonLogicRule) : JsonLogicRule

/** Greater than (>) */
data class GreaterThanRule(val left: JsonLogicRule, val right: JsonLogicRule) : JsonLogicRule

/** Greater than or equal (>=) */
data class GreaterThanOrEqualRule(val left: JsonLogicRule, val right: JsonLogicRule) : JsonLogicRule

/** Less than (<) - only 2 arguments supported */
data class LessThanRule(val left: JsonLogicRule, val right: JsonLogicRule) : JsonLogicRule

/** Less than or equal (<=) - only 2 arguments supported */
data class LessThanOrEqualRule(val left: JsonLogicRule, val right: JsonLogicRule) : JsonLogicRule

// =============================================================================
// Logic Operations
// =============================================================================

/** Logical AND - returns first falsy value or last value */
data class AndRule(val operands: List<JsonLogicRule>) : JsonLogicRule

/** Logical OR - returns first truthy value or last value */
data class OrRule(val operands: List<JsonLogicRule>) : JsonLogicRule

// =============================================================================
// String/Array Operations
// =============================================================================

/** In - checks if needle is in haystack (string or array) */
data class InRule(val needle: JsonLogicRule, val haystack: JsonLogicRule) : JsonLogicRule

// =============================================================================
// Data Access Operations
// =============================================================================

/** Variable access (var) - retrieves value from data using path */
data class VarRule(val path: JsonLogicRule) : JsonLogicRule

// =============================================================================
// Internal Types
// =============================================================================

/** Array literal that may contain rules (evaluated at runtime) */
data class ArrayRule(val elements: List<JsonLogicRule>) : JsonLogicRule
