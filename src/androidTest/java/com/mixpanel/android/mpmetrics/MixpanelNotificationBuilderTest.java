package com.mixpanel.android.mpmetrics;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.test.AndroidTestCase;

import org.mockito.ArgumentMatcher;

import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

public class MixpanelNotificationBuilderTest extends AndroidTestCase {

    private final String VALID_RESOURCE_NAME = "com_mixpanel_android_logo";
    private final int VALID_RESOURCE_ID = R.drawable.com_mixpanel_android_logo;
    private final String INVALID_RESOURCE_NAME = "NOT A VALID RESOURCE";
    private final String DEFAULT_TITLE = "DEFAULT TITLE";
    private final int DEFAULT_ICON_ID = android.R.drawable.sym_def_app_icon;
    private final Intent DEFAULT_INTENT = new Intent(Intent.ACTION_BUG_REPORT); // ACTION_BUG_REPORT is chosen because it's identifiably weird

    @Override
    public void setUp() throws Exception {
        super.setUp();
        System.setProperty("org.mockito.android.target", getContext().getCacheDir().getPath());

        now = System.currentTimeMillis();
        builderSpy = spy(new Notification.Builder(getContext()));
        mpPushSpy = spy(new MixpanelPushNotification(this.getContext(), builderSpy, getTestResources(), now));

        when(mpPushSpy.getDefaultTitle()).thenReturn(DEFAULT_TITLE);
        when(mpPushSpy.getDefaultIcon()).thenReturn(DEFAULT_ICON_ID);
        when(mpPushSpy.getDefaultIntent()).thenReturn(DEFAULT_INTENT);
    }

    public void testNotificationEmptyIntent() {
        assertNull(mpPushSpy.readInboundIntent(new Intent()));
    }

    public void testBasicNotification() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_title", "TITLE");
        mpPushSpy.createNotification(intent);

        verify(builderSpy).setDefaults(MPConfig.getInstance(getContext()).getNotificationDefaults());
        verify(builderSpy).setWhen(now);
        verify(builderSpy).setContentTitle("TITLE");
        verify(builderSpy).setContentText("MESSAGE");
        verify(builderSpy).setTicker("MESSAGE");
        verify(builderSpy).setContentIntent(any(PendingIntent.class));
    }

    public void testMessage() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        mpPushSpy.createNotification(intent);
        verify(builderSpy).setContentText("MESSAGE");
        verify(builderSpy).setContentIntent(any(PendingIntent.class));
    }

    public void testNoMessage() {
        final Intent intent = new Intent();
        Notification notification = mpPushSpy.createNotification(intent);
        assertNull(notification);
    }

    public void testTitle() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_title", "TITLE");
        mpPushSpy.createNotification(intent);
        verify(builderSpy).setContentTitle("TITLE");
    }

    public void testNoTitle() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        mpPushSpy.createNotification(intent);
        verify(builderSpy).setContentTitle(DEFAULT_TITLE);
    }

    public void testIcon() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_icnm", VALID_RESOURCE_NAME);
        mpPushSpy.createNotification(intent);
        verify(builderSpy).setSmallIcon(VALID_RESOURCE_ID);
    }

    public void testNoIcon() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        mpPushSpy.createNotification(intent);
        verify(builderSpy).setSmallIcon(DEFAULT_ICON_ID);
    }

    public void testInvalidIcon() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_icnm", INVALID_RESOURCE_NAME);
        mpPushSpy.createNotification(intent);
        verify(builderSpy).setSmallIcon(DEFAULT_ICON_ID);
    }

    public void testThumbnailImageUsingResourceName() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_icnm_l", VALID_RESOURCE_NAME);

        Bitmap fakeBitmap = getFakeBitmap();
        when(mpPushSpy.getBitmapFromResourceId(VALID_RESOURCE_ID)).thenReturn(fakeBitmap);

        mpPushSpy.createNotification(intent);

        verify(mpPushSpy).getBitmapFromResourceId(VALID_RESOURCE_ID);
        verify(builderSpy).setLargeIcon(fakeBitmap);
    }

    public void testThumbnailImageUsingInvalidResourceName() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_icnm_l", INVALID_RESOURCE_NAME);
        mpPushSpy.createNotification(intent);
        verify(builderSpy, never()).setLargeIcon(any(Bitmap.class));
    }

    public void testNoThumbnailImage() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        mpPushSpy.createNotification(intent);
        verify(builderSpy, never()).setLargeIcon(any(Bitmap.class));
    }

    public void testIconColor() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_color", "#ff9900");
        mpPushSpy.createNotification(intent);
        verify(builderSpy).setColor(Color.parseColor("#ff9900"));
    }

    public void testNoIconColor() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        mpPushSpy.createNotification(intent);
        verify(builderSpy, never()).setColor(anyInt());
    }

    public void testCTA() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_cta", "http://mixpanel.com");
        mpPushSpy.createNotification(intent);
        verify(mpPushSpy).buildIntentForUri(argThat(new URIMatcher("http://mixpanel.com")));
        verify(mpPushSpy, never()).buildNotificationIntent(DEFAULT_INTENT, null, null, null);
    }

    public void testCTAWithCampaignMetadata() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_message_id", "1");
        intent.putExtra("mp_campaign_id", "2");
        intent.putExtra("mp", "some_extra_data");
        intent.putExtra("mp_cta", "http://mixpanel.com");
        Intent fakeIntent = new Intent(Intent.ACTION_PROCESS_TEXT);
        when(mpPushSpy.buildIntentForUri(any(Uri.class))).thenReturn(fakeIntent);
        mpPushSpy.createNotification(intent);
        verify(mpPushSpy).buildNotificationIntent(fakeIntent, "2", "1", "some_extra_data");
    }

    public void testNoCTA() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        mpPushSpy.createNotification(intent);
        verify(mpPushSpy).buildNotificationIntent(DEFAULT_INTENT, null, null, null);
    }

    private static final class URIMatcher implements ArgumentMatcher<Uri> {
        public URIMatcher(String expectedUri) {
            this.expectedUri = expectedUri;
        }

        @Override
        public boolean matches(Uri uri) {
            return uri.toString().equals(expectedUri);
        }

        String expectedUri;
    }

    private Bitmap getFakeBitmap() {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8);
    }

    private ResourceIds getTestResources() {
        final Map<String, Integer> resources = new HashMap<>();
        resources.put(VALID_RESOURCE_NAME, VALID_RESOURCE_ID);
        return new TestUtils.TestResourceIds(resources);
    }

    private long now;
    private Notification.Builder builderSpy;
    private MixpanelPushNotification mpPushSpy;
}
