package com.mixpanel.android.mpmetrics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import com.mixpanel.android.viewcrawler.GestureTracker;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
/* package */ class MixpanelActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable check;
    private boolean mIsForeground = false;
    private boolean mPaused = true;
    public static final int CHECK_DELAY = 500;

    public MixpanelActivityLifecycleCallbacks(MixpanelAPI mpInstance, MPConfig config) {
        mMpInstance = mpInstance;
        mConfig = config;
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (android.os.Build.VERSION.SDK_INT >= MPConfig.UI_FEATURES_MIN_API && mConfig.getAutoShowMixpanelUpdates()) {
            if (!activity.isTaskRoot()) {
                return; // No checks, no nothing.
            }

            MixpanelAPI.People people = mMpInstance.getPeople();
            
            if (people.getShowNotificationOnActive()) {
                people.showNotificationIfAvailable(activity);
            }
                
            if (people.getShowSurveyOnActive()) {
                mMpInstance.getPeople().showSurveyIfAvailable(activity);
            }
        }
        new GestureTracker(mMpInstance, activity);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }

    @Override
    public void onActivityPaused(Activity activity) {
        mPaused = true;

        if (check != null) {
            mHandler.removeCallbacks(check);
        }

        mHandler.postDelayed(check = new Runnable(){
            @Override
            public void run() {
                if (mIsForeground && mPaused) {
                    mIsForeground = false;
                    mMpInstance.flush();
                }
            }
        }, CHECK_DELAY);
    }

    @Override
    public void onActivityDestroyed(Activity activity) { }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

    @Override
    public void onActivityResumed(Activity activity) {
        if (android.os.Build.VERSION.SDK_INT >= MPConfig.UI_FEATURES_MIN_API && mConfig.getAutoShowMixpanelUpdates()) {
            mMpInstance.getPeople().joinExperimentIfAvailable();
        }

        mPaused = true;
        boolean wasBackground = !mIsForeground;
        mIsForeground = true;

        if (check != null) {
            mHandler.removeCallbacks(check);
        }

        if (wasBackground) {
            // App is in foreground now
        }
    }

    @Override
    public void onActivityStopped(Activity activity) { }

    private final MixpanelAPI mMpInstance;
    private final MPConfig mConfig;
}
