package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.test.AndroidTestCase;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DecideUpdatesTest extends AndroidTestCase {
    public void setUp() throws BadDecideObjectException, JSONException {
        mMockTimeMillis = 0;

        final Bitmap oneRedPx = Bitmap.createBitmap(new int[] { Color.RED }, 1, 1, Bitmap.Config.RGB_565);
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        oneRedPx.compress(Bitmap.CompressFormat.PNG, 100, stream);
        final byte[] bitmapBytes = stream.toByteArray();
        mSuccessImageResult = new ServerMessage.Result(ServerMessage.Status.SUCCEEDED, bitmapBytes);
        mFailureImageResult = new ServerMessage.Result(ServerMessage.Status.FAILED_UNRECOVERABLE, null);
        mImageResult = mSuccessImageResult;

        mMockPoster = new ServerMessage() {
            @Override
            public Result get(Context context, String imageUrl, String shouldBeNull) {
                assertNull(shouldBeNull); // No fallback for images
                return mImageResult;
            }
        };

        mDecideUpdates = new DecideUpdates(getContext(), "TEST TOKEN") {
            @Override
            long currentTimeMillis() {
                return mMockTimeMillis;
            }

            @Override
            protected void runOnIsolatedThread(Runnable task) {
                task.run();
            }

            @Override
            protected ServerMessage newPoster() {
                return mMockPoster;
            }
        };

        mMockMessages = new MockMessages(getContext());

        JSONObject surveyDesc1 = new JSONObject(
        "{\"collections\":[{\"id\":1,\"selector\":\"true\"}],\"id\":1,\"questions\":[{\"prompt\":\"a\",\"extra_data\":{\"$choices\":[\"1\",\"2\"]},\"type\":\"multiple_choice\",\"id\":1}]}"
        );

        JSONObject surveyDesc2 = new JSONObject(
        "{\"collections\":[{\"id\":2,\"selector\":\"true\"}],\"id\":2,\"questions\":[{\"prompt\":\"a\",\"extra_data\":{\"$choices\":[\"1\",\"2\"]},\"type\":\"multiple_choice\",\"id\":2}]}"
        );

        mSomeSurveys = new ArrayList<Survey>();
        mSomeSurveys.add(new Survey(surveyDesc1));
        mSomeSurveys.add(new Survey(surveyDesc2));

        JSONObject notifsDesc1 = new JSONObject(
        "{\"body\":\"body1\",\"title\":\"title1\",\"message_id\":1,\"image_url\":\"http://x.com/image1\",\"cta\":\"cta1\",\"cta_url\":\"http://x.com/cta1\",\"id\":11,\"type\":\"takeover\"}"
        );
        JSONObject notifsDesc2 = new JSONObject(
        "{\"body\":\"body2\",\"title\":\"title2\",\"message_id\":2,\"image_url\":\"http://x.com/image2\",\"cta\":\"cta2\",\"cta_url\":\"http://x.com/cta2\",\"id\":22,\"type\":\"mini\"}"
        );

        mSomeNotifications = new ArrayList<InAppNotification>();
        mSomeNotifications.add(new InAppNotification(notifsDesc1));
        mSomeNotifications.add(new InAppNotification(notifsDesc2));

        mSurveyCallbacks = new MockSurveyCallbacks();
        mNotificationCallbacks = new MockInAppNotificationCallbacks();
    }

    public void testSurveyCallbackWithCache() {
        List<Survey> cache = mDecideUpdates.peekAtSurveyCache();
        cache.add(mSomeSurveys.get(0));

        // Should drop the cache and run a new request, this is an unknown distinct id
        mDecideUpdates.setSurveyCallback(mSurveyCallbacks, "DISTINCT ID", mMockMessages);
        assertTrue(cache.isEmpty());
        assertEquals(mMockMessages.checks.size(), 1);
        assertTrue(mSurveyCallbacks.seen.isEmpty());

        mMockMessages.checks.clear();
        cache.add(mSomeSurveys.get(0));

        // Should pull from cache, known distinct id
        mDecideUpdates.setSurveyCallback(mSurveyCallbacks, "DISTINCT ID", mMockMessages);
        assertTrue(cache.isEmpty());
        assertTrue(mMockMessages.checks.isEmpty());
        assertEquals(mSurveyCallbacks.seen.size(), 1);
        assertEquals(mSurveyCallbacks.seen.get(0), mSomeSurveys.get(0));
    }

    public void testInAppCallbackWithCache() {
        List<InAppNotification> cache = mDecideUpdates.peekAtNotificationCache();
        cache.add(mSomeNotifications.get(0));

        // Should drop the cache and run a new request, this is an unknown distinct id
        mDecideUpdates.setInAppCallback(mNotificationCallbacks, "DISTINCT ID", mMockMessages);
        assertTrue(cache.isEmpty());
        assertEquals(mMockMessages.checks.size(), 1);
        assertTrue(mNotificationCallbacks.seen.isEmpty());

        mMockMessages.checks.clear();
        cache.add(mSomeNotifications.get(0));

        // Should pull from cache, known distinct id
        mDecideUpdates.setInAppCallback(mNotificationCallbacks, "DISTINCT ID", mMockMessages);
        assertTrue(cache.isEmpty());
        assertTrue(mMockMessages.checks.isEmpty());
        assertEquals(mNotificationCallbacks.seen.size(), 1);
        assertEquals(mNotificationCallbacks.seen.get(0), mSomeNotifications.get(0));
    }

    public void testRunningSurveyRequest() {
        mDecideUpdates.setSurveyCallback(mSurveyCallbacks, "DISTINCT ID", mMockMessages);
        assertEquals(mMockMessages.checks.size(), 1);
        assertTrue(mSurveyCallbacks.seen.isEmpty()); // Should be waiting for request

        mMockTimeMillis += 1; // A little time passes
        mDecideUpdates.setInAppCallback(mNotificationCallbacks, "DISTINCT ID", mMockMessages);
        assertEquals(mMockMessages.checks.size(), 1);
        assertTrue(mNotificationCallbacks.seen.isEmpty()); // Now I'm waiting for the request, too

        mNotificationCallbacks.seen.clear();
        mMockMessages.checks.clear();
        mMockTimeMillis += MPConfig.DECIDE_REQUEST_TIMEOUT_MILLIS; // An aeon passes
        mDecideUpdates.setInAppCallback(mNotificationCallbacks, "DISTINCT ID", mMockMessages);
        assertEquals(mMockMessages.checks.size(), 1); // New request
        assertEquals(mNotificationCallbacks.seen.size(), 1); // Old callback was called with null.
        assertNull(mNotificationCallbacks.seen.get(0));
    }

    public void testRunningNotificationsRequest() {
        mDecideUpdates.setInAppCallback(mNotificationCallbacks, "DISTINCT ID", mMockMessages);
        assertEquals(mMockMessages.checks.size(), 1);
        assertTrue(mNotificationCallbacks.seen.isEmpty()); // Should be waiting for request

        mMockTimeMillis += 1; // A little time passes
        mDecideUpdates.setSurveyCallback(mSurveyCallbacks, "DISTINCT ID", mMockMessages);
        assertEquals(mMockMessages.checks.size(), 1);
        assertTrue(mSurveyCallbacks.seen.isEmpty()); // Now I'm waiting for the request, too

        mSurveyCallbacks.seen.clear();
        mMockMessages.checks.clear();
        mMockTimeMillis += MPConfig.DECIDE_REQUEST_TIMEOUT_MILLIS; // An aeon passes
        mDecideUpdates.setSurveyCallback(mSurveyCallbacks, "DISTINCT ID", mMockMessages);
        assertEquals(mMockMessages.checks.size(), 1); // New request
        assertEquals(mSurveyCallbacks.seen.size(), 1); // Old callback was called with null.
        assertNull(mSurveyCallbacks.seen.get(0));
    }

    public void testWaitingSurveyCallbacks() {
        mDecideUpdates.setSurveyCallback(mSurveyCallbacks, "DISTINCT ID", mMockMessages);
        assertEquals(mMockMessages.checks.size(), 1);
        assertTrue(mSurveyCallbacks.seen.isEmpty());

        mMockMessages.checks.clear();
        mDecideUpdates.setSurveyCallback(mSurveyCallbacks, "DISTINCT ID", mMockMessages);
        assertEquals(mSurveyCallbacks.seen.size(), 1);
        assertNull(mSurveyCallbacks.seen.get(0));
    }

    public void testWaitingNotificationCallbacks() {
        mDecideUpdates.setInAppCallback(mNotificationCallbacks, "DISTINCT ID", mMockMessages);
        assertEquals(mMockMessages.checks.size(), 1);
        assertTrue(mNotificationCallbacks.seen.isEmpty());

        mMockMessages.checks.clear();
        mDecideUpdates.setInAppCallback(mNotificationCallbacks, "DISTINCT ID", mMockMessages);
        assertEquals(mNotificationCallbacks.seen.size(), 1);
        assertNull(mNotificationCallbacks.seen.get(0));
    }

    public void testRecentRequestSurveyCallbacks() {
        mDecideUpdates.setSurveyCallback(mSurveyCallbacks, "DISTINCT ID", mMockMessages);
        assertEquals(mMockMessages.checks.size(), 1);
        assertTrue(mSurveyCallbacks.seen.isEmpty());

        // Resolve outstanding request 1 milli later
        mMockTimeMillis += 1;
        mDecideUpdates.reportResults("DISTINCT ID", Collections.<Survey>emptyList(), Collections.<InAppNotification>emptyList());

        assertEquals(mSurveyCallbacks.seen.size(), 1);
        assertNull(mSurveyCallbacks.seen.get(0));

        // We had a recent request, we should not perform a server check
        mSurveyCallbacks.seen.clear();
        mMockMessages.checks.clear();
        mDecideUpdates.setSurveyCallback(mSurveyCallbacks, "DISTINCT ID", mMockMessages);
        assertTrue(mMockMessages.checks.isEmpty());
        assertEquals(mSurveyCallbacks.seen.size(), 1);
        assertNull(mSurveyCallbacks.seen.get(0));

        // After our min interval, we should perform another check
        mMockTimeMillis += MPConfig.MAX_DECIDE_REQUEST_FREQUENCY_MILLIS;
        mSurveyCallbacks.seen.clear();
        mMockMessages.checks.clear();
        mDecideUpdates.setSurveyCallback(mSurveyCallbacks, "DISTINCT ID", mMockMessages);
        assertEquals(mMockMessages.checks.size(), 1);
        assertTrue(mSurveyCallbacks.seen.isEmpty());
    }

    public void testRecentRequestNotificationCallbacks() {
        mDecideUpdates.setInAppCallback(mNotificationCallbacks, "DISTINCT ID", mMockMessages);
        assertEquals(mMockMessages.checks.size(), 1);
        assertTrue(mNotificationCallbacks.seen.isEmpty());

        // Resolve outstanding request 1 milli later
        mMockTimeMillis += 1;
        mDecideUpdates.reportResults("DISTINCT ID", Collections.<Survey>emptyList(), Collections.<InAppNotification>emptyList());

        assertEquals(mNotificationCallbacks.seen.size(), 1);
        assertNull(mNotificationCallbacks.seen.get(0));

        // We had a recent request, we should not perform a server check
        mNotificationCallbacks.seen.clear();
        mMockMessages.checks.clear();
        mDecideUpdates.setInAppCallback(mNotificationCallbacks, "DISTINCT ID", mMockMessages);
        assertTrue(mMockMessages.checks.isEmpty());
        assertEquals(mNotificationCallbacks.seen.size(), 1);
        assertNull(mNotificationCallbacks.seen.get(0));

        // After our min interval, we should perform another check
        mMockTimeMillis += MPConfig.MAX_DECIDE_REQUEST_FREQUENCY_MILLIS;
        mNotificationCallbacks.seen.clear();
        mMockMessages.checks.clear();
        mDecideUpdates.setInAppCallback(mNotificationCallbacks, "DISTINCT ID", mMockMessages);
        assertEquals(mMockMessages.checks.size(), 1);
        assertTrue(mNotificationCallbacks.seen.isEmpty());
    }

    public void testReportResultsBehavior() throws JSONException, BadDecideObjectException {
        // With unknown distinct id and no callbacks, no cache changes
        mDecideUpdates.reportResults("STRANGE ID", mSomeSurveys, mSomeNotifications);
        assertTrue(mDecideUpdates.peekAtSurveyCache().isEmpty());
        assertTrue(mDecideUpdates.peekAtNotificationCache().isEmpty());

        mDecideUpdates.setSurveyCallback(mSurveyCallbacks, "HAS ID", mMockMessages);
        mDecideUpdates.setInAppCallback(mNotificationCallbacks, "HAS ID", mMockMessages);

        // Should have one check queued up and no calls to callbacks
        assertEquals(mMockMessages.checks.size(), 1);
        assertTrue(mSurveyCallbacks.seen.isEmpty());
        assertTrue(mNotificationCallbacks.seen.isEmpty());

        // With unknown distinct id, drop results on the floor
        mDecideUpdates.reportResults("STRANGE ID", mSomeSurveys, mSomeNotifications);
        assertTrue(mDecideUpdates.peekAtSurveyCache().isEmpty());
        assertTrue(mDecideUpdates.peekAtNotificationCache().isEmpty());
        assertTrue(mSurveyCallbacks.seen.isEmpty());
        assertTrue(mNotificationCallbacks.seen.isEmpty());

        // With known distinct id and callbacks, make the callbacks, and leave
        // unused items in the caches
        mDecideUpdates.reportResults("HAS ID", mSomeSurveys, mSomeNotifications);
        assertEquals(mDecideUpdates.peekAtSurveyCache().size(), 1);
        assertEquals(mDecideUpdates.peekAtNotificationCache().size(), 1);
        assertEquals(mSurveyCallbacks.seen.size(), 1);
        assertEquals(mNotificationCallbacks.seen.size(), 1);

        // Order of responses should be preserved
        assertEquals(mSurveyCallbacks.seen.get(0).getId(), 1);
        assertEquals(mDecideUpdates.peekAtSurveyCache().get(0).getId(), 2);

        assertEquals(mNotificationCallbacks.seen.get(0).getMessageId(), 1);
        assertEquals(mDecideUpdates.peekAtNotificationCache().get(0).getMessageId(), 2);

        JSONObject newSurveyDesc = new JSONObject(
        "{\"collections\":[{\"id\":1,\"selector\":\"true\"}],\"id\":100,\"questions\":[{\"prompt\":\"a100\",\"extra_data\":{\"$choices\":[\"1\",\"2\"]},\"type\":\"multiple_choice\",\"id\":1}]}"
        );

        final List<Survey> moreSurveys = new ArrayList<Survey>();
        moreSurveys.add(mSomeSurveys.get(1));
        moreSurveys.add(new Survey(newSurveyDesc));

        JSONObject newNotifDesc = new JSONObject(
        "{\"body\":\"body100\",\"title\":\"title1\",\"message_id\":100,\"image_url\":\"http://x.com/image1\",\"cta\":\"cta1\",\"cta_url\":\"http://x.com/cta1\",\"id\":1100,\"type\":\"takeover\"}"
        );
        final List<InAppNotification> moreNotifications = new ArrayList<InAppNotification>();
        moreNotifications.add(new InAppNotification(newNotifDesc));
        moreNotifications.add(mSomeNotifications.get(0));
        mSurveyCallbacks.seen.clear();
        mNotificationCallbacks.seen.clear();

        // With known distinct id and no callbacks, add UNSEEN ONLY to cache, AFTER previously cached stuff
        mDecideUpdates.reportResults("HAS ID", moreSurveys, moreNotifications);
        assertTrue(mSurveyCallbacks.seen.isEmpty());
        assertTrue(mNotificationCallbacks.seen.isEmpty());
        assertEquals(mDecideUpdates.peekAtNotificationCache().size(), 2);
        assertEquals(mDecideUpdates.peekAtSurveyCache().size(), 2);
        assertEquals(mDecideUpdates.peekAtSurveyCache().get(0).getId(), 2);
        assertEquals(mDecideUpdates.peekAtNotificationCache().get(0).getMessageId(), 2);
        assertEquals(mDecideUpdates.peekAtSurveyCache().get(1).getId(), 100);
        assertEquals(mDecideUpdates.peekAtNotificationCache().get(1).getMessageId(), 100);

        // Future callbacks should consume the cache without checking the server,
        // even if a lot of time has passed.
        mMockTimeMillis += MPConfig.MAX_DECIDE_REQUEST_FREQUENCY_MILLIS * 2;

        mSurveyCallbacks.seen.clear();
        mMockMessages.checks.clear();
        mDecideUpdates.setSurveyCallback(mSurveyCallbacks, "HAS ID", mMockMessages);
        mDecideUpdates.setSurveyCallback(mSurveyCallbacks, "HAS ID", mMockMessages);

        assertTrue(mMockMessages.checks.isEmpty());
        assertEquals(mSurveyCallbacks.seen.size(), 2);
        assertTrue(mNotificationCallbacks.seen.isEmpty());
        assertEquals(mDecideUpdates.peekAtNotificationCache().size(), 2);
        assertTrue(mDecideUpdates.peekAtSurveyCache().isEmpty());
        assertEquals(mSurveyCallbacks.seen.get(0).getId(), 2);
        assertEquals(mSurveyCallbacks.seen.get(1).getId(), 100);
    }

    public void testImageResults() {
        mDecideUpdates.setInAppCallback(mNotificationCallbacks, "DISTINCT ID", mMockMessages);
        assertTrue(mNotificationCallbacks.seen.isEmpty());

        mImageResult = mFailureImageResult;
        mDecideUpdates.reportResults("DISTINCT ID", Collections.<Survey>emptyList(), mSomeNotifications);
        assertEquals(mNotificationCallbacks.seen.size(), 1);
        assertNull(mNotificationCallbacks.seen.get(0));

        mNotificationCallbacks.seen.clear();
        mImageResult = mSuccessImageResult;
        mDecideUpdates.setInAppCallback(mNotificationCallbacks, "DISTINCT ID", mMockMessages);
        assertEquals(mNotificationCallbacks.seen.size(), 1);
        assertEquals(mNotificationCallbacks.seen.get(0), mSomeNotifications.get(1));

        final Bitmap image = mSomeNotifications.get(1).getImage();
        assertEquals(1, image.getWidth());
        assertEquals(1, image.getHeight());
        final int pixel = image.getPixel(0, 0);
        assertEquals(Color.RED, pixel);
    }

    public static class MockMessages extends AnalyticsMessages {
        public MockMessages(Context context) {
            super(context);
        }

        @Override
        public void checkDecideService(DecideChecker.DecideCheck check) {
            checks.add(check);
        }

        public List<DecideChecker.DecideCheck> checks = new ArrayList<DecideChecker.DecideCheck>();
    }

    public static class MockSurveyCallbacks implements SurveyCallbacks {
        @Override
        public void foundSurvey(final Survey s) {
            seen.add(s);
        }
        public final List<Survey> seen = new ArrayList<Survey>();
    }

    public static class MockInAppNotificationCallbacks implements InAppNotificationCallbacks {
        @Override
        public void foundNotification(final InAppNotification n) {
            seen.add(n);
        }
        public final List<InAppNotification> seen = new ArrayList<InAppNotification>();
    }

    private ServerMessage.Result mSuccessImageResult;
    private ServerMessage.Result mFailureImageResult;
    private ServerMessage.Result mImageResult;
    private DecideUpdates mDecideUpdates;
    private MockMessages mMockMessages;
    private ServerMessage mMockPoster;
    private MockSurveyCallbacks mSurveyCallbacks;
    private MockInAppNotificationCallbacks mNotificationCallbacks;
    private List<Survey> mSomeSurveys;
    private List<InAppNotification> mSomeNotifications;
    private long mMockTimeMillis;
}
