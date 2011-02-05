package com.mixpanel.android.mpmetrics;

import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import org.apache.http.client.ResponseHandler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.mixpanel.android.dbadapter.MPDbAdapter;
import com.mixpanel.android.network.Base64Coder;
import com.mixpanel.android.network.HTTPRequestHelper;

import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;

public class MPMetrics {
    
    private static final String LOGTAG = "MPMetrics";
    
    public static final String SUPER_PROPERTY_TYPE_ALL = "all";
    public static final String SUPER_PROPERTY_TYPE_EVENTS = "events";
    public static final String SUPER_PROPERTY_TYPE_FUNNELS = "funnels";
    
    private static final String API_URL = "http://api.mixpanel.com";
    private static final String ENDPOINT_TRACK = API_URL + "/track";
    
    private static final int BULK_UPLOAD_LIMIT = 40;
    private static final int FLUSH_RATE = 60 * 1000; // time, in milliseconds that the data should be flushed 
    private static final int DATA_EXPIRATION = 12; // number of hours to try sending data before giving up

    
    private Handler mHandler;
    private Context mContext;
    private Timer mTimer;
    
    private String mToken;
    private boolean mTestMode;
    
    private Vector<Long> mDispatchedEvents;
    private JSONArray mEvents;
    private MPDbAdapter mDbAdapter;
    
    private Map<String, String> mSuperProperties;
    private String mSuperPropertiesType;
    
    private String mCarrier;
    private String mModel;
    private String mVersion;
    private String mDeviceId;
    
    public MPMetrics(Context context, String token) {
        mContext = context;
        mToken = token;
        
        mTestMode = false;
        
        mHandler = new Handler() {
            @Override
            public void handleMessage(final Message msg) {
                if (msg.getData().containsKey(HTTPRequestHelper.RESPONSE)) {
                    boolean success = msg.getData().getBoolean(HTTPRequestHelper.RESPONSE_SUCCESS);
                    String bundleResult = msg.getData().getString(HTTPRequestHelper.RESPONSE);
                    long responseId = msg.getData().getLong(HTTPRequestHelper.RESPONSE_ID);
                    parseResults(bundleResult, responseId, success);
                }
            }
        };
        
        GregorianCalendar gc = new GregorianCalendar();
        gc.add(GregorianCalendar.HOUR, -1 * DATA_EXPIRATION);
        
        try {
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        mDbAdapter = new MPDbAdapter(mContext);
        mDbAdapter.open();
        mDbAdapter.cleanupEvents(gc.getTime());
        mDbAdapter.close();
        
        mDispatchedEvents = new Vector<Long>();
       
        mEvents = new JSONArray();
        mSuperProperties = null;
        mSuperPropertiesType = SUPER_PROPERTY_TYPE_ALL;
        mCarrier = getCarrier();
        mModel = getModel();
        mVersion = getVersion();
        mDeviceId = getDeviceId();
        
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                flush();
            }
        }, 0, FLUSH_RATE);
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
    
   
    /**
     * Stores the event data in the database
     * @param data the base64 encoded data to store
     * @return  the rowId of the stored data, or -1 if failed
     */
    private long persistEventData(String data) {
        if (Global.DEBUG) Log.d(LOGTAG, "persistEventData");
        long id = -1;
        try {
            mDbAdapter.open();
            id = mDbAdapter.createEvents(data);
            mDbAdapter.close();
        } catch (Exception e) {
            
        }
        
        return id;
    }
    
    /**
     * Removes the event data from the database
     * @param id the id of the row that is to be removed
     * @return  true if removed successfully, false if otherwise
     */
    private boolean removeEventData(long id) {
        if (Global.DEBUG) Log.d(LOGTAG, "removeEventData");
        boolean results = false;
        try {
            mDbAdapter.open();
            results = mDbAdapter.deleteEvents(id);
            mDbAdapter.close();
        } catch (Exception e) {
            
        }
        return results;
    }
    
    
    /**
     * Sends a the payload to the endpoint 
     * @param url   Mixpanel API endpoint
     * @param data  the base64 encoded value for the "data" param
     */
    private void sendRequest(final String url) {
        if (Global.DEBUG) Log.d(LOGTAG, "sendRequest");
        try {
            mDbAdapter.open();
            Cursor cursor = mDbAdapter.fetchEvents();
            int idColumnIndex = cursor.getColumnIndexOrThrow(MPDbAdapter.KEY_ROWID);
            int dataColumnIndex = cursor.getColumnIndexOrThrow(MPDbAdapter.KEY_DATA);

            if (cursor.moveToFirst()) {
                do {
                    final long id = cursor.getLong(idColumnIndex);
                    final String data = cursor.getString(dataColumnIndex);
                    
                    if (!mDispatchedEvents.contains(id)) {
                        mDispatchedEvents.add(id);
                        
                        final ResponseHandler<String> responseHandler = 
                            HTTPRequestHelper.getResponseHandlerInstance(mHandler, id);
                        
                        new Thread() {
                            @Override
                            public void run() {
                                if (Global.DEBUG) Log.d(LOGTAG, "Sending data id: " + id);
                                Map<String, String> params = new HashMap<String, String>();
                                params.put("data", data);
                                HTTPRequestHelper helper = new HTTPRequestHelper(responseHandler, mHandler);
                                helper.performPost(url, null, null, null, params);
                                
                            }
                        }.start();
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
            mDbAdapter.close();
        } catch (Exception e) {
            
        }
        
        
        
    }
    
    /**
     * Force the collected event data to be sent to MixPanel's servers. This should be called before your activity or 
     * service is destroyed to ensure that any remaining events are sent.
     */
    public synchronized void flush() {
        if (Global.DEBUG) Log.d(LOGTAG, "flush");
        if (Global.DEBUG) {
            try {
                Log.d(LOGTAG, mEvents.toString(2));
            } catch (JSONException e) {
            }
        }
        
        String data = Base64Coder.encodeString(mEvents.toString());
        
        persistEventData(data);
        
        sendRequest(ENDPOINT_TRACK);
        
        mEvents = new JSONArray();
    }
    
    /**
     * Add an event to the bulk events container 
     * @param dataObj   the event to add
     */
    private synchronized void addEvent(JSONObject dataObj) {
        if (Global.DEBUG) Log.d(LOGTAG, "addEvent");
        mEvents.put(dataObj);
    }
    
    
    /**
     * Returns the number of events in the bulk events container 
     * @return  the number of events
     */
    private synchronized long getNumEvents() {
        if (Global.DEBUG) Log.d(LOGTAG, "getNumEvents");
        return mEvents.length();
    }
    
    /**
     * Register super properties for events  
     * @param superProperties    A map containing the key value pairs of the super properties to register
     * @param type  Indicates which types of events to apply the super properties. Must be SUPER_PROPERTY_TYPE_ALL, 
     *              SUPER_PROPERTY_TYPE_EVENTS, or SUPER_PROPERTY_TYPE_FUNNELS
     */
    public void registerSuperProperties(Map<String, String> superProperties, String type) {
        if (Global.DEBUG) Log.d(LOGTAG, "registerSuperProperties");
        mSuperProperties = superProperties;
        mSuperPropertiesType = type;
    }
    
    /**
     * Register super properties for all events  
     * @param superProperties    A Map containing the key value pairs of the super properties to register
     */
    public void registerSuperProperties(Map<String, String> superProperties) {
        if (Global.DEBUG) Log.d(LOGTAG, "registerSuperProperties");
        registerSuperProperties(superProperties, SUPER_PROPERTY_TYPE_ALL);
    }
    
    /**
     * Clear super properties  
     */
    public void clearSuperProperties() {
        if (Global.DEBUG) Log.d(LOGTAG, "clearSuperProperties");
        mSuperProperties = null;
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
     * Same as calling event(eventName, null)
     * @param eventName The name of the event to send
     */
    public void event(String eventName) {
        if (Global.DEBUG) Log.d(LOGTAG, "event");
        event(eventName, null);
    }
    
    /**
     * Logs event-based data to MixPanel servers. To conserve resources, a call to this method may not 
     * be sent immediately.   
     * @param eventName The name of the event to send
     * @param properties A Map containing the key value pairs of the properties to include in this event. 
     * Pass null if no extra properties exist 
     */
    
    public void event(String eventName, Map<String, String> properties) {
        if (Global.DEBUG) Log.d(LOGTAG, "event");
        
        
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
                if (mSuperPropertiesType == SUPER_PROPERTY_TYPE_ALL || mSuperPropertiesType == SUPER_PROPERTY_TYPE_EVENTS) {
                    for (Map.Entry<String, String> entry: mSuperProperties.entrySet()) {
                        propertiesObj.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            
            if (properties != null) {
                for (Map.Entry<String, String> entry: properties.entrySet()) {
                    propertiesObj.put(entry.getKey(), entry.getValue());
                }
            }
            
            
            dataObj.put("properties", propertiesObj);
            
            
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        addEvent(dataObj);
        
        if (mTestMode || getNumEvents() >= BULK_UPLOAD_LIMIT) {
            flush();
        }
        
    }
    
    /**
     * Same as calling funnel(funnelName, step, goal, null)
     * @param funnelName    The name of the funnel that this event belongs to
     * @param step  The step of the funnel that this event belongs to
     * @param goal  The name of this step
     */
    public void funnel(String funnelName, int step, String goal) {
        if (Global.DEBUG) Log.d(LOGTAG, "funnel");
        funnel(funnelName, step, goal, null);
    }
    
    /**
     * Logs a funnel event to MixPanel servers. To conserve bandwidth resources, a call to this method may not 
     * be sent immediately.   
     * @param funnelName    The name of the funnel that this event belongs to
     * @param step  The step of the funnel that this event belongs to
     * @param goal  The name of this step
     * @param properties A Map containing the key value pairs of the properties to include in this event. 
     * pass null if no extra properties exist 
     */
    public void funnel(String funnelName, int step, String goal,  Map<String, String> properties) {
        if (Global.DEBUG) Log.d(LOGTAG, "funnel");
        
        String time = Long.toString(System.currentTimeMillis() / 1000);
        
        JSONObject dataObj = new JSONObject();
        try {
            dataObj.put("event", "mp_funnel");
            JSONObject propertiesObj = new JSONObject();
            propertiesObj.put("token", mToken);
            propertiesObj.put("time", time);
            propertiesObj.put("funnel", funnelName);
            propertiesObj.put("step", step);
            propertiesObj.put("goal", goal);
            propertiesObj.put("distinct_id", mDeviceId == null ? "UNKNOWN" : mDeviceId);
            propertiesObj.put("carrier", mCarrier == null ? "UNKNOWN" : mCarrier);
            propertiesObj.put("model",  mModel == null ? "UNKNOWN" : mModel);
            propertiesObj.put("version", mVersion == null ? "UNKNOWN" : mVersion);
            propertiesObj.put("mp_lib", "android");

            if (mSuperProperties != null) {
                if (mSuperPropertiesType == SUPER_PROPERTY_TYPE_ALL || 
                        mSuperPropertiesType == SUPER_PROPERTY_TYPE_FUNNELS) {
                    for (Map.Entry<String, String> entry: mSuperProperties.entrySet()) {
                        propertiesObj.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            
            if (properties != null) {
                for (Map.Entry<String, String> entry: properties.entrySet()) {
                    propertiesObj.put(entry.getKey(), entry.getValue());
                }
            }
            
            dataObj.put("properties", propertiesObj);
            
            
        } catch (JSONException e) {
            e.printStackTrace();
        }

        addEvent(dataObj);
        
        if (mTestMode || getNumEvents() >= BULK_UPLOAD_LIMIT) {
            flush();
        }
    }

    /**
     * Parses the response sent back from a HTTP request   
     * @param resultsString The response body
     * @param responseId    The identifier of the response
     */
    private synchronized void parseResults(String resultString, long id, boolean success) {
        if (Global.DEBUG) Log.d(LOGTAG, "resultsString: " + resultString + " id: " + id + " success: " + success);
        
        if (success) {
            removeEventData(id);
        } else {
         // Did not get a response from the server
        }
        
        mDispatchedEvents.remove(id);
        
    }
 
}
