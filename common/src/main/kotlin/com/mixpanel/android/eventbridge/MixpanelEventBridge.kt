package com.mixpanel.android.eventbridge

import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/**
 * Central event dispatcher for Mixpanel analytics events.
 *
 * This singleton manages event listeners and dispatches events to them asynchronously.
 * Listeners are held as weak references to prevent memory leaks.
 *
 * Thread Safety: All operations are executed on a single-threaded executor,
 * ensuring serial execution and eliminating the need for locks.
 */
object MixpanelEventBridge {

    // private const val LOGTAG = "MixpanelAPI.EventBridge"

    /**
     * Single-threaded executor for serial event processing.
     * All listener operations and notifications happen on this thread.
     */
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * List of registered listeners stored as weak references.
     * Dead references are automatically cleaned up before each operation.
     */
    private val listeners = mutableListOf<WeakReference<MixpanelEventListener>>()

    /**
     * Registers an event listener with the bridge.
     *
     * The listener is stored as a weak reference. The caller must maintain
     * a strong reference to prevent premature garbage collection.
     *
     * Duplicate registrations are ignored (same object reference).
     *
     * @param listener The listener to register
     */
    @JvmStatic
    fun registerListener(listener: MixpanelEventListener) {
        executor.execute {
            cleanupDeadReferences()

            // Check for duplicate registration
            if (listeners.any { it.get() === listener }) {
                return@execute
            }

            listeners.add(WeakReference(listener))
            // MPLog.d(LOGTAG, "Event bridge registered listener.")
        }
    }

    /**
     * Unregisters an event listener from the bridge.
     *
     * @param listener The listener to unregister
     */
    @JvmStatic
    fun unregisterListener(listener: MixpanelEventListener) {
        executor.execute {
            listeners.removeAll { it.get() == null || it.get() === listener }
        }
    }

    /**
     * Removes all registered listeners.
     */
    @JvmStatic
    fun removeAllListeners() {
        executor.execute {
            listeners.clear()
        }
    }

    /**
     * Notifies all registered listeners of a tracked event.
     *
     * This method is called internally by MixpanelAPI after tracking an event.
     *
     * @param eventName The name of the tracked event
     * @param properties The event properties as JSONObject
     */
    @JvmStatic
    fun notifyListeners(eventName: String, properties: JSONObject?) {
        executor.execute {
            if (listeners.isEmpty()) {
                // MPLog.d(LOGTAG, "Event bridge has no listeners registered: event $eventName")
                return@execute
            }

            // Create event data map
            val event = mapOf<String, Any?>(
                "eventName" to eventName,
                "properties" to properties
            )

            listeners.mapNotNull { it.get() }.forEach { listener ->
                try {
                    listener.onEventTracked(event)
                    // MPLog.w(LOGTAG, "Event dispatched to event bridge - '$eventName'")
                } catch (e: Exception) {
                    // Never let listener errors interrupt event processing
                    // MPLog.w(LOGTAG, "Event bridge listener failed for event '$eventName': ${e.message}")
                }
            }
        }
    }

    /**
     * Removes listeners that have been garbage collected.
     */
    private fun cleanupDeadReferences() {
        listeners.removeAll { it.get() == null }
    }
}
