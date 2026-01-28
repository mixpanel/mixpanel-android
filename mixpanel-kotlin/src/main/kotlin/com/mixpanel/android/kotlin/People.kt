package com.mixpanel.android.kotlin

import com.mixpanel.android.kotlin.builders.IncrementBuilder
import com.mixpanel.android.kotlin.builders.PropertyBuilder
import com.mixpanel.android.mpmetrics.MixpanelAPI
import org.json.JSONObject

/**
 * Kotlin-friendly wrapper for [MixpanelAPI.People].
 *
 * Provides idiomatic Kotlin APIs for user profile operations.
 *
 * Example:
 * ```kotlin
 * mixpanel.people.setProperties {
 *     "name" to "John Doe"
 *     "email" to "john@example.com"
 * }
 * ```
 */
class People internal constructor(
    @PublishedApi internal val people: MixpanelAPI.People,
) {
    /**
     * Access to the underlying [MixpanelAPI.People] for advanced use cases.
     */
    val java: MixpanelAPI.People get() = people

    /**
     * Sets properties on the user profile.
     *
     * Example:
     * ```kotlin
     * mixpanel.people.setProperties {
     *     "name" to "John Doe"
     *     "email" to "john@example.com"
     *     "age" to 30
     * }
     * ```
     *
     * @param properties DSL block for building properties
     */
    inline fun setProperties(properties: PropertyBuilder.() -> Unit) {
        people.set(PropertyBuilder().apply(properties).build())
    }

    /**
     * Sets properties only if they haven't been set before.
     *
     * Example:
     * ```kotlin
     * mixpanel.people.setOnceProperties {
     *     "createdAt" to System.currentTimeMillis()
     *     "signupSource" to "organic"
     * }
     * ```
     *
     * @param properties DSL block for building properties
     */
    inline fun setOnceProperties(properties: PropertyBuilder.() -> Unit) {
        people.setOnce(PropertyBuilder().apply(properties).build())
    }

    /**
     * Sets a single property on the user profile.
     *
     * @param name The property name
     * @param value The property value
     */
    fun set(
        name: String,
        value: Any?,
    ) {
        people.set(name, value)
    }

    /**
     * Sets a single property only if it hasn't been set before.
     *
     * @param name The property name
     * @param value The property value
     */
    fun setOnce(
        name: String,
        value: Any?,
    ) {
        people.setOnce(name, value)
    }

    /**
     * Merges a JSON object into a property.
     *
     * @param name The property name
     * @param updates The JSON updates to merge
     */
    fun merge(
        name: String,
        updates: JSONObject,
    ) {
        people.merge(name, updates)
    }

    /**
     * Merges properties into a property using DSL syntax.
     *
     * Example:
     * ```kotlin
     * mixpanel.people.merge("preferences") {
     *     "theme" to "dark"
     *     "notifications" to true
     * }
     * ```
     *
     * @param name The property name
     * @param updates DSL block for building the updates
     */
    inline fun merge(
        name: String,
        updates: PropertyBuilder.() -> Unit,
    ) {
        people.merge(name, PropertyBuilder().apply(updates).build())
    }

    /**
     * Increments numeric properties.
     *
     * Example:
     * ```kotlin
     * mixpanel.people.increment {
     *     "loginCount" by 1
     *     "totalSpent" by 29.99
     * }
     * ```
     *
     * @param increments DSL block for building increments
     */
    inline fun increment(increments: IncrementBuilder.() -> Unit) {
        people.increment(IncrementBuilder().apply(increments).build())
    }

    /**
     * Increments a single property.
     *
     * @param name The property name
     * @param by The amount to increment by
     */
    fun increment(
        name: String,
        by: Double,
    ) {
        people.increment(name, by)
    }

    /**
     * Appends a value to a list property.
     *
     * @param name The property name
     * @param value The value to append
     */
    fun append(
        name: String,
        value: Any,
    ) {
        people.append(name, value)
    }

    /**
     * Removes a value from a list property.
     *
     * @param name The property name
     * @param value The value to remove
     */
    fun remove(
        name: String,
        value: Any,
    ) {
        people.remove(name, value)
    }

    /**
     * Adds values to a list property, ignoring duplicates.
     *
     * @param name The property name
     * @param values The values to union
     */
    fun union(
        name: String,
        values: List<Any>,
    ) {
        val jsonArray = org.json.JSONArray(values)
        people.union(name, jsonArray)
    }

    /**
     * Removes a property from the user profile.
     *
     * @param name The property name to unset
     */
    fun unset(name: String) {
        people.unset(name)
    }

    /**
     * Tracks a revenue transaction.
     *
     * Example:
     * ```kotlin
     * mixpanel.people.trackCharge(amount = 29.99) {
     *     "sku" to "PREMIUM_MONTHLY"
     *     "currency" to "USD"
     * }
     * ```
     *
     * @param amount The transaction amount
     * @param properties DSL block for transaction properties
     */
    inline fun trackCharge(
        amount: Double,
        properties: PropertyBuilder.() -> Unit = {},
    ) {
        people.trackCharge(amount, PropertyBuilder().apply(properties).build())
    }

    /**
     * Clears all revenue transactions.
     */
    fun clearCharges() {
        people.clearCharges()
    }

    /**
     * Deletes the user profile from Mixpanel.
     */
    fun deleteUser() {
        people.deleteUser()
    }

    /**
     * Returns whether the user has been identified.
     */
    val isIdentified: Boolean
        get() = people.isIdentified
}
