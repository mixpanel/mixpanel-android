package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;

import com.mixpanel.android.takeoverinapp.TakeoverInAppActivity;
import com.mixpanel.android.util.MPLog;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

    public static boolean checkPushNotificationConfiguration(Context context) {
        final PackageManager packageManager = context.getPackageManager();
        final String packageName = context.getPackageName();

        if (packageManager == null || packageName == null) {
            return false;
        }

        if (PackageManager.PERMISSION_GRANTED != packageManager.checkPermission("android.permission.INTERNET", packageName)) {
            MPLog.w(LOGTAG, "Package does not have permission android.permission.INTERNET");
            MPLog.i(LOGTAG, "You can fix this by adding the following to your AndroidManifest.xml file:\n" +
                    "<uses-permission android:name=\"android.permission.INTERNET\" />");
            return false;
        }

        // check services
        final PackageInfo servicesInfo;
        try {
            servicesInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SERVICES);
        } catch (final PackageManager.NameNotFoundException e) {
            return false;
        }
        final ServiceInfo[] services = servicesInfo.services;
        if (services == null || services.length == 0) {
            return false;
        }

        Intent intent = new Intent("com.google.firebase.MESSAGING_EVENT");
        intent.setPackage(packageName);
        List<ResolveInfo> intentServices = packageManager.queryIntentServices(intent, PackageManager.GET_META_DATA);
        Iterator<ResolveInfo> it = intentServices.iterator();
        while (it.hasNext()) {
            ResolveInfo resolveInfo = it.next();
            String serviceName = resolveInfo.serviceInfo.name;
            if (!serviceName.startsWith(packageName) && !serviceName.equals("com.mixpanel.android.mpmetrics.MixpanelFCMMessagingService")) {
                it.remove();
            }
        }
        if (intentServices == null || intentServices.size() == 0) {
            return false;
        }

        List<ServiceInfo> registeredServices = new ArrayList<>();
        for (ResolveInfo intentService : intentServices) {
            for (ServiceInfo serviceInfo : services) {
                if (serviceInfo.name.equals(intentService.serviceInfo.name) && serviceInfo.isEnabled()) {
                    registeredServices.add(intentService.serviceInfo);
                }
            }
        }
        if (registeredServices.size() > 1) {
            MPLog.w(LOGTAG, "You can't have more than one service handling \"com.google.firebase.MESSAGING_EVENT\" intent filter. " +
                    "Android will only use the first one that is declared on your AndroidManifest.xml. If you have more than one push provider " +
                    "you need to crate your own FirebaseMessagingService class.");
        }

        try {
            Class.forName("com.google.android.gms.common.GooglePlayServicesUtil");
        } catch(final ClassNotFoundException e) {
            MPLog.w(LOGTAG, "Google Play Services aren't included in your build- push notifications won't work on Lollipop/API 21 or greater");
            MPLog.i(LOGTAG, "You can fix this by adding com.google.android.gms:play-services as a dependency of your gradle or maven project");
        }

        return true;
    }
}
