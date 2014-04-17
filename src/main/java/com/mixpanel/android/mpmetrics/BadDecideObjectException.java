package com.mixpanel.android.mpmetrics;

/* package */  class BadDecideObjectException extends Exception {
    public BadDecideObjectException(String detailMessage) {
        super(detailMessage);
    }

    public BadDecideObjectException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    private static final long serialVersionUID = 4858739193395706341L;
}
