package com.mixpanel.android.mpmetrics;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;


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

    public void eventsMessage(JSONObject eventsJson) {
        Message m = Message.obtain();
        m.what = ENQUEUE_EVENTS;
        m.obj = eventsJson;

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
                mDisableFallback = mConfig.getDisableFallback();
                mFlushInterval = mConfig.getFlushInterval();
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

                    if (msg.what == SET_FLUSH_INTERVAL) { // TODO remove when associated deprecated api is removed
                        final Long newIntervalObj = (Long) msg.obj;
                        logAboutMessageToMixpanel("Changing flush interval to " + newIntervalObj);
                        mFlushInterval = newIntervalObj.longValue();
                        removeMessages(FLUSH_QUEUE);
                    }
                    else if (msg.what == SET_DISABLE_FALLBACK) { // TODO remove when assoicated deprecated api is removed
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
                        JSONObject message = (JSONObject) msg.obj;

                        logAboutMessageToMixpanel("Queuing event for sending later");
                        logAboutMessageToMixpanel("    " + message.toString());

                        queueDepth = mDbAdapter.addJSON(message, MPDbAdapter.Table.EVENTS);
                    }
                    else if (msg.what == FLUSH_QUEUE) {
                        logAboutMessageToMixpanel("Flushing queue due to scheduled or forced flush");
                        updateFlushFrequency();
                        sendAllData(mDbAdapter);
                    }
                    else if (msg.what == CHECK_FOR_SURVEYS) {
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
                final ServerMessage poster = getPoster();
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
                ServerMessage.Result result = poster.get(endpointUrl, fallbackUrl);

                if (result.getStatus() == ServerMessage.Status.SUCCEEDED) {
                    final SurveyCallbacks callbacks = check.getCallbacks();
                    final String response = result.getResponse();
                    if (callbacks != null) {
                        try {
                            JSONObject parsed = new JSONObject(response);
                            JSONArray surveys = parsed.getJSONArray("surveys");
                            if (surveys.length() == 0) {
                                callbacks.foundSurvey(null);
                            } else {
                                JSONObject firstSurveyJson = surveys.getJSONObject(0);
                                Survey firstSurvey = new Survey(firstSurveyJson); // Can throw a JSON error
                                // TODO you need to check if you've already seen the survey
                                // and Mixpanel just hasn't figured it out yet.
                                callbacks.foundSurvey(firstSurvey);
                            }
                        } catch (JSONException e) {
                            Log.e(LOGTAG, "Mixpanel endpoint returned invalid JSON " + response);
                        }
                    }
                }
            }// runSurveyCheck

            private void sendAllData(MPDbAdapter dbAdapter) {
                logAboutMessageToMixpanel("Sending records to Mixpanel");
                if (mDisableFallback) {
                    sendData(dbAdapter, MPDbAdapter.Table.EVENTS, mConfig.getEventsEndpoint(), null);
                    sendData(dbAdapter, MPDbAdapter.Table.PEOPLE, mConfig.getPeopleEndpoint(), null);
                } else {
                    sendData(dbAdapter, MPDbAdapter.Table.EVENTS, mConfig.getEventsEndpoint(), mConfig.getEventsFallbackEndpoint());
                    sendData(dbAdapter, MPDbAdapter.Table.PEOPLE, mConfig.getPeopleEndpoint(), mConfig.getPeopleFallbackEndpoint());
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

            private MPDbAdapter mDbAdapter;
            private long mFlushInterval; // TODO remove when associated deprecated APIs are removed
            private boolean mDisableFallback; // TODO remove when associated deprecated APIs are removed
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

    // TODO REMOVE when associated deprecated APIs are removed
    private static int SET_FLUSH_INTERVAL = 4; // Reset frequency of flush interval
    private static int SET_DISABLE_FALLBACK = 10; // Disable fallback to http

    private static final String LOGTAG = "MixpanelAPI";

    private static final Map<Context, AnalyticsMessages> sInstances = new HashMap<Context, AnalyticsMessages>();
}
