package com.mixpanel.android.eventbridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.ArrayDeque

/**
 * Represents a tracked Mixpanel event.
 *
 * @property eventName The name of the tracked event
 * @property properties The event properties, or null if none
 */
data class MixpanelEvent(
    val eventName: String,
    val properties: JSONObject?
)

/**
 * Central event dispatcher for Mixpanel analytics events.
 *
 * This singleton dispatches events using Kotlin Flows.
 * Events are cached (up to 100) and can be replayed to new collectors.
 *
 * Thread Safety: Cache operations use synchronized blocks; event delivery uses coroutines.
 */
object MixpanelEventBridge {

    private const val MAX_CACHE_SIZE = 100

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val eventFlow = MutableSharedFlow<MixpanelEvent>()
    private val eventCache = ArrayDeque<MixpanelEvent>(MAX_CACHE_SIZE)
    private val cacheLock = Any()

    /**
     * Returns a Flow of events with optional replay of cached events.
     *
     * The caller is responsible for collecting this flow in their own CoroutineScope.
     * Cancellation is automatic when the collecting scope is canceled.
     *
     * @param replayCount Number of cached events to replay (0 to MAX_CACHE_SIZE, default 0)
     * @return Flow emitting [MixpanelEvent] instances
     */
    @JvmStatic
    @JvmOverloads
    fun events(replayCount: Int = 0): Flow<MixpanelEvent> = flow {
        // Emit cached events first
        val cached = synchronized(cacheLock) {
            val count = replayCount.coerceIn(0, eventCache.size)
            eventCache.toList().takeLast(count)
        }
        cached.forEach { emit(it) }

        // Then collect live events
        eventFlow.collect { emit(it) }
    }

    /**
     * Notifies all collectors of a tracked event.
     *
     * This method is called internally by MixpanelAPI after tracking an event.
     * The event is cached (up to 100 events) and emitted to the flow.
     *
     * @param eventName The name of the tracked event
     * @param properties The event properties as JSONObject
     */
    @JvmStatic
    fun notifyListeners(eventName: String, properties: JSONObject?) {
        val event = MixpanelEvent(eventName, properties)

        synchronized(cacheLock) {
            if (eventCache.size >= MAX_CACHE_SIZE) {
                eventCache.removeFirst()
            }
            eventCache.addLast(event)
        }

        scope.launch {
            eventFlow.emit(event)
        }
    }
}
