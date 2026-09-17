# AGENTS.md — Core SDK Components (`com.mixpanel.android.mpmetrics`)

Directory-local guidance, additive to the root `AGENTS.md`. `CLAUDE.md` here imports this file.

## Component overview

The heart of the SDK lives here:
- **MixpanelAPI** — public API facade (events, people, groups, feature flags)
- **AnalyticsMessages** — message queue + worker `HandlerThread`; owns both the DB adapter and the HTTP poster
- **MPDbAdapter** — SQLite persistence layer
- **PersistentIdentity** — identity and super properties (SharedPreferences)
- **FeatureFlagManager** — flag caching/fetching (its own worker thread + network executor)

Network code (`HttpService`, `RemoteService`) lives in `../util/`, not here.

Note on visibility: several types here are deliberately public API surface
(`MixpanelAPI`, `MixpanelOptions`, `MPConfig`, `FeatureFlagOptions`, `MixpanelFlagVariant`,
`VariantLookupPolicy`, `DeviceIdProvider`, `SuperPropertyUpdate`, `ExceptionHandler`, …).
Keep **new** helpers package-private; every public type is compatibility surface.

## Critical rules

1. **MixpanelAPI** — maintain backward compatibility; every public method thread-safe and
   never throws. Tracking/mutating operations additionally null-check inputs, check
   `hasOptedOutTracking()`, and wrap work in try-catch. Plain accessors (`getToken()`,
   `getDistinctId()`, …) skip those checks by design, and opt-out control methods
   (`optInTracking()`) must run even while opted out. JavaDoc with examples on new methods.
2. **Thread safety** — dedicated lock objects (`private final Object mLock = new Object()`),
   never `synchronized (this)`.
3. **Thread boundaries** — public API on caller thread; message processing, DB, and tracking
   network I/O on the `AnalyticsWorker` HandlerThread; flag fetches on FeatureFlagManager's
   executor. Never block or touch the DB on the main thread.
4. **Message passing** — inside `AnalyticsMessages`, work is dispatched as Handler messages
   (`mWorker.runMessage(msg)`); external callers use its typed methods
   (`eventsMessage()`, `peopleMessage()`, `postToServer()`, …), not raw messages.
   ```java
   Message msg = Message.obtain();
   msg.what = ENQUEUE_EVENTS;
   msg.obj  = new EventDescription(event, properties, token);
   mWorker.runMessage(msg);
   ```
5. **Database operations** — `rawQuery`-based; always close cursors in `finally`.

## Common tasks

**New public API method:** overload for progressive disclosure; for tracking/mutating
methods, validate inputs + check opt-out + try-catch in `MixpanelAPI`; hand off via an
`AnalyticsMessages` typed method backed by a new message type with an immutable description
class; add tests.

**Database schema change:** increment `DATABASE_VERSION`; add migration in `onUpgrade`
(never drop existing tables/data); update the `Table` enum if needed; test the upgrade path.

**New configuration option:** add to `MPConfig` (read from manifest `metaData` with a
default), expose in `MixpanelOptions.Builder` if runtime-settable, document the manifest key.

## Testing

```bash
# Unit tests (JVM)
./gradlew :analytics:test

# Instrumented — class selection uses instrumentation runner args, NOT --tests
./gradlew :analytics:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.mpmetrics.MixpanelBasicTest
./gradlew :analytics:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.mpmetrics.PersistentIdentityTest
```

Use the BlockingQueue pattern for async assertions (poll with a timeout).

## Performance/behavior facts (verified)

- Event batching every 60 s (`FlushInterval` default), flush on background (configurable)
- HTTP timeouts are **hardcoded** in `HttpService` (2 s/30 s and 15 s/60 s connect/read pairs);
  retry is 3 attempts with 100 ms/200 ms delays before attempts 2 and 3 (no delay after the
  final attempt — the in-code "100ms, 200ms, 300ms" comment is stale)
- GZIP is opt-in via `MPConfig` (default off)
- Database cleanup is age-based; SharedPreferences values are cached in memory

## Do NOT modify without approval

- `DATABASE_VERSION` (requires migration testing)
- Public API method signatures (breaks compatibility)
- Message type constants (affects message processing)
- Table names or schemas (requires migration)

This is the core of a widely-used SDK — every change here affects thousands of apps.
