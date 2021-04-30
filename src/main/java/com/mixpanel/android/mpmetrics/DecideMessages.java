package com.mixpanel.android.mpmetrics;

import android.content.Context;

import com.mixpanel.android.util.MPLog;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashSet;
import java.util.Set;

// Will be called from both customer threads and the Mixpanel worker thread.
/* package */ class DecideMessages {
    public DecideMessages(Context context, String token, ConnectIntegrations connectIntegrations) {
        mContext = context;
        mToken = token;
        mIntegrations = new HashSet<String>();
        mConnectIntegrations = connectIntegrations;
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

    public synchronized void reportResults(boolean automaticEvents,
                                           JSONArray integrations) {
        if (mAutomaticEventsEnabled == null && !automaticEvents) {
            MPDbAdapter.getInstance(mContext).cleanupAutomaticEvents(mToken);
        }
        mAutomaticEventsEnabled = automaticEvents;

        if (integrations != null) {
            try {
                HashSet<String> integrationsSet = new HashSet<String>();
                for (int i = 0; i < integrations.length(); i++) {
                    integrationsSet.add(integrations.getString(i));
                }
                if (!mIntegrations.equals(integrationsSet)) {
                    mIntegrations = integrationsSet;
                    mConnectIntegrations.setupIntegrations(mIntegrations);
                }
            } catch(JSONException e) {
                MPLog.e(LOGTAG, "Got an integration id from " + integrations.toString() + " that wasn't an int", e);
            }
        }
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
    private Set<String> mIntegrations;
    private final ConnectIntegrations mConnectIntegrations;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.DecideUpdts";
}
