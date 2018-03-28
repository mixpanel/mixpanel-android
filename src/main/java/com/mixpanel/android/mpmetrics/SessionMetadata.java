package com.mixpanel.android.mpmetrics;

import com.mixpanel.android.util.MPLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Random;

import static com.mixpanel.android.mpmetrics.ConfigurationChecker.LOGTAG;

/* package */ class SessionMetadata {
    private long mEventsCounter, mPeopleCounter, mSessionID, mSessionStartEpoch;

    /* package */ SessionMetadata() {
        initSession();
    }

    protected void initSession() {
        mEventsCounter = 0L;
        mPeopleCounter = 0L;
        mSessionID = new Random().nextLong();
        mSessionStartEpoch = System.currentTimeMillis();
    }

    public JSONObject getMetadataForEvent() {
        return getNewMetadata(true);
    }

    public JSONObject getMetadataForPeople() {
        return getNewMetadata(false);
    }

    private JSONObject getNewMetadata(boolean isEvent) {
        try {
            JSONObject metadataJson = new JSONObject();
            metadataJson.put("$mp_event_id", new Random().nextLong());
            metadataJson.put("$mp_session_id", mSessionID);
            metadataJson.put("$mp_session_seq_id", isEvent ? mEventsCounter : mPeopleCounter);
            metadataJson.put("$mp_session_start_sec", mSessionStartEpoch);
            if (isEvent) {
                mEventsCounter += 1;
            } else {
                mPeopleCounter += 1;
            }

            return metadataJson;
        } catch (JSONException e) {
            MPLog.e(LOGTAG, "value read cannot be written to a JSON object", e);
        }

        return new JSONObject();
    }
}
