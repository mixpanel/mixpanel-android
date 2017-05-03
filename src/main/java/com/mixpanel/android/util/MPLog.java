package com.mixpanel.android.util;

import android.util.Log;

import com.mixpanel.android.mpmetrics.MPConfig;

public class MPLog {

    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;
    public static final int NONE = Integer.MAX_VALUE;

    private static int sMinLevel = WARN;

    public static void setLevel(int minLevel) {
        sMinLevel = minLevel;
    }

    public static void v(String tag, String message) {
        if (shouldLog(VERBOSE)) {
            Log.v(tag, message);
        }
    }

    public static void v(String tag, String message, Throwable throwable) {
        if (shouldLog(VERBOSE)) {
            Log.v(tag, message, throwable);
        }
    }

    public static void d(String tag, String message) {
        if (shouldLog(DEBUG)) {
            Log.d(tag, message);
        }
    }

    public static void d(String tag, String message, Throwable throwable) {
        if (shouldLog(DEBUG)) {
            Log.d(tag, message, throwable);
        }
    }

    public static void i(String tag, String message) {
        if (shouldLog(INFO)) {
            Log.i(tag, message);
        }
    }

    public static void i(String tag, String message, Throwable throwable) {
        if (shouldLog(INFO)) {
            Log.i(tag, message, throwable);
        }
    }

    public static void w(String tag, String message) {
        if (shouldLog(WARN)) {
            Log.w(tag, message);
        }
    }

    public static void w(String tag, String message, Throwable throwable) {
        if (shouldLog(WARN)) {
            Log.w(tag, message, throwable);
        }
    }

    public static void e(String tag, String message) {
        if (shouldLog(ERROR)) {
            Log.e(tag, message);
        }
    }

    public static void e(String tag, String message, Throwable throwable) {
        if (shouldLog(ERROR)) {
            Log.e(tag, message, throwable);
        }
    }

    public static void wtf(String tag, String message) {
        if (shouldLog(ERROR)) {
            Log.wtf(tag, message);
        }
    }

    public static void wtf(String tag, String message, Throwable throwable) {
        if (shouldLog(ERROR)) {
            Log.wtf(tag, message, throwable);
        }
    }

    private static boolean shouldLog(int level) {
        return sMinLevel <= level;
    }
}
