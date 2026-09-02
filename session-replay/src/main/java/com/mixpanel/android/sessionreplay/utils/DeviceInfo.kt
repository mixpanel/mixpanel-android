package com.mixpanel.android.sessionreplay.utils

import android.content.Context
import android.content.res.Resources
import android.os.Build

object DeviceInfo {
    // Screen Dimensions in logical pixels (1x density)
    // Scaled to match screenshot capture at 1x density
    val screenWidth: Int
        get() =
            try {
                val metrics = Resources.getSystem().displayMetrics
                if (metrics.density <= 0f) {
                    metrics.widthPixels // Use physical pixels if density is invalid
                } else {
                    (metrics.widthPixels / metrics.density).toInt()
                }
            } catch (e: Exception) {
                0 // Default value in case of error
            }

    val screenHeight: Int
        get() =
            try {
                val metrics = Resources.getSystem().displayMetrics
                if (metrics.density <= 0f) {
                    metrics.heightPixels // Use physical pixels if density is invalid
                } else {
                    (metrics.heightPixels / metrics.density).toInt()
                }
            } catch (e: Exception) {
                0 // Default value in case of error
            }

    // Device Type
    val deviceType: String
        get() =
            try {
                if (screenHeight > screenWidth) {
                    "Phone"
                } else {
                    "Tablet"
                }
            } catch (e: Exception) {
                "Unknown" // Default value in case of error
            }

    // Device Model
    val deviceModel: String
        get() =
            try {
                Build.MODEL
            } catch (e: Exception) {
                "Unknown" // Default value in case of error
            }

    // OS Version
    val osVersion: String
        get() =
            try {
                Build.VERSION.RELEASE
            } catch (e: Exception) {
                "Unknown" // Default value in case of error
            }

    // Host app bundle/package identifier.
    // Requires a Context — Android has no global equivalent of iOS's Bundle.main.
    // Returns null rather than a sentinel so callers can omit the value entirely.
    fun bundleId(context: Context): String? =
        try {
            context.packageName
        } catch (e: Exception) {
            null // Caller omits the value when unavailable
        }

    // Host app build number: versionCode, or longVersionCode on API 28+.
    // This is the Android analog of iOS CFBundleVersion, not versionName.
    fun buildNumber(context: Context): String? =
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toString()
            }
        } catch (e: Exception) {
            null // Caller omits the value when unavailable
        }
}
