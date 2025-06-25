# GitHub Copilot Instructions - Mixpanel Android SDK

You are working on the Mixpanel Android SDK, a production library used by thousands of apps. Follow these critical patterns:

## Core Principles
- **NEVER crash the host app** - catch all exceptions and fail silently with logging
- **Thread-safe by design** - all public APIs must handle concurrent access
- **Minimal dependencies** - use Android/Java stdlib only, no external libraries
- **Defensive programming** - check nulls, validate inputs, handle edge cases

## Code Style
```java
// Package-private visibility for internals
class InternalHelper { } // NOT public class

// Member variables with 'm' prefix
private final Context mContext;
private final String mToken;

// Constants in CAPS_WITH_UNDERSCORES
private static final String LOGTAG = "MixpanelAPI";

// Synchronize on dedicated lock objects
private final Object mLock = new Object();
synchronized (mLock) { /* critical section */ }
```

## Architecture Rules
- Public API through `MixpanelAPI` class only
- Single `HandlerThread` for background work
- Message-based communication between threads
- Token-based singleton instances
- SQLite for persistence (no ORM)

## Error Handling
```java
// ALWAYS catch and log, never throw
try {
    riskyOperation();
} catch (Exception e) {
    MPLog.e(LOGTAG, "Operation failed", e);
    // Continue gracefully
}
```

## Threading Model
```java
// Queue work to background thread
Message msg = Message.obtain();
msg.what = ENQUEUE_EVENTS;
msg.obj = eventDescription;
mWorker.runMessage(msg);
```

## Testing
- **Instrumented tests only** - no unit tests
- Use `BlockingQueue` for async verification
- Test with real SQLite, not mocks
- Always provide timeout for async operations

## API Design
```java
// Progressive disclosure through overloading
public void track(String eventName) {
    track(eventName, null);
}

// Accept null for optional parameters
public void track(String eventName, JSONObject properties) {
    // properties may be null
}
```

## Android Patterns
- Always use application context to prevent leaks
- Check permissions defensively
- Handle all SDK versions gracefully
- No runtime permissions required

Remember: This SDK is critical infrastructure. Prioritize reliability over features.