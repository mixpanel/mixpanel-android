package com.mixpanel.android.sessionreplay.utils

import java.util.concurrent.locks.ReentrantReadWriteLock

class ReadWriteLock(
    label: String
) {
    val lock = ReentrantReadWriteLock()

    // The label is not directly used in ReentrantReadWriteLock,
    // but we could log it for debugging if needed.

    inline fun <T> read(action: () -> T): T {
        lock.readLock().lock()
        try {
            return action()
        } finally {
            lock.readLock().unlock()
        }
    }

    inline fun <T> write(action: () -> T): T {
        lock.writeLock().lock()
        try {
            return action()
        } finally {
            lock.writeLock().unlock()
        }
    }
}
