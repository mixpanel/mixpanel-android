package com.mixpanel.android.sessionreplay.services

import com.mixpanel.android.sessionreplay.models.SessionEvent
import com.mixpanel.android.sessionreplay.tracking.EventHandler
import com.mixpanel.android.sessionreplay.utils.EventType
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock
import kotlin.math.min

class EventService(
    private val queueSizeLimit: Int = 1000
) {
    private var eventHandler: EventHandler? = null // Nullable to allow for deinitialization
    private val events = mutableListOf<SessionEvent>()
    private val readWriteLock = ReentrantReadWriteLock() // Using ReentrantReadWriteLock

    // initialize method
    fun initialize() {
        eventHandler = EventHandler(this) // Initialize eventHandler
        eventHandler?.initialize()
    }

    /** Makes the next captured frame emit a meta event, so every replay opens with its dimensions. */
    fun resetViewportTracking() {
        eventHandler?.resetViewportTracking()
    }

    // deinitialize method
    fun deinitialize() {
        eventHandler?.deinitialize()
        eventHandler = null // Clear the reference to allow garbage collection
        clearEvents()
    }

    private fun firstFullSnapshotIndex(events: List<SessionEvent>): Int {
        for ((index, event) in events.withIndex()) {
            if (event.type == EventType.FULL_SNAPSHOT) {
                return index
            }
        }
        return -1
    }

    private fun containsFullSnapshot(events: List<SessionEvent>): Boolean = firstFullSnapshotIndex(events) >= 0

    private fun evictEvents(numEvents: Int = 1) {
        val currentEvents =
            readWriteLock.readLock().withLock {
                events.toList() // Make a copy under read lock
            }
        // A meta event retained by an earlier eviction is re-stated below rather than dropped, so
        // it cannot count towards the quota — otherwise eviction frees no room and the queue grows.
        val retainedMeta = if (currentEvents.firstOrNull()?.type == EventType.META) 1 else 0
        var eventsToEvict = min(numEvents + retainedMeta, currentEvents.size)

        val candidateEventsToEvict = currentEvents.subList(0, eventsToEvict)
        val candidateEventsRemain = currentEvents.subList(eventsToEvict, currentEvents.size)

        val nextFullSnapshotIndex = firstFullSnapshotIndex(candidateEventsRemain)
        if (containsFullSnapshot(candidateEventsToEvict) && nextFullSnapshotIndex >= 0) {
            eventsToEvict += nextFullSnapshotIndex
        }

        val evictedMeta = currentEvents.subList(0, eventsToEvict).lastOrNull { it.type == EventType.META }

        readWriteLock.writeLock().withLock {
            repeat(eventsToEvict) { events.removeAt(0) }
            // A meta event states the viewport for every frame after it, so dropping one without
            // re-stating it leaves the surviving frames with no dimensions to scale to.
            if (evictedMeta != null && events.firstOrNull()?.type != EventType.META) {
                events.add(0, evictedMeta)
            }
        }
    }

    fun enqueueEvent(event: SessionEvent) {
        val currentEvents =
            readWriteLock.readLock().withLock { events.size } // Get size under read lock
        if (currentEvents >= queueSizeLimit) {
            evictEvents()
        }
        readWriteLock.writeLock().withLock {
            events.add(event)
        }
    }

    val eventsCount: Int
        get() = readWriteLock.readLock().withLock { events.size }

    val isEventsEmpty: Boolean
        get() = readWriteLock.readLock().withLock { events.isEmpty() }

    fun dequeueEvents(numEvents: Int): List<SessionEvent> {
        val currentEvents = readWriteLock.readLock().withLock { events.toList() }
        val dequeuedEvents = currentEvents.subList(0, min(currentEvents.size, numEvents))

        readWriteLock.writeLock().withLock {
            events.removeAll(dequeuedEvents)
        }
        return dequeuedEvents
    }

    fun prependEvents(newEvents: List<SessionEvent>) {
        val currentEvents = readWriteLock.readLock().withLock { events.size }
        if (currentEvents + newEvents.size > queueSizeLimit) {
            evictEvents(newEvents.size)
        }
        readWriteLock.writeLock().withLock {
            events.addAll(0, newEvents)
        }
    }

    fun clearEvents() {
        readWriteLock.writeLock().withLock {
            events.clear()
        }
    }
}
