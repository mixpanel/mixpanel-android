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

        final JSONObject inAppJson = new JSONObject(
                "{\"id\": 1234, \"message_id\": 4321, \"type\": \"takeover\", \"body\": \"Hook me up, yo!\", \"body_color\": 4294901760, \"title\": null, \"title_color\": 4278255360, \"image_url\": \"http://mixpanel.com/Balok.jpg\", \"bg_color\": 3909091328, \"close_color\": 4294967295, \"extras\": {\"image_fade\": true},\"buttons\": [{\"text\": \"Button!\", \"text_color\": 4278190335, \"bg_color\": 4294967040, \"border_color\": 4278255615, \"cta_url\": \"hellomixpanel://deeplink/howareyou\"}, {\"text\": \"Button 2!\", \"text_color\": 4278190335, \"bg_color\": 4294967040, \"border_color\": 4278255615, \"cta_url\": \"hellomixpanel://deeplink/howareyou\"}]}"
        );
        final TakeoverInAppNotification inApp = new TakeoverInAppNotification(inAppJson);
        inApp.setImage(bitmap);

        mInAppState = new UpdateDisplayState.DisplayState.InAppNotificationState(inApp, 0xBB);
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

    private UpdateDisplayState.DisplayState.InAppNotificationState mInAppState;
}
