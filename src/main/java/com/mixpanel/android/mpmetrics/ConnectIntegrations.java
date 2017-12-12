package com.mixpanel.android.mpmetrics;

import android.os.Handler;

import com.mixpanel.android.util.MPLog;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

/* package */ class ConnectIntegrations {
    private final MixpanelAPI mMixpanel;
    private String mSavedUrbanAirshipChannelID;
    private int mUrbanAirshipRetries;

    private static final String LOGTAG = "MixpanelAPI.CnctInts";
    private static final int UA_MAX_RETRIES = 3;

    public ConnectIntegrations(MixpanelAPI mixpanel) {
        mMixpanel = mixpanel;
    }

    public void reset() {
        mSavedUrbanAirshipChannelID = null;
        mUrbanAirshipRetries = 0;
    }

    public synchronized void setupIntegrations(Set<String> integrations) {
        if (integrations.contains("urbanairship")) {
            setUrbanAirshipPeopleProp();
        }
    }

    private synchronized void setUrbanAirshipPeopleProp() {
        String urbanAirshipClassName = "com.urbanairship.UAirship";
        try {
            Class urbanAirshipClass = Class.forName(urbanAirshipClassName);
            Object sharedUAirship = urbanAirshipClass.getMethod("shared", null).invoke(null);
            Object pushManager = sharedUAirship.getClass().getMethod("getPushManager", null).invoke(sharedUAirship);
            String channelID = (String)pushManager.getClass().getMethod("getChannelId", null).invoke(pushManager);
            if (channelID != null && !channelID.isEmpty()) {
                mUrbanAirshipRetries = 0;
                if (mSavedUrbanAirshipChannelID == null || !mSavedUrbanAirshipChannelID.equals(channelID)) {
                    mMixpanel.getPeople().set("$android_urban_airship_channel_id", channelID);
                    mSavedUrbanAirshipChannelID = channelID;
                }
            } else {
                mUrbanAirshipRetries++;
                if (mUrbanAirshipRetries <= UA_MAX_RETRIES) {
                    final Handler delayedHandler = new Handler();
                    delayedHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            setUrbanAirshipPeopleProp();
                        }
                    }, 2000);
                }
            }
        } catch (ClassNotFoundException e) {
            MPLog.w(LOGTAG, "Urban Airship SDK not found but Urban Airship is integrated on Mixpanel", e);
        } catch (NoSuchMethodException e) {
            MPLog.e(LOGTAG, "Urban Airship SDK class exists but methods do not", e);
        } catch (InvocationTargetException e) {
            MPLog.e(LOGTAG, "method invocation failed", e);
        } catch (IllegalAccessException e) {
            MPLog.e(LOGTAG, "method invocation failed", e);
        }
    }
}
