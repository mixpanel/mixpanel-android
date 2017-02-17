package com.mixpanel.android.mpmetrics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.mixpanel.android.viewcrawler.GestureTracker;

import org.json.JSONException;
import org.json.JSONObject;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
/* package */ class MixpanelActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable check;
    private boolean mIsForeground = true;
    private boolean mPaused = true;
    public static final int CHECK_DELAY = 500;

    public MixpanelActivityLifecycleCallbacks(MixpanelAPI mpInstance, MPConfig config) {
        mMpInstance = mpInstance;
        mConfig = config;
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (activity.getIntent().hasExtra("mp_campaign_id") && activity.getIntent().hasExtra("mp_message_id")) {
            String campaignId = activity.getIntent().getStringExtra("mp_campaign_id");
            String messageId = activity.getIntent().getStringExtra("mp_message_id");

            JSONObject pushProps = new JSONObject();
            try {
                pushProps.put("campaign_id", campaignId);
                pushProps.put("message_id", messageId);
                pushProps.put("message_type", "push");
                mMpInstance.track("$app_open", pushProps);
            } catch (JSONException e) {}

            activity.getIntent().removeExtra("mp_campaign_id");
            activity.getIntent().removeExtra("mp_message_id");
        }

        if (android.os.Build.VERSION.SDK_INT >= MPConfig.UI_FEATURES_MIN_API && mConfig.getAutoShowMixpanelUpdates()) {
            if (!activity.isTaskRoot()) {
                return; // No checks, no nothing.
            }

            mMpInstance.getPeople().showNotificationIfAvailable(activity);
            mMpInstance.getPeople().showSurveyIfAvailable(activity);
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

    protected boolean isInForeground() {
        return mIsForeground;
    }

    private final MixpanelAPI mMpInstance;
    private final MPConfig mConfig;
}
