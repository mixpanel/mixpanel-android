package com.mixpanel.android.mpmetrics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
/* package */ class MixpanelActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable check;
    private boolean mIsForeground = false;
    private boolean mPaused = true;
    private boolean mHasSeenActivity = false;
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
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }

    @Override
    public void onActivityPaused(final Activity activity) {
        mHasSeenActivity = true;
        mPaused = true;

        if (check != null) {
            mHandler.removeCallbacks(check);
        }
        mCurrentActivity = null;

        mHandler.postDelayed(check = new Runnable(){
            @Override
            public void run() {
                if (mIsForeground && mPaused) {
                    mIsForeground = false;
                    try {
                        double sessionLength = System.currentTimeMillis() - sStartSessionTime;
                        if (sessionLength >= mConfig.getMinimumSessionDuration() && sessionLength < mConfig.getSessionTimeoutDuration() && mMpInstance.getTrackAutomaticEvents()) {
                            double elapsedTime = sessionLength / 1000;
                            double elapsedTimeRounded = Math.round(elapsedTime * 10.0) / 10.0;
                            JSONObject sessionProperties = new JSONObject();
                            sessionProperties.put(AutomaticEvents.SESSION_LENGTH, elapsedTimeRounded);
                            mMpInstance.getPeople().increment(AutomaticEvents.TOTAL_SESSIONS, 1);
                            mMpInstance.getPeople().increment(AutomaticEvents.TOTAL_SESSIONS_LENGTH, elapsedTimeRounded);
                            mMpInstance.track(AutomaticEvents.SESSION, sessionProperties, true);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    mMpInstance.onBackground();
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
        mCurrentActivity = new WeakReference<>(activity);

        mHasSeenActivity = true;
        mPaused = false;
        boolean wasBackground = !mIsForeground;
        mIsForeground = true;

        if (check != null) {
            mHandler.removeCallbacks(check);
        }

        if (wasBackground) {
            // App is in foreground now
            sStartSessionTime = (double) System.currentTimeMillis();
            mMpInstance.onForeground();
        }
    }

    @Override
    public void onActivityStopped(Activity activity) { }

    protected boolean isInForeground() {
        return mIsForeground;
    }

    /**
     * Marks the app as foregrounded when these callbacks were registered after an activity had
     * already resumed. registerActivityLifecycleCallbacks does not replay onActivityResumed, so
     * without this the first trip to the background after a deferred init would not flush.
     * No-op once any activity callback has been received, since that state is authoritative.
     */
    /* package */ void onRegisteredWhileResumed() {
        if (mHasSeenActivity) {
            return;
        }
        mIsForeground = true;
        mPaused = false;
    }

    private final MixpanelAPI mMpInstance;
    private final MPConfig mConfig;
    private WeakReference<Activity> mCurrentActivity;
}
