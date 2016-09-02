package com.mixpanel.android.mpmetrics;

/**
 * For use with {@link MixpanelAPI.People#addOnMixpanelTweaksUpdatedListener(OnMixpanelTweaksUpdatedListener)}
 */
public interface OnMixpanelTweaksUpdatedListener {
    public void onMixpanelTweakUpdated();
}