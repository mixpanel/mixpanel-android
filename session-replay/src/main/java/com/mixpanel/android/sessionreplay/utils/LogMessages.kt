package com.mixpanel.android.sessionreplay.utils

internal object LogMessages {
    val AUTO_START_RECORDING_DEPRECATED = """
        `autoStartRecording` is deprecated and should be replaced with `recordingSessionsPercent`.
        Remove `autoStartRecording` from your `MPSessionReplayConfig` assignment, and allow the default (true) value to be used.
        If you want to disable automatic recording, set `MPSessionReplayConfig.recordingSessionsPercent` to 0.0.
        This ensures that you are able to dynamically enable/disable auto recording remote configs.
    """.trimIndent()
}
