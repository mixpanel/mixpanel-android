package com.mixpanel.android.viewcrawler;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;

import com.mixpanel.android.R;
import com.mixpanel.android.mpmetrics.MixpanelAPI;

/**
 * Tracking ABTesting Gestures
 * $ab_gesture1 = 4 times two finger tap when last tap is hold for 3 seconds
 * $ab_gesture2 = 5 times two finger tap
 **/
public class GestureTracker {

    public GestureTracker(MixpanelAPI mMixpanel, Activity parent) {
        trackGestures(mMixpanel, parent);
    }

    private void trackGestures(final MixpanelAPI mMixpanel, final Activity parent) {
        parent.getWindow().getDecorView().setOnTouchListener(getGestureTrackerTouchListener(mMixpanel));
    }

    private View.OnTouchListener getGestureTrackerTouchListener(final MixpanelAPI mMixpanel) {
        return new View.OnTouchListener() {

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
                        if ((System.currentTimeMillis() - mFirstToSecondFingerDifference) < TIME_BETWEEN_FINGERS_THRESHOLD) {
                            if (System.currentTimeMillis() - mTimePassedBetweenTaps > TIME_BETWEEN_TAPS_THRESHOLD) {
                                resetGesture();
                            }
                            mSecondFingerTimeDown = System.currentTimeMillis();
                            mDidTapDownBothFingers = true;
                        } else {
                            resetGesture();
                        }
                        break;
                    case MotionEvent.ACTION_POINTER_UP:
                        if (mDidTapDownBothFingers) {
                            mFirstToSecondFingerDifference = System.currentTimeMillis();
                        } else {
                            resetGesture();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if ((System.currentTimeMillis() - mFirstToSecondFingerDifference) < TIME_BETWEEN_FINGERS_THRESHOLD) {
                            if ((System.currentTimeMillis() - mSecondFingerTimeDown) >= TIME_FOR_LONG_TAP) {
                                // Long Tap
                                if (mGestureSteps == 3) {
                                    mMixpanel.track("$ab_gesture1");
                                    resetGesture();
                                }
                                mGestureSteps = 0;
                            } else {
                                // Short Tap
                                mTimePassedBetweenTaps = System.currentTimeMillis();
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
                return false;
            }

            private void resetGesture() {
                mFirstToSecondFingerDifference = -1;
                mSecondFingerTimeDown = -1;
                mGestureSteps = 0;
                mTimePassedBetweenTaps = -1;
                mDidTapDownBothFingers = false;
            }

            private long mSecondFingerTimeDown = -1;
            private long mFirstToSecondFingerDifference = -1;
            private int mGestureSteps = 0;
            private long mTimePassedBetweenTaps = -1;
            private boolean mDidTapDownBothFingers = false;
            private final int TIME_BETWEEN_FINGERS_THRESHOLD = 100;
            private final int TIME_BETWEEN_TAPS_THRESHOLD = 1000;
            private final int TIME_FOR_LONG_TAP = 2500;
        };
    }

}
