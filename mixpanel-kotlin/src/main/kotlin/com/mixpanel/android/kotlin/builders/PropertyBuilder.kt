package com.mixpanel.android.kotlin.builders

import org.json.JSONObject

/**
 * Builder for constructing properties using DSL syntax.
 *
 * Example:
 * ```kotlin
 * mixpanel.track("Event") {
 *     "key" to "value"
 *     "count" to 42
 * }
 * ```
 */
@MixpanelDsl
class PropertyBuilder {
    private val properties = mutableMapOf<String, Any?>()

    /**
     * Adds a property. Null values are sent as JSON null to the server.
     */
    infix fun String.to(value: Any?) {
        properties[this] = value
    }

    /**
     * Builds the properties as a JSONObject.
     */
    fun build(): JSONObject = JSONObject(properties)

    /**
     * Returns the properties as a Map.
     */
    fun toMap(): Map<String, Any?> = properties.toMap()
}

/**
 * Builds a JSONObject using DSL syntax.
 *
 * Example:
 * ```kotlin
 * val props = properties {
 *     "item" to "Premium"
 *     "price" to 9.99
 * }
 * mixpanel.track("Purchase", props)
 * ```
 */
inline fun properties(block: PropertyBuilder.() -> Unit): JSONObject = PropertyBuilder().apply(block).build()
