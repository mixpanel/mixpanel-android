package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;

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
            super(context, referrerPreferences, token, false, null, trackAutomaticEvents);
        }

        public CleanMixpanelAPI(final Context context, final Future<SharedPreferences> referrerPreferences, final String token) {
            super(context, referrerPreferences, token, false, null, false);
        }

        public CleanMixpanelAPI(final Context context, final Future<SharedPreferences> referrerPreferences, final String token, final String instanceName) {
            super(context, referrerPreferences, token, false, null, instanceName, false);
        }

        @Override
            /* package */ PersistentIdentity getPersistentIdentity(final Context context, final Future<SharedPreferences> referrerPreferences, final String token, final String instanceName) {
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

            return super.getPersistentIdentity(context, referrerPreferences, token, instanceName);
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

}
