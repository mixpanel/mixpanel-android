package com.mixpanel.android.mpmetrics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class RegistrationReceiver extends BroadcastReceiver {
    String LOGTAG = "MPRegistration";
    
	@Override
    public void onReceive(Context context, Intent intent) {
		String token = context.getSharedPreferences("mpPushPref", 0).getString("mp_token", null);
		if (token == null) return;
		
		MPMetrics mp = MPMetrics.getInstance(context, token);	
        String action = intent.getAction();

        if ("com.google.android.c2dm.intent.REGISTRATION".equals(action)) {
        	String registration = intent.getStringExtra("registration_id");

        	if (intent.getStringExtra("error") != null) {
                if (MPConfig.DEBUG) Log.d(LOGTAG, "Error when registering for GCM: " + intent.getStringExtra("error"));
                // Registration failed, try again later
            } else if (registration != null) {
                if (MPConfig.DEBUG) Log.d(LOGTAG, "registering GCM ID: " + registration);
                mp.setPushId(registration);
            } else if (intent.getStringExtra("unregistered") != null) {
                // unregistration done, new messages from the authorized sender will be rejected
                if (MPConfig.DEBUG) Log.d(LOGTAG, "unregistering from GCM");
            	mp.removePushId();             
            }
        }
    }
}
