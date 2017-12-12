package com.mixpanel.android.mpmetrics;

import android.test.AndroidTestCase;

import com.mixpanel.android.viewcrawler.UpdatesFromMixpanel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class DecideMessagesTest extends AndroidTestCase {

    @Override
    public void setUp() throws JSONException, BadDecideObjectException {

        mListenerCalls = new LinkedBlockingQueue<String>();
        mMockListener = new DecideMessages.OnNewResultsListener() {
            @Override
            public void onNewResults() {
                mListenerCalls.add("CALLED");
            }

            @Override
            public void onNewConnectIntegrations() {
                mListenerCalls.add("CONNECT INTEGRATIONS");
            }
        };

        mMockUpdates = new UpdatesFromMixpanel() {
            @Override
            public void startUpdates() {
                ; // do nothing
            }

            @Override
            public void applyPersistedUpdates() {
            }

            @Override
            public void storeVariants(JSONArray variants) {
            }

            @Override
            public void setEventBindings(JSONArray bindings) {
                ; // TODO should observe bindings here
            }

            @Override
            public void setVariants(JSONArray variants) {
                ; // TODO should observe this
            }

            @Override
            public Tweaks getTweaks() {
                return null;
            }

            @Override
            public void addOnMixpanelTweaksUpdatedListener(OnMixpanelTweaksUpdatedListener listener) {

            }

            @Override
            public void removeOnMixpanelTweaksUpdatedListener(OnMixpanelTweaksUpdatedListener listener) {

            }
        };

        mDecideMessages = new DecideMessages(getContext(), "TEST TOKEN", mMockListener, mMockUpdates, new HashSet<Integer>());
        mSomeNotifications = new ArrayList<>();

        JSONObject notifsDesc1 = new JSONObject(
                "{\"id\": 1234, \"message_id\": 4321, \"type\": \"takeover\", \"body\": \"Hook me up, yo!\", \"body_color\": 4294901760, \"title\": null, \"title_color\": 4278255360, \"image_url\": \"http://mixpanel.com/Balok.jpg\", \"bg_color\": 3909091328, \"close_color\": 4294967295, \"extras\": {\"image_fade\": true},\"buttons\": [{\"text\": \"Button!\", \"text_color\": 4278190335, \"bg_color\": 4294967040, \"border_color\": 4278255615, \"cta_url\": \"hellomixpanel://deeplink/howareyou\"}, {\"text\": \"Button 2!\", \"text_color\": 4278190335, \"bg_color\": 4294967040, \"border_color\": 4278255615, \"cta_url\": \"hellomixpanel://deeplink/howareyou\"}]}"
        );
        JSONObject notifsDesc2 = new JSONObject(
                "{\"body\":\"A\",\"image_tint_color\":4294967295,\"border_color\":4294967295,\"message_id\":85151,\"bg_color\":3858759680,\"extras\":{},\"image_url\":\"https://cdn.mxpnl.com/site_media/images/engage/inapp_messages/mini/icon_megaphone.png\",\"cta_url\":null,\"type\":\"mini\",\"id\":1191793,\"body_color\":4294967295}"
        );

        mSomeNotifications.add(new TakeoverInAppNotification(notifsDesc1));
        mSomeNotifications.add(new MiniInAppNotification(notifsDesc2));

        mSomeBindings = new JSONArray(); // TODO need some bindings
        mSomeVariants = new JSONArray(); // TODO need some variants
    }

    public void testDuplicateIds() throws JSONException, BadDecideObjectException {
        mDecideMessages.reportResults(mSomeNotifications, mSomeBindings, mSomeVariants, mIsAutomaticEventsEnabled, null);

        final List<InAppNotification> fakeNotifications = new ArrayList<InAppNotification>(mSomeNotifications.size());
        for (final InAppNotification real: mSomeNotifications) {
            if (real.getClass().isInstance(TakeoverInAppNotification.class)) {
                fakeNotifications.add(new TakeoverInAppNotification(new JSONObject(real.toString())));
            } else if (real.getClass().isInstance(MiniInAppNotification.class)) {
                fakeNotifications.add(new MiniInAppNotification(new JSONObject(real.toString())));
            }
            assertEquals(mDecideMessages.getNotification(false), real);
        }

        assertNull(mDecideMessages.getNotification(false));

        mDecideMessages.reportResults(fakeNotifications, mSomeBindings, mSomeVariants, mIsAutomaticEventsEnabled, null);

        assertNull(mDecideMessages.getNotification(false));

        JSONObject notificationNewIdDesc = new JSONObject(
                "{\"body\":\"A\",\"image_tint_color\":4294967295,\"border_color\":4294967295,\"message_id\":85151,\"bg_color\":3858759680,\"extras\":{},\"image_url\":\"https://cdn.mxpnl.com/site_media/images/engage/inapp_messages/mini/icon_megaphone.png\",\"cta_url\":null,\"type\":\"mini\",\"id\":1,\"body_color\":4294967295}"
        );
        final InAppNotification unseenNotification = new MiniInAppNotification(notificationNewIdDesc);
        fakeNotifications.add(unseenNotification);

        mDecideMessages.reportResults(fakeNotifications, mSomeBindings, mSomeVariants, mIsAutomaticEventsEnabled, null);

        assertEquals(mDecideMessages.getNotification(false), unseenNotification);

        assertNull(mDecideMessages.getNotification(false));
    }

    public void testPops() {
        final InAppNotification nullBeforeNotification = mDecideMessages.getNotification(false);
        assertNull(nullBeforeNotification);

        mDecideMessages.reportResults(mSomeNotifications, mSomeBindings, mSomeVariants, mIsAutomaticEventsEnabled, null);

        final InAppNotification n1 = mDecideMessages.getNotification(false);
        assertEquals(mSomeNotifications.get(0), n1);

        final InAppNotification n2 = mDecideMessages.getNotification(false);
        assertEquals(mSomeNotifications.get(1), n2);

        final InAppNotification shouldBeNullNotification = mDecideMessages.getNotification(false);
        assertNull(shouldBeNullNotification);
    }

    public void testListenerCalls() throws JSONException, BadDecideObjectException {
        assertNull(mListenerCalls.peek());
        mDecideMessages.reportResults(mSomeNotifications, mSomeBindings, mSomeVariants, mIsAutomaticEventsEnabled, null);
        assertEquals(mListenerCalls.poll(), "CALLED");
        assertNull(mListenerCalls.peek());

        // No new info means no new calls
        mDecideMessages.reportResults(mSomeNotifications, mSomeBindings, mSomeVariants, mIsAutomaticEventsEnabled, null);
        assertNull(mListenerCalls.peek());

        // New info means new calls
        JSONObject notificationNewIdDesc = new JSONObject(
                "{\"body\":\"A\",\"image_tint_color\":4294967295,\"border_color\":4294967295,\"message_id\":85151,\"bg_color\":3858759680,\"extras\":{},\"image_url\":\"https://cdn.mxpnl.com/site_media/images/engage/inapp_messages/mini/icon_megaphone.png\",\"cta_url\":null,\"type\":\"mini\",\"id\":1,\"body_color\":4294967295}"
        );
        final InAppNotification unseenNotification = new MiniInAppNotification(notificationNewIdDesc);
        final List<InAppNotification> newNotifications = new ArrayList<InAppNotification>();
        newNotifications.add(unseenNotification);

        mDecideMessages.reportResults(newNotifications, mSomeBindings, mSomeVariants, mIsAutomaticEventsEnabled, null);
        assertEquals(mListenerCalls.poll(), "CALLED");
        assertNull(mListenerCalls.peek());
    }

    private BlockingQueue<String> mListenerCalls;
    private DecideMessages.OnNewResultsListener mMockListener;
    private UpdatesFromMixpanel mMockUpdates;
    private DecideMessages mDecideMessages;
    private JSONArray mSomeBindings;
    private JSONArray mSomeVariants;
    private List<InAppNotification> mSomeNotifications;
    private boolean mIsAutomaticEventsEnabled;
}
