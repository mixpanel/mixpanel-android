package com.mixpanel.android.sessionreplay.services

import com.mixpanel.android.sessionreplay.logging.Logger
import com.mixpanel.android.sessionreplay.network.FlushRequest
import com.mixpanel.android.sessionreplay.network.NetworkMonitoring
import com.mixpanel.android.sessionreplay.utils.EndPoints
import com.mixpanel.android.sessionreplay.utils.PayloadInfo
import com.mixpanel.android.sessionreplay.utils.ReplaySettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID

class FlushService(
    token: String,
    distinctId: String,
    var wifiOnly: Boolean,
    private val networkMonitor: NetworkMonitoring,
    private val lifecycleScope: CoroutineScope,
    private val eventService: EventService = EventService(),
    private val serverUrl: String = EndPoints.DEFAULT_BASE_URL,
    private val flushRequest: FlushRequest = FlushRequest(token, distinctId, serverUrl),
    flushInterval: Long = ReplaySettings.FLUSH_INTERVAL, // Flush interval in seconds
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO // Injecting dispatcher
) {
    // Pre-compute delay in milliseconds, capping to prevent overflow
    private val flushIntervalMs: Long = if (flushInterval >= Long.MAX_VALUE / 1000) Long.MAX_VALUE else flushInterval * 1000

    private var flushJob: Job? = null
    private var seqId: Int = 0
    private var replayStartTime: Double = Date().time / 1000.0 // Convert to seconds
    private var batchStartTime: Double = replayStartTime
    var replayId: String = UUID.randomUUID().toString()

    // Mutex to guarantee serial execution of flush events
    private val flushMutex = Mutex()

    internal fun getDistinctId(): String = flushRequest.distinctId

    internal fun updateDistinctId(newDistinctId: String) {
        flushRequest.updateDistinctId(newDistinctId)
    }

    fun start() {
        seqId = 0
        replayStartTime = Date().time / 1000.0
        batchStartTime = replayStartTime
        replayId = UUID.randomUUID().toString()

        // Skip auto-flush if interval is <=0 (disabled)
        if (flushIntervalMs <= 0L) {
            Logger.info("Auto-flush disabled due to flushInterval <= 0")
            return
        }

        flushJob = lifecycleScope.launch(coroutineDispatcher) {
            while (isActive) {
                delay(flushIntervalMs)
                if (!wifiOnly || networkMonitor.isUsingWiFi) {
                    flushMutex.withLock {
                        flushBatch(false)
                    }
                }
            }
        }
    }

    fun stop() {
        flushJob?.cancel()
        flushJob = null
    }

    /**
     * Flushes events from the queue.
     *
     * This function attempts to send a batch of events to the server.
     * It respects the `wifiOnly` setting, skipping the flush if the device is not on Wi-Fi and `wifiOnly` is true.
     * The flushing process is protected by a mutex to ensure serial execution.
     *
     * @param forAll If true, attempts to flush all events currently in the queue in batches.
     *               If false (default), attempts to flush only one batch of events.
     * @param onComplete A callback function that will be executed on the main thread after the flush attempt
     *                   (regardless of success or failure, or if the flush was skipped due to network conditions).
     */
    fun flushEvents(
        forAll: Boolean = false,
        onComplete: () -> Unit = {}
    ) {
        if (wifiOnly && !networkMonitor.isUsingWiFi) {
            Logger.warn("Device is not using Wi-Fi, skipping flush request")

            lifecycleScope.launch {
                withContext(Dispatchers.Main) { onComplete() }
            }
            return
        }

        lifecycleScope.launch(coroutineDispatcher) {
            flushMutex.withLock {
                flushBatch(forAll)
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    private suspend fun flushBatch(forAll: Boolean) {
        do {
            // Check if we're in exponential backoff before attempting to flush
            if (flushRequest.isInBackoff()) {
                Logger.info("In exponential backoff, stopping flush batch")
                break // Exit the loop early to prevent rapid retries
            }

            val events = eventService.dequeueEvents(50)
            if (events.isEmpty()) {
                return // No events to flush
            }
            val batchStartTime = Date().time / 1000.0
            val payloadInfo = PayloadInfo(
                events,
                batchStartTime,
                seqId,
                replayId,
                ((Date().time / 1000.0) - replayStartTime).toInt() * 1000,
                replayStartTime
            )

            val success = flushRequest.sendRequest(payloadInfo)
            if (success) {
                seqId++
            } else {
                Logger.warn("Failed to flush events to the server.")
                eventService.prependEvents(events)

                // Check again after failure to see if we've entered backoff
                if (forAll && flushRequest.isInBackoff()) {
                    Logger.info("Entered exponential backoff after failed request, stopping flush loop")
                    break
                }
            }
        } while (forAll && !eventService.isEventsEmpty)
    }
}
