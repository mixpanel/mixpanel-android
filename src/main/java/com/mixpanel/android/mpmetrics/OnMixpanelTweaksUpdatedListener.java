package com.mixpanel.android.mpmetrics;

import java.util.Set;

/**
 * For use with {@link MixpanelAPI.People#addOnMixpanelTweaksUpdatedListener(OnMixpanelTweaksUpdatedListener)}
 */
public interface OnMixpanelTweaksUpdatedListener {
    /**
     * Called when the Mixpanel library has updated tweaks.
     * This method will not be called once per tweak update, but rather any time a batch of updates
     * becomes available.
     *
     * @param updatedTweaksName The set of tweak names that were updated.
     */
    public void onMixpanelTweakUpdated(Set<String> updatedTweaksName);
}