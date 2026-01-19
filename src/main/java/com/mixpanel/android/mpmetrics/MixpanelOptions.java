package com.mixpanel.android.mpmetrics;

import static com.mixpanel.android.mpmetrics.ConfigurationChecker.LOGTAG;

import com.mixpanel.android.util.MPLog;
import com.mixpanel.android.util.ProxyServerInteractor;

import org.json.JSONObject;

public class MixpanelOptions {

    private final String instanceName;
    private final boolean optOutTrackingDefault;
    private final JSONObject superProperties;
    private final boolean featureFlagsEnabled;
    private final JSONObject featureFlagsContext;
    private final String serverURL;
    private final ProxyServerInteractor proxyServerInteractor;

    private MixpanelOptions(Builder builder) {
        this.instanceName = builder.instanceName;
        this.optOutTrackingDefault = builder.optOutTrackingDefault;
        this.superProperties = builder.superProperties;
        this.featureFlagsEnabled = builder.featureFlagsEnabled;
        this.featureFlagsContext = builder.featureFlagsContext;
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
         * The provided JSONObject will be defensively copied.
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
         * The provided JSONObject will be defensively copied.
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
         * Sets the base URL used for Mixpanel API requests. Useful if you need to proxy
         * Mixpanel requests or route data to Mixpanel's EU servers.
         *
         * <p>Defaults to https://api.mixpanel.com. To route data to Mixpanel's EU servers,
         * set to https://api-eu.mixpanel.com
         *
         * <p>Setting this at initialization time ensures the ad-blocker check uses
         * the correct host.
         *
         * @param serverURL The base URL used for Mixpanel API requests
         *     (e.g., "https://api-eu.mixpanel.com" or "https://my-proxy.example.com").
         * @return This Builder instance for chaining.
         */
        public Builder serverURL(String serverURL) {
            this.serverURL = serverURL;
            return this;
        }

        /**
         * Sets the proxy server interactor for handling proxy-specific headers and responses.
         *
         * <p>This is useful when using a custom proxy server that requires additional
         * authentication headers or needs to process responses differently.
         *
         * @param proxyServerInteractor The interactor to handle proxy server communication.
         * @return This Builder instance for chaining.
         */
        public Builder proxyServerInteractor(ProxyServerInteractor proxyServerInteractor) {
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
