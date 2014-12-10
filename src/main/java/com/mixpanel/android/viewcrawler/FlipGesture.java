package com.mixpanel.android.viewcrawler;


import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

import com.mixpanel.android.mpmetrics.MPConfig;

/* package */ class FlipGesture implements SensorEventListener {

    public interface OnFlipGestureListener {
        public void onFlipGesture();
    }

    public FlipGesture(OnFlipGestureListener listener) {
        mListener = listener;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Samples may come in around 4 times per second

        final float[] smoothed = smoothXYZ(event.values);
        final int oldFlipState = mFlipState;

        final float totalGravitySquared =
                smoothed[0] * smoothed[0] + smoothed[1] * smoothed[1] + smoothed[2] * smoothed[2];

        final float minimumGravitySquared = MINIMUM_GRAVITY_FOR_FLIP * MINIMUM_GRAVITY_FOR_FLIP;
        final float maximumGravitySquared = MAXIMUM_GRAVITY_FOR_FLIP * MAXIMUM_GRAVITY_FOR_FLIP;

        mFlipState = FLIP_STATE_NONE;

        if (smoothed[2] > MINIMUM_GRAVITY_FOR_FLIP && smoothed[2] < MAXIMUM_GRAVITY_FOR_FLIP) {
            mFlipState = FLIP_STATE_UP;
        }

        if (smoothed[2] < -MINIMUM_GRAVITY_FOR_FLIP && smoothed[2] > -MAXIMUM_GRAVITY_FOR_FLIP) {
            mFlipState = FLIP_STATE_DOWN;
        }

        // Might overwrite current state, which is what we want.
        if (totalGravitySquared < minimumGravitySquared ||
                totalGravitySquared > maximumGravitySquared) {
            mFlipState = FLIP_STATE_NONE;
        }

        if (oldFlipState != mFlipState) {
            mLastFlipTime = event.timestamp;
        }

        // We need at least 1/4 seconds to recognize an UP or DOWN state
        // We need at least 1 seconds to recognize a NONE state

        final long flipDurationNanos = event.timestamp - mLastFlipTime;

        switch (mFlipState) {
            case FLIP_STATE_DOWN:
                if (flipDurationNanos > MINIMUM_UP_DOWN_DURATION && mTriggerState == TRIGGER_STATE_NONE) {
                    if (MPConfig.DEBUG) {
                        Log.v(LOGTAG, "Flip gesture begun");
                    }
                    mTriggerState = TRIGGER_STATE_BEGIN;
                }
                break;
            case FLIP_STATE_UP:
                if (flipDurationNanos > MINIMUM_UP_DOWN_DURATION && mTriggerState == TRIGGER_STATE_BEGIN) {
                    if (MPConfig.DEBUG) {
                        Log.v(LOGTAG, "Flip gesture completed");
                    }
                    mTriggerState = TRIGGER_STATE_NONE;
                    mListener.onFlipGesture();
                }
                break;
            case FLIP_STATE_NONE:
                if (flipDurationNanos > MINIMUM_CANCEL_DURATION && mTriggerState != TRIGGER_STATE_NONE) {
                    if (MPConfig.DEBUG) {
                        Log.v(LOGTAG, "Flip gesture abandoned");
                    }
                    mTriggerState = TRIGGER_STATE_NONE;
                }
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        ; // Do nothing
    }

    private float[] smoothXYZ(final float[] samples) {
        // Note that smoothing doesn't depend on sample timestamp!
        for (int i = 0; i < 3; i++) {
            final float oldVal = mSmoothed[i];
            mSmoothed[i] = oldVal + (ACCELEROMETER_SMOOTHING * (samples[i] - oldVal));
        }

        return mSmoothed;
    }

    private int mTriggerState = -1;
    private int mFlipState = FLIP_STATE_NONE;
    private long mLastFlipTime = -1;
    private final float[] mSmoothed = new float[3];
    private final OnFlipGestureListener mListener;

    private static final float MINIMUM_GRAVITY_FOR_FLIP = 9.8f - 2.0f;
    private static final float MAXIMUM_GRAVITY_FOR_FLIP = 9.8f + 2.0f;

    // 1000000000 one second
    //  250000000 one quarter second
    private static final long MINIMUM_UP_DOWN_DURATION = 250000000;
    private static final long MINIMUM_CANCEL_DURATION = 1000000000;

    private static final int FLIP_STATE_UP = -1;
    private static final int FLIP_STATE_NONE = 0;
    private static final int FLIP_STATE_DOWN = 1;

    private static final int TRIGGER_STATE_NONE = 0;
    private static final int TRIGGER_STATE_BEGIN = 1;

    // Higher is noisier but more responsive, 1.0 to 0.0
    private static final float ACCELEROMETER_SMOOTHING = 0.7f;

    private static final String LOGTAG = "MixpanelAPI.FlipGesture";
}
