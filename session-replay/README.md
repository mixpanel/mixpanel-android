# Mixpanel Android Session Replay SDK

The official Mixpanel Session Replay SDK for Android.

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
implementation("com.mixpanel.android:mixpanel-android-session-replay:1.3.0")
```

## Usage

Initialize the Session Replay SDK in your Application class:

```kotlin
import com.mixpanel.android.sessionreplay.MPSessionReplay
import com.mixpanel.android.sessionreplay.models.MPSessionReplayConfig
import com.mixpanel.android.sessionreplay.sensitive_views.AutoMaskedView

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val token = "YOUR_PROJECT_TOKEN"
        val distinctId = "YOUR_DISTINCT_ID"

        // Configure Session Replay
        val config = MPSessionReplayConfig(
            wifiOnly = false,
            autoMaskedViews = mutableSetOf(AutoMaskedView.Text),
            enableLogging = true
        )

        // Initialize Session Replay
        MPSessionReplay.initialize(
            this,
            token,
            distinctId,
            config
        ) { result ->
            result.fold(
                onSuccess = { instance ->
                    // Session Replay initialized successfully
                },
                onFailure = { error ->
                    // Handle initialization error
                }
            )
        }
    }
}
```

## Requirements

- Android SDK 21+

## License

Apache License 2.0
