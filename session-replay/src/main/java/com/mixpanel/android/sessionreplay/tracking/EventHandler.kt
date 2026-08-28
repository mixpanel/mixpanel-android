package com.mixpanel.android.sessionreplay.tracking

import com.mixpanel.android.sessionreplay.logging.Logger
import com.mixpanel.android.sessionreplay.models.RawScreenshotEvent
import com.mixpanel.android.sessionreplay.models.RawTouchEvent
import com.mixpanel.android.sessionreplay.models.SessionEvent
import com.mixpanel.android.sessionreplay.models.SessionEventData
import com.mixpanel.android.sessionreplay.models.SessionPosition
import com.mixpanel.android.sessionreplay.services.EventService
import com.mixpanel.android.sessionreplay.utils.EventType
import com.mixpanel.android.sessionreplay.utils.IncrementalSource
import com.mixpanel.android.sessionreplay.utils.PayloadObjectId
import com.mixpanel.android.sessionreplay.utils.SessionReplayEncoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

interface EventListener {
    fun receivedTouchEvent(rawEvent: RawTouchEvent)

    fun receivedScreenshotEvent(rawEvent: RawScreenshotEvent)

    /**
     * Receives a fully-formed [SessionEvent] published via [EventPublisher.publishCustomEvent].
     * Default no-op so existing implementations don't need to change.
     */
    fun receivedCustomEvent(event: SessionEvent) {}
}

class EventHandler(
    private val eventService: EventService,
    private val eventPublisher: EventPublisher = EventPublisher.shared
) : EventListener {
    private lateinit var eventSerialQueue: ExecutorService

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
            val touchEvent = when (rawEvent) {
                is RawTouchEvent.Interaction -> SessionEvent(
                    type = EventType.INCREMENTAL_SNAPSHOT,
                    data = SessionEventData.DetailedData(
                        source = IncrementalSource.MOUSE_INTERACTION,
                        type = rawEvent.type,
                        id = PayloadObjectId.MAIN_SNAPSHOT,
                        x = rawEvent.point.x,
                        y = rawEvent.point.y
                    ),
                    timestamp = rawEvent.timestamp
                )

                is RawTouchEvent.Move -> SessionEvent(
                    type = EventType.INCREMENTAL_SNAPSHOT,
                    data = SessionEventData.PositionData(
                        source = IncrementalSource.TOUCH_MOVE,
                        positions = rawEvent.samples.map { sample ->
                            SessionPosition(
                                x = sample.point.x.toDouble(),
                                y = sample.point.y.toDouble(),
                                id = PayloadObjectId.MAIN_SNAPSHOT,
                                // rrweb replays a sample at `event.timestamp + timeOffset`, so
                                // offsets are <= 0 against the batch's final sample.
                                timeOffset = (sample.timestamp - rawEvent.timestamp).toInt()
                            )
                        }
                    ),
                    timestamp = rawEvent.timestamp
                )
            }
            Logger.debug("Received touch event: $touchEvent")
            eventService.enqueueEvent(touchEvent)
        }
    }

    override fun receivedScreenshotEvent(rawEvent: RawScreenshotEvent) {
        eventSerialQueue.execute {
            // Stamp with the frame's capture time, not now — this runs on the serial queue,
            // behind however many events are already in front of it.
            val event = if (rawEvent.isInitial) {
                SessionReplayEncoder.mainSessionEvent(rawEvent.data, rawEvent.timestamp)
            } else {
                SessionReplayEncoder.incrementalSessionEvent(rawEvent.data, rawEvent.timestamp)
            }
            event?.let { eventService.enqueueEvent(it) }
        }
    }

    override fun receivedCustomEvent(event: SessionEvent) {
        eventSerialQueue.execute {
            Logger.debug { "Received custom session event: type=${event.type}" }
            eventService.enqueueEvent(event)
        }
    }
}
