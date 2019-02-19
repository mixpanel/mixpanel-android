package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.os.Build;

import com.mixpanel.android.takeoverinapp.TakeoverInAppActivity;
import com.mixpanel.android.util.MPLog;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file has been modified from its original version by Mixpanel, Inc. The original
 * contents were part of GCMRegistrar, retrieved from
 * https://code.google.com/p/gcm/source/browse/gcm-client/src/com/google/android/gcm/GCMRegistrar.java
 * on Jan 3, 2013
 */


/* package */ class ConfigurationChecker {

    public static String LOGTAG = "MixpanelAPI.ConfigurationChecker";

    private static Boolean mTakeoverActivityAvailable;

    public static boolean checkBasicConfiguration(Context context) {
        final PackageManager packageManager = context.getPackageManager();
        final String packageName = context.getPackageName();

        if (packageManager == null || packageName == null) {
            MPLog.w(LOGTAG, "Can't check configuration when using a Context with null packageManager or packageName");
            return false;
        }
        if (PackageManager.PERMISSION_GRANTED != packageManager.checkPermission("android.permission.INTERNET", packageName)) {
            MPLog.w(LOGTAG, "Package does not have permission android.permission.INTERNET - Mixpanel will not work at all!");
            MPLog.i(LOGTAG, "You can fix this by adding the following to your AndroidManifest.xml file:\n" +
                    "<uses-permission android:name=\"android.permission.INTERNET\" />");
            return false;
        }

        return true;
    }

    public static boolean checkTakeoverInAppActivityAvailable(Context context) {
        if (mTakeoverActivityAvailable == null) {
            if (Build.VERSION.SDK_INT < MPConfig.UI_FEATURES_MIN_API) {
                // No need to log, TakeoverInAppActivity doesn't work on this platform.
                mTakeoverActivityAvailable = false;
                return mTakeoverActivityAvailable;
            }

            final Intent takeoverInAppIntent = new Intent(context, TakeoverInAppActivity.class);
            takeoverInAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            takeoverInAppIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

            final PackageManager packageManager = context.getPackageManager();
            final List<ResolveInfo> intentActivities = packageManager.queryIntentActivities(takeoverInAppIntent, 0);
            if (intentActivities.size() == 0) {
                MPLog.w(LOGTAG, TakeoverInAppActivity.class.getName() + " is not registered as an activity in your application, so takeover in-apps can't be shown.");
                MPLog.i(LOGTAG, "Please add the child tag <activity android:name=\"com.mixpanel.android.takeoverinapp.TakeoverInAppActivity\" /> to your <application> tag.");
                mTakeoverActivityAvailable = false;
                return mTakeoverActivityAvailable;
            }

            mTakeoverActivityAvailable = true;
        }

        return mTakeoverActivityAvailable;
    }
}
