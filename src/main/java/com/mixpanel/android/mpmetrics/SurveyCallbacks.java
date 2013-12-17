package com.mixpanel.android.mpmetrics;

/**
 * For use with MixpanelAPI.checkForSurvey. SurveyCallbacks.foundSurvey() will be called when the library gets a survey from Mixpanel.
 */
public interface SurveyCallbacks {
    /**
     * foundSurvey will be called when the MixpanelAPI check for available surveys
     * returns. The argument will be null if no survey was available, or
     * a non-null survey if one exists to be shown to the user.
     *
     * foundSurvey() should be safe to call from an arbitrary thread.
     */
    public void foundSurvey(Survey s);
}
