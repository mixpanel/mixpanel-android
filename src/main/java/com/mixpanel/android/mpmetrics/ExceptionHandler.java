package com.mixpanel.android.mpmetrics;


public class ExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "MixpanelAPI.Exception";

    private static final int SLEEP_TIMEOUT_MS = 400;

    private static ExceptionHandler sInstance;
    private final Thread.UncaughtExceptionHandler mDefaultExceptionHandler;
    private static final String DEFAULT_HANDLER_PACKAGE_NAME = "com.android.internal.os";

    public ExceptionHandler() {
        mDefaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    public static void init() {
        if (sInstance == null) {
            sInstance = new ExceptionHandler();
        }
    }

    @Override
    public void uncaughtException(final Thread t, final Throwable e) {
        // Only one worker thread - giving priority to storing the event first and then flush
        MixpanelAPI.allInstances(new MixpanelAPI.InstanceProcessor() {
            @Override
            public void process(MixpanelAPI mixpanel) {
                mixpanel.track(AutomaticEvents.APP_CRASHED, null, true);
            }
        });

        MixpanelAPI.allInstances(new MixpanelAPI.InstanceProcessor() {
            @Override
            public void process(MixpanelAPI mixpanel) {
                mixpanel.flushNoDecideCheck();
            }
        });

        if (mDefaultExceptionHandler != null) {
            mDefaultExceptionHandler.uncaughtException(t, e);
        } else {
            killProcessAndExit();
        }
    }

    private void killProcessAndExit() {
        try {
            Thread.sleep(SLEEP_TIMEOUT_MS);
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(10);
    }
}
