package com.mixpanel.android.mpmetrics;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import android.test.AndroidTestCase;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
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
            "{\"body\":\"Hook me up, yo!\",\"title\":\"Tranya?\",\"message_id\":1781,\"image_url\":\"http://mixpanel.com/Balok.jpg\",\"cta\":\"I'm Down!\",\"cta_url\":\"http://www.mixpanel.com\",\"id\":119911,\"type\":\"mini\"}"
        );
        final InAppNotification inApp = new InAppNotification(inAppJson);
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

        final InAppNotification originalInApp = original.getInAppNotification();
        final InAppNotification reconstructedInApp = reconstructed.getInAppNotification();
        assertEquals(originalInApp.getId(), reconstructedInApp.getId());
        assertEquals(originalInApp.getMessageId(), reconstructedInApp.getMessageId());
        assertEquals(originalInApp.getBody(), reconstructedInApp.getBody());
        assertEquals(originalInApp.getCallToAction(), reconstructedInApp.getCallToAction());
        assertEquals(originalInApp.getCallToActionUrl(), reconstructedInApp.getCallToActionUrl());
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
