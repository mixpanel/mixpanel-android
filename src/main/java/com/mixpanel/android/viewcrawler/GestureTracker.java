package com.mixpanel.android.viewcrawler;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import com.mixpanel.android.mpmetrics.MixpanelAPI;

/**
 * Tracking ABTesting Gestures
 */
public class GestureTracker {
	public GestureTracker(MixpanelAPI mMixpanel, Activity parent) {
		trackGestures(mMixpanel, parent);
	}

	private void trackGestures(final MixpanelAPI mMixpanel, Activity parent) {
		parent.getWindow().getDecorView().findViewById(android.R.id.content).setOnTouchListener(new View.OnTouchListener() {

			private void resetGesture() {
				mFirstToSecondFingerDifference = -1;
				mSecondFingerTimeDown = System.currentTimeMillis();
				mGestureSteps = 0;
				mBetweenSteps = -1;
			}

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getPointerCount() > 2) {
					resetGesture();
					return false;
				}

				switch (event.getActionMasked()) {
					case MotionEvent.ACTION_DOWN:
						mFirstToSecondFingerDifference = System.currentTimeMillis();
						break;
					case MotionEvent.ACTION_POINTER_DOWN:
						if ((System.currentTimeMillis() - mFirstToSecondFingerDifference) < TimeBetweenFingersThreshold) {
							mSecondFingerTimeDown = System.currentTimeMillis();
							mFirstToSecondFingerDifference = TimeBetweenFingersThreshold;
							if (System.currentTimeMillis() - mBetweenSteps > TimeBetweenTapsThreshold && mBetweenSteps != -1) {
								resetGesture();
							}
						} else {
							resetGesture();
						}
						break;
					case MotionEvent.ACTION_POINTER_UP:
						if (mFirstToSecondFingerDifference == TimeBetweenFingersThreshold) {
							mFirstToSecondFingerDifference = System.currentTimeMillis();
						} else {
							resetGesture();
						}
						break;
					case MotionEvent.ACTION_UP:
						if ((System.currentTimeMillis() - mFirstToSecondFingerDifference) < TimeBetweenFingersThreshold) {
							if ((System.currentTimeMillis() - mSecondFingerTimeDown) >= TimeForLongTap) {
								//Long Tap
								if (mGestureSteps == 3) {
									mMixpanel.track("$ab_gesture1");
									resetGesture();
								}
								mGestureSteps = 0;
							} else {
								//Short Tap
								mBetweenSteps = System.currentTimeMillis();
								if (mGestureSteps < 4) {
									mGestureSteps += 1;
								} else if (mGestureSteps == 4) {
									mMixpanel.track("$ab_gesture2");
									resetGesture();
								} else {
									resetGesture();
								}
							}
						}
						break;
				}
				return true;
			}

			private long mSecondFingerTimeDown = System.currentTimeMillis();
			private long mFirstToSecondFingerDifference = -1;
			private int mGestureSteps = 0;
			private long mBetweenSteps = -1;
			private final int TimeBetweenFingersThreshold = 100;
			private final int TimeBetweenTapsThreshold = 1000;
			private final int TimeForLongTap = 2500;
		});
	}
}
