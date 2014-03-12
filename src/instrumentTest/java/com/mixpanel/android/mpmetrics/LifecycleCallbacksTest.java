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
        // Should show a notification only if both are available
        mMockMixpanel.availableNotification.set(mInAppNotification);
        mMockMixpanel.availableSurvey.set(mSurvey);

        mCallbacks.onActivityStarted(mValidActivity);

        assertNull(mMockMixpanel.availableNotification.get());
        assertNotNull(mMockMixpanel.availableSurvey.get());
        assertEquals(mMockMixpanel.showNotificationCalls.size(), 1);
        assertEquals(mMockMixpanel.showNotificationCalls.get(0), mInAppNotification);
        assertTrue(mMockMixpanel.showSurveyCalls.isEmpty());
    }

    public void testSurveyAvailable() {
        // Should only show surveys if no notifications are available
        mMockMixpanel.availableNotification.set(null);
        mMockMixpanel.availableSurvey.set(mSurvey);

        mCallbacks.onActivityStarted(mValidActivity);

        assertNull(mMockMixpanel.availableSurvey.get());
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
            public InAppNotification getNextInAppNotification() {
                return availableNotification.getAndClear();
            }

            @Override
            public Survey getNextSurvey() {
                return availableSurvey.getAndClear();
            }

            @Override
            public void showSurvey(final Survey s, final Activity parent) {
                showSurveyCalls.add(s);
            }

            @Override
            public void showNotification(final InAppNotification notification, final Activity parent) {
                showNotificationCalls.add(notification);
            }
        };

        public SynchronizedReference<Survey> availableSurvey = new SynchronizedReference<Survey>();
        public SynchronizedReference<InAppNotification> availableNotification = new SynchronizedReference<InAppNotification>();
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
    private Survey mSurvey;
    private InAppNotification mInAppNotification;
}
