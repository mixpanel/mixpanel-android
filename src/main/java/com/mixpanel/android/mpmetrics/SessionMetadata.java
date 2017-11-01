package com.mixpanel.android.mpmetrics;

import com.mixpanel.android.util.MPLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Random;

import static com.mixpanel.android.mpmetrics.ConfigurationChecker.LOGTAG;

/**
 * Created by yardeneitan on 10/30/17.
 */

public class SessionMetadata {
    private Long eventsCounter;
    private Long peopleCounter;
    private Long sessionID;
    private Long sessionStartEpoch;


    public SessionMetadata() {
        eventsCounter = 0L;
        peopleCounter = 0L;
        sessionID = 0L;
        sessionStartEpoch = System.currentTimeMillis();
    }

    void reset() {
        eventsCounter = 0L;
        peopleCounter = 0L;
        sessionID = new Random().nextLong();
    }

    void addMetadataToObject(JSONObject object, Boolean isEvent) {
        try {
            object.put("$mp_event_id", new Random().nextLong());
            object.put("$mp_session_id", sessionID);
            object.put("$mp_session_seq_id", isEvent ? eventsCounter : peopleCounter);
            object.put("$mp_session_start_sec", sessionStartEpoch);
            if (isEvent) {
                eventsCounter += 1;
            } else {
                peopleCounter += 1;
            }
        } catch (JSONException e) {
            MPLog.e(LOGTAG, "value read cannot be written to a JSON object", e);
        }
    }


}
