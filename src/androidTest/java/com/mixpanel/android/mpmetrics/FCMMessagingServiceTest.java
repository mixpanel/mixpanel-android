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
        long now = System.currentTimeMillis();
        Notification.Builder builder = spy(new Notification.Builder(getContext()));

        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_icnm", "ic_pretend_icon");
        intent.putExtra("mp_icnm_l", "com_mixpanel_android_logo");
        intent.putExtra("mp_color", "#ff9900");
        intent.putExtra("mp_title", "TITLE");
        intent.putExtra("mp_cta", mGoodUri.toString());

        MixpanelFCMMessagingService.buildNotification(getContext(), intent, builder, mResourceIds, now);

        verifyBasicNotification(builder, now);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            verifyExpandableNotification(builder);
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
