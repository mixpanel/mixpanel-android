package com.mixpanel.android.openfeature

import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.android.mpmetrics.MixpanelFlagVariant
import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.FeatureProvider
import dev.openfeature.kotlin.sdk.Hook
import dev.openfeature.kotlin.sdk.ProviderEvaluation
import dev.openfeature.kotlin.sdk.ProviderMetadata
import dev.openfeature.kotlin.sdk.Reason
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.exceptions.ErrorCode

/**
 * An OpenFeature provider backed by the Mixpanel Android SDK's feature flags.
 *
 * @param flags The [MixpanelAPI.Flags] instance obtained via `mixpanel.getFlags()`.
 */
class MixpanelProvider(private val flags: MixpanelAPI.Flags) : FeatureProvider {

    override val hooks: List<Hook<*>> = emptyList()

    override val metadata: ProviderMetadata = MixpanelProviderMetadata()

    override suspend fun initialize(initialContext: EvaluationContext?) {
        // No-op: the Mixpanel SDK manages its own initialization.
    }

    override suspend fun onContextSet(oldContext: EvaluationContext?, newContext: EvaluationContext) {
        // No-op: context is managed by the Mixpanel SDK via mixpanel.identify().
    }

    override fun shutdown() {
        // No-op: the Mixpanel SDK manages its own lifecycle.
    }

    override fun getBooleanEvaluation(
        key: String,
        defaultValue: Boolean,
        context: EvaluationContext?
    ): ProviderEvaluation<Boolean> {
        if (!flags.areFlagsReady()) {
            return providerNotReady(defaultValue)
        }

        val fallback = MixpanelFlagVariant(defaultValue as Any)
        val variant = flags.getVariantSync(key, fallback)

        if (variant === fallback) {
            return flagNotFound(defaultValue)
        }

        val value = variant.value
        if (value is Boolean) {
            return success(value, variant.key)
        }

        return typeMismatch(defaultValue)
    }

    override fun getStringEvaluation(
        key: String,
        defaultValue: String,
        context: EvaluationContext?
    ): ProviderEvaluation<String> {
        if (!flags.areFlagsReady()) {
            return providerNotReady(defaultValue)
        }

        val fallback = MixpanelFlagVariant(defaultValue as Any)
        val variant = flags.getVariantSync(key, fallback)

        if (variant === fallback) {
            return flagNotFound(defaultValue)
        }

        val value = variant.value
        if (value is String) {
            return success(value, variant.key)
        }

        return typeMismatch(defaultValue)
    }

    override fun getIntegerEvaluation(
        key: String,
        defaultValue: Int,
        context: EvaluationContext?
    ): ProviderEvaluation<Int> {
        if (!flags.areFlagsReady()) {
            return providerNotReady(defaultValue)
        }

        val fallback = MixpanelFlagVariant(defaultValue as Any)
        val variant = flags.getVariantSync(key, fallback)

        if (variant === fallback) {
            return flagNotFound(defaultValue)
        }

        val value = variant.value
        val variantKey = variant.key
        when (value) {
            is Int -> return success(value, variantKey)
            is Long -> return success(value.toInt(), variantKey)
            is Double -> {
                if (value == Math.floor(value) && !value.isInfinite()) {
                    return success(value.toInt(), variantKey)
                }
            }
            is Float -> {
                val d = value.toDouble()
                if (d == Math.floor(d) && !d.isInfinite()) {
                    return success(value.toInt(), variantKey)
                }
            }
        }

        return typeMismatch(defaultValue)
    }

    override fun getDoubleEvaluation(
        key: String,
        defaultValue: Double,
        context: EvaluationContext?
    ): ProviderEvaluation<Double> {
        if (!flags.areFlagsReady()) {
            return providerNotReady(defaultValue)
        }

        val fallback = MixpanelFlagVariant(defaultValue as Any)
        val variant = flags.getVariantSync(key, fallback)

        if (variant === fallback) {
            return flagNotFound(defaultValue)
        }

        val value = variant.value
        if (value is Number) {
            return success(value.toDouble(), variant.key)
        }

        return typeMismatch(defaultValue)
    }

    override fun getObjectEvaluation(
        key: String,
        defaultValue: Value,
        context: EvaluationContext?
    ): ProviderEvaluation<Value> {
        if (!flags.areFlagsReady()) {
            return providerNotReady(defaultValue)
        }

        val fallback = MixpanelFlagVariant("", null)
        val variant = flags.getVariantSync(key, fallback)

        if (variant === fallback) {
            return flagNotFound(defaultValue)
        }

        return success(toValue(variant.value), variant.key)
    }

    // --- Helpers ---

    private fun <T> success(value: T, variant: String? = null): ProviderEvaluation<T> {
        return ProviderEvaluation(
            value = value,
            variant = variant,
            reason = Reason.STATIC.toString()
        )
    }

    private fun <T> providerNotReady(defaultValue: T): ProviderEvaluation<T> {
        return ProviderEvaluation(
            value = defaultValue,
            errorCode = ErrorCode.PROVIDER_NOT_READY,
            reason = Reason.ERROR.toString()
        )
    }

    private fun <T> flagNotFound(defaultValue: T): ProviderEvaluation<T> {
        return ProviderEvaluation(
            value = defaultValue,
            errorCode = ErrorCode.FLAG_NOT_FOUND,
            reason = Reason.ERROR.toString()
        )
    }

    private fun <T> typeMismatch(defaultValue: T): ProviderEvaluation<T> {
        return ProviderEvaluation(
            value = defaultValue,
            errorCode = ErrorCode.TYPE_MISMATCH,
            reason = Reason.ERROR.toString()
        )
    }

    private fun toValue(obj: Any?): Value {
        return when (obj) {
            null -> Value.Null
            is Boolean -> Value.Boolean(obj)
            is String -> Value.String(obj)
            is Int -> Value.Integer(obj)
            is Long -> Value.Integer(obj.toInt())
            is Double -> Value.Double(obj)
            is Float -> Value.Double(obj.toDouble())
            else -> Value.String(obj.toString())
        }
    }

    private class MixpanelProviderMetadata : ProviderMetadata {
        override val name: String = "mixpanel-provider"
    }
}
