package com.mixpanel.android.mpmetrics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.mixpanel.android.util.MPConstants.SessionReplay;
import com.mixpanel.android.util.MPLog;

import java.io.Serializable;
import java.util.HashMap;

public class SessionReplayBroadcastReceiver extends BroadcastReceiver {
    private static final String LOGTAG = "SessionReplayBroadcastReceiver";
    private final MixpanelAPI sdkInstance;

    public SessionReplayBroadcastReceiver(MixpanelAPI instance) {
        this.sdkInstance = instance;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (SessionReplay.REGISTER_ACTION.equals(action)) {
            HashMap<String, Object> data = null;
            Serializable serializableData = intent.getSerializableExtra("data");
            if (serializableData instanceof HashMap) {
                try {
                    data = (HashMap<String, Object>) serializableData;
                } catch (ClassCastException e) {
                    MPLog.e(LOGTAG, "Failed to cast broadcast extras data to HashMap", e);
                    MPLog.d(LOGTAG, "Broadcast extras data: " + serializableData);
                }
            }
            if (data != null && data.containsKey(SessionReplay.REPLAY_ID_KEY)) {
                sdkInstance.registerSuperPropertiesMap(data);
            }
        } else if (SessionReplay.UNREGISTER_ACTION.equals(action)) {
            sdkInstance.unregisterSuperProperty(SessionReplay.REPLAY_ID_KEY);
        }
    }
}
