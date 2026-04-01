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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * An OpenFeature provider backed by the Mixpanel Android SDK's feature flags.
 *
 * @param flags The [MixpanelAPI.Flags] instance obtained via `mixpanel.getFlags()`.
 */
class MixpanelProvider(private val flags: MixpanelAPI.Flags) : FeatureProvider {

    override val hooks: List<Hook<*>> = emptyList()

    override val metadata: ProviderMetadata = MixpanelProviderMetadata()

    override suspend fun initialize(initialContext: EvaluationContext?) {
        if (initialContext != null) {
            applyContext(initialContext)
        }
    }

    override suspend fun onContextSet(oldContext: EvaluationContext?, newContext: EvaluationContext) {
        applyContext(newContext)
    }

    private suspend fun applyContext(context: EvaluationContext) {
        val contextMap = evaluationContextToMap(context)
        suspendCoroutine<Unit> { continuation ->
            flags.setContext(contextMap) { continuation.resume(Unit) }
        }
    }

    override fun shutdown() {
        // No-op: the Mixpanel SDK manages its own lifecycle.
    }

    override fun getBooleanEvaluation(
        key: String,
        defaultValue: Boolean,
        context: EvaluationContext?
    ): ProviderEvaluation<Boolean> {
        return resolveFlag(key, defaultValue) { variant ->
            val value = variant.value
            if (value is Boolean) {
                success(value, variant.key)
            } else {
                typeMismatch(key, "Boolean", value, defaultValue)
            }
        }
    }

    override fun getStringEvaluation(
        key: String,
        defaultValue: String,
        context: EvaluationContext?
    ): ProviderEvaluation<String> {
        return resolveFlag(key, defaultValue) { variant ->
            val value = variant.value
            if (value is String) {
                success(value, variant.key)
            } else {
                typeMismatch(key, "String", value, defaultValue)
            }
        }
    }

    override fun getIntegerEvaluation(
        key: String,
        defaultValue: Int,
        context: EvaluationContext?
    ): ProviderEvaluation<Int> {
        return resolveFlag(key, defaultValue) { variant ->
            val value = variant.value
            val variantKey = variant.key
            when (value) {
                is Int -> success(value, variantKey)
                is Long -> success(value.toInt(), variantKey)
                is Double -> {
                    if (value == Math.floor(value) && !value.isInfinite()) {
                        success(value.toInt(), variantKey)
                    } else {
                        typeMismatch(key, "Int", value, defaultValue)
                    }
                }
                is Float -> {
                    val d = value.toDouble()
                    if (d == Math.floor(d) && !d.isInfinite()) {
                        success(value.toInt(), variantKey)
                    } else {
                        typeMismatch(key, "Int", value, defaultValue)
                    }
                }
                else -> typeMismatch(key, "Int", value, defaultValue)
            }
        }
    }

    override fun getDoubleEvaluation(
        key: String,
        defaultValue: Double,
        context: EvaluationContext?
    ): ProviderEvaluation<Double> {
        return resolveFlag(key, defaultValue) { variant ->
            val value = variant.value
            if (value is Number) {
                success(value.toDouble(), variant.key)
            } else {
                typeMismatch(key, "Double", value, defaultValue)
            }
        }
    }

    override fun getObjectEvaluation(
        key: String,
        defaultValue: Value,
        context: EvaluationContext?
    ): ProviderEvaluation<Value> {
        return resolveFlag(key, defaultValue, fallbackVariant = MixpanelFlagVariant("", null)) { variant ->
            success(toValue(variant.value), variant.key)
        }
    }

    // --- Helpers ---

    /**
     * Shared resolution logic for all flag evaluation methods.
     * Handles readiness checks, fallback creation, exception handling, and flag-not-found detection.
     * The [coerce] block is only called when a flag variant is successfully found.
     */
    private fun <T> resolveFlag(
        key: String,
        defaultValue: T,
        fallbackVariant: MixpanelFlagVariant = MixpanelFlagVariant(defaultValue as Any),
        coerce: (MixpanelFlagVariant) -> ProviderEvaluation<T>
    ): ProviderEvaluation<T> {
        if (!flags.areFlagsReady()) {
            return providerNotReady(defaultValue)
        }

        val variant: MixpanelFlagVariant
        try {
            variant = flags.getVariantSync(key, fallbackVariant)
        } catch (e: Exception) {
            return generalError(defaultValue, e.message)
        }

        if (variant === fallbackVariant) {
            return flagNotFound(key, defaultValue)
        }

        return coerce(variant)
    }

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
            reason = Reason.ERROR.toString(),
            errorMessage = "Flags not ready"
        )
    }

    private fun <T> flagNotFound(flagKey: String, defaultValue: T): ProviderEvaluation<T> {
        return ProviderEvaluation(
            value = defaultValue,
            errorCode = ErrorCode.FLAG_NOT_FOUND,
            reason = Reason.ERROR.toString(),
            errorMessage = "Flag \"$flagKey\" not found"
        )
    }

    private fun <T> typeMismatch(
        flagKey: String,
        expectedType: String,
        actualValue: Any?,
        defaultValue: T
    ): ProviderEvaluation<T> {
        val actualType = actualValue?.let { it::class.simpleName } ?: "null"
        return ProviderEvaluation(
            value = defaultValue,
            errorCode = ErrorCode.TYPE_MISMATCH,
            reason = Reason.ERROR.toString(),
            errorMessage = "Flag \"$flagKey\" value is not a $expectedType: $actualType"
        )
    }

    private fun <T> generalError(defaultValue: T, message: String? = null): ProviderEvaluation<T> {
        return ProviderEvaluation(
            value = defaultValue,
            errorCode = ErrorCode.GENERAL,
            errorMessage = message ?: "Unexpected error during flag evaluation",
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
            is Map<*, *> -> Value.Structure(
                obj.entries.associate { (k, v) -> k.toString() to toValue(v) }
            )
            is Iterable<*> -> Value.List(obj.map { toValue(it) })
            else -> Value.String(obj.toString())
        }
    }

    private fun evaluationContextToMap(ctx: EvaluationContext): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val targetingKey = ctx.getTargetingKey()
        if (targetingKey.isNotEmpty()) {
            map["targetingKey"] = targetingKey
        }
        for (key in ctx.keySet()) {
            map[key] = valueToAny(ctx.getValue(key))
        }
        return map
    }

    private fun valueToAny(value: Value?): Any? {
        if (value == null || value.isNull()) return null
        return when (value) {
            is Value.Boolean -> value.asBoolean()
            is Value.String -> value.asString()
            is Value.Integer -> value.asInteger()
            is Value.Double -> value.asDouble()
            is Value.List -> value.asList()?.map { valueToAny(it) }
            is Value.Structure -> value.asStructure()?.mapValues { (_, v) -> valueToAny(v) }
            is Value.Instant -> value.asString()
            else -> value.asString()
        }
    }

    private class MixpanelProviderMetadata : ProviderMetadata {
        override val name: String = "mixpanel-provider"
    }
}
