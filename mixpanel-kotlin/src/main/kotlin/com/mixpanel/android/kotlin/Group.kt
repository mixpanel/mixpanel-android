package com.mixpanel.android.kotlin

import com.mixpanel.android.kotlin.builders.PropertyBuilder
import com.mixpanel.android.mpmetrics.MixpanelAPI

/**
 * Kotlin-friendly wrapper for [MixpanelAPI.Group].
 *
 * Provides idiomatic Kotlin APIs for group operations.
 *
 * Example:
 * ```kotlin
 * mixpanel.group(key = "Company", id = "Acme Inc").setProperties {
 *     "industry" to "Technology"
 *     "employees" to 500
 * }
 * ```
 */
class Group internal constructor(
    @PublishedApi internal val group: MixpanelAPI.Group,
) {
    /**
     * Access to the underlying [MixpanelAPI.Group] for advanced use cases.
     */
    val java: MixpanelAPI.Group get() = group

    /**
     * Sets properties on the group.
     *
     * Example:
     * ```kotlin
     * mixpanel.group(key = "Company", id = "Acme Inc").setProperties {
     *     "name" to "Acme Inc"
     *     "industry" to "Technology"
     *     "employees" to 500
     * }
     * ```
     *
     * @param properties DSL block for building properties
     */
    inline fun setProperties(properties: PropertyBuilder.() -> Unit) {
        group.set(PropertyBuilder().apply(properties).build())
    }

    /**
     * Sets properties only if they haven't been set before.
     *
     * Example:
     * ```kotlin
     * mixpanel.group(key = "Company", id = "Acme Inc").setOnceProperties {
     *     "createdAt" to System.currentTimeMillis()
     *     "foundedYear" to 2020
     * }
     * ```
     *
     * @param properties DSL block for building properties
     */
    inline fun setOnceProperties(properties: PropertyBuilder.() -> Unit) {
        group.setOnce(PropertyBuilder().apply(properties).build())
    }

    /**
     * Sets a single property on the group.
     *
     * @param name The property name
     * @param value The property value
     */
    fun set(
        name: String,
        value: Any?,
    ) {
        group.set(name, value)
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
        group.setOnce(name, value)
    }

    /**
     * Removes a property from the group.
     *
     * @param name The property name
     * @param value The value to remove
     */
    fun remove(
        name: String,
        value: Any,
    ) {
        group.remove(name, value)
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
        group.union(name, jsonArray)
    }

    /**
     * Removes a property from the group.
     *
     * @param name The property name to unset
     */
    fun unset(name: String) {
        group.unset(name)
    }

    /**
     * Deletes the group from Mixpanel.
     */
    fun deleteGroup() {
        group.deleteGroup()
    }
}
