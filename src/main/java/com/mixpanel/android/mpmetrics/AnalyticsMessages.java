package com.mixpanel.android.mpmetrics;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;


/**
 * Manage communication of events with the internal database and the Mixpanel servers.
 *
 * Consider refactoring to use AsyncTasks instead of custom Looper/Handler assembly below.
 *
 * <p>This class straddles the thread boundary between user threads and
 * a logical Mixpanel thread.
 */
/* package */ class AnalyticsMessages {
    /**
     * Do not call directly. You should call AnalyticsMessages.getInstance()
     */
    /* package */ AnalyticsMessages(Context context) {
        mContext = context;
        mConfig = getConfig(context);
        mLogMixpanelMessages = new AtomicBoolean(false);
        mWorker = new Worker();
    }

    /**
     * Use this to get an instance of AnalyticsMessages instead of creating one directly
     * for yourself.
     *
     * @param messageContext should be the Main Activity of the application
     *     associated with these messages.
     */
    public static AnalyticsMessages getInstance(Context messageContext) {
        synchronized (sInstances) {
            Context appContext = messageContext.getApplicationContext();
            AnalyticsMessages ret;
            if (! sInstances.containsKey(appContext)) {
                if (MPConfig.DEBUG) Log.d(LOGTAG, "Constructing new AnalyticsMessages for Context " + appContext);
                ret = new AnalyticsMessages(appContext);
                sInstances.put(appContext, ret);
            }
            else {
                if (MPConfig.DEBUG) Log.d(LOGTAG, "AnalyticsMessages for Context " + appContext + " already exists- returning");
                ret = sInstances.get(appContext);
            }
            return ret;
        }
    }

    public void logPosts() {
        mLogMixpanelMessages.set(true);
    }

    public void eventsMessage(EventDTO eventDTO) {
        Message m = Message.obtain();
        m.what = ENQUEUE_EVENTS;
        m.obj = eventDTO;
        mWorker.runMessage(m);
    }

    public void peopleMessage(JSONObject peopleJson) {
        Message m = Message.obtain();
        m.what = ENQUEUE_PEOPLE;
        m.obj = peopleJson;

        mWorker.runMessage(m);
    }

    public void postToServer() {
        Message m = Message.obtain();
        m.what = FLUSH_QUEUE;

        mWorker.runMessage(m);
    }

    /**
     * Remove this when we eliminate the associated deprecated public ifc
     */
    public void setFlushInterval(long milliseconds) {
        Message m = Message.obtain();
        m.what = SET_FLUSH_INTERVAL;
        m.obj = new Long(milliseconds);

        mWorker.runMessage(m);
    }

    /**
     * Remove this when we eliminate the associated deprecated public ifc
     */
    public void setDisableFallback(boolean disableIfTrue) {
        Message m = Message.obtain();
        m.what = SET_DISABLE_FALLBACK;
        m.obj = new Boolean(disableIfTrue);

        mWorker.runMessage(m);
    }

    /**
     * All fields SurveyCheck must return non-null values.
     */
    public interface SurveyCheck {
        public SurveyCallbacks getCallbacks();
        public String getDistinctId();
        public String getToken();
    }

    public void checkForSurveys(SurveyCheck check) {
        Message m = Message.obtain();
        m.what = CHECK_FOR_SURVEYS;
        m.obj = check;
        mWorker.runMessage(m);
    }

    public void hardKill() {
        Message m = Message.obtain();
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
        return MPConfig.readConfig(context);
    }

    protected ServerMessage getPoster() {
        return new ServerMessage();
    }

    ////////////////////////////////////////////////////

    static class EventDTO {

        public EventDTO(String eventName, JSONObject properties, String token) {
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

        private String eventName;
        private JSONObject properties;
        private String token;
    }

    // Sends a message if and only if we are running with Mixpanel Message log enabled.
    // Will be called from the Mixpanel thread.
    private void logAboutMessageToMixpanel(String message) {
        if (mLogMixpanelMessages.get() || MPConfig.DEBUG) {
            Log.i(LOGTAG, message + " (Thread " + Thread.currentThread().getId() + ")");
        }
    }

    // Worker will manage the (at most single) IO thread associated with
    // this AnalyticsMessages instance.
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
            if (isDead()) {
                // We died under suspicious circumstances. Don't try to send any more events.
                logAboutMessageToMixpanel("Dead mixpanel worker dropping a message: " + msg);
            }
            else {
                synchronized(mHandlerLock) {
                    if (mHandler != null) mHandler.sendMessage(msg);
                }
            }
        }

        // NOTE that the returned worker will run FOREVER, unless you send a hard kill
        // (which you really shouldn't)
        private Handler restartWorkerThread() {
            Handler ret = null;

            final SynchronousQueue<Handler> handlerQueue = new SynchronousQueue<Handler>();

            Thread thread = new Thread() {
                @Override
                public void run() {
                    if (MPConfig.DEBUG)
                        Log.i(LOGTAG, "Starting worker thread " + this.getId());

                    Looper.prepare();
                    try {
                        handlerQueue.put(new AnalyticsMessageHandler());
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Couldn't build worker thread for Analytics Messages", e);
                    }

                    try {
                        Looper.loop();
                    } catch (RuntimeException e) {
                        Log.e(LOGTAG, "Mixpanel Thread dying from RuntimeException", e);
                    }
                }
            };
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();

            try {
                ret = handlerQueue.take();
            } catch (InterruptedException e) {
                throw new RuntimeException("Couldn't retrieve handler from worker thread");
            }

            return ret;
        }

        private class AnalyticsMessageHandler extends Handler {
            public AnalyticsMessageHandler() {
                super();
                mDbAdapter = null;
                mSeenSurveys = Collections.synchronizedSet(new HashSet<Integer>());
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
                        JSONObject message = (JSONObject) msg.obj;

                        logAboutMessageToMixpanel("Queuing people record for sending later");
                        logAboutMessageToMixpanel("    " + message.toString());

                        queueDepth = mDbAdapter.addJSON(message, MPDbAdapter.Table.PEOPLE);
                    }
                    else if (msg.what == ENQUEUE_EVENTS) {
                        EventDTO eventDTO = (EventDTO) msg.obj;
                        try {
                            JSONObject message = prepareEventObject(eventDTO);
                            logAboutMessageToMixpanel("Queuing event for sending later");
                            logAboutMessageToMixpanel("    " + message.toString());
                            queueDepth = mDbAdapter.addJSON(message, MPDbAdapter.Table.EVENTS);
                        } catch (JSONException e) {
                            Log.e(LOGTAG, "Exception tracking event " + eventDTO.getEventName(), e);
                        }
                    }
                    else if (msg.what == FLUSH_QUEUE) {
                        logAboutMessageToMixpanel("Flushing queue due to scheduled or forced flush");
                        updateFlushFrequency();
                        sendAllData(mDbAdapter);
                    }
                    else if (msg.what == CHECK_FOR_SURVEYS) {
                        logAboutMessageToMixpanel("Flushing before checking surveys");
                        updateFlushFrequency();
                        sendAllData(mDbAdapter);
                        logAboutMessageToMixpanel("Checking Mixpanel for available surveys");
                        SurveyCheck check = (SurveyCheck) msg.obj;
                        runSurveyCheck(check);
                    }
                    else if (msg.what == KILL_WORKER) {
                        Log.w(LOGTAG, "Worker recieved a hard kill. Dumping all events and force-killing. Thread id " + Thread.currentThread().getId());
                        synchronized(mHandlerLock) {
                            mDbAdapter.deleteDB();
                            mHandler = null;
                            Looper.myLooper().quit();
                        }
                    } else {
                        Log.e(LOGTAG, "Unexpected message recieved by Mixpanel worker: " + msg);
                    }

                    ///////////////////////////

                    if (queueDepth >= mConfig.getBulkUploadLimit()) {
                        logAboutMessageToMixpanel("Flushing queue due to bulk upload limit");
                        updateFlushFrequency();
                        sendAllData(mDbAdapter);
                    }
                    else if(queueDepth > 0) {
                        if (!hasMessages(FLUSH_QUEUE)) {
                            // The hasMessages check is a courtesy for the common case
                            // of delayed flushes already enqueued from inside of this thread.
                            // Callers outside of this thread can still send
                            // a flush right here, so we may end up with two flushes
                            // in our queue, but we're ok with that.

                            logAboutMessageToMixpanel("Queue depth " + queueDepth + " - Adding flush in " + mFlushInterval);
                            if (mFlushInterval >= 0) {
                                    sendEmptyMessageDelayed(FLUSH_QUEUE, mFlushInterval);
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    Log.e(LOGTAG, "Worker threw an unhandled exception- will not send any more mixpanel messages", e);
                    synchronized (mHandlerLock) {
                        mHandler = null;
                        try {
                            Looper.myLooper().quit();
                        } catch (Exception tooLate) {
                            Log.e(LOGTAG, "Could not halt looper", tooLate);
                        }
                    }
                    throw e;
                }
            }// handleMessage

            private void runSurveyCheck(SurveyCheck check) {
                // TODO break up requesting surveys, checking list, and submitting foundSurvey job
                final SurveyCallbacks callbacks = check.getCallbacks();
                String escapedToken;
                String escapedId;
                try {
                    escapedToken = URLEncoder.encode(check.getToken(), "utf-8");
                    escapedId = URLEncoder.encode(check.getDistinctId(), "utf-8");
                } catch(UnsupportedEncodingException e) {
                    throw new RuntimeException("Mixpanel library requires utf-8 string encoding to be available", e);
                }
                final String checkQuery = new StringBuilder()
                    .append("?version=1&lib=android&token=")
                    .append(escapedToken)
                    .append("&distinct_id=")
                    .append(escapedId)
                    .toString();
                final String endpointUrl = mConfig.getDecideEndpoint() + checkQuery;
                final String fallbackUrl = mConfig.getDecideFallbackEndpoint() + checkQuery;
                final ServerMessage poster = getPoster();
                final ServerMessage.Result result = poster.get(endpointUrl, fallbackUrl);
                if (result.getStatus() != ServerMessage.Status.SUCCEEDED) {
                    Log.e(LOGTAG, "Couldn't reach Mixpanel to check for Surveys.");
                    return;
                }
                final String response = result.getResponse();
                JSONArray surveys = null;
                try {
                    final JSONObject parsed = new JSONObject(response);
                    surveys = parsed.getJSONArray("surveys");
                } catch (JSONException e) {
                    Log.e(LOGTAG, "Mixpanel endpoint returned invalid JSON " + response);
                    return;
                }
                Survey found = null;
                for (int i = 0; found == null && i < surveys.length(); i++) {
                    try {
                        final JSONObject candidateJson = surveys.getJSONObject(i);
                        final Survey candidate = new Survey(candidateJson); // Can throw a JSON error
                        if (mSeenSurveys.contains(candidate.getId())) {
                            if (MPConfig.DEBUG) {
                                Log.i(LOGTAG, "Recieved a duplicate survey from Mixpanel, ignoring.");
                            }
                        } else {
                            found = candidate;
                            // mSeenSurveys.add(found.getId()); TODO UNCOMMENT BEFORE MERGE
                        }
                    } catch (JSONException e) {
                        found = null;
                    }
                }

                final Survey toReport = found;
                final Looper mainLooper = Looper.getMainLooper();
                if (mainLooper != null) {
                    new Handler(mainLooper).post(new Runnable() {
                        @Override
                        public void run() {
                            callbacks.foundSurvey(toReport);
                        }
                    });
                } else {
                    // No main looper, we just run it here
                    callbacks.foundSurvey(toReport);
                }
            }// runSurveyCheck

            public boolean isOnline() {
                boolean isOnline;
                try {
                    ConnectivityManager cm =
                            (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo netInfo = cm.getActiveNetworkInfo();
                    isOnline = netInfo != null && netInfo.isConnectedOrConnecting();
                    if (MPConfig.DEBUG) Log.d(LOGTAG, "ConnectivityManager says we " + (isOnline ? "are" : "are not") + " online");
                } catch (SecurityException e) {
                    isOnline = true;
                    if (MPConfig.DEBUG) Log.d(LOGTAG, "Don't have permission to check connectivity so returning true");
                }
                return isOnline;
            }

            private void sendAllData(MPDbAdapter dbAdapter) {
                if (isOnline()) {
                    logAboutMessageToMixpanel("Sending records to Mixpanel");
                    if (mDisableFallback) {
                        sendData(dbAdapter, MPDbAdapter.Table.EVENTS, mConfig.getEventsEndpoint(), null);
                        sendData(dbAdapter, MPDbAdapter.Table.PEOPLE, mConfig.getPeopleEndpoint(), null);
                    } else {
                        sendData(dbAdapter, MPDbAdapter.Table.EVENTS, mConfig.getEventsEndpoint(), mConfig.getEventsFallbackEndpoint());
                        sendData(dbAdapter, MPDbAdapter.Table.PEOPLE, mConfig.getPeopleEndpoint(), mConfig.getPeopleFallbackEndpoint());
                    }
                } else {
                    logAboutMessageToMixpanel("Can't send data to mixpanel, because the device is not connected to the internet");
                }
            }

            private void sendData(MPDbAdapter dbAdapter, MPDbAdapter.Table table, String endpointUrl, String fallbackUrl) {
                String[] eventsData = dbAdapter.generateDataString(table);

                if (eventsData != null) {
                    String lastId = eventsData[0];
                    String rawMessage = eventsData[1];
                    ServerMessage poster = getPoster();
                    ServerMessage.Result eventsPosted = poster.postData(rawMessage, endpointUrl, fallbackUrl);
                    ServerMessage.Status postStatus = eventsPosted.getStatus();

                    if (postStatus == ServerMessage.Status.SUCCEEDED) {
                        logAboutMessageToMixpanel("Posted to " + endpointUrl);
                        logAboutMessageToMixpanel("Sent Message\n" + rawMessage);
                        dbAdapter.cleanupEvents(lastId, table);
                    }
                    else if (postStatus == ServerMessage.Status.FAILED_RECOVERABLE) {
                        // Try again later
                        if (!hasMessages(FLUSH_QUEUE)) {
                            sendEmptyMessageDelayed(FLUSH_QUEUE, mFlushInterval);
                        }
                    }
                    else { // give up, we have an unrecoverable failure.
                        dbAdapter.cleanupEvents(lastId, table);
                    }
                }
            }

            private JSONObject getDefaultEventProperties()
                    throws JSONException {
                JSONObject ret = new JSONObject();

                ret.put("mp_lib", "android");
                ret.put("$lib_version", MPConfig.VERSION);

                // For querying together with data from other libraries
                ret.put("$os", "Android");
                ret.put("$os_version", Build.VERSION.RELEASE == null ? "UNKNOWN" : Build.VERSION.RELEASE);

                ret.put("$manufacturer", Build.MANUFACTURER == null ? "UNKNOWN" : Build.MANUFACTURER);
                ret.put("$brand", Build.BRAND == null ? "UNKNOWN" : Build.BRAND);
                ret.put("$model", Build.MODEL == null ? "UNKNOWN" : Build.MODEL);

                DisplayMetrics displayMetrics = mSystemInformation.getDisplayMetrics();
                ret.put("$screen_dpi", displayMetrics.densityDpi);
                ret.put("$screen_height", displayMetrics.heightPixels);
                ret.put("$screen_width", displayMetrics.widthPixels);

                String applicationVersionName = mSystemInformation.getAppVersionName();
                if (null != applicationVersionName)
                    ret.put("$app_version", applicationVersionName);

                Boolean hasNFC = mSystemInformation.hasNFC();
                if (null != hasNFC)
                    ret.put("$has_nfc", hasNFC.booleanValue());

                Boolean hasTelephony = mSystemInformation.hasTelephony();
                if (null != hasTelephony)
                    ret.put("$has_telephone", hasTelephony.booleanValue());

                String carrier = mSystemInformation.getCurrentNetworkOperator();
                if (null != carrier)
                    ret.put("$carrier", carrier);

                Boolean isWifi = mSystemInformation.isWifiConnected();
                if (null != isWifi)
                    ret.put("$wifi", isWifi.booleanValue());

                Boolean isBluetoothEnabled = mSystemInformation.isBluetoothEnabled();
                if (isBluetoothEnabled != null)
                    ret.put("$bluetooth_enabled", isBluetoothEnabled);

                String bluetoothVersion = mSystemInformation.getBluetoothVersion();
                if (bluetoothVersion != null)
                    ret.put("$bluetooth_version", bluetoothVersion);

                return ret;
            }

            private JSONObject prepareEventObject(EventDTO eventDTO) throws JSONException {
                JSONObject eventObj = new JSONObject();
                JSONObject properties = eventDTO.getProperties();
                JSONObject propertiesObj = getDefaultEventProperties();
                propertiesObj.put("token", eventDTO.getToken());
                if (properties != null) {
                    for (Iterator<?> iter = properties.keys(); iter.hasNext();) {
                        String key = (String) iter.next();
                        propertiesObj.put(key, properties.get(key));
                    }
                }
                eventObj.put("event", eventDTO.getEventName());
                eventObj.put("properties", propertiesObj);
                return eventObj;
            }

            private MPDbAdapter mDbAdapter;
            private final Set<Integer> mSeenSurveys;
            private long mFlushInterval; // XXX remove when associated deprecated APIs are removed
            private boolean mDisableFallback; // XXX remove when associated deprecated APIs are removed
        }// AnalyticsMessageHandler

        private void updateFlushFrequency() {
            long now = System.currentTimeMillis();
            long newFlushCount = mFlushCount + 1;

            if (mLastFlushTime > 0) {
                long flushInterval = now - mLastFlushTime;
                long totalFlushTime = flushInterval + (mAveFlushFrequency * mFlushCount);
                mAveFlushFrequency = totalFlushTime / newFlushCount;

                long seconds = mAveFlushFrequency / 1000;
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
    private final AtomicBoolean mLogMixpanelMessages;
    private final Worker mWorker;
    private final Context mContext;
    private final MPConfig mConfig;

    // Messages for our thread
    private static int ENQUEUE_PEOPLE = 0; // submit events and people data
    private static int ENQUEUE_EVENTS = 1; // push given JSON message to people DB
    private static int FLUSH_QUEUE = 2; // push given JSON message to events DB
    private static int KILL_WORKER = 5; // Hard-kill the worker thread, discarding all events on the eve
    private static int CHECK_FOR_SURVEYS = 11; // Poll the Mixpanel decide API for surveys

    private static int SET_FLUSH_INTERVAL = 4; // XXX REMOVE when associated deprecated APIs are removed
    private static int SET_DISABLE_FALLBACK = 10; // XXX REMOVE when associated deprecated APIs are removed

    private static final String LOGTAG = "MixpanelAPI";

    private static final Map<Context, AnalyticsMessages> sInstances = new HashMap<Context, AnalyticsMessages>();

}
