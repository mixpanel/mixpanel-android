package com.mixpanel.android.mpmetrics;

import android.graphics.Color;
import android.os.Bundle;
import android.test.AndroidTestCase;

import com.mixpanel.android.util.RemoteService;
import com.mixpanel.android.util.HttpService;
import com.mixpanel.android.viewcrawler.UpdatesFromMixpanel;

import org.json.JSONArray;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

public class DecideCheckerTest extends AndroidTestCase {

    @Override
    public void setUp() {
        mConfig = new MPConfig(new Bundle(), null);
        mDecideChecker = new DecideChecker(getContext(), mConfig);
        mPoster = new MockPoster();
        mEventBinder = new MockUpdatesFromMixpanel();
        mEventBinder.startUpdates();
        mDecideMessages1 = new DecideMessages(getContext(), "TOKEN 1", null, mEventBinder, new HashSet<Integer>());
        mDecideMessages1.setDistinctId("DISTINCT ID 1");
        mDecideMessages2 = new DecideMessages(getContext(), "TOKEN 2", null, mEventBinder, new HashSet<Integer>());
        mDecideMessages2.setDistinctId("DISTINCT ID 2");
        mDecideMessages3 = new DecideMessages(getContext(), "TOKEN 3", null, mEventBinder, new HashSet<Integer>());
        mDecideMessages3.setDistinctId("DISTINCT ID 3");
    }

    public void testReadEmptyLists() throws RemoteService.ServiceUnavailableException {
        mDecideChecker.addDecideCheck(mDecideMessages1);

        mPoster.response = bytes("{}");
        mDecideChecker.runDecideCheck(mDecideMessages1.getToken(), mPoster);
        assertNull(mDecideMessages1.getNotification(false));
        assertUpdatesSeen(new JSONArray[] {
                new JSONArray()
        });
        mEventBinder.bindingsSeen.clear();

        mPoster.response = bytes("{\"notifications\":[]}");
        mDecideChecker.runDecideCheck(mDecideMessages1.getToken(), mPoster);
        assertNull(mDecideMessages1.getNotification(false));
        assertUpdatesSeen(new JSONArray[] {
                new JSONArray()
        });
    }

    public void testBadDecideResponses() throws RemoteService.ServiceUnavailableException {
        mDecideChecker.addDecideCheck(mDecideMessages1);

        // Corrupted or crazy responses.
        mPoster.response = bytes("{ WONT PARSE");
        mDecideChecker.runDecideCheck(mDecideMessages1.getToken(), mPoster);
        assertNull(mDecideMessages1.getNotification(false));
        assertUpdatesSeen(new JSONArray[] {}); // No updates at all on parsing failure
        mEventBinder.bindingsSeen.clear();

        // Just pure (but legal) JSON craziness
        mPoster.response = bytes("null");
        mDecideChecker.runDecideCheck(mDecideMessages1.getToken(), mPoster);
        assertNull(mDecideMessages1.getNotification(false));
        assertUpdatesSeen(new JSONArray[]{});
        mEventBinder.bindingsSeen.clear();

        // Valid JSON that isn't relevant
        mPoster.response = bytes("{\"Ziggy Startdust and the Spiders from Mars\":\"The Best Ever Number One\"}");
        mDecideChecker.runDecideCheck(mDecideMessages1.getToken(), mPoster);
        assertNull(mDecideMessages1.getNotification(false));
        assertUpdatesSeen(new JSONArray[]{
                new JSONArray()
        });
        mEventBinder.bindingsSeen.clear();
    }

    public void testDecideResponses() throws DecideChecker.UnintelligibleMessageException {
        {
            final String nonsense = "I AM NONSENSE";
            try {
                final DecideChecker.Result parseNonsense = DecideChecker.parseDecideResponse(nonsense);
                fail("Should have thrown exception on parse");
            } catch (DecideChecker.UnintelligibleMessageException e) {
                ; // OK
            }
        }

        {
            final String allNull = "null";
            try {
                final DecideChecker.Result parseAllNull = DecideChecker.parseDecideResponse(allNull);
                fail("Should have thrown exception on decide response that isn't surrounded by {}");
            } catch (DecideChecker.UnintelligibleMessageException e) {
                ; // OK
            }
        }

        {
            final String elementsNull = "{\"notifications\": null}";
            final DecideChecker.Result parseElementsNull = DecideChecker.parseDecideResponse(elementsNull);
            assertTrue(parseElementsNull.notifications.isEmpty());
        }

        {
            final String elementsEmpty = "{\"notifications\": []}";
            final DecideChecker.Result parseElementsEmpty = DecideChecker.parseDecideResponse(elementsEmpty);
            assertTrue(parseElementsEmpty.notifications.isEmpty());
        }

        {
            final String notificationOnly = "{\"notifications\":[{\"id\": 1234, \"message_id\": 4321, \"type\": \"takeover\", \"body\": \"Hook me up, yo!\", \"body_color\": 4294901760, \"title\": null, \"title_color\": 4278255360, \"image_url\": \"http://mixpanel.com/Balok.jpg\", \"bg_color\": 3909091328, \"close_color\": 4294967295, \"extras\": {\"image_fade\": true},\"buttons\": [{\"text\": \"Button!\", \"text_color\": 4278190335, \"bg_color\": 4294967040, \"border_color\": 4278255615, \"cta_url\": \"hellomixpanel://deeplink/howareyou\"}, {\"text\": \"Button 2!\", \"text_color\": 4278190335, \"bg_color\": 4294967040, \"border_color\": 4278255615, \"cta_url\": \"hellomixpanel://deeplink/howareyou\"}]}]}";

            final DecideChecker.Result parseNotificationOnly = DecideChecker.parseDecideResponse(notificationOnly);
            assertEquals(parseNotificationOnly.notifications.size(), 1);

            final TakeoverInAppNotification parsed = (TakeoverInAppNotification) parseNotificationOnly.notifications.get(0);
            assertEquals(parsed.getId(), 1234);
            assertEquals(parsed.getMessageId(), 4321);

            assertEquals(parsed.getBody(), "Hook me up, yo!");
            assertNull(parsed.getTitle());
            assertEquals(parsed.hasTitle(), false);
            assertEquals(parsed.hasBody(), true);
            assertEquals(parsed.getBackgroundColor(), Color.argb(233, 0, 0, 0));
            assertEquals(parsed.getBodyColor(), Color.parseColor("#FFFF0000"));
            assertEquals(parsed.getTitleColor(), Color.parseColor("#FF00FF00"));
            assertEquals(parsed.getImageUrl(), "http://mixpanel.com/Balok.jpg");
            assertEquals(parsed.getCloseColor(), Color.WHITE);
            assertEquals(parsed.setShouldShowShadow(), true);
            assertEquals(parsed.getButton(0).getText(), "Button!");
            assertEquals(parsed.getButton(0).getTextColor(), Color.BLUE);
            assertEquals(parsed.getButton(0).getCtaUrl(), "hellomixpanel://deeplink/howareyou");
            assertEquals(parsed.getButton(0).getBorderColor(), Color.parseColor("#FF00FFFF"));
            assertEquals(parsed.getButton(0).getBackgroundColor(), Color.parseColor("#FFFFFF00"));
            assertEquals(parsed.getButton(1).getText(), "Button 2!");
            assertEquals(parsed.getType(), InAppNotification.Type.TAKEOVER);

        }

        {
            final String both = "{\"notifications\":[{\"body\":\"A\",\"image_tint_color\":4294967295,\"border_color\":4294967295,\"message_id\":85151,\"bg_color\":3858759680,\"extras\":{},\"image_url\":\"https://cdn.mxpnl.com/site_media/images/engage/inapp_messages/mini/icon_megaphone.png\",\"cta_url\":null,\"type\":\"mini\",\"id\":1191793,\"body_color\":4294967295}]}";
            final DecideChecker.Result parseBoth = DecideChecker.parseDecideResponse(both);

            final MiniInAppNotification parsedNotification = (MiniInAppNotification) parseBoth.notifications.get(0);
            assertEquals(parsedNotification.getBody(), "A");
            assertEquals(parsedNotification.getBodyColor(), Color.WHITE);
            assertEquals(parsedNotification.getImageTintColor(), Color.WHITE);
            assertEquals(parsedNotification.getBorderColor(), Color.WHITE);
            assertEquals(parsedNotification.getBackgroundColor(), Color.parseColor("#E6000000"));
            assertEquals(parsedNotification.getExtras().length(), 0);
            assertEquals(parsedNotification.getMessageId(), 85151);
            assertEquals(parsedNotification.getImageUrl(), "https://cdn.mxpnl.com/site_media/images/engage/inapp_messages/mini/icon_megaphone.png");
            assertEquals(parsedNotification.getCtaUrl(), null);
            assertEquals(parsedNotification.getId(), 1191793);
            assertEquals(parsedNotification.getType(), InAppNotification.Type.MINI);

        }
    }

    public void testAutomaticResponse() throws DecideChecker.UnintelligibleMessageException, RemoteService.ServiceUnavailableException {
        final String automaticEventsTrue = "{\"notifications\": null, \"automatic_events\": true}";
        DecideChecker.Result parseElements;
        parseElements = DecideChecker.parseDecideResponse(automaticEventsTrue);
        assertTrue(parseElements.automaticEvents);

        final String automaticEventsFalse = "{\"notifications\": null, \"automatic_events\": false}";
        parseElements = DecideChecker.parseDecideResponse(automaticEventsFalse);
        assertFalse(parseElements.automaticEvents);

        mDecideChecker.addDecideCheck(mDecideMessages1);

        assertNull(mDecideMessages1.isAutomaticEventsEnabled());
        assertTrue(mDecideMessages1.shouldTrackAutomaticEvent());

        mPoster.response = bytes("{\"notifications\": null, \"automatic_events\": true}");
        mDecideChecker.runDecideCheck(mDecideMessages1.getToken(), mPoster);
        assertTrue(mDecideMessages1.isAutomaticEventsEnabled());
        assertTrue(mDecideMessages1.shouldTrackAutomaticEvent());

        mPoster.response = bytes("{\"notifications\": null, \"automatic_events\": false}");
        mDecideChecker.runDecideCheck(mDecideMessages1.getToken(), mPoster);
        assertFalse(mDecideMessages1.isAutomaticEventsEnabled());
        assertFalse(mDecideMessages1.shouldTrackAutomaticEvent());
    }

    private void assertUpdatesSeen(JSONArray[] expected) {
        assertEquals(expected.length, mEventBinder.bindingsSeen.size());
        for (int bindingCallIx = 0; bindingCallIx < expected.length; bindingCallIx++) {
            final JSONArray expectedArray = expected[bindingCallIx];
            final JSONArray seen = mEventBinder.bindingsSeen.get(bindingCallIx);
            assertEquals(expectedArray.toString(), seen.toString());
        }
    }

    private byte[] bytes(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("This is not an android device, or a compatible java. WHO ARE YOU?");
        }
    }

    private static class MockPoster extends HttpService {
        @Override
        public byte[] performRequest(String url, Map<String, Object> params, SSLSocketFactory socketFactory) throws IOException {
            assertNull(params);
            requestedUrls.add(url);

            if (null != exception) {
                throw exception;
            }
            return response;
        }

        public List<String> requestedUrls = new ArrayList<String>();
        public byte[] response = null;
        public IOException exception = null;
    }

    private static class MockUpdatesFromMixpanel implements UpdatesFromMixpanel {

        @Override
        public void startUpdates() {
            mStarted = true;
        }

        @Override
        public void applyPersistedUpdates() {
        }

        @Override
        public void storeVariants(JSONArray variants) {
        }

        @Override
        public void setEventBindings(JSONArray bindings) {
            assertTrue(mStarted);
            bindingsSeen.add(bindings);
        }

        @Override
        public void setVariants(JSONArray variants) {
            assertTrue(mStarted);
            variantsSeen.add(variants);
        }

        @Override
        public Tweaks getTweaks() {
            assertTrue(mStarted);
            return null;
        }

        @Override
        public void addOnMixpanelTweaksUpdatedListener(OnMixpanelTweaksUpdatedListener listener) {

        }

        @Override
        public void removeOnMixpanelTweaksUpdatedListener(OnMixpanelTweaksUpdatedListener listener) {

        }

        public List<JSONArray> bindingsSeen = new ArrayList<JSONArray>();
        public List<JSONArray> variantsSeen = new ArrayList<JSONArray>();

        private volatile boolean mStarted = false;
    }

    private DecideChecker mDecideChecker;
    private MockPoster mPoster;
    private MPConfig mConfig;
    private MockUpdatesFromMixpanel mEventBinder;
    private DecideMessages mDecideMessages1, mDecideMessages2, mDecideMessages3;
}
