package com.mixpanel.android.eventbridge

/**
 * Interface for listening to Mixpanel analytics events.
 *
 * Components that want to receive notifications when events are tracked
 * should implement this interface and register with [MixpanelEventBridge].
 *
 * The bridge holds weak references to listeners, so implementers must
 * maintain a strong reference to prevent premature garbage collection.
 */
fun interface MixpanelEventListener {
    /**
     * Called when an event is tracked in the Mixpanel SDK.
     *
     * This method is called asynchronously on the bridge's executor thread.
     * Implementations should not block this thread for long periods.
     *
     * @param event Event data map with guaranteed keys:
     *              - "eventName" (String): The name of the tracked event
     *              - "properties" (JSONObject): The event properties
     */
    fun onEventTracked(event: Map<String, Any?>)
}
