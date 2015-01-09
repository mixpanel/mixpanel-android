package com.mixpanel.android.mpmetrics;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.test.AndroidTestCase;

import java.util.HashMap;
import java.util.Map;

public class GCMReceiverTest extends AndroidTestCase {
    @Override
    public void setUp() throws PackageManager.NameNotFoundException {
        // ACTION_BUG_REPORT is chosen because it's identifiably weird
        final Intent defaultIntent = new Intent(Intent.ACTION_BUG_REPORT);
        mGcmReceiver = new TestGCMReceiver(defaultIntent);
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
        assertNull(mGcmReceiver.readInboundIntent(this.getContext(), intent, mResourceIds));
    }

    public void testCompleteNotification() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_icnm", "ic_pretend_icon");
        intent.putExtra("mp_title", "TITLE");
        intent.putExtra("mp_cta", mGoodUri.toString());
        final GCMReceiver.NotificationData created = mGcmReceiver.readInboundIntent(getContext(), intent, mResourceIds);

        assertEquals(created.icon, 12345);
        assertEquals(created.title, "TITLE");
        assertEquals(created.message, "MESSAGE");
        assertEquals(Intent.ACTION_VIEW, created.intent.getAction());
        assertEquals(mGoodUri, created.intent.getData());
    }

    public void testMinimalNotification(){
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        final GCMReceiver.NotificationData created = mGcmReceiver.readInboundIntent(getContext(), intent, mResourceIds);
        assertEquals(created.icon, mDefaultIcon);
        assertEquals(created.title, mDefaultTitle);
        assertEquals(created.message, "MESSAGE");
        assertNull(created.intent.getData());
    }

    public void testBadIconNotification() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_icnm", "NO SUCH ICON");
        final GCMReceiver.NotificationData created = mGcmReceiver.readInboundIntent(getContext(), intent, mResourceIds);

        assertEquals(created.icon, mDefaultIcon);
    }

    public void testBadUri() {
        final Intent intent = new Intent();
        intent.putExtra("mp_message", "MESSAGE");
        intent.putExtra("mp_cta", (String) null);
        final GCMReceiver.NotificationData created = mGcmReceiver.readInboundIntent(getContext(), intent, mResourceIds);
        assertNull(created.intent.getData());
    }

    private static class TestGCMReceiver extends GCMReceiver {
        public TestGCMReceiver(Intent aDummy) {
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
    private GCMReceiver mGcmReceiver;
    private ResourceIds mResourceIds;
    private Uri mGoodUri;
}
