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
        mSurvey = new Survey(surveyJson);

        final JSONObject inAppJson = new JSONObject(
            "{\"body\":\"Hook me up, yo!\",\"title\":\"Tranya?\",\"message_id\":1781,\"image_url\":\"http://mixpanel.com/Balok.jpg\",\"cta\":\"I'm Down!\",\"cta_url\":\"http://www.mixpanel.com\",\"id\":119911,\"type\":\"mini\"}"
        );
        mInApp = new InAppNotification(inAppJson);
        mInApp.setImage(bitmap);

        mSurveyState = new UpdateDisplayState.DisplayState.SurveyState(mSurvey, 0xFF, bitmap, true);
        mInAppState = new UpdateDisplayState.DisplayState.InAppNotificationState(mInApp, 0xBB);
    }

    public void testSurveyParcelable() {
        final Parcel parcel = Parcel.obtain();
        mSurveyState.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final UpdateDisplayState.DisplayState.SurveyState reconstructed =
                UpdateDisplayState.DisplayState.SurveyState.CREATOR.createFromParcel(parcel);

        assertEquals(mSurveyState.getHighlightColor(), reconstructed.getHighlightColor());
        assertTrue(mSurveyState.getAnswers().contentEquals(reconstructed.getAnswers()));

        final Bitmap originalBackground = mSurveyState.getBackground();
        final Bitmap reconstructedBackground = reconstructed.getBackground();
        assertEquals(originalBackground.getWidth(), reconstructedBackground.getWidth());
        assertEquals(originalBackground.getPixel(0, 0), reconstructedBackground.getPixel(0, 0));

        final Survey reconstructedSurvey = reconstructed.getSurvey();
        assertEquals(mSurvey.getId(), reconstructedSurvey.getId());
        assertEquals(mSurvey.getCollectionId(), reconstructedSurvey.getCollectionId());
        final List<Survey.Question> originalQuestions = mSurvey.getQuestions();
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

    public void testInAppParcelable() {
        final Parcel parcel = Parcel.obtain();
        mInAppState.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final UpdateDisplayState.DisplayState.InAppNotificationState reconstructed =
                UpdateDisplayState.DisplayState.InAppNotificationState.CREATOR.createFromParcel(parcel);

        assertEquals(mInAppState.getHighlightColor(), reconstructed.getHighlightColor());

        final InAppNotification reconstructedInApp = reconstructed.getInAppNotification();
        assertEquals(mInApp.getId(), reconstructedInApp.getId());
        assertEquals(mInApp.getMessageId(), reconstructedInApp.getMessageId());
        assertEquals(mInApp.getBody(), reconstructedInApp.getBody());
        assertEquals(mInApp.getCallToAction(), reconstructedInApp.getCallToAction());
        assertEquals(mInApp.getCallToActionUrl(), reconstructedInApp.getCallToActionUrl());
        assertEquals(mInApp.getImageUrl(), reconstructedInApp.getImageUrl());
        assertEquals(mInApp.getTitle(), reconstructedInApp.getTitle());
        assertEquals(mInApp.getType(), reconstructedInApp.getType());

        final Bitmap originalImage = mInApp.getImage();
        final Bitmap reconstructedImage = reconstructedInApp.getImage();
        assertEquals(originalImage.getWidth(), reconstructedImage.getWidth());
        assertEquals(originalImage.getPixel(0, 0), originalImage.getPixel(0, 0));
    }


    private Survey mSurvey;
    private InAppNotification mInApp;
    private UpdateDisplayState.DisplayState.SurveyState mSurveyState;
    private UpdateDisplayState.DisplayState.InAppNotificationState mInAppState;
}
