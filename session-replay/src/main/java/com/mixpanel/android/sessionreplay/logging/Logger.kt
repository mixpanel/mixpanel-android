package com.mixpanel.android.sessionreplay.logging

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

enum class LogLevel { DEBUG, INFO, WARNING, ERROR }

data class LogMessage(
    val file: String,
    val function: String,
    val text: String,
    val level: LogLevel
)

interface Logging {
    fun addMessage(message: LogMessage)
}

object Logger {
    private val loggers = mutableListOf<Logging>()
    private val enabledLevels = mutableSetOf<LogLevel>()
    private val readWriteLock = ReentrantReadWriteLock()

    fun addLogging(logging: Logging) =
        readWriteLock.write {
            loggers.add(logging)
        }

    fun removeLogging(logging: Logging) =
        readWriteLock.write {
            loggers.remove(logging)
        }

    fun enableLevel(level: LogLevel) =
        readWriteLock.write {
            enabledLevels.add(level)
        }

    fun disableLevel(level: LogLevel) =
        readWriteLock.write {
            enabledLevels.remove(level)
        }

    // Logging functions with string argument (eager evaluation)
    internal fun debug(message: String) {
        logIfEnabled(LogLevel.DEBUG, message)
    }

    internal fun info(message: String) {
        logIfEnabled(LogLevel.INFO, message)
    }

    internal fun warn(message: String) {
        logIfEnabled(LogLevel.WARNING, message)
    }

    internal fun error(message: String) {
        logIfEnabled(LogLevel.ERROR, message)
    }

    // Lazy logging functions - message lambda only invoked if level is enabled
    // Use these for expensive string operations to avoid allocation when logging is disabled
    internal inline fun debug(message: () -> String) {
        if (isLevelEnabled(LogLevel.DEBUG)) {
            logIfEnabled(LogLevel.DEBUG, message())
        }
    }

    internal inline fun info(message: () -> String) {
        if (isLevelEnabled(LogLevel.INFO)) {
            logIfEnabled(LogLevel.INFO, message())
        }
    }

    internal inline fun warn(message: () -> String) {
        if (isLevelEnabled(LogLevel.WARNING)) {
            logIfEnabled(LogLevel.WARNING, message())
        }
    }

    internal inline fun error(message: () -> String) {
        if (isLevelEnabled(LogLevel.ERROR)) {
            logIfEnabled(LogLevel.ERROR, message())
        }
    }

    /**
     * Check if a log level is enabled. Use this for conditional logging blocks.
     */
    fun isLevelEnabled(level: LogLevel): Boolean = readWriteLock.read { enabledLevels.contains(level) }

    private fun getCallerDetails(): Pair<String, String> {
        val stackTrace = Thread.currentThread().stackTrace
        // Skip the first couple elements which are related to the Thread's stack trace itself.
        val skipFrames = 2

        // Find the first relevant caller after skipping the initial frames
        val callerElement =
            stackTrace
                .drop(skipFrames)
                .firstOrNull {
                    it.className != Logger::class.java.name &&
                        // Exclude Logger class
                        !it.className.startsWith("kotlin.coroutines") &&
                        // Exclude coroutine internals
                        !it.className.startsWith("kotlinx.coroutines") &&
                        !it.className.startsWith("java.lang.Thread") &&
                        // Exclude Thread class
                        !it.className.contains('$')
                }

        val fileName = callerElement?.fileName ?: "Unknown File"
        val functionName = callerElement?.methodName ?: "Unknown Function"
        return Pair(fileName, functionName)
    }

    private fun logIfEnabled(
        level: LogLevel,
        message: String
    ) {
        val enabled = readWriteLock.read { enabledLevels.contains(level) }
        if (enabled) {
            val (fileName, functionName) = getCallerDetails()
            forwardLogMessage(LogMessage(fileName, functionName, message, level))
        }
    }

    private fun forwardLogMessage(message: LogMessage) =
        readWriteLock.write {
            loggers.forEach { it.addMessage(message) }
        }
}
