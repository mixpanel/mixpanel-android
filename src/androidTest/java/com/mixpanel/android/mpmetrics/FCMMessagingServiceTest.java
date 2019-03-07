package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.test.AndroidTestCase;

import java.util.HashMap;
import java.util.Map;

public class FCMMessagingServiceTest extends AndroidTestCase {
    @Override
    public void setUp() throws PackageManager.NameNotFoundException {
        // ACTION_BUG_REPORT is chosen because it's identifiably weird
        mDefaultIntent = new Intent(Intent.ACTION_BUG_REPORT);
        final Map<String, Integer> resources = new HashMap<String, Integer>();
        resources.put("ic_pretend_icon", 12345);
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
        intent.putExtra("mp_title", "TITLE");
        intent.putExtra("mp_cta", mGoodUri.toString());
        final MixpanelFCMMessagingService.NotificationData created = MixpanelFCMMessagingService.readInboundIntent(getContext(), intent, mResourceIds, mDefaultIntent);

        assertEquals(created.icon, 12345);
        assertEquals(created.title, "TITLE");
        assertEquals(created.message, "MESSAGE");
        assertEquals(Intent.ACTION_VIEW, created.intent.getAction());
        assertEquals(mGoodUri, created.intent.getData());
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
