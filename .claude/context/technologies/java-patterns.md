# Java Patterns in Mixpanel Android SDK

## Core Java Patterns

### Singleton with Token-Based Instances

**Multi-Instance Singleton Pattern:**
```java
public class MixpanelAPI {
    private static final Map<String, MixpanelAPI> sInstances = new HashMap<>();
    private static final Object sInstancesLock = new Object();
    
    public static MixpanelAPI getInstance(Context context, String token) {
        synchronized (sInstancesLock) {
            Context appContext = context.getApplicationContext();
            MixpanelAPI instance = sInstances.get(token);
            
            if (instance == null) {
                instance = new MixpanelAPI(appContext, token);
                sInstances.put(token, instance);
            }
            return instance;
        }
    }
}
```

### Builder Pattern

**Fluent Configuration Builder:**
```java
public class MixpanelOptions {
    private final boolean mFeatureFlagsEnabled;
    private final Integer mFlushInterval;
    private final boolean mGzipPayload;
    
    private MixpanelOptions(Builder builder) {
        this.mFeatureFlagsEnabled = builder.featureFlagsEnabled;
        this.mFlushInterval = builder.flushInterval;
        this.mGzipPayload = builder.gzipPayload;
    }
    
    public static class Builder {
        private boolean featureFlagsEnabled = true;
        private Integer flushInterval = null;
        private boolean gzipPayload = true;
        
        public Builder featureFlagsEnabled(boolean enabled) {
            this.featureFlagsEnabled = enabled;
            return this;
        }
        
        public MixpanelOptions build() {
            return new MixpanelOptions(this);
        }
    }
}
```

### Interface Segregation

**Focused Public Interfaces:**
```java
public interface People {
    void identify(String distinctId);
    void set(String property, Object value);
    void setOnce(String property, Object value);
    void increment(String property, double value);
    void append(String property, Object value);
    void union(String property, JSONArray values);
    void unset(String property);
    void deleteUser();
}

public interface Group {
    void set(String property, Object value);
    void setOnce(String property, Object value);
    void remove(String property, Object value);
    void union(String property, JSONArray values);
    void unset(String property);
    void deleteGroup();
}
```

### Enum Pattern for Type Safety

**Database Table Enum:**
```java
public enum Table {
    EVENTS("events", DatabaseHelper.EVENTS_TABLE, DB_OUT_OF_MEMORY_ERROR),
    PEOPLE("people", DatabaseHelper.PEOPLE_TABLE, DB_OUT_OF_MEMORY_ERROR),
    ANONYMOUS_PEOPLE("anonymous_people", DatabaseHelper.ANONYMOUS_PEOPLE_TABLE, DB_OUT_OF_MEMORY_ERROR),
    GROUPS("groups", DatabaseHelper.GROUPS_TABLE, DB_OUT_OF_MEMORY_ERROR);
    
    private final String mTableName;
    private final String mCreateTableStatement;
    private final String mOutOfMemoryError;
    
    Table(String name, String createStatement, String outOfMemoryError) {
        mTableName = name;
        mCreateTableStatement = createStatement;
        mOutOfMemoryError = outOfMemoryError;
    }
    
    public String getName() {
        return mTableName;
    }
}
```

### Immutable Value Objects

**Event Description:**
```java
public static final class EventDescription {
    private final String eventName;
    private final JSONObject properties;
    private final String token;
    private final boolean isAutomatic;
    
    public EventDescription(String eventName, JSONObject properties, 
                           String token, boolean isAutomatic) {
        this.eventName = eventName;
        this.properties = properties;
        this.token = token;
        this.isAutomatic = isAutomatic;
    }
    
    // Only getters, no setters
    public String getEventName() {
        return eventName;
    }
}
```

### Thread Safety Patterns

**Fine-Grained Locking:**
```java
public class PersistentIdentity {
    private final Object mSuperPropertiesLock = new Object();
    private final Object mReferrerPropertiesLock = new Object();
    
    public void registerSuperProperties(JSONObject properties) {
        synchronized (mSuperPropertiesLock) {
            JSONObject currentProps = getSuperProperties();
            // Merge properties
            saveSuperProperties(currentProps);
        }
    }
    
    public void updateReferrerProperties(JSONObject properties) {
        synchronized (mReferrerPropertiesLock) {
            // Update referrer properties separately
        }
    }
}
```

### Factory Pattern

**Message Factory:**
```java
public class AnalyticsMessages {
    private static final class AnalyticsMessageHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ENQUEUE_EVENTS:
                    EventDescription event = (EventDescription) msg.obj;
                    handleEventMessage(event);
                    break;
                case FLUSH_QUEUE:
                    handleFlushMessage();
                    break;
                // Other cases
            }
        }
    }
}
```

### Observer Pattern

**Feature Flag Updates:**
```java
public interface OnFeatureFlagUpdateListener {
    void onFeatureFlagUpdate(String flagKey, Object flagValue);
}

public class FeatureFlagManager {
    private final List<OnFeatureFlagUpdateListener> mListeners;
    
    public void addOnFeatureFlagUpdateListener(OnFeatureFlagUpdateListener listener) {
        synchronized (mListeners) {
            mListeners.add(listener);
        }
    }
    
    private void notifyListeners(String flagKey, Object flagValue) {
        synchronized (mListeners) {
            for (OnFeatureFlagUpdateListener listener : mListeners) {
                listener.onFeatureFlagUpdate(flagKey, flagValue);
            }
        }
    }
}
```

### Null Object Pattern

**Empty Preferences Implementation:**
```java
public class EmptyPreferences implements SharedPreferences {
    @Override
    public Map<String, ?> getAll() {
        return new HashMap<>();
    }
    
    @Override
    public String getString(String key, String defValue) {
        return defValue;
    }
    
    @Override
    public Editor edit() {
        return new EmptyEditor();
    }
    
    // All methods return defaults or no-ops
}
```

### Strategy Pattern

**Flush Strategy:**
```java
public interface FlushStrategy {
    boolean shouldFlush(int queueSize, long lastFlushTime);
}

public class TimeBasedFlushStrategy implements FlushStrategy {
    private final long mFlushInterval;
    
    @Override
    public boolean shouldFlush(int queueSize, long lastFlushTime) {
        long now = System.currentTimeMillis();
        return (now - lastFlushTime) >= mFlushInterval;
    }
}

public class SizeBasedFlushStrategy implements FlushStrategy {
    private final int mBatchSize;
    
    @Override
    public boolean shouldFlush(int queueSize, long lastFlushTime) {
        return queueSize >= mBatchSize;
    }
}
```

### Template Method Pattern

**Database Operations:**
```java
public abstract class DatabaseOperation<T> {
    protected abstract T performOperation(SQLiteDatabase db) throws Exception;
    
    public T execute() {
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            return performOperation(db);
        } catch (Exception e) {
            MPLog.e(LOGTAG, "Database operation failed", e);
            return getDefaultValue();
        } finally {
            if (db != null) {
                db.close();
            }
        }
    }
    
    protected abstract T getDefaultValue();
}
```

### Command Pattern

**Analytics Messages:**
```java
public static class EventDescription {
    // Command object for event tracking
}

public static class PeopleDescription {
    // Command object for people updates
}

public static class GroupDescription {
    // Command object for group updates
}

// Handler processes commands
private void handleMessage(Message msg) {
    switch (msg.what) {
        case ENQUEUE_EVENTS:
            processEventCommand((EventDescription) msg.obj);
            break;
        case ENQUEUE_PEOPLE:
            processPeopleCommand((PeopleDescription) msg.obj);
            break;
    }
}
```

### Defensive Copy Pattern

**Property Protection:**
```java
public void track(String eventName, JSONObject properties) {
    JSONObject props = null;
    if (properties != null) {
        // Defensive copy to prevent external modification
        try {
            props = new JSONObject(properties.toString());
        } catch (JSONException e) {
            MPLog.e(LOGTAG, "Failed to copy properties", e);
        }
    }
    // Use props safely
}
```

### Resource Management Pattern

**Try-With-Resources Alternative:**
```java
// Pre-Java 7 pattern still used for compatibility
Cursor cursor = null;
try {
    cursor = db.query(table, columns, selection, args, null, null, orderBy);
    // Process cursor
} finally {
    if (cursor != null) {
        cursor.close();
    }
}
```

These Java patterns demonstrate professional SDK development practices with emphasis on thread safety, immutability, clean interfaces, and defensive programming suitable for a widely-distributed library.