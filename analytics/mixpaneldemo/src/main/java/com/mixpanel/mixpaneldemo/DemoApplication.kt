package com.mixpanel.mixpaneldemo

import android.app.Application
import androidx.annotation.OptIn
import com.mixpanel.android.annotation.ExperimentalMixpanelApi
import com.mixpanel.android.autocapture.ClickEvent
import com.mixpanel.android.mpmetrics.AutocaptureOptions
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.android.mpmetrics.MixpanelOptions

class DemoApplication : Application() {
    // Autocapture is experimental: acknowledging that here silences the build warning for this
    // class. Opting in applies to this scope only.
    @OptIn(markerClass = [ExperimentalMixpanelApi::class])
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
