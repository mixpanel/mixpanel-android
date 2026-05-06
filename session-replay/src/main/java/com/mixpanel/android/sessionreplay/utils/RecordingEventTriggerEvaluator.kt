package com.mixpanel.android.sessionreplay.utils

import com.mixpanel.android.jsonlogic.JsonLogicEvaluator
import com.mixpanel.android.jsonlogic.JsonLogicParser
import com.mixpanel.android.sessionreplay.logging.Logger
import com.mixpanel.android.sessionreplay.models.RecordingEventTrigger
import kotlinx.serialization.json.JsonElement
import org.json.JSONObject

/**
 * Evaluates whether an event matches trigger conditions.
 *
 * This class is responsible for determining if a tracked event should
 * trigger session replay recording based on:
 * 1. Event name match with configured triggers
 * 2. Property filters (JSONLogic expressions)
 *
 * Note: Sampling is handled by startRecording() using the returned percentage.
 */
class RecordingEventTriggerEvaluator(
    private val triggers: Map<String, RecordingEventTrigger>
) {

    /**
     * Evaluate if an event should start recording.
     *
     * @param eventName The name of the tracked event
     * @param properties The event properties as JSONObject
     * @return Sampling percentage if conditions pass, null if should skip recording
     */
    fun shouldStartRecording(
        eventName: String,
        properties: JSONObject
    ): Double? {
        // 1. Check if event has a registered trigger
        val trigger = triggers[eventName] ?: run {
            Logger.debug("[SessionReplay] No trigger configured for event: $eventName")
            return null
        }

        // 2. Check property filters (if present)
        trigger.propertyFilters?.let { filters ->
            if (!passesPropertyFilters(filters, properties)) {
                Logger.debug("[SessionReplay] Event '$eventName' failed property filters")
                return null
            }
        }

        // 3. Validate percentage is within expected range
        val percentage = trigger.percentage
        if (percentage !in 0.0..100.0) {
            Logger.warn("[SessionReplay] Invalid trigger percentage for event '$eventName': $percentage (expected 0-100)")
            return null
        }

        // Trigger matched - return percentage for sampling by startRecording()
        Logger.debug("[SessionReplay] Event '$eventName' passed trigger evaluation ($percentage% sampling)")
        return percentage
    }

    /**
     * Evaluate JSONLogic expression against properties.
     *
     * @param filters JSONLogic expression
     * @param properties Event properties as JSONObject
     * @return true if filters pass, false if they fail or error occurs
     */
    private fun passesPropertyFilters(
        filters: JsonElement,
        properties: JSONObject
    ): Boolean = try {
        val rule = JsonLogicParser.parse(filters.toString())
        val result = JsonLogicEvaluator.evaluate(rule, properties)
        result as? Boolean ?: false
    } catch (e: Exception) {
        // Log error and fail closed (don't start recording on filter errors)
        Logger.error("[SessionReplay] JSONLogic evaluation error: ${e.message}")
        false
    }
}
