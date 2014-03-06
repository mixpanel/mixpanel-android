package com.mixpanel.android.mpmetrics;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.test.AndroidTestCase;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

// THESE TESTS FAIL ON OLD API VERSIONS. We should fix the test runner.
public class LifecycleCallbacksTest extends AndroidTestCase {

    public void setUp() throws BadDecideObjectException, JSONException {
        mPrefsFuture = mPrefsLoader.loadPreferences(getContext(), "EMPTY REFERRER PREFERENCES", null);
        mMockMixpanel = new CallbacksMockMixpanel(getContext(), mPrefsFuture, "TEST TOKEN FOR LIFECYCLE CALLBACKS");
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

    private static class CallbacksMockMixpanel extends MockMixpanel {
        public CallbacksMockMixpanel(final Context context, final Future<SharedPreferences> prefsFuture, final String testToken) {
            super(context, prefsFuture, testToken);
        }

        public People getPeople() {
            return mMockPeople;
        }

        private People mMockPeople = new MockMixpanel.MockPeople() {
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
            public InAppNotificationDisplay showNotification(final InAppNotification notification, final Activity parent) {
                showNotificationCalls.add(notification);
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

    private final SharedPreferencesLoader mPrefsLoader = new SharedPreferencesLoader();
    private Future<SharedPreferences> mPrefsFuture;
    private CallbacksMockMixpanel mMockMixpanel;
    private MixpanelActivityLifecycleCallbacks mCallbacks;
    private Activity mValidActivity;
    private Activity mFinishingActivity, mDestroyedActivity;
    private Survey mSurvey;
    private InAppNotification mInAppNotification;
}
