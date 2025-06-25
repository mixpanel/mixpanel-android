# Feature Development Workflow

## Adding New Features to Mixpanel Android SDK

### Pre-Development Checklist

- [ ] Review existing similar features for patterns
- [ ] Check API compatibility with iOS/Web SDKs
- [ ] Consider backwards compatibility impact
- [ ] Plan threading model for feature
- [ ] Design error handling approach

### Step 1: Define Public API

**Location:** `src/main/java/com/mixpanel/android/mpmetrics/MixpanelAPI.java`

```java
// Add to main API class
public void newFeature(String param1, JSONObject properties) {
    if (!hasOptedOut()) {
        try {
            // Implementation
        } catch (Exception e) {
            MPLog.e(LOGTAG, "newFeature failed", e);
        }
    }
}

// Add to appropriate interface (People, Group, etc.)
public interface People {
    void newPeopleFeature(String property, Object value);
}
```

### Step 2: Implement Message Types

**Location:** `src/main/java/com/mixpanel/android/mpmetrics/AnalyticsMessages.java`

```java
// Add message type constant
private static final int NEW_FEATURE_MESSAGE = 20;

// Create description class
public static class NewFeatureDescription {
    private final String param1;
    private final JSONObject properties;
    private final String token;
    
    public NewFeatureDescription(String param1, JSONObject properties, String token) {
        this.param1 = param1;
        this.properties = properties;
        this.token = token;
    }
}

// Handle in message handler
@Override
public void handleMessage(Message msg) {
    switch (msg.what) {
        case NEW_FEATURE_MESSAGE:
            NewFeatureDescription desc = (NewFeatureDescription) msg.obj;
            processNewFeature(desc);
            break;
    }
}
```

### Step 3: Add Database Support (if needed)

**Location:** `src/main/java/com/mixpanel/android/mpmetrics/MPDbAdapter.java`

```java
// Add table if new data type
public enum Table {
    EVENTS(...),
    PEOPLE(...),
    NEW_FEATURE("new_feature", 
        DatabaseHelper.NEW_FEATURE_TABLE, 
        "Database out of memory for new feature data.");
}

// Update DatabaseHelper
private static class DatabaseHelper extends SQLiteOpenHelper {
    static final String NEW_FEATURE_TABLE = 
        "CREATE TABLE new_feature (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "data TEXT NOT NULL, created_at INTEGER NOT NULL)";
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        // Add table creation
        db.execSQL(NEW_FEATURE_TABLE);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 5) {
            db.execSQL(NEW_FEATURE_TABLE);
        }
    }
}
```

### Step 4: Add Configuration Options

**Location:** `src/main/java/com/mixpanel/android/mpmetrics/MPConfig.java`

```java
// Add configuration field
private final boolean mNewFeatureEnabled;

// Add to constructor
MPConfig(Bundle metaData, Context context) {
    mNewFeatureEnabled = metaData.getBoolean(
        "com.mixpanel.android.MPConfig.NewFeatureEnabled", true);
}

// Add getter
public boolean getNewFeatureEnabled() {
    return mNewFeatureEnabled;
}
```

**Location:** `src/main/java/com/mixpanel/android/mpmetrics/MixpanelOptions.java`

```java
public static class Builder {
    private boolean newFeatureEnabled = true;
    
    public Builder newFeatureEnabled(boolean enabled) {
        this.newFeatureEnabled = enabled;
        return this;
    }
}
```

### Step 5: Implement Core Logic

**Follow these patterns:**

```java
// Thread safety
private final Object mNewFeatureLock = new Object();

// Defensive programming
public void processNewFeature(String input) {
    if (null == input || input.length() == 0) {
        MPLog.d(LOGTAG, "Invalid input for new feature");
        return;
    }
    
    synchronized (mNewFeatureLock) {
        try {
            // Core logic
        } catch (Exception e) {
            MPLog.e(LOGTAG, "Failed to process new feature", e);
            // Continue gracefully
        }
    }
}

// Resource management
Cursor cursor = null;
try {
    cursor = db.query(...);
    // Process
} finally {
    if (cursor != null) {
        cursor.close();
    }
}
```

### Step 6: Add Tests

**Location:** `src/androidTest/java/com/mixpanel/android/mpmetrics/`

```java
@RunWith(AndroidJUnit4.class)
@LargeTest
public class NewFeatureTest {
    private MixpanelAPI mMixpanel;
    private BlockingQueue<String> mMessages;
    
    @Before
    public void setUp() {
        mMessages = new LinkedBlockingQueue<>();
        mMixpanel = TestUtils.createMixpanelApiWithMockedMessages(
            getContext(), mMessages);
    }
    
    @Test
    public void testNewFeatureBasic() throws Exception {
        mMixpanel.newFeature("test", null);
        String message = mMessages.poll(2, TimeUnit.SECONDS);
        assertNotNull(message);
        
        JSONObject parsed = new JSONObject(message);
        assertEquals("test", parsed.getString("param1"));
    }
    
    @Test
    public void testNewFeatureWithProperties() throws Exception {
        JSONObject props = new JSONObject();
        props.put("key", "value");
        
        mMixpanel.newFeature("test", props);
        // Verify
    }
    
    @Test
    public void testNewFeatureThreadSafety() throws Exception {
        // Test concurrent access
    }
}
```

### Step 7: Update Documentation

**JavaDoc Requirements:**
```java
/**
 * Brief description of the feature.
 *
 * <p>Detailed explanation of behavior and use cases.
 *
 * <p>Example usage:
 * <pre>
 * {@code
 * MixpanelAPI mixpanel = MixpanelAPI.getInstance(context, TOKEN);
 * mixpanel.newFeature("example", properties);
 * }
 * </pre>
 *
 * @param param1 Description of parameter
 * @param properties Optional properties, may be null
 * @see RelatedClass
 * @since 8.3.0
 */
public void newFeature(String param1, JSONObject properties) {
```

### Step 8: Update Demo App

**Location:** `mixpaneldemo/src/main/java/`

Add demonstration of new feature to showcase usage:
```kotlin
// In demo activity
binding.newFeatureButton.setOnClickListener {
    mixpanel.newFeature("demo_value", JSONObject().apply {
        put("source", "demo_app")
    })
    showToast("New feature triggered!")
}
```

### Step 9: Update ProGuard Rules

**Location:** `proguard.txt`

```proguard
# Keep new feature classes if needed
-keep class com.mixpanel.android.mpmetrics.NewFeatureDescription { *; }
```

### Step 10: Version and Release

1. Update version in `gradle.properties`:
   ```properties
   VERSION_NAME=8.3.0
   ```

2. Update `CHANGELOG.md`:
   ```markdown
   ## Version 8.3.0
   - Added new feature for X functionality
   - Improved Y performance
   ```

3. Run release checklist:
   ```bash
   ./gradlew clean build
   ./gradlew test
   ./gradlew connectedAndroidTest
   ./gradlew androidJavadocs
   ```

### Integration Testing Checklist

- [ ] Test with minimum SDK version (21)
- [ ] Test with latest SDK version
- [ ] Test with ProGuard enabled
- [ ] Test offline behavior
- [ ] Test configuration options
- [ ] Test thread safety
- [ ] Test memory usage
- [ ] Test with demo app

### Common Pitfalls to Avoid

1. **Forgetting thread safety** - Always synchronize shared state
2. **Throwing exceptions** - SDK should never crash host app
3. **Blocking main thread** - All I/O on background thread
4. **Memory leaks** - Use application context, weak references
5. **Breaking API compatibility** - Maintain backwards compatibility
6. **Missing null checks** - Defensive programming required
7. **Ignoring configuration** - Respect MPConfig and MixpanelOptions

This workflow ensures new features follow established patterns and maintain the SDK's reliability and performance standards.