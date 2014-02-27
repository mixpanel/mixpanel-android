package com.mixpanel.android.mpmetrics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

@TargetApi(14)
class MixpanelActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

    public MixpanelActivityLifecycleCallbacks(MixpanelAPI mpInstance) {
        mMpInstance = mpInstance;
    }

    /**
     * If MixpanelActivityLifecycleCallbacks is registered with the Application then this method
     * will be called anytime an activity is created. Our goal is to automatically check for and show
     * an eligible survey when the app is opened. The Mixpanel library is unlikely to be
     * instantiated in time for this to be called on the initial opening of the application.
     * However, this method is executed when the application is in memory but closed and the
     * user re-opens it.
     *
     * @param activity
     * @param savedInstanceState
     */
    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (! isActivityValid(activity)) {
            return;
        }
        setStartTimeIfNeeded();

        if(activity.isTaskRoot()) {
            checkForDecideUpdates(activity);
        }
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
        if (! isActivityValid(activity)) {
            return;
        }
        setStartTimeIfNeeded();

        if (!mHasDoneFirstCheck && activity.isTaskRoot()) {
            checkForDecideUpdates(activity);
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

    private void checkForDecideUpdates(final Activity activity) {
        mHasDoneFirstCheck = true;
        mMpInstance.getPeople().checkForNotification(new InAppNotificationCallbacks() {
            @Override
            public void foundNotification(final InAppNotification n) {
                if (null == n) {
                    checkForSurveys(activity);
                } else if (isActivityValid(activity)) {
                    mMpInstance.getPeople().showNotification(n, activity);
                }
            }
        });
    }

    /**
     * Check for surveys and show one if applicable only if the activity is the task root.
     * We use activity.findViewById(android.R.id.content) to get the root view of the root activity
     * We instantiate a new SurveyCallbacks that auto-shows the survey.
     * @param activity
     */
    private void checkForSurveys(final Activity activity) {
        if (! ConfigurationChecker.checkSurveyActivityAvailable(activity.getApplicationContext())) {
            return;
        }

        mMpInstance.getPeople().checkForSurvey(new SurveyCallbacks() {
            @Override
            public void foundSurvey(final Survey survey) {
                showOrAskToShowSurvey(survey, activity);
            }
        }, activity);
    }

    private void showOrAskToShowSurvey(final Survey survey, final Activity activity) {
        if (null == survey) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "No survey found, nothing to show the user.");
            return;
        } else if (! isActivityValid(activity)) {
            Log.i(LOGTAG, "Activity is dead, can't show a survey");
            return;
        }

        final long endTime = System.currentTimeMillis();
        final long totalTime = endTime - getStartTime();
        if (totalTime <= mTimeoutMillis) { // If we're quick enough, just show the survey!
            if (MPConfig.DEBUG) Log.d(LOGTAG, "found survey " + survey.getId() + ", calling showSurvey...");
            mMpInstance.getPeople().showSurvey(survey, activity);
        } else {
            final AlertDialog.Builder alertBuilder = new AlertDialog.Builder(activity);
            alertBuilder.setTitle("We'd love your feedback!");
            alertBuilder.setMessage("Mind taking a quick survey?");
            alertBuilder.setPositiveButton("Sure", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mMpInstance.getPeople().showSurvey(survey, activity);
                }
            });
            alertBuilder.setNegativeButton("No, Thanks", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Don't hassle the user about this particular survey again.
                    mMpInstance.getPeople().append("$surveys", survey.getId());
                    mMpInstance.getPeople().append("$collections", survey.getCollectionId());
                }
            });
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    alertBuilder.show();
                }
            });
        }
    }

    private boolean isActivityValid(Activity activity) {
        if (null == activity) {
            return false;
        }
        if (activity.isFinishing()) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= 17) {
            if (activity.isDestroyed()) {
                return false;
            }
        }

        return true;
    }

    private synchronized void setStartTimeIfNeeded() {
        if (mStartTime < 0) {
            mStartTime = System.currentTimeMillis();
        }
    }

    private synchronized long getStartTime() {
        return mStartTime;
    }

    private final MixpanelAPI mMpInstance;
    private boolean mHasDoneFirstCheck = false;
    private long mStartTime = -1;
    private final long mTimeoutMillis = 2000; // 2 second timeout
    private static final String LOGTAG = "MixpanelAPI:MixpanelActivityLifecycleCallbacks";
}
