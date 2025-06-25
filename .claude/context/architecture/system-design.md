# Mixpanel Android SDK System Design

## Architecture Overview

The Mixpanel Android SDK implements a **Producer-Consumer Pattern** with persistent storage for reliable analytics event delivery.

```
┌─────────────────┐
│   Client App    │
│  (Your Code)    │
└────────┬────────┘
         │ API Calls
         ▼
┌─────────────────┐     ┌──────────────────┐
│  MixpanelAPI    │────▶│ MixpanelOptions  │
│  (Singleton)    │     │ (Configuration)  │
└────────┬────────┘     └──────────────────┘
         │ Events
         ▼
┌─────────────────┐     ┌──────────────────┐
│AnalyticsMessages│────▶│ Message Queue    │
│ (Producer)      │     │ (HandlerThread)  │
└────────┬────────┘     └────────┬─────────┘
         │                       │ Batch
         ▼                       ▼
┌─────────────────┐     ┌──────────────────┐
│  MPDbAdapter    │     │   HttpService    │
│  (SQLite)       │────▶│  (Network I/O)   │
└─────────────────┘     └────────┬─────────┘
                                 │
                                 ▼
                        ┌──────────────────┐
                        │ Mixpanel Servers │
                        └──────────────────┘
```

## Core Components

### 1. MixpanelAPI (Public Interface)
**Responsibilities:**
- SDK entry point and facade
- Instance management (one per token)
- API method validation
- Thread-safe public interface

**Key Design Decisions:**
- Singleton pattern with token-based instances
- All public methods thread-safe
- Minimal surface area for SDK consumers
- Graceful degradation on errors

### 2. AnalyticsMessages (Message Queue)
**Responsibilities:**
- Event queuing and batching
- Background thread management
- Flush scheduling
- Retry logic coordination

**Implementation Details:**
- Uses HandlerThread for background processing
- Message-based communication pattern
- Configurable flush intervals
- Automatic batching for efficiency

### 3. MPDbAdapter (Persistence Layer)
**Responsibilities:**
- SQLite database management
- Offline event storage
- Event retrieval for sending
- Data lifecycle management

**Database Schema:**
```sql
-- Events Table
CREATE TABLE events (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    data TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

-- People Updates Table  
CREATE TABLE people (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    data TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

-- Anonymous People Updates
CREATE TABLE anonymous_people (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    data TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

-- Groups Table
CREATE TABLE groups (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    data TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
```

### 4. PersistentIdentity (State Management)
**Responsibilities:**
- User identity persistence
- Super properties management
- Anonymous ID generation
- SharedPreferences wrapper

**Storage Strategy:**
- SharedPreferences for small data
- Synchronized access for thread safety
- Automatic migration between versions

### 5. HttpService (Network Layer)
**Responsibilities:**
- HTTP request execution
- GZIP compression
- SSL pinning support
- Connection management

**Network Strategy:**
- HttpURLConnection (no external dependencies)
- Configurable timeouts
- Automatic retry with backoff
- Response validation

## Data Flow Patterns

### Event Tracking Flow
```
1. App calls: mixpanel.track("Event", properties)
2. MixpanelAPI validates and enriches event
3. Event sent to AnalyticsMessages queue
4. Worker thread batches events
5. MPDbAdapter stores events in SQLite
6. HttpService sends batch to servers
7. On success: Remove from database
8. On failure: Retry with backoff
```

### Identity Management Flow
```
1. Anonymous ID generated on first launch
2. Stored in SharedPreferences
3. Can be upgraded to identified user
4. Alias links anonymous to identified
5. All events tagged with current ID
```

## Threading Model

### Main Thread
- All public API calls
- Quick validation only
- No I/O operations
- Immediate return to caller

### Worker Thread (HandlerThread)
- Single background thread
- Sequential message processing
- Database operations
- Network requests
- Retry scheduling

### Network Thread
- Spawned for HTTP requests
- Short-lived connections
- Returns result to worker thread

## Configuration Architecture

### Three-Level Configuration
1. **Compile-time Defaults** (MPConfig.java)
2. **AndroidManifest Meta-data** (XML)
3. **Runtime Options** (MixpanelOptions)

Priority: Runtime > Manifest > Defaults

### Configuration Parameters
- API endpoints
- Flush intervals
- Event limits
- Feature toggles
- Debug settings

## Error Handling Strategy

### Principles
1. **Never crash the host app**
2. **Log errors for debugging**
3. **Degrade gracefully**
4. **Retry with backoff**

### Implementation
```java
try {
    // Risky operation
} catch (Exception e) {
    MPLog.e(LOGTAG, "Operation failed", e);
    // Continue execution with fallback
}
```

## Security Considerations

### Data Protection
- HTTPS only communication
- Optional SSL pinning
- No sensitive data in logs
- Configurable data expiration

### Privacy Features
- Opt-out capability
- GDPR compliance helpers
- Data deletion APIs
- Anonymous tracking mode

## Performance Optimizations

### Batching Strategy
- Events batched by count (50) or time (60s)
- Automatic flush on app background
- Smart flush on low battery

### Memory Management
- Bounded in-memory queues
- Automatic data pruning
- Lazy initialization
- Weak references for callbacks

### Database Optimization
- Prepared statements
- Transaction batching
- Automatic vacuum
- Size-based cleanup

## Extension Points

### Custom Implementations
1. **MixpanelNetworkErrorListener** - Network error callbacks
2. **ExceptionHandler** - Crash reporting integration
3. **ResourceReader** - Custom resource loading

### Feature Flags System
- Separate feature flag API
- Cached locally with TTL
- Automatic refresh
- Delegate pattern for updates

## Scalability Considerations

### SDK Size
- Minimal dependencies
- ProGuard optimization
- No reflection usage
- Efficient serialization

### Host App Impact
- Single background thread
- Bounded memory usage
- Configurable limits
- No UI thread blocking

This architecture provides a robust, scalable foundation for analytics collection while maintaining excellent performance and reliability characteristics suitable for integration into any Android application.