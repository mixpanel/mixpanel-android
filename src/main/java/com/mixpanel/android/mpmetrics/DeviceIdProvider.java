package com.mixpanel.android.mpmetrics;

/**
 * Interface for providing custom device IDs to the Mixpanel SDK.
 *
 * <p>Use this to control device ID generation instead of relying on the SDK's default
 * behavior (random UUID).
 *
 * <p><b>Important: Choose your device ID strategy up front.</b> This callback is invoked:
 * <ul>
 *   <li>Once during initialization (if no persisted identity exists)</li>
 *   <li>On each call to {@link MixpanelAPI#reset()}</li>
 *   <li>On each call to {@link MixpanelAPI#optOutTracking()}</li>
 * </ul>
 *
 * <p><b>Controlling Reset Behavior:</b>
 * <ul>
 *   <li>Return the <b>same value</b> each time = Device ID never changes (persistent identity)</li>
 *   <li>Return a <b>different value</b> each time = Device ID changes on reset (ephemeral identity)</li>
 * </ul>
 *
 * <p><b>Warning:</b> Adding a {@code DeviceIdProvider} to an existing app that previously used
 * the default device ID may cause identity discontinuity. The SDK will log a warning if the
 * provider returns a value different from the persisted anonymous ID.
 *
 * <p><b>Example - Persistent Device ID:</b>
 * <pre>{@code
 * MixpanelOptions options = new MixpanelOptions.Builder()
 *     .deviceIdProvider(() -> MyKeychainHelper.getOrCreatePersistentId())
 *     .build();
 * }</pre>
 *
 * <p><b>Example - Ephemeral Device ID (resets each time):</b>
 * <pre>{@code
 * MixpanelOptions options = new MixpanelOptions.Builder()
 *     .deviceIdProvider(() -> UUID.randomUUID().toString())
 *     .build();
 * }</pre>
 */
public interface DeviceIdProvider {
    /**
     * Provides a device ID for Mixpanel tracking.
     *
     * <p>This method may be called from a background thread. Implementations should be
     * thread-safe and should not perform long-running operations.
     *
     * @return A non-empty string to use as the device ID, or an empty string to fall back
     *         to the SDK's default behavior (random UUID).
     */
    String getDeviceId();
}
