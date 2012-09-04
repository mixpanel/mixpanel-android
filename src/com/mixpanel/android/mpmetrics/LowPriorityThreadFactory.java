package com.mixpanel.android.mpmetrics;

import java.util.concurrent.ThreadFactory;

/* package */ class LowPriorityThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    }
}