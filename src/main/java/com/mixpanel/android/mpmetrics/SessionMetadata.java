package com.mixpanel.android.mpmetrics;

import static com.mixpanel.android.mpmetrics.ConfigurationChecker.LOGTAG;

import com.mixpanel.android.util.MPLog;
import java.security.SecureRandom;
import org.json.JSONException;
import org.json.JSONObject;

/* package */ class SessionMetadata {
  private long mEventsCounter, mPeopleCounter, mSessionStartEpoch;
  private String mSessionID;
  private final SecureRandom mRandom;

  /* package */ SessionMetadata() {
    initSession();
    mRandom = new SecureRandom();
  }

  protected void initSession() {
    mEventsCounter = 0L;
    mPeopleCounter = 0L;
    mSessionID = Long.toHexString(new SecureRandom().nextLong());
    mSessionStartEpoch = System.currentTimeMillis() / 1000;
  }

  public JSONObject getMetadataForEvent() {
    return getNewMetadata(true);
  }

  public JSONObject getMetadataForPeople() {
    return getNewMetadata(false);
  }

  private JSONObject getNewMetadata(boolean isEvent) {
    JSONObject metadataJson = new JSONObject();
    try {
      metadataJson.put("$mp_event_id", Long.toHexString(mRandom.nextLong()));
      metadataJson.put("$mp_session_id", mSessionID);
      metadataJson.put("$mp_session_seq_id", isEvent ? mEventsCounter : mPeopleCounter);
      metadataJson.put("$mp_session_start_sec", mSessionStartEpoch);
      if (isEvent) {
        mEventsCounter++;
      } else {
        mPeopleCounter++;
      }
    } catch (JSONException e) {
      MPLog.e(LOGTAG, "Cannot create session metadata JSON object", e);
    }

    return metadataJson;
  }
}
