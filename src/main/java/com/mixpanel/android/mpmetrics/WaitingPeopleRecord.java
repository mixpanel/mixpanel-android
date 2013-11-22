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

    // XXX: re-approach as an in-memory queue, to accomodate $union.
    // You can still collapse against the head record on set()
    public WaitingPeopleRecord() {
        mAdds = new HashMap<String, Double>();
        mAppends = new ArrayList<JSONObject>();
        mSets = new JSONObject();
    }

    public void setOnWaitingPeopleRecord(JSONObject sets)
        throws JSONException {
        for(final Iterator<?> iter = sets.keys(); iter.hasNext();) {
            final String key = (String) iter.next();
            final Object val = sets.get(key);

            // Subsequent sets will eliminate the effect of earlier increments and appends
            mAdds.remove(key);

            final List<JSONObject> remainingAppends = new ArrayList<JSONObject>();
            for (final JSONObject nextAppend: remainingAppends) {
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
        for(final String key: adds.keySet()) {
            final Number oldIncrement = mAdds.get(key);
            final Number changeIncrement = adds.get(key);

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
        final JSONObject stored = new JSONObject(jsonString);

        JSONObject newSets = new JSONObject();
        if (stored.has("$set")) {
            newSets = stored.getJSONObject("$set");
        }// if $set is found

        final Map<String, Double> newAdds = new HashMap<String, Double>();
        if (stored.has("$add")) {
            final JSONObject addsJSON = stored.getJSONObject("$add");
            for(final Iterator<?> iter = addsJSON.keys(); iter.hasNext();) {
                final String key = (String) iter.next();
                final Double amount = addsJSON.getDouble(key);
                newAdds.put(key, amount);
            }
        }// if $add is found

        final List<JSONObject> newAppends = new ArrayList<JSONObject>();
        if(stored.has("$append")) {
            final JSONArray appendsJSON = stored.getJSONArray("$append");
            for(int i = 0; i < appendsJSON.length(); i++) {
                final JSONObject nextAppend = appendsJSON.getJSONObject(i);
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
            final JSONObject addObject = new JSONObject();
            for(final String addKey:mAdds.keySet()) {
                final Double value = mAdds.get(addKey);
                addObject.put(addKey, value);
            }

            final JSONArray appendArray = new JSONArray();
            for(final JSONObject append:mAppends) {
                appendArray.put(append);
            }

            final JSONObject retObject = new JSONObject();
            retObject.put("$set", mSets);
            retObject.put("$add", addObject);
            retObject.put("$append", appendArray);

            ret = retObject.toString();
        } catch (final JSONException e) {
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
