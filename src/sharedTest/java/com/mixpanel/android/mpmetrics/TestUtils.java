package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;

import com.mixpanel.android.util.RemoteService;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TestUtils {
    public static byte[] bytes(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("This is not an android device, or a compatible java. WHO ARE YOU?");
        }
    }

    public static class CleanMixpanelAPI extends MixpanelAPI {
        public CleanMixpanelAPI(final Context context, final Future<SharedPreferences> referrerPreferences, final String token, final boolean trackAutomaticEvents) {
            super(context, referrerPreferences, token, MPConfig.getInstance(context, null),
                  new MixpanelOptions.Builder().featureFlagsEnabled(true).build(), trackAutomaticEvents);
        }

        public CleanMixpanelAPI(final Context context, final Future<SharedPreferences> referrerPreferences, final String token) {
            super(context, referrerPreferences, token, MPConfig.getInstance(context, null),
                  new MixpanelOptions.Builder().featureFlagsEnabled(true).build(), false);
        }

        public CleanMixpanelAPI(final Context context, final Future<SharedPreferences> referrerPreferences, final String token, final String instanceName) {
            super(context, referrerPreferences, token, false, null, instanceName, false);
        }

        public CleanMixpanelAPI(final Context context, final Future<SharedPreferences> referrerPreferences, final String token, final MixpanelOptions options) {
            super(context, referrerPreferences, token, MPConfig.getInstance(context, null), options, false);
        }

        @Override
            /* package */ PersistentIdentity getPersistentIdentity(final Context context, final Future<SharedPreferences> referrerPreferences, final String token, final String instanceName, final DeviceIdProvider deviceIdProvider) {
            String instanceKey = instanceName != null ? instanceName : token;
            final String prefsName = "com.mixpanel.android.mpmetrics.MixpanelAPI_" + instanceKey;
            final SharedPreferences ret = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
            ret.edit().clear().commit();

            final String timeEventsPrefsName = "com.mixpanel.android.mpmetrics.MixpanelAPI.TimeEvents_" + instanceKey;
            final SharedPreferences timeSharedPrefs = context.getSharedPreferences(timeEventsPrefsName, Context.MODE_PRIVATE);
            timeSharedPrefs.edit().clear().commit();

            final String mixpanelPrefsName = "com.mixpanel.android.mpmetrics.Mixpanel";
            final SharedPreferences mpSharedPrefs = context.getSharedPreferences(mixpanelPrefsName, Context.MODE_PRIVATE);
            mpSharedPrefs.edit().clear().putBoolean(token, true).putBoolean("has_launched", true).apply();

            return super.getPersistentIdentity(context, referrerPreferences, token, instanceName, deviceIdProvider);
        }

        @Override
            /* package */ boolean sendAppOpen() {
            return false;
        }
    }

    public static class TestResourceIds implements ResourceIds {
        public TestResourceIds(final Map<String, Integer> anIdMap) {
            mIdMap = anIdMap;
        }

        @Override
        public boolean knownIdName(String name) {
            return mIdMap.containsKey(name);
        }

        @Override
        public int idFromName(String name) {
            return mIdMap.get(name);
        }

        @Override
        public String nameForId(int id) {
            for (Map.Entry<String, Integer> entry : mIdMap.entrySet()) {
                if (entry.getValue() == id) {
                    return entry.getKey();
                }
            }

            return null;
        }

        private final Map<String, Integer> mIdMap;
    }

    public static class EmptyPreferences implements Future<SharedPreferences> {
        public EmptyPreferences(Context context) {
            mPrefs = context.getSharedPreferences("MIXPANEL_TEST_PREFERENCES", Context.MODE_PRIVATE);
            mPrefs.edit().clear().commit();
        }

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
        public SharedPreferences get() {
            return mPrefs;
        }

        @Override
        public SharedPreferences get(final long timeout, final TimeUnit unit) {
            return mPrefs;
        }

        private final SharedPreferences mPrefs;
    }

    /**
     * Stub/Mock handler that just runs stuff synchronously
     */
    public static class SynchronousHandler extends Handler {
        @Override
        public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
            dispatchMessage(msg);
            return true;
        }
    }

    /**
     * Cleans up all Mixpanel data (SharedPreferences) for testing.
     * This clears the global Mixpanel SharedPreferences used for tracking instances.
     */
    public static void cleanUpMixpanelData(Context context) {
        String mixpanelPrefsName = "com.mixpanel.android.mpmetrics.Mixpanel";
        SharedPreferences prefs = context.getSharedPreferences(mixpanelPrefsName, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    /**
     * Creates a CleanMixpanelAPI instance with a custom RemoteService for testing.
     * This allows tests to intercept and mock HTTP requests.
     *
     * @param context The test context
     * @param mockService The mock RemoteService to use for HTTP requests
     * @return A MixpanelAPI instance configured with the mock service
     */
    public static MixpanelAPI createMixpanelAPIWithMockHttpService(
            Context context, RemoteService mockService) {
        Future<SharedPreferences> referrerPreferences = new EmptyPreferences(context);
        // Set mock service BEFORE constructing so it's available during super() call
        CleanMixpanelAPIWithMockService.setMockService(mockService);
        return new CleanMixpanelAPIWithMockService(
                context, referrerPreferences, "test_token");
    }

    /**
     * Extension of CleanMixpanelAPI that uses a custom RemoteService for testing.
     */
    public static class CleanMixpanelAPIWithMockService extends CleanMixpanelAPI {
        private static RemoteService sMockService;

        static void setMockService(RemoteService mockService) {
            sMockService = mockService;
        }

        public CleanMixpanelAPIWithMockService(
                Context context,
                Future<SharedPreferences> referrerPreferences,
                String token) {
            super(context, referrerPreferences, token);
        }

        @Override
        RemoteService getHttpService() {
            return sMockService;
        }
    }

}
