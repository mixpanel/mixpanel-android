# Android SDK Development Patterns

## SDK-Specific Android Patterns

### Minimum SDK Strategy

**Supporting API 21+ (Android 5.0):**
```java
// Build.VERSION checks for compatibility
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    // Use newer API
} else {
    // Fallback implementation
}
```

**AndroidX Migration:**
- All support libraries migrated to AndroidX
- Uses androidx.annotation for nullability
- androidx.core for backwards compatibility

### Context Management

**Application Context Usage:**
```java
// CORRECT: Use application context for SDK
public static MixpanelAPI getInstance(Context context, String token) {
    Context appContext = context.getApplicationContext();
    // Prevents activity leaks
}

// INCORRECT: Holding activity context
private Context mContext; // Could be activity
```

**Context-Safe Operations:**
```java
// Safe context usage in background threads
private void performOperation() {
    final Context context = mContext.get();
    if (null != context) {
        // Proceed with operation
    }
}
```

### Lifecycle Integration

**Activity Lifecycle Callbacks:**
```java
public class MixpanelActivityLifecycleCallbacks 
        implements Application.ActivityLifecycleCallbacks {
    
    @Override
    public void onActivityStarted(Activity activity) {
        if (mConfig.getAutoShowMixpanelUpdates()) {
            checkForSurveys();
        }
    }
    
    @Override
    public void onActivityPaused(Activity activity) {
        mPaused = true;
        if (hasStarted()) {
            trackAutomaticEvents();
        }
    }
}
```

**Automatic Registration:**
```java
// SDK automatically registers for lifecycle
if (trackAutomaticEvents && Build.VERSION.SDK_INT >= 14) {
    Application app = (Application) appContext;
    app.registerActivityLifecycleCallbacks(
        new MixpanelActivityLifecycleCallbacks(this)
    );
}
```

### Permission Handling

**No Runtime Permissions Required:**
```xml
<!-- Only normal permissions needed -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.BLUETOOTH" />
```

**Optional Permission Checks:**
```java
// Check permissions defensively
boolean hasPermission = PackageManager.PERMISSION_GRANTED ==
    context.checkCallingOrSelfPermission(permission);
if (hasPermission) {
    // Use feature requiring permission
}
```

### Resource Management

**Resource ID Handling:**
```java
public class ResourceIds {
    // Avoids R.class dependency in library
    private final Map<String, Integer> mIdNameToId;
    
    public boolean hasResource(String name) {
        return mIdNameToId.containsKey(name);
    }
    
    public int idFromName(String name) {
        return mIdNameToId.get(name);
    }
}
```

**Dynamic Resource Loading:**
```java
// Load resources without R references
int id = resources.getIdentifier(name, defType, defPackage);
if (0 != id) {
    return resources.getDrawable(id);
}
```

### Storage Patterns

**SharedPreferences Usage:**
```java
// Namespace preferences to avoid conflicts
private static final String PREFERENCES_NAME = 
    "com.mixpanel.android.mpmetrics.MixpanelAPI_";

SharedPreferences prefs = context.getSharedPreferences(
    PREFERENCES_NAME + token, 
    Context.MODE_PRIVATE
);
```

**Database Path Management:**
```java
// Use proper database directory
File dbDir = context.getDatabasePath(dbName).getParentFile();
if (!dbDir.exists()) {
    dbDir.mkdirs();
}
```

### Threading Patterns

**HandlerThread Over Service:**
```java
// Lightweight background processing
HandlerThread workerThread = new HandlerThread(
    "com.mixpanel.android.AnalyticsWorker",
    Process.THREAD_PRIORITY_BACKGROUND
);
workerThread.start();
```

**Main Thread Checks:**
```java
// Ensure UI operations on main thread
if (Looper.myLooper() == Looper.getMainLooper()) {
    // On main thread
} else {
    // Post to main thread
    mMainHandler.post(runnable);
}
```

### ProGuard/R8 Configuration

**Consumer Rules:**
```proguard
# Mixpanel
-keep class com.mixpanel.android.** { *; }
-dontwarn com.mixpanel.android.**

# Keep survey and notification support
-keep class com.mixpanel.android.surveys.** { *; }
-keep class com.mixpanel.android.notifications.** { *; }

# Preserve annotations
-keepattributes *Annotation*
```

### Build Configuration

**BuildConfig Usage:**
```java
// Version tracking
public static final String VERSION = 
    com.mixpanel.android.BuildConfig.VERSION_NAME;

// Debug mode detection
if (BuildConfig.DEBUG) {
    MPLog.setLevel(MPLog.LogLevel.VERBOSE);
}
```

### Manifest Configuration

**Meta-data Configuration:**
```xml
<application>
    <!-- Configuration via manifest -->
    <meta-data 
        android:name="com.mixpanel.android.MPConfig.EnableDebugLogging"
        android:value="true" />
    
    <meta-data 
        android:name="com.mixpanel.android.MPConfig.FlushInterval"
        android:value="30000" />
        
    <meta-data 
        android:name="com.mixpanel.android.MPConfig.DataExpiration"  
        android:value="432000000" /> <!-- 5 days -->
</application>
```

### Network State Monitoring

**Connectivity Receiver:**
```java
public class ConnectivityReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(
                intent.getAction())) {
            // Network state changed
            checkNetworkAndFlush();
        }
    }
}
```

### Memory Management

**Weak References for Callbacks:**
```java
// Prevent memory leaks
private final WeakReference<Activity> mActivity;

public void performAction() {
    Activity activity = mActivity.get();
    if (null != activity && !activity.isFinishing()) {
        // Safe to use activity
    }
}
```

### SDK Initialization

**Lazy Initialization Pattern:**
```java
// Initialize only when first used
private static MixpanelAPI getInstance() {
    if (null == sInstance) {
        synchronized (sInstanceLock) {
            if (null == sInstance) {
                sInstance = new MixpanelAPI();
            }
        }
    }
    return sInstance;
}
```

### Error Recovery

**Defensive Programming:**
```java
// Recover from configuration errors
try {
    ApplicationInfo appInfo = packageManager.getApplicationInfo(
        packageName, PackageManager.GET_META_DATA);
    Bundle metaData = appInfo.metaData;
    // Use meta data
} catch (NameNotFoundException e) {
    // Use defaults
}
```

These Android-specific patterns ensure the SDK works reliably across different Android versions, device configurations, and app architectures while maintaining a small footprint and minimal impact on the host application.