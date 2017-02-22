package com.mixpanel.android.util;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.mixpanel.android.mpmetrics.MixpanelAPI;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

/**
 * Created by yardeneitan on 2/20/17.
 */
public class ExceptionHandler {
    private final static String TAG = "ExceptionHandler";

    private static final String DEFAULT_HANDLER_PACKAGE_NAME = "com.android.internal.os";
    private static final int MAX_STACK_TRACE_SIZE = 131071; //128 KB - 1
    private static final int TIMESTAMP_DIFFERENCE_TO_AVOID_RESTART_LOOPS_IN_MILLIS = 2000;
    private static final String SHARED_PREFERENCES_FILE = "custom_activity_on_crash";
    private static final String SHARED_PREFERENCES_FIELD_TIMESTAMP = "last_crash_timestamp";
    private static Application application;
    private final MixpanelAPI mMixpanel;
    private final Context mContext;

    public ExceptionHandler(MixpanelAPI mixpanel, Context context) {
        mMixpanel = mixpanel;
        mContext = context;
        initialize();
    }

    public void initialize() {
        try {
            if (mContext == null) {
                Log.e(TAG, "exception handler failed: context is null!");
            } else {
                final Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();

                    if (oldHandler != null && !oldHandler.getClass().getName().startsWith(DEFAULT_HANDLER_PACKAGE_NAME)) {
                        Log.e(TAG, "IMPORTANT WARNING! If you use ACRA, Crashlytics or similar libraries, you must initialize them AFTER! Your original handler will not be called.");
                    }

                    application = (Application) mContext.getApplicationContext();

                    //We define a default exception handler that does what we want so it can be called from Crashlytics/ACRA
                    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                        @Override
                        public void uncaughtException(Thread thread, final Throwable throwable) {
                            Log.e(TAG, "App has crashed, tracking to Mixpanel");

                            if (hasCrashedInTheLastSeconds(application)) {
                                Log.e(TAG, "App already crashed in the last 2 seconds, not starting custom error activity because we could enter a restart loop. Are you sure that your app does not crash directly on init?", throwable);
                                if (oldHandler != null) {
                                    oldHandler.uncaughtException(thread, throwable);
                                    return;
                                }
                            } else {
                                setLastCrashTimestamp(application, new Date().getTime());
                                StringWriter sw = new StringWriter();
                                PrintWriter pw = new PrintWriter(sw);
                                throwable.printStackTrace(pw);
                                String stackTraceString = sw.toString();
                                String message = throwable.getMessage();
                                //Reduce data to 128KB so we don't get a TransactionTooLargeException when sending the intent.
                                //The limit is 1MB on Android but some devices seem to have it lower.
                                if (stackTraceString.length() > MAX_STACK_TRACE_SIZE) {
                                    String disclaimer = " [stack trace too large]";
                                    stackTraceString = stackTraceString.substring(0, MAX_STACK_TRACE_SIZE - disclaimer.length()) + disclaimer;
                                }
                                //track mixpanel
								try {
									final JSONObject properties = new JSONObject();
									properties.put("Reason", message);
									properties.put("Trace", stackTraceString);
									mMixpanel.saveCrashTrack("App Crashed", properties);

								} catch (JSONException e) {
									e.printStackTrace();
								}
                            }
                            killCurrentProcess();
                        }
                    });
                    Log.i(TAG, "ExceptionHandler has been initalized.");
            }
        } catch (Throwable t) {
            Log.e(TAG, "An unknown error occurred while initializing the exception handler.", t);
        }
    }

    private static boolean hasCrashedInTheLastSeconds(Context context) {
        long lastTimestamp = getLastCrashTimestamp(context);
        long currentTimestamp = new Date().getTime();

        return (lastTimestamp <= currentTimestamp && currentTimestamp - lastTimestamp < TIMESTAMP_DIFFERENCE_TO_AVOID_RESTART_LOOPS_IN_MILLIS);
    }

    private static void setLastCrashTimestamp(Context context, long timestamp) {
        context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE).edit().putLong(SHARED_PREFERENCES_FIELD_TIMESTAMP, timestamp).commit();
    }

    private static long getLastCrashTimestamp(Context context) {
        return context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE).getLong(SHARED_PREFERENCES_FIELD_TIMESTAMP, -1);
    }

    private static void killCurrentProcess() {
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(10);
    }
}
