package com.mixpanel.android.mpmetrics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.BadParcelableException;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.mixpanel.android.viewcrawler.GestureTracker;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
/* package */ class MixpanelActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable check;
    private boolean mIsForeground = true;
    private boolean mPaused = true;
    private static Double sStartSessionTime;
    public static final int CHECK_DELAY = 500;

    public MixpanelActivityLifecycleCallbacks(MixpanelAPI mpInstance, MPConfig config) {
        mMpInstance = mpInstance;
        mConfig = config;
        if (sStartSessionTime == null) {
            sStartSessionTime = (double) System.currentTimeMillis();
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        trackCampaignOpenedIfNeeded(activity.getIntent());

        if (android.os.Build.VERSION.SDK_INT >= MPConfig.UI_FEATURES_MIN_API && mConfig.getAutoShowMixpanelUpdates()) {
            if (!activity.isTaskRoot()) {
                return; // No checks, no nothing.
            }

            mMpInstance.getPeople().showNotificationIfAvailable(activity);
        }
        new GestureTracker(mMpInstance, activity);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }

    @Override
    public void onActivityPaused(final Activity activity) {
        mPaused = true;

        if (check != null) {
            mHandler.removeCallbacks(check);
        }

        mHandler.postDelayed(check = new Runnable(){
            @Override
            public void run() {
                if (mIsForeground && mPaused) {
                    mIsForeground = false;
                    try {
                        double sessionLength = System.currentTimeMillis() - sStartSessionTime;
                        if (sessionLength >= mConfig.getMinimumSessionDuration() && sessionLength < mConfig.getSessionTimeoutDuration()) {
                            NumberFormat nf = NumberFormat.getNumberInstance(Locale.ENGLISH);
                            nf.setMaximumFractionDigits(1);
                            String sessionLengthString = nf.format((System.currentTimeMillis() - sStartSessionTime) / 1000);
                            JSONObject sessionProperties = new JSONObject();
                            sessionProperties.put(AutomaticEvents.SESSION_LENGTH, sessionLengthString);
                            mMpInstance.track(AutomaticEvents.SESSION, sessionProperties, true);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
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

        mPaused = false;
        boolean wasBackground = !mIsForeground;
        mIsForeground = true;

        if (check != null) {
            mHandler.removeCallbacks(check);
        }

        if (wasBackground) {
            // App is in foreground now
            sStartSessionTime = (double) System.currentTimeMillis();
        }
    }

    @Override
    public void onActivityStopped(Activity activity) { }

    protected boolean isInForeground() {
        return mIsForeground;
    }

    private void trackCampaignOpenedIfNeeded(Intent intent) {
        if (intent == null) {
            return;
        }

        try {
            if (intent.hasExtra("mp_campaign_id") && intent.hasExtra("mp_message_id")) {
                String campaignId = intent.getStringExtra("mp_campaign_id");
                String messageId = intent.getStringExtra("mp_message_id");
                String extraLogData = intent.getStringExtra("mp");

                try {
                    JSONObject pushProps;
                    if (extraLogData != null) {
                        pushProps = new JSONObject(extraLogData);
                    } else {
                        pushProps = new JSONObject();
                    }
                    pushProps.put("campaign_id", Integer.valueOf(campaignId).intValue());
                    pushProps.put("message_id", Integer.valueOf(messageId).intValue());
                    pushProps.put("message_type", "push");
                    mMpInstance.track("$app_open", pushProps);
                } catch (JSONException e) {}

                intent.removeExtra("mp_campaign_id");
                intent.removeExtra("mp_message_id");
                intent.removeExtra("mp");
            }
        } catch (BadParcelableException e) {
            // https://github.com/mixpanel/mixpanel-android/issues/251
        }
    }

    private final MixpanelAPI mMpInstance;
    private final MPConfig mConfig;
}
