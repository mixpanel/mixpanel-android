# AGENTS.md — Mixpanel Android SDK

Canonical instructions for AI coding agents (Claude Code, Cursor, Copilot, Codex, etc.)
working on this repository. `CLAUDE.md` is a symlink to this file — edit **this** file only.

**Mixpanel Android SDK** is a production analytics library used by thousands of Android
apps. It prioritizes **reliability, thread safety, and backward compatibility above all
else.** Reliability > features. When in doubt, follow existing patterns.

## Context map — read on demand, not all at once

This file is the router. Open the deeper docs only when the task needs them:

- `.claude/context/codebase-map.md` — where things live (concern → file)
- `.claude/context/discovered-patterns.md` — detailed coding standards
- `.claude/context/architecture/system-design.md` — system architecture
- `.claude/context/workflows/` — feature-development, testing, release workflows
- `.cursor/rules/` — the same rules as Cursor `.mdc` enforcement files
- `.github/copilot-instructions.md` — Copilot persistent guidance

## Project configuration

- Min SDK 21, Target/Compile SDK 34
- Android Gradle Plugin 8.13.2, Kotlin 2.1.0, JDK 17
- No external runtime dependencies beyond `androidx.annotation` / `androidx.core` —
  Android SDK and Java standard library only.

### Subprojects

- **`:analytics`** (`analytics/`) — main `mixpanel-android` SDK. Consumes `:common` via its
  **published Maven coordinate** (`com.mixpanel.android:mixpanel-android-common:X.Y.Z`), not
  as a `project(':common')` dependency, so `:common` must be released before the main SDK
  can pick up changes. Buys back independent snapshot publishing for `:common`.
- **`:common`** — published as `com.mixpanel.android:mixpanel-android-common`. Holds
  `MixpanelEventBridge` (Kotlin `SharedFlow` cross-SDK event dispatcher) and a Kotlin
  JsonLogic implementation. Versioned independently (own `gradle.properties`).
- **`:openfeature-provider`** — published as `com.mixpanel.android:mixpanel-android-openfeature`.
  Consumes the main SDK via its published Maven coordinate.
- **`:analytics:mixpaneldemo`** (`analytics/mixpaneldemo/`) — sample app, not published.

For local iteration on `:common`, swap the Maven dep for the commented-out `project(':...')`
line in the consumer's build script (the workflow `openfeature-provider/build.gradle.kts` uses).

## Environment setup

```bash
./gradlew --version   # Gradle with JDK 17
adb devices           # connected device/emulator (needed for instrumented tests only)
./gradlew clean build
```

## Build & test commands

```bash
# Build the library
./gradlew :analytics:build

# Unit tests (JVM, no device) — analytics/src/test/, JUnit/Robolectric
./gradlew :analytics:test

# Instrumented tests (require a device/emulator) — analytics/src/androidTest/, AndroidJUnit4
# IMPORTANT: run from :analytics, NOT :analytics:mixpaneldemo
./gradlew :analytics:connectedAndroidTest

# A single instrumented class / method / methods
./gradlew :analytics:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.mpmetrics.TestClassName
./gradlew :analytics:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.mpmetrics.TestClassName#testMethodName
./gradlew :analytics:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.mpmetrics.TestClassName#testMethod1,testMethod2

# Install to local Maven / build demo
./gradlew :analytics:install
./gradlew :analytics:mixpaneldemo:build
./gradlew :analytics:mixpaneldemo:installDebug

# Docs & coverage
./gradlew :analytics:androidJavadocs
./gradlew :analytics:createDebugCoverageReport

# Lint — review analytics/build/reports/lint-results-debug.html
./gradlew :analytics:lint

# AnimalSniffer — fails on Java 9+ APIs and unguarded Android calls above minSdk.
# Report: analytics/build/reports/animalsniffer/release.text
./gradlew :analytics:animalsnifferRelease
```

### Testing approach

- **Two test source sets exist:**
  - `analytics/src/test/` — **unit tests** (JUnit/Robolectric, run on the JVM via `:analytics:test`).
  - `analytics/src/androidTest/` — **instrumented tests** (AndroidJUnit4, real device/emulator).
- Async behavior is verified with the **BlockingQueue pattern**; `TestUtils` provides mocks.
- Real database testing (not mocked). Thread-safety validation belongs in instrumented tests.

## Core principles (MANDATORY)

1. **NEVER CRASH THE HOST APP** — wrap operations in try-catch, log via `MPLog`, fail silently, never re-throw.
2. **THREAD SAFETY** — every public API must handle concurrent access. Use dedicated lock objects, not `this`.
3. **NO EXTERNAL DEPENDENCIES** — Android SDK + Java stdlib only.
4. **BACKWARD COMPATIBILITY** — never break existing public APIs.
5. **DEFENSIVE PROGRAMMING** — null-check, validate inputs, handle edge cases, degrade gracefully.

## Conventions

- **Visibility:** package-private by default; keep internals non-public. No new public classes without cause.
- **Fields:** `private final` with `m` prefix (`mContext`). Static nested classes for data models.
- **Context:** always application context (`context.getApplicationContext()`); `WeakReference` for activity/callback refs.
- **Concurrency:** single background `HandlerThread` + `Message`-based dispatch. No `Service`/`ContentProvider`. Prefer synchronized blocks over synchronized methods.
- **Resources:** close `Cursor`s and DB handles in `finally`; lazy-init expensive objects.
- **Database:** direct SQLite (no ORM), enum-based table management, prepared statements, age-based cleanup.
- **API design:** singleton per token; builder-style `MixpanelOptions`; fluent People/Group ops; method overloading for progressive disclosure.
- **Config hierarchy:** Runtime (`MixpanelOptions`) > Manifest meta-data > compile-time defaults (`MPConfig`).

## Architecture

Producer-consumer with persistent storage. **Respect the layering — never skip a layer**
(e.g. `MixpanelAPI` must not call `HttpService` directly):

```
User code → MixpanelAPI → AnalyticsMessages → MPDbAdapter → HttpService → Mixpanel servers
```

- **MixpanelAPI** — singleton entry point (`getInstance()`); events, people, groups, feature flags.
- **AnalyticsMessages** — user-thread↔background message queue; batching, retry, offline.
- **MPDbAdapter** — SQLite persistence and offline queue.
- **PersistentIdentity** — distinct/anonymous IDs, super properties (SharedPreferences).
- **HttpService** (`util/`) — HTTP with GZIP, configurable timeouts/retry.
- **FeatureFlagManager** — feature-flag loading/caching.

Implementation notes: events batch every ~60s or on background; SQLite queues offline;
feature flags cache and refresh periodically; automatic lifecycle events are configurable;
ProGuard rules ship in `analytics/proguard.txt`.

## Working on this codebase

**Good for autonomous work:** test coverage, systematic refactors (defensive null checks,
resource cleanup, tracing logs), JavaDoc/examples, mechanical style conformance.

**Needs a human:** API design, breaking changes, architecture changes, performance work
without metrics, anything UI/UX (this is a library).

**Common pitfalls:** Activity context (use application context); synchronizing on `this`;
unclosed `Cursor`s; forgetting BlockingQueue for async assertions; ignoring the config hierarchy.

### Illustrative patterns

```java
// Error handling — always wrap, log, continue. NEVER re-throw.
try { riskyOperation(); }
catch (Exception e) { MPLog.e(LOGTAG, "Operation failed", e); }

// Threading — queue to the background thread
Message msg = Message.obtain();
msg.what = ENQUEUE_EVENTS;
msg.obj  = new EventDescription(event, properties, token);
mMessages.enqueueMessage(msg);
```

```java
// Async test — BlockingQueue with a timeout
mMixpanel.track("Event");
String message = mMessages.poll(2, TimeUnit.SECONDS);
assertNotNull("Should receive message", message);
```

## Validation before opening a PR

1. `./gradlew clean :analytics:build` — no errors/warnings.
2. `./gradlew :analytics:test` — unit tests pass.
3. `./gradlew :analytics:connectedAndroidTest` — instrumented tests pass (device/emulator).
4. `./gradlew :analytics:lint` and `./gradlew :analytics:animalsnifferRelease` — clean.
5. Manually verify via the demo app when behavior changed.

**PR checklist:** no new external deps · try-catch around new operations · thread safety
verified · tests added (unit and/or instrumented) · no public-API breaks · JavaDoc on public
methods · `m`-prefixed fields · application context only.

## Release process

Semantic versioning (X.Y.Z), published to Maven Central via the Central Portal.

- Version lives in each module's `gradle.properties` as `VERSION_NAME`
  (main SDK: `analytics/gradle.properties`).
- `./release.sh [version]` updates versions + README, builds and publishes to OSSRH staging,
  uploads to the Portal, tags git, and bumps to the next snapshot.
- Requires `CENTRAL_PORTAL_TOKEN` / `CENTRAL_PORTAL_PASSWORD` (or the `centralPortalToken` /
  `centralPortalPassword` Gradle properties in `~/.gradle/gradle.properties`).
- Deployments appear at https://central.sonatype.com/publishing/deployments; a manual release
  from the Portal UI is required unless automatic publishing is enabled.
- CI: the `publish-maven.yml` workflow; Portal tokens stored as repo secrets.
- Published coordinate: `com.mixpanel.android:mixpanel-android:X.Y.Z`.
