package com.mixpanel.android.mpmetrics;

/**
 * This interface is for
 */
public interface TweakRegistrar {
    void declareTweaks(Tweaks t);
    void registerObjectForTweaks(Tweaks t, Object registrant);
}
