package com.mixpanel.android.mpmetrics;

import android.util.Log;

import com.mixpanel.android.viewcrawler.UpdatesFromMixpanel;

import org.json.JSONArray;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

// Will be called from both customer threads and the Mixpanel worker thread.
/* package */ class DecideMessages {

    public interface OnNewResultsListener {
        void onNewResults();
    }

    public DecideMessages(String token, OnNewResultsListener listener, UpdatesFromMixpanel updatesFromMixpanel) {
        mToken = token;
        mListener = listener;
        mUpdatesFromMixpanel = updatesFromMixpanel;

        mDistinctId = null;
        mUnseenSurveys = new LinkedList<Survey>();
        mUnseenNotifications = new LinkedList<InAppNotification>();
        mSurveyIds = new HashSet<Integer>();
        mNotificationIds = new HashSet<Integer>();
    }

    public String getToken() {
        return mToken;
    }

    // Called from other synchronized code. Do not call into other synchronized code or you'll
    // risk deadlock
    public synchronized void setDistinctId(String distinctId) {
        mUnseenSurveys.clear();
        mUnseenNotifications.clear();
        mDistinctId = distinctId;
    }

    public synchronized String getDistinctId() {
        return mDistinctId;
    }

    public synchronized void reportResults(List<Survey> newSurveys, List<InAppNotification> newNotifications, JSONArray eventBindings, JSONArray variants) {
        boolean newContent = false;
        mUpdatesFromMixpanel.setEventBindings(eventBindings);

        for (final Survey s : newSurveys) {
            final int id = s.getId();
            if (! mSurveyIds.contains(id)) {
                mSurveyIds.add(id);
                mUnseenSurveys.add(s);
                newContent = true;
            }
        }

        for (final InAppNotification n : newNotifications) {
            final int id = n.getId();
            if (! mNotificationIds.contains(id)) {
                mNotificationIds.add(id);
                mUnseenNotifications.add(n);
                newContent = true;
            }
        }

        mVariants = variants;

        if (MPConfig.DEBUG) {
            Log.v(LOGTAG, "New Decide content has become available. " +
                    newSurveys.size() + " surveys and " +
                    newNotifications.size() + " notifications have been added.");
        }

        if (newContent && hasUpdatesAvailable() && null != mListener) {
            mListener.onNewResults();
        }
    }

    public synchronized Survey getSurvey(boolean replace) {
        if (mUnseenSurveys.isEmpty()) {
            return null;
        }
        Survey s = mUnseenSurveys.remove(0);
        if (replace) {
            mUnseenSurveys.add(mUnseenSurveys.size(), s);
        }
        return s;
    }

    public synchronized Survey getSurvey(int id, boolean replace) {
        Survey survey = null;
        for (int i = 0; i < mUnseenSurveys.size(); i++) {
            if (mUnseenSurveys.get(i).getId() == id) {
                survey = mUnseenSurveys.get(i);
                if (!replace) {
                    mUnseenSurveys.remove(i);
                }
                break;
            }
        }
        return survey;
    }

    public synchronized JSONArray getVariants() {
        return mVariants;
    }

    public synchronized InAppNotification getNotification(boolean replace) {
        if (mUnseenNotifications.isEmpty()) {
            if (MPConfig.DEBUG) {
                Log.v(LOGTAG, "No unseen notifications exist, none will be returned.");
            }
            return null;
        }
        InAppNotification n = mUnseenNotifications.remove(0);
        if (replace) {
            mUnseenNotifications.add(mUnseenNotifications.size(), n);
        } else {
            if (MPConfig.DEBUG) {
                Log.v(LOGTAG, "Recording notification " + n + " as seen.");
            }
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

    public synchronized boolean hasUpdatesAvailable() {
        return (! mUnseenNotifications.isEmpty()) || (! mUnseenSurveys.isEmpty()) || mVariants != null;
    }

    // Mutable, must be synchronized
    private String mDistinctId;

    private final String mToken;
    private final Set<Integer> mSurveyIds;
    private final Set<Integer> mNotificationIds;
    private final List<Survey> mUnseenSurveys;
    private final List<InAppNotification> mUnseenNotifications;
    private final OnNewResultsListener mListener;
    private final UpdatesFromMixpanel mUpdatesFromMixpanel;
    private JSONArray mVariants;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.DecideUpdts";
}
