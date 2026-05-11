package com.mixpanel.sessionreplaydemo

import android.app.Application
import android.content.Context
import android.util.Log
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.android.sessionreplay.MPSessionReplay
import com.mixpanel.android.sessionreplay.MPSessionReplayError
import com.mixpanel.android.sessionreplay.MPSessionReplayInstance
import com.mixpanel.android.sessionreplay.debug.DebugOptions
import com.mixpanel.android.sessionreplay.debug.DebugOverlayColors
import com.mixpanel.android.sessionreplay.models.MPSessionReplayConfig
import com.mixpanel.android.sessionreplay.models.RemoteSettingsMode
import com.mixpanel.android.sessionreplay.sensitive_views.AutoMaskedView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

class MainApplication : Application() {
    private val applicationScope = MainScope()

    override fun onCreate() {
        super.onCreate()
        // Launch to run immediately on current (main) thread
        applicationScope.launch(Dispatchers.Main.immediate) {
            try {
                initializeMixpanel(this@MainApplication)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Mixpanel", e)
            }
        }
    }

    companion object {
        const val TAG = "MainApplication"
        suspend fun initializeMixpanel(
            context: Context
        ): MPSessionReplayInstance? = suspendCoroutine { continuation: Continuation<MPSessionReplayInstance?> ->
            val token = Constants.MIXPANEL_TOKEN
            val trackAutomaticEvents = true
            val mixpanel = MixpanelAPI.getInstance(context, token, trackAutomaticEvents)
            mixpanel.identify("distinctId")

            val props = JSONObject()
            props.put("source", "Pat's affiliate site")
            props.put("Opted out of email", true)

            mixpanel.track("Sign Up", props)

            val config = MPSessionReplayConfig(
                wifiOnly = false,
                autoMaskedViews = mutableSetOf(AutoMaskedView.Text),
                enableLogging = true,
                autoStartRecording = false,
                remoteSettingsMode = RemoteSettingsMode.FALLBACK,
                debugOptions = DebugOptions(overlayColors = DebugOverlayColors())
            )

            MPSessionReplay.initialize(
                context,
                token,
                mixpanel.distinctId,
                config
            ) { result ->
                result.fold(
                    onSuccess = { instance ->
                        Log.d("SessionReplay", "Session Replay initialized successfully")
                        continuation.resumeWith(Result.success(instance))
                    },
                    onFailure = { error ->
                        when (error) {
                            is MPSessionReplayError.Disabled -> {
                                Log.d("SessionReplay", "Session Replay disabled: ${error.reason}")
                            }

                            is MPSessionReplayError.InitializationError -> {
                                Log.e(
                                    "SessionReplay",
                                    "Session Replay initialization failed",
                                    error.cause
                                )
                            }

                            else -> {
                                Log.e(
                                    "SessionReplay",
                                    "Session Replay initialization failed",
                                    error
                                )
                            }
                        }
                        continuation.resumeWith(Result.failure(error))
                    }
                )
            }
        }
    }
}
