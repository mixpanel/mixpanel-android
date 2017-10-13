package com.mixpanel.android.mpmetrics;

import android.content.Context;

import com.mixpanel.android.util.MPLog;
import com.mixpanel.android.viewcrawler.UpdatesFromMixpanel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

// Will be called from both customer threads and the Mixpanel worker thread.
/* package */ class DecideMessages {

    public interface OnNewResultsListener {
        void onNewResults();
        void onNewConnectIntegrations();
    }

    public DecideMessages(Context context, String token, OnNewResultsListener listener, UpdatesFromMixpanel updatesFromMixpanel, HashSet<Integer> notificationIds) {
        mContext = context;
        mToken = token;
        mListener = listener;
        mUpdatesFromMixpanel = updatesFromMixpanel;

        mDistinctId = null;
        mUnseenNotifications = new LinkedList<InAppNotification>();
        mNotificationIds = new HashSet<Integer>(notificationIds);
        mVariants = null;
        mIntegrations = new HashSet<String>();
    }

    public String getToken() {
        return mToken;
    }

    // Called from other synchronized code. Do not call into other synchronized code or you'll
    // risk deadlock
    public synchronized void setDistinctId(String distinctId) {
        if (mDistinctId == null || !mDistinctId.equals(distinctId)){
            mUnseenNotifications.clear();
        }
        mDistinctId = distinctId;
    }

    public synchronized String getDistinctId() {
        return mDistinctId;
    }

    public synchronized void reportResults(List<InAppNotification> newNotifications,
                                           JSONArray eventBindings,
                                           JSONArray variants,
                                           boolean automaticEvents,
                                           JSONArray integrations) {
        boolean newContent = false;
        int newVariantsLength = variants.length();
        boolean hasNewVariants = false;

        mUpdatesFromMixpanel.setEventBindings(eventBindings);

        for (final InAppNotification n : newNotifications) {
            final int id = n.getId();
            if (! mNotificationIds.contains(id)) {
                mNotificationIds.add(id);
                mUnseenNotifications.add(n);
                newContent = true;
            }
        }

        // the following logic checks if the variants have been applied by looking up their id's in the HashSet
        // this is needed to make sure the user defined `mListener` will get called on new variants receiving
        mVariants = variants;

        for (int i = 0; i < newVariantsLength; i++) {
            try {
                JSONObject variant = variants.getJSONObject(i);
                if (!mLoadedVariants.contains(variant.getInt("id"))) {
                    newContent = true;
                    hasNewVariants = true;
                    break;
                }
            } catch(JSONException e) {
                MPLog.e(LOGTAG, "Could not convert variants[" + i + "] into a JSONObject while comparing the new variants", e);
            }
        }

        if (hasNewVariants && mVariants != null) {
            mLoadedVariants.clear();

            for (int i = 0; i < newVariantsLength; i++) {
                try {
                    JSONObject variant = mVariants.getJSONObject(i);
                    mLoadedVariants.add(variant.getInt("id"));
                } catch(JSONException e) {
                    MPLog.e(LOGTAG, "Could not convert variants[" + i + "] into a JSONObject while updating the map", e);
                }
            }
        }

        // in the case we do not receive a new variant, this means the A/B test should be turned off
        if (newVariantsLength == 0) {
            mVariants = new JSONArray();
            if (mLoadedVariants.size() > 0) {
                mLoadedVariants.clear();
                newContent = true;
            }
        }
        mUpdatesFromMixpanel.storeVariants(mVariants);

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
                    mListener.onNewConnectIntegrations();
                }
            } catch(JSONException e) {
                MPLog.e(LOGTAG, "Got an integration id from " + integrations.toString() + " that wasn't an int", e);
            }
        }

        MPLog.v(LOGTAG, "New Decide content has become available. " +
                    newNotifications.size() + " notifications and " +
                    variants.length() + " experiments have been added.");

        if (newContent && null != mListener) {
            mListener.onNewResults();
        }
    }

    public synchronized JSONArray getVariants() {
        return mVariants;
    }

    public synchronized InAppNotification getNotification(boolean replace) {
        if (mUnseenNotifications.isEmpty()) {
            MPLog.v(LOGTAG, "No unseen notifications exist, none will be returned.");
            return null;
        }
        InAppNotification n = mUnseenNotifications.remove(0);
        if (replace) {
            mUnseenNotifications.add(n);
        } else {
            MPLog.v(LOGTAG, "Recording notification " + n + " as seen.");
        }
        return n;
    }

    public synchronized InAppNotification getNotification(int id, boolean replace) {
        InAppNotification notif = null;
        for (int i = 0; i < mUnseenNotifications.size(); i++) {
            if (mUnseenNotifications.get(i).getId() == id) {
                notif = mUnseenNotifications.get(i);
                if (!replace) {
                    mUnseenNotifications.remove(i);
                }
                break;
            }
        }
        return notif;
    }

    public synchronized Set<String> getIntegrations() { return mIntegrations; }

    // if a notification was failed to show, add it back to the unseen list so that we
    // won't lose it
    public synchronized void markNotificationAsUnseen(InAppNotification notif) {
        if (!MPConfig.DEBUG) {
            mUnseenNotifications.add(notif);
        }
    }

    public synchronized boolean hasUpdatesAvailable() {
        return (! mUnseenNotifications.isEmpty()) || (mVariants != null && mVariants.length() > 0);
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
    private final Set<Integer> mNotificationIds;
    private final List<InAppNotification> mUnseenNotifications;
    private final OnNewResultsListener mListener;
    private final UpdatesFromMixpanel mUpdatesFromMixpanel;
    private JSONArray mVariants;
    private static final Set<Integer> mLoadedVariants = new HashSet<>();
    private Boolean mAutomaticEventsEnabled;
    private Context mContext;
    private Set<String> mIntegrations;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.DecideUpdts";
}
