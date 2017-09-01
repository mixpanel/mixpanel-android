package com.mixpanel.android.mpmetrics;

import java.util.Set;

/**
 * For use with {@link MixpanelAPI.People#addOnMixpanelTweaksUpdatedListener(OnMixpanelTweaksUpdatedListener)}
 */
public interface OnMixpanelTweaksUpdatedListener {
    public void onMixpanelTweakUpdated(Set<String> updatedTweaksName);
}