package com.mixpanel.android.mpmetrics;

import com.mixpanel.android.viewcrawler.UpdatesFromMixpanel;

import org.json.JSONArray;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

// Will be called from both customer threads and the Mixpanel worker thread.
/* package */ class DecideMessages {

    public interface OnNewResultsListener {
        public void onNewResults(String distinctId);
    }

    public DecideMessages(String token, String distinctId, OnNewResultsListener listener, UpdatesFromMixpanel updatesFromMixpanel) {
        mToken = token;
        mDistinctId = distinctId;

        mListener = listener;
        mUpdatesFromMixpanel = updatesFromMixpanel;
        mUnseenSurveys = new LinkedList<Survey>();
        mUnseenNotifications = new LinkedList<InAppNotification>();
        mSurveyIds = new HashSet<Integer>();
        mNotificationIds = new HashSet<Integer>();
        mIsDestroyed = new AtomicBoolean(false);
    }

    public String getToken() {
        return mToken;
    }

    public String getDistinctId() {
        return mDistinctId;
    }

    public void destroy() {
        mIsDestroyed.set(true);
    }

    public boolean isDestroyed() {
        return mIsDestroyed.get();
    }

    // Do not consult destroyed status inside of this method.
    public synchronized void reportResults(List<Survey> newSurveys, List<InAppNotification> newNotifications, JSONArray eventBindings) {
        boolean newContent = false;
        mUpdatesFromMixpanel.setEventBindings(eventBindings);

        for (final Survey s: newSurveys) {
            final int id = s.getId();
            if (! mSurveyIds.contains(id)) {
                mSurveyIds.add(id);
                mUnseenSurveys.add(s);
                newContent = true;
            }
        }

        for (final InAppNotification n: newNotifications) {
            final int id = n.getId();
            if (! mNotificationIds.contains(id)) {
                mNotificationIds.add(id);
                mUnseenNotifications.add(n);
                newContent = true;
            }
        }

        if (newContent && hasUpdatesAvailable() && null != mListener) {
            mListener.onNewResults(getDistinctId());
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

    public synchronized InAppNotification getNotification(boolean replace) {
        if (mUnseenNotifications.isEmpty()) {
            return null;
        }
        InAppNotification n = mUnseenNotifications.remove(0);
        if (replace) {
            mUnseenNotifications.add(mUnseenNotifications.size(), n);
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
        return (! mUnseenNotifications.isEmpty()) || (! mUnseenSurveys.isEmpty());
    }

    private final String mToken;
    private final String mDistinctId;
    private final Set<Integer> mSurveyIds;
    private final Set<Integer> mNotificationIds;
    private final List<Survey> mUnseenSurveys;
    private final List<InAppNotification> mUnseenNotifications;
    private final OnNewResultsListener mListener;
    private final UpdatesFromMixpanel mUpdatesFromMixpanel;
    private final AtomicBoolean mIsDestroyed;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.DecideUpdates";
}
