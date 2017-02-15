package com.mixpanel.android.mpmetrics;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.test.AndroidTestCase;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class UpdateDisplayStateTest extends AndroidTestCase {
    public void setUp() throws BadDecideObjectException, JSONException {
        final Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        final Bitmap bitmap = Bitmap.createBitmap(100, 100, conf);

        final JSONObject surveyJson = new JSONObject(
                "{\"collections\":[{\"id\":151,\"selector\":\"\\\"@mixpanel\\\" in properties[\\\"$email\\\"]\"}]," +
                        "\"id\":299," +
                        "\"questions\":[" +
                        "{\"prompt\":\"PROMPT1\",\"extra_data\":{\"$choices\":[\"Answer1,1\",\"Answer1,2\",\"Answer1,3\"]},\"type\":\"multiple_choice\",\"id\":287}," +
                        "{\"prompt\":\"PROMPT2\",\"extra_data\":{},\"type\":\"text\",\"id\":289}]}"
        );
        final Survey survey = new Survey(surveyJson);

        final JSONObject inAppJson = new JSONObject(
                "{\"id\": 1234, \"message_id\": 4321, \"type\": \"takeover\", \"body\": \"Hook me up, yo!\", \"body_color\": 4294901760, \"title\": null, \"title_color\": 4278255360, \"image_url\": \"http://mixpanel.com/Balok.jpg\", \"bg_color\": 3909091328, \"close_color\": 4294967295, \"extras\": {\"image_fade\": true},\"buttons\": [{\"text\": \"Button!\", \"text_color\": 4278190335, \"bg_color\": 4294967040, \"border_color\": 4278255615, \"cta_url\": \"hellomixpanel://deeplink/howareyou\"}, {\"text\": \"Button 2!\", \"text_color\": 4278190335, \"bg_color\": 4294967040, \"border_color\": 4278255615, \"cta_url\": \"hellomixpanel://deeplink/howareyou\"}]}"
        );
        final TakeoverInAppNotification inApp = new TakeoverInAppNotification(inAppJson);
        inApp.setImage(bitmap);

        mSurveyState = new UpdateDisplayState.DisplayState.SurveyState(survey);
        mSurveyState.setBackground(bitmap);
        mSurveyState.setHighlightColor(0xFF);
        mInAppState = new UpdateDisplayState.DisplayState.InAppNotificationState(inApp, 0xBB);
    }

    public void testSurveyParcelable() {
        final Parcel parcel = Parcel.obtain();
        mSurveyState.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final UpdateDisplayState.DisplayState.SurveyState reconstructed =
                UpdateDisplayState.DisplayState.SurveyState.CREATOR.createFromParcel(parcel);

        assertSameSurvey(mSurveyState, reconstructed);
    }

    public void testInAppParcelable() {
        final Parcel parcel = Parcel.obtain();
        mInAppState.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final UpdateDisplayState.DisplayState.InAppNotificationState reconstructed =
                UpdateDisplayState.DisplayState.InAppNotificationState.CREATOR.createFromParcel(parcel);

        assertSameNotification(mInAppState, reconstructed);
    }

    public void testWholeStateParcel() {
        {
            final Parcel parcel = Parcel.obtain();
            final UpdateDisplayState original = new UpdateDisplayState(mInAppState, "TEST DISTINCT ID 1", "TEST TOKEN 1");
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            final UpdateDisplayState reconstructed = UpdateDisplayState.CREATOR.createFromParcel(parcel);

            assertEquals(original.getDistinctId(), reconstructed.getDistinctId());
            assertEquals(original.getToken(), reconstructed.getToken());

            final UpdateDisplayState.DisplayState.InAppNotificationState reconstructedDisplay =
                    (UpdateDisplayState.DisplayState.InAppNotificationState) reconstructed.getDisplayState();

            assertSameNotification(mInAppState, reconstructedDisplay);
        }

        {
            final Parcel parcel = Parcel.obtain();
            final UpdateDisplayState original = new UpdateDisplayState(mSurveyState, "TEST DISTINCT ID 2", "TEST TOKEN 2");
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            final UpdateDisplayState reconstructed = UpdateDisplayState.CREATOR.createFromParcel(parcel);

            assertEquals(original.getDistinctId(), reconstructed.getDistinctId());
            assertEquals(original.getToken(), reconstructed.getToken());

            final UpdateDisplayState.DisplayState.SurveyState reconstructedDisplay =
                    (UpdateDisplayState.DisplayState.SurveyState) reconstructed.getDisplayState();

            assertSameSurvey(mSurveyState, reconstructedDisplay);
        }
    }

    private void assertSameSurvey(UpdateDisplayState.DisplayState.SurveyState original,
                                  UpdateDisplayState.DisplayState.SurveyState reconstructed) {
        assertEquals(original.getHighlightColor(), reconstructed.getHighlightColor());
        assertTrue(original.getAnswers().contentEquals(reconstructed.getAnswers()));

        final Bitmap originalBackground = original.getBackground();
        final Bitmap reconstructedBackground = reconstructed.getBackground();
        assertEquals(originalBackground.getWidth(), reconstructedBackground.getWidth());
        assertEquals(originalBackground.getPixel(0, 0), reconstructedBackground.getPixel(0, 0));

        final Survey originalSurvey = original.getSurvey();
        final Survey reconstructedSurvey = reconstructed.getSurvey();
        assertEquals(originalSurvey.getId(), reconstructedSurvey.getId());
        assertEquals(originalSurvey.getCollectionId(), reconstructedSurvey.getCollectionId());
        final List<Survey.Question> originalQuestions = originalSurvey.getQuestions();
        final List<Survey.Question> reconstructedQuestions = reconstructedSurvey.getQuestions();

        assertEquals(originalQuestions.size(), reconstructedQuestions.size());
        for (int questionIndex = 0; questionIndex < originalQuestions.size(); questionIndex++) {
            final Survey.Question oQ = originalQuestions.get(questionIndex);
            final Survey.Question rQ = reconstructedQuestions.get(questionIndex);

            assertEquals(oQ.getId(), rQ.getId());
            assertEquals(oQ.getPrompt(), rQ.getPrompt());

            List<String> oChoices = oQ.getChoices();
            List<String> rChoices = rQ.getChoices();
            assertEquals(oChoices.size(), rChoices.size());
            for (int choiceIndex = 0; choiceIndex < oChoices.size(); choiceIndex++) {
                assertEquals(oChoices.get(choiceIndex), rChoices.get(choiceIndex));
            }
        }
    }

    private void assertSameNotification(UpdateDisplayState.DisplayState.InAppNotificationState original,
                                      UpdateDisplayState.DisplayState.InAppNotificationState reconstructed) {
        assertEquals(original.getHighlightColor(), reconstructed.getHighlightColor());

        final TakeoverInAppNotification originalInApp = (TakeoverInAppNotification) original.getInAppNotification();
        final TakeoverInAppNotification reconstructedInApp = (TakeoverInAppNotification) reconstructed.getInAppNotification();
        assertEquals(originalInApp.getId(), reconstructedInApp.getId());
        assertEquals(originalInApp.getMessageId(), reconstructedInApp.getMessageId());
        assertEquals(originalInApp.getBody(), reconstructedInApp.getBody());
        assertEquals(originalInApp.getBodyColor(), reconstructedInApp.getBodyColor());
        assertEquals(originalInApp.getTitle(), reconstructedInApp.getTitle());
        assertEquals(originalInApp.getTitleColor(), reconstructedInApp.getTitleColor());
        assertEquals(originalInApp.getImageUrl(), reconstructedInApp.getImageUrl());
        assertEquals(originalInApp.getButton(0).getBackgroundColor(), reconstructedInApp.getButton(0).getBackgroundColor());
        assertEquals(originalInApp.getButton(0).getText(), reconstructedInApp.getButton(0).getText());
        assertEquals(originalInApp.getButton(0).getCtaUrl(), reconstructedInApp.getButton(0).getCtaUrl());
        assertEquals(originalInApp.getButton(0).getTextColor(), reconstructedInApp.getButton(0).getTextColor());
        assertEquals(originalInApp.getButton(0).getBorderColor(), reconstructedInApp.getButton(0).getBorderColor());
        assertEquals(originalInApp.getButton(1).getBackgroundColor(), reconstructedInApp.getButton(1).getBackgroundColor());
        assertEquals(originalInApp.getButton(1).getText(), reconstructedInApp.getButton(1).getText());
        assertEquals(originalInApp.getButton(1).getCtaUrl(), reconstructedInApp.getButton(1).getCtaUrl());
        assertEquals(originalInApp.getButton(1).getTextColor(), reconstructedInApp.getButton(1).getTextColor());
        assertEquals(originalInApp.getButton(1).getBorderColor(), reconstructedInApp.getButton(1).getBorderColor());
        assertEquals(originalInApp.getCloseColor(), reconstructedInApp.getCloseColor());
        assertEquals(originalInApp.getExtras().toString(), reconstructedInApp.getExtras().toString());
        assertEquals(originalInApp.setShouldShowShadow(), reconstructedInApp.setShouldShowShadow());
        assertEquals(originalInApp.getImageUrl(), reconstructedInApp.getImageUrl());
        assertEquals(originalInApp.getTitle(), reconstructedInApp.getTitle());
        assertEquals(originalInApp.getType(), reconstructedInApp.getType());

        final Bitmap originalImage = originalInApp.getImage();
        final Bitmap reconstructedImage = reconstructedInApp.getImage();
        assertEquals(originalImage.getWidth(), reconstructedImage.getWidth());
        assertEquals(originalImage.getPixel(0, 0), originalImage.getPixel(0, 0));
    }

    private UpdateDisplayState.DisplayState.SurveyState mSurveyState;
    private UpdateDisplayState.DisplayState.InAppNotificationState mInAppState;
}
