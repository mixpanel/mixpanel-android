package com.mixpanel.android.kotlin.builders

import org.json.JSONObject

/**
 * Builder for feature flags configuration.
 */
@MixpanelDsl
class FeatureFlagsBuilder {
    /**
     * Whether feature flags are enabled.
     */
    var enabled: Boolean = false

    private val contextProperties = mutableMapOf<String, Any?>()

    /**
     * Sets context properties for flag evaluation.
     */
    fun context(vararg pairs: Pair<String, Any?>) {
        contextProperties.putAll(pairs)
    }

    internal val contextJson: JSONObject
        get() = JSONObject(contextProperties)
}
