package com.mixpanel.android.eventbridge;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Central event dispatcher for Mixpanel analytics events.
 *
 * This singleton manages event listeners and dispatches events to them asynchronously.
 * Listeners are held as weak references to prevent memory leaks.
 *
 * Thread Safety: All operations are executed on a single-threaded executor,
 * ensuring serial execution and eliminating the need for locks.
 */
public final class MixpanelEventBridge {

    // private static final String LOGTAG = "MixpanelAPI.EventBridge";

    /**
     * Single-threaded executor for serial event processing.
     * All listener operations and notifications happen on this thread.
     */
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * List of registered listeners stored as weak references.
     * Dead references are automatically cleaned up before each operation.
     */
    private static final List<WeakReference<MixpanelEventListener>> listeners = new ArrayList<>();

    /**
     * Private constructor to prevent instantiation.
     */
    private MixpanelEventBridge() {
    }

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
    public static void registerListener(final MixpanelEventListener listener) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                cleanupDeadReferences();

                // Check for duplicate registration
                for (WeakReference<MixpanelEventListener> ref : listeners) {
                    MixpanelEventListener existing = ref.get();
                    if (existing != null && existing == listener) {
                        return;
                    }
                }

                listeners.add(new WeakReference<>(listener));
                // MPLog.d(LOGTAG, "Event bridge registered listener.");
            }
        });
    }

    /**
     * Unregisters an event listener from the bridge.
     *
     * @param listener The listener to unregister
     */
    public static void unregisterListener(final MixpanelEventListener listener) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Iterator<WeakReference<MixpanelEventListener>> iterator = listeners.iterator();
                while (iterator.hasNext()) {
                    final WeakReference<MixpanelEventListener> ref = iterator.next();
                    final MixpanelEventListener existing = ref.get();
                    if (existing == null || existing == listener) {
                        iterator.remove();
                    }
                }
            }
        });
    }

    /**
     * Removes all registered listeners.
     */
    public static void removeAllListeners() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                listeners.clear();
            }
        });
    }

    /**
     * Notifies all registered listeners of a tracked event.
     *
     * This method is called internally by MixpanelAPI after tracking an event.
     *
     * @param eventName The name of the tracked event
     * @param properties The event properties as JSONObject
     */
    public static void notifyListeners(
            final String eventName,
            final JSONObject properties
    ) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (listeners.isEmpty()) {
                    // MPLog.d(LOGTAG, "Event bridge has no listeners registered: event " + eventName);
                    return;
                }

                // Create event data map instead of MixpanelEventBridgeEvent
                final Map<String, Object> event = new HashMap<>(2);
                event.put("eventName", eventName);
                event.put("properties", properties);

                for (WeakReference<MixpanelEventListener> listenerRef : listeners) {
                    final MixpanelEventListener listener = listenerRef.get();
                    if (listener != null) {
                        try {
                            listener.onEventTracked(event);
                            // MPLog.w(LOGTAG, "Event dispatched to event bridge - '" + eventName + "'");
                        } catch (Exception e) {
                            // Never let listener errors interrupt event processing
                            // MPLog.w(LOGTAG, "Event bridge listener failed for event '" + eventName + "': " + e.getMessage());
                        }
                    }
                }
            }
        });
    }

    /**
     * Removes listeners that have been garbage collected.
     */
    private static void cleanupDeadReferences() {
        final Iterator<WeakReference<MixpanelEventListener>> iterator = listeners.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().get() == null) {
                iterator.remove();
            }
        }
    }
}
