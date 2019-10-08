package com.mixpanel.android.mpmetrics;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.test.AndroidTestCase;

import org.mockito.ArgumentMatcher;

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class MixpanelNotificationBuilderTest extends AndroidTestCase {

    private final String VALID_RESOURCE_NAME = "com_mixpanel_android_logo";
    private final int VALID_RESOURCE_ID = R.drawable.com_mixpanel_android_logo;
    private final String VALID_IMAGE_URL = "https://dev.images.mxpnl.com/1939595/fc767ba09b1a5420bdbcee71c7ae9904.png";
    private final String INVALID_IMAGE_URL = "http:/badurl";
    private final String INVALID_RESOURCE_NAME = "NOT A VALID RESOURCE";
    private final String DEFAULT_TITLE = "DEFAULT TITLE";
    private final int DEFAULT_ICON_ID = android.R.drawable.sym_def_app_icon;
    private final Intent DEFAULT_INTENT = new Intent(Intent.ACTION_BUG_REPORT); // ACTION_BUG_REPORT is chosen because it's identifiably weird
    private Context context;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        System.setProperty("org.mockito.android.target", getContext().getCacheDir().getPath());

        this.context = getContext();

        now = System.currentTimeMillis();
        builderSpy = spy(new Notification.Builder(getContext()));
        mpPushSpy = spy(new MixpanelPushNotification(context, builderSpy, now) {
            @Override
            protected ResourceIds getResourceIds(Context context) {
                return getTestResources();
            }
        });

        when(mpPushSpy.getDefaultTitle()).thenReturn(DEFAULT_TITLE);
        when(mpPushSpy.getDefaultIcon()).thenReturn(DEFAULT_ICON_ID);
        when(mpPushSpy.getDefaultIntent()).thenReturn(DEFAULT_INTENT);
    }

    public void testNotificationEmptyIntent() {
        mpPushSpy.parseIntent(new Intent());
        assertNull(mpPushSpy.getData());
    }

    public void testBasicNotification() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_title", "TITLE");
        mpPushSpy.createNotification(intent);

        verify(builderSpy).setShowWhen(true);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Intent intent = new Intent();
            intent.putExtra("mp_message", "MESSAGE");
            intent.putExtra("mp_icnm", VALID_RESOURCE_NAME);
            mpPushSpy.createNotification(intent);
            verify(builderSpy).setSmallIcon(VALID_RESOURCE_ID);
        }
    }

    public void testNoIcon() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Intent intent = new Intent();
            intent.putExtra("mp_message", "MESSAGE");
            mpPushSpy.createNotification(intent);
            verify(builderSpy).setSmallIcon(DEFAULT_ICON_ID);
        }
    }

    public void testInvalidIcon() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Intent intent = new Intent();
            intent.putExtra("mp_message", "MESSAGE");
            intent.putExtra("mp_icnm", INVALID_RESOURCE_NAME);
            mpPushSpy.createNotification(intent);
            verify(builderSpy).setSmallIcon(DEFAULT_ICON_ID);
        }
    }

    public void testExpandedImageUsingValidUrl() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            final Intent intent = new Intent();
            intent.putExtra("mp_message", "MESSAGE");
            intent.putExtra("mp_img", VALID_IMAGE_URL);

            Bitmap fakeBitmap = getFakeBitmap();

            when(mpPushSpy.getBitmapFromUrl(VALID_IMAGE_URL)).thenReturn(fakeBitmap);

            mpPushSpy.createNotification(intent);
            verify(mpPushSpy).getBitmapFromUrl(VALID_IMAGE_URL);
            verify(mpPushSpy).setBigPictureStyle(fakeBitmap);
        }
    }

    public void testExpandedImageUsingInvalidUrl() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            final Intent intent = new Intent();
            intent.putExtra("mp_message", "MESSAGE");
            intent.putExtra("mp_img", INVALID_IMAGE_URL);

            when(mpPushSpy.getBitmapFromUrl(INVALID_IMAGE_URL)).thenReturn(null);

            mpPushSpy.createNotification(intent);
            verify(mpPushSpy).getBitmapFromUrl(INVALID_IMAGE_URL);
            verify(mpPushSpy).setBigTextStyle("MESSAGE");
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            final Intent intent = new Intent();
            intent.putExtra("mp_message", "MESSAGE");
            intent.putExtra("mp_color", "#ff9900");
            mpPushSpy.createNotification(intent);
            verify(builderSpy).setColor(Color.parseColor("#ff9900"));
        }
    }

    public void testNoIconColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            final Intent intent = new Intent();
            intent.putExtra("mp_message", "MESSAGE");
            mpPushSpy.createNotification(intent);
            verify(builderSpy, never()).setColor(anyInt());
        }
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

    public void testActionButtons() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            final Intent intent = new Intent();
            intent.putExtra("mp_message", "MESSAGE");
            intent.putExtra("mp_buttons", "[{\"lbl\": \"Button 1\", \"uri\": \"my-app://action\"}, {\"icnm\": \"" + VALID_RESOURCE_NAME + "\", \"lbl\": \"Button 2\", \"uri\": \"my-app://action2\"}, {\"lbl\": \"Button 3\", \"uri\": \"https://mixpanel.com\", \"icnm\": \"" + INVALID_RESOURCE_NAME + "\"}]");
            mpPushSpy.createNotification(intent);

            verify(mpPushSpy, atLeastOnce()).createAction(-1, "Button 1", "my-app://action");
            verify(mpPushSpy, atLeastOnce()).createAction(VALID_RESOURCE_ID, "Button 2", "my-app://action2");
            verify(mpPushSpy, atLeastOnce()).createAction(-1, "Button 3", "https://mixpanel.com");
            verify(builderSpy, times(3)).addAction(any(Notification.Action.class));
        }
    }

    public void testNoActionButtons() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            final Intent intent = new Intent();
            intent.putExtra("mp_message", "MESSAGE");
            mpPushSpy.createNotification(intent);
            verify(builderSpy, never()).addAction(any(Notification.Action.class));
        }
    }

    public void testValidNotificationBadge() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_bdgcnt", 2);
        mpPushSpy.createNotification(intent);
        verify(builderSpy).setNumber(2);
    }

    public void testInvalidNotificationBadge() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_bdgcnt", 0);
        mpPushSpy.createNotification(intent);
        verify(builderSpy, never()).setNumber(any(Integer.class));
    }

    public void testChannelId() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final Intent intent = new Intent();
            intent.putExtra("mp_message", "MESSAGE");
            intent.putExtra("mp_channel_id", "12345");
            Notification notification = mpPushSpy.createNotification(intent);
            verify(builderSpy).setChannelId("12345");
            assertEquals(notification.getChannelId(), "12345");
        }
    }

    public void testNoChannelId() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final Intent intent = new Intent();
            intent.putExtra("mp_message", "MESSAGE");
            Notification notification = mpPushSpy.createNotification(intent);
            verify(builderSpy).setChannelId(MixpanelNotificationData.DEFAULT_CHANNEL_ID);
            assertEquals(notification.getChannelId(), MixpanelNotificationData.DEFAULT_CHANNEL_ID);
        }
    }

    public void testSubText() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            final Intent intent = new Intent();
            intent.putExtra("mp_message", "MESSAGE");
            intent.putExtra("mp_subtxt", "SUBTEXT");
            mpPushSpy.createNotification(intent);
            verify(builderSpy).setSubText("SUBTEXT");
        }
    }

    public void testNoSubText() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            final Intent intent = new Intent();
            intent.putExtra("mp_message", "MESSAGE");
            mpPushSpy.createNotification(intent);
            verify(builderSpy, never()).setSubText(any(String.class));
        }
    }

    public void testTicker() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_ticker", "TICK");
        mpPushSpy.createNotification(intent);
        verify(builderSpy).setTicker("TICK");
    }

    public void testNoTicker() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        mpPushSpy.createNotification(intent);
        verify(builderSpy).setTicker("MESSAGE");
    }

    public void testSticky() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_sticky", "true");
        Notification notification = mpPushSpy.createNotification(intent);
        int flag = notification.flags;
        assertTrue((flag | Notification.FLAG_AUTO_CANCEL) != flag);
    }

    public void testNoSticky() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        Notification notification = mpPushSpy.createNotification(intent);
        int flag = notification.flags;
        assertEquals(flag | Notification.FLAG_AUTO_CANCEL, flag);
    }

    public void testTimestamp() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_time", "2014-10-02T15:01:23+0000");

        mpPushSpy.createNotification(intent);

        verify(builderSpy).setWhen(1412262083000L);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            verify(builderSpy).setShowWhen(true);
        }
    }

    public void testTimestampUTC() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_time", "2014-10-02T15:01:23+0000");
        mpPushSpy.createNotification(intent);
        verify(builderSpy).setWhen(1412262083000L);
    }

    public void testTimestampZulu() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_time", "2014-10-02T15:01:23Z");
        mpPushSpy.createNotification(intent);
        verify(builderSpy).setWhen(1412262083000L);
    }

    public void testTimestampCentral() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_time", "2014-10-02T15:01:23-0500");
        mpPushSpy.createNotification(intent);
        verify(builderSpy).setWhen(1412280083000L);
    }

    public void testTimestampUserLocal() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        intent.putExtra("mp_time", "2014-10-02T15:01:23");
        mpPushSpy.createNotification(intent);
        verify(builderSpy).setWhen(1412287283000L);
    }

    public void testNoTimestamp() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        verify(builderSpy, never()).setWhen(any(Long.class));
    }

    public void testVisibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Intent intent = new Intent();
            intent.putExtra("mp_message", "MESSAGE");
            intent.putExtra("mp_visibility", Notification.VISIBILITY_SECRET);
            Notification notification = mpPushSpy.createNotification(intent);
            verify(builderSpy).setVisibility(Notification.VISIBILITY_SECRET);
        }
    }

    public void testDefaultVisibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Intent intent = new Intent();
            intent.putExtra("mp_message", "MESSAGE");
            Notification notification = mpPushSpy.createNotification(intent);
            verify(builderSpy).setVisibility(Notification.VISIBILITY_PRIVATE);
        }
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
