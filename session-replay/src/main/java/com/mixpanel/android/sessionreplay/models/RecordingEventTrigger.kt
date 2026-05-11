package com.mixpanel.android.sessionreplay.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Represents a single event trigger configuration from Settings API.
 *
 * An event trigger defines conditions under which session replay recording
 * should be automatically started when a specific event is tracked.
 *
 * @property percentage Sampling percentage (0-100). Required.
 * @property propertyFilters Optional JSONLogic expression for property filtering.
 */
@Serializable
data class RecordingEventTrigger(
    /**
     * Sampling percentage (0-100). Determines what percentage of events
     * matching this trigger should actually start recording.
     */
    val percentage: Double,

    /**
     * Optional JSONLogic expression for property filtering.
     * If null, no property filtering is applied (all events with this name match).
     * If present, the event properties must satisfy the JSONLogic expression.
     */
    @SerialName("property_filters")
    val propertyFilters: JsonElement? = null
)
