package com.mixpanel.android.sessionreplay.utils

internal object LogMessages {
    val AUTO_START_RECORDING_DEPRECATED = """
        `autoStartRecording` is deprecated and should be replaced with `recordingSessionsPercent`.
        Remove `autoStartRecording` from your `MPSessionReplayConfig` assignment, and allow the default (true) value to be used.
        If you want to disable automatic recording, set `MPSessionReplayConfig.recordingSessionsPercent` to 0.0.
        This ensures that you are able to dynamically enable/disable auto recording remote configs.
    """.trimIndent()

    /**
     * Printed once per `initialize` when an app turns wireframes on, in debuggable builds only.
     *
     * The ask is verification. An integrator who has just turned wireframes on is pointed at the
     * debug emitter and asked to confirm, against their own expectations of what may leave the
     * device, that nothing sensitive is captured — while it is still cheap to find out.
     */
    const val WIREFRAMES_BETA_NOTICE =
        "Wireframes enabled (beta). Before shipping to production, inspect the wireframes your " +
            "app produces with the wireframe debug emitter and confirm that no sensitive " +
            "information is captured."
}
