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

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = MPSessionReplayConfig(
            token = "YOUR_MIXPANEL_TOKEN"
        )
        MPSessionReplay.initialize(this, config)
    }
}
```

## Requirements

- Android SDK 21+

## License

Apache License 2.0
