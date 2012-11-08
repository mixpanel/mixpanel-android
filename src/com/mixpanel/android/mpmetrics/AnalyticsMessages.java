package com.mixpanel.android.mpmetrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.SynchronousQueue;

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
        mLogMixpanelMessages = false;
        mWorker = new Worker(context);
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
            AnalyticsMessages ret;
            if (! sInstances.containsKey(messageContext)) {
                ret = new AnalyticsMessages(messageContext);
                sInstances.put(messageContext, ret);
            }
            else {
                ret = sInstances.get(messageContext);
            }
            return ret;
        }
    }

    public void logPosts() {
        mLogMixpanelMessages = true;
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

    public void setFlushInterval(long milliseconds) {
        Message m = Message.obtain();
        m.what = SET_FLUSH_INTERVAL;
        m.obj = new Long(milliseconds);

        mWorker.runMessage(m);
    }

    public void hardKill() {
        Message m = Message.obtain();
        m.what = KILL_WORKER;

        mWorker.runMessage(m);
    }

    /////////////////////////////////////////////////////////

    // For testing, to allow for Mocking.
    protected MPDbAdapter makeDbAdapter(Context context) {
        return new MPDbAdapter(context);
    }

    protected HttpPoster getPoster() {
        return new HttpPoster();
    }

    ////////////////////////////////////////////////////

    // Sends a message if and only if we are running with Mixpanel Message log enabled.
    // Will be called from the Mixpanel thread.
    private void logAboutMessageToMixpanel(String message) {
        if (mLogMixpanelMessages || MPConfig.DEBUG) {
            Log.i(LOGTAG, message);
        }
    }


    // Worker will manage the (at most single) IO thread associated with
    // this AnalyticsMessages instance.
    private class Worker {
        public Worker(Context context) {
            mDbAdapter = makeDbAdapter(context);
            mDbAdapter.cleanupEvents(System.currentTimeMillis() - MPConfig.DATA_EXPIRATION, MPDbAdapter.EVENTS_TABLE);
            mDbAdapter.cleanupEvents(System.currentTimeMillis() - MPConfig.DATA_EXPIRATION, MPDbAdapter.PEOPLE_TABLE);

            mHandler = null;
        }

        public void runMessage(Message msg) {
            synchronized(mHandlerLock) {
                if (null == mHandler) { // Worker is dead, get another.
                    logAboutMessageToMixpanel("Starting new worker thread");
                    mHandler = restartWorkerThread();
                }

                mHandler.sendMessage(msg);
            }
        }

        // NOTE that the returned worker will not be set with a timeout-
        // Timeouts will be scheduled after the first post is run (so if you
        // grab a handler and don't post anything to it, you'll spawn a
        // zombie thread that never dies.)
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
                    Looper.loop();
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
            @Override
            public void handleMessage(Message msg) {
                int queueDepth = -1;

                if (msg.what == SET_FLUSH_INTERVAL) {
                    Long newIntervalObj = (Long) msg.obj;
                    logAboutMessageToMixpanel("Changing flush interval to " + newIntervalObj);
                    mFlushInterval = newIntervalObj.longValue();
                    removeMessages(FLUSH_QUEUE);
                }
                else if (msg.what == ENQUEUE_PEOPLE) {
                    JSONObject message = (JSONObject) msg.obj;

                    logAboutMessageToMixpanel("Queuing people record for sending later");
                    logAboutMessageToMixpanel("    " + message.toString());

                    queueDepth = mDbAdapter.addJSON(message, MPDbAdapter.PEOPLE_TABLE);
                }
                else if (msg.what == ENQUEUE_EVENTS) {
                    JSONObject message = (JSONObject) msg.obj;

                    logAboutMessageToMixpanel("Queuing event for sending later");
                    logAboutMessageToMixpanel("    " + message.toString());

                    queueDepth = mDbAdapter.addJSON(message, MPDbAdapter.EVENTS_TABLE);
                }

                ///////////////////////////
                logAboutMessageToMixpanel("Queue depth " + queueDepth);

                if (queueDepth >= MPConfig.BULK_UPLOAD_LIMIT) {
                    logAboutMessageToMixpanel("Flushing queue due to bulk upload limit");
                    updateFlushFrequency();
                    sendAllData();
                }
                else if (msg.what == FLUSH_QUEUE) {
                    logAboutMessageToMixpanel("Flushing queue due to scheduled or forced flush");
                    updateFlushFrequency();
                    sendAllData();
                }
                else if(queueDepth > 0) {
                    if (!hasMessages(FLUSH_QUEUE)) {
                        // The hasMessages check is a courtesy for the common case
                        // of delayed flushes already enqueued from inside of this thread.
                        // Callers outside of this thread can still send
                        // a flush right here, so we may end up with two flushes
                        // in our queue, but we're ok with that.
                        sendEmptyMessageDelayed(FLUSH_QUEUE, mFlushInterval);
                    }
                }

                ///////////////////////

                if (msg.what == TIMEOUT) {
                    synchronized(mHandlerLock) {
                        // While this block is running, we can be sure
                        // that no new messages are being put on our queue.

                        if (hasMessages(ENQUEUE_PEOPLE) ||
                            hasMessages(ENQUEUE_EVENTS) ||
                            hasMessages(FLUSH_QUEUE)) {
                            // Don't timeout if we still have messages to process
                        }
                        else {
                            mHandler = null;
                            Looper.myLooper().quit();
                            logAboutMessageToMixpanel("Analytics messages worker dying of idleness. Thread id " + Thread.currentThread().getId());
                        }
                    }// synchronized
                }

                if (msg.what == KILL_WORKER) {
                    Log.w(LOGTAG, "Worker recieved a hard kill. Dumping all events and force-killing. Thread id " + Thread.currentThread().getId());
                    synchronized(mHandlerLock) {
                        mDbAdapter.deleteAllEvents(MPDbAdapter.EVENTS_TABLE);
                        mDbAdapter.deleteAllEvents(MPDbAdapter.PEOPLE_TABLE);
                        mHandler = null;
                        Looper.myLooper().quit();
                    }
                }

                if (mHandler != null) {
                    removeMessages(TIMEOUT);
                    sendEmptyMessageDelayed(TIMEOUT, MPConfig.SUBMIT_THREAD_TTL);
                }
            }// handleMessage

            private void sendAllData() {
                logAboutMessageToMixpanel("Sending records to Mixpanel");

                sendData(MPDbAdapter.EVENTS_TABLE, MPConfig.BASE_ENDPOINT + "/track?ip=1");
                sendData(MPDbAdapter.PEOPLE_TABLE, MPConfig.BASE_ENDPOINT + "/engage");
            }

            private void sendData(String table, String endpointUrl) {
                String[] eventsData = mDbAdapter.generateDataString(table);

                if (eventsData != null) {
                    String lastId = eventsData[0];
                    String rawMessage = eventsData[1];
                    HttpPoster.PostResult eventsPosted = getPoster().postData(rawMessage, endpointUrl);

                    if (eventsPosted == HttpPoster.PostResult.SUCCEEDED) {
                        logAboutMessageToMixpanel("Posted to " + endpointUrl);
                        logAboutMessageToMixpanel("Sent Message\n" + rawMessage);
                        mDbAdapter.cleanupEvents(lastId, table);
                    }
                    else if (eventsPosted == HttpPoster.PostResult.FAILED_RECOVERABLE) {
                        // Try again later
                        if (!hasMessages(FLUSH_QUEUE)) {
                            sendEmptyMessageDelayed(FLUSH_QUEUE, mFlushInterval);
                        }
                    }
                    else { // give up, we have an unrecoverable failure.
                        mDbAdapter.cleanupEvents(lastId, table);
                    }
                }
            }
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
        private final MPDbAdapter mDbAdapter; // Should only be used by the Handler
        private Handler mHandler;

        private long mFlushInterval = MPConfig.FLUSH_RATE;
        private long mFlushCount = 0;
        private long mAveFlushFrequency = 0;
        private long mLastFlushTime = -1;
    }

    /////////////////////////////////////////////////////////

    private volatile boolean mLogMixpanelMessages; // Used across thread boundaries
    private final Worker mWorker;

    // Messages for our thread
    private static int ENQUEUE_PEOPLE = 0; // submit events and people data
    private static int ENQUEUE_EVENTS = 1; // push given JSON message to people DB
    private static int FLUSH_QUEUE = 2; // push given JSON message to events DB
    private static int TIMEOUT = 3; // Kill this looper if there is nothing left to do
    private static int SET_FLUSH_INTERVAL = 4; // Reset frequency of flush interval
    private static int KILL_WORKER = 5; // Hard-kill the worker thread, discarding all events on the event queue.

    private static final String LOGTAG = "MixpanelAPI";

    private static final Map<Context, AnalyticsMessages> sInstances = new HashMap<Context, AnalyticsMessages>();
}
