package com.mixpanel.android.mpmetrics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

/* package */ class WaitingPeopleRecord {
    private static final String LOGTAG = "MixpanelAPI";

    public WaitingPeopleRecord() {
        mAdds = new HashMap<String, Double>();
        mAppends = new ArrayList<JSONObject>();
        mSets = new JSONObject();
    }

    public void setOnWaitingPeopleRecord(JSONObject sets)
        throws JSONException {
        for(Iterator<?> iter = sets.keys(); iter.hasNext();) {
            String key = (String) iter.next();
            Object val = sets.get(key);

            // Subsequent sets will eliminate the effect of earlier increments and appends
            mAdds.remove(key);

            List<JSONObject> remainingAppends = new ArrayList<JSONObject>();
            for (JSONObject nextAppend: remainingAppends) {
                nextAppend.remove(key);
                if (nextAppend.length() > 0) {
                    remainingAppends.add(nextAppend);
                }
            }
            mAppends = remainingAppends;

            mSets.put(key, val);
        }
    }

    public void incrementToWaitingPeopleRecord(Map<String, ? extends Number> adds) {
        for(String key: adds.keySet()) {
            Number oldIncrement = mAdds.get(key);
            Number changeIncrement = adds.get(key);

            if ((oldIncrement == null) && (changeIncrement != null)) {
                mAdds.put(key, changeIncrement.doubleValue());
            }
            else if ((oldIncrement != null) && (changeIncrement != null)) {
                // the result of two increments is the same as the sum of
                // the increment values
                mAdds.put(key, oldIncrement.doubleValue() + changeIncrement.doubleValue());
            }
        }
    }

    public void appendToWaitingPeopleRecord(JSONObject properties) {
        mAppends.add(properties);
    }

    public void readFromJSONString(String jsonString)
        throws JSONException {
        JSONObject stored = new JSONObject(jsonString);

        JSONObject newSets = new JSONObject();
        if (stored.has("$set")) {
            newSets = stored.getJSONObject("$set");
        }// if $set is found

        Map<String, Double> newAdds = new HashMap<String, Double>();
        if (stored.has("$add")) {
            JSONObject addsJSON = stored.getJSONObject("$add");
            for(Iterator<?> iter = addsJSON.keys(); iter.hasNext();) {
                String key = (String) iter.next();
                Double amount = addsJSON.getDouble(key);
                newAdds.put(key, amount);
            }
        }// if $add is found

        List<JSONObject> newAppends = new ArrayList<JSONObject>();
        if(stored.has("$append")) {
            JSONArray appendsJSON = stored.getJSONArray("$append");
            for(int i = 0; i < appendsJSON.length(); i++) {
                JSONObject nextAppend = appendsJSON.getJSONObject(i);
                newAppends.add(nextAppend);
            }
        }// if $append is found

        mSets = newSets;
        mAdds = newAdds;
        mAppends = newAppends;
    }

    public String toJSONString() {
        String ret = null;

        try {
            JSONObject addObject = new JSONObject();
            for(String addKey:mAdds.keySet()) {
                Double value = mAdds.get(addKey);
                addObject.put(addKey, value);
            }

            JSONArray appendArray = new JSONArray();
            for(JSONObject append:mAppends) {
                appendArray.put(append);
            }

            JSONObject retObject = new JSONObject();
            retObject.put("$set", mSets);
            retObject.put("$add", addObject);
            retObject.put("$append", appendArray);

            ret = retObject.toString();
        } catch (JSONException e) {
            Log.e(LOGTAG, "Could not write Waiting User Properties to JSON", e);
        }

        return ret;
    }

    public JSONObject setMessage() {
        return mSets;
    }

    public Map<String, Double> incrementMessage() {
        return mAdds;
    }

    public List<JSONObject> appendMessages() {
        return mAppends;
    }

    private JSONObject mSets;
    private Map<String, Double> mAdds;
    private List<JSONObject> mAppends;
}
