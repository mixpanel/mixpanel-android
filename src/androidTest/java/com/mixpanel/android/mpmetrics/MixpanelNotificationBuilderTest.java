package com.mixpanel.android.mpmetrics;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.test.AndroidTestCase;

import org.mockito.ArgumentMatcher;

import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class MixpanelNotificationBuilderTest extends AndroidTestCase {

    private final String VALID_RESOURCE_NAME = "com_mixpanel_android_logo";
    private final int VALID_RESOURCE_ID = R.drawable.com_mixpanel_android_logo;
    private final String VALID_IMAGE_URL = "https://dev.images.mxpnl.com/1939595/fc767ba09b1a5420bdbcee71c7ae9904.png";
    private final String INVALID_IMAGE_URL = "http:/badurl";
    private final String INVALID_RESOURCE_NAME = "NOT A VALID RESOURCE";
    private final String DEFAULT_TITLE = "DEFAULT TITLE";
    private final static String VISIBILITY_SECRET = "VISIBILITY_SECRET";
    private final int DEFAULT_ICON_ID = android.R.drawable.sym_def_app_icon;
    private Context context;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        System.setProperty("org.mockito.android.target", getContext().getCacheDir().getPath());

        this.context = getContext();

        now = System.currentTimeMillis();
        builderSpy = spy(new Notification.Builder(getContext()));
        mpPushSpy = spy(new MixpanelPushNotification(context, builderSpy, now) {
            protected ResourceIds getResourceIds(Context context) {
                return getTestResources();
            }
        });

        when(mpPushSpy.getDefaultTitle()).thenReturn(DEFAULT_TITLE);
        when(mpPushSpy.getDefaultIcon()).thenReturn(DEFAULT_ICON_ID);
    }

    public void testNotificationEmptyIntent() {
        Notification notification = mpPushSpy.createNotification(new Intent());
        assertNull(notification);
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
        MixpanelNotificationData.PushTapAction fakeOnTap = new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapActionType.fromString("browser"), "http://mixpanel.com");
        PushTapActionMatcher matchesFakeOnTap = new PushTapActionMatcher(fakeOnTap);

        mpPushSpy.createNotification(intent);
        verify(mpPushSpy).buildOnTap(null);
        verify(mpPushSpy).getRoutingIntent(argThat(matchesFakeOnTap));
    }

    public void testCTAWithCampaignMetadata() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_message_id", "1");
        intent.putExtra("mp_campaign_id", "2");
        intent.putExtra("mp", "some_extra_data");
        intent.putExtra("mp_cta", "http://mixpanel.com");

        MixpanelNotificationData.PushTapAction fakeOnTap = new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapActionType.fromString("browser"), "http://mixpanel.com");
        PushTapActionMatcher matchesFakeOnTap = new PushTapActionMatcher(fakeOnTap);
        mpPushSpy.createNotification(intent);

        verify(mpPushSpy).getRoutingIntent(argThat(matchesFakeOnTap));
        verify(mpPushSpy).buildBundle(argThat(matchesFakeOnTap));
    }

    public void testNoCTA() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");

        MixpanelNotificationData.PushTapAction fakeOnTap = new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapActionType.fromString("homescreen"));
        PushTapActionMatcher matchesFakeOnTap = new PushTapActionMatcher(fakeOnTap);
        mpPushSpy.createNotification(intent);

        verify(mpPushSpy).buildOnTap(null);
        verify(mpPushSpy).buildOnTapFromURI(null);
        verify(mpPushSpy).getRoutingIntent(argThat(matchesFakeOnTap));
        verify(mpPushSpy).buildBundle(argThat(matchesFakeOnTap));
    }

    public void testOnTapHomescreen() {
        final Intent intent = new Intent();
        final String onTap = "{\"type\": \"homescreen\"}";
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_ontap", onTap);

        MixpanelNotificationData.PushTapAction fakeOnTap = new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapActionType.fromString("homescreen"));
        PushTapActionMatcher matchesFakeOnTap = new PushTapActionMatcher(fakeOnTap);
        mpPushSpy.createNotification(intent);

        verify(mpPushSpy).buildOnTap(onTap);
        verify(mpPushSpy, never()).buildOnTapFromURI(nullable(String.class));
        verify(mpPushSpy, never()).getDefaultOnTap();
        verify(mpPushSpy).buildNotificationFromData();
        verify(mpPushSpy).getRoutingIntent(argThat(matchesFakeOnTap));
        verify(mpPushSpy).buildBundle(argThat(matchesFakeOnTap));

        Bundle options = mpPushSpy.buildBundle(fakeOnTap);
        assertEquals(options.getString("mp_tap_target"), "notification");
        assertEquals(options.getString("mp_tap_action_type"), MixpanelNotificationData.PushTapActionType.HOMESCREEN.toString());
    }

    public void testOnTapBrowser() {
        final Intent intent = new Intent();
        final String onTap = "{\"type\": \"browser\", \"uri\": \"http://mixpanel.com\"}";
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_ontap", onTap);

        MixpanelNotificationData.PushTapAction fakeOnTap = new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapActionType.fromString("browser"), "http://mixpanel.com");
        PushTapActionMatcher matchesFakeOnTap = new PushTapActionMatcher(fakeOnTap);
        mpPushSpy.createNotification(intent);

        verify(mpPushSpy).buildOnTap(onTap);
        verify(mpPushSpy, never()).buildOnTapFromURI(nullable(String.class));
        verify(mpPushSpy, never()).getDefaultOnTap();
        verify(mpPushSpy).buildNotificationFromData();
        verify(mpPushSpy).getRoutingIntent(argThat(matchesFakeOnTap));
        verify(mpPushSpy).buildBundle(argThat(matchesFakeOnTap));

        Bundle options = mpPushSpy.buildBundle(fakeOnTap);
        assertEquals(options.getString("mp_tap_target"), "notification");
        assertEquals(options.getString("mp_tap_action_type"), MixpanelNotificationData.PushTapActionType.URL_IN_BROWSER.toString());
    }

    public void testOnTapDeeplink() {
        final Intent intent = new Intent();
        final String onTap = "{\"type\": \"deeplink\", \"uri\": \"my-app://action2\"}";
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_ontap", onTap);

        MixpanelNotificationData.PushTapAction fakeOnTap = new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapActionType.fromString("deeplink"), "my-app://action2");
        PushTapActionMatcher matchesFakeOnTap = new PushTapActionMatcher(fakeOnTap);
        mpPushSpy.createNotification(intent);

        verify(mpPushSpy).buildOnTap(onTap);
        verify(mpPushSpy, never()).buildOnTapFromURI(nullable(String.class));
        verify(mpPushSpy, never()).getDefaultOnTap();
        verify(mpPushSpy).buildNotificationFromData();
        verify(mpPushSpy).getRoutingIntent(argThat(matchesFakeOnTap));
        verify(mpPushSpy).buildBundle(argThat(matchesFakeOnTap));

        Bundle options = mpPushSpy.buildBundle(fakeOnTap);
        assertEquals(options.getString("mp_tap_target"), "notification");
        assertEquals(options.getString("mp_tap_action_type"), MixpanelNotificationData.PushTapActionType.DEEP_LINK.toString());
    }

    public void testOnTapError() {
        final Intent intent = new Intent();
        final String onTap = "{\"type\": \"badtype\", \"uri\": \"my-app://action2\"}";
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_ontap", onTap);

        MixpanelNotificationData.PushTapAction fakeOnTap = new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapActionType.fromString("badtype"), "my-app://action2");
        mpPushSpy.createNotification(intent);

        verify(mpPushSpy).buildOnTap(onTap);
        assertFalse(mpPushSpy.isValid());
    }

    public void testNoOnTap() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");

        MixpanelNotificationData.PushTapAction fakeOnTap = new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapActionType.fromString("homescreen"));
        PushTapActionMatcher matchesFakeOnTap = new PushTapActionMatcher(fakeOnTap);
        mpPushSpy.createNotification(intent);

        verify(mpPushSpy).buildOnTap(null);
        verify(mpPushSpy).buildOnTapFromURI(null);
        verify(mpPushSpy).getDefaultOnTap();
        verify(mpPushSpy).buildNotificationFromData();
        verify(mpPushSpy).getRoutingIntent(argThat(matchesFakeOnTap));
        verify(mpPushSpy).buildBundle(argThat(matchesFakeOnTap));

        Bundle options = mpPushSpy.buildBundle(fakeOnTap);
        assertEquals(options.getString("mp_tap_target"), "notification");
        assertEquals(options.getString("mp_tap_action_type"), MixpanelNotificationData.PushTapActionType.HOMESCREEN.toString());
    }

    public void testActionButtons() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            final Intent intent = new Intent();
            intent.putExtra("mp_message", "MESSAGE");
            intent.putExtra("mp_buttons", "[{\"id\": \"id1\", \"lbl\": \"Button 1\", \"ontap\": {\"type\": \"homescreen\"}}, {\"id\": \"id2\", \"lbl\": \"Button 2\", \"ontap\": {\"type\": \"deeplink\", \"uri\": \"my-app://action2\"}}, {\"id\": \"id3\", \"lbl\": \"Button 3\", {\"type\": \"browser\", \"uri\": \"http://mixpanel.com\"}}]");

            MixpanelNotificationData.PushTapAction fakeOnTap1 = new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapActionType.HOMESCREEN);
            MixpanelNotificationData.PushTapAction fakeOnTap2 = new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapActionType.DEEP_LINK, "my-app://action2");
            MixpanelNotificationData.PushTapAction fakeOnTap3 = new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapActionType.URL_IN_BROWSER, "http://mixpanel.com");

            List<MixpanelNotificationData.MixpanelNotificationButtonData> fakeButtonList = new ArrayList<>();
            fakeButtonList.add(new MixpanelNotificationData.MixpanelNotificationButtonData("Button 1", new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapActionType.HOMESCREEN, null), "id1"));
            fakeButtonList.add(new MixpanelNotificationData.MixpanelNotificationButtonData("Button 2", new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapActionType.DEEP_LINK, "my-app://action2"), "id2"));
            fakeButtonList.add(new MixpanelNotificationData.MixpanelNotificationButtonData("Button 3", new MixpanelNotificationData.PushTapAction(MixpanelNotificationData.PushTapActionType.URL_IN_BROWSER, "http://mixpanel.com"), "id3"));

            when(mpPushSpy.buildButtons(intent.getStringExtra("mp_buttons"))).thenReturn(fakeButtonList);
            mpPushSpy.createNotification(intent);

            PushTapActionMatcher matchesFakeOnTap = new PushTapActionMatcher(fakeOnTap1);
            verifyButtonAction(matchesFakeOnTap, fakeButtonList.get(0), 1);

            matchesFakeOnTap = new PushTapActionMatcher(fakeOnTap2);
            verifyButtonAction(matchesFakeOnTap, fakeButtonList.get(1), 2);

            matchesFakeOnTap = new PushTapActionMatcher(fakeOnTap3);
            verifyButtonAction(matchesFakeOnTap, fakeButtonList.get(2), 3);

            verify(builderSpy, times(3)).addAction(any(Notification.Action.class));
            verify(mpPushSpy, times(4)).buildBundle(any(MixpanelNotificationData.PushTapAction.class));
        }
    }

    private void verifyButtonAction(PushTapActionMatcher matchesFakeOnTap, MixpanelNotificationData.MixpanelNotificationButtonData buttonData, int index) {
        verify(mpPushSpy, atLeastOnce()).createAction(buttonData.getLabel(), buttonData.getOnTap(), buttonData.getId(), index);
        verify(mpPushSpy).getRoutingIntent(argThat(matchesFakeOnTap), eq(buttonData.getId()), eq(buttonData.getLabel()));
        verify(mpPushSpy).buildBundle(argThat(matchesFakeOnTap), eq(buttonData.getId()), eq(buttonData.getLabel()));
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
        intent.putExtra("mp_bdgcnt", "2");
        mpPushSpy.createNotification(intent);
        verify(builderSpy).setNumber(2);
    }

    public void testInvalidNotificationBadge() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_bdgcnt", "0");
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
            intent.putExtra("mp_visibility", MixpanelNotificationBuilderTest.VISIBILITY_SECRET);
            Notification notification = mpPushSpy.createNotification(intent);
            verify(builderSpy).setVisibility(Notification.VISIBILITY_SECRET);
            assertEquals(notification.visibility, Notification.VISIBILITY_SECRET);
        }
    }

    public void testDefaultVisibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Intent intent = new Intent();
            intent.putExtra("mp_message", "MESSAGE");
            Notification notification = mpPushSpy.createNotification(intent);
            verify(builderSpy).setVisibility(Notification.VISIBILITY_PRIVATE);
            assertEquals(notification.visibility, Notification.VISIBILITY_PRIVATE);
        }
    }

    private static final class PushTapActionMatcher implements ArgumentMatcher<MixpanelNotificationData.PushTapAction> {
        public PushTapActionMatcher(MixpanelNotificationData.PushTapAction expectedAction) { this.expectedAction = expectedAction; }

        @Override
        public boolean matches(MixpanelNotificationData.PushTapAction action) {
            return action.getActionType() == expectedAction.getActionType() && action.getUri() == null && expectedAction.getUri() == null ||
                    action.getActionType() == expectedAction.getActionType() && action.getUri().equals(expectedAction.getUri());
        }

        MixpanelNotificationData.PushTapAction expectedAction;
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
