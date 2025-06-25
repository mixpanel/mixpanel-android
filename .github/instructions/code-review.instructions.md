# Code Review Instructions - Mixpanel Android SDK

When reviewing code changes, check for these critical items:

## Security & Stability
- [ ] **No uncaught exceptions** - All operations wrapped in try-catch
- [ ] **No Activity leaks** - Using application context only
- [ ] **Thread-safe** - Proper synchronization on shared state
- [ ] **No blocking operations** - Network/disk I/O on background thread

## API Design
- [ ] **Backwards compatible** - No breaking changes to public API
- [ ] **Defensive inputs** - Handles null/empty/invalid gracefully
- [ ] **Progressive disclosure** - Simple overloads for common cases

## Code Quality
- [ ] **Package-private classes** - Not exposing internals
- [ ] **Final fields** - Immutable where possible
- [ ] **Member prefix 'm'** - Consistent naming convention
- [ ] **Resource cleanup** - Cursors/databases closed in finally

## Testing
- [ ] **Instrumented tests added** - For new features
- [ ] **Async verification** - Using BlockingQueue pattern
- [ ] **Thread safety tested** - Concurrent access validated
- [ ] **Error cases tested** - Invalid inputs handled

## Common Issues to Flag

### Memory Leaks
```java
// BAD - Leaks Activity
private Context mContext;

// GOOD - Application context
private final Context mContext;
mContext = context.getApplicationContext();
```

### Threading
```java
// BAD - Direct thread creation
new Thread(() -> { }).start();

// GOOD - Use HandlerThread
mWorker.runMessage(msg);
```

### Error Handling
```java
// BAD - Throws to caller
public void track(String event) throws Exception {

// GOOD - Catch and log
try {
    // operation
} catch (Exception e) {
    MPLog.e(LOGTAG, "Failed", e);
}
```

### Database
```java
// BAD - No cleanup
Cursor cursor = db.query(...);

// GOOD - Finally block
Cursor cursor = null;
try {
    cursor = db.query(...);
} finally {
    if (cursor != null) cursor.close();
}
```

## Performance Considerations
- Lazy initialization used appropriately
- Database operations batched
- Minimal object allocation in hot paths
- No reflection or dynamic class loading

## Documentation
- Public methods have JavaDoc
- Complex logic has explanatory comments
- Examples provided for new APIs
- Deprecation includes migration path