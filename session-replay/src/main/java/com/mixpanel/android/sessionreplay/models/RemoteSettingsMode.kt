package com.mixpanel.android.sessionreplay.models

/**
 * Controls how the SDK handles remote settings from the Mixpanel settings endpoint.
 */
enum class RemoteSettingsMode {
    /**
     * Remote SDK config settings are fetched but not used. Uses app's original config values.
     */
    DISABLED,

    /**
     * Fetch SDK config from settings endpoint.
     * On API failure/timeout: SDK initialization is disabled entirely.
     */
    STRICT,

    /**
     * Fetch SDK config from settings endpoint.
     * On API failure/timeout: use cached values if available, otherwise use app's original config.
     */
    FALLBACK
}
