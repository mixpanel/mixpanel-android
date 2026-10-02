# AGENTS.md — Mixpanel Android SDK

Canonical instructions for AI coding agents (Claude Code, Cursor, Copilot, Codex, etc.)
working on this repository. `CLAUDE.md` imports this file via `@AGENTS.md` — edit **this**
file; put Claude-specific additions (if ever needed) below the import in `CLAUDE.md`.

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
- Nested `AGENTS.md` files in `analytics/src/main/java/com/mixpanel/android/mpmetrics/`
  and `analytics/src/androidTest/` — directory-local detail, loaded when working there

## Project configuration

- Min SDK 21; Target/Compile SDK 34 (`:session-replay` builds with compileSdk 35)
- Gradle 9.3.1 (wrapper), AGP 8.13.2 (root `build.gradle` classpath), Kotlin 2.1.0
  (`:common` pins language level 2.0), JDK 17 toolchains (`:analytics:mixpaneldemo` targets Java 8)
- Runtime dependencies of `:analytics` are deliberately minimal (see `analytics/build.gradle`):
  `mixpanel-android-common`, `androidx.annotation`, `androidx.core`,
  `androidx.lifecycle:lifecycle-process`, `io.github.jamsesso:json-logic-java`
  (plus a `gson` pin for its transitive JSON dep). **Do not add new runtime deps.**

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
- **`:session-replay`** (`session-replay/`) — published as
  `com.mixpanel.android:mixpanel-android-session-replay`, versioned independently
  (own `gradle.properties`, CHANGELOG, README, and CI: `.github/workflows/session-replay-ci.yml`).
- **`:analytics:mixpaneldemo`** and **`:session-replay:sessionreplaydemo`** — sample apps, not published.
- **`build-logic/`** — included build with the `mixpanel.maven-publish` and `mixpanel.ktlint`
  convention plugins used by the Kotlin modules.

For local iteration across modules, swap the Maven dep for the commented-out `project(':...')`
line in the consumer's build script (both `analytics/build.gradle` and
`openfeature-provider/build.gradle.kts` carry one).

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

# Build / install the demo app
./gradlew :analytics:mixpaneldemo:build
./gradlew :analytics:mixpaneldemo:installDebug

# Docs & coverage
./gradlew :analytics:androidJavadocs
./gradlew :analytics:createDebugCoverageReport   # combined coverage; needs device/emulator (CI uses this)
./gradlew :analytics:jacocoTestReport            # custom unit-test coverage task (jacoco 0.8.12)

# Lint — review analytics/build/reports/lint-results-debug.html
./gradlew :analytics:lint

# AnimalSniffer — fails on Java 9+ APIs and unguarded Android calls above minSdk.
# Report: analytics/build/reports/animalsniffer/release.text
./gradlew :analytics:animalsnifferRelease
```

### Testing approach

- **Test source sets:**
  - `analytics/src/test/` — **unit tests** (JVM via `:analytics:test`; JUnit 4, Robolectric 4.11.1, Mockito 5.x).
  - `analytics/src/androidTest/` — **instrumented tests** (AndroidJUnit4 + espresso/truth/mockito-android, real device/emulator).
  - `analytics/src/sharedTest/` — helpers wired into both source sets.
- Async behavior is verified with the **BlockingQueue pattern**; `TestUtils` provides mocks.
- Real database testing (not mocked). Thread-safety validation belongs in instrumented tests.

## Core principles (MANDATORY)

1. **NEVER CRASH THE HOST APP** — wrap operations in try-catch, log via `MPLog`, fail silently, never re-throw.
2. **THREAD SAFETY** — every public API must handle concurrent access. Use dedicated lock objects, not `this`.
3. **MINIMAL DEPENDENCIES** — never add a runtime dependency; the small allowed set is listed under Project configuration.
4. **BACKWARD COMPATIBILITY** — never break existing public APIs.
5. **DEFENSIVE PROGRAMMING** — null-check, validate inputs, handle edge cases, degrade gracefully.

## Conventions

- **Visibility:** prefer package-private for internals (a number of existing internals are historically public — don't add new public classes without cause).
- **Fields:** `private final` with `m` prefix (`mContext`). Static nested classes for data models.
- **Context:** always application context (`context.getApplicationContext()`); `WeakReference` for activity/callback refs.
- **Concurrency:** background work runs on dedicated `HandlerThread`s (`AnalyticsWorker` in `AnalyticsMessages`; a second in `FeatureFlagManager`, which also owns a single-thread network executor) with `Message`-based dispatch. No `Service`/`ContentProvider` components. Use dedicated lock objects (not `this`); prefer synchronized blocks over synchronized methods.
- **Resources:** close `Cursor`s and DB handles in `finally`; lazy-init expensive objects.
- **Database:** direct SQLite (no ORM), enum-based table management, age-based cleanup. Queries are built with `rawQuery`.
- **API design:** singleton keyed by instance name (or token) + app context; builder-style `MixpanelOptions`; fluent People/Group ops; method overloading for progressive disclosure.
- **Config hierarchy:** Runtime (`MixpanelOptions`) > Manifest meta-data > compile-time defaults (`MPConfig`).

## Architecture

Producer-consumer with persistent storage. There are **two network paths**:

```
User code → MixpanelAPI ─ tracking ─→ AnalyticsMessages ─→ MPDbAdapter (SQLite queue)
                        │                              └─→ HttpService ─→ Mixpanel servers
                        └─ feature flags ─→ FeatureFlagManager ─→ HttpService (no SQLite)
```

**Respect the layering:** `MixpanelAPI` never performs HTTP itself, and tracking never
bypasses `AnalyticsMessages`/`MPDbAdapter`. `AnalyticsMessages` owns *both* the DB adapter
and its poster (`MPDbAdapter` never talks to the network). The only `performRequest`
callers are `AnalyticsMessages` and `FeatureFlagManager`.

- **MixpanelAPI** — singleton entry point (`getInstance()`); events, people, groups, feature flags.
- **AnalyticsMessages** — user-thread↔background message queue; batching, retry, offline.
- **MPDbAdapter** — SQLite persistence and offline queue.
- **PersistentIdentity** — distinct/anonymous IDs, super properties (SharedPreferences).
- **HttpService** (`util/`) — HTTP client; GZIP is opt-in via `MPConfig` (default off);
  hardcoded timeouts; 3-attempt retry with short linear backoff.
- **FeatureFlagManager** — TTL-based flag caching (in-memory + SharedPreferences blob),
  refreshed on demand (identify/reset, first foreground, TTL expiry) — no periodic timer.

Implementation notes: events batch every ~60s (`FlushInterval`) or on app background;
SQLite queues offline; automatic lifecycle events are configurable; a minimal consumer
ProGuard rule ships in `analytics/proguard.txt` (release builds do not minify).

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

// Threading — queue to the background thread (internal to AnalyticsMessages;
// callers use its typed methods like eventsMessage()/peopleMessage()/postToServer())
Message msg = Message.obtain();
msg.what = ENQUEUE_EVENTS;
msg.obj  = new EventDescription(event, properties, token);
mWorker.runMessage(msg);
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

**PR checklist:** no new runtime deps · try-catch around new operations · thread safety
verified · tests added (unit and/or instrumented) · no public-API breaks · JavaDoc on public
methods · `m`-prefixed fields · application context only.

## Release process

Semantic versioning (X.Y.Z) per module, published to Maven Central via the Central Portal.
**Releases are driven entirely by GitHub Actions — there is no local release script.**

- `VERSION_NAME` lives in each module's `gradle.properties`
  (`analytics/`, `common/`, `openfeature-provider/`, `session-replay/`).
- `.github/workflows/prepare-release.yml` — bumps `VERSION_NAME`, updates README,
  generates the changelog, and opens a release PR.
- `.github/workflows/release-maven-central.yml` — parameterized by module (`inputs.module`,
  per-module tag prefixes): validates the version, builds, tests, publishes to OSSRH staging,
  triggers the Portal upload (`publishing_type=user_managed`), and creates a draft GitHub
  release/tag.
- Credentials (CI secrets → env): `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`
  (secret `MAVEN_CENTRAL_TOKEN`), `SIGNING_KEY` (`GPG_PRIVATE_KEY`),
  `SIGNING_PASSWORD` (`GPG_PASSPHRASE`) — wired in
  `build-logic/convention/src/main/kotlin/MavenPublishConventionPlugin.kt`.
- Deployments appear at https://central.sonatype.com/publishing/deployments; finish with a
  manual release from the Portal UI.
- Published coordinates: `com.mixpanel.android:mixpanel-android`,
  `…:mixpanel-android-common`, `…:mixpanel-android-openfeature`,
  `…:mixpanel-android-session-replay`.
