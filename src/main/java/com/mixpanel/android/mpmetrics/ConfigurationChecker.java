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

    public static boolean checkPushConfiguration(Context context) {

        if (Build.VERSION.SDK_INT < 8) {
            // Not a warning, may be expected behavior
            MPLog.i(LOGTAG, "Mixpanel push notifications not supported in SDK " + Build.VERSION.SDK_INT);
            return false;
        }

        final PackageManager packageManager = context.getPackageManager();
        final String packageName = context.getPackageName();

        if (packageManager == null || packageName == null) {
            MPLog.w(LOGTAG, "Can't check configuration when using a Context with null packageManager or packageName");
            return false;
        }

        final String permissionName = packageName + ".permission.C2D_MESSAGE";

        // check special permission
        try {
            packageManager.getPermissionInfo(permissionName, PackageManager.GET_META_DATA);
        } catch (final NameNotFoundException e) {
            MPLog.w(LOGTAG, "Application does not define permission " + permissionName);
            MPLog.i(LOGTAG, "You will need to add the following lines to your application manifest:\n" +
                    "<permission android:name=\"" + packageName + ".permission.C2D_MESSAGE\" android:protectionLevel=\"signature\" />\n" +
                    "<uses-permission android:name=\"" + packageName + ".permission.C2D_MESSAGE\" />");
            return false;
        }
        // check regular permissions

        if (PackageManager.PERMISSION_GRANTED != packageManager.checkPermission("com.google.android.c2dm.permission.RECEIVE", packageName)) {
            MPLog.w(LOGTAG, "Package does not have permission com.google.android.c2dm.permission.RECEIVE");
            MPLog.i(LOGTAG, "You can fix this by adding the following to your AndroidManifest.xml file:\n" +
                    "<uses-permission android:name=\"com.google.android.c2dm.permission.RECEIVE\" />");
            return false;
        }

        if (PackageManager.PERMISSION_GRANTED != packageManager.checkPermission("android.permission.INTERNET", packageName)) {
            MPLog.w(LOGTAG, "Package does not have permission android.permission.INTERNET");
            MPLog.i(LOGTAG, "You can fix this by adding the following to your AndroidManifest.xml file:\n" +
                    "<uses-permission android:name=\"android.permission.INTERNET\" />");
            return false;
        }

        if (PackageManager.PERMISSION_GRANTED != packageManager.checkPermission("android.permission.WAKE_LOCK", packageName)) {
            MPLog.w(LOGTAG, "Package does not have permission android.permission.WAKE_LOCK");
            MPLog.i(LOGTAG, "You can fix this by adding the following to your AndroidManifest.xml file:\n" +
                    "<uses-permission android:name=\"android.permission.WAKE_LOCK\" />");
            return false;
        }

        // This permission is only required on older devices
        if (PackageManager.PERMISSION_GRANTED != packageManager.checkPermission("android.permission.GET_ACCOUNTS", packageName)) {
            MPLog.i(LOGTAG, "Package does not have permission android.permission.GET_ACCOUNTS");
            MPLog.i(LOGTAG, "Android versions below 4.1 require GET_ACCOUNTS to receive Mixpanel push notifications.\n" +
                    "Devices with later OS versions will still be able to receive messages, but if you'd like to support " +
                    "older devices, you'll need to add the following to your AndroidManifest.xml file:\n" +
                    "<uses-permission android:name=\"android.permission.GET_ACCOUNTS\" />");

            if (Build.VERSION.SDK_INT < 16) {
                return false;
            }
        }

        // check receivers
        final PackageInfo receiversInfo;
        try {
            receiversInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_RECEIVERS);
        } catch (final NameNotFoundException e) {
            MPLog.w(LOGTAG, "Could not get receivers for package " + packageName);
            return false;
        }

        final ActivityInfo[] receivers = receiversInfo.receivers;
        if (receivers == null || receivers.length == 0) {
            MPLog.w(LOGTAG, "No receiver for package " + packageName);
            MPLog.i(LOGTAG, "You can fix this with the following into your <application> tag:\n" +
                            samplePushConfigurationMessage(packageName));
            return false;
        }

        final Set<String> allowedReceivers = new HashSet<String>();
        for (final ActivityInfo receiver : receivers) {
            if ("com.google.android.c2dm.permission.SEND".equals(receiver.permission)) {
                allowedReceivers.add(receiver.name);
            }
        }

        if (allowedReceivers.isEmpty()) {
            MPLog.w(LOGTAG, "No receiver allowed to receive com.google.android.c2dm.permission.SEND");
            MPLog.i(LOGTAG, "You can fix by pasting the following into the <application> tag in your AndroidManifest.xml:\n" +
                    samplePushConfigurationMessage(packageName));
            return false;
        }

        if (!checkReceiver(context, allowedReceivers, "com.google.android.c2dm.intent.RECEIVE")) {
            return false;
        }

        boolean canRegisterWithPlayServices = false;
        try {
            Class.forName("com.google.android.gms.common.GooglePlayServicesUtil");
            canRegisterWithPlayServices = true;
        } catch(final ClassNotFoundException e) {
            MPLog.w(LOGTAG, "Google Play Services aren't included in your build- push notifications won't work on Lollipop/API 21 or greater");
            MPLog.i(LOGTAG, "You can fix this by adding com.google.android.gms:play-services as a dependency of your gradle or maven project");
        }

        boolean canRegisterWithRegistrationIntent = true;
        if (!checkReceiver(context, allowedReceivers, "com.google.android.c2dm.intent.REGISTRATION")) {
            MPLog.i(LOGTAG, "(You can still receive push notifications on Lollipop/API 21 or greater with this configuration)");
            canRegisterWithRegistrationIntent = false;
        }

        return canRegisterWithPlayServices || canRegisterWithRegistrationIntent;
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

    private static String samplePushConfigurationMessage(String packageName) {
        return
        "<receiver android:name=\"com.mixpanel.android.mpmetrics.GCMReceiver\"\n" +
        "          android:permission=\"com.google.android.c2dm.permission.SEND\" >\n" +
        "    <intent-filter>\n" +
        "       <action android:name=\"com.google.android.c2dm.intent.RECEIVE\" />\n" +
        "       <action android:name=\"com.google.android.c2dm.intent.REGISTRATION\" />\n" +
        "       <category android:name=\"" + packageName + "\" />\n" +
        "    </intent-filter>\n" +
        "</receiver>";
    }

    private static boolean checkReceiver(Context context, Set<String> allowedReceivers, String action) {
        final PackageManager pm = context.getPackageManager();
        final String packageName = context.getPackageName();
        final Intent intent = new Intent(action);
        intent.setPackage(packageName);
        final List<ResolveInfo> receivers = pm.queryBroadcastReceivers(intent, PackageManager.GET_META_DATA);

        if (receivers.isEmpty()) {
            MPLog.w(LOGTAG, "No receivers for action " + action);
            MPLog.i(LOGTAG, "You can fix by pasting the following into the <application> tag in your AndroidManifest.xml:\n" +
                    samplePushConfigurationMessage(packageName));
            return false;
        }
        // make sure receivers match
        for (final ResolveInfo receiver : receivers) {
            final String name = receiver.activityInfo.name;
            if (!allowedReceivers.contains(name)) {
                MPLog.w(LOGTAG, "Receiver " + name + " is not set with permission com.google.android.c2dm.permission.SEND");
                MPLog.i(LOGTAG, "Please add the attribute 'android:permission=\"com.google.android.c2dm.permission.SEND\"' to your <receiver> tag");
                return false;
            }
        }

        return true;
    }
}
