package com.mixpanel.android.sessionreplay

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mixpanel.android.sessionreplay.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Helper to defer operations until the app enters foreground.
 * This prevents network calls during background app starts (e.g., push notifications),
 * which could cause DoS concerns when many devices wake simultaneously.
 */
internal class ForegroundAwaiter {
    /**
     * Suspends until the app enters the foreground.
     */
    suspend fun waitForForeground() {
        // Run on main thread due to requirement from ProcessLifecycleOwner
        withContext(Dispatchers.Main) {
            // Check if already in foreground before registering observer
            if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                Logger.info("App already in foreground, continuing initialization immediately")
                return@withContext
            }

            Logger.info("Waiting for app to enter foreground before continuing initialization")
            suspendCancellableCoroutine { continuation ->
                val observer = object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        // Remove this observer after first foreground
                        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
                        Logger.info("App entered foreground, continuing initialization")
                        continuation.resume(Unit)
                    }
                }

                ProcessLifecycleOwner.get().lifecycle.addObserver(observer)

                // Handle cancellation
                continuation.invokeOnCancellation {
                    // ProcessLifecycleOwner requires that Observers are removed on main thread
                    launch(Dispatchers.Main) {
                        Logger.info("Initialization cancelled before app entered foreground")
                        ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
                    }
                }
            }
        }
    }
}
