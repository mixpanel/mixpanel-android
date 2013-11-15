package com.mixpanel.android.mpmetrics;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

/**
 * Abstracts away possibly non-present system information classes,
 * and handles permission-dependent queries for default system information.
 */
/* package */ class SystemInformation {
    public static final String LOGTAG = "MixpanelAPI";

    public SystemInformation(Context context) {
        mContext = context;

        PackageManager packageManager = mContext.getPackageManager();

        String foundAppVersionName = null;
        Integer foundAppVersionCode = null;
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(mContext.getPackageName(), 0);
            foundAppVersionName = packageInfo.versionName;
            foundAppVersionCode = packageInfo.versionCode;
        } catch (NameNotFoundException e) {
            Log.w(LOGTAG, "System information constructed with a context that apparently doesn't exist.");
        }

        mAppVersionName = foundAppVersionName;
        mAppVersionCode = foundAppVersionCode;

        // We can't count on these features being available, since we need to
        // run on old devices. Thus, the reflection fandango below...
        Class<? extends PackageManager> packageManagerClass = packageManager.getClass();

        Method hasSystemFeatureMethod = null;
        try {
            hasSystemFeatureMethod = packageManagerClass.getMethod("hasSystemFeature", String.class);
        } catch (NoSuchMethodException e) {
            // Nothing, this is an expected outcome
        }

        Boolean foundNFC = null;
        Boolean foundTelephony = null;
        if (null != hasSystemFeatureMethod) {
            try {
                foundNFC = (Boolean) hasSystemFeatureMethod.invoke(packageManager, "android.hardware.nfc");
                foundTelephony = (Boolean) hasSystemFeatureMethod.invoke(packageManager, "android.hardware.telephony");
            } catch (InvocationTargetException e) {
                Log.w(LOGTAG, "System version appeared to support PackageManager.hasSystemFeature, but we were unable to call it.");
            } catch (IllegalAccessException e) {
                Log.w(LOGTAG, "System version appeared to support PackageManager.hasSystemFeature, but we were unable to call it.");
            }
        }

        mHasNFC = foundNFC;
        mHasTelephony = foundTelephony;
        mDisplayMetrics = new DisplayMetrics();

        Display display = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        display.getMetrics(mDisplayMetrics);
    }

    public String getAppVersionName() { return mAppVersionName; }

    public Integer getAppVersionCode() { return mAppVersionCode; }

    public boolean hasNFC() { return mHasNFC; }

    public boolean hasTelephony() { return mHasTelephony; }

    public DisplayMetrics getDisplayMetrics() { return mDisplayMetrics; }

    public String getPhoneRadioType() {
        String ret = null;

        TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (null != telephonyManager) {
            switch(telephonyManager.getPhoneType()) {
            case 0x00000000: // TelephonyManager.PHONE_TYPE_NONE
                ret = "none";
                break;
            case 0x00000001: // TelephonyManager.PHONE_TYPE_GSM
                ret = "gsm";
                break;
            case 0x00000002: // TelephonyManager.PHONE_TYPE_CDMA
                ret = "cdma";
                break;
            case 0x00000003: // TelephonyManager.PHONE_TYPE_SIP
                ret = "sip";
                break;
            default:
                ret = null;
            }
        }

        return ret;
    }

    // Note this is the *current*, not the canonical network, because it
    // doesn't require special permissions to access. Unreliable for CDMA phones,
    //
    public String getCurrentNetworkOperator() {
        String ret = null;

        TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (null != telephonyManager)
            ret = telephonyManager.getNetworkOperatorName();

        return ret;
    }


    public Boolean isWifiConnected() {
        Boolean ret = null;

        if (PackageManager.PERMISSION_GRANTED == mContext.checkCallingOrSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE)) {
            ConnectivityManager connManager = (ConnectivityManager) this.mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo wifiInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            ret = wifiInfo.isConnected();
        }

        return ret;
    }

    public Boolean isBluetoothEnabled() {
        Boolean isBluetoothEnabled = null;
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null) {
                isBluetoothEnabled = bluetoothAdapter.isEnabled();
            }
        } catch (SecurityException e) {
            // do nothing since we don't have permissions
        }
        return isBluetoothEnabled;
    }

    public String getBluetoothVersion() {
        String bluetoothVersion = null;
        if (android.os.Build.VERSION.SDK_INT >= 8) {
            bluetoothVersion = "none";
            if(android.os.Build.VERSION.SDK_INT >= 18 &&
                    mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                bluetoothVersion = "ble";
            } else if(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
                bluetoothVersion = "classic";
            }
        }
        return bluetoothVersion;
    }

    private final Context mContext;

    // Unchanging facts
    private final Boolean mHasNFC;
    private final Boolean mHasTelephony;
    private final DisplayMetrics mDisplayMetrics;
    private final String mAppVersionName;
    private final Integer mAppVersionCode;
}
