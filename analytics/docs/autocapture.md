# Android Autocapture

Autocapture automatically tracks user interactions in your Android app without requiring manual instrumentation.

## Overview

Autocapture captures three types of events:

| Event | Name | Description |
|-------|------|-------------|
| Click | `$mp_click` | Fired when a user taps any element |
| Rage Click | `$mp_rage_click` | Fired when a user taps rapidly (4+ times) in the same area |
| Dead Click | `$mp_dead_click` | Fired when a tap produces no visible UI response |

**Privacy:** Autocapture is designed with privacy in mind. No personally identifiable information (PII) is captured by default.

## Quick Start

Autocapture is **disabled by default**. Enable it by providing `AutocaptureOptions` during SDK initialization:

### Kotlin

```kotlin
import com.mixpanel.android.mpmetrics.AutocaptureOptions
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.android.mpmetrics.MixpanelOptions

val autocaptureOptions = AutocaptureOptions.Builder().build()

val options = MixpanelOptions.Builder()
    .autocaptureOptions(autocaptureOptions)
    .build()

val mixpanel = MixpanelAPI.getInstance(context, "YOUR_TOKEN", true, options)
```

### Java

```java
import com.mixpanel.android.mpmetrics.AutocaptureOptions;
import com.mixpanel.android.mpmetrics.MixpanelAPI;
import com.mixpanel.android.mpmetrics.MixpanelOptions;

AutocaptureOptions autocaptureOptions = new AutocaptureOptions.Builder().build();

MixpanelOptions options = new MixpanelOptions.Builder()
    .autocaptureOptions(autocaptureOptions)
    .build();

MixpanelAPI mixpanel = MixpanelAPI.getInstance(context, "YOUR_TOKEN", true, options);
```

That's it! No additional setup required. Autocapture automatically intercepts all touch events.

## Configuration Options

### ClickOptions

| Option | Default | Description |
|--------|---------|-------------|
| `enabled` | `true` | Track all click events |

### RageClickOptions

| Option | Default | Description |
|--------|---------|-------------|
| `enabled` | `true` | Track rage click events |
| `clickThreshold` | `4` | Number of clicks required to trigger |
| `timeWindowMs` | `1000` | Time window in milliseconds |
| `radius` | `44` | Spatial threshold in dp (density-independent pixels) |

### DeadClickOptions

| Option | Default | Description |
|--------|---------|-------------|
| `enabled` | `true` | Track dead click events |
| `timeoutMs` | `500` | Response wait time in milliseconds |
| `baselineDelayMs` | `150` | Delay before capturing baseline snapshot |

### Custom Configuration Example

#### Kotlin

```kotlin
val autocaptureOptions = AutocaptureOptions.Builder()
    .clickOptions(ClickOptions.Builder().enabled(true).build())
    .rageClickOptions(
        RageClickOptions.Builder()
            .enabled(true)
            .clickThreshold(5)        // Require 5 clicks instead of 4
            .timeWindowMs(800)        // Shorter time window
            .radius(50f)              // Larger radius
            .build()
    )
    .deadClickOptions(
        DeadClickOptions.Builder()
            .enabled(false)           // Disable dead click detection
            .build()
    )
    .build()

val options = MixpanelOptions.Builder()
    .autocaptureOptions(autocaptureOptions)
    .build()
```

#### Java

```java
AutocaptureOptions autocaptureOptions = new AutocaptureOptions.Builder()
    .clickOptions(new ClickOptions.Builder().enabled(true).build())
    .rageClickOptions(
        new RageClickOptions.Builder()
            .enabled(true)
            .clickThreshold(5)        // Require 5 clicks instead of 4
            .timeWindowMs(800)        // Shorter time window
            .radius(50f)              // Larger radius
            .build()
    )
    .deadClickOptions(
        new DeadClickOptions.Builder()
            .enabled(false)           // Disable dead click detection
            .build()
    )
    .build();

MixpanelOptions options = new MixpanelOptions.Builder()
    .autocaptureOptions(autocaptureOptions)
    .build();
```

## Event Properties

All autocapture events include these properties:

| Property | Description |
|----------|-------------|
| `$x` | Touch X coordinate (screen pixels) |
| `$y` | Touch Y coordinate (screen pixels) |
| `$el_id` | Element identifier (see resolution rules below) |
| `$el_tag_name` | Class name of the view (e.g., `Button`, `TextView`) |
| `$attr-aria-label` | Content description (accessibility label) |
| `$attr-role` | Element role (Button, Switch, etc.) |
| `$elements` | View hierarchy string (max 5 levels) |

### Rage Click Additional Properties

| Property | Description |
|----------|-------------|
| `$tap_count` | Number of taps in the rage click sequence |

## Element Identification (`$el_id`)

The `$el_id` property uses the following resolution order:

### Resolution Order

1. `contentDescription` (if non-empty)
2. Resource ID name (e.g., `R.id.checkout_button` → `"checkout_button"`)
3. `ClassName_view_<hashCode>` (fallback)

### Best Practices

#### XML Views

```xml
<!-- Recommended: Set contentDescription for reliable tracking -->
<Button
    android:id="@+id/checkout_button"
    android:contentDescription="checkout_button"
    android:text="Checkout"
    android:onClick="onCheckoutClick"/>

<!-- Alternative: Use meaningful resource IDs -->
<Button
    android:id="@+id/checkout_button"
    android:text="Checkout"
    android:onClick="onCheckoutClick"/>
```

#### Jetpack Compose

```kotlin
// Recommended: Set semantics for reliable tracking
Button(
    onClick = { /* ... */ },
    modifier = Modifier.semantics {
        contentDescription = "checkout_button"
    }
) {
    Text("Checkout")
}

// Alternative: Use testTag (maps to contentDescription)
Button(
    onClick = { /* ... */ },
    modifier = Modifier.testTag("checkout_button")
) {
    Text("Checkout")
}
```

## Compose Support

Autocapture has **full support** for Jetpack Compose UI. The SDK uses Compose's native semantic APIs to extract element information.

### How It Works

1. Touch events are intercepted via `Window.Callback`
2. Compose semantic nodes are traversed using `SemanticsOwner`
3. Element properties extracted from `SemanticsConfiguration`

### Compose Semantics Mapping

| Compose Semantics | Maps To |
|-------------------|---------|
| `contentDescription` / `testTag` | `$el_id` |
| `role` | `$attr-role` |
| Semantic flags | Element type detection |

### Example

```kotlin
@Composable
fun CheckoutButton() {
    Button(
        onClick = { processCheckout() },
        modifier = Modifier.semantics {
            contentDescription = "checkout_button"  // → $el_id
            role = Role.Button                       // → $attr-role: "Button"
        }
    ) {
        Text("Checkout")
    }
}
```

## Dead Click Detection

Dead click detection monitors interactive elements for UI response:

### How It Works

1. User taps an element with click handlers
2. Wait 150ms for animations to settle (baseline delay)
3. Capture a snapshot of the UI state (view count, content hash, window count)
4. Wait until 500ms total (timeout)
5. If UI hasn't changed, emit `$mp_dead_click`

### Excluded Controls

These controls are excluded from dead click detection because they always produce a visual response when tapped (inherent feedback). They still emit `$mp_click` events.

**Android (XML Views):**
- `EditText` - Keyboard appears
- `Switch` / `CompoundButton` / `Checkbox` / `RadioButton` / `ToggleButton` - Toggles own state
- `SeekBar` - Thumb moves
- `Spinner` - Dropdown opens
- `DatePicker` / `NumberPicker` - Picker UI appears

**Android (Compose):**
- `TextField` / `BasicTextField` - Keyboard appears
- `Switch` / `Checkbox` / `RadioButton` - Toggles own state
- `Slider` - Thumb moves

**iOS (UIKit):**
- `UITextField` / `UITextView` - Keyboard appears
- `UISwitch` - Toggles own state
- `UISlider` - Thumb moves
- `UIStepper` - Value changes
- `UISegmentedControl` - Selection changes
- `UIDatePicker` / `UIPickerView` - Picker UI appears

**iOS (SwiftUI):**
- `TextField` / `TextEditor` / `SecureField` - Keyboard appears
- `Toggle` - Toggles own state
- `Slider` / `Stepper` - Value changes
- `Picker` / `DatePicker` - Picker UI appears

### What Counts as UI Change

- View count change (new views added/removed)
- Content change (text, button states, etc.)
- Window count change (alerts, dialogs, bottom sheets)
- Navigation events (Activity changes)

**Note:** Text input controls (`EditText`, `UITextField`, `TextField`, etc.) are fully excluded from dead click monitoring, so the keyboard appearing after a tap does not produce a false `$mp_dead_click`.

## Multi-Window Support

Autocapture tracks touches on **all windows** in your app using `WindowSpy`:

| Window Type | Tracked |
|-------------|---------|
| Activity | ✅ |
| AlertDialog | ✅ |
| BottomSheetDialog | ✅ |
| PopupWindow | ✅ |
| PopupMenu | ✅ |
| Spinner dropdown | ✅ |
| DatePickerDialog | ✅ |
| TimePickerDialog | ✅ |
| Toast | ⚠️ Detected but not interactive |

### How It Works

`WindowSpy` hooks into Android's internal `WindowManagerGlobal` to detect when windows are added or removed. This allows autocapture to track clicks on dialogs, popups, and other overlays without any manual setup.

**ProGuard Note:** The SDK includes ProGuard rules to preserve `WindowManagerGlobal` reflection:

```proguard
-keep class android.view.WindowManagerGlobal { *; }
```

## Signal UI Change API

For JS-based navigation frameworks (React Native) or custom navigation that bypasses standard Activity lifecycle, use `signalUIChange()` to prevent false dead click positives:

```kotlin
// Kotlin
mixpanel.signalUIChange()

// Java
mixpanel.signalUIChange();
```

This cancels any pending dead click detection by notifying the SDK that a UI change occurred.

## Privacy Considerations

### What is Captured

- Touch coordinates
- View class names and hierarchy
- Content descriptions (accessibility labels)
- Resource ID names

### What is NOT Captured

Autocapture does not capture visible text content (`$el_text`) from tapped elements. Tracking text can be invasive and raise privacy concerns. Additionally, the complexity of nested view hierarchies can cause text extraction to capture content from unintended views — for example, tapping a container layout might extract text from a deeply nested label that isn't semantically related to the tap. The remaining captured properties (`$el_id`, `$el_tag_name`, `$attr-aria-label`, `$attr-role`, `$elements`) are purely structural UI metadata.

## Platform Support

| Platform | Support | Notes |
|----------|---------|-------|
| Android 5.0+ (API 21) | Full | Minimum SDK version |
| XML Views | Full | Traditional Android UI |
| Jetpack Compose | Full | Native Compose semantics support |
| React Native | Full | Via bridge to native SDK |
| Flutter | Partial | Native SDK sees only FlutterView |

## Requirements

- Min SDK: 21 (Android 5.0)
- Compile SDK: 34
- Java: 17 (source/target compatibility)
- No additional dependencies required

## Troubleshooting

### Enable Debug Logging

```kotlin
// Kotlin
mixpanel.setEnableLogging(true)

// Java
mixpanel.setEnableLogging(true);
```

This will log autocapture events to Logcat:

```
MP.AutocaptureManager: emitted $mp_click for checkout_button
MP.AutocaptureManager: emitted $mp_rage_click for submit_btn (count: 4)
MP.AutocaptureManager: emitted $mp_dead_click for broken_link
```

### Verify Events in Dashboard

1. Enable logging as shown above
2. Trigger interactions in your app
3. Check Logcat for autocapture events
4. Check the Mixpanel Live View for events appearing with names `$mp_click`, `$mp_rage_click`, `$mp_dead_click`

### Common Issues

**Events not appearing:**
- Verify `AutocaptureOptions` is passed to `MixpanelOptions`
- Ensure autocapture is enabled (check `isEnabled()` returns true)
- Verify network connectivity and event flushing

**Compose elements showing hash IDs:**
- Set `contentDescription` or `testTag` on interactive elements
- Use `Modifier.semantics { contentDescription = "..." }`

**False positive dead clicks:**
- Element may have a handler that doesn't produce visible UI change
- Use `signalUIChange()` for custom navigation

**Missing clicks on dialogs/popups:**
- WindowSpy should handle this automatically
- Check ProGuard rules are applied (if using ProGuard)
- Verify `WindowManagerGlobal` is not obfuscated

## Technical Details

### Touch Interception

Autocapture uses `Window.Callback` wrapping to intercept touch events:

1. `WindowSpy` detects all root view additions (Activities, Dialogs, etc.)
2. For each window, a `TouchInterceptor` wraps the existing `Window.Callback`
3. On `dispatchTouchEvent`, the interceptor:
   - Lets the event propagate normally (non-invasive)
   - Captures `ACTION_UP` events for processing
   - Extracts semantic information from the tapped view

This approach:
- Requires zero customer setup
- Captures all windows (main, alerts, sheets, popups)
- Works with both XML Views and Jetpack Compose
- Non-blocking (< 5ms overhead per touch)

### Thread Safety

All autocapture components use thread-safe patterns:
- Synchronized access to shared state
- WeakHashMap for window tracking (prevents memory leaks)
- Main thread for UI operations
- Background thread for event emission

### Performance

Target performance budgets:
- Touch event processing: < 5ms
- Semantic extraction: < 10ms
- Dead click snapshot: < 15ms

Actual overhead is minimal and should not be noticeable in production apps.

### Memory Management

- `WeakReference` for Activity tracking (prevents leaks)
- `WeakHashMap` for window interceptors (auto-cleanup)
- Automatic cleanup on Activity pause
- No persistent state across sessions

## Migration from Manual Tracking

If you're currently using manual `track()` calls for clicks, you can gradually migrate:

1. Enable autocapture alongside existing tracking
2. Compare event counts in dashboard
3. Set meaningful `contentDescription` or resource IDs for important elements
4. Identify and remove redundant manual `track()` calls
5. Keep manual tracking for business-specific events

## Example Integration

### Application Class

```kotlin
// Kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val autocaptureOptions = AutocaptureOptions.Builder().build()

        val options = MixpanelOptions.Builder()
            .autocaptureOptions(autocaptureOptions)
            .build()

        val mixpanel = MixpanelAPI.getInstance(
            this,
            "YOUR_PROJECT_TOKEN",
            true,  // trackAutomaticEvents
            options
        )

        mixpanel.setEnableLogging(BuildConfig.DEBUG)
    }
}
```

```java
// Java
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        AutocaptureOptions autocaptureOptions = new AutocaptureOptions.Builder().build();

        MixpanelOptions options = new MixpanelOptions.Builder()
            .autocaptureOptions(autocaptureOptions)
            .build();

        MixpanelAPI mixpanel = MixpanelAPI.getInstance(
            this,
            "YOUR_PROJECT_TOKEN",
            true,  // trackAutomaticEvents
            options
        );

        mixpanel.setEnableLogging(BuildConfig.DEBUG);
    }
}
```

### AndroidManifest.xml

```xml
<application
    android:name=".MyApplication"
    ...>
```

## FAQ

**Does autocapture work with custom Views?**

Yes! Custom Views are tracked like any other View. Set `contentDescription` or a meaningful resource ID for proper identification.

**Does autocapture impact app performance?**

The overhead is minimal (< 5ms per touch). Touch interception is non-blocking and runs on the main thread with minimal processing.

**Can I disable autocapture at runtime?**

Autocapture is configured at SDK initialization and cannot be toggled at runtime. To disable it, don't pass `AutocaptureOptions` to `MixpanelOptions`.

**Does autocapture work in app extensions?**

No, autocapture is automatically disabled in app extensions to avoid unexpected behavior.

**How does autocapture handle ProGuard/R8?**

The SDK includes necessary ProGuard rules. If you're using ProGuard/R8, ensure your configuration includes the SDK's rules (automatically applied when using AGP 3.0+).

**Can I capture custom properties with autocapture events?**

Autocapture events have predefined properties. For custom properties, continue using manual `track()` calls.

## Learn More

- [Mixpanel Android SDK Documentation](https://mixpanel.com/help/reference/android)
- [Javadoc Reference](http://mixpanel.github.io/mixpanel-android/)
- [GitHub Repository](https://github.com/mixpanel/mixpanel-android)
- [Sample App](https://github.com/mixpanel/mixpanel-android/tree/master/analytics/mixpaneldemo)
