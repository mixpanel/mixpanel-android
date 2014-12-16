package com.mixpanel.android.viewcrawler;

/* This interface is for internal use in the Mixpanel library, and should not be
   implemented in client code. */
public interface TrackingDebug {
    public void reportTrack(String eventName);
}
