# CLAUDE.md - Mixpanel Core Components

This file provides specific guidance for working with core SDK components in the `com.mixpanel.android.mpmetrics` package.

## Component Overview

This package contains the heart of the Mixpanel Android SDK:
- **MixpanelAPI** - Public entry point (the ONLY public class)
- **AnalyticsMessages** - Message queue and worker thread management
- **MPDbAdapter** - SQLite persistence layer
- **PersistentIdentity** - Identity and super properties management
- **HttpService** - Network communication layer

## Critical Patterns for Core Components

### 1. Visibility Rules
```java
// WRONG - Making internal classes public
public class NewHelper {  // NO!

// CORRECT - Package-private by default
class NewHelper {  // YES!
```

### 2. Thread Safety Requirements
All classes in this package MUST be thread-safe:
```java
// ALWAYS use dedicated lock objects
private final Object mLock = new Object();

// NEVER use 'this' for synchronization
synchronized (mLock) {  // CORRECT
    // critical section
}
```

### 3. Never Crash the Host App
```java
// EVERY public method must be defensive
public void track(String event, JSONObject properties) {
    try {
        if (hasOptedOut()) {
            return;
        }
        if (event == null) {
            MPLog.e(LOGTAG, "Event cannot be null");
            return;
        }
        // implementation
    } catch (Exception e) {
        MPLog.e(LOGTAG, "Failed to track event", e);
    }
}
```

### 4. Message Passing Pattern
When modifying AnalyticsMessages or worker thread behavior:
```java
// Use Handler messages, not direct method calls
Message msg = Message.obtain();
msg.what = ENQUEUE_EVENTS;
msg.obj = new AnalyticsMessageDescription(token, event);
mWorker.runMessage(msg);
```

### 5. Database Operations
When working with MPDbAdapter:
```java
Cursor cursor = null;
try {
    cursor = db.query(...);
    // use cursor
} finally {
    if (cursor != null) {
        cursor.close();
    }
}
```

## Component-Specific Guidelines

### MixpanelAPI
- This is the ONLY public class - guard its API carefully
- Every public method needs null checks and opt-out checks
- Changes here affect ALL SDK users
- Maintain backward compatibility

### AnalyticsMessages
- Single HandlerThread for all background work
- Never block the main thread
- Batch operations for efficiency
- Handle offline gracefully

### MPDbAdapter
- Direct SQLite usage (no ORM)
- Always use transactions for bulk operations
- Clean up old data automatically
- Handle database upgrades carefully

### PersistentIdentity
- Thread-safe SharedPreferences access
- Lazy loading of values
- Cache for performance
- Never lose user identity

### HttpService
- Configurable timeouts
- Automatic retry with backoff
- GZIP compression
- Never expose raw responses

## Testing Requirements

All changes to core components require instrumented tests:
```java
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ComponentTest {
    private BlockingQueue<String> mMessages;
    
    @Before
    public void setUp() {
        mMessages = new LinkedBlockingQueue<>();
    }
    
    @Test
    public void testAsyncOperation() throws InterruptedException {
        // Trigger async operation
        api.track("test");
        
        // Wait for completion
        String result = mMessages.poll(5, TimeUnit.SECONDS);
        assertNotNull(result);
    }
}
```

## Common Pitfalls

### DON'T
- Create new public classes
- Throw exceptions from public methods
- Use Activity context (memory leaks)
- Access database on main thread
- Create new threads (use HandlerThread)

### DO
- Keep classes package-private
- Catch and log all exceptions
- Use application context
- Batch database operations
- Use message passing for async work

## Making Changes

1. **Before modifying**, understand the component's role in the system
2. **Maintain thread safety** - these are core components
3. **Test on real devices** - emulators hide timing issues
4. **Consider backward compatibility** - SDK is widely used
5. **Update tests** - every change needs test coverage

## Performance Considerations

- Event batching happens every 60 seconds
- Database cleanup runs periodically
- HTTP requests timeout after 10 seconds
- SharedPreferences cached in memory
- Minimize synchronization scope

Remember: These core components are the foundation of the SDK. Changes here have the highest impact and risk. Be extra careful with thread safety, error handling, and backward compatibility.