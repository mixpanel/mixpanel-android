// Copyright 2014 Square, Inc.
package com.mixpanel.android.util;

import static com.mixpanel.android.util.Log.Level.DEBUG;
import static com.mixpanel.android.util.Log.Level.ERROR;
import static com.mixpanel.android.util.Log.Level.INFO;
import static com.mixpanel.android.util.Log.Level.WARN;

public class Log {

    private static Level level = Level.DEBUG;

    public static enum Level {
        DEBUG, INFO, WARN, ERROR, NONE;
        boolean shouldLog(Level messageLevel) {
            return ordinal() <= messageLevel.ordinal();
        }
    }

    public static void setLevel(Level level) {
        Log.level = level;
    }

    public static void d(String tag, String message) {
        if (level.shouldLog(DEBUG)) android.util.Log.d(tag, message);
    }

    public static void d(String tag, String message, Throwable e) {
        if (level.shouldLog(DEBUG)) android.util.Log.d(tag, message, e);
    }

    public static void i(String tag, String message) {
        if (level.shouldLog(INFO)) android.util.Log.i(tag, message);
    }

    public static void i(String tag, String message, Throwable e) {
        if (level.shouldLog(INFO)) android.util.Log.i(tag, message, e);
    }

    public static void w(String tag, Throwable e) {
        if (level.shouldLog(WARN)) android.util.Log.w(tag, e);
    }

    public static void w(String tag, String message) {
        if (level.shouldLog(WARN)) android.util.Log.w(tag, message);
    }

    public static void e(String tag, String message, Throwable e) {
        if (level.shouldLog(ERROR)) android.util.Log.d(tag, message);
    }

    public static void e(String tag, String message) {
        if (level.shouldLog(ERROR)) android.util.Log.e(tag, message);
    }
}
