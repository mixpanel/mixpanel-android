package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.DisplayMetrics;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.mixpanel.android.util.Base64Coder;
import com.mixpanel.android.util.HttpService;
import com.mixpanel.android.util.MPLog;
import com.mixpanel.android.util.RemoteService;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

/**
 * Manage communication of events with the internal database and the Mixpanel servers.
 *
 * <p>This class straddles the thread boundary between user threads and
 * a logical Mixpanel thread.
 */
/* package */ class AnalyticsMessages {

    /**
     * Do not call directly. You should call AnalyticsMessages.getInstance()
     */
    /* package */ AnalyticsMessages(final Context context) {
        mContext = context;
        mConfig = getConfig(context);
        mWorker = createWorker();
        getPoster().checkIsMixpanelBlocked();
    }

    protected Worker createWorker() {
        return new Worker();
    }

    /**
     * Use this to get an instance of AnalyticsMessages instead of creating one directly
     * for yourself.
     *
     * @param messageContext should be the Main Activity of the application
     *     associated with these messages.
     */
    public static AnalyticsMessages getInstance(final Context messageContext) {
        synchronized (sInstances) {
            final Context appContext = messageContext.getApplicationContext();
            AnalyticsMessages ret;
            if (! sInstances.containsKey(appContext)) {
                ret = new AnalyticsMessages(appContext);
                sInstances.put(appContext, ret);
            } else {
                ret = sInstances.get(appContext);
            }
            return ret;
        }
    }

    public void eventsMessage(final EventDescription eventDescription) {
        final Message m = Message.obtain();
        m.what = ENQUEUE_EVENTS;
        m.obj = eventDescription;
        mWorker.runMessage(m);
    }

    // Must be thread safe.
    public void peopleMessage(final PeopleDescription peopleDescription) {
        final Message m = Message.obtain();
        m.what = ENQUEUE_PEOPLE;
        m.obj = peopleDescription;

        mWorker.runMessage(m);
    }

    public void postToServer(final FlushDescription flushDescription) {
        final Message m = Message.obtain();
        m.what = FLUSH_QUEUE;
        m.obj = flushDescription.getToken();
        m.arg1 = flushDescription.shouldCheckDecide() ? 1 : 0;

        mWorker.runMessage(m);
    }

    public void installDecideCheck(final DecideMessages check) {
        final Message m = Message.obtain();
        m.what = INSTALL_DECIDE_CHECK;
        m.obj = check;

        mWorker.runMessage(m);
    }

    public void registerForGCM(final String senderID) {
        final Message m = Message.obtain();
        m.what = REGISTER_FOR_GCM;
        m.obj = senderID;

        mWorker.runMessage(m);
    }

    public void hardKill() {
        final Message m = Message.obtain();
        m.what = KILL_WORKER;

        mWorker.runMessage(m);
    }

    /////////////////////////////////////////////////////////
    // For testing, to allow for Mocking.

    /* package */ boolean isDead() {
        return mWorker.isDead();
    }

    protected MPDbAdapter makeDbAdapter(Context context) {
        return MPDbAdapter.getInstance(context);
    }

    protected MPConfig getConfig(Context context) {
        return MPConfig.getInstance(context);
    }

    protected RemoteService getPoster() {
        return new HttpService();
    }

    ////////////////////////////////////////////////////

    static class EventDescription extends MixpanelDescription {
        public EventDescription(String eventName, JSONObject properties, String token, boolean isAutomatic) {
            super(token);
            mEventName = eventName;
            mProperties = properties;
            mIsAutomatic = isAutomatic;
        }

        public String getEventName() {
            return mEventName;
        }

        public JSONObject getProperties() {
            return mProperties;
        }

        public boolean isAutomatic() {
            return mIsAutomatic;
        }

        private final String mEventName;
        private final JSONObject mProperties;
        private final boolean mIsAutomatic;
    }

    static class PeopleDescription extends MixpanelDescription {
        public PeopleDescription(JSONObject message, String token) {
            super(token);
            this.message = message;
        }

        public JSONObject getMessage() {
            return message;
        }


        private final JSONObject message;
    }

    static class FlushDescription extends MixpanelDescription {
        public FlushDescription(String token) {
            this(token, true);
        }

        protected FlushDescription(String token, boolean checkDecide) {
            super(token);
            this.checkDecide = checkDecide;
        }

        public boolean shouldCheckDecide() {
            return checkDecide;
        }

        private final boolean checkDecide;
    }

    static class MixpanelDescription {
        public MixpanelDescription(String token) {
            this.mToken = token;
        }

        public String getToken() {
            return mToken;
        }

        private final String mToken;
    }

    // Sends a message if and only if we are running with Mixpanel Message log enabled.
    // Will be called from the Mixpanel thread.
    private void logAboutMessageToMixpanel(String message) {
        MPLog.v(LOGTAG, message + " (Thread " + Thread.currentThread().getId() + ")");
    }

    private void logAboutMessageToMixpanel(String message, Throwable e) {
        MPLog.v(LOGTAG, message + " (Thread " + Thread.currentThread().getId() + ")", e);
    }

    // Worker will manage the (at most single) IO thread associated with
    // this AnalyticsMessages instance.
    // XXX: Worker class is unnecessary, should be just a subclass of HandlerThread
    class Worker {
        public Worker() {
            mHandler = restartWorkerThread();
        }

        public boolean isDead() {
            synchronized(mHandlerLock) {
                return mHandler == null;
            }
        }

        public void runMessage(Message msg) {
            synchronized(mHandlerLock) {
                if (mHandler == null) {
                    // We died under suspicious circumstances. Don't try to send any more events.
                    logAboutMessageToMixpanel("Dead mixpanel worker dropping a message: " + msg.what);
                } else {
                    mHandler.sendMessage(msg);
                }
            }
        }

        // NOTE that the returned worker will run FOREVER, unless you send a hard kill
        // (which you really shouldn't)
        protected Handler restartWorkerThread() {
            final HandlerThread thread = new HandlerThread("com.mixpanel.android.AnalyticsWorker", Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();
            final Handler ret = new AnalyticsMessageHandler(thread.getLooper());
            return ret;
        }

        class AnalyticsMessageHandler extends Handler {
            public AnalyticsMessageHandler(Looper looper) {
                super(looper);
                mDbAdapter = null;
                mSystemInformation = new SystemInformation(mContext);
                mDecideChecker = createDecideChecker();
                mFlushInterval = mConfig.getFlushInterval();
            }

            protected DecideChecker createDecideChecker() {
                return new DecideChecker(mContext, mConfig, mSystemInformation);
            }

            @Override
            public void handleMessage(Message msg) {
                if (mDbAdapter == null) {
                    mDbAdapter = makeDbAdapter(mContext);
                    mDbAdapter.cleanupEvents(System.currentTimeMillis() - mConfig.getDataExpiration(), MPDbAdapter.Table.EVENTS);
                    mDbAdapter.cleanupEvents(System.currentTimeMillis() - mConfig.getDataExpiration(), MPDbAdapter.Table.PEOPLE);
                }

                try {
                    int returnCode = MPDbAdapter.DB_UNDEFINED_CODE;
                    String token = null;

                    if (msg.what == ENQUEUE_PEOPLE) {
                        final PeopleDescription message = (PeopleDescription) msg.obj;

                        logAboutMessageToMixpanel("Queuing people record for sending later");
                        logAboutMessageToMixpanel("    " + message.toString());
                        token = message.getToken();
                        returnCode = mDbAdapter.addJSON(message.getMessage(), token, MPDbAdapter.Table.PEOPLE, false);
                    } else if (msg.what == ENQUEUE_EVENTS) {
                        final EventDescription eventDescription = (EventDescription) msg.obj;
                        try {
                            final JSONObject message = prepareEventObject(eventDescription);
                            logAboutMessageToMixpanel("Queuing event for sending later");
                            logAboutMessageToMixpanel("    " + message.toString());
                            token = eventDescription.getToken();

                            DecideMessages decide = mDecideChecker.getDecideMessages(token);
                            if (decide != null && eventDescription.isAutomatic() && !decide.shouldTrackAutomaticEvent()) {
                                return;
                            }
                            returnCode = mDbAdapter.addJSON(message, token, MPDbAdapter.Table.EVENTS, eventDescription.isAutomatic());
                        } catch (final JSONException e) {
                            MPLog.e(LOGTAG, "Exception tracking event " + eventDescription.getEventName(), e);
                        }
                    } else if (msg.what == FLUSH_QUEUE) {
                        logAboutMessageToMixpanel("Flushing queue due to scheduled or forced flush");
                        updateFlushFrequency();
                        token = (String) msg.obj;
                        boolean shouldCheckDecide = msg.arg1 == 1 ? true : false;
                        sendAllData(mDbAdapter, token);
                        if (shouldCheckDecide && SystemClock.elapsedRealtime() >= mDecideRetryAfter) {
                            try {
                                mDecideChecker.runDecideCheck(token, getPoster());
                            } catch (RemoteService.ServiceUnavailableException e) {
                                mDecideRetryAfter = SystemClock.elapsedRealtime() + e.getRetryAfter() * 1000;
                            }
                        }
                    } else if (msg.what == INSTALL_DECIDE_CHECK) {
                        logAboutMessageToMixpanel("Installing a check for in-app notifications");
                        final DecideMessages check = (DecideMessages) msg.obj;
                        mDecideChecker.addDecideCheck(check);
                        if (SystemClock.elapsedRealtime() >= mDecideRetryAfter) {
                            try {
                                mDecideChecker.runDecideCheck(check.getToken(), getPoster());
                            } catch (RemoteService.ServiceUnavailableException e) {
                                mDecideRetryAfter = SystemClock.elapsedRealtime() + e.getRetryAfter() * 1000;
                            }
                        }
                    } else if (msg.what == REGISTER_FOR_GCM) {
                        final String senderId = (String) msg.obj;
                        runGCMRegistration(senderId);
                    } else if (msg.what == KILL_WORKER) {
                        MPLog.w(LOGTAG, "Worker received a hard kill. Dumping all events and force-killing. Thread id " + Thread.currentThread().getId());
                        synchronized(mHandlerLock) {
                            mDbAdapter.deleteDB();
                            mHandler = null;
                            Looper.myLooper().quit();
                        }
                    } else {
                        MPLog.e(LOGTAG, "Unexpected message received by Mixpanel worker: " + msg);
                    }

                    ///////////////////////////
                    if ((returnCode >= mConfig.getBulkUploadLimit() || returnCode == MPDbAdapter.DB_OUT_OF_MEMORY_ERROR) && mFailedRetries <= 0 && token != null) {
                        logAboutMessageToMixpanel("Flushing queue due to bulk upload limit (" + returnCode + ") for project " + token);
                        updateFlushFrequency();
                        sendAllData(mDbAdapter, token);
                        if (SystemClock.elapsedRealtime() >= mDecideRetryAfter) {
                            try {
                                mDecideChecker.runDecideCheck(token, getPoster());
                            } catch (RemoteService.ServiceUnavailableException e) {
                                mDecideRetryAfter = SystemClock.elapsedRealtime() + e.getRetryAfter() * 1000;
                            }
                        }
                    } else if (returnCode > 0 && !hasMessages(FLUSH_QUEUE, token)) {
                        // The !hasMessages(FLUSH_QUEUE, token) check is a courtesy for the common case
                        // of delayed flushes already enqueued from inside of this thread.
                        // Callers outside of this thread can still send
                        // a flush right here, so we may end up with two flushes
                        // in our queue, but we're OK with that.

                        logAboutMessageToMixpanel("Queue depth " + returnCode + " - Adding flush in " + mFlushInterval);
                        if (mFlushInterval >= 0) {
                            final Message flushMessage = Message.obtain();
                            flushMessage.what = FLUSH_QUEUE;
                            flushMessage.obj = token;
                            flushMessage.arg1 = 1;
                            sendMessageDelayed(flushMessage, mFlushInterval);
                        }
                    }
                } catch (final RuntimeException e) {
                    MPLog.e(LOGTAG, "Worker threw an unhandled exception", e);
                    synchronized (mHandlerLock) {
                        mHandler = null;
                        try {
                            Looper.myLooper().quit();
                            MPLog.e(LOGTAG, "Mixpanel will not process any more analytics messages", e);
                        } catch (final Exception tooLate) {
                            MPLog.e(LOGTAG, "Could not halt looper", tooLate);
                        }
                    }
                }
            }// handleMessage

            protected long getTrackEngageRetryAfter() {
                return mTrackEngageRetryAfter;
            }

            private void runGCMRegistration(String senderID) {
                final String registrationId;
                try {
                    // We don't actually require Google Play Services to be available
                    // (since we can't specify what version customers will be using,
                    // and because the latest Google Play Services actually have
                    // dependencies on Java 7)

                    // Consider adding a transitive dependency on the latest
                    // Google Play Services version and requiring Java 1.7
                    // in the next major library release.
                    try {
                        final int resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(mContext);
                        if (resultCode != ConnectionResult.SUCCESS) {
                            MPLog.i(LOGTAG, "Can't register for push notifications, Google Play Services are not installed.");
                            return;
                        }
                    } catch (RuntimeException e) {
                        MPLog.i(LOGTAG, "Can't register for push notifications, Google Play services are not configured.");
                        return;
                    }

                    InstanceID instanceID = InstanceID.getInstance(mContext);
                    registrationId = instanceID.getToken(senderID, GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
                } catch (IOException e) {
                    MPLog.i(LOGTAG, "Exception when trying to register for GCM", e);
                    return;
                } catch (NoClassDefFoundError e) {
                    MPLog.w(LOGTAG, "Google play services were not part of this build, push notifications cannot be registered or delivered");
                    return;
                }

                MixpanelAPI.allInstances(new MixpanelAPI.InstanceProcessor() {
                    @Override
                    public void process(MixpanelAPI api) {
                        MPLog.v(LOGTAG, "Using existing pushId " + registrationId);
                        api.getPeople().setPushRegistrationId(registrationId);
                    }
                });
            }

            private void sendAllData(MPDbAdapter dbAdapter, String token) {
                final RemoteService poster = getPoster();
                if (!poster.isOnline(mContext, mConfig.getOfflineMode())) {
                    logAboutMessageToMixpanel("Not flushing data to Mixpanel because the device is not connected to the internet.");
                    return;
                }

                sendData(dbAdapter, token, MPDbAdapter.Table.EVENTS, mConfig.getEventsEndpoint());
                sendData(dbAdapter, token, MPDbAdapter.Table.PEOPLE, mConfig.getPeopleEndpoint());
            }

            private void sendData(MPDbAdapter dbAdapter, String token, MPDbAdapter.Table table, String url) {
                final RemoteService poster = getPoster();
                DecideMessages decideMessages = mDecideChecker.getDecideMessages(token);
                boolean includeAutomaticEvents = true;
                if (decideMessages == null || decideMessages.isAutomaticEventsEnabled() == null) {
                    includeAutomaticEvents = false;
                }
                String[] eventsData = dbAdapter.generateDataString(table, token, includeAutomaticEvents);
                Integer queueCount = 0;
                if (eventsData != null) {
                    queueCount = Integer.valueOf(eventsData[2]);
                }

                while (eventsData != null && queueCount > 0) {
                    final String lastId = eventsData[0];
                    final String rawMessage = eventsData[1];

                    final String encodedData = Base64Coder.encodeString(rawMessage);
                    final Map<String, Object> params = new HashMap<String, Object>();
                    params.put("data", encodedData);
                    if (MPConfig.DEBUG) {
                        params.put("verbose", "1");
                    }

                    boolean deleteEvents = true;
                    byte[] response;
                    try {
                        final SSLSocketFactory socketFactory = mConfig.getSSLSocketFactory();
                        response = poster.performRequest(url, params, socketFactory);
                        if (null == response) {
                            deleteEvents = false;
                            logAboutMessageToMixpanel("Response was null, unexpected failure posting to " + url + ".");
                        } else {
                            deleteEvents = true; // Delete events on any successful post, regardless of 1 or 0 response
                            String parsedResponse;
                            try {
                                parsedResponse = new String(response, "UTF-8");
                            } catch (UnsupportedEncodingException e) {
                                throw new RuntimeException("UTF not supported on this platform?", e);
                            }
                            if (mFailedRetries > 0) {
                                mFailedRetries = 0;
                                removeMessages(FLUSH_QUEUE, token);
                            }

                            logAboutMessageToMixpanel("Successfully posted to " + url + ": \n" + rawMessage);
                            logAboutMessageToMixpanel("Response was " + parsedResponse);
                        }
                    } catch (final OutOfMemoryError e) {
                        MPLog.e(LOGTAG, "Out of memory when posting to " + url + ".", e);
                    } catch (final MalformedURLException e) {
                        MPLog.e(LOGTAG, "Cannot interpret " + url + " as a URL.", e);
                    } catch (final RemoteService.ServiceUnavailableException e) {
                        logAboutMessageToMixpanel("Cannot post message to " + url + ".", e);
                        deleteEvents = false;
                        mTrackEngageRetryAfter = e.getRetryAfter() * 1000;
                    } catch (final SocketTimeoutException e) {
                        logAboutMessageToMixpanel("Cannot post message to " + url + ".", e);
                        deleteEvents = false;
                    } catch (final IOException e) {
                        logAboutMessageToMixpanel("Cannot post message to " + url + ".", e);
                        deleteEvents = false;
                    }

                    if (deleteEvents) {
                        logAboutMessageToMixpanel("Not retrying this batch of events, deleting them from DB.");
                        dbAdapter.cleanupEvents(lastId, table, token, includeAutomaticEvents);
                    } else {
                        removeMessages(FLUSH_QUEUE, token);
                        mTrackEngageRetryAfter = Math.max((long)Math.pow(2, mFailedRetries) * 60000, mTrackEngageRetryAfter);
                        mTrackEngageRetryAfter = Math.min(mTrackEngageRetryAfter, 10 * 60 * 1000); // limit 10 min
                        final Message flushMessage = Message.obtain();
                        flushMessage.what = FLUSH_QUEUE;
                        flushMessage.obj = token;
                        sendMessageDelayed(flushMessage, mTrackEngageRetryAfter);
                        mFailedRetries++;
                        logAboutMessageToMixpanel("Retrying this batch of events in " + mTrackEngageRetryAfter + " ms");
                        break;
                    }

                    eventsData = dbAdapter.generateDataString(table, token, includeAutomaticEvents);
                    if (eventsData != null) {
                        queueCount = Integer.valueOf(eventsData[2]);
                    }
                }
            }

            private JSONObject getDefaultEventProperties()
                    throws JSONException {
                final JSONObject ret = new JSONObject();

                ret.put("mp_lib", "android");
                ret.put("$lib_version", MPConfig.VERSION);

                // For querying together with data from other libraries
                ret.put("$os", "Android");
                ret.put("$os_version", Build.VERSION.RELEASE == null ? "UNKNOWN" : Build.VERSION.RELEASE);

                ret.put("$manufacturer", Build.MANUFACTURER == null ? "UNKNOWN" : Build.MANUFACTURER);
                ret.put("$brand", Build.BRAND == null ? "UNKNOWN" : Build.BRAND);
                ret.put("$model", Build.MODEL == null ? "UNKNOWN" : Build.MODEL);

                try {
                    try {
                        final int servicesAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(mContext);
                        switch (servicesAvailable) {
                            case ConnectionResult.SUCCESS:
                                ret.put("$google_play_services", "available");
                                break;
                            case ConnectionResult.SERVICE_MISSING:
                                ret.put("$google_play_services", "missing");
                                break;
                            case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:
                                ret.put("$google_play_services", "out of date");
                                break;
                            case ConnectionResult.SERVICE_DISABLED:
                                ret.put("$google_play_services", "disabled");
                                break;
                            case ConnectionResult.SERVICE_INVALID:
                                ret.put("$google_play_services", "invalid");
                                break;
                        }
                    } catch (RuntimeException e) {
                        // Turns out even checking for the service will cause explosions
                        // unless we've set up meta-data
                        ret.put("$google_play_services", "not configured");
                    }

                } catch (NoClassDefFoundError e) {
                    ret.put("$google_play_services", "not included");
                }

                final DisplayMetrics displayMetrics = mSystemInformation.getDisplayMetrics();
                ret.put("$screen_dpi", displayMetrics.densityDpi);
                ret.put("$screen_height", displayMetrics.heightPixels);
                ret.put("$screen_width", displayMetrics.widthPixels);

                final String applicationVersionName = mSystemInformation.getAppVersionName();
                if (null != applicationVersionName) {
                    ret.put("$app_version", applicationVersionName);
                    ret.put("$app_version_string", applicationVersionName);
                }

                 final Integer applicationVersionCode = mSystemInformation.getAppVersionCode();
                 if (null != applicationVersionCode) {
                    ret.put("$app_release", applicationVersionCode);
                    ret.put("$app_build_number", applicationVersionCode);
                }

                final Boolean hasNFC = mSystemInformation.hasNFC();
                if (null != hasNFC)
                    ret.put("$has_nfc", hasNFC.booleanValue());

                final Boolean hasTelephony = mSystemInformation.hasTelephony();
                if (null != hasTelephony)
                    ret.put("$has_telephone", hasTelephony.booleanValue());

                final String carrier = mSystemInformation.getCurrentNetworkOperator();
                if (null != carrier)
                    ret.put("$carrier", carrier);

                final Boolean isWifi = mSystemInformation.isWifiConnected();
                if (null != isWifi)
                    ret.put("$wifi", isWifi.booleanValue());

                final Boolean isBluetoothEnabled = mSystemInformation.isBluetoothEnabled();
                if (isBluetoothEnabled != null)
                    ret.put("$bluetooth_enabled", isBluetoothEnabled);

                final String bluetoothVersion = mSystemInformation.getBluetoothVersion();
                if (bluetoothVersion != null)
                    ret.put("$bluetooth_version", bluetoothVersion);

                return ret;
            }

            private JSONObject prepareEventObject(EventDescription eventDescription) throws JSONException {
                final JSONObject eventObj = new JSONObject();
                final JSONObject eventProperties = eventDescription.getProperties();
                final JSONObject sendProperties = getDefaultEventProperties();
                sendProperties.put("token", eventDescription.getToken());
                if (eventProperties != null) {
                    for (final Iterator<?> iter = eventProperties.keys(); iter.hasNext();) {
                        final String key = (String) iter.next();
                        sendProperties.put(key, eventProperties.get(key));
                    }
                }
                eventObj.put("event", eventDescription.getEventName());
                eventObj.put("properties", sendProperties);
                return eventObj;
            }

            private MPDbAdapter mDbAdapter;
            private final DecideChecker mDecideChecker;
            private final long mFlushInterval;
            private long mDecideRetryAfter;
            private long mTrackEngageRetryAfter;
            private int mFailedRetries;
        }// AnalyticsMessageHandler

        private void updateFlushFrequency() {
            final long now = System.currentTimeMillis();
            final long newFlushCount = mFlushCount + 1;

            if (mLastFlushTime > 0) {
                final long flushInterval = now - mLastFlushTime;
                final long totalFlushTime = flushInterval + (mAveFlushFrequency * mFlushCount);
                mAveFlushFrequency = totalFlushTime / newFlushCount;

                final long seconds = mAveFlushFrequency / 1000;
                logAboutMessageToMixpanel("Average send frequency approximately " + seconds + " seconds.");
            }

            mLastFlushTime = now;
            mFlushCount = newFlushCount;
        }

        private final Object mHandlerLock = new Object();
        private Handler mHandler;
        private long mFlushCount = 0;
        private long mAveFlushFrequency = 0;
        private long mLastFlushTime = -1;
        private SystemInformation mSystemInformation;
    }

    public long getTrackEngageRetryAfter() {
        return ((Worker.AnalyticsMessageHandler) mWorker.mHandler).getTrackEngageRetryAfter();
    }
    /////////////////////////////////////////////////////////

    // Used across thread boundaries
    private final Worker mWorker;
    protected final Context mContext;
    protected final MPConfig mConfig;

    // Messages for our thread
    private static final int ENQUEUE_PEOPLE = 0; // submit events and people data
    private static final int ENQUEUE_EVENTS = 1; // push given JSON message to people DB
    private static final int FLUSH_QUEUE = 2; // push given JSON message to events DB
    private static final int KILL_WORKER = 5; // Hard-kill the worker thread, discarding all events on the event queue. This is for testing, or disasters.
    private static final int INSTALL_DECIDE_CHECK = 12; // Run this DecideCheck at intervals until it isDestroyed()
    private static final int REGISTER_FOR_GCM = 13; // Register for GCM using Google Play Services

    private static final String LOGTAG = "MixpanelAPI.Messages";

    private static final Map<Context, AnalyticsMessages> sInstances = new HashMap<Context, AnalyticsMessages>();

}
