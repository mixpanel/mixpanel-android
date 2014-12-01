package com.mixpanel.android.viewcrawler;

import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.mixpanel.android.mpmetrics.MixpanelAPI;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Handles translating events detected by ViewVisitors into events sent to Mixpanel
 *
 * - Builds properties by interrogating view subtrees
 *
 * - Possibly debounces events using the Handler given at construction
 *
 * - Calls MixpanelAPI.track
 */
/* package */ class DynamicEventTracker implements ViewVisitor.OnEventListener {

    public DynamicEventTracker(MixpanelAPI mixpanel, Handler homeHandler) {
        mMixpanel = mixpanel;
        mDebouncedEvents = new HashMap<Signature, UnsentEvent>();
        mTask = new SendDebouncedTask();
        mHandler = homeHandler;
    }

    @Override
    public void OnEvent(View v, String eventName, boolean debounce) {
        // Will be called on the UI thread
        final long moment = System.currentTimeMillis();
        final JSONObject properties = new JSONObject();
        try {
            final String text = textPropertyFromView(v);
            properties.put("$text", text);
            properties.put("$from_binding", true);

            // We may call track much later, but we'll be tracking something
            // that happened right at moment.
            properties.put("time", moment / 1000);
        } catch (JSONException e) {
            Log.e(LOGTAG, "Can't format properties from view due to JSON issue", e);
        }

        if (debounce) {
            final Signature eventSignature = new Signature(v, eventName);
            final UnsentEvent event = new UnsentEvent(eventName, properties, moment);

            // No scheduling mTask without holding a lock on mDebouncedEvents,
            // so that we don't have a rogue thread spinning away when no events
            // are coming in.
            synchronized (mDebouncedEvents) {
                final boolean needsRestart = mDebouncedEvents.isEmpty();
                mDebouncedEvents.put(eventSignature, event);
                if (needsRestart) {
                    mHandler.postDelayed(mTask, DEBOUNCE_TIME_MILLIS);
                }
            }
        } else {
            mMixpanel.track(eventName, properties);
        }
    }

    // Attempts to send all tasks in mDebouncedEvents that have been waiting for
    // more than DEBOUNCE_TIME_MILLIS. Will reschedule itself as long as there
    // are more events waiting (but will *not* wait on an empty set)
    private final class SendDebouncedTask implements Runnable {
        @Override
        public void run() {
            final long now = System.currentTimeMillis();
            synchronized (mDebouncedEvents) {
                final Iterator<Map.Entry<Signature, UnsentEvent>> iter = mDebouncedEvents.entrySet().iterator();
                while (iter.hasNext()) {
                    final Map.Entry<Signature, UnsentEvent> entry = iter.next();
                    final UnsentEvent val = entry.getValue();
                    if (now - val.timeSentMillis > DEBOUNCE_TIME_MILLIS) {
                        mMixpanel.track(val.eventName, val.properties);
                        iter.remove();
                    }
                }

                if (! mDebouncedEvents.isEmpty()) {
                    // In the average case, this is enough time to catch the next signal
                    mHandler.postDelayed(this, DEBOUNCE_TIME_MILLIS / 2);
                }
            } // synchronized
        }
    }

    /**
     * Recursively scans a view and it's children, looking for user-visible text to
     * provide as an event property.
     */
    private static String textPropertyFromView(View v) {
        String ret = null;

        if (v instanceof TextView) {
            final TextView textV = (TextView) v;
            final CharSequence retSequence = textV.getText();
            if (null != retSequence) {
                ret = retSequence.toString();
            }
        } else if (v instanceof ViewGroup) {
            final StringBuilder builder = new StringBuilder();
            final ViewGroup vGroup = (ViewGroup) v;
            final int childCount = vGroup.getChildCount();
            boolean textSeen = false;
            for (int i = 0; i < childCount && builder.length() < MAX_PROPERTY_LENGTH; i++) {
                final View child = vGroup.getChildAt(i);
                final String childText = textPropertyFromView(child);
                if (null != childText && childText.length() > 0) {
                    if (textSeen) {
                        builder.append(", ");
                    }
                    builder.append(childText);
                    textSeen = true;
                }
            }

            if (builder.length() > MAX_PROPERTY_LENGTH) {
                ret = builder.substring(0, MAX_PROPERTY_LENGTH);
            } else if (textSeen) {
                ret = builder.toString();
            }
        }

        return ret;
    }

    // An event is the same from a debouncing perspective if it comes from the same view,
    // and has the same event name.
    private static class Signature {
        public Signature(final View view, final String eventName) {
            mHashCode = view.hashCode() ^ eventName.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Signature) {
                return mHashCode == o.hashCode();
            }

            return false;
        }

        @Override
        public int hashCode() {
            return mHashCode;
        }

        private final int mHashCode;
    }

    private static class UnsentEvent {
        public UnsentEvent(final String name, final JSONObject props, final long timeSent) {
            eventName = name;
            properties = props;
            timeSentMillis = timeSent;
        }

        public final long timeSentMillis;
        public final String eventName;
        public final JSONObject properties;
    }

    private final MixpanelAPI mMixpanel;
    private final Handler mHandler;
    private final Runnable mTask;

    // List of debounced events, All accesses must be synchronized
    private final Map<Signature, UnsentEvent> mDebouncedEvents;

    private static final int MAX_PROPERTY_LENGTH = 128;
    private static final int DEBOUNCE_TIME_MILLIS = 1000; // 1 second delay before sending

    @SuppressWarnings("Unused")
    private static String LOGTAG = "MixpanelAPI.DynamicEventTracker";
}
