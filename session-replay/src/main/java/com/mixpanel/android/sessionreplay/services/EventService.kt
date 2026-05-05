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
        var eventsToEvict = min(numEvents, currentEvents.size)

        val candidateEventsToEvict = currentEvents.subList(0, eventsToEvict)
        val candidateEventsRemain = currentEvents.subList(eventsToEvict, currentEvents.size)

        val nextFullSnapshotIndex = firstFullSnapshotIndex(candidateEventsRemain)
        if (containsFullSnapshot(candidateEventsToEvict) && nextFullSnapshotIndex >= 0) {
            eventsToEvict += nextFullSnapshotIndex
        }

        readWriteLock.writeLock().withLock {
            repeat(eventsToEvict) { events.removeAt(0) }
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
