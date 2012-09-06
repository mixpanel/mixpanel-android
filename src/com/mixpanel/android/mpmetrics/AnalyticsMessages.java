package com.mixpanel.android.mpmetrics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.mixpanel.android.util.Base64Coder;
import com.mixpanel.android.util.StringUtils;

/**
 * Manage communication of events with the internal database and the Mixpanel servers.
 *
 * This class straddles the thread boundary between user threads and
 * a logical Mixpanel thread (a Threadpool with a zero or one Threads in it)
 *
 * Because we want the actual Mixpanel thread to die and restart as work comes and
 * goes, we can't use the more natural Looper/Handler pattern.
 */
/* package */ class AnalyticsMessages {

    /**
     * Do not call directly. You should call AnalyticsMessages.getInstance()
     */
    /* package */ AnalyticsMessages(Context context) {
        mLogMixpanelMessages = false;
        mDbAdapter = makeDbAdapter(context);

        mDbAdapter.cleanupEvents(System.currentTimeMillis() - MPConfig.DATA_EXPIRATION, MPDbAdapter.EVENTS_TABLE);
        mDbAdapter.cleanupEvents(System.currentTimeMillis() - MPConfig.DATA_EXPIRATION, MPDbAdapter.PEOPLE_TABLE);

        mFlushTimer = makeFlushTimer();
    }

    /**
     * Use this to get an instance of AnalyticsMessages instead of creating one directly
     * for yourself. ONLY call this in the main UI thread or a thread with an existing
     * Looper. (it may register Handlers with the current Looper)
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

    // Public methods of this object should only be called
    // from the main UI thread.
    public void eventsMessage(JSONObject eventsJson) {
        sExecutor.submit(new EventsQueueTask(eventsJson));
    }

    public void peopleMessage(JSONObject peopleJson) {
        sExecutor.submit(new PeopleQueueTask(peopleJson));
    }

    public void submitPeople() {
        sExecutor.submit(new SubmitTask(FLUSH_PEOPLE));
    }

    public void submitEvents() {
        sExecutor.submit(new SubmitTask(FLUSH_EVENTS));
    }

    public void enableLogAboutMessagesToMixpanel(boolean enable) {
        mLogMixpanelMessages = enable;
    }

    /////////////////////////////////////////////////////////

    protected MPDbAdapter makeDbAdapter(Context context) {
        return  new MPDbAdapter(context);
    }

    protected FlushTimer makeFlushTimer() {
        return new FlushTimer();
    }

    /////////////////////////////////////////////////////////

    // Sends a message if and only if we are running with Mixpanel Message log enabled.
    // Will be called from the Mixpanel thread.
    private void logAboutMessageToMixpanel(String message) {
        if (MPConfig.DEBUG || mLogMixpanelMessages) {
            Log.i(LOGTAG, message);
        }
    }

    /**
     * To be posted to by the Mixpanel thread and constructed
     * in the calling UI thread.
     */
    /* package */ class FlushTimer {

        private class FlushHandler extends Handler {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == ENQUEUE_OPERATION) {
                    Message debounced = (Message) msg.obj;
                    if ((debounced.what == FLUSH_PEOPLE) && (! mEnqueuingPeople)) {
                        if (MPConfig.DEBUG) Log.d(LOGTAG, "ENQUING people flush for later execution");

                        mEnqueuingPeople = true;
                        sendMessageDelayed(debounced, MPConfig.FLUSH_RATE);
                    }
                    else if ((debounced.what == FLUSH_EVENTS) && (! mEnqueuingEvents)) {
                        if (MPConfig.DEBUG) Log.d(LOGTAG, "ENQUING events flush for later execution");
                        mEnqueuingEvents = true;
                        sendMessageDelayed(debounced, MPConfig.FLUSH_RATE);
                    }
                    else {
                        if (MPConfig.DEBUG) Log.d(LOGTAG, "Ignoring delayed flush call, debouncing.");
                    }
                }
                else if (msg.what == FLUSH_PEOPLE) {
                    if (MPConfig.DEBUG) Log.d(LOGTAG, "Executing people flush");

                    mEnqueuingPeople = false;
                    submitPeople();
                }
                else if (msg.what == FLUSH_EVENTS) {
                    if (MPConfig.DEBUG) Log.d(LOGTAG, "Executing events flush");

                    mEnqueuingEvents = false;
                    submitEvents();
                }
            }

            private boolean mEnqueuingPeople = false;
            private boolean mEnqueuingEvents = false;
        }

        public FlushTimer() {
            mHandler = new FlushHandler();
        }

        public void sendImmediateFlush(int flushType) {
            mHandler.sendEmptyMessage(flushType);
        }

        // Will be called by the Mixpanel thread
        public void sendDebouncedFlush(int flushType) {
            Message inner = Message.obtain();
            inner.what = flushType;

            Message outer = Message.obtain();
            outer.what = ENQUEUE_OPERATION;
            outer.obj = inner;

            mHandler.sendMessage(outer);
        }

        private final Handler mHandler;
        private final int ENQUEUE_OPERATION = 100; // Must be distinct from FLUSH_PEOPLE and FLUSH_EVENTS
    }

    /////////////////////////////////////////////////////////

    private class EventsQueueTask implements Runnable {
        private final JSONObject mDataObject;

        public EventsQueueTask(JSONObject dataObject) {
            mDataObject = dataObject;
        }

        @Override
        public void run() {
            logAboutMessageToMixpanel("Queuing event for sending later");
            logAboutMessageToMixpanel("    " + mDataObject.toString());

            int count = mDbAdapter.addJSON(mDataObject, MPDbAdapter.EVENTS_TABLE);
            if (MPConfig.TEST_MODE || count >= MPConfig.BULK_UPLOAD_LIMIT) {
                logAboutMessageToMixpanel("Events Queue is full, requesting a send (" + count + " messages in queue)");
                mFlushTimer.sendImmediateFlush(FLUSH_EVENTS);
            } else {
                mFlushTimer.sendDebouncedFlush(FLUSH_EVENTS);
            }
        }
    }

    private class PeopleQueueTask implements Runnable {
        private final JSONObject mDataObject;

        public PeopleQueueTask(JSONObject dataObject) {
            mDataObject = dataObject;
        }

        @Override
        public void run() {
            logAboutMessageToMixpanel("Queuing people record for sending later");
            logAboutMessageToMixpanel("    " + mDataObject.toString());

            int count = mDbAdapter.addJSON(mDataObject, MPDbAdapter.PEOPLE_TABLE);
            if (MPConfig.TEST_MODE || count >= MPConfig.BULK_UPLOAD_LIMIT) {
                logAboutMessageToMixpanel("People record queue is full, requesting a send");
                mFlushTimer.sendImmediateFlush(FLUSH_PEOPLE);
            } else {
                mFlushTimer.sendDebouncedFlush(FLUSH_PEOPLE);
            }
        }
    }

    private class SubmitTask implements Runnable {
        private final String table;
        private final int messageType;

        public SubmitTask(int messageType) {
            this.messageType = messageType;
            if (messageType == FLUSH_PEOPLE) {
                table = MPDbAdapter.PEOPLE_TABLE;
            } else {
                table = MPDbAdapter.EVENTS_TABLE;
            }
        }

        @Override
        public void run() {
            if (messageType == FLUSH_PEOPLE) {
                logAboutMessageToMixpanel("Submitting people records to Mixpanel");
            }
            else {
                logAboutMessageToMixpanel("Submitting events to Mixpanel");
            }

            String[] data = mDbAdapter.generateDataString(table);
            if (data == null) {
                logAboutMessageToMixpanel("    No data to submit.");
                return;
            }

            String lastId = data[0];
            String rawMessage = data[1];
            String encodedData = Base64Coder.encodeString(rawMessage);

            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost;
            if (messageType == FLUSH_PEOPLE) {
                httppost = new HttpPost(MPConfig.BASE_ENDPOINT + "/engage");
            } else {
                httppost = new HttpPost(MPConfig.BASE_ENDPOINT + "/track?ip=1");
            }

            try {
                List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
                nameValuePairs.add(new BasicNameValuePair("data", encodedData));
                httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

                HttpResponse response = httpclient.execute(httppost);
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    mFlushTimer.sendDebouncedFlush(messageType);
                    return;
                }

                try {
                    String result = StringUtils.inputStreamToString(entity.getContent());
                    if (MPConfig.DEBUG) {
                        Log.d(LOGTAG, "HttpResponse result: " + result);
                    }
                    if (!result.equals("1\n")) {
                        mFlushTimer.sendDebouncedFlush(messageType);
                        return;
                    }
                    logAboutMessageToMixpanel("Sent Message\n" + rawMessage);

                    // Success, so prune the database.
                    mDbAdapter.cleanupEvents(lastId, table);

                } catch (IOException e) {
                    Log.e(LOGTAG, "SubmitTask " + table, e);
                    mFlushTimer.sendDebouncedFlush(messageType);
                    return;
                } catch (OutOfMemoryError e) { //?
                    Log.e(LOGTAG, "SubmitTask " + table, e);
                    mFlushTimer.sendDebouncedFlush(messageType);
                    return;
                }
            // Any exceptions, log them and stop this task.
            } catch (ClientProtocolException e) {
                Log.e(LOGTAG, "SubmitTask " + table, e);
                mFlushTimer.sendDebouncedFlush(messageType);
                return;
            } catch (IOException e) {
                Log.e(LOGTAG, "SubmitTask " + table, e);
                mFlushTimer.sendDebouncedFlush(messageType);
                return;
            }
        }
    }

    private volatile boolean mLogMixpanelMessages; // Used across thread boundaries

    private final MPDbAdapter mDbAdapter;
    private final FlushTimer mFlushTimer;

    private static int FLUSH_EVENTS = 0;
    private static int FLUSH_PEOPLE = 1;
    private static final String LOGTAG = "MixpanelAPI";

    private static final Map<Context, AnalyticsMessages> sInstances = new HashMap<Context, AnalyticsMessages>();

    // Creates a single thread pool to perform the HTTP requests and insert events into sqlite
    // The running thread will always be run at MIN_PRIORITY
    // You should NOT increase the max size of this pool without some serious thinking,
    // since the design above may assume that no submitted tasks are run concurrently.
    private static ThreadPoolExecutor sExecutor =
            new ThreadPoolExecutor(0, 1, MPConfig.SUBMIT_THREAD_TTL, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), new LowPriorityThreadFactory());

    static {
        sExecutor.setKeepAliveTime(MPConfig.SUBMIT_THREAD_TTL, TimeUnit.MILLISECONDS);
    }
}
