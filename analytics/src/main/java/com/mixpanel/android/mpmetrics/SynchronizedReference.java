package com.mixpanel.android.mpmetrics;

/**
 * We need this for stronger ordering guarantees than AtomicReference
 * (and we don't need compareAndSet)
 */
/* package */ class SynchronizedReference<T> {
    public SynchronizedReference() {
        mContents = null;
    }

    public synchronized void set(T contents) {
        mContents = contents;
    }

    public synchronized T getAndClear() {
        final T ret = mContents;
        mContents = null;
        return ret;
    }

    public synchronized T get() {
        return mContents;
    }

    private T mContents;
}
