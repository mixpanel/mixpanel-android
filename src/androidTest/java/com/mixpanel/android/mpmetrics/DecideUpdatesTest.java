package com.mixpanel.android.mpmetrics;

import android.test.AndroidTestCase;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class DecideUpdatesTest extends AndroidTestCase {

    @Override
    public void setUp() throws JSONException, BadDecideObjectException {

        mListenerCalls = new LinkedBlockingQueue<String>();
        mMockListener = new DecideUpdates.OnNewResultsListener() {
            @Override
            public void onNewResults(final String distinctId) {
                mListenerCalls.add("CALLED");
            }
        };

        mDecideUpdates = new DecideUpdates("TEST TOKEN", "TEST DISTINCT ID", mMockListener);
        mSomeSurveys = new ArrayList<Survey>();
        mSomeNotifications = new ArrayList<InAppNotification>();

        JSONObject surveyDesc1 = new JSONObject(
                "{\"collections\":[{\"id\":1,\"selector\":\"true\"}],\"id\":1,\"questions\":[{\"prompt\":\"a\",\"extra_data\":{\"$choices\":[\"1\",\"2\"]},\"type\":\"multiple_choice\",\"id\":1}]}"
        );

        JSONObject surveyDesc2 = new JSONObject(
                "{\"collections\":[{\"id\":2,\"selector\":\"true\"}],\"id\":2,\"questions\":[{\"prompt\":\"a\",\"extra_data\":{\"$choices\":[\"1\",\"2\"]},\"type\":\"multiple_choice\",\"id\":2}]}"
        );
        mSomeSurveys.add(new Survey(surveyDesc1));
        mSomeSurveys.add(new Survey(surveyDesc2));

        JSONObject notifsDesc1 = new JSONObject(
                "{\"body\":\"body1\",\"title\":\"title1\",\"message_id\":1,\"image_url\":\"http://x.com/image1\",\"cta\":\"cta1\",\"cta_url\":\"http://x.com/cta1\",\"id\":11,\"type\":\"takeover\"}"
        );
        JSONObject notifsDesc2 = new JSONObject(
                "{\"body\":\"body2\",\"title\":\"title2\",\"message_id\":2,\"image_url\":\"http://x.com/image2\",\"cta\":\"cta2\",\"cta_url\":\"http://x.com/cta2\",\"id\":22,\"type\":\"mini\"}"
        );

        mSomeNotifications.add(new InAppNotification(notifsDesc1));
        mSomeNotifications.add(new InAppNotification(notifsDesc2));
    }

    public void testDestruction() {
        assertFalse(mDecideUpdates.isDestroyed());
        mDecideUpdates.destroy();
        assertTrue(mDecideUpdates.isDestroyed());
    }

    public void testDuplicateIds() throws JSONException, BadDecideObjectException {
        mDecideUpdates.reportResults(mSomeSurveys, mSomeNotifications);

        final List<Survey> fakeSurveys = new ArrayList<Survey>(mSomeSurveys.size());
        for (final Survey real: mSomeSurveys) {
            fakeSurveys.add(new Survey(new JSONObject(real.toJSON())));
            assertEquals(mDecideUpdates.getSurvey(false), real);
        }

        final List<InAppNotification> fakeNotifications = new ArrayList<InAppNotification>(mSomeNotifications.size());
        for (final InAppNotification real: mSomeNotifications) {
            fakeNotifications.add(new InAppNotification(new JSONObject(real.toJSON())));
            assertEquals(mDecideUpdates.getNotification(false), real);
        }

        assertNull(mDecideUpdates.getSurvey(false));
        assertNull(mDecideUpdates.getNotification(false));

        mDecideUpdates.reportResults(fakeSurveys, fakeNotifications);

        assertNull(mDecideUpdates.getSurvey(false));
        assertNull(mDecideUpdates.getNotification(false));

        JSONObject surveyNewIdDesc = new JSONObject(
                "{\"collections\":[{\"id\":1,\"selector\":\"true\"}],\"id\":1001,\"questions\":[{\"prompt\":\"a\",\"extra_data\":{\"$choices\":[\"1\",\"2\"]},\"type\":\"multiple_choice\",\"id\":1}]}"
        );
        final Survey unseenSurvey = new Survey(surveyNewIdDesc);
        fakeSurveys.add(unseenSurvey);

        JSONObject notificationNewIdDesc = new JSONObject(
                "{\"body\":\"body2\",\"title\":\"title2\",\"message_id\":2,\"image_url\":\"http://x.com/image2\",\"cta\":\"cta2\",\"cta_url\":\"http://x.com/cta2\",\"id\":22022,\"type\":\"mini\"}"
        );
        final InAppNotification unseenNotification = new InAppNotification(notificationNewIdDesc);
        fakeNotifications.add(unseenNotification);

        mDecideUpdates.reportResults(fakeSurveys, fakeNotifications);

        assertEquals(mDecideUpdates.getSurvey(false), unseenSurvey);
        assertEquals(mDecideUpdates.getNotification(false), unseenNotification);

        assertNull(mDecideUpdates.getSurvey(false));
        assertNull(mDecideUpdates.getNotification(false));
    }

    public void testPops() {
        final Survey nullBeforeSurvey = mDecideUpdates.getSurvey(false);
        assertNull(nullBeforeSurvey);

        final InAppNotification nullBeforeNotification = mDecideUpdates.getNotification(false);
        assertNull(nullBeforeNotification);

        mDecideUpdates.reportResults(mSomeSurveys, mSomeNotifications);

        final Survey s1 = mDecideUpdates.getSurvey(false);
        assertEquals(mSomeSurveys.get(0), s1);

        final Survey s2 = mDecideUpdates.getSurvey(false);
        assertEquals(mSomeSurveys.get(1), s2);

        final Survey shouldBeNullSurvey = mDecideUpdates.getSurvey(false);
        assertNull(shouldBeNullSurvey);

        final InAppNotification n1 = mDecideUpdates.getNotification(false);
        assertEquals(mSomeNotifications.get(0), n1);

        final InAppNotification n2 = mDecideUpdates.getNotification(false);
        assertEquals(mSomeNotifications.get(1), n2);

        final InAppNotification shouldBeNullNotification = mDecideUpdates.getNotification(false);
        assertNull(shouldBeNullNotification);
    }

    public void testListenerCalls() throws JSONException, BadDecideObjectException {
        assertNull(mListenerCalls.peek());
        mDecideUpdates.reportResults(mSomeSurveys, mSomeNotifications);
        assertEquals(mListenerCalls.poll(), "CALLED");
        assertNull(mListenerCalls.peek());

        // No new info means no new calls
        mDecideUpdates.reportResults(mSomeSurveys, mSomeNotifications);
        assertNull(mListenerCalls.peek());

        // New info means new calls
        JSONObject notificationNewIdDesc = new JSONObject(
                "{\"body\":\"body2\",\"title\":\"title2\",\"message_id\":2,\"image_url\":\"http://x.com/image2\",\"cta\":\"cta2\",\"cta_url\":\"http://x.com/cta2\",\"id\":22022,\"type\":\"mini\"}"
        );
        final InAppNotification unseenNotification = new InAppNotification(notificationNewIdDesc);
        final List<InAppNotification> newNotifications = new ArrayList<InAppNotification>();
        newNotifications.add(unseenNotification);

        mDecideUpdates.reportResults(mSomeSurveys, newNotifications);
        assertEquals(mListenerCalls.poll(), "CALLED");
        assertNull(mListenerCalls.peek());
    }

    private BlockingQueue<String> mListenerCalls;
    private DecideUpdates.OnNewResultsListener mMockListener;
    private DecideUpdates mDecideUpdates;
    private List<Survey> mSomeSurveys;
    private List<InAppNotification> mSomeNotifications;
}
