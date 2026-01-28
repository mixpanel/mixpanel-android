package com.mixpanel.android.kotlin.builders

import com.mixpanel.android.mpmetrics.MixpanelOptions
import com.mixpanel.android.util.ProxyServerInteractor
import org.json.JSONObject

/**
 * Builder for Mixpanel configuration options.
 */
@MixpanelDsl
class MixpanelOptionsBuilder {
    /**
     * Instance name for multiple Mixpanel instances.
     */
    var instanceName: String? = null

    /**
     * Whether tracking is opted out by default.
     */
    var optOutTrackingDefault: Boolean = false

    private var serverURLValue: String? = null
    private var proxyServerInteractorValue: ProxyServerInteractor? = null
    private var featureFlagsEnabled: Boolean = false
    private var featureFlagsContext: JSONObject = JSONObject()
    private val superPropertiesMap = mutableMapOf<String, Any?>()

    /**
     * Sets a custom server URL for API requests.
     *
     * @param url The server URL
     * @param proxyInteractor Optional proxy interactor for custom headers
     */
    fun serverURL(
        url: String,
        proxyInteractor: ProxyServerInteractor? = null,
    ) {
        serverURLValue = url
        proxyServerInteractorValue = proxyInteractor
    }

    /**
     * Configures feature flags.
     */
    fun featureFlags(block: FeatureFlagsBuilder.() -> Unit) {
        val builder = FeatureFlagsBuilder().apply(block)
        featureFlagsEnabled = builder.enabled
        featureFlagsContext = builder.contextJson
    }

    /**
     * Sets initial super properties.
     */
    fun superProperties(vararg pairs: Pair<String, Any?>) {
        superPropertiesMap.putAll(pairs)
    }

    /**
     * Sets initial super properties using DSL.
     */
    fun superProperties(block: PropertyBuilder.() -> Unit) {
        superPropertiesMap.putAll(PropertyBuilder().apply(block).toMap())
    }

    internal fun build(): MixpanelOptions {
        val builder = MixpanelOptions.Builder()

        instanceName?.let { builder.instanceName(it) }
        builder.optOutTrackingDefault(optOutTrackingDefault)
        builder.featureFlagsEnabled(featureFlagsEnabled)
        builder.featureFlagsContext(featureFlagsContext)

        if (superPropertiesMap.isNotEmpty()) {
            builder.superProperties(JSONObject(superPropertiesMap))
        }

        serverURLValue?.let { url ->
            if (proxyServerInteractorValue != null) {
                builder.serverURL(url, proxyServerInteractorValue)
            } else {
                builder.serverURL(url)
            }
        }

        return builder.build()
    }
}
