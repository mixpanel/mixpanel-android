package com.mixpanel.android.sessionreplay.tracking

import com.mixpanel.android.sessionreplay.logging.Logger
import com.mixpanel.android.sessionreplay.models.RawScreenshotEvent
import com.mixpanel.android.sessionreplay.models.RawTouchEvent
import com.mixpanel.android.sessionreplay.models.SessionEvent
import com.mixpanel.android.sessionreplay.models.SessionEventData
import com.mixpanel.android.sessionreplay.services.EventService
import com.mixpanel.android.sessionreplay.utils.EventType
import com.mixpanel.android.sessionreplay.utils.IncrementalSource
import com.mixpanel.android.sessionreplay.utils.PayloadObjectId
import com.mixpanel.android.sessionreplay.utils.SessionReplayEncoder
import com.mixpanel.android.sessionreplay.utils.TimingAdjustment
import com.mixpanel.android.sessionreplay.utils.TouchInteraction
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

interface EventListener {
    fun receivedTouchEvent(rawEvent: RawTouchEvent)

    fun receivedScreenshotEvent(rawEvent: RawScreenshotEvent)
}

class EventHandler(
    private val eventService: EventService,
    private val eventPublisher: EventPublisher = EventPublisher.shared
) : EventListener {
    private lateinit var eventSerialQueue: ExecutorService

    /** Dimensions last stated by a meta event. Only ever touched from [eventSerialQueue]. */
    private var lastMetaDimensions: Pair<Int, Int>? = null

    /**
     * Subscribes this handler to the [EventPublisher].
     * This method is called automatically during initialization.
     */
    fun initialize() {
        eventSerialQueue = Executors.newSingleThreadExecutor()
        eventPublisher.subscribe(this)
    }

    fun deinitialize() {
        eventPublisher.unsubscribe(this)
        eventSerialQueue.shutdown() // Gracefully shutdown the executor
    }

    override fun receivedTouchEvent(rawEvent: RawTouchEvent) {
        eventSerialQueue.execute {
            val currentTimestamp = Date().time / 1000.0 // Convert to seconds
            val touchEvent = SessionEvent(
                type = EventType.INCREMENTAL_SNAPSHOT, // Use an enum or similar for types
                data = SessionEventData.DetailedData(
                    source = IncrementalSource.TOUCH_INTERACTION,
                    type = TouchInteraction.START,
                    id = PayloadObjectId.MAIN_SNAPSHOT,
                    x = rawEvent.start.x.toInt(),
                    y = rawEvent.start.y.toInt()
                ),
                timestamp = (currentTimestamp * 1000 + TimingAdjustment.TOUCH_INTERACTION).toLong()
            )
            Logger.debug("Received touch event: $touchEvent")
            eventService.enqueueEvent(touchEvent)
        }
    }

    override fun receivedScreenshotEvent(rawEvent: RawScreenshotEvent) {
        eventSerialQueue.execute {
            val event = if (rawEvent.isInitial) {
                SessionReplayEncoder.mainSessionEvent(rawEvent.data)
            } else {
                SessionReplayEncoder.incrementalSessionEvent(rawEvent.data)
            }
            event?.let {
                enqueueMetaIfDimensionsChanged(rawEvent, it.timestamp)
                eventService.enqueueEvent(it)
            }
        }
    }

    /**
     * Queues a meta event ahead of a frame whose dimensions the player does not have yet, sharing
     * the frame's [timestamp] so the two stay ordered together.
     */
    private fun enqueueMetaIfDimensionsChanged(
        rawEvent: RawScreenshotEvent,
        timestamp: Long
    ) {
        if (rawEvent.width <= 0 || rawEvent.height <= 0) {
            return
        }
        val dimensions = rawEvent.width to rawEvent.height
        if (dimensions == lastMetaDimensions) {
            return
        }
        eventService.enqueueEvent(
            SessionEvent(
                type = EventType.META,
                data = SessionEventData.DimensionData(rawEvent.width, rawEvent.height),
                timestamp = timestamp
            )
        )
        lastMetaDimensions = dimensions
    }

    /** Makes the next frame emit a meta event, so every replay opens with its dimensions. */
    fun resetViewportTracking() {
        eventSerialQueue.execute { lastMetaDimensions = null }
    }
}
