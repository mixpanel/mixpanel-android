package com.mixpanel.android.mpmetrics;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.display.DisplayManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.Display;
import com.mixpanel.android.util.MPLog;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Abstracts away possibly non-present system information classes, and handles permission-dependent
 * queries for default system information.
 */
/* package */ class SystemInformation {

  /* package */ static SystemInformation getInstance(Context context) {
    synchronized (sInstanceLock) {
      if (null == sInstance) {
        final Context appContext = context.getApplicationContext();
        sInstance = new SystemInformation(appContext);
      }
    }

    return sInstance;
  }

  private SystemInformation(Context context) {
    mContext = context;

    PackageManager packageManager = mContext.getPackageManager();

    String foundAppVersionName = null;
    Integer foundAppVersionCode = null;
    try {
      PackageInfo packageInfo = packageManager.getPackageInfo(mContext.getPackageName(), 0);
      foundAppVersionName = packageInfo.versionName;
      foundAppVersionCode = packageInfo.versionCode;
    } catch (NameNotFoundException e) {
      MPLog.w(
          LOGTAG, "System information constructed with a context that apparently doesn't exist.");
    }

    ApplicationInfo applicationInfo = context.getApplicationInfo();
    int appNameStringId = applicationInfo.labelRes;

    mAppVersionName = foundAppVersionName;
    mAppVersionCode = foundAppVersionCode;
    mAppName =
        appNameStringId == 0
            ? applicationInfo.nonLocalizedLabel == null
                ? "Misc"
                : applicationInfo.nonLocalizedLabel.toString()
            : context.getString(appNameStringId);

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
        foundTelephony =
            (Boolean) hasSystemFeatureMethod.invoke(packageManager, "android.hardware.telephony");
      } catch (InvocationTargetException e) {
        MPLog.w(
            LOGTAG,
            "System version appeared to support PackageManager.hasSystemFeature, but we were unable"
                + " to call it.");
      } catch (IllegalAccessException e) {
        MPLog.w(
            LOGTAG,
            "System version appeared to support PackageManager.hasSystemFeature, but we were unable"
                + " to call it.");
      }
    }

    mHasNFC = foundNFC;
    mHasTelephony = foundTelephony;
    mDisplayMetrics = new DisplayMetrics();

    DisplayManager displayManager =
        (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);
    Display display =
        (displayManager != null) ? displayManager.getDisplay(Display.DEFAULT_DISPLAY) : null;

    if (display != null) {
      display.getMetrics(mDisplayMetrics);
    } else {
      DisplayMetrics defaultMetrics = mContext.getResources().getDisplayMetrics();
      mDisplayMetrics.setTo(defaultMetrics);
    }
  }

  public String getAppVersionName() {
    return mAppVersionName;
  }

  public Integer getAppVersionCode() {
    return mAppVersionCode;
  }

  public String getAppName() {
    return mAppName;
  }

  public boolean hasNFC() {
    return mHasNFC;
  }

  public boolean hasTelephony() {
    return mHasTelephony;
  }

  public DisplayMetrics getDisplayMetrics() {
    return mDisplayMetrics;
  }

  // Note this is the *current*, not the canonical network, because it
  // doesn't require special permissions to access. Unreliable for CDMA phones,
  //
  public String getCurrentNetworkOperator() {
    String ret = null;

    TelephonyManager telephonyManager =
        (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
    if (null != telephonyManager) {
      ret = telephonyManager.getNetworkOperatorName();
    }

    return ret;
  }

  @SuppressLint("MissingPermission")
  @SuppressWarnings("MissingPermission")
  public Boolean isWifiConnected() {
    Boolean ret = null;

    if (PackageManager.PERMISSION_GRANTED
        == mContext.checkCallingOrSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE)) {
      ConnectivityManager connManager =
          (ConnectivityManager) this.mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo networkInfo = connManager.getActiveNetworkInfo();
      ret =
          (networkInfo != null
              && networkInfo.getType() == ConnectivityManager.TYPE_WIFI
              && networkInfo.isConnected());
    }

    return ret;
  }

  @SuppressLint("MissingPermission")
  @SuppressWarnings("MissingPermission")
  public Boolean isBluetoothEnabled() {
    Boolean isBluetoothEnabled = null;
    try {
      PackageManager pm = mContext.getPackageManager();
      int hasBluetoothPermission =
          pm.checkPermission(Manifest.permission.BLUETOOTH, mContext.getPackageName());
      if (hasBluetoothPermission == PackageManager.PERMISSION_GRANTED) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
          isBluetoothEnabled = bluetoothAdapter.isEnabled();
        }
      }
    } catch (Exception e) {
      // something went wrong, don't crash, we can live without it
    }
    return isBluetoothEnabled;
  }

  public String getBluetoothVersion() {
    String bluetoothVersion = "none";
    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
        && mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
      bluetoothVersion = "ble";
    } else if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
      bluetoothVersion = "classic";
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
  private final String mAppName;

  private static SystemInformation sInstance;
  private static final Object sInstanceLock = new Object();

  private static final String LOGTAG = "MixpanelAPI.SysInfo";
}
