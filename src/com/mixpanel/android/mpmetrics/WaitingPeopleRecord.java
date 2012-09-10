package com.mixpanel.android.mpmetrics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

class WaitingPeopleRecord {
    private static final String LOGTAG = "MixpanelAPI";

    public WaitingPeopleRecord() {
        mAdds = new HashMap<String, Long>();
        try {
            mSets = new JSONObject("{}");
        } catch (JSONException e) {
            throw new RuntimeException("Cannot initialize WaitingPeopleRecord JSON object");
        }
    }

    public void setOnWaitingPeopleRecord(JSONObject sets)
        throws JSONException {
        for(Iterator<?> iter = sets.keys(); iter.hasNext();) {
            String key = (String) iter.next();
            Object val = sets.get(key);

            // Subsequent sets will eliminate the effect of increments
            mAdds.remove(key);
            mSets.put(key, val);
        }
    }

    public void incrementToWaitingPeopleRecord(Map<String, Long> adds) {
        for(String key: adds.keySet()) {
            Long oldIncrement = mAdds.get(key);
            Long changeIncrement = adds.get(key);

            if ((oldIncrement == null) && (changeIncrement != null)) {
                mAdds.put(key, changeIncrement);
            }
            else if ((oldIncrement != null) && (changeIncrement != null)) {
                // the result of two increments is the same as the sum of
                // the increment values
                mAdds.put(key, oldIncrement + changeIncrement);
            }
        }
    }

    public void readFromJSONString(String jsonString)
        throws JSONException {
        JSONObject stored = new JSONObject(jsonString);

        JSONObject newSets = stored.getJSONObject("$set");

        Map<String, Long> newAdds = new HashMap<String, Long>();
        JSONObject addsJSON = stored.getJSONObject("$add");
        for(Iterator<?> iter = addsJSON.keys(); iter.hasNext();) {
            String key = (String) iter.next();
            Long amount = addsJSON.getLong(key);
            newAdds.put(key, amount);
        }

        mSets = newSets;
        mAdds = newAdds;
    }

    public String toJSONString() {
        String ret = null;

        try {
            JSONObject addObject = new JSONObject();
            for(String addKey:mAdds.keySet()) {
                Long value = mAdds.get(addKey);
                addObject.put(addKey, value);
            }

            JSONObject retObject = new JSONObject();
            retObject.put("$set", mSets);
            retObject.put("$add", addObject);

            ret = retObject.toString();
        } catch (JSONException e) {
            Log.e(LOGTAG, "Could not write Waiting User Properties to JSON", e);
        }

        return ret;
    }

    public JSONObject setMessage() {
        return mSets;
    }

    public Map<String, Long> incrementMessage() {
        return mAdds;
    }

    private JSONObject mSets;
    private Map<String, Long> mAdds;
}
