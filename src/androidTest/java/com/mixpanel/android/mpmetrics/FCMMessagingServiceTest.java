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
        final Intent defaultIntent = new Intent(Intent.ACTION_BUG_REPORT);
        mFCMMessaging = new TestFCMMessagingService(defaultIntent);
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
        assertNull(mFCMMessaging.readInboundIntent(this.getContext(), intent, mResourceIds));
    }

    public void testCompleteNotification() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_icnm", "ic_pretend_icon");
        intent.putExtra("mp_title", "TITLE");
        intent.putExtra("mp_cta", mGoodUri.toString());
        final MixpanelFCMMessagingService.NotificationData created = mFCMMessaging.readInboundIntent(getContext(), intent, mResourceIds);

        assertEquals(created.icon, 12345);
        assertEquals(created.title, "TITLE");
        assertEquals(created.message, "MESSAGE");
        assertEquals(Intent.ACTION_VIEW, created.intent.getAction());
        assertEquals(mGoodUri, created.intent.getData());
    }

    public void testMinimalNotification(){
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        final MixpanelFCMMessagingService.NotificationData created = mFCMMessaging.readInboundIntent(getContext(), intent, mResourceIds);
        assertEquals(created.icon, mDefaultIcon);
        assertEquals(created.title, mDefaultTitle);
        assertEquals(created.message, "MESSAGE");
        assertNull(created.intent.getData());
    }

    public void testBadIconNotification() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_icnm", "NO SUCH ICON");
        final MixpanelFCMMessagingService.NotificationData created = mFCMMessaging.readInboundIntent(getContext(), intent, mResourceIds);

        assertEquals(created.icon, mDefaultIcon);
    }

    public void testBadUri() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_cta", (String) null);
        final MixpanelFCMMessagingService.NotificationData created = mFCMMessaging.readInboundIntent(getContext(), intent, mResourceIds);
        assertNull(created.intent.getData());
    }

    private static class TestFCMMessagingService extends MixpanelFCMMessagingService {
        public TestFCMMessagingService(Intent aDummy) {
            dummyIntent = aDummy;
        }

        @Override
        public Intent getDefaultIntent(Context context) {
            return dummyIntent;
        }

        public final Intent dummyIntent;
    }

    private CharSequence mDefaultTitle;
    private int mDefaultIcon;
    private TestFCMMessagingService mFCMMessaging;
    private ResourceIds mResourceIds;
    private Uri mGoodUri;
}
