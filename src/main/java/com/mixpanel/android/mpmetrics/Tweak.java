package com.mixpanel.android.mpmetrics;

/**
 * A Tweak allows you to alter values in your user's applications through the Mixpanel UI.
 * Use Tweaks to expose parameters you can adjust in A/B tests, to determine what application
 * settings result in the best experiences for your users and which are best for achieving
 * your goals.
 *
 * You can declare tweaks with
 * {@link MixpanelAPI#stringTweak(String, String)}, {@link MixpanelAPI#booleanTweak(String, boolean)},
 * {@link MixpanelAPI#doubleTweak(String, double)}, {@link MixpanelAPI#longTweak(String, long)},
 * and other tweak-related interfaces on MixpanelAPI.
 */
public interface Tweak<T> {
    /**
     * @return a value for this tweak, either the default value or a value set as part of a Mixpanel A/B test.
     */
    T get();
}
