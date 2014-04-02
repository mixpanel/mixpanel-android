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

    @Override
    public void onActivityResumed(Activity activity) {
        checkForDecideUpdates(activity);
    }

    @Override
    public void onActivityPaused(Activity activity) {
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

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }

    @Override
    public void onActivityDestroyed(Activity activity) { }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

    @Override
    public void onActivityStarted(Activity activity) { }

    @Override
    public void onActivityStopped(Activity activity) { }
}
