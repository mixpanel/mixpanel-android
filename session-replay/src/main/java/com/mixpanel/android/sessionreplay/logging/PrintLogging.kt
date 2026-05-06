package com.mixpanel.android.sessionreplay.logging

import android.util.Log

class PrintLogging : Logging {
    override fun addMessage(message: LogMessage) {
        println("[Mixpanel Session Replay - ${message.file} - func ${message.function}] (${message.level.name}) - ${message.text}")
    }
}

class PrintDebugLogging : Logging {
    override fun addMessage(message: LogMessage) {
        // On Android, Log.d() provides similar functionality to debugPrint() in Swift
        Log.d(
            "MixpanelSessionReplay",
            "[Mixpanel Session Replay - ${message.file} - func ${message.function}] (${message.level.name}) - ${message.text}"
        )
    }
}
