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
		setContentView(R.layout.com_mixpanel_android_activity_notification);
		
		TextView titleView = (TextView) findViewById(R.id.com_mixpanel_android_notification_title);
		String titleText = getIntent().getStringExtra("notificationTitle");
		titleView.setText(titleText);
		
		TextView subtextView = (TextView) findViewById(R.id.com_mixpanel_android_notification_subtext);
		String subtextText = getIntent().getStringExtra("notificationSubtext");
		subtextView.setText(subtextText);
	}

	public void dismissNotification(final View view) {
		finish();
	}
}