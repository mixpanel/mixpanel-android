package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.test.AndroidTestCase;

import com.mixpanel.android.viewcrawler.UpdatesFromMixpanel;

import org.apache.http.NameValuePair;
import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

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
            public byte[] performRequest(String endpointUrl, List<NameValuePair> nameValuePairs) {
                return mExpectations.setExpectationsRequest(endpointUrl, nameValuePairs);
            }
        };

        mMockConfig = new MPConfig(new Bundle()) {
            @Override
            public boolean getAutoShowMixpanelUpdates() {
                return false;
            }
        };

        mMockMessages = new AnalyticsMessages(getContext()) {
            @Override
            protected ServerMessage getPoster() {
                return mMockPoster;
            }

            @Override
            protected MPConfig getConfig(Context context) { return mMockConfig; }
        };
    }

    public void testDecideChecks() {
        // Should not make any requests on construction if the user has not been identified
        mExpectations.expect("ALWAYS WRONG", "ALWAYS WRONG");

        MixpanelAPI api = new TestUtils.CleanMixpanelAPI(getContext(), mMockPreferences, "TEST TOKEN testSurveyChecks") {
            @Override
            AnalyticsMessages getAnalyticsMessages() {
                return mMockMessages;
            }

            @Override
            UpdatesFromMixpanel constructUpdatesFromMixpanel(final Context context, final String token) {
                return new MockUpdates();
            }

            @Override
            DecideMessages constructDecideUpdates(String token, DecideMessages.OnNewResultsListener listener, UpdatesFromMixpanel binder) {
                return new MockMessages(token, listener, binder);
            }
        };

        // Could be too early to see anything, but if so we'll pick it up when we
        // set the next round of expectations.
        final Survey shouldBeNull = api.getPeople().getSurveyIfAvailable();
        assertNull(shouldBeNull);

        // Should make a request on identify
        mExpectations.expect(
            "https://decide.mixpanel.com/decide?version=1&lib=android&token=TEST+TOKEN+testSurveyChecks&distinct_id=DECIDE+CHECKS+ID+1",
             "{" +
                  "\"notifications\":[{\"body\":\"Hook me up, yo!\",\"title\":\"Tranya?\",\"message_id\":1781,\"image_url\":\"http://mixpanel.com/Balok.jpg\",\"cta\":\"I'm Down!\",\"cta_url\":\"http://www.mixpanel.com\",\"id\":119911,\"type\":\"mini\"}]," +
                  "\"surveys\":[{\"collections\":[{\"id\":3319,\"name\":\"All users 2\"}],\"id\":397,\"questions\":[{\"prompt\":\"prompt text\",\"extra_data\":{},\"type\":\"text\",\"id\":457}],\"name\":\"Demo survey\"}]," +
                  "\"event_bindings\": [{\"event_name\":\"EVENT NAME\",\"path\":[{\"index\":0,\"view_class\":\"com.android.internal.policy.impl.PhoneWindow.DecorView\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarOverlayLayout\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarContainer\"}],\"target_activity\":\"ACTIVITY\",\"event_type\":\"EVENT TYPE\"}]" +
             "}"
        );
        api.getPeople().identify("DECIDE CHECKS ID 1");
        mExpectations.checkExpectations();

        // We should be done, and Updates should have our goodies waiting
        {
            final Survey shouldExistSurvey = api.getPeople().getSurveyIfAvailable();
            assertEquals(shouldExistSurvey.getId(), 397);
            final InAppNotification shouldExistNotification = api.getPeople().getNotificationIfAvailable();
            assertEquals(shouldExistNotification.getId(), 119911);
        }

        assertNull(api.getPeople().getSurveyIfAvailable());
        assertNull(api.getPeople().getNotificationIfAvailable());

        // We should run a new check on every flush (right before the flush)
        mExpectations.expect(
            "https://decide.mixpanel.com/decide?version=1&lib=android&token=TEST+TOKEN+testSurveyChecks&distinct_id=DECIDE+CHECKS+ID+1",
            "{" +
                    "\"notifications\":[{\"body\":\"b\",\"title\":\"t\",\"message_id\":1111,\"image_url\":\"http://mixpanel.com/Balok.jpg\",\"cta\":\"c1\",\"cta_url\":\"http://www.mixpanel.com\",\"id\":3333,\"type\":\"mini\"}]," +
                    "\"surveys\":[{\"collections\":[{\"id\":3319,\"name\":\"n\"}],\"id\":8888,\"questions\":[{\"prompt\":\"p\",\"extra_data\":{},\"type\":\"text\",\"id\":457}],\"name\":\"N2\"}]," +
                    "\"event_bindings\": [{\"event_name\":\"EVENT NAME\",\"path\":[{\"index\":0,\"view_class\":\"com.android.internal.policy.impl.PhoneWindow.DecorView\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarOverlayLayout\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarContainer\"}],\"target_activity\":\"ACTIVITY\",\"event_type\":\"EVENT TYPE\"}]" +
            "}"
         );
        api.flush();
        mExpectations.checkExpectations();

        {
            final Survey shouldExistSurvey = api.getPeople().getSurveyIfAvailable();
            assertEquals(shouldExistSurvey.getId(), 8888);
            final InAppNotification shouldExistNotification = api.getPeople().getNotificationIfAvailable();
            assertEquals(shouldExistNotification.getId(), 3333);
        }

        assertNull(api.getPeople().getSurveyIfAvailable());
        assertNull(api.getPeople().getNotificationIfAvailable());

        // We should check, but IGNORE repeated objects when we see them come through
        mExpectations.expect(
            "https://decide.mixpanel.com/decide?version=1&lib=android&token=TEST+TOKEN+testSurveyChecks&distinct_id=DECIDE+CHECKS+ID+1",
            "{" +
                    "\"notifications\":[{\"body\":\"b\",\"title\":\"t\",\"message_id\":1111,\"image_url\":\"http://mixpanel.com/Balok.jpg\",\"cta\":\"c1\",\"cta_url\":\"http://www.mixpanel.com\",\"id\":3333,\"type\":\"mini\"}]," +
                    "\"surveys\":[{\"collections\":[{\"id\":3319,\"name\":\"n\"}],\"id\":8888,\"questions\":[{\"prompt\":\"p\",\"extra_data\":{},\"type\":\"text\",\"id\":457}],\"name\":\"N2\"}]," +
                    "\"event_bindings\": [{\"event_name\":\"EVENT NAME\",\"path\":[{\"index\":0,\"view_class\":\"com.android.internal.policy.impl.PhoneWindow.DecorView\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarOverlayLayout\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarContainer\"}],\"target_activity\":\"ACTIVITY\",\"event_type\":\"EVENT TYPE\"}]" +
            "}"
        );
        api.flush();
        mExpectations.checkExpectations();
        assertNull(api.getPeople().getSurveyIfAvailable());
        assertNull(api.getPeople().getNotificationIfAvailable());

        // Seen never changes, even if we re-identify
        mExpectations.expect(
            "https://decide.mixpanel.com/decide?version=1&lib=android&token=TEST+TOKEN+testSurveyChecks&distinct_id=DECIDE+CHECKS+ID+2",
            "{" +
                    "\"notifications\":[{\"body\":\"b\",\"title\":\"t\",\"message_id\":1111,\"image_url\":\"http://mixpanel.com/Balok.jpg\",\"cta\":\"c1\",\"cta_url\":\"http://www.mixpanel.com\",\"id\":3333,\"type\":\"mini\"}]," +
                    "\"surveys\":[{\"collections\":[{\"id\":3319,\"name\":\"n\"}],\"id\":8888,\"questions\":[{\"prompt\":\"p\",\"extra_data\":{},\"type\":\"text\",\"id\":457}],\"name\":\"N2\"}]," +
                    "\"event_bindings\": [{\"event_name\":\"EVENT NAME\",\"path\":[{\"index\":0,\"view_class\":\"com.android.internal.policy.impl.PhoneWindow.DecorView\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarOverlayLayout\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarContainer\"}],\"target_activity\":\"ACTIVITY\",\"event_type\":\"EVENT TYPE\"}]" +
            "}"
        );
        api.getPeople().identify("DECIDE CHECKS ID 2");
        api.flush();

        mExpectations.checkExpectations();

        assertNull(api.getPeople().getSurveyIfAvailable());
        assertNull(api.getPeople().getNotificationIfAvailable());
    }

    public void testDecideChecksOnConstruction() {
        final String useToken = "TEST IDENTIFIED ON CONSTRUCTION";

        final String prefsName = "com.mixpanel.android.mpmetrics.MixpanelAPI_" + useToken;
        final SharedPreferences ret = getContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = ret.edit();
        editor.putString("people_distinct_id", "Present Before Construction");
        editor.commit();

        // We should run a check on construction if we are constructed with a people distinct id
        mExpectations.expect(
            "https://decide.mixpanel.com/decide?version=1&lib=android&token=TEST+IDENTIFIED+ON+CONSTRUCTION&distinct_id=Present+Before+Construction",
            "{" +
                    "\"notifications\":[{\"body\":\"b\",\"title\":\"t\",\"message_id\":1111,\"image_url\":\"http://mixpanel.com/Balok.jpg\",\"cta\":\"c1\",\"cta_url\":\"http://www.mixpanel.com\",\"id\":3333,\"type\":\"mini\"}]," +
                    "\"surveys\":[{\"collections\":[{\"id\":3319,\"name\":\"n\"}],\"id\":8888,\"questions\":[{\"prompt\":\"p\",\"extra_data\":{},\"type\":\"text\",\"id\":457}],\"name\":\"N2\"}]," +
                    "\"event_bindings\": [{\"event_name\":\"EVENT NAME\",\"path\":[{\"index\":0,\"view_class\":\"com.android.internal.policy.impl.PhoneWindow.DecorView\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarOverlayLayout\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarContainer\"}],\"target_activity\":\"ACTIVITY\",\"event_type\":\"EVENT TYPE\"}]" +
            "}"
        );

        MixpanelAPI api = new MixpanelAPI(getContext(), mMockPreferences, useToken) {
            @Override
            AnalyticsMessages getAnalyticsMessages() {
                return mMockMessages;
            }

            @Override
            DecideMessages constructDecideUpdates(String token, DecideMessages.OnNewResultsListener listener, UpdatesFromMixpanel binder) {
                return new MockMessages(token, listener, binder);
            }

            @Override
            boolean sendAppOpen() {
                return false;
            }
        };

        mExpectations.checkExpectations();
        final Survey foundSurvey = api.getPeople().getSurveyIfAvailable();
        final InAppNotification foundNotification = api.getPeople().getNotificationIfAvailable();

        assertEquals(foundSurvey.getId(), 8888);
        assertEquals(foundNotification.getId(), 3333);
    }

    private static class Expectations {
        public Expectations() {
            final ByteArrayOutputStream imageStream = new ByteArrayOutputStream();
            final Bitmap.Config conf = Bitmap.Config.ARGB_8888;
            final Bitmap testBitmap = Bitmap.createBitmap(100, 100, conf);
            testBitmap.compress(Bitmap.CompressFormat.JPEG, 50, imageStream);
            imageBytes = imageStream.toByteArray();
        }

        public synchronized void expect(String url, String response) {
            mExpectUrl = url;
            mResponse = response;
            badUrl = null;
            badNameValuePairs = null;
            mResultsFound = false;
            resultsBad = false;
        }

        public void checkExpectations() {
            final long startWaiting = System.currentTimeMillis();
            final long timeout = 1000;
            while (true) {
                try {
                    synchronized (this) {
                        if (mResultsFound) {
                            if (resultsBad) {
                                fail("Unexpected URL " + badUrl + " in MixpanelAPI (expected " + mExpectUrl + ")\n" +
                                        "Got params " + badNameValuePairs);
                            }

                            break;
                        }
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

        public synchronized byte[] setExpectationsRequest(final String endpointUrl, List<NameValuePair> nameValuePairs) {
            if (endpointUrl.equals(mExpectUrl)) {
                return TestUtils.bytes(mResponse);
            } else if (Pattern.matches("^http://mixpanel.com/Balok.{0,3}\\.jpg$", endpointUrl)) {
                return imageBytes;
            } else {
                badUrl = endpointUrl;
                badNameValuePairs = nameValuePairs;
                resultsBad = true;
                return "{}".getBytes();
            }
        }

        public synchronized void resolve() {
            mResultsFound = true;
            this.notify();
        }

        public synchronized String toString() {
            return "Expectations(" + mExpectUrl + ", " + mResponse + ", " + mResultsFound + ")";
        }

        private String mExpectUrl = null;
        private String mResponse = null;
        private String badUrl = null;
        private List<NameValuePair> badNameValuePairs = null;
        private boolean mResultsFound = false;
        private boolean resultsBad = false;
        private byte[] imageBytes;
    }

    private class MockMessages extends DecideMessages {
        public MockMessages(final String token, final OnNewResultsListener listener, final UpdatesFromMixpanel binder) {
            super(token, listener, binder);
        }

        @Override
        public void reportResults(List<Survey> newSurveys, List<InAppNotification> newNotifications, JSONArray newBindings, JSONArray variants) {
            super.reportResults(newSurveys, newNotifications, newBindings, variants);
            mExpectations.resolve();
        }
    }

    private class MockUpdates implements UpdatesFromMixpanel {
        @Override
        public void startUpdates() {
            ;
        }

        @Override
        public void setEventBindings(JSONArray bindings) {
            ; // TODO we need to test that (possibly empty, never null) bindings come through
        }

        @Override
        public void setVariants(JSONArray variants) {
            ;
        }

        @Override
        public Tweaks getTweaks() {
            return null;
        }
    }

    private MPConfig mMockConfig;
    private Future<SharedPreferences> mMockPreferences;
    private Expectations mExpectations;
    private ServerMessage mMockPoster;
    private AnalyticsMessages mMockMessages;
}
