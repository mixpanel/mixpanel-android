package com.mixpanel.android.mpmetrics;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

class MixpanelActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

    public MixpanelActivityLifecycleCallbacks(Context context, String token) {
        this.mContext = context;
        this.mToken = token;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        Configuration config = activity.getResources().getConfiguration();
        boolean dueToOrientationChange = mCurOrientation != null && config.orientation != mCurOrientation;
        if(!dueToOrientationChange && activity.isTaskRoot()) {
            checkForSurveys(activity);
        }
        mCurOrientation = config.orientation;
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (!mHasDoneFirstCheck) {
            mCurOrientation = activity.getResources().getConfiguration().orientation;
            checkForSurveys(activity);
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {}

    @Override
    public void onActivityPaused(Activity activity) {}

    @Override
    public void onActivityStopped(Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

    @Override
    public void onActivityDestroyed(Activity activity) {}

    private void checkForSurveys(Activity activity) {
        if (activity.isTaskRoot()) {
            mHasDoneFirstCheck = true;
            MixpanelAPI mixpanelAPI = MixpanelAPI.getInstance(mContext, mToken);
            View view = activity.findViewById(android.R.id.content);
            mixpanelAPI.getPeople().checkForSurvey(new ShowSurvey(mContext, mToken, view));
        }
    }

    private Context mContext;
    private String mToken;
    private boolean mHasDoneFirstCheck = false;
    private Integer mCurOrientation;
    private static final String LOGTAG = "MixpanelAPI:MixpanelActivityLifecycleCallbacks";
}