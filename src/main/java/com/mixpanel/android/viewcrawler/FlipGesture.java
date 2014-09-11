package com.mixpanel.android.viewcrawler;


import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

/* package */ class FlipGesture implements SensorEventListener {

    public interface OnFlipGestureListener {
        public void onFlipGesture();
    }

    public FlipGesture(OnFlipGestureListener listener) {
        mListener = listener;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final float[] smoothed = smoothXYZ(event.values);
        final int oldFlipState = mFlipState;

        if (smoothed[0] > MAXIMUM_XY_GRAVITY_FOR_FLIP || smoothed[0] < -MAXIMUM_XY_GRAVITY_FOR_FLIP) {
            mFlipState = FLIP_STATE_NONE;
        } else if (smoothed[1] > MAXIMUM_XY_GRAVITY_FOR_FLIP || smoothed[1] < -MAXIMUM_XY_GRAVITY_FOR_FLIP) {
            mFlipState = FLIP_STATE_NONE;
        } else if (smoothed[2] > MINIMUM_Z_GRAVITY_FOR_FLIP) {
            mFlipState = FLIP_STATE_UP;
        } else if (smoothed[2] < -MINIMUM_Z_GRAVITY_FOR_FLIP) {
            mFlipState = FLIP_STATE_DOWN;
        } else {
            mFlipState = FLIP_STATE_NONE;
        }

        if (oldFlipState != mFlipState) {
            mLastFlipTime = event.timestamp;
        }

        final long flipDurationNanos = event.timestamp - mLastFlipTime;

        if (flipDurationNanos > MINIMUM_FLIP_DURATION_NANOS) {
            if (mFlipState == FLIP_STATE_NONE && mTriggerState != TRIGGER_STATE_NONE) {
                mTriggerState = TRIGGER_STATE_NONE;
            } else if (mFlipState == FLIP_STATE_UP && mTriggerState == TRIGGER_STATE_NONE) {
                mTriggerState = TRIGGER_STATE_UP_BEGIN;
            } else if (mFlipState == FLIP_STATE_DOWN && mTriggerState == TRIGGER_STATE_UP_BEGIN) {
                mTriggerState = TRIGGER_STATE_DOWN_MIDDLE;
            } else if (mFlipState == FLIP_STATE_UP && mTriggerState == TRIGGER_STATE_DOWN_MIDDLE) {
                mTriggerState = TRIGGER_STATE_NONE;
                mListener.onFlipGesture();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        ; // Do nothing
    }

    private float[] smoothXYZ(final float[] samples) {
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

    private static final float MINIMUM_Z_GRAVITY_FOR_FLIP = 9.0f;
    private static final float MAXIMUM_XY_GRAVITY_FOR_FLIP = 2.0f;

    private static final long MINIMUM_FLIP_DURATION_NANOS = 1000000000;

    private static final int FLIP_STATE_UP = -1;
    private static final int FLIP_STATE_NONE = 0;
    private static final int FLIP_STATE_DOWN = 1;

    private static final int TRIGGER_STATE_NONE = 0;
    private static final int TRIGGER_STATE_UP_BEGIN = 1;
    private static final int TRIGGER_STATE_DOWN_MIDDLE = 2;

    private static final float ACCELEROMETER_SMOOTHING = 0.5f;
}
