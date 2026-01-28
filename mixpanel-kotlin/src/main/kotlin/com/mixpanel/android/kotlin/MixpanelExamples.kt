@file:Suppress("unused", "UNUSED_VARIABLE")

package com.mixpanel.android.kotlin

import android.content.Context
import com.mixpanel.android.kotlin.builders.properties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Example usages of the Mixpanel Kotlin API.
 *
 * Import: `import com.mixpanel.android.kotlin.Mixpanel`
 */
object MixpanelExamples {
    // ========================================
    // INITIALIZATION
    // ========================================

    /**
     * Basic initialization.
     */
    fun initializeBasic(context: Context): Mixpanel = Mixpanel.getInstance(context, "YOUR_PROJECT_TOKEN")

    /**
     * Full initialization with all options.
     */
    fun initializeFull(context: Context): Mixpanel =
        Mixpanel.getInstance(context, "YOUR_PROJECT_TOKEN") {
            instanceName = "myApp"
            optOutTrackingDefault = false
            serverURL("https://api-eu.mixpanel.com")

            featureFlags {
                enabled = true
                context(
                    "userId" to "user_123",
                    "plan" to "premium",
                )
            }

            superProperties(
                "appVersion" to "2.0.0",
                "platform" to "android",
            )
        }

    // ========================================
    // EVENT TRACKING
    // ========================================

    /**
     * Track events.
     */
    fun trackEvents(mixpanel: Mixpanel) {
        // Simple event
        mixpanel.track(name = "App Opened")

        // Event with properties using DSL
        mixpanel.track(name = "Purchase") {
            "item" to "Premium Subscription"
            "price" to 9.99
            "currency" to "USD"
            "quantity" to 1
        }

        // Build properties separately
        val props =
            properties {
                "query" to "kotlin tutorials"
                "resultsCount" to 42
            }
        mixpanel.track(name = "Search", properties = props)

        // Conditional properties
        val eventProps =
            properties {
                "screen" to "Home"
            }
        if (someCondition()) {
            eventProps.put("experimentGroup", "treatment")
        }
        mixpanel.track(name = "Screen Viewed", properties = eventProps)
    }

    /**
     * Timed events and tracking with groups.
     */
    fun advancedTracking(mixpanel: Mixpanel) {
        // Time an event
        mixpanel.timeEvent("Checkout")
        // ... user completes checkout ...
        mixpanel.track(name = "Checkout") {
            "items" to 3
        }

        // Get elapsed time
        val elapsed = mixpanel.eventElapsedTime("Checkout")

        // Clear timed events
        mixpanel.clearTimedEvent("Checkout")
        mixpanel.clearTimedEvents()

        // Track with group context
        mixpanel.trackWithGroups(
            name = "Team Event",
            groups = mapOf("Company" to "Acme Inc", "Team" to "Engineering"),
        ) {
            "action" to "deploy"
        }
    }

    // ========================================
    // SUPER PROPERTIES
    // ========================================

    /**
     * Register super properties.
     */
    fun registerSuperProperties(mixpanel: Mixpanel) {
        mixpanel.registerSuperProperties {
            "appVersion" to "2.0.0"
            "platform" to "android"
            "deviceType" to "phone"
        }

        mixpanel.registerSuperPropertiesOnce {
            "firstOpenDate" to System.currentTimeMillis()
            "installSource" to "play_store"
        }

        // Atomically update super properties
        mixpanel.updateSuperProperties { currentProps ->
            currentProps.put("sessionCount", currentProps.optInt("sessionCount", 0) + 1)
            currentProps
        }

        // Read current super properties
        val currentProps = mixpanel.superProperties
    }

    // ========================================
    // PEOPLE PROPERTIES
    // ========================================

    /**
     * Set user profile properties.
     */
    fun setPeopleProperties(mixpanel: Mixpanel) {
        mixpanel.people.setProperties {
            "name" to "John Doe"
            "email" to "john@example.com"
            "age" to 30
            "isPremium" to true
        }

        mixpanel.people.setOnceProperties {
            "createdAt" to System.currentTimeMillis()
            "signupSource" to "organic"
        }

        mixpanel.people.increment {
            "loginCount" by 1
            "totalSpent" by 29.99
            "points" by 100
        }

        mixpanel.people.trackCharge(amount = 29.99) {
            "sku" to "PREMIUM_MONTHLY"
            "currency" to "USD"
        }

        // Single property setters
        mixpanel.people.set("lastLogin", System.currentTimeMillis())
        mixpanel.people.setOnce("firstLogin", System.currentTimeMillis())

        // Check identity (use mixpanel.distinctId, not people.distinctId)
        val visitorId = mixpanel.distinctId
        val identified = mixpanel.people.isIdentified
    }

    // ========================================
    // GROUP ANALYTICS
    // ========================================

    /**
     * Group membership and properties.
     */
    fun setGroupProperties(mixpanel: Mixpanel) {
        // Associate user with a group
        mixpanel.setGroup(key = "Company", id = "Acme Inc")

        // Associate user with multiple groups
        mixpanel.setGroup(key = "Team", ids = listOf("Engineering", "Platform"))

        // Add to an additional group
        mixpanel.addGroup(key = "Team", id = "DevOps")

        // Remove from a group
        mixpanel.removeGroup(key = "Team", id = "Platform")

        // Set group properties using DSL
        mixpanel.group(key = "Company", id = "Acme Inc").setProperties {
            "name" to "Acme Inc"
            "industry" to "Technology"
            "employees" to 500
            "plan" to "enterprise"
        }

        // Set single property
        mixpanel.group(key = "Company", id = "Acme Inc").set("lastActivity", System.currentTimeMillis())

        mixpanel.group(key = "Company", id = "Acme Inc").setOnceProperties {
            "createdAt" to System.currentTimeMillis()
            "foundedYear" to 2020
        }
    }

    // ========================================
    // FEATURE FLAGS
    // ========================================

    /**
     * Use feature flags.
     */
    fun useFeatureFlags(
        mixpanel: Mixpanel,
        scope: CoroutineScope,
    ) {
        scope.launch {
            // Check if enabled
            val isEnabled = mixpanel.flags.isEnabled(name = "new_feature")
            if (isEnabled) {
                // Show new feature
            }

            // Get variant and extract typed value
            val variant = mixpanel.flags.getVariant(name = "theme")
            val theme = variant.value as? String ?: "light"

            // Get numeric values
            val maxItemsVariant = mixpanel.flags.getVariant(name = "max_items")
            val maxItems = (maxItemsVariant.value as? Number)?.toInt() ?: 10

            // Full variant info
            val experimentVariant = mixpanel.flags.getVariant(name = "experiment")
            println("Variant: ${experimentVariant.key} = ${experimentVariant.value}")
        }
    }

    // ========================================
    // COMPLETE EXAMPLE
    // ========================================

    /**
     * Complete usage example.
     */
    fun completeExample(
        context: Context,
        scope: CoroutineScope,
    ) {
        // Initialize
        val mixpanel =
            Mixpanel.getInstance(context, "YOUR_TOKEN") {
                featureFlags { enabled = true }
                superProperties("platform" to "android")
            }

        // Identify user
        mixpanel.identify("user_123")

        // Set user properties
        mixpanel.people.setProperties {
            "name" to "Jane Smith"
            "email" to "jane@example.com"
        }

        // Track events
        mixpanel.track(name = "Screen Viewed") {
            "screen" to "Home"
        }

        // Feature flags with coroutines
        scope.launch {
            val showNewFeature = mixpanel.flags.isEnabled(name = "new_home_design")
            if (showNewFeature) {
                mixpanel.track(name = "New Feature Shown") {
                    "feature" to "new_home_design"
                }
            }
        }

        // Track purchase
        mixpanel.track(name = "Purchase Completed") {
            "productId" to "sku_123"
            "price" to 19.99
        }

        mixpanel.people.trackCharge(amount = 19.99) {
            "productId" to "sku_123"
        }

        // Flush events
        mixpanel.flush()
    }

    // ========================================
    // ACCESSING JAVA API
    // ========================================

    /**
     * Access underlying Java API when needed.
     */
    fun accessJavaApi(mixpanel: Mixpanel) {
        // Access the underlying MixpanelAPI
        val javaApi = mixpanel.java

        // Use Java methods directly
        javaApi.trackMap("Event", mapOf("key" to "value"))

        // Access underlying People API
        val javaPeople = mixpanel.people.java

        // Access underlying Flags API
        val javaFlags = mixpanel.flags.java
    }

    private fun someCondition(): Boolean = true
}
