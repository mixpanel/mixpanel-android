package com.mixpanel.android.kotlin

import android.content.Context
import com.mixpanel.android.kotlin.builders.MixpanelOptionsBuilder
import com.mixpanel.android.kotlin.builders.PropertyBuilder
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.android.util.MixpanelNetworkErrorListener
import org.json.JSONObject

/**
 * Kotlin-friendly wrapper for [MixpanelAPI].
 *
 * This class provides idiomatic Kotlin APIs with named parameters and DSL builders.
 * Use this instead of [MixpanelAPI] for a cleaner Kotlin experience.
 *
 * Example:
 * ```kotlin
 * val mixpanel = Mixpanel.getInstance(context, "YOUR_TOKEN") {
 *     featureFlags { enabled = true }
 *     superProperties("platform" to "android")
 * }
 *
 * mixpanel.track(name = "Purchase") {
 *     "item" to "Premium"
 *     "price" to 9.99
 * }
 * ```
 */
class Mixpanel private constructor(
    @PublishedApi internal val api: MixpanelAPI,
) {
    /**
     * Access to the underlying [MixpanelAPI] for advanced use cases.
     */
    val java: MixpanelAPI get() = api

    // ========================================
    // IDENTITY
    // ========================================

    /**
     * Associates future events with a specific user.
     *
     * @param distinctId A unique identifier for the user
     */
    fun identify(distinctId: String) {
        api.identify(distinctId)
    }

    /**
     * Creates an alias for the current distinct ID.
     *
     * @param alias The alias to create
     * @param distinctId Optional distinct ID to alias (defaults to current)
     */
    fun alias(
        alias: String,
        distinctId: String? = null,
    ) {
        api.alias(alias, distinctId)
    }

    /**
     * Clears all stored properties and resets the distinct ID.
     */
    fun reset() {
        api.reset()
    }

    /**
     * Returns the current distinct ID.
     */
    val distinctId: String
        get() = api.distinctId

    /**
     * Returns the anonymous ID assigned before identification.
     */
    val anonymousId: String
        get() = api.anonymousId

    /**
     * Returns the project token.
     */
    val token: String
        get() = api.token

    // ========================================
    // EVENT TRACKING
    // ========================================

    /**
     * Tracks an event with optional properties.
     *
     * Example:
     * ```kotlin
     * mixpanel.track(name = "Purchase") {
     *     "item" to "Premium Subscription"
     *     "price" to 9.99
     * }
     * ```
     *
     * @param name The name of the event
     * @param properties DSL block for building properties
     */
    inline fun track(
        name: String,
        properties: PropertyBuilder.() -> Unit = {},
    ) {
        api.track(name, PropertyBuilder().apply(properties).build())
    }

    /**
     * Tracks an event with pre-built properties.
     *
     * @param name The name of the event
     * @param properties Pre-built JSONObject of properties
     */
    fun track(
        name: String,
        properties: JSONObject,
    ) {
        api.track(name, properties)
    }

    /**
     * Times an event. Call [track] with the same event name to log the timed event.
     *
     * @param name The name of the event to time
     */
    fun timeEvent(name: String) {
        api.timeEvent(name)
    }

    /**
     * Clears all timed events.
     */
    fun clearTimedEvents() {
        api.clearTimedEvents()
    }

    /**
     * Clears a specific timed event.
     *
     * @param name The name of the timed event to clear
     */
    fun clearTimedEvent(name: String) {
        api.clearTimedEvent(name)
    }

    /**
     * Returns the elapsed time in seconds for a timed event.
     *
     * @param name The name of the timed event
     * @return The elapsed time in seconds, or 0 if the event hasn't been timed
     */
    fun eventElapsedTime(name: String): Double = api.eventElapsedTime(name)

    /**
     * Tracks an event with group associations.
     *
     * Example:
     * ```kotlin
     * mixpanel.trackWithGroups(
     *     name = "Purchase",
     *     groups = mapOf("Company" to "Acme Inc")
     * ) {
     *     "item" to "Premium"
     *     "price" to 9.99
     * }
     * ```
     *
     * @param name The name of the event
     * @param groups Map of group keys to group IDs
     * @param properties DSL block for building properties
     */
    @Suppress("UNCHECKED_CAST")
    inline fun trackWithGroups(
        name: String,
        groups: Map<String, Any>,
        properties: PropertyBuilder.() -> Unit = {},
    ) {
        val propsMap = PropertyBuilder().apply(properties).toMap()
        api.trackWithGroups(name, propsMap, groups)
    }

    // ========================================
    // SUPER PROPERTIES
    // ========================================

    /**
     * Registers properties that will be sent with every event.
     *
     * Example:
     * ```kotlin
     * mixpanel.registerSuperProperties {
     *     "appVersion" to "2.0.0"
     *     "platform" to "android"
     * }
     * ```
     *
     * @param properties DSL block for building properties
     */
    inline fun registerSuperProperties(properties: PropertyBuilder.() -> Unit) {
        api.registerSuperProperties(PropertyBuilder().apply(properties).build())
    }

    /**
     * Registers properties only if they haven't been set before.
     *
     * @param properties DSL block for building properties
     */
    inline fun registerSuperPropertiesOnce(properties: PropertyBuilder.() -> Unit) {
        api.registerSuperPropertiesOnce(PropertyBuilder().apply(properties).build())
    }

    /**
     * Removes a super property.
     *
     * @param name The name of the property to remove
     */
    fun unregisterSuperProperty(name: String) {
        api.unregisterSuperProperty(name)
    }

    /**
     * Clears all super properties.
     */
    fun clearSuperProperties() {
        api.clearSuperProperties()
    }

    /**
     * Returns the current super properties.
     */
    val superProperties: JSONObject
        get() = api.superProperties

    /**
     * Atomically updates super properties using a callback.
     *
     * Example:
     * ```kotlin
     * mixpanel.updateSuperProperties { currentProps ->
     *     currentProps.put("loginCount", currentProps.optInt("loginCount", 0) + 1)
     *     currentProps
     * }
     * ```
     *
     * @param update A function that receives the current properties and returns the updated properties
     */
    fun updateSuperProperties(update: (JSONObject) -> JSONObject) {
        api.updateSuperProperties { update(it) }
    }

    // ========================================
    // GROUPS
    // ========================================

    /**
     * Associates this user with a group.
     *
     * @param key The group key (e.g., "Company", "Team")
     * @param id The group identifier
     */
    fun setGroup(
        key: String,
        id: Any,
    ) {
        api.setGroup(key, id)
    }

    /**
     * Associates this user with multiple groups.
     *
     * @param key The group key (e.g., "Company", "Team")
     * @param ids The list of group identifiers
     */
    fun setGroup(
        key: String,
        ids: List<Any>,
    ) {
        api.setGroup(key, ids)
    }

    /**
     * Adds this user to an additional group.
     *
     * @param key The group key
     * @param id The group identifier to add
     */
    fun addGroup(
        key: String,
        id: Any,
    ) {
        api.addGroup(key, id)
    }

    /**
     * Removes this user from a group.
     *
     * @param key The group key
     * @param id The group identifier to remove
     */
    fun removeGroup(
        key: String,
        id: Any,
    ) {
        api.removeGroup(key, id)
    }

    /**
     * Gets a group instance for setting group properties.
     *
     * Example:
     * ```kotlin
     * mixpanel.group(key = "Company", id = "Acme Inc").setProperties {
     *     "industry" to "Technology"
     * }
     * ```
     *
     * @param key The group key
     * @param id The group identifier
     * @return A [Group] wrapper for group operations
     */
    fun group(
        key: String,
        id: Any,
    ): Group = Group(api.getGroup(key, id))

    // ========================================
    // PEOPLE & FLAGS
    // ========================================

    /**
     * Access to People (user profile) operations.
     */
    val people: People = People(api.people)

    /**
     * Access to feature flags operations.
     */
    val flags: Flags = Flags(api.flags)

    // ========================================
    // OPT OUT
    // ========================================

    /**
     * Opts the user out of tracking.
     */
    fun optOutTracking() {
        api.optOutTracking()
    }

    /**
     * Opts the user back into tracking.
     */
    fun optInTracking() {
        api.optInTracking()
    }

    /**
     * Opts the user back into tracking with a specific distinct ID.
     *
     * @param distinctId The distinct ID to use after opting in
     */
    fun optInTracking(distinctId: String) {
        api.optInTracking(distinctId)
    }

    /**
     * Opts the user back into tracking with a specific distinct ID and properties.
     *
     * @param distinctId The distinct ID to use after opting in
     * @param properties Properties to track with the opt-in event
     */
    inline fun optInTracking(
        distinctId: String,
        properties: PropertyBuilder.() -> Unit,
    ) {
        api.optInTracking(distinctId, PropertyBuilder().apply(properties).build())
    }

    /**
     * Returns whether the user has opted out of tracking.
     */
    val hasOptedOut: Boolean
        get() = api.hasOptedOutTracking()

    // ========================================
    // FLUSH
    // ========================================

    /**
     * Sends all queued events to Mixpanel immediately.
     */
    fun flush() {
        api.flush()
    }

    // ========================================
    // CONFIGURATION
    // ========================================

    /**
     * Enables or disables Mixpanel debug logging.
     *
     * @param enabled Whether to enable logging
     */
    fun enableLogging(enabled: Boolean = true) {
        api.setEnableLogging(enabled)
    }

    /**
     * The number of events to batch before sending to the server.
     */
    var flushBatchSize: Int
        get() = api.flushBatchSize
        set(value) {
            api.flushBatchSize = value
        }

    /**
     * The maximum database size in bytes.
     */
    var maximumDatabaseLimit: Int
        get() = api.maximumDatabaseLimit
        set(value) {
            api.maximumDatabaseLimit = value
        }

    /**
     * Sets a listener for network errors. Pass null to remove.
     *
     * @param listener The listener to receive network error callbacks, or null to remove
     */
    fun setNetworkErrorListener(listener: MixpanelNetworkErrorListener?) {
        api.setNetworkErrorListener(listener)
    }

    /**
     * Configures whether to use IP address for geolocation.
     *
     * @param useIp Whether to use IP address for geolocation
     */
    fun setUseIpAddressForGeolocation(useIp: Boolean) {
        api.setUseIpAddressForGeolocation(useIp)
    }

    /**
     * Returns whether the app is currently in the foreground.
     */
    val isAppInForeground: Boolean
        get() = api.isAppInForeground

    companion object {
        /**
         * Gets a [Mixpanel] instance with DSL-based configuration.
         *
         * Example:
         * ```kotlin
         * val mixpanel = Mixpanel.getInstance(context, "YOUR_TOKEN") {
         *     instanceName = "myApp"
         *     serverURL = "https://api-eu.mixpanel.com"
         *     featureFlags {
         *         enabled = true
         *         context("userId" to "123")
         *     }
         *     superProperties("platform" to "android")
         * }
         * ```
         *
         * @param context The application context
         * @param token Your Mixpanel project token
         * @param trackAutomaticEvents Whether to track automatic events (defaults to true)
         * @param configure DSL block for configuration options
         * @return A configured [Mixpanel] instance
         */
        @JvmStatic
        @JvmOverloads
        fun getInstance(
            context: Context,
            token: String,
            trackAutomaticEvents: Boolean = true,
            configure: MixpanelOptionsBuilder.() -> Unit = {},
        ): Mixpanel {
            val options = MixpanelOptionsBuilder().apply(configure).build()
            val api = MixpanelAPI.getInstance(context, token, trackAutomaticEvents, options)
            return Mixpanel(api)
        }

        /**
         * Wraps an existing [MixpanelAPI] instance.
         *
         * @param api The MixpanelAPI instance to wrap
         * @return A [Mixpanel] wrapper
         */
        @JvmStatic
        fun wrap(api: MixpanelAPI): Mixpanel = Mixpanel(api)
    }
}
