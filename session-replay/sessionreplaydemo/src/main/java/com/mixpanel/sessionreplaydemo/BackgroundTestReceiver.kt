package com.mixpanel.sessionreplaydemo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * A broadcast receiver to test background initialization scenario.
 * This simulates what happens when a push notification wakes up the app in the background.
 *
 * To test the deferred initialization behavior:
 *
 * Step 1: Fully stop the app and clear state
 *   adb shell am force-stop com.mixpanel.sessionreplaydemo
 *   adb shell pm clear com.mixpanel.sessionreplaydemo
 *
 * Step 2: Send broadcast to wake app in background
 *   adb shell am broadcast -a com.mixpanel.sessionreplaydemo.BACKGROUND_TEST com.mixpanel.sessionreplaydemo
 *
 * Step 3: Watch logs - you should see:
 *   ✅ "Waiting for app to enter foreground before continuing initialization"
 *   ✅ You should NOT see "Checking recording settings for project" (no network call)
 *
 * Step 4: Launch the app to foreground
 *   adb shell am start -n com.mixpanel.sessionreplaydemo/.MainActivity
 *
 * Step 5: Watch logs - you should now see:
 *   ✅ "App entered foreground, continuing initialization"
 *   ✅ "Checking recording settings for project" (network call happens NOW)
 *
 * Monitor logs with:
 *   adb logcat -s BackgroundTestReceiver:D SessionReplay:D MPSessionReplay:D SettingsService:D
 */
class BackgroundTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BackgroundTestReceiver", "Broadcast received")
    }
}
