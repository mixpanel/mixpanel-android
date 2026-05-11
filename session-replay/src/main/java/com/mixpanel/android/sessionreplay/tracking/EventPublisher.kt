package com.mixpanel.android.sessionreplay.tracking

import com.mixpanel.android.sessionreplay.models.RawScreenshotEvent
import com.mixpanel.android.sessionreplay.models.RawTouchEvent

class EventPublisher {
    companion object {
        val shared = EventPublisher()
    }

    private constructor() // Private constructor to enforce singleton pattern

    private val subscribers = mutableListOf<EventListener>()

    fun subscribe(subscriber: EventListener) {
        subscribers.add(subscriber)
    }

    fun unsubscribe(subscriber: EventListener) {
        subscribers.remove(subscriber)
    }

    fun publishTouchEvent(event: RawTouchEvent) {
        subscribers.forEach { it.receivedTouchEvent(event) }
    }

    fun publishSessionEvent(event: RawScreenshotEvent) {
        subscribers.forEach { it.receivedScreenshotEvent(event) }
    }
}
