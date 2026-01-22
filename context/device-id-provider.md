# Custom Device ID Provider

The Mixpanel Android SDK allows you to provide a custom device ID generation strategy through the `DeviceIdProvider` interface. This gives you full control over how device IDs are generated and whether they persist across `reset()` calls.

## Overview

By default, the SDK generates a random UUID as the device ID. The `DeviceIdProvider` feature allows you to:

- Use your own device ID generation logic
- Store device IDs in a custom location (e.g., server-side, Keychain equivalent)
- Control whether device IDs persist across `reset()` / `optOutTracking()` calls

## Basic Usage

### Define a Custom Provider

```java
DeviceIdProvider myProvider = new DeviceIdProvider() {
    @Override
    public String getDeviceId() {
        // Return your custom device ID
        return "my-custom-device-id";
    }
};
```

Or using a lambda:

```java
DeviceIdProvider myProvider = () -> "my-custom-device-id";
```

### Configure MixpanelOptions

```java
MixpanelOptions options = new MixpanelOptions.Builder()
    .deviceIdProvider(myProvider)
    .build();

MixpanelAPI mixpanel = MixpanelAPI.getInstance(context, "YOUR_TOKEN", false, options);
```

## Controlling Reset Behavior

The key insight is that your provider's return value controls reset behavior:

### Persistent Device ID (Never Reset)

Return the **same value** every time to create a persistent device ID that survives `reset()`:

```java
// Store once, return forever
private String persistentId = loadFromSecureStorage();

DeviceIdProvider persistentProvider = () -> {
    if (persistentId == null) {
        persistentId = UUID.randomUUID().toString();
        saveToSecureStorage(persistentId);
    }
    return persistentId;
};
```

### Ephemeral Device ID (Reset Creates New ID)

Return a **new value** each time to create ephemeral device IDs that change on `reset()`:

```java
DeviceIdProvider ephemeralProvider = () -> UUID.randomUUID().toString();
```

## When the Provider is Called

The provider is called:

1. **During SDK initialization** - When no persisted identity exists
2. **On `reset()`** - After clearing the identity, a new device ID is generated
3. **On `optOutTracking()`** - Same as reset, clears and regenerates identity

## Migration Considerations

### Adding a Provider to an Existing App

If you add a `DeviceIdProvider` to an app that already has users with persisted device IDs:

- The **persisted value takes precedence** to preserve identity continuity
- A warning is logged if the provider returns a different value than the persisted ID
- Call `reset()` after initialization if you want to force use of the provider value

```java
// Example: Force new device ID from provider
MixpanelAPI mixpanel = MixpanelAPI.getInstance(context, token, false, options);
mixpanel.reset(); // Provider will be called to generate new ID
```

### Best Practice

The device ID strategy should be an **architectural decision made at project inception**, not retrofitted later. Adding a provider to an existing app may cause identity discontinuity for existing users.

## Error Handling

The SDK handles provider errors gracefully:

- **Null return value**: Falls back to default UUID generation, logs a warning
- **Empty/whitespace-only return value**: Falls back to default UUID generation, logs a warning
- **Exception thrown**: Falls back to default UUID generation, logs the error

Returning `null` is the idiomatic way to signal "I can't provide an ID, use SDK default":

```java
DeviceIdProvider provider = () -> {
    String id = fetchDeviceIdFromServer(); // might fail
    return id; // null = use SDK default (UUID)
};
```

Exception handling is also built-in:

```java
// This is safe - SDK won't crash
DeviceIdProvider faultyProvider = () -> {
    throw new RuntimeException("Something went wrong");
};
// SDK will log error and use UUID.randomUUID().toString()
```

## Complete Example

```java
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Create a provider that stores ID in SharedPreferences
        SharedPreferences prefs = getSharedPreferences("my_prefs", MODE_PRIVATE);

        DeviceIdProvider provider = () -> {
            String storedId = prefs.getString("device_id", null);
            if (storedId == null) {
                storedId = UUID.randomUUID().toString();
                prefs.edit().putString("device_id", storedId).apply();
            }
            return storedId;
        };

        MixpanelOptions options = new MixpanelOptions.Builder()
            .deviceIdProvider(provider)
            .featureFlagsEnabled(true)
            .build();

        MixpanelAPI.getInstance(this, "YOUR_TOKEN", false, options);
    }
}
```

## API Reference

### DeviceIdProvider Interface

```java
public interface DeviceIdProvider {
    /**
     * Provides a custom device ID for Mixpanel tracking.
     *
     * @return The device ID to use. Must be non-null and non-empty.
     *         If null or empty is returned, the SDK falls back to UUID generation.
     */
    String getDeviceId();
}
```

### MixpanelOptions.Builder

```java
public Builder deviceIdProvider(DeviceIdProvider deviceIdProvider)
```

Sets a custom device ID provider. Pass `null` to use default behavior (random UUID).

## Related Methods

- `MixpanelAPI.getAnonymousId()` - Returns the current device ID (raw value from provider)
- `MixpanelAPI.getDistinctId()` - Returns `$device:<anonymousId>` before `identify()`, user ID after
- `MixpanelAPI.reset()` - Clears identity and calls provider to generate new device ID
- `MixpanelAPI.optOutTracking()` - Same as reset, also opts out of tracking
