package com.mixpanel.android.eventbridge

import androidx.annotation.RestrictTo
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

/**
 * Represents a tracked Mixpanel event.
 *
 * @property eventName The name of the tracked event
 * @property properties The event properties, or null if none
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class MixpanelEvent(
    val eventName: String,
    val properties: JSONObject?
)

/**
 * Central event dispatcher for Mixpanel analytics events.
 *
 * This singleton dispatches events using Kotlin SharedFlow.
 * Events are not cached - only active collectors receive events.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object MixpanelEventBridge {
    private val eventFlow = MutableSharedFlow<MixpanelEvent>(
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        extraBufferCapacity = 10
    )

    /**
     * Returns a SharedFlow of events.
     */
    fun events(): SharedFlow<MixpanelEvent> = eventFlow.asSharedFlow()

    /**
     * Emits an event to all collectors.
     *
     * This method is called internally by MixpanelAPI after tracking an event.
     *
     * @param eventName The name of the tracked event
     * @param properties The event properties as JSONObject
     */
    @JvmStatic
    fun notifyListeners(
        eventName: String,
        properties: JSONObject?
    ) {
        eventFlow.tryEmit(MixpanelEvent(eventName, properties))
    }
}
