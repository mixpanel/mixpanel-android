package com.mixpanel.android.mpmetrics;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.test.AndroidTestCase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

// THESE TESTS FAIL ON OLD API VERSIONS. We should fix the test runner.
public class LifecycleCallbacksTest extends AndroidTestCase {

    public void setUp() throws BadDecideObjectException, JSONException {
        mPrefsFuture = mPrefsLoader.loadPreferences(getContext(), "EMPTY REFERRER PREFERENCES", null);
        mMockMixpanel = new MockMixpanel();
        mCallbacks = new MixpanelActivityLifecycleCallbacks(mMockMixpanel);
        mValidActivity = new TaskRootActivity();
        mFinishingActivity = new TaskRootActivity() {
            @Override
            public boolean isFinishing() {
                return true;
            }
        };
        mDestroyedActivity = new TaskRootActivity() {
            @Override
            public boolean isDestroyed() {
                return true;
            }
        };
        mInAppNotification = new InAppNotification(
            new JSONObject(
                "{\"body\":\"Hook me up, yo!\",\"title\":\"Tranya?\",\"message_id\":1781,\"image_url\":\"http://mixpanel.com/Balok.jpg\",\"cta\":\"I'm Down!\",\"cta_url\":\"http://www.mixpanel.com\",\"id\":119911,\"type\":\"takeover\"}"
            )
        );
        mSurvey = new Survey(
            new JSONObject(
                "{\"collections\":[{\"id\":3319,\"name\":\"All users 2\"},{\"id\":3329,\"name\":\"all 2\"}],\"id\":397,\"questions\":[{\"prompt\":\"prompt text\",\"extra_data\":{},\"type\":\"text\",\"id\":457}],\"name\":\"Demo survey\"}"
            )
        );
    }

    public void testBothAvailable() {
        // Should only check on notifications at first.
        mCallbacks.onActivityStarted(mValidActivity);
        assertEquals(mMockMixpanel.notificationCallbacks.size(), 1);
        assertTrue(mMockMixpanel.surveyCallbacks.isEmpty());
        assertTrue(mMockMixpanel.showNotificationCalls.isEmpty());
        assertTrue(mMockMixpanel.showSurveyCalls.isEmpty());

        // Should show (and not check) if a notification shows up
        final InAppNotificationCallbacks callback = mMockMixpanel.notificationCallbacks.get(0);
        mMockMixpanel.notificationCallbacks.clear();
        callback.foundNotification(mInAppNotification);

        assertTrue(mMockMixpanel.notificationCallbacks.isEmpty());
        assertTrue(mMockMixpanel.surveyCallbacks.isEmpty());
        assertEquals(mMockMixpanel.showNotificationCalls.size(), 1);
        assertTrue(mMockMixpanel.showSurveyCalls.isEmpty());
    }

    public void testSurveyAvailable() {
        // Should only check on notifications at first.
        mCallbacks.onActivityStarted(mValidActivity);
        assertEquals(mMockMixpanel.notificationCallbacks.size(), 1);
        assertTrue(mMockMixpanel.surveyCallbacks.isEmpty());
        assertTrue(mMockMixpanel.showNotificationCalls.isEmpty());
        assertTrue(mMockMixpanel.showSurveyCalls.isEmpty());

        // Should check Surveys if no notification is available
        final InAppNotificationCallbacks notificationCallback = mMockMixpanel.notificationCallbacks.get(0);
        mMockMixpanel.notificationCallbacks.clear();
        notificationCallback.foundNotification(null);

        assertTrue(mMockMixpanel.notificationCallbacks.isEmpty());
        assertEquals(mMockMixpanel.surveyCallbacks.size(), 1);
        assertTrue(mMockMixpanel.showNotificationCalls.isEmpty());
        assertTrue(mMockMixpanel.showSurveyCalls.isEmpty());

        // Reporting a survey shouldn't spawn any other calls
        final SurveyCallbacks surveyCallback = mMockMixpanel.surveyCallbacks.get(0);
        mMockMixpanel.surveyCallbacks.clear();
        surveyCallback.foundSurvey(mSurvey);

        assertTrue(mMockMixpanel.notificationCallbacks.isEmpty());
        assertTrue(mMockMixpanel.surveyCallbacks.isEmpty());
        assertTrue(mMockMixpanel.showNotificationCalls.isEmpty());
        assertEquals(mMockMixpanel.showSurveyCalls.size(), 1);
        assertEquals(mMockMixpanel.showSurveyCalls.get(0), mSurvey);
    }

    public void testTimeThrottling() {
        fail("This test (and the associated behavior) hasn't been written yet");
    }

    private class MockMixpanel extends MixpanelAPI {
        public MockMixpanel() {
            super(getContext(), mPrefsFuture, "MOCK MIXPANEL TOKEN FOR TEST");
        }

        @Override
        public People getPeople() {
            return mMockPeople;
        }

        private final People mMockPeople = new People() {
            @Override
            public void checkForNotification(final InAppNotificationCallbacks callbacks) {
                notificationCallbacks.add(callbacks);
            }

            @Override
            public void checkForSurvey(final SurveyCallbacks callbacks, final Activity parent) {
                surveyCallbacks.add(callbacks);
            }

            @Override
            public void showSurvey(final Survey s, final Activity parent) {
                showSurveyCalls.add(s);
            }

            @Override
            public void showNotification(final InAppNotification notification, final Activity parent) {
                showNotificationCalls.add(notification);
            }

            @Override
            public void identify(final String distinctId) {
                fail("Unexpected call");
            }

            @Override
            public void set(final String propertyName, final Object value) {
                fail("Unexpected call");
            }

            @Override
            public void set(final JSONObject properties) {
                fail("Unexpected call");
            }

            @Override
            public void setOnce(final String propertyName, final Object value) {
                fail("Unexpected call");
            }

            @Override
            public void setOnce(final JSONObject properties) {
                fail("Unexpected call");
            }

            @Override
            public void increment(final String name, final double increment) {
                fail("Unexpected call");
            }

            @Override
            public void increment(final Map<String, ? extends Number> properties) {
                fail("Unexpected call");
            }

            @Override
            public void append(final String name, final Object value) {
                fail("Unexpected call");
            }

            @Override
            public void union(final String name, final JSONArray value) {
                fail("Unexpected call");
            }

            @Override
            public void unset(final String name) {
                fail("Unexpected call");
            }

            @Override
            public void trackCharge(final double amount, final JSONObject properties) {
                fail("Unexpected call");
            }

            @Override
            public void clearCharges() {
                fail("Unexpected call");
            }

            @Override
            public void deleteUser() {
                fail("Unexpected call");
            }

            @Override
            public void initPushHandling(final String senderID) {
                fail("Unexpected call");
            }

            @Override
            public void setPushRegistrationId(final String registrationId) {
                fail("Unexpected call");
            }

            @Override
            public void clearPushRegistrationId() {
                fail("Unexpected call");
            }

            @Override
            public String getDistinctId() {
                fail("Unexpected call");
                return null;
            }

            @Override
            public void checkForSurvey(final SurveyCallbacks callbacks) {
                fail("Unexpected call");
            }

            @Override
            public People withIdentity(final String distinctId) {
                fail("Unexpected call");
                return null;
            }
        };

        public final List<InAppNotificationCallbacks> notificationCallbacks =
                Collections.synchronizedList(new ArrayList<InAppNotificationCallbacks>());
        public final List<SurveyCallbacks> surveyCallbacks =
                Collections.synchronizedList(new ArrayList<SurveyCallbacks>());
        public final List<InAppNotification> showNotificationCalls =
                Collections.synchronizedList(new ArrayList<InAppNotification>());
        public final List<Survey> showSurveyCalls =
                Collections.synchronizedList(new ArrayList<Survey>());
    }

    private class TaskRootActivity extends Activity {
        @Override
        public boolean isTaskRoot() {
            return true;
        }

        @Override
        public Context getApplicationContext() {
            return getContext();
        }
    }

    private final SharedPreferencesLoader mPrefsLoader = new SharedPreferencesLoader();
    private Future<SharedPreferences> mPrefsFuture;
    private MockMixpanel mMockMixpanel;
    private MixpanelActivityLifecycleCallbacks mCallbacks;
    private Activity mValidActivity;
    private Activity mFinishingActivity, mDestroyedActivity;
    private Survey mSurvey;
    private InAppNotification mInAppNotification;
}
