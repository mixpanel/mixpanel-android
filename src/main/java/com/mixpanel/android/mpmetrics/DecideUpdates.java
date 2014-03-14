package com.mixpanel.android.mpmetrics;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

// Will be called from both customer threads and the Mixpanel worker thread.
/* package */ class DecideUpdates {

    public interface OnNewResultsListener {
        public void onNewResults(String distinctId);
    }

    public DecideUpdates(String token, String distinctId, OnNewResultsListener listener) {
        mToken = token;
        mDistinctId = distinctId;

        mListener = listener;
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
    public synchronized void reportResults(List<Survey> newSurveys, List<InAppNotification> newNotifications) {
        boolean newContent = false;

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

    public synchronized Survey popSurvey() {
        if (mUnseenSurveys.isEmpty()) {
            return null;
        }
        return mUnseenSurveys.remove(0);
    }

    public synchronized InAppNotification popNotification() {
        if (mUnseenNotifications.isEmpty()) {
            return null;
        }
        return mUnseenNotifications.remove(0);
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
    private final AtomicBoolean mIsDestroyed;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI DecideUpdates";
}
