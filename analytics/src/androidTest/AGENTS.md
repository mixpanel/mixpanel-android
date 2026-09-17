# AGENTS.md — Instrumented Tests (`analytics/src/androidTest/`)

Directory-local guidance, additive to the root `AGENTS.md`. `CLAUDE.md` here imports this file.

## Scope

These are the SDK's **instrumented tests** — they run on a real device/emulator and validate
real SQLite, async timing, and framework integration. The SDK **also has JVM unit tests** in
`analytics/src/test/` (JUnit/Robolectric/Mockito, run via `:analytics:test`); put logic that
doesn't need a device there instead. Shared helpers live in `analytics/src/sharedTest/`.

## Test structure

The real capture idiom (from `MixpanelBasicTest`): a `BlockingQueue` fed by inline
overrides of the layers under test, wired in via `TestUtils.CleanMixpanelAPI`:

```java
final BlockingQueue<JSONObject> messages = new LinkedBlockingQueue<>();

final MPDbAdapter captureAdapter =
    new MPDbAdapter(context, MPConfig.getInstance(context, null)) {
      @Override
      public int addJSON(JSONObject message, String token, MPDbAdapter.Table table) {
        messages.add(message);
        return 1;
      }
    };

final AnalyticsMessages captureMessages =
    new AnalyticsMessages(context, MPConfig.getInstance(context, null)) {
      @Override
      public MPDbAdapter makeDbAdapter(Context context) { return captureAdapter; }
    };

MixpanelAPI mixpanel =
    new TestUtils.CleanMixpanelAPI(context, mMockPreferences, "Test token") {
      @Override
      protected AnalyticsMessages getAnalyticsMessages() { return captureMessages; }
    };
```

`TestUtils` (in `sharedTest/`) provides `CleanMixpanelAPI` (fresh state) and
`createMixpanelAPIWithMockHttpService(context, mockService)` for network-level mocking.
Use the instrumentation **target** context.

## Key patterns

**BlockingQueue for async (MANDATORY)** — never bare `Thread.sleep()` as the primary wait:

```java
mixpanel.track("TestEvent");
JSONObject message = messages.poll(2, TimeUnit.SECONDS);
assertNotNull("Event message should be queued within timeout", message);
assertEquals("TestEvent", message.getString("event"));
```

**Real SQLite** — exercise `MPDbAdapter` directly against the real DB (`addJSON`,
`generateDataString`, `cleanupEvents`); no mocking the database.

**Thread safety** — fan out N threads with a `CountDownLatch`, await, then drain the
queue and assert all events arrived.

**Error handling** — feed nulls/empty strings/invalid JSON; the SDK must never crash and
must remain functional afterward.

## Guidelines

- Real components over mocks; never mock the Android framework.
- Descriptive assertion messages; realistic timeouts (2–5 s).
- Clean state in `setUp` — `TestUtils.cleanUpMixpanelData(context)` wipes DB + prefs;
  `TestUtils.EmptyPreferences` stubs referrer prefs. Clean up in `tearDown`/`finally`.
- Test real workflows, edge cases, concurrency, persistence, error recovery —
  not implementation details, exact timing, or UI.
- Naming: `[Feature]Test.java`; methods like `testErrorHandling_NullInput_DoesNotCrash`.

## Running tests

**IMPORTANT**: run from the `:analytics` module (not `:analytics:mixpaneldemo`).
Class/method selection uses instrumentation runner args — `--tests` does NOT work for
connected tests.

```bash
# All instrumented tests
./gradlew :analytics:connectedAndroidTest

# One class / one method / several methods
./gradlew :analytics:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.mpmetrics.MixpanelBasicTest
./gradlew :analytics:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.mpmetrics.MixpanelBasicTest#testMethodName
./gradlew :analytics:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.mpmetrics.MixpanelBasicTest#testMethod1,testMethod2

# Combined coverage report (needs the device/emulator; what CI runs)
./gradlew :analytics:createDebugCoverageReport
```

These tests are the safety net for a critical SDK — comprehensive tests prevent production issues.
