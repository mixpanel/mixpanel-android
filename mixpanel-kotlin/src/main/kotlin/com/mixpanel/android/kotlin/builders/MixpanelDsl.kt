package com.mixpanel.android.kotlin.builders

/**
 * DSL marker to prevent scope leakage in Mixpanel DSL builders.
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class MixpanelDsl
