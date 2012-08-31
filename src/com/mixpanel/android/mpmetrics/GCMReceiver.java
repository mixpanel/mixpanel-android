package com.mixpanel.android.mpmetrics;

import java.util.Map;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

public class GCMReceiver extends BroadcastReceiver {
    String LOGTAG = "MPGCMReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if ("com.google.android.c2dm.intent.REGISTRATION".equals(action)) {
            String registration = intent.getStringExtra("registration_id");

            if (intent.getStringExtra("error") != null) {
                if (MPConfig.DEBUG) Log.d(LOGTAG, "Error when registering for GCM: " + intent.getStringExtra("error"));
                // Registration failed, try again later
            } else if (registration != null) {
                if (MPConfig.DEBUG) Log.d(LOGTAG, "registering GCM ID: " + registration);

                Map<String, MPMetrics> allMetrics = MPMetrics.allInstances();
                for (String token : allMetrics.keySet()) {
                    allMetrics.get(token).getPeople().setPushRegistrationId(registration);
                }
            } else if (intent.getStringExtra("unregistered") != null) {
                // unregistration done, new messages from the authorized sender will be rejected
                if (MPConfig.DEBUG) Log.d(LOGTAG, "unregistering from GCM");

                Map<String, MPMetrics> allMetrics = MPMetrics.allInstances();
                for (String token : allMetrics.keySet()) {
                    allMetrics.get(token).getPeople().clearPushRegistrationId();
                }
            }
        } else if ("com.google.android.c2dm.intent.RECEIVE".equals(action)) {
            String message = intent.getExtras().getString("mp_message");

            if (message == null) return;
            if (MPConfig.DEBUG) Log.d(LOGTAG, "MP GCM notification received: " + message);

            PackageManager manager = context.getPackageManager();
            Intent appIntent = manager.getLaunchIntentForPackage(context.getPackageName());
            CharSequence notificationTitle = "";
            int notificationIcon = android.R.drawable.sym_def_app_icon;
            try {
                ApplicationInfo appInfo = manager.getApplicationInfo(context.getPackageName(), 0);
                notificationTitle = manager.getApplicationLabel(appInfo);
                notificationIcon = appInfo.icon;
            } catch (NameNotFoundException e) { }

            PendingIntent contentIntent = PendingIntent.getActivity(
                context.getApplicationContext(),
                0,
                appIntent,   // add this pass null to intent
                PendingIntent.FLAG_UPDATE_CURRENT
            );

            NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            Notification n = new Notification(notificationIcon, message, System.currentTimeMillis());
            n.flags |= Notification.FLAG_AUTO_CANCEL;
            n.setLatestEventInfo(context, notificationTitle, message, contentIntent);
            nm.notify(0, n);
        }
    }
}
