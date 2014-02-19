package com.mixpanel.android.mpmetrics;

import com.mixpanel.android.R;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.TextView;

public class NotificationActivity extends Activity {
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (getIntent().getIntExtra("notificationType", 0) == NOTIFICATION_TYPE_FULL) {
	    	getWindow().setBackgroundDrawableResource(R.color.com_mixpanel_android_notification_dim);
			setContentView(R.layout.com_mixpanel_android_activity_notification_full);

			TextView subtextView = (TextView) findViewById(R.id.com_mixpanel_android_notification_subtext);
			String subtextText = getIntent().getStringExtra("notificationSubtext");
			subtextView.setText(subtextText);
		} else {
			getWindow().setBackgroundDrawableResource(android.R.color.transparent);
			setContentView(R.layout.com_mixpanel_android_activity_notification_mini);
		}
		
		TextView titleView = (TextView) findViewById(R.id.com_mixpanel_android_notification_title);
		String titleText = getIntent().getStringExtra("notificationTitle");
		titleView.setText(titleText);
	}

	public void dismissNotification(final View view) {
		finish();
	}
	
	public static final int NOTIFICATION_TYPE_FULL = 0;
	public static final int NOTIFICATION_TYPE_MINI = 1;
}