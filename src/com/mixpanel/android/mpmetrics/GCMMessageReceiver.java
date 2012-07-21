package com.mixpanel.android.mpmetrics;

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

public class GCMMessageReceiver extends BroadcastReceiver {
	String LOGTAG = "MPGMMessage";

	@Override
	public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
		if ("com.google.android.c2dm.intent.RECEIVE".equals(action)) {
			Log.v(LOGTAG, "in message");

            String message = intent.getExtras().getString("mp_message");
            if (message == null || !MPConfig.ALLOW_MP_PUSH) 
            	return;

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