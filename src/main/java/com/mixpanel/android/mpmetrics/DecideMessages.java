package com.mixpanel.android.mpmetrics;

import android.content.Context;


// Will be called from both customer threads and the Mixpanel worker thread.
/* package */ class DecideMessages {
    public DecideMessages(Context context, String token) {
        mContext = context;
        mToken = token;
    }

    public String getToken() {
        return mToken;
    }

    // Called from other synchronized code. Do not call into other synchronized code or you'll
    // risk deadlock
    public synchronized void setDistinctId(String distinctId) {
        mDistinctId = distinctId;
    }

    public synchronized String getDistinctId() {
        return mDistinctId;
    }

    public synchronized void reportResults(boolean automaticEvents) {
        if (mAutomaticEventsEnabled == null && !automaticEvents) {
            MPDbAdapter.getInstance(mContext).cleanupAutomaticEvents(mToken);
        }
        mAutomaticEventsEnabled = automaticEvents;
    }

    public Boolean isAutomaticEventsEnabled() {
        return mAutomaticEventsEnabled;
    }

    public boolean shouldTrackAutomaticEvent() {
        return isAutomaticEventsEnabled() == null ? true : isAutomaticEventsEnabled();
    }

    // Mutable, must be synchronized
    private String mDistinctId;

    private final String mToken;
    private Boolean mAutomaticEventsEnabled;
    private final Context mContext;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.DecideUpdts";
}
