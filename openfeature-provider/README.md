# Mixpanel OpenFeature Provider for Android

[![OpenFeature](https://img.shields.io/badge/OpenFeature-compatible-green)](https://openfeature.dev/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/mixpanel/mixpanel-android/blob/master/LICENSE)

##### _April 15, 2026_ - [open-feature-v0.1.0](https://github.com/mixpanel/mixpanel-android/releases/tag/open-feature-v0.1.0)

An [OpenFeature](https://openfeature.dev/) provider that wraps Mixpanel's feature flags for use with the OpenFeature Kotlin (Android) SDK. This allows you to use Mixpanel's feature flagging capabilities through OpenFeature's standardized, vendor-agnostic API.

## Overview

This package provides a bridge between Mixpanel's native feature flags implementation and the OpenFeature specification. By using this provider, you can:

- Leverage Mixpanel's powerful feature flag and experimentation platform
- Use OpenFeature's standardized API for flag evaluation
- Easily switch between feature flag providers without changing your application code
- Integrate with OpenFeature's ecosystem of tools and frameworks

## Installation

Add the following dependencies to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.mixpanel.android:mixpanel-android-openfeature:<version>")
    implementation("dev.openfeature:kotlin-sdk-android:0.7.2")
}
```

Or with Groovy (`build.gradle`):

```groovy
dependencies {
    implementation 'com.mixpanel.android:mixpanel-android-openfeature:<version>'
    implementation 'dev.openfeature:kotlin-sdk-android:0.7.2'
}
```

## Quick Start

```kotlin
import com.mixpanel.android.openfeature.MixpanelProvider
import com.mixpanel.android.mpmetrics.MixpanelOptions
import dev.openfeature.kotlin.sdk.OpenFeatureAPI

// 1. Create the provider using the convenience constructor
val options = MixpanelOptions.Builder()
    .featureFlags()
    .build()
val provider = MixpanelProvider(context, "YOUR_PROJECT_TOKEN", options)

// 2. Register the provider with OpenFeature
OpenFeatureAPI.setProviderAndWait(provider)

// 3. Get a client and evaluate flags
val client = OpenFeatureAPI.getClient()
val showNewFeature = client.getBooleanValue("new-feature-flag", false)

if (showNewFeature) {
    // New feature is enabled!
}
```

### Alternative: Use an Existing MixpanelAPI Instance

If you already have a `MixpanelAPI` instance, you can pass its `Flags` object directly:

```kotlin
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.android.openfeature.MixpanelProvider

val mixpanel = MixpanelAPI.getInstance(context, "YOUR_PROJECT_TOKEN", false, options)
val provider = MixpanelProvider(mixpanel.getFlags())

OpenFeatureAPI.setProviderAndWait(provider)
```

> **Important:** If you need to call `mixpanel.identify()` to update the logged-in user or `mixpanel.track()` to use [Runtime Events](https://docs.mixpanel.com/docs/feature-flags/runtime-events) for targeting, you must call these methods on the **same `MixpanelAPI` instance** whose `Flags` object was passed to the provider. Using a different instance will not affect the provider's flag evaluation.

When using the convenience constructor, the underlying instance is accessible via `provider.mixpanel`:

```kotlin
val provider = MixpanelProvider(context, "YOUR_PROJECT_TOKEN", options)

// Use the same instance for identity and tracking
provider.mixpanel?.identify("user-123")
provider.mixpanel?.track("Purchase Completed")
```

## Usage Examples

### Basic Boolean Flag

```kotlin
val client = OpenFeatureAPI.getClient()

// Get a boolean flag with a default value
val isFeatureEnabled = client.getBooleanValue("my-feature", false)

if (isFeatureEnabled) {
    // Show the new feature
}
```

### Mixpanel Flag Types and OpenFeature Evaluation Methods

Mixpanel feature flags support three flag types. Use the corresponding OpenFeature evaluation method based on your flag's variant values:

| Mixpanel Flag Type | Variant Values | OpenFeature Method |
|---|---|---|
| Feature Gate | `true` / `false` | `getBooleanValue()` |
| Experiment | boolean, string, number, or JSON object | `getBooleanValue()`, `getStringValue()`, `getIntegerValue()`, `getDoubleValue()`, or `getObjectValue()` |
| Dynamic Config | JSON object | `getObjectValue()` |

```kotlin
val client = OpenFeatureAPI.getClient()

// Feature Gate - boolean variants
val isFeatureOn = client.getBooleanValue("new-checkout", false)

// Experiment with string variants
val buttonColor = client.getStringValue("button-color-test", "blue")

// Experiment with integer variants
val maxItems = client.getIntegerValue("max-items", 10)

// Experiment with double variants
val threshold = client.getDoubleValue("score-threshold", 0.5)

// Dynamic Config - JSON object variants
val featureConfig = client.getObjectValue("homepage-layout", Value.Structure(mapOf(
    "layout" to Value.String("grid"),
    "itemsPerRow" to Value.Integer(3)
)))
```

### Getting Full Resolution Details

If you need additional metadata about the flag evaluation:

```kotlin
val client = OpenFeatureAPI.getClient()

val details = client.getBooleanDetails("my-feature", false)

println(details.value)        // The resolved value
println(details.variant)      // The variant key from Mixpanel
println(details.reason)       // Why this value was returned
println(details.errorCode)    // Error code if evaluation failed
```

### Setting Context

You can pass evaluation context that will be sent to Mixpanel for flag evaluation using `OpenFeatureAPI.setContext()`:

```kotlin
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.Value

OpenFeatureAPI.setContext(ImmutableContext(
    attributes = mutableMapOf(
        "email" to Value.String("user@example.com"),
        "plan" to Value.String("premium")
    )
))
```

> **Note:** Per-evaluation context (the optional `context` parameter on evaluation methods) is **not supported** by this provider. Context must be set globally via `OpenFeatureAPI.setContext()`, which triggers a re-fetch of flag values from Mixpanel.

### Using custom_properties for Runtime Properties

You can pass `custom_properties` in the evaluation context for use with Mixpanel's [Runtime Properties](https://docs.mixpanel.com/docs/feature-flags/runtime-properties) targeting rules. Values must be flat key-value pairs (no nested objects):

```kotlin
OpenFeatureAPI.setContext(ImmutableContext(
    attributes = mutableMapOf(
        "custom_properties" to Value.Structure(mapOf(
            "tier" to Value.String("enterprise"),
            "seats" to Value.Integer(50),
            "industry" to Value.String("technology")
        ))
    )
))
```

## Context Mapping

Understanding how OpenFeature context maps to Mixpanel:

### All Properties Passed Directly

All properties in the OpenFeature `EvaluationContext` are passed directly to Mixpanel's feature flag evaluation. There is no transformation or filtering of properties.

```kotlin
// This OpenFeature context...
OpenFeatureAPI.setContext(ImmutableContext(
    targetingKey = "user-123",
    attributes = mutableMapOf(
        "email" to Value.String("user@example.com"),
        "plan" to Value.String("premium"),
        "beta_tester" to Value.Boolean(true)
    )
))

// ...is passed to Mixpanel as-is for flag evaluation
```

### targetingKey is Not Special

Unlike some feature flag providers, `targetingKey` is **not** used as a special bucketing key in Mixpanel. It is simply passed as another context property. Mixpanel's server-side configuration determines which properties are used for:

- **Targeting rules**: Which users see which variants
- **Bucketing**: How users are consistently assigned to variants

### User Identity is Managed Separately

**Important**: This provider does **not** call `mixpanel.identify()`. User identity should be managed separately through the same Mixpanel instance that backs this provider:

```kotlin
// Manage identity through the same Mixpanel instance
provider.mixpanel?.identify("user-123")

// The provider will use Mixpanel's current distinct_id automatically
val client = OpenFeatureAPI.getClient()
val value = client.getBooleanValue("my-flag", false)
```

## API Reference

### MixpanelProvider

The main provider class that implements the OpenFeature `FeatureProvider` interface.

#### Constructors

```kotlin
// Primary constructor - pass a Flags instance directly
MixpanelProvider(flags: MixpanelAPI.Flags)

// Convenience constructor - creates a MixpanelAPI instance internally
MixpanelProvider(context: Context, token: String, options: MixpanelOptions)
```

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `metadata` | `ProviderMetadata` | Provider metadata with the name "mixpanel-provider" |
| `mixpanel` | `MixpanelAPI?` | The underlying Mixpanel instance (only available when using the convenience constructor) |

#### Methods

| Method | Description |
|--------|-------------|
| `initialize(initialContext?)` | Called when the provider is registered. Applies initial context if provided. |
| `onContextSet(oldContext, newContext)` | Called when the global evaluation context changes. Sends updated context to Mixpanel. |
| `shutdown()` | Called when the provider is shut down. No-op since Mixpanel manages its own lifecycle. |
| `getBooleanEvaluation(key, defaultValue, context)` | Evaluates a boolean flag |
| `getStringEvaluation(key, defaultValue, context)` | Evaluates a string flag |
| `getIntegerEvaluation(key, defaultValue, context)` | Evaluates an integer flag |
| `getDoubleEvaluation(key, defaultValue, context)` | Evaluates a double flag |
| `getObjectEvaluation(key, defaultValue, context)` | Evaluates an object flag |

## Error Handling

The provider uses OpenFeature's standard error codes to indicate issues during flag evaluation:

### PROVIDER_NOT_READY

Returned when flags are evaluated before the provider has finished initializing.

```kotlin
// To avoid this error, use setProviderAndWait
OpenFeatureAPI.setProviderAndWait(provider)

// Or listen for the READY event
OpenFeatureAPI.observe<OpenFeatureEvents.ProviderReady> {
    // Now safe to evaluate flags
}
```

### FLAG_NOT_FOUND

Returned when the requested flag does not exist in Mixpanel.

```kotlin
val details = client.getBooleanDetails("nonexistent-flag", false)

if (details.errorCode == ErrorCode.FLAG_NOT_FOUND) {
    println("Flag does not exist, using default value")
}
```

### TYPE_MISMATCH

Returned when the flag value type does not match the requested type.

```kotlin
// If 'my-flag' is configured as a string in Mixpanel...
val details = client.getBooleanDetails("my-flag", false)

if (details.errorCode == ErrorCode.TYPE_MISMATCH) {
    println("Flag is not a boolean, using default value")
}
```

## Troubleshooting

### Flags Always Return Default Values

**Possible causes:**

1. **Feature flags not enabled**: Ensure you configured Mixpanel with feature flags enabled:
   ```kotlin
   val options = MixpanelOptions.Builder()
       .featureFlags()
       .build()
   ```

2. **Provider not ready**: Make sure to wait for the provider to initialize:
   ```kotlin
   OpenFeatureAPI.setProviderAndWait(provider)
   ```

3. **Network issues**: Check Logcat for failed requests to Mixpanel's flags API.

4. **Flag not configured**: Verify the flag exists in your Mixpanel project and is enabled.

### Type Mismatch Errors

If you are getting `TYPE_MISMATCH` errors:

1. **Check flag configuration**: Verify the flag's value type in Mixpanel matches how you are evaluating it:
   ```kotlin
   // If flag value is a string like "true", use getStringValue, not getBooleanValue
   val value = client.getStringValue("my-flag", "default")
   ```

2. **Use getObjectValue for complex types**: For JSON objects or arrays, use `getObjectValue`.

### Exposure Events Not Tracking

If `$experiment_started` events are not appearing in Mixpanel:

1. **Verify Mixpanel tracking is working**: Test that other Mixpanel events are being tracked successfully.

2. **Check for duplicate evaluations**: Mixpanel only tracks the first exposure per flag per session to avoid duplicate events.

### Flags Not Updating After Context Change

When you update the OpenFeature context, the provider needs to fetch new flag values:

```kotlin
// Update context and wait for new flags
OpenFeatureAPI.setContext(ImmutableContext(
    attributes = mutableMapOf("plan" to Value.String("premium"))
))

// Now evaluate with new context
val value = client.getBooleanValue("premium-feature", false)
```

If flags still are not updating, check that your targeting rules in Mixpanel are configured to use the context properties you are setting.

## License

Apache-2.0
