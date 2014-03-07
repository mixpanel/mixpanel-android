package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

// Will be called from both customer threads and the Mixpanel worker thread.
/* package */ class DecideUpdates {
    public DecideUpdates(String token, String distinctId) {
        mToken = token;
        mDistinctId = distinctId;

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

    /* package */ synchronized void reportResults(List<Survey> newSurveys, List<InAppNotification> newNotifications) {
        for (final Survey s: newSurveys) {
            final int id = s.getId();
            if (! mSurveyIds.contains(id)) {
                mSurveyIds.add(id);
                mUnseenSurveys.add(s);
            }
        }

        for (final InAppNotification n: newNotifications) {
            final int id = n.getId();
            if (! mNotificationIds.contains(id)) {
                mNotificationIds.add(id);
                mUnseenNotifications.add(n);
            }
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

    /* package */ long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    // FOR TESTING ONLY. In the real app, this will be useless and race-condition-tacular
    // TODO JUST POP FROM THESE INSTEAD
    /* package */ List<Survey> peekAtSurveyCache() {
        return mUnseenSurveys;
    }

    // TODO JUST POP THESE INSTEAD
    /* package */ List<InAppNotification> peekAtNotificationCache() {
        return mUnseenNotifications;
    }

    private final String mToken;
    private final String mDistinctId;
    private final Set<Integer> mSurveyIds;
    private final Set<Integer> mNotificationIds;
    private final List<Survey> mUnseenSurveys;
    private final List<InAppNotification> mUnseenNotifications;
    private final AtomicBoolean mIsDestroyed;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI DecideUpdates";
}
