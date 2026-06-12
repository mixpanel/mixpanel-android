package com.mixpanel.mixpaneldemo

import android.app.Application
import com.mixpanel.android.mpmetrics.AutocaptureOptions
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.android.mpmetrics.MixpanelOptions

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Mixpanel with autocapture enabled
        val autocaptureOptions = AutocaptureOptions.Builder().build()

        val options = MixpanelOptions.Builder()
            .autocaptureOptions(autocaptureOptions)
            .build()

        val mixpanel = MixpanelAPI.getInstance(this, MIXPANEL_PROJECT_TOKEN, true, options)
        mixpanel.setEnableLogging(true)

    }
}
