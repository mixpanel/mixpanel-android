package com.mixpanel.android.kotlin.builders

/**
 * Builder for increment operations.
 */
@MixpanelDsl
class IncrementBuilder {
    private val increments = mutableMapOf<String, Number>()

    /**
     * Increments a property by the given value.
     */
    infix fun String.by(value: Number) {
        increments[this] = value
    }

    /**
     * Builds the increments as a Map.
     */
    fun build(): Map<String, Number> = increments.toMap()
}
