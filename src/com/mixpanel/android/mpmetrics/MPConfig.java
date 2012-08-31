package com.mixpanel.android.mpmetrics;

/**
 * Stores global configuration options for the Mixpanel library.
 * May be overridden to achieve custom behavior.
 */
public class MPConfig {
    // When we've reached this many track calls, flush immediately
    public static int BULK_UPLOAD_LIMIT = 40;

    // Time interval in ms events/people requests are flushed at.
    public static long FLUSH_RATE = 60 * 1000;

    // Remove events and people records that have sat around for this many milliseconds
    // on first initialization of the library. Default is 48 hours.
    // Must be reconfigured before the library is initialized for the first time.
    public static int DATA_EXPIRATION = 1000 * 60 * 60 * 48;

    // Base url that track requests will be sent to. Events will be sent to /track
    // and people requests will be sent to /engage
    public static String BASE_ENDPOINT = "http://api.mixpanel.com";

    // Time in milliseconds that the submission thread must be idle for before it dies.
    // Must be reconfigured before the library is initialized for the first time.
    public static int SUBMIT_THREAD_TTL = 180 * 1000;

    // Set to true to see debugging logcat output:
    public static boolean DEBUG = true; // false;

    // Set to true to send all api requests immediately
    public static boolean TEST_MODE = false;
}
