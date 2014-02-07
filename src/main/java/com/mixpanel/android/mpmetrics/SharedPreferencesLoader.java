package com.mixpanel.android.mpmetrics;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferencesLoader {

    public SharedPreferencesLoader() {
        mExecutor = Executors.newSingleThreadExecutor();
    }

    public Future<SharedPreferences> loadPreferences(Context context, String name) {
        final LoadSharedPreferences loadSharedPrefs = new LoadSharedPreferences(context, name);
        final FutureTask<SharedPreferences> task = new FutureTask<SharedPreferences>(loadSharedPrefs);
        mExecutor.execute(task);
        return task;
    }

    private static class LoadSharedPreferences implements Callable<SharedPreferences> {
        public LoadSharedPreferences(Context context, String prefsName) {
            mContext = context;
            mPrefsName = prefsName;
        }

        @Override
        public SharedPreferences call() {
            return mContext.getSharedPreferences(mPrefsName, Context.MODE_PRIVATE);
        }

        private final Context mContext;
        private final String mPrefsName;
    }

    private final Executor mExecutor;
}
