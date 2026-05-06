package com.mixpanel.android.sessionreplay

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver.OnDrawListener
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.mixpanel.android.sessionreplay.debug.DebugMaskOverlayManager
import com.mixpanel.android.sessionreplay.debug.DebugMaskOverlayView
import com.mixpanel.android.sessionreplay.logging.Logger
import com.mixpanel.android.sessionreplay.models.MPSessionReplayConfig
import com.mixpanel.android.sessionreplay.models.RawScreenshotEvent
import com.mixpanel.android.sessionreplay.network.NetworkMonitor
import com.mixpanel.android.sessionreplay.sensitive_views.SensitiveViewManager
import com.mixpanel.android.sessionreplay.services.EventService
import com.mixpanel.android.sessionreplay.services.FlushService
import com.mixpanel.android.sessionreplay.tracking.EventPublisher
import com.mixpanel.android.sessionreplay.tracking.ScreenRecorder
import com.mixpanel.android.sessionreplay.tracking.TouchEventListener
import com.mixpanel.android.sessionreplay.tracking.TouchEventRecorder
import com.mixpanel.android.sessionreplay.utils.LogMessages.AUTO_START_RECORDING_DEPRECATED
import curtains.Curtains
import curtains.OnRootViewsChangedListener
import curtains.OnTouchEventListener
import curtains.phoneWindow
import curtains.touchEventInterceptors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Random
import java.util.Timer
import kotlin.concurrent.timer

class MPSessionReplayInstance(
    private val appContext: Context,
    var token: String,
    distinctId: String,
    var config: MPSessionReplayConfig,
    val sessionReplaySender: SessionReplaySender = SessionReplaySender,
    private val lifecycleScope: CoroutineScope = ProcessLifecycleOwner.get().lifecycleScope,
    private val eventService: EventService = EventService(),
    val flushService: FlushService = FlushService(
        token,
        distinctId,
        config.wifiOnly,
        networkMonitor = NetworkMonitor(appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager),
        lifecycleScope = lifecycleScope,
        flushInterval = config.flushInterval,
        eventService = eventService,
        serverUrl = config.serverUrl
    )
) : TouchEventListener {
    private val minIntervalBetweenScreenshots = 500L // 500ms = 2 times per second max
    private val minimalScreenshotDelayMs = 10L // Minimal delay when screenshot can be captured immediately
    private var lastScreenshotTime = 0L
    private var pendingScreenshotRequest = false

    @Volatile
    private var hasStartedRecording = false

    /**
     * Controls whether event triggers are evaluated (default: true).
     * When paused, events from the Mixpanel SDK will not trigger recording.
     */
    @Volatile
    private var isEventTriggersEnabled = true

    private val touchEventRecorder = TouchEventRecorder(appContext, this)
    private val debugMaskOverlayManager: DebugMaskOverlayManager? =
        config.debugOptions?.overlayColors?.let { colors ->
            DebugMaskOverlayManager.create(appContext, colors)
        }

    private val onDrawListener = OnDrawListener {
        // Signal that content may have changed (used to skip revalidation when static)
        ScreenRecorder.shared.contentMayHaveChanged = true
        scheduleScreenshotCapture()
    }
    private val onTouchEventListener = OnTouchEventListener { motionEvent -> onTouch(motionEvent) }
    private var onRootViewsChangedListener = OnRootViewsChangedListener { view, added ->
        // Skip debug overlay windows to avoid infinite screenshot loops
        if (view is DebugMaskOverlayView) return@OnRootViewsChangedListener
        if (added) onViewAdded(view) else onViewRemoved(view)
    }

    private fun setupViewCallbacks(view: View) {
        view.viewTreeObserver?.isAlive?.let { isAlive ->
            if (isAlive) {
                view.viewTreeObserver.addOnDrawListener(onDrawListener)
            }
        }

        view.phoneWindow?.let { it.touchEventInterceptors += onTouchEventListener }

        // calling this method because the views brought to front from the stack may not
        // trigger a screenshot unless the user interact with it. so, this will trigger
        // a screenshot capture for those views.
        scheduleScreenshotCapture()
    }

    private fun cleanupViewCallbacks(view: View) {
        view.viewTreeObserver?.isAlive?.let { isAlive ->
            if (isAlive) {
                view.viewTreeObserver.removeOnDrawListener(onDrawListener)
            }
        }

        view.phoneWindow?.let { it.touchEventInterceptors -= onTouchEventListener }
    }

    private fun onViewAdded(view: View) {
        synchronized(this) {
            // setup callbacks for the new view
            setupViewCallbacks(view)
        }
    }

    private fun onViewRemoved(view: View) {
        synchronized(this) {
            cleanupViewCallbacks(view)
            // When a view is removed, we should schedule a screen capture
            scheduleScreenshotCapture()
        }
    }

    private fun onTouch(motionEvent: MotionEvent) {
        if (hasStartedRecording) touchEventRecorder.onTouchEvent(motionEvent)
    }

    private var initialScreenshotCaptured = false
    private var recordTimer: Timer? = null
    private var pendingScreenshotJob: Job? = null

    private val appLifecycleObserver: DefaultLifecycleObserver
        get() {
            val observer = object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    appDidEnterBackground()
                }

                override fun onStart(owner: LifecycleOwner) {
                    autoStartRecording()
                }
            }
            return observer
        }

    init {
        Logger.info("Initializing Session Replay with token: $token, distinctId: $distinctId")
        Logger.info("Session Replay Config: $config")

        SensitiveViewManager.autoMaskedViews = config.autoMaskedViews

        Curtains.onRootViewsChangedListeners += onRootViewsChangedListener
        Curtains.rootViews.forEach { rootView ->
            // Setup callbacks for each existing root view
            setupViewCallbacks(rootView)
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            appLifecycleObserver
        )

        eventService.initialize()

        // Set up debug mask overlay listener if enabled
        debugMaskOverlayManager?.let { manager ->
            SensitiveViewManager.setMaskRegionsListener { entries ->
                manager.updateMaskEntries(entries)
            }
        }
    }

    /**
     * De-initializes the Session Replay instance.
     */
    fun deinitialize() {
        Logger.info("Session Replay pre-initialization cleanup called")

        // Cleanup event collection in SessionReplayManager
        SessionReplayManager.cleanupEventCollection()

        Curtains.onRootViewsChangedListeners -= onRootViewsChangedListener
        Curtains.rootViews.forEach { rootView ->
            if (rootView !is DebugMaskOverlayView) {
                cleanupViewCallbacks(rootView)
            }
        }

        // Disable and clean up debug mask overlay
        debugMaskOverlayManager?.disable()

        SensitiveViewManager.deinitialize()

        ProcessLifecycleOwner.get().lifecycle.removeObserver(
            appLifecycleObserver
        )

        // Ensure flush service is stopped if still running
        if (hasStartedRecording) {
            stopRecording()
        }

        pendingScreenshotJob?.cancel()
        pendingScreenshotJob = null

        flushService.stop()

        eventService.deinitialize()

        // Final cleanup of bitmap pool
        ScreenRecorder.shared.clearBitmapPool()
    }

    /**
     * Uploads all locally stored events to Mixpanel.
     *
     * @param onComplete Optional callback that will be called when the flush operation is complete.
     */
    fun flush(onComplete: () -> Unit = {}) {
        flushService.flushEvents(forAll = true, onComplete = onComplete)
    }

    internal fun getContext(): Context = appContext

    fun getReplayId(): String = flushService.replayId

    fun getDistinctId(): String = flushService.getDistinctId()

    fun isRecording(): Boolean = hasStartedRecording

    fun identify(distinctId: String) {
        flushService.updateDistinctId(distinctId)
    }

    fun addSensitiveView(view: View) {
        SensitiveViewManager.addSensitiveView(view)
    }

    fun removeSensitiveView(view: View) {
        SensitiveViewManager.removeSensitiveView(view)
    }

    fun addSafeView(view: View) {
        SensitiveViewManager.addSafeView(view)
    }

    fun removeSafeView(view: View) {
        SensitiveViewManager.removeSafeView(view)
    }

    fun addSensitiveClass(aClass: Class<*>) {
        SensitiveViewManager.addSensitiveClass(aClass)
    }

    fun removeSensitiveClass(aClass: Class<*>) {
        SensitiveViewManager.removeSensitiveClass(aClass)
    }

    /**
     * Pauses event-triggered recording.
     *
     * When disabled, the SDK will ignore events from the Mixpanel SDK that would normally
     * trigger recording based on configured event triggers. This does not affect:
     * - Manual recording via `startRecording()` / `stopRecording()`
     * - Bridge registration (handler remains registered)
     * - Settings parsing or remote configuration
     *
     * Note: This is a global setting that affects all event triggers of session replay.
     */
    fun disableEventTriggers() {
        isEventTriggersEnabled = false
        Logger.info("[SessionReplay] Event triggers disabled")
    }

    /**
     * Enables event-triggered recording.
     *
     * Re-enables processing of events from the Mixpanel SDK for triggering recording.
     * Event triggers will evaluate normally after calling this method.
     *
     * Note: Event triggers are enabled by default on initialization.
     */
    fun enableEventTriggers() {
        isEventTriggersEnabled = true
        Logger.info("[SessionReplay] Event triggers enabled")
    }

    /**
     * Returns whether event triggers are currently enabled.
     *
     * @return true if event triggers are enabled, false if paused
     */
    fun isEventTriggersEnabled(): Boolean = isEventTriggersEnabled

    /**
     * Automatically starts recording session replays based on the configured percentage.
     * If the percentage is 0, it will not start recording.
     */
    fun autoStartRecording() {
        @Suppress("DEPRECATION")
        if (config.autoStartRecording) {
            val recordingSessionsPercent = config.recordingSessionsPercent
            if (recordingSessionsPercent > 0) {
                Logger.info("Session replay auto-start recording is enabled. Sampling rate: $recordingSessionsPercent%")
                startRecording(recordingSessionsPercent)
            } else {
                Logger.info("Session replay auto-start recording is disabled. Sampling rate: $recordingSessionsPercent%")
            }
        } else {
            Logger.info("Session replay auto-start recording is disabled.")
            Logger.warn(AUTO_START_RECORDING_DEPRECATED)
        }
    }

    /**
     * Starts recording session replays.
     *
     * @param sessionsPercent The percentage of sessions to record. Defaults to 100%.
     */
    fun startRecording(sessionsPercent: Double = 100.0) {
        if (hasStartedRecording) {
            return
        }

        if (sessionsPercent > 0 && Random().nextDouble() * 100 <= sessionsPercent) {
            Logger.info("Session replay recording started! Sampling rate: $sessionsPercent%")
            hasStartedRecording = true

            // Enable debug mask overlay if configured
            debugMaskOverlayManager?.enable()

            scheduleScreenshotCapture()
            flushService.start()
            sessionReplaySender.registerSessionReplay(
                appContext,
                mapOf("\$mp_replay_id" to getReplayId())
            )
        } else {
            Logger.info("Session replay recording not started due to sampling rate ($sessionsPercent%)")
        }
    }

    private suspend fun captureScreenshot(initial: Boolean): Boolean {
        var captured = false
        // Filter out debug overlay windows — they have their own surface and
        // must not be selected as the capture target.
        val rootViews = Curtains.rootViews
        rootViews.lastOrNull { it !is DebugMaskOverlayView }?.let { view ->
            val fullScreenView = rootViews.firstOrNull { it !is DebugMaskOverlayView }
            val screenshotVariant = if (initial) "initial" else "incremental"
            val beforeCapture = System.currentTimeMillis()
            val screenshot = ScreenRecorder.shared.captureScreenshot(view, fullScreenView)
            Logger.info("Captured $screenshotVariant screenshot in ${System.currentTimeMillis() - beforeCapture}ms")

            screenshot?.let {
                EventPublisher.shared.publishSessionEvent(RawScreenshotEvent(it, initial))
                Logger.info("Published $screenshotVariant screenshot event in ${System.currentTimeMillis() - beforeCapture}ms")
                captured = true
            }

            // reset time even if we discarded screenshot (ex: rapid scroll)
            // If we don't do this, we could be spamming non-stop attempts which is resource heavy
            lastScreenshotTime = System.currentTimeMillis()
        }
        return captured
    }

    fun stopRecording() {
        hasStartedRecording = false
        initialScreenshotCaptured = false

        // Disable debug mask overlay
        debugMaskOverlayManager?.disable()

        flushService.stop()
        flushService.flushEvents(forAll = true)
        sessionReplaySender.unregisterSessionReplay(appContext, "\$mp_replay_id")

        // Clear bitmap pool to free memory
        ScreenRecorder.shared.clearBitmapPool()
    }

    override fun onTouchStart() {
        if (recordTimer != null) return
        recordTimer = timer(period = 150L) {
            scheduleScreenshotCapture()
        }
    }

    override fun onTouchEnd() {
        recordTimer?.cancel()
        recordTimer = null
    }

    private fun appDidEnterBackground() {
        stopRecording()
    }

    private fun scheduleScreenshotWithDelay(delayMs: Long) {
        pendingScreenshotRequest = true
        pendingScreenshotJob?.cancel()
        pendingScreenshotJob = lifecycleScope.launch {
            delay(delayMs)
            Logger.info("View hierarchy changed or touch event, capturing screenshot")
            // TODO: Refactor incremental screenshot usage or remove it
            //  only capture initial aka "main" screenshots for now
            captureScreenshot(initial = true)
            pendingScreenshotRequest = false
        }
    }

    private fun scheduleScreenshotCapture() {
        if (!hasStartedRecording) {
            return
        }

        if (pendingScreenshotRequest) {
            return // Already have a pending screenshot
        }

        if (!initialScreenshotCaptured) {
            pendingScreenshotRequest = true
            lifecycleScope.launch {
                initialScreenshotCaptured = captureScreenshot(initial = true)
                pendingScreenshotRequest = false
            }
            return
        }

        val currentTime = System.currentTimeMillis()
        val timeElapsedSinceLastScreenshot = currentTime - lastScreenshotTime

        val delayMs = if (timeElapsedSinceLastScreenshot >= minIntervalBetweenScreenshots) {
            minimalScreenshotDelayMs // Can capture now, minimal delay
        } else {
            val calculatedDelay = minIntervalBetweenScreenshots - timeElapsedSinceLastScreenshot
            Logger.info("A screenshot will be captured in $calculatedDelay millis")
            calculatedDelay
        }

        scheduleScreenshotWithDelay(delayMs)
    }
}

object SessionReplaySender {
    private const val REGISTER_ACTION = "com.mixpanel.properties.register"
    private const val UNREGISTER_ACTION = "com.mixpanel.properties.unregister"

    fun registerSessionReplay(
        context: Context,
        data: Map<String, Any>? = null
    ) {
        Logger.info("Registering session replay")
        val intent = Intent(REGISTER_ACTION)
        data?.let { intent.putExtra("data", HashMap(it)) }
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    fun unregisterSessionReplay(
        context: Context,
        key: String
    ) {
        Logger.info("Unregistering session replay")
        val intent = Intent(UNREGISTER_ACTION)
        intent.putExtra(key, "")
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }
}
