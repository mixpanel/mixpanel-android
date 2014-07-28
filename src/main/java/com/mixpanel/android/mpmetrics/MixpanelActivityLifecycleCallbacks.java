package com.mixpanel.android.mpmetrics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

@TargetApi(14)
/* package */ class MixpanelActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

    public MixpanelActivityLifecycleCallbacks(MixpanelAPI mpInstance) {
        mMpInstance = mpInstance;
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (!activity.isTaskRoot()) {
            return; // No checks, no nothing.
        }

        mMpInstance.getPeople().showNotificationIfAvailable(activity);
        mMpInstance.getPeople().showSurveyIfAvailable(activity);
    }

    private final MixpanelAPI mMpInstance;

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }

    @Override
    public void onActivityPaused(Activity activity) { }

    @Override
    public void onActivityDestroyed(Activity activity) { }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

    @Override
    public void onActivityResumed(Activity activity) { }

    @Override
    public void onActivityStopped(Activity activity) { }
}
