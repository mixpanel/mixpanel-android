package com.mixpanel.android.eventbridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.ArrayDeque

/**
 * Central event dispatcher for Mixpanel analytics events.
 *
 * This singleton manages event listeners and dispatches events using Kotlin Flows.
 * Events are cached (up to 100) and can be replayed to new listeners.
 *
 * Thread Safety: Cache operations use synchronized blocks; event delivery uses coroutines.
 */
object MixpanelEventBridge {

    private const val MAX_CACHE_SIZE = 100

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val eventFlow = MutableSharedFlow<Map<String, Any?>>()
    private val eventCache = ArrayDeque<Map<String, Any?>>(MAX_CACHE_SIZE)
    private val cacheLock = Any()
    private val listenerJobs = mutableMapOf<MixpanelEventListener, Job>()
    private val listenersLock = Any()

    /**
     * Registers an event listener with the bridge.
     *
     * The listener will receive cached events (based on replayCount) followed by live events.
     * Duplicate registrations will cancel the previous subscription.
     *
     * @param listener The listener to register
     * @param replayCount Number of cached events to replay (0 to MAX_CACHE_SIZE, default 0)
     */
    @JvmStatic
    @JvmOverloads
    fun registerListener(listener: MixpanelEventListener, replayCount: Int = 0) {
        synchronized(listenersLock) {
            listenerJobs[listener]?.cancel()
        }

        val job = scope.launch {
            // Replay cached events
            val eventsToReplay = synchronized(cacheLock) {
                val count = replayCount.coerceIn(0, eventCache.size)
                eventCache.toList().takeLast(count)
            }

            eventsToReplay.forEach { event ->
                try {
                    listener.onEventTracked(event)
                } catch (e: Exception) {
                    // Never let listener errors interrupt event processing
                }
            }

            // Collect live events
            eventFlow.collect { event ->
                try {
                    listener.onEventTracked(event)
                } catch (e: Exception) {
                    // Never let listener errors interrupt event processing
                }
            }
        }

        synchronized(listenersLock) {
            listenerJobs[listener] = job
        }
    }

    /**
     * Unregisters an event listener from the bridge.
     *
     * @param listener The listener to unregister
     */
    @JvmStatic
    fun unregisterListener(listener: MixpanelEventListener) {
        synchronized(listenersLock) {
            listenerJobs.remove(listener)?.cancel()
        }
    }

    /**
     * Removes all registered listeners.
     */
    @JvmStatic
    fun removeAllListeners() {
        synchronized(listenersLock) {
            listenerJobs.values.forEach { it.cancel() }
            listenerJobs.clear()
        }
    }

    /**
     * Notifies all registered listeners of a tracked event.
     *
     * This method is called internally by MixpanelAPI after tracking an event.
     * The event is cached (up to 100 events) and emitted to the flow.
     *
     * @param eventName The name of the tracked event
     * @param properties The event properties as JSONObject
     */
    @JvmStatic
    fun notifyListeners(eventName: String, properties: JSONObject?) {
        val event = mapOf(
            "eventName" to eventName,
            "properties" to properties
        )

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
