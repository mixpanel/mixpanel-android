package com.mixpanel.android.mpmetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * Immutable data class representing a pending first-time event targeting definition.
 * These events can trigger feature flag variant changes when matched.
 */
class FirstTimeEventDefinition {
    @NonNull final String flagKey;
    @NonNull final String flagId;
    @NonNull final String projectId;
    @NonNull final String firstTimeEventHash;
    @NonNull final String eventName;
    @Nullable final JSONObject propertyFilters;  // JsonLogic filter, null means no property filtering
    @NonNull final MixpanelFlagVariant pendingVariant;

    FirstTimeEventDefinition(
            @NonNull String flagKey,
            @NonNull String flagId,
            @NonNull String projectId,
            @NonNull String firstTimeEventHash,
            @NonNull String eventName,
            @Nullable JSONObject propertyFilters,
            @NonNull MixpanelFlagVariant pendingVariant) {
        this.flagKey = flagKey;
        this.flagId = flagId;
        this.projectId = projectId;
        this.firstTimeEventHash = firstTimeEventHash;
        this.eventName = eventName;
        this.propertyFilters = propertyFilters;
        this.pendingVariant = pendingVariant;
    }

    /**
     * Returns the composite key used to uniquely identify this pending event.
     * Format: "{flagKey}:{firstTimeEventHash}"
     */
    @NonNull
    String getCompositeKey() {
        return flagKey + ":" + firstTimeEventHash;
    }
}
