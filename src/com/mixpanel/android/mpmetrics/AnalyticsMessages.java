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

import com.mixpanel.android.util.StringUtils;

/**
 * This class straddles the thread boundary between user threads and
 * a logical Mixpanel thread (a Threadpool with a zero or one Threads in it)
 *
 * Because we want the actual Mixpanel thread to die and restart as work comes and
 * goes, we can't use the more natural Looper/Handler pattern.
 */
/* package */ class AnalyticsMessages {

    private AnalyticsMessages(Context context) {
        mDbAdapter = new MPDbAdapter(context);
        mDbAdapter.cleanupEvents(System.currentTimeMillis() - MPConfig.DATA_EXPIRATION, MPDbAdapter.EVENTS_TABLE);
        mDbAdapter.cleanupEvents(System.currentTimeMillis() - MPConfig.DATA_EXPIRATION, MPDbAdapter.PEOPLE_TABLE);

        mFlushTimer = new FlushTimer();
    }

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

    public void eventsMessage(JSONObject message) {
        sExecutor.submit(new EventsQueueTask(message));
    }

    public void peopleMessage(JSONObject message) {
        sExecutor.submit(new PeopleQueueTask(message));
    }

    public void submitPeople() {
        sExecutor.submit(new SubmitTask(FLUSH_PEOPLE));
    }

    public void submitEvents() {
        sExecutor.submit(new SubmitTask(FLUSH_EVENTS));
    }

    /////////////////////////////////////////////////////////

    /**
     * To be posted to by the Mixpanel thread and constructed
     * in the calling UI thread.
     */
    private class FlushTimer extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == ENQUEUE_OPERATION) {
                Message debounced = (Message) msg.obj;
                if ((debounced.what == FLUSH_PEOPLE) && (! mEnqueuingPeople)) {
                    mEnqueuingPeople = true;
                    sendMessageDelayed(debounced, MPConfig.FLUSH_RATE);
                }
                else if ((debounced.what == FLUSH_EVENTS) && (! mEnqueuingEvents)) {
                    mEnqueuingEvents = true;
                    sendMessageDelayed(debounced, MPConfig.FLUSH_RATE);
                }
            }
            else if (msg.what == FLUSH_PEOPLE) {
                mEnqueuingPeople = false;
                submitPeople();
            }
            else if (msg.what == FLUSH_EVENTS) {
                mEnqueuingEvents = true;
                submitEvents();
            }
        }

        // Will be called by the Mixpanel thread
        public void sendDebouncedFlush(int flushType) {
            Message inner = Message.obtain();
            inner.what = flushType;

            Message outer = Message.obtain();
            outer.what = ENQUEUE_OPERATION;
            outer.obj = inner;

            sendMessage(outer);
        }

        private final int ENQUEUE_OPERATION = 100; // Must be distinct from FLUSH_PEOPLE and FLUSH_EVENTS
        private boolean mEnqueuingPeople = false;
        private boolean mEnqueuingEvents = false;
    }

    /////////////////////////////////////////////////////////

    private class EventsQueueTask implements Runnable {
        private final JSONObject mDataObject;

        public EventsQueueTask(JSONObject dataObject) {
            mDataObject = dataObject;
        }

        @Override
        public void run() {
            if (MPConfig.DEBUG) {
                Log.d(LOGTAG, "EventsQueueTask queuing event");
                Log.d(LOGTAG, "    " + mDataObject.toString());
            }

            int count = mDbAdapter.addJSON(mDataObject, MPDbAdapter.EVENTS_TABLE);
            if (MPConfig.TEST_MODE || count >= MPConfig.BULK_UPLOAD_LIMIT) {
                if (MPConfig.DEBUG) Log.d(LOGTAG, "EventsQueueTask in test or count greater than MPConfig.BULK_UPLOAD_LIMIT");
                mFlushTimer.sendEmptyMessage(FLUSH_EVENTS);
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
            if (MPConfig.DEBUG) Log.d(LOGTAG, "PeopleQueueTask queuing an action");

            int count = mDbAdapter.addJSON(mDataObject, MPDbAdapter.PEOPLE_TABLE);
            if (MPConfig.TEST_MODE || count >= MPConfig.BULK_UPLOAD_LIMIT) {
                if (MPConfig.DEBUG) Log.d(LOGTAG, "PeopleQueueTask MPConfig.TEST_MODE set or count greater than MPConfig.BULK_UPLOAD_LIMIT");
                mFlushTimer.sendEmptyMessage(FLUSH_PEOPLE);
            } else {
                mFlushTimer.sendDebouncedFlush(FLUSH_PEOPLE);
            }
        }
    }

    private class SubmitTask implements Runnable {
        private String table;
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
            if (MPConfig.DEBUG) Log.d(LOGTAG, "SubmitTask " + this.table + " running.");

            String[] data = mDbAdapter.generateDataString(table);
            if (data == null) {
                return;
            }

            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost;
            if (this.table.equals(MPDbAdapter.PEOPLE_TABLE)) {
                httppost = new HttpPost(MPConfig.BASE_ENDPOINT + "/engage");
            } else {
                httppost = new HttpPost(MPConfig.BASE_ENDPOINT + "/track?ip=1");
            }

            try {
                List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
                nameValuePairs.add(new BasicNameValuePair("data", data[1]));
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

                    // Success, so prune the database.
                    mDbAdapter.cleanupEvents(data[0], table);

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

    private final MPDbAdapter mDbAdapter;
    private final FlushTimer mFlushTimer;

    private static int FLUSH_EVENTS = 0;
    private static int FLUSH_PEOPLE = 1;
    private static final String LOGTAG = "MPMetrics";

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
