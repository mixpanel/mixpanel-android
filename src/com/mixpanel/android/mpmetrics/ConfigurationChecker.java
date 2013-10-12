package com.mixpanel.android.mpmetrics;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.util.Log;

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

    public static boolean checkPushConfiguration(Context context) {

        if (Build.VERSION.SDK_INT < 8) {
            // Not a warning, may be expected behavior
            Log.i(LOGTAG, "Push not supported in SDK " + Build.VERSION.SDK_INT);
            return false;
        }

        PackageManager packageManager = context.getPackageManager();
        String packageName = context.getPackageName();
        String permissionName = packageName + ".permission.C2D_MESSAGE";
        // check special permission
        try {
            packageManager.getPermissionInfo(permissionName, PackageManager.GET_PERMISSIONS);
        } catch (NameNotFoundException e) {
            Log.w(LOGTAG, "Application does not define permission " + permissionName);
            Log.i(LOGTAG, "You will need to add the following lines to your application manifest:\n" +
                    "<permission android:name=\"" + packageName + ".permission.C2D_MESSAGE\" android:protectionLevel=\"signature\" />\n" +
                    "<uses-permission android:name=\"" + packageName + ".permission.C2D_MESSAGE\" />");
            return false;
        }
        // check regular permissions

        if(PackageManager.PERMISSION_GRANTED != packageManager.checkPermission("com.google.android.c2dm.permission.RECEIVE", packageName)) {
            Log.w(LOGTAG, "Package does not have permission com.google.android.c2dm.permission.RECEIVE");
            Log.i(LOGTAG, "You can fix this by adding the following to your AndroidManifest.xml file:\n" +
                    "<uses-permission android:name=\"com.google.android.c2dm.permission.RECEIVE\" />");
            return false;
        }

        if(PackageManager.PERMISSION_GRANTED != packageManager.checkPermission("android.permission.INTERNET", packageName)) {
            Log.w(LOGTAG, "Package does not have permission android.permission.INTERNET");
            Log.i(LOGTAG, "You can fix this by adding the following to your AndroidManifest.xml file:\n" +
                    "<uses-permission android:name=\"android.permission.INTERNET\" />");
            return false;
        }

        if(PackageManager.PERMISSION_GRANTED != packageManager.checkPermission("android.permission.GET_ACCOUNTS", packageName)) {
            Log.w(LOGTAG, "Package does not have permission android.permission.GET_ACCOUNTS");
            Log.i(LOGTAG, "You can fix this by adding the following to your AndroidManifest.xml file:\n" +
                    "<uses-permission android:name=\"android.permission.GET_ACCOUNTS\" />");
            return false;
        }

        if(PackageManager.PERMISSION_GRANTED != packageManager.checkPermission("android.permission.WAKE_LOCK", packageName)) {
            Log.w(LOGTAG, "Package does not have permission android.permission.WAKE_LOCK");
            Log.i(LOGTAG, "You can fix this by adding the following to your AndroidManifest.xml file:\n" +
                    "<uses-permission android:name=\"android.permission.WAKE_LOCK\" />");
            return false;
        }

        // check receivers
        PackageInfo receiversInfo;
        try {
            receiversInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_RECEIVERS);
        } catch (NameNotFoundException e) {
            Log.w(LOGTAG, "Could not get receivers for package " + packageName);
            return false;
        }
        ActivityInfo[] receivers = receiversInfo.receivers;
        if (receivers == null || receivers.length == 0) {
            Log.w(LOGTAG, "No receiver for package " + packageName);
            Log.i(LOGTAG, "You can fix this with the following into your <application> tag:\n" +
                            sampleConfigurationMessage(packageName));
            return false;
        }

        Set<String> allowedReceivers = new HashSet<String>();
        for (ActivityInfo receiver : receivers) {
            if ("com.google.android.c2dm.permission.SEND".equals(receiver.permission)) {
                allowedReceivers.add(receiver.name);
            }
        }

        if (allowedReceivers.isEmpty()) {
            Log.w(LOGTAG, "No receiver allowed to receive com.google.android.c2dm.permission.SEND");
            Log.i(LOGTAG, "You can fix by pasting the following into the <application> tag in your AndroidManifest.xml:\n" +
                    sampleConfigurationMessage(packageName));
            return false;
        }

        return checkReceiver(context, allowedReceivers, "com.google.android.c2dm.intent.REGISTRATION") &&
                checkReceiver(context, allowedReceivers, "com.google.android.c2dm.intent.RECEIVE");
    }

    private static boolean checkReceiver(Context context, Set<String> allowedReceivers, String action) {
        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();
        Intent intent = new Intent(action);
        intent.setPackage(packageName);
        List<ResolveInfo> receivers = pm.queryBroadcastReceivers(intent, PackageManager.GET_INTENT_FILTERS);

        if (receivers.isEmpty()) {
            Log.w(LOGTAG, "No receivers for action " + action);
            Log.i(LOGTAG, "You can fix by pasting the following into the <application> tag in your AndroidManifest.xml:\n" +
                    sampleConfigurationMessage(packageName));
            return false;
        }
        // make sure receivers match
        for (ResolveInfo receiver : receivers) {
            String name = receiver.activityInfo.name;
            if (!allowedReceivers.contains(name)) {
                Log.w(LOGTAG, "Receiver " + name + " is not set with permission com.google.android.c2dm.permission.SEND");
                Log.i(LOGTAG, "Please add the attribute 'android:permission=\"com.google.android.c2dm.permission.SEND\"' to your <receiver> tag");
                return false;
            }
        }

        return true;
    }

    public static String sampleConfigurationMessage(String packageName) {
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
}
