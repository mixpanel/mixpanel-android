package com.mixpanel.android.mpmetrics;

import static com.mixpanel.android.mpmetrics.ConfigurationChecker.LOGTAG;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.mixpanel.android.util.MPLog;

import java.io.Serializable;
import java.util.HashMap;

public class SessionReplayBroadcastReceiver extends BroadcastReceiver {
    private final MixpanelAPI sdkInstance;

    public static final String REGISTER_ACTION = "com.mixpanel.properties.register";
    public static final String  UNREGISTER_ACTION = "com.mixpanel.properties.unregister";

    public SessionReplayBroadcastReceiver(MixpanelAPI instance) {
        this.sdkInstance = instance;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (REGISTER_ACTION.equals(action)) {
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
            if (data != null && data.containsKey("$mp_replay_id")) {
                sdkInstance.registerSuperPropertiesMap(data);
            }
        } else if (UNREGISTER_ACTION.equals(action)) {
            sdkInstance.unregisterSuperProperty("$mp_replay_id");
        }
    }
}
