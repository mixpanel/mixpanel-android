package com.mixpanel.android.customoperators

import androidx.annotation.RestrictTo
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * RFC 3339 timestamp handling for date targeting.
 *
 * Both sides resolve to whole seconds: sub-second precision is deliberately discarded so that a
 * target carrying an end-of-day `.999` still matches a subject sitting on the last whole second.
 *
 * Kept free of any JsonLogic type so the same conversion can back either engine.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object Rfc3339 {
    // Strict RFC3339 guard for datetime strings.
    private val RFC3339 =
        Regex(
            "^(\\d{4})-(\\d{2})-(\\d{2})[Tt](\\d{2}):(\\d{2}):(\\d{2})" +
                "(\\.\\d+)?([Zz]|[+-]\\d{2}:\\d{2})$"
        )

    // Epoch milliseconds are compared as a Long, so anything at or beyond this is out of range.
    private val MAX_EPOCH_MS = Long.MAX_VALUE.toDouble()

    /**
     * Converts an RFC 3339 timestamp to whole seconds since the epoch, or null when the string is
     * not a well-formed timestamp.
     */
    @JvmStatic
    fun toUnixSeconds(raw: String): Long? {
        val match = RFC3339.matchEntire(raw.trim().uppercase()) ?: return null
        val year = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val day = match.groupValues[3].toInt()
        val hour = match.groupValues[4].toInt()
        val minute = match.groupValues[5].toInt()
        val second = match.groupValues[6].toInt()

        var offsetSeconds = 0L
        val offset = match.groupValues[8]
        if (offset != "Z") {
            val offsetHours = offset.substring(1, 3).toInt()
            val offsetMinutes = offset.substring(4, 6).toInt()
            // The pattern only guarantees two digits either side of the colon, so the values still
            // have to be real clock offsets.
            if (offsetHours > 23 || offsetMinutes > 59) {
                return null
            }
            val sign = if (offset[0] == '-') -1 else 1
            offsetSeconds = sign * (offsetHours * 3600L + offsetMinutes * 60L)
        }

        val calendar: Calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        // A lenient calendar rolls an impossible date such as 2026-02-30 forward into a real instant,
        // so a malformed property would match a date rule instead of failing closed.
        calendar.isLenient = false
        calendar.set(year, month - 1, day, hour, minute, second)
        val wallSeconds =
            try {
                calendar.timeInMillis / 1000L
            } catch (e: IllegalArgumentException) {
                return null
            }
        return wallSeconds - offsetSeconds
    }

    /**
     * Converts an epoch-milliseconds target to whole seconds, or null when it is not a real instant.
     */
    @JvmStatic
    fun epochMillisToUnixSeconds(millis: Double): Long? {
        // A value Long cannot represent is not a real timestamp; narrowing one would saturate into a
        // finite bound and let a nonsense target define a rollout window.
        if (millis.isNaN() || millis >= MAX_EPOCH_MS || millis <= -MAX_EPOCH_MS) {
            return null
        }
        return millis.toLong() / 1000L
    }
}
