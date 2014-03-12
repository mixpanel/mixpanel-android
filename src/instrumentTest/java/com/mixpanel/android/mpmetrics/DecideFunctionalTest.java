package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.AndroidTestCase;

import org.apache.http.NameValuePair;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DecideFunctionalTest extends AndroidTestCase {

    public void setUp() throws InterruptedException {
        final SharedPreferences referrerPreferences = getContext().getSharedPreferences("MIXPANEL_TEST_PREFERENCES", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = referrerPreferences.edit();
        editor.clear();
        editor.commit();

        mMockPreferences = new Future<SharedPreferences>() {
            @Override
            public boolean cancel(final boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public SharedPreferences get() throws InterruptedException, ExecutionException {
                return referrerPreferences;
            }

            @Override
            public SharedPreferences get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return referrerPreferences;
            }
        };

        mExpectations = new Expectations();
        mMockPoster = new ServerMessage() {
            @Override
            /* package */ Result performRequest(String endpointUrl, List<NameValuePair> nameValuePairs) {
                synchronized (mExpectations) {
                    if (endpointUrl.equals(mExpectations.expectUrl)) {
                        System.out.println("EXPECTATIONS A OK! " + mExpectations);
                    } else {
                        fail("Unexpected URL " + endpointUrl + " in MixpanelAPI");
                    }

                    return new Result(Status.SUCCEEDED, TestUtils.bytes(mExpectations.response));
                }
            }
        };

        mMockMessages = new AnalyticsMessages(getContext()) {
            @Override
            protected ServerMessage getPoster() {
                return mMockPoster;
            }
        };
    }

    public void testDecideChecks() {
        // Should not make any requests on construction if the user has not been identified
        synchronized (mExpectations) {
            mExpectations.expectUrl = "ALWAYS WRONG";
            mExpectations.response = "ALWAYS WRONG";
        }
        MixpanelAPI api = new TestUtils.CleanMixpanelAPI(getContext(), mMockPreferences, "TEST TOKEN testSurveyChecks") {
            @Override
            AnalyticsMessages getAnalyticsMessages() {
                return mMockMessages;
            }

            @Override
            DecideUpdates constructDecideUpdates(String token, String distinctId) {
                return new MockUpdates(token, distinctId);
            }
        };

        // Could be too early to see anything, but if so we'll pick it up when we
        // set the next round of expectations.
        final Survey shouldBeNull = api.getPeople().getNextSurvey();
        assertNull(shouldBeNull);

        // Should make a request on identify
        synchronized (mExpectations) {
            mExpectations.expectUrl = "https://decide.mixpanel.com/decide?version=1&lib=android&token=TEST+TOKEN+testSurveyChecks&distinct_id=DECIDE+CHECKS+ID+1";
            mExpectations.response = "{" +
                    "\"notifications\":[{\"body\":\"Hook me up, yo!\",\"title\":\"Tranya?\",\"message_id\":1781,\"image_url\":\"http://mixpanel.com/Balok.jpg\",\"cta\":\"I'm Down!\",\"cta_url\":\"http://www.mixpanel.com\",\"id\":119911,\"type\":\"mini\"}]," +
                    "\"surveys\":[{\"collections\":[{\"id\":3319,\"name\":\"All users 2\"}],\"id\":397,\"questions\":[{\"prompt\":\"prompt text\",\"extra_data\":{},\"type\":\"text\",\"id\":457}],\"name\":\"Demo survey\"}]" +
                    "}";
            mExpectations.resultsFound = false;
        }
        api.getPeople().identify("DECIDE CHECKS ID 1");
        mExpectations.checkExpectations();

        // We should be done, and Updates should have our goodies waiting
        {
            final Survey shouldExistSurvey = api.getPeople().getNextSurvey();
            assertEquals(shouldExistSurvey.getId(), 397);
            final InAppNotification shouldExistNotification = api.getPeople().getNextInAppNotification();
            assertEquals(shouldExistNotification.getId(), 119911);
        }

        assertNull(api.getPeople().getNextSurvey());
        assertNull(api.getPeople().getNextInAppNotification());

        // We should run a new check on every flush (right before the flush)
        synchronized (mExpectations) {
            mExpectations.expectUrl = "https://decide.mixpanel.com/decide?version=1&lib=android&token=TEST+TOKEN+testSurveyChecks&distinct_id=DECIDE+CHECKS+ID+1";
            mExpectations.response = "{" +
                    "\"notifications\":[{\"body\":\"b\",\"title\":\"t\",\"message_id\":1111,\"image_url\":\"http://mixpanel.com/Balok.jpg\",\"cta\":\"c1\",\"cta_url\":\"http://www.mixpanel.com\",\"id\":3333,\"type\":\"mini\"}]," +
                    "\"surveys\":[{\"collections\":[{\"id\":3319,\"name\":\"n\"}],\"id\":8888,\"questions\":[{\"prompt\":\"p\",\"extra_data\":{},\"type\":\"text\",\"id\":457}],\"name\":\"N2\"}]" +
                    "}";
            mExpectations.resultsFound = false;
        }
        api.flush();
        mExpectations.checkExpectations();

        {
            final Survey shouldExistSurvey = api.getPeople().getNextSurvey();
            assertEquals(shouldExistSurvey.getId(), 8888);
            final InAppNotification shouldExistNotification = api.getPeople().getNextInAppNotification();
            assertEquals(shouldExistNotification.getId(), 3333);
        }

        assertNull(api.getPeople().getNextSurvey());
        assertNull(api.getPeople().getNextInAppNotification());

        // We should check, but IGNORE repeated objects when we see them come through
        synchronized (mExpectations) {
            mExpectations.expectUrl = "https://decide.mixpanel.com/decide?version=1&lib=android&token=TEST+TOKEN+testSurveyChecks&distinct_id=DECIDE+CHECKS+ID+1";
            mExpectations.response = "{" +
                    "\"notifications\":[{\"body\":\"b\",\"title\":\"t\",\"message_id\":1111,\"image_url\":\"http://mixpanel.com/Balok.jpg\",\"cta\":\"c1\",\"cta_url\":\"http://www.mixpanel.com\",\"id\":3333,\"type\":\"mini\"}]," +
                    "\"surveys\":[{\"collections\":[{\"id\":3319,\"name\":\"n\"}],\"id\":8888,\"questions\":[{\"prompt\":\"p\",\"extra_data\":{},\"type\":\"text\",\"id\":457}],\"name\":\"N2\"}]" +
                    "}";
            mExpectations.resultsFound = false;
        }
        api.flush();
        mExpectations.checkExpectations();
        assertNull(api.getPeople().getNextSurvey());
        assertNull(api.getPeople().getNextInAppNotification());

        // We should rewrite our memory, including seen objects, when we call identify
        synchronized (mExpectations) {
            mExpectations.expectUrl = "https://decide.mixpanel.com/decide?version=1&lib=android&token=TEST+TOKEN+testSurveyChecks&distinct_id=DECIDE+CHECKS+ID+2";
            mExpectations.response = "{" +
                    "\"notifications\":[{\"body\":\"b\",\"title\":\"t\",\"message_id\":1111,\"image_url\":\"http://mixpanel.com/Balok.jpg\",\"cta\":\"c1\",\"cta_url\":\"http://www.mixpanel.com\",\"id\":3333,\"type\":\"mini\"}]," +
                    "\"surveys\":[{\"collections\":[{\"id\":3319,\"name\":\"n\"}],\"id\":8888,\"questions\":[{\"prompt\":\"p\",\"extra_data\":{},\"type\":\"text\",\"id\":457}],\"name\":\"N2\"}]" +
                    "}";
            mExpectations.resultsFound = false;
        }
        api.getPeople().identify("DECIDE CHECKS ID 2");

        mExpectations.checkExpectations();

        {
            final Survey shouldExistSurvey = api.getPeople().getNextSurvey();
            assertEquals(shouldExistSurvey.getId(), 8888);
            final InAppNotification shouldExistNotification = api.getPeople().getNextInAppNotification();
            assertEquals(shouldExistNotification.getId(), 3333);
        }

        assertNull(api.getPeople().getNextSurvey());
        assertNull(api.getPeople().getNextInAppNotification());
    }

    public void testDecideChecksOnConstruction() {
        final String useToken = "TEST IDENTIFIED ON CONSTRUCTION";

        final String prefsName = "com.mixpanel.android.mpmetrics.MixpanelAPI_" + useToken;
        final SharedPreferences ret = getContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = ret.edit();
        editor.putString("people_distinct_id", "Present Before Construction");
        editor.commit();

        // We should run a check on construction if we are constructed with a people distinct id
        synchronized (mExpectations) {
            mExpectations.expectUrl = "https://decide.mixpanel.com/decide?version=1&lib=android&token=TEST+IDENTIFIED+ON+CONSTRUCTION&distinct_id=Present+Before+Construction";
            mExpectations.response = "{" +
                    "\"notifications\":[{\"body\":\"b\",\"title\":\"t\",\"message_id\":1111,\"image_url\":\"http://mixpanel.com/Balok.jpg\",\"cta\":\"c1\",\"cta_url\":\"http://www.mixpanel.com\",\"id\":3333,\"type\":\"mini\"}]," +
                    "\"surveys\":[{\"collections\":[{\"id\":3319,\"name\":\"n\"}],\"id\":8888,\"questions\":[{\"prompt\":\"p\",\"extra_data\":{},\"type\":\"text\",\"id\":457}],\"name\":\"N2\"}]" +
                    "}";
            mExpectations.resultsFound = false;
        }

        MixpanelAPI api = new MixpanelAPI(getContext(), mMockPreferences, useToken) {
            @Override
            AnalyticsMessages getAnalyticsMessages() {
                return mMockMessages;
            }

            @Override
            DecideUpdates constructDecideUpdates(String token, String distinctId) {
                return new MockUpdates(token, distinctId);
            }
        };

        mExpectations.checkExpectations();
        final Survey foundSurvey = api.getPeople().getNextSurvey();
        final InAppNotification foundNotification = api.getPeople().getNextInAppNotification();

        assertEquals(foundSurvey.getId(), 8888);
        assertEquals(foundNotification.getId(), 3333);
    }

    private static class Expectations {
        public String expectUrl = null;
        public String response = null;
        public boolean resultsFound = false;

        public void checkExpectations() {
            final long startWaiting = System.currentTimeMillis();
            final long timeout = 1000;
            while (true) {
                try {
                    synchronized (this) {
                        if (this.resultsFound) break;
                        this.wait(timeout);
                    }
                } catch (InterruptedException e) {
                    ; // Next iteration
                }

                if (startWaiting + (2 * timeout) < System.currentTimeMillis()) {
                    fail("Test timed out waiting on expectation " + this);
                    break;
                }
            }
        }

        public synchronized String toString() {
            return "Expectations(" + expectUrl + ", " + response + ", " + resultsFound + ")";
        }
    }

    private class MockUpdates extends DecideUpdates {
        public MockUpdates(final String token, final String distinctId) {
            super(token, distinctId);
        }

        @Override
        public void reportResults(List<Survey> newSurveys, List<InAppNotification> newNotifications) {
            super.reportResults(newSurveys, newNotifications);
            synchronized (mExpectations) {
                mExpectations.resultsFound = true;
                mExpectations.notify();
            }
        }
    }

    private Future<SharedPreferences> mMockPreferences;
    private Expectations mExpectations;
    private ServerMessage mMockPoster;
    private AnalyticsMessages mMockMessages;
}
