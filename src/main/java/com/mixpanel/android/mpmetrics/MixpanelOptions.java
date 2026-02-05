package com.mixpanel.android.mpmetrics;

import static com.mixpanel.android.mpmetrics.ConfigurationChecker.LOGTAG;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mixpanel.android.util.MPLog;
import com.mixpanel.android.util.ProxyServerInteractor;

import org.json.JSONObject;

public class MixpanelOptions {

    private final String instanceName;
    private final boolean optOutTrackingDefault;
    private final JSONObject superProperties;
    private final boolean featureFlagsEnabled;
    private final JSONObject featureFlagsContext;
    private final DeviceIdProvider deviceIdProvider;
    private final String serverURL;
    private final ProxyServerInteractor proxyServerInteractor;

    private MixpanelOptions(Builder builder) {
        this.instanceName = builder.instanceName;
        this.optOutTrackingDefault = builder.optOutTrackingDefault;
        this.superProperties = builder.superProperties;
        this.featureFlagsEnabled = builder.featureFlagsEnabled;
        this.featureFlagsContext = builder.featureFlagsContext;
        this.deviceIdProvider = builder.deviceIdProvider;
        this.serverURL = builder.serverURL;
        this.proxyServerInteractor = builder.proxyServerInteractor;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public boolean isOptOutTrackingDefault() {
        return optOutTrackingDefault;
    }

    public JSONObject getSuperProperties() {
        // Defensive copy to prevent modification of the internal JSONObject
        if (superProperties == null) {
            return null;
        }
        try {
            return new JSONObject(superProperties.toString());
        } catch (Exception e) {
            // This should ideally not happen if superProperties was a valid JSONObject
            MPLog.e(LOGTAG, "Invalid super properties", e);
            return null;
        }
    }

    public boolean areFeatureFlagsEnabled() {
        return featureFlagsEnabled;
    }

    public JSONObject getFeatureFlagsContext() {
        // Defensive copy
        if (featureFlagsContext == null) {
            return new JSONObject();
        }
        try {
            return new JSONObject(featureFlagsContext.toString());
        } catch (Exception e) {
            // This should ideally not happen if featureFlagsContext was a valid JSONObject
            MPLog.e(LOGTAG, "Invalid feature flags context", e);
            return new JSONObject();
        }
    }

    /**
     * Returns the custom device ID provider, or null if not set.
     *
     * @return The configured {@link DeviceIdProvider}, or null for default behavior.
     */
    @Nullable
    public DeviceIdProvider getDeviceIdProvider() {
        return deviceIdProvider;
    }

    /**
     * Returns the server URL configured for this Mixpanel instance.
     *
     * @return The server URL, or null if not configured (will use default).
     */
    public String getServerURL() {
        return serverURL;
    }

    /**
     * Returns the proxy server interactor configured for this Mixpanel instance.
     *
     * @return The ProxyServerInteractor, or null if not configured.
     */
    public ProxyServerInteractor getProxyServerInteractor() {
        return proxyServerInteractor;
    }

    public static class Builder {
        private String instanceName;
        private boolean optOutTrackingDefault = false;
        private JSONObject superProperties;
        private boolean featureFlagsEnabled = false;
        private JSONObject featureFlagsContext = new JSONObject();
        private DeviceIdProvider deviceIdProvider = null;
        private String serverURL;
        private ProxyServerInteractor proxyServerInteractor;

        public Builder() {
        }

        /**
         * Sets the distinct instance name for the MixpanelAPI. This is useful if you want to
         * manage multiple Mixpanel project instances.
         *
         * @param instanceName The unique name for the Mixpanel instance.
         * @return This Builder instance for chaining.
         */
        public Builder instanceName(String instanceName) {
            this.instanceName = instanceName;
            return this;
        }

        /**
         * Sets the default opt-out tracking state. If true, the SDK will not send any
         * events or profile updates by default. This can be overridden at runtime.
         *
         * @param optOutTrackingDefault True to opt-out of tracking by default, false otherwise.
         * @return This Builder instance for chaining.
         */
        public Builder optOutTrackingDefault(boolean optOutTrackingDefault) {
            this.optOutTrackingDefault = optOutTrackingDefault;
            return this;
        }

        /**
         * Sets the super properties to be sent with every event.
         * These properties are persistently stored.
         *
         * @param superProperties A JSONObject containing key-value pairs for super properties.
         *                        The provided JSONObject will be defensively copied.
         * @return This Builder instance for chaining.
         */
        public Builder superProperties(JSONObject superProperties) {
            if (superProperties == null) {
                this.superProperties = null;
            } else {
                try {
                    // Defensive copy
                    this.superProperties = new JSONObject(superProperties.toString());
                } catch (Exception e) {
                    // Log error or handle as appropriate if JSON is invalid
                    this.superProperties = null;
                }
            }
            return this;
        }

        /**
         * Enables or disables the Mixpanel feature flags functionality.
         *
         * @param featureFlagsEnabled True to enable feature flags, false to disable.
         * @return This Builder instance for chaining.
         */
        public Builder featureFlagsEnabled(boolean featureFlagsEnabled) {
            this.featureFlagsEnabled = featureFlagsEnabled;
            return this;
        }

        /**
         * Sets the context to be used for evaluating feature flags.
         * This can include properties like distinct_id or other custom properties.
         *
         * @param featureFlagsContext A JSONObject containing key-value pairs for the feature flags context.
         *                            The provided JSONObject will be defensively copied.
         * @return This Builder instance for chaining.
         */
        public Builder featureFlagsContext(JSONObject featureFlagsContext) {
            if (featureFlagsContext == null) {
                this.featureFlagsContext = new JSONObject();
            } else {
                try {
                    // Defensive copy
                    this.featureFlagsContext = new JSONObject(featureFlagsContext.toString());
                } catch (Exception e) {
                    // Log error or handle as appropriate if JSON is invalid
                    this.featureFlagsContext = null;
                }
            }
            return this;
        }

        /**
         * Sets a custom device ID provider.
         *
         * <p>Use this to control device ID generation instead of relying on the SDK's
         * default behavior (random UUID).
         *
         * <p><b>Important:</b> The device ID strategy is an architectural decision that
         * should be made at project inception, not retrofitted later. Adding a provider
         * to an existing app may cause identity discontinuity.
         *
         * <p><b>Controlling Reset Behavior:</b>
         * <ul>
         *   <li>Return the <b>same value</b> each time = Device ID never changes</li>
         *   <li>Return a <b>different value</b> each time = Device ID changes on reset</li>
         * </ul>
         *
         * @param deviceIdProvider The provider to use, or null for default behavior.
         * @return This Builder instance for chaining.
         * @see DeviceIdProvider
         */
        public Builder deviceIdProvider(DeviceIdProvider deviceIdProvider) {
            this.deviceIdProvider = deviceIdProvider;
            return this;
        }

        /**
         * Sets a custom server URL for Mixpanel API requests.
         *
         * <p>Use this to route data to a different Mixpanel region or your own proxy server.
         * Defaults to {@code https://api.mixpanel.com}.
         *
         * <pre>{@code
         * // Mixpanel regional server (e.g., EU)
         * .serverURL("https://api-eu.mixpanel.com")
         *
         * // Custom proxy server
         * .serverURL("https://my-proxy.example.com")
         * }</pre>
         *
         * @param serverURL The base URL for API requests.
         * @return This Builder instance for chaining.
         */
        public Builder serverURL(@NonNull String serverURL) {
            this.serverURL = serverURL;
            return this;
        }

        /**
         * Sets a custom server URL with a {@link ProxyServerInteractor} for advanced proxy handling.
         *
         * <p>Use this when your proxy server requires custom request headers or you need to
         * listen to API responses.
         *
         * <pre>{@code
         * .serverURL("https://my-proxy.example.com", new ProxyServerInteractor() {
         *     @Override
         *     public Map<String, String> getProxyRequestHeaders() {
         *         Map<String, String> headers = new HashMap<>();
         *         headers.put("Authorization", "Bearer " + token);
         *         return headers;
         *     }
         *
         *     @Override
         *     public void onProxyResponse(String url, int responseCode) {
         *         // Handle response
         *     }
         * })
         * }</pre>
         *
         * @param serverURL             The base URL for the proxy server.
         * @param proxyServerInteractor Handler for custom headers and response callbacks.
         * @return This Builder instance for chaining.
         */
        public Builder serverURL(
                @NonNull String serverURL,
                @Nullable ProxyServerInteractor proxyServerInteractor
        ) {
            this.serverURL = serverURL;
            this.proxyServerInteractor = proxyServerInteractor;
            return this;
        }

        /**
         * Builds and returns a {@link MixpanelOptions} instance with the configured settings.
         *
         * @return A new {@link MixpanelOptions} instance.
         */
        public MixpanelOptions build() {
            return new MixpanelOptions(this);
        }
    }
}
