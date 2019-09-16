package com.mixpanel.android.mpmetrics;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.test.AndroidTestCase;

import org.mockito.ArgumentMatcher;

import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

public class FCMMessagingServiceTest extends AndroidTestCase {

    @Override
    public void setUp() throws PackageManager.NameNotFoundException {
        System.setProperty("org.mockito.android.target", getContext().getCacheDir().getPath());

        // ACTION_BUG_REPORT is chosen because it's identifiably weird
        mDefaultIntent = new Intent(Intent.ACTION_BUG_REPORT);
        final Map<String, Integer> resources = new HashMap<String, Integer>();
        resources.put("ic_pretend_icon", 12345);
        resources.put("com_mixpanel_android_logo", R.drawable.com_mixpanel_android_logo);
        mResourceIds = new TestUtils.TestResourceIds(resources);
        mGoodUri = Uri.parse("http://mixpanel.com");

        final PackageManager manager = getContext().getPackageManager();
        final ApplicationInfo appInfo = manager.getApplicationInfo(getContext().getPackageName(), 0);
        mDefaultIcon = appInfo.icon;
        mDefaultTitle = manager.getApplicationLabel(appInfo);
    }

    public void testNotificationEmptyIntent() {
        final Intent intent = new Intent();
        assertNull(MixpanelFCMMessagingService.readInboundIntent(this.getContext(), intent, mResourceIds, mDefaultIntent));
    }

    public void testCompleteNotification() {

        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_icnm", "ic_pretend_icon");
        intent.putExtra("mp_icnm_l", "com_mixpanel_android_logo");
        intent.putExtra("mp_color", "#ff9900");
        intent.putExtra("mp_title", "TITLE");
        intent.putExtra("mp_cta", mGoodUri.toString());
        intent.putExtra("mp_buttons", "[{\"lbl\": \"Button 1\", \"uri\": \"my-app://action\"}, {\"icnm\": \"ic_pretend_icon\", \"lbl\": \"Button 2\", \"uri\": \"my-app://action2\"}, {\"lbl\": \"Button 3\", \"uri\": \"https://mixpanel.com\"}]");

        long now = System.currentTimeMillis();

        MixpanelFCMMessagingService.NotificationData notificationData = MixpanelFCMMessagingService.readInboundIntent(this.getContext(), intent, mResourceIds, mDefaultIntent);
        Notification.Builder builder = spy(new Notification.Builder(getContext()));

        MixpanelFCMMessagingService.buildNotification(getContext(), intent, builder, mResourceIds, now);

        verifyBasicNotification(builder, now);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            verifyExpandableNotification(builder);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            verifyButtons(notificationData, builder);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            verifyCustomIconColor(builder);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verifyChannelSet(builder);
        }
    }

    private void verifyBasicNotification(Notification.Builder builder, long now) {
        verify(builder).setDefaults(MPConfig.getInstance(getContext()).getNotificationDefaults());
        verify(builder).setWhen(now);
        verify(builder).setContentTitle("TITLE");
        verify(builder).setContentText("MESSAGE");
        verify(builder).setTicker("MESSAGE");
        verify(builder).setContentIntent(any(PendingIntent.class));
        verify(builder).setSmallIcon(12345);
        verify(builder).setLargeIcon(any(Bitmap.class));
    }

    private void verifyExpandableNotification(Notification.Builder builder) {
        // setStyle is also called internally by Notifications.class so we expect it to be called twice.
        verify(builder, times(2)).setStyle(any(Notification.BigTextStyle.class));
    }

    private void verifyCustomIconColor(Notification.Builder builder) {
        verify(builder).setColor(Color.parseColor("#ff9900"));
    }

    private void verifyButtons(MixpanelFCMMessagingService.NotificationData data, Notification.Builder builder) {
        assertEquals(data.buttons.get(0).icon, -1);
        assertEquals(data.buttons.get(0).label, "Button 1");
        assertEquals("my-app://action", data.buttons.get(0).uri);

        assertEquals(data.buttons.get(1).icon, 12345);
        assertEquals(data.buttons.get(1).label, "Button 2");
        assertEquals("my-app://action2", data.buttons.get(1).uri);

        assertEquals(data.buttons.get(2).icon, -1);
        assertEquals(data.buttons.get(2).label, "Button 3");
        assertEquals("https://mixpanel.com", data.buttons.get(2).uri);

        verify(builder, times(3)).addAction(any(Notification.Action.class));
        verify(builder, atLeastOnce()).addAction(argThat(new ExpectedAction("Button 1", -1)));
        verify(builder, atLeastOnce()).addAction(argThat(new ExpectedAction("Button 2", 12345)));
        verify(builder, atLeastOnce()).addAction(argThat(new ExpectedAction("Button 3", -1)));
    }

    private static final class ExpectedAction implements ArgumentMatcher<Notification.Action> {
        private String expectedTitle;
        private int expectedIconId;

        public ExpectedAction(String expectedTitle, int expectedIconId) {
            this.expectedTitle = expectedTitle;
            this.expectedIconId = expectedIconId;
        }

        @Override
        public boolean matches(Notification.Action action) {
            boolean titleMatches = action.title.equals(this.expectedTitle);
            boolean iconMatches = this.expectedIconId == action.icon;
            return titleMatches && iconMatches;
        }
    }


    private void verifyChannelSet(Notification.Builder builder) {
        verify(builder).setChannelId(anyString());
    }

    public void testMinimalNotification(){
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        final MixpanelFCMMessagingService.NotificationData created = MixpanelFCMMessagingService.readInboundIntent(getContext(), intent, mResourceIds, mDefaultIntent);
        assertEquals(created.icon, mDefaultIcon);
        assertEquals(created.title, mDefaultTitle);
        assertEquals(created.message, "MESSAGE");
        assertNull(created.intent.getData());
    }

    public void testBadIconNotification() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_icnm", "NO SUCH ICON");
        final MixpanelFCMMessagingService.NotificationData created = MixpanelFCMMessagingService.readInboundIntent(getContext(), intent, mResourceIds, mDefaultIntent);

        assertEquals(created.icon, mDefaultIcon);
    }

    public void testBadUri() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_cta", (String) null);
        final MixpanelFCMMessagingService.NotificationData created = MixpanelFCMMessagingService.readInboundIntent(getContext(), intent, mResourceIds, mDefaultIntent);
        assertNull(created.intent.getData());
    }

    private CharSequence mDefaultTitle;
    private int mDefaultIcon;
    private Intent mDefaultIntent;
    private ResourceIds mResourceIds;
    private Uri mGoodUri;
}
