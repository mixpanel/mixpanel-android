package com.mixpanel.android.mpmetrics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;

@TargetApi(14)
class MixpanelActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

    public MixpanelActivityLifecycleCallbacks(MixpanelAPI mpInstance) {
        mMpInstance = mpInstance;
        mHasChecked = false;
    }

    /**
     * The Mixpanel library is unlikely to be instantiated in time for this to be
     * called on the initial opening of the application.
     * However, this method is executed when the application
     * is in memory but closed and the user re-opens it.
     *
     * @param activity
     * @param savedInstanceState
     */
    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        checkForDecideUpdates(activity);
    }

    /**
     * This method is called anytime an activity is started (which is quite frequently). The only
     * reason we are interested in this call is to check and show an eligible survey on initial app
     * open. Unfortunately, by the time MixpanelActivityLifecycleCallbacks is registered, we've
     * already missed the onActivityCreated call. We'll use this event to "catch up".
     * checkDecideService is only called if hasn't been previously called in the life of the app.
     *
     * @param activity
     */
    @Override
    public void onActivityStarted(Activity activity) {
        checkForDecideUpdates(activity);
    }

    @Override
    public void onActivityResumed(Activity activity) {
        checkForDecideUpdates(activity);
    }

    /**
     * We rely on the fact that the we'll either get an onActivityPaused or onActivityDestroyed
     * message if/when the activity goes away.
     */
    @Override
    public void onActivityPaused(Activity activity) {
        cleanupActivity(activity);
    }

    @Override
    public void onActivityStopped(Activity activity) {
        cleanupActivity(activity);
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

    /**
     * We rely on the fact that the we'll either get an onActivityPaused or onActivityDestroyed
     * message if/when the activity goes away.
     */
    @Override
    public void onActivityDestroyed(Activity activity) {
        cleanupActivity(activity);
    }

    // Should always be called on the main thread.
    private void checkForDecideUpdates(final Activity activity) {
        if (! activity.isTaskRoot()) {
            return; // No checks, no nothing.
        }

        mMpInstance.getPeople().showNotificationIfAvailable(activity);
        mMpInstance.getPeople().showSurveyIfAvailable(activity);

        if (! mHasChecked) {
            mHasChecked = true;
            mUpdatesListener = new UpdatesListener(activity);
            mMpInstance.getPeople().addOnMixpanelUpdatesReceivedListener(mUpdatesListener);
        }
    }

    private void cleanupActivity(Activity activity) {
        if (null != mUpdatesListener && mUpdatesListener.getActivity() == activity) {
            mUpdatesListener.disable();
            mUpdatesListener = null;
        }
    }

    // Must be constructed on the main thread
    private class UpdatesListener implements OnMixpanelUpdatesReceivedListener, Runnable {
        // Construct on main UI thread only
        public UpdatesListener(final Activity activity) {
            mOnBehalfOf = activity;
            mDisabled = false;
            mHandler = new Handler();
        }

        @Override
        // Will be run on some random thread
        public void onMixpanelUpdatesReceived() {
            mHandler.post(this);
        }

        // Call on main UI thread only
        public void disable() {
            mDisabled = true;
            mOnBehalfOf = null;
        }

        // Call on main UI thread only
        public Activity getActivity() {
            return mOnBehalfOf;
        }

        @Override
        // Will be run on the Main UI thread
        public void run() {
            if (! mDisabled) {
                checkForDecideUpdates(mOnBehalfOf);
            }
        }

        private Activity mOnBehalfOf;
        private boolean mDisabled;
        private final Handler mHandler;
    }

    private final MixpanelAPI mMpInstance;
    private boolean mHasChecked;
    private UpdatesListener mUpdatesListener;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI:MixpanelActivityLifecycleCallbacks";
}
