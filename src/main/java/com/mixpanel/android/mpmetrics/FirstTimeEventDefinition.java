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

        // Validate non-empty strings (propertyFilters is nullable)
        if (flagKey.isEmpty()) {
            throw new IllegalArgumentException("flagKey cannot be empty");
        }
        if (flagId.isEmpty()) {
            throw new IllegalArgumentException("flagId cannot be empty");
        }
        if (projectId.isEmpty()) {
            throw new IllegalArgumentException("projectId cannot be empty");
        }
        if (firstTimeEventHash.isEmpty()) {
            throw new IllegalArgumentException("firstTimeEventHash cannot be empty");
        }
        if (eventName.isEmpty()) {
            throw new IllegalArgumentException("eventName cannot be empty");
        }

        this.flagKey = flagKey;
        this.flagId = flagId;
        this.projectId = projectId;
        this.firstTimeEventHash = firstTimeEventHash;
        this.eventName = eventName;
        this.propertyFilters = propertyFilters;  // null is valid
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
