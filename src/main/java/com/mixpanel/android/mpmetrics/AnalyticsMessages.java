package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;

import com.mixpanel.android.util.Base64Coder;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


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
        mWorker = new Worker();
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
            }
            else {
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
    public void peopleMessage(final JSONObject peopleJson) {
        final Message m = Message.obtain();
        m.what = ENQUEUE_PEOPLE;
        m.obj = peopleJson;

        mWorker.runMessage(m);
    }

    public void postToServer() {
        final Message m = Message.obtain();
        m.what = FLUSH_QUEUE;

        mWorker.runMessage(m);
    }

    /**
     * Remove this when we eliminate the associated deprecated public ifc
     */
    public void setFlushInterval(final long milliseconds) {
        final Message m = Message.obtain();
        m.what = SET_FLUSH_INTERVAL;
        m.obj = milliseconds;

        mWorker.runMessage(m);
    }

    /**
     * Remove this when we eliminate the associated deprecated public ifc
     */
    public void setDisableFallback(boolean disableIfTrue) {
        final Message m = Message.obtain();
        m.what = SET_DISABLE_FALLBACK;
        m.obj = disableIfTrue;

        mWorker.runMessage(m);
    }

    public void installDecideCheck(final DecideUpdates check) {
        final Message m = Message.obtain();
        m.what = INSTALL_DECIDE_CHECK;
        m.obj = check;

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
        return new MPDbAdapter(context);
    }

    protected MPConfig getConfig(Context context) {
        return MPConfig.getInstance(context);
    }

    protected ServerMessage getPoster() {
        return new ServerMessage();
    }

    ////////////////////////////////////////////////////

    static class EventDescription {
        public EventDescription(String eventName, JSONObject properties, String token) {
            this.eventName = eventName;
            this.properties = properties;
            this.token = token;
        }

        public String getEventName() {
            return eventName;
        }

        public JSONObject getProperties() {
            return properties;
        }

        public String getToken() {
            return token;
        }

        private final String eventName;
        private final JSONObject properties;
        private final String token;
    }

    // Sends a message if and only if we are running with Mixpanel Message log enabled.
    // Will be called from the Mixpanel thread.
    private void logAboutMessageToMixpanel(String message) {
        if (MPConfig.DEBUG) {
            Log.d(LOGTAG, message + " (Thread " + Thread.currentThread().getId() + ")");
        }
    }

    // Worker will manage the (at most single) IO thread associated with
    // this AnalyticsMessages instance.
    // XXX: Worker class is unnecessary, should be just a subclass of HandlerThread
    private class Worker {
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
        private Handler restartWorkerThread() {
            final HandlerThread thread = new HandlerThread("com.mixpanel.android.AnalyticsWorker", Thread.MIN_PRIORITY);
            thread.start();
            final Handler ret = new AnalyticsMessageHandler(thread.getLooper());
            return ret;
        }

        private class AnalyticsMessageHandler extends Handler {
            public AnalyticsMessageHandler(Looper looper) {
                super(looper);
                mDbAdapter = null;
                mDecideChecker = new DecideChecker(mContext, mConfig);
                mDisableFallback = mConfig.getDisableFallback();
                mFlushInterval = mConfig.getFlushInterval();
                mSystemInformation = new SystemInformation(mContext);
            }

            @Override
            public void handleMessage(Message msg) {
                if (mDbAdapter == null) {
                    mDbAdapter = makeDbAdapter(mContext);
                    mDbAdapter.cleanupEvents(System.currentTimeMillis() - mConfig.getDataExpiration(), MPDbAdapter.Table.EVENTS);
                    mDbAdapter.cleanupEvents(System.currentTimeMillis() - mConfig.getDataExpiration(), MPDbAdapter.Table.PEOPLE);
                }

                try {
                    int queueDepth = -1;

                    if (msg.what == SET_FLUSH_INTERVAL) {
                        final Long newIntervalObj = (Long) msg.obj;
                        logAboutMessageToMixpanel("Changing flush interval to " + newIntervalObj);
                        mFlushInterval = newIntervalObj.longValue();
                        removeMessages(FLUSH_QUEUE);
                    }
                    else if (msg.what == SET_DISABLE_FALLBACK) {
                        final Boolean disableState = (Boolean) msg.obj;
                        logAboutMessageToMixpanel("Setting fallback to " + disableState);
                        mDisableFallback = disableState.booleanValue();
                    }
                    else if (msg.what == ENQUEUE_PEOPLE) {
                        final JSONObject message = (JSONObject) msg.obj;

                        logAboutMessageToMixpanel("Queuing people record for sending later");
                        logAboutMessageToMixpanel("    " + message.toString());

                        queueDepth = mDbAdapter.addJSON(message, MPDbAdapter.Table.PEOPLE);
                    }
                    else if (msg.what == ENQUEUE_EVENTS) {
                        final EventDescription eventDescription = (EventDescription) msg.obj;
                        try {
                            final JSONObject message = prepareEventObject(eventDescription);
                            logAboutMessageToMixpanel("Queuing event for sending later");
                            logAboutMessageToMixpanel("    " + message.toString());
                            queueDepth = mDbAdapter.addJSON(message, MPDbAdapter.Table.EVENTS);
                        } catch (final JSONException e) {
                            Log.e(LOGTAG, "Exception tracking event " + eventDescription.getEventName(), e);
                        }
                    }
                    else if (msg.what == FLUSH_QUEUE) {
                        logAboutMessageToMixpanel("Flushing queue due to scheduled or forced flush");
                        updateFlushFrequency();
                        mDecideChecker.runDecideChecks(getPoster());
                        sendAllData(mDbAdapter);
                    }
                    else if (msg.what == INSTALL_DECIDE_CHECK) {
                        logAboutMessageToMixpanel("Installing a check for surveys and in app notifications");
                        final DecideUpdates check = (DecideUpdates) msg.obj;
                        mDecideChecker.addDecideCheck(check);
                        mDecideChecker.runDecideChecks(getPoster());
                    }
                    else if (msg.what == KILL_WORKER) {
                        Log.w(LOGTAG, "Worker received a hard kill. Dumping all events and force-killing. Thread id " + Thread.currentThread().getId());
                        synchronized(mHandlerLock) {
                            mDbAdapter.deleteDB();
                            mHandler = null;
                            Looper.myLooper().quit();
                        }
                    } else {
                        Log.e(LOGTAG, "Unexpected message received by Mixpanel worker: " + msg);
                    }

                    ///////////////////////////

                    if (queueDepth >= mConfig.getBulkUploadLimit()) {
                        logAboutMessageToMixpanel("Flushing queue due to bulk upload limit");
                        updateFlushFrequency();
                        sendAllData(mDbAdapter);
                    } else if (queueDepth > 0 && !hasMessages(FLUSH_QUEUE)) {
                        // The !hasMessages(FLUSH_QUEUE) check is a courtesy for the common case
                        // of delayed flushes already enqueued from inside of this thread.
                        // Callers outside of this thread can still send
                        // a flush right here, so we may end up with two flushes
                        // in our queue, but we're OK with that.

                        logAboutMessageToMixpanel("Queue depth " + queueDepth + " - Adding flush in " + mFlushInterval);
                        if (mFlushInterval >= 0) {
                            sendEmptyMessageDelayed(FLUSH_QUEUE, mFlushInterval);
                        }
                    }
                } catch (final RuntimeException e) {
                    Log.e(LOGTAG, "Worker threw an unhandled exception", e);
                    synchronized (mHandlerLock) {
                        mHandler = null;
                        try {
                            Looper.myLooper().quit();
                            Log.e(LOGTAG, "Mixpanel will not process any more analytics messages", e);
                        } catch (final Exception tooLate) {
                            Log.e(LOGTAG, "Could not halt looper", tooLate);
                        }
                    }
                }
            }// handleMessage


            private void sendAllData(MPDbAdapter dbAdapter) {
                final ServerMessage poster = getPoster();
                if (! poster.isOnline(mContext)) {
                    logAboutMessageToMixpanel("Not flushing data to Mixpanel because the device is not connected to the internet.");
                    return;
                }

                logAboutMessageToMixpanel("Sending records to Mixpanel");
                if (mDisableFallback) {
                    sendData(dbAdapter, MPDbAdapter.Table.EVENTS, new String[]{ mConfig.getEventsEndpoint() });
                    sendData(dbAdapter, MPDbAdapter.Table.PEOPLE, new String[]{ mConfig.getPeopleEndpoint() });
                 } else {
                    sendData(dbAdapter, MPDbAdapter.Table.EVENTS,
                             new String[]{ mConfig.getEventsEndpoint(), mConfig.getEventsFallbackEndpoint() });
                    sendData(dbAdapter, MPDbAdapter.Table.PEOPLE,
                             new String[]{ mConfig.getPeopleEndpoint(), mConfig.getPeopleFallbackEndpoint() });
                }
            }

            private void sendData(MPDbAdapter dbAdapter, MPDbAdapter.Table table, String[] urls) {
                final ServerMessage poster = getPoster();
                final String[] eventsData = dbAdapter.generateDataString(table);

                if (eventsData != null) {
                    final String lastId = eventsData[0];
                    final String rawMessage = eventsData[1];

                    final String encodedData = Base64Coder.encodeString(rawMessage);
                    final List<NameValuePair> params = new ArrayList<NameValuePair>(1);
                    params.add(new BasicNameValuePair("data", encodedData));
                    if (MPConfig.DEBUG) {
                        params.add(new BasicNameValuePair("verbose", "1"));
                    }

                    boolean deleteEvents = true;
                    byte[] response;
                    for (String url : urls) {
                        try {
                            response = poster.performRequest(url, params);
                            deleteEvents = true; // Delete events on any successful post, regardless of 1 or 0 response
                            if (null == response) {
                                if (MPConfig.DEBUG) {
                                    Log.d(LOGTAG, "Response was null, unexpected failure posting to " + url + ".");
                                }
                            } else {
                                String parsedResponse;
                                try {
                                    parsedResponse = new String(response, "UTF-8");
                                } catch (UnsupportedEncodingException e) {
                                    throw new RuntimeException("UTF not supported on this platform?", e);
                                }

                                logAboutMessageToMixpanel("Successfully posted to " + url + ": \n" + rawMessage);
                                logAboutMessageToMixpanel("Response was " + parsedResponse);
                            }
                            break;
                        } catch (final OutOfMemoryError e) {
                            Log.e(LOGTAG, "Out of memory when posting to " + url + ".", e);
                            break;
                        } catch (final MalformedURLException e) {
                            Log.e(LOGTAG, "Cannot interpret " + url + " as a URL.", e);
                            break;
                        } catch (final IOException e) {
                            if (MPConfig.DEBUG)
                                Log.d(LOGTAG, "Cannot post message to " + url + ".", e);
                            deleteEvents = false;
                        }
                    }

                    if (deleteEvents) {
                        logAboutMessageToMixpanel("Not retrying this batch of events, deleting them from DB.");
                        dbAdapter.cleanupEvents(lastId, table);
                    } else {
                        logAboutMessageToMixpanel("Retrying this batch of events.");
                        if (!hasMessages(FLUSH_QUEUE)) {
                            sendEmptyMessageDelayed(FLUSH_QUEUE, mFlushInterval);
                        }
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

                final DisplayMetrics displayMetrics = mSystemInformation.getDisplayMetrics();
                ret.put("$screen_dpi", displayMetrics.densityDpi);
                ret.put("$screen_height", displayMetrics.heightPixels);
                ret.put("$screen_width", displayMetrics.widthPixels);

                final String applicationVersionName = mSystemInformation.getAppVersionName();
                if (null != applicationVersionName)
                    ret.put("$app_version", applicationVersionName);

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
            private long mFlushInterval; // XXX remove when associated deprecated APIs are removed
            private boolean mDisableFallback; // XXX remove when associated deprecated APIs are removed
            private final DecideChecker mDecideChecker;
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

    /////////////////////////////////////////////////////////

    // Used across thread boundaries
    private final Worker mWorker;
    private final Context mContext;
    private final MPConfig mConfig;

    // Messages for our thread
    private static int ENQUEUE_PEOPLE = 0; // submit events and people data
    private static int ENQUEUE_EVENTS = 1; // push given JSON message to people DB
    private static int FLUSH_QUEUE = 2; // push given JSON message to events DB
    private static int KILL_WORKER = 5; // Hard-kill the worker thread, discarding all events on the event queue. This is for testing, or disasters.
    private static int INSTALL_DECIDE_CHECK = 12; // Run this DecideCheck at intervals until it isDestroyed()

    private static int SET_FLUSH_INTERVAL = 4; // XXX REMOVE when associated deprecated APIs are removed
    private static int SET_DISABLE_FALLBACK = 10; // XXX REMOVE when associated deprecated APIs are removed

    private static final String LOGTAG = "MixpanelAPI";

    private static final Map<Context, AnalyticsMessages> sInstances = new HashMap<Context, AnalyticsMessages>();

}
