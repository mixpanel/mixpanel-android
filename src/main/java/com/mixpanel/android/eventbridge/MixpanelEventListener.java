package com.mixpanel.android.eventbridge;

import java.util.Map;

/**
 * Interface for listening to Mixpanel analytics events.
 *
 * Components that want to receive notifications when events are tracked
 * should implement this interface and register with {@link MixpanelEventBridge}.
 *
 * The bridge holds weak references to listeners, so implementers must
 * maintain a strong reference to prevent premature garbage collection.
 */
public interface MixpanelEventListener {
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
    void onEventTracked(Map<String, Object> event);
}
