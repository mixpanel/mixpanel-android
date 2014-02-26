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

// Will be called from both customer threads and the Mixpanel worker thread.
//
// States and behavior
//
//  - Set callback, and one is already cached in memory
//          - Call back with cached
//
//  - Set callback, waiting callback, running request is ancient
//          - Call waiting callback with null
//          - set waiting callback
//          - start request
//
//  - Set callback, waiting callback
//          - Call back with null
//
//  - Set callback, running request is ancient
//          - Set waiting callback
//          - Start request
//
//  - Set callback, running request is young
//          - Set waiting callback
//
//  - Set callback, recent request
//          - Call back with null
//
//  - Set callback
//          - Set waiting callback
//          - start request
//
//  - Call back from request
//          - Fill cache
//          - Serve waiting callbacks from cache
//          - Clear waiting callbacks
//
//
/* package */ class DecideUpdates {
    public DecideUpdates(Context context, String token) {
        mContext = context;
        mToken = token;
        mUnseenSurveys = new LinkedList<Survey>();
        mUnseenNotifications = new LinkedList<InAppNotification>();
        mSurveyIds = new HashSet<Integer>();
        mNotificationIds = new HashSet<Integer>();
        resetState(null);
    }

    public synchronized void setSurveyCallback(SurveyCallbacks callbacks, final String distinctId, final AnalyticsMessages messages) {
        if (! distinctId.equals(mRequestDistinctId)) {
            resetState(distinctId); // Purge all state if we have a new distinctId
        }

        long lastRequestAge = 1 + MPConfig.DECIDE_REQUEST_TIMEOUT_MILLIS;
        if (mRequestRunningSince >= 0) {
            lastRequestAge = currentTimeMillis() - mRequestRunningSince;
        }

        final boolean runningRequestIsAncient = mRequestIsRunning && lastRequestAge > MPConfig.DECIDE_REQUEST_TIMEOUT_MILLIS;
        final boolean lastRequestIsRecent = lastRequestAge < MPConfig.MAX_DECIDE_REQUEST_FREQUENCY_MILLIS;

        final Survey cached = popSurvey();
        if (cached != null) {
            runSurveyCallback(cached, callbacks);
        } else if (null != mWaitingSurveyCallbacks && runningRequestIsAncient) {
            runSurveyCallback(null, mWaitingSurveyCallbacks);
            mWaitingSurveyCallbacks = callbacks;
            requestDecideCheck(distinctId, messages);
        } else if (null != mWaitingSurveyCallbacks) {
            runSurveyCallback(null, callbacks);
        } else if (runningRequestIsAncient) {
            mWaitingSurveyCallbacks = callbacks;
            requestDecideCheck(distinctId, messages);
        } else if (mRequestIsRunning) {
            mWaitingSurveyCallbacks = callbacks;
        } else if (lastRequestIsRecent) {
            runSurveyCallback(null, callbacks);
        } else {
            mWaitingSurveyCallbacks = callbacks;
            requestDecideCheck(distinctId, messages);
        }
    }

    public synchronized void setInAppCallback(InAppNotificationCallbacks callbacks, final String distinctId, final AnalyticsMessages messages) {
        if (! distinctId.equals(mRequestDistinctId)) {
            resetState(distinctId); // Purge all state if we have a new distinctId
        }

        long lastRequestAge = 1 + MPConfig.DECIDE_REQUEST_TIMEOUT_MILLIS;
        if (mRequestRunningSince >= 0) {
            lastRequestAge = currentTimeMillis() - mRequestRunningSince;
        }

        final boolean runningRequestIsAncient = mRequestIsRunning && lastRequestAge > MPConfig.DECIDE_REQUEST_TIMEOUT_MILLIS;
        final boolean lastRequestIsRecent = lastRequestAge < MPConfig.MAX_DECIDE_REQUEST_FREQUENCY_MILLIS;

        final InAppNotification cached = popNotification();
        if (cached != null) {
            runInAppCallback(cached, callbacks);
        } else if (null != mWaitingInAppCallbacks && runningRequestIsAncient) {
            runInAppCallback(null, mWaitingInAppCallbacks);
            mWaitingInAppCallbacks = callbacks;
            requestDecideCheck(distinctId, messages);
        } else if (null != mWaitingInAppCallbacks) {
            runInAppCallback(null, callbacks);
        } else if (runningRequestIsAncient) {
            mWaitingInAppCallbacks = callbacks;
            requestDecideCheck(distinctId, messages);
        } else if (mRequestIsRunning) {
            mWaitingInAppCallbacks = callbacks;
        } else if (lastRequestIsRecent) {
            runInAppCallback(null, callbacks);
        } else {
            mWaitingInAppCallbacks = callbacks;
            requestDecideCheck(distinctId, messages);
        }
    }

    /* package */ synchronized void reportResults(String distinctId, List<Survey> newSurveys, List<InAppNotification> newNotifications) {
        if (! distinctId.equals(mRequestDistinctId)) {
            // Stale request, ignore it
            Log.i(LOGTAG, "Received decide results for an old distinct id, discarding them.");
            return;
        }
        mRequestIsRunning = false;

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

        if (null != mWaitingSurveyCallbacks) {
            final Survey survey = popSurvey();
            runSurveyCallback(survey, mWaitingSurveyCallbacks);
            mWaitingSurveyCallbacks = null;
        }

        if (null != mWaitingInAppCallbacks) {
            final InAppNotification notification = popNotification();
            runInAppCallback(notification, mWaitingInAppCallbacks);
            mWaitingInAppCallbacks = null;
        }
    }

    /* package */ long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    // FOR TESTING ONLY. In the real app, this will be useless and race-condition-tacular
    /* package */ List<Survey> peekAtSurveyCache() {
        return mUnseenSurveys;
    }

    /* package */ List<InAppNotification> peekAtNotificationCache() {
        return mUnseenNotifications;
    }

    protected ServerMessage newPoster() {
        return new ServerMessage();
    }

    protected void runOnIsolatedThread(final Runnable r) {
        CALLBACK_EXECUTOR.submit(r);
    }

    private Survey popSurvey() {
        if (mUnseenSurveys.isEmpty()) {
            return null;
        }
        return mUnseenSurveys.remove(0);
    }

    private InAppNotification popNotification() {
        if (mUnseenNotifications.isEmpty()) {
            return null;
        }
        return mUnseenNotifications.remove(0);
    }

    private void requestDecideCheck(final String distinctId, final AnalyticsMessages messages) {
        mRequestRunningSince = currentTimeMillis();
        mRequestIsRunning = true;
        mRequestDistinctId = distinctId;
        final DecideCallbacks callbacks = new DecideCallbacks() {
            @Override
            public void foundResults(List<Survey> surveys, List<InAppNotification> notifications) {
                reportResults(distinctId, surveys, notifications);
            }
        };
        final DecideChecker.DecideCheck check = new DecideChecker.DecideCheck(callbacks, distinctId, mToken);
        messages.checkDecideService(check);
    }

    private void runSurveyCallback(final Survey survey, final SurveyCallbacks callbacks) {
        mWaitingSurveyCallbacks = null;
        runOnIsolatedThread(new Runnable() {
            @Override
            public void run() {
                callbacks.foundSurvey(survey);
            }
        });
    }

    private void runInAppCallback(final InAppNotification tryNotification, final InAppNotificationCallbacks callbacks) {
        runOnIsolatedThread(new Runnable() {
            @Override
            public void run() {
                InAppNotification reportNotification = null;
                try {
                    if (null != tryNotification) {
                        String imageUrl;
                        if (tryNotification.getType() == InAppNotification.Type.MINI) {
                            imageUrl = tryNotification.getImageUrl();
                        } else {
                            imageUrl = tryNotification.getImage2xUrl();
                        }
                        final ServerMessage imageMessage = newPoster();
                        final ServerMessage.Result result = imageMessage.get(mContext, imageUrl, null);
                        if (result.getStatus() != ServerMessage.Status.SUCCEEDED) {
                            // Shouldn't drop this notification on the floor if this is a connectivity issue!
                            Log.i(LOGTAG, "Could not access image at " + imageUrl);
                        } else {
                            final byte[] imageBytes = result.getResponseBytes();
                            final Bitmap image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                            if (null == image) {
                                Log.w(LOGTAG, "Notification referred to bad or corrupted image at " + imageUrl);
                            } else {
                                tryNotification.setImage(image);
                                reportNotification = tryNotification;
                            }
                        }
                    }
                } catch (OutOfMemoryError e) {
                    Log.w(LOGTAG, "Notification image is too big, can't fit it into memory.", e);
                }
                callbacks.foundNotification(reportNotification);
            }
        });
    }

    private void resetState(String distinctId) {
        mSurveyIds.clear();
        mNotificationIds.clear();
        mUnseenSurveys.clear();
        mUnseenNotifications.clear();
        if (null != mWaitingSurveyCallbacks) {
            runSurveyCallback(null, mWaitingSurveyCallbacks);
        }
        mWaitingSurveyCallbacks = null;
        if (null != mWaitingInAppCallbacks) {
            runInAppCallback(null, mWaitingInAppCallbacks);
        }
        mWaitingInAppCallbacks = null;
        mRequestIsRunning = false;
        mRequestRunningSince = -1;
        mRequestDistinctId = distinctId;
    }

    private final Context mContext;
    private final String mToken;
    private final Set<Integer> mSurveyIds;
    private final Set<Integer> mNotificationIds;

    // STATES
    private final List<Survey> mUnseenSurveys;
    private final List<InAppNotification> mUnseenNotifications;
    private SurveyCallbacks mWaitingSurveyCallbacks;
    private InAppNotificationCallbacks mWaitingInAppCallbacks;
    private boolean mRequestIsRunning;
    private long mRequestRunningSince;
    private String mRequestDistinctId;

    private static final String LOGTAG = "MixpanelAPI DecideUpdates";
    private static final ExecutorService CALLBACK_EXECUTOR = Executors.newSingleThreadExecutor();
}
