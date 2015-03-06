package com.mixpanel.android.mpmetrics;


class ServiceUnavailableException extends Exception {
    public ServiceUnavailableException(String message, String strRetryAfter) {
        super(message);
        int retry;
        try {
            retry = Integer.parseInt(strRetryAfter);
        } catch (NumberFormatException e) {
            retry = 0;
        }
        mRetryAfter = retry;
    }

    public int getRetryAfter() {
        return mRetryAfter;
    }

    private final int mRetryAfter;
}
