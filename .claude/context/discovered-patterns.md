# Discovered Patterns - Mixpanel Android SDK

## Coding Conventions

### Java Code Style

**Package-Private Visibility by Default:**
```java
// CORRECT: Package-private for internal classes
class MPDbAdapter {
    // Implementation details hidden from SDK consumers
}

// INCORRECT: Public for internal classes
public class MPDbAdapter {
    // Exposes internals unnecessarily
}
```

**Final Fields for Immutability:**
```java
// CORRECT: Final fields prevent mutation
private final Context mContext;
private final String mToken;
private final Map<String, String> mDeviceInfo;

// INCORRECT: Mutable fields
private Context mContext;
private String mToken;
```

**Static Nested Classes for Data Models:**
```java
// CORRECT: Static nested class for data containers
public static class EventDescription {
    private final String eventName;
    private final JSONObject properties;
    private final String token;
}

// INCORRECT: Non-static inner class
public class EventDescription {
    // Holds implicit reference to outer class
}
```

### Synchronization Patterns

**Synchronized Blocks Over Methods:**
```java
// CORRECT: Fine-grained synchronization
public void registerSuperProperties(JSONObject properties) {
    synchronized (mSuperPropertiesLock) {
        // Critical section only
    }
}

// AVOID: Entire method synchronized
public synchronized void registerSuperProperties(JSONObject properties) {
    // Over-synchronization
}
```

**Double-Checked Locking for Singletons:**
```java
// CORRECT: Thread-safe lazy initialization
public static MixpanelAPI getInstance(Context context, String token) {
    if (null == mInstances) {
        synchronized (sInstancesLock) {
            if (null == mInstances) {
                mInstances = new HashMap<>();
            }
        }
    }
    // Continue with instance retrieval
}
```

### Error Handling Philosophy

**Silent Failures for Non-Critical Errors:**
```java
// CORRECT: Log and continue
try {
    properties.put("key", value);
} catch (JSONException e) {
    MPLog.e(LOGTAG, "Unable to add property", e);
    // Continue execution
}

// INCORRECT: Throwing exceptions
try {
    properties.put("key", value);
} catch (JSONException e) {
    throw new RuntimeException("Invalid property", e);
}
```

**Defensive Null Checking:**
```java
// CORRECT: Null-safe operations
if (null != mPeople) {
    mPeople.set(property, value);
}

// INCORRECT: Assuming non-null
mPeople.set(property, value); // NPE risk
```

### API Design Patterns

**Fluent Interface for Builders:**
```java
// CORRECT: Method chaining
MixpanelOptions options = new MixpanelOptions.Builder()
    .featureFlagsEnabled(true)
    .gzipPayload(true)
    .flushInterval(60000)
    .build();
```

**Overloaded Methods for Convenience:**
```java
// CORRECT: Progressive disclosure
public static MixpanelAPI getInstance(Context context, String token) {
    return getInstance(context, token, true);
}

public static MixpanelAPI getInstance(Context context, String token, boolean trackAutomaticEvents) {
    return getInstance(context, token, trackAutomaticEvents, null);
}
```

### Threading Patterns

**HandlerThread for Background Work:**
```java
// CORRECT: Dedicated background thread
HandlerThread thread = new HandlerThread(
    "com.mixpanel.android.AnalyticsWorker",
    Process.THREAD_PRIORITY_BACKGROUND
);
thread.start();
mHandler = new AnalyticsMessageHandler(thread.getLooper());
```

**Message-Based Communication:**
```java
// CORRECT: Send messages to background thread
Message msg = Message.obtain();
msg.what = ENQUEUE_EVENTS;
msg.obj = new EventDescription(eventName, properties, token);
mWorker.runMessage(msg);
```

### Database Patterns

**Enum-Based Table Management:**
```java
// CORRECT: Type-safe table references
public enum Table {
    EVENTS("events", DatabaseHelper.EVENTS_TABLE, DB_OUT_OF_MEMORY_ERROR),
    PEOPLE("people", DatabaseHelper.PEOPLE_TABLE, DB_OUT_OF_MEMORY_ERROR);
    
    private final String mTableName;
    Table(String name, String createStatement, String outOfMemoryError) {
        mTableName = name;
    }
}
```

**Cursor Management:**
```java
// CORRECT: Always close cursors
Cursor cursor = null;
try {
    cursor = db.rawQuery(query, null);
    // Process cursor
} finally {
    if (cursor != null) {
        cursor.close();
    }
}
```

### Configuration Patterns

**Hierarchical Configuration:**
```java
// Priority: Runtime > Manifest > Defaults
// 1. Runtime configuration
if (options != null && options.flushInterval != null) {
    return options.flushInterval;
}
// 2. AndroidManifest meta-data
int manifestValue = metaData.getInt("com.mixpanel.android.MPConfig.FlushInterval", -1);
if (manifestValue != -1) {
    return manifestValue;
}
// 3. Default value
return 60 * 1000; // 60 seconds
```

### Testing Patterns

**Test Utilities for Mocking:**
```java
// CORRECT: Centralized test utilities
public class TestUtils {
    public static MixpanelAPI createMixpanelApiWithMockedMessages(
            Context context, BlockingQueue<String> messages) {
        // Creates testable instance
    }
}
```

**Blocking Queues for Async Testing:**
```java
// CORRECT: Synchronous testing of async operations
BlockingQueue<String> messages = new LinkedBlockingQueue<>();
MixpanelAPI api = TestUtils.createMixpanelApiWithMockedMessages(context, messages);
api.track("Test Event");
String message = messages.poll(2, TimeUnit.SECONDS);
assertNotNull(message);
```

### Resource Management

**Lazy Resource Loading:**
```java
// CORRECT: Load resources only when needed
private Drawable mNotificationIcon = null;

private Drawable getNotificationIcon() {
    if (null == mNotificationIcon) {
        mNotificationIcon = BitmapDrawable.createFromPath(iconPath);
    }
    return mNotificationIcon;
}
```

### Documentation Standards

**Comprehensive JavaDoc:**
```java
/**
 * Track an event.
 *
 * <p>Every call to track eventually results in a data point sent to Mixpanel.
 *
 * @param eventName The name of the event to send
 * @param properties A JSONObject containing the key-value pairs of the properties
 *                   to include in this event. Pass null if no extra properties.
 */
public void track(String eventName, JSONObject properties) {
    // Implementation
}
```

### Naming Conventions

**Member Variable Prefix:**
```java
// CORRECT: 'm' prefix for member variables
private final Context mContext;
private final String mToken;
private final MixpanelOptions mConfig;

// INCORRECT: No prefix
private final Context context;
private final String token;
```

**Constants in CAPS_WITH_UNDERSCORES:**
```java
// CORRECT: Clear constant naming
private static final String LOGTAG = "MixpanelAPI";
private static final int FLUSH_QUEUE = 0;
private static final int DATABASE_VERSION = 4;
```

### Lifecycle Integration

**Activity Lifecycle Callbacks:**
```java
// CORRECT: Clean lifecycle integration
public class MixpanelActivityLifecycleCallbacks 
        implements Application.ActivityLifecycleCallbacks {
    @Override
    public void onActivityStarted(Activity activity) {
        // Track app foreground
    }
}
```

### Performance Patterns

**Batch Operations:**
```java
// CORRECT: Batch database operations
db.beginTransaction();
try {
    for (Event event : events) {
        // Insert event
    }
    db.setTransactionSuccessful();
} finally {
    db.endTransaction();
}
```

**Lazy Initialization:**
```java
// CORRECT: Initialize expensive objects on demand
private MPDbAdapter mDb;

private MPDbAdapter getDb() {
    if (null == mDb) {
        mDb = new MPDbAdapter(mContext);
    }
    return mDb;
}
```

These patterns represent the core conventions discovered throughout the Mixpanel Android SDK codebase. They emphasize thread safety, defensive programming, performance optimization, and clean API design suitable for a widely-used SDK.