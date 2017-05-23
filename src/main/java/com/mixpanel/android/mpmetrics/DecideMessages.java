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
    }

    public DecideMessages(Context context, String token, OnNewResultsListener listener, UpdatesFromMixpanel updatesFromMixpanel) {
        mContext = context;
        mToken = token;
        mListener = listener;
        mUpdatesFromMixpanel = updatesFromMixpanel;

        mDistinctId = null;
        mUnseenNotifications = new LinkedList<InAppNotification>();
        mNotificationIds = new HashSet<Integer>();
        mVariants = new JSONArray();
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

    public synchronized void reportResults(List<InAppNotification> newNotifications, JSONArray eventBindings, JSONArray variants, boolean automaticEvents) {
        boolean newContent = false;
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
        int newVariantsLength = variants.length();
        boolean hasNewVariants = false;

        for (int i = 0; i < newVariantsLength; i++) {
            try {
                JSONObject variant = variants.getJSONObject(i);
                if (!mLoadedVariants.contains(variant.getInt("id"))) {
                    mVariants = variants;
                    newContent = true;
                    hasNewVariants = true;
                    break;
                }
            } catch(JSONException e) {
                MPLog.e(LOGTAG, "Could not convert variants[" + i + "] into a JSONObject while comparing the new variants", e);
            }
        }

        if (hasNewVariants) {
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
        if (mAutomaticEventsEnabled == null && !automaticEvents) {
            MPDbAdapter.getInstance(mContext).cleanupAutomaticEvents(mToken);
        }
        mAutomaticEventsEnabled = automaticEvents;

        // in the case we do not receive a new variant, this means the A/B test should be turned off
        if (newVariantsLength == 0 && mLoadedVariants.size() > 0) {
            mLoadedVariants.clear();
            mVariants = new JSONArray();
            newContent = true;
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

    // if a notification was failed to show, add it back to the unseen list so that we
    // won't lose it
    public synchronized void markNotificationAsUnseen(InAppNotification notif) {
        if (!MPConfig.DEBUG) {
            mUnseenNotifications.add(notif);
        }
    }

    public synchronized boolean hasUpdatesAvailable() {
        return (! mUnseenNotifications.isEmpty()) || mVariants.length() > 0;
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

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.DecideUpdts";
}
