package com.mixpanel.android.jsonlogic

import androidx.annotation.RestrictTo

/**
 * Base exception for JsonLogic errors.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
abstract class EvaluationException(message: String) : Exception(message)

/**
 * Thrown when an unsupported operator is encountered during parsing.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class UnsupportedOperatorException(operator: String) : EvaluationException(
    "Unsupported operator: '$operator'. " +
        "Try updating to a newer SDK version for possible operator support."
)

/**
 * Thrown when a type mismatch occurs during evaluation.
 * Examples: string in comparison operator, non-string needle in 'in' operator.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class TypeMismatchException(expression: String, reason: String) : EvaluationException(
    "Type mismatch in '$expression': $reason. " +
        "Try updating to a newer SDK version for possible type support."
)

/**
 * Thrown when an expression is structurally invalid.
 * Examples: wrong argument count, malformed JSON, invalid number.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class InvalidExpressionException(expression: String, reason: String) : EvaluationException(
    "Invalid expression '$expression': $reason. " +
        "Try updating to a newer SDK version for possible expression support."
)
