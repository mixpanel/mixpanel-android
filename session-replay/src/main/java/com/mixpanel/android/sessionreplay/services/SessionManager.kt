package com.mixpanel.android.sessionreplay.services

import java.util.Date
import java.util.UUID

class SessionManager private constructor() {
    var seqId: Int = 0
    var replayId: String = UUID.randomUUID().toString()
    var replayStartTime: Double = Date().time / 1000.0
    var batchStartTime: Double = replayStartTime
    var hasStarted: Boolean = false
    var hasSensitiveViews: Boolean = false
    var hasSafeViews: Boolean = false

    companion object {
        @JvmStatic
        val shared = SessionManager()
    }

    init {
        // Initialization code (same as in the private constructor)
    }

    fun increaseSeqId() {
        seqId += 1
    }

    fun generateNewSession() {
        seqId = 0
        replayId = UUID.randomUUID().toString()
        replayStartTime = Date().time / 1000.0
        batchStartTime = replayStartTime
        hasStarted = false
        hasSensitiveViews = false
        hasSafeViews = false
    }
}
