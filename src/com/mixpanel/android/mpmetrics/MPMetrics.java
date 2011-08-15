package com.mixpanel.android.mpmetrics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import com.mixpanel.android.util.StringUtils;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

public class MPMetrics {
    private static final String LOGTAG = "MPMetrics";

    private static final String API_URL = "http://api.mixpanel.com";
    private static final String ENDPOINT_TRACK = API_URL + "/track";

    private static final int BULK_UPLOAD_LIMIT = 40;
    private static final int FLUSH_RATE = 60 * 1000; // time, in milliseconds that the data should be flushed

    // Remove events that have sat around for this many milliseconds
    private static final int DATA_EXPIRATION = 1000 * 60 * 60 * 12; // 12 hours

    private Context mContext;

    private String mToken;

    private String mCarrier;
    private String mModel;
    private String mVersion;
    private String mDeviceId;

    private Map<String, String> mSuperProperties;

    private MPDbAdapter mDbAdapter;

    private boolean mTestMode;

    private Timer mTimer;

    // Creates a single thread pool to perform the HTTP requests on
    // Multiple requests may be queued up to prevent races.
    private final ExecutorService executor = Executors.newFixedThreadPool(1);

    public MPMetrics(Context context, String token) {
    	mContext = context;
        mToken = token;

        mCarrier = getCarrier();
        mModel = getModel();
        mVersion = getVersion();
        mDeviceId = getDeviceId();

        mDbAdapter = new MPDbAdapter(mContext);
        mDbAdapter.cleanupEvents(System.currentTimeMillis() - DATA_EXPIRATION);

        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                flush();
            }
        }, 0, FLUSH_RATE);
    }

    /**
     * Register super properties for events
     * @param superProperties    A map containing the key value pairs of the super properties to register
     * @param type  Indicates which types of events to apply the super properties. Must be SUPER_PROPERTY_TYPE_ALL,
     *              SUPER_PROPERTY_TYPE_EVENTS, or SUPER_PROPERTY_TYPE_FUNNELS
     */
    public void registerSuperProperties(Map<String, String> superProperties) {
        if (Global.DEBUG) Log.d(LOGTAG, "registerSuperProperties");

        if (mSuperProperties == null) {
        	mSuperProperties = superProperties;
        } else {
        	for (Map.Entry<String, String> entry: superProperties.entrySet()) {
                mSuperProperties.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Clear super properties
     */
    public void clearSuperProperties() {
        if (Global.DEBUG) Log.d(LOGTAG, "clearSuperProperties");
        mSuperProperties = null;
    }

    /**
     * Track an event.
     *
     * @param eventName The name of the event to send
     * @param properties A Map containing the key value pairs of the properties to include in this event.
     * Pass null if no extra properties exist.
     */
    public void event(String eventName, Map<String, String> properties) {
        String time = Long.toString(System.currentTimeMillis() / 1000);

        JSONObject dataObj = new JSONObject();
        try {
            dataObj.put("event", eventName);
            JSONObject propertiesObj = new JSONObject();
            propertiesObj.put("token", mToken);
            propertiesObj.put("time", time);
            propertiesObj.put("distinct_id", mDeviceId == null ? "UNKNOWN" : mDeviceId);
            propertiesObj.put("carrier", mCarrier == null ? "UNKNOWN" : mCarrier);
            propertiesObj.put("model",  mModel == null ? "UNKNOWN" : mModel);
            propertiesObj.put("version", mVersion == null ? "UNKNOWN" : mVersion);
            propertiesObj.put("mp_lib", "android");

            if (mSuperProperties != null) {
                for (Map.Entry<String, String> entry: mSuperProperties.entrySet()) {
                    propertiesObj.put(entry.getKey(), entry.getValue());
                }
            }

            if (properties != null) {
                for (Map.Entry<String, String> entry: properties.entrySet()) {
                    propertiesObj.put(entry.getKey(), entry.getValue());
                }
            }

            dataObj.put("properties", propertiesObj);
        } catch (JSONException e) {
        	Log.e(LOGTAG, "event", e);
            return;
        }

        int count = mDbAdapter.addEvent(dataObj);

        if (mTestMode || count >= BULK_UPLOAD_LIMIT) {
            flush();
        }
    }

    public void flush() {
    	if (Global.DEBUG) Log.d(LOGTAG, "flush");

    	executor.submit(new TrackTask());
    }

    /**
     * Enable test mode. Normally, calls to MPMetrics.event() and MPMetrics.funnel() are collected and sent out in
     * bulk to conserve resources. By enabling test mode, data will be sent out as soon as a call to
     * event() or funnel() is made.
     */
    public void enableTestMode() {
        if (Global.DEBUG) Log.d(LOGTAG, "enableTestMode");
        mTestMode = true;
    }

    /**
     * Return the carrier of the phone
     * @return   A String containing the carrier
     */
    private String getCarrier() {
        return Build.BRAND;
    }

    /**
     * Return the model of the phone
     * @return  A String containing the model
     */
    private String getModel() {
        return Build.MODEL;
    }

    /**
     * Return the Android version of the phone
     * @return  A String containing the android version
     */
    private String getVersion() {
        return Build.VERSION.RELEASE;
    }

    /**
     * Return the unique device identifier of the phone
     * @param context   the context
     * @return  A String containing the device's unique identifier
     */
    private String getDeviceId() {
        String product = Build.PRODUCT;
        String androidId = Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (product == null || androidId == null) {
            return null;
        } else {
            return product + "_" + androidId;
        }
    }

    private class TrackTask implements Runnable {
    	private static final String LOGTAG = "TrackTask";

		@Override
		public void run() {
			String data = mDbAdapter.generateDataString();
			if (data == null) {
				// Couldn't get data for whatever reason, so just return.
				return;
			}
			StringTokenizer tok = new StringTokenizer(data, ":");
			long timestamp = Long.parseLong(tok.nextToken());
			data = tok.nextToken();

	    	// Post the data
		    HttpClient httpclient = new DefaultHttpClient();
		    HttpPost httppost = new HttpPost(ENDPOINT_TRACK);

		    try {
		        // Add your data
		        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
		        nameValuePairs.add(new BasicNameValuePair("data", data));
		        httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

		        HttpResponse response = httpclient.execute(httppost);
		        HttpEntity entity = response.getEntity();
		        if (entity == null) {
		        	return;
		        }

		        try {
		        	String result = StringUtils.inputStreamToString(entity.getContent());
		        	if (Global.DEBUG) {
		        		Log.d(LOGTAG, "HttpResponse result: " + result);
		        	}

		        	// Success, so prune the database.
		        	mDbAdapter.cleanupEvents(timestamp);

		        // If anything went wrong, don't remove from the db so we can try again
		        // on the next flush.
		        } catch (IOException e) {
		        	Log.e(LOGTAG, "TrackTask", e);
			        return;
		        } catch (OutOfMemoryError e) {
		        	Log.e(LOGTAG, "TrackTask", e);
			        return;
		        }
		    // Any exceptions, log them and stop this task.
		    } catch (ClientProtocolException e) {
		    	Log.e(LOGTAG, "TrackTask", e);
		        return;
		    } catch (IOException e) {
		    	Log.e(LOGTAG, "TrackTask", e);
		        return;
		    }
		}
    }
}
