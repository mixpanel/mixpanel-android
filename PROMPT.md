# Ralph Loop: Agent-Readiness Improvement

## Objective

Improve this project's btar agent-readiness score from the current baseline of 60 to **80+**.

Current breakdown: Type(30/30) + Lint(30/30) + Coverage(0/40) = 60/100.

The primary gap is **test coverage**. This project has no JVM unit tests — only instrumented tests that require an Android emulator. Your task is to add JVM unit tests using Robolectric and JUnit to boost coverage.

## Constraints

- **Never crash the host app** — all changes must pass `./gradlew build`
- **No new external dependencies** beyond Robolectric, JUnit, and Mockito (already common Android testing deps)
- **No breaking API changes** — public API surface must remain unchanged
- **Follow existing patterns** — read CLAUDE.md and AGENTS.md for conventions
- **Max 5 files per iteration** — make incremental, focused changes
- **Stay on branch** — all changes on `agent-readiness-improvements` branch

## Priority Order

1. **Add Robolectric test infrastructure** — Add test dependencies to `build.gradle`, create `src/test/` directory structure
2. **Add JVM unit tests** for pure-logic classes (no Android framework deps):
   - `PersistentIdentity` — identity management logic
   - `MPConfig` — configuration parsing
   - `FeatureFlagManager` — feature flag evaluation
   - `SessionMetadata` — session tracking logic
   - `MixpanelNotificationData` — notification data parsing
3. **Add JVM unit tests** for classes with light Android deps (use Robolectric):
   - `JsonUtils` — JSON serialization helpers
   - `MixpanelAPI` basic construction
   - `AnalyticsMessages` message formatting
4. **Improve coverage** on remaining uncovered paths

## Per-Iteration Steps

1. Run btar to get current score:
   ```bash
   node ../btar/dist/cli.js analyze . --json
   ```
2. Read the recommendations from btar output
3. Implement the highest-priority improvement (see priority order above)
4. Verify the build passes:
   ```bash
   ./gradlew build
   ```
5. Run the JVM tests:
   ```bash
   ./gradlew test
   ```
6. Save the new baseline if score improved:
   ```bash
   node ../btar/dist/cli.js analyze . --save-baseline
   ```

## Completion Criteria

When the btar score reaches **80 or higher**, output:

```
<promise>AGENT READY</promise>
```

## Key Files

- `build.gradle` — Add test dependencies here (testImplementation scope)
- `src/test/java/com/mixpanel/android/` — Create JVM tests here
- `src/main/java/com/mixpanel/android/mpmetrics/` — Main source code to test
- `src/main/java/com/mixpanel/android/util/` — Utility classes to test

## Test Dependencies to Add

```groovy
testImplementation 'junit:junit:4.13.2'
testImplementation 'org.robolectric:robolectric:4.11.1'
testImplementation 'org.mockito:mockito-core:5.8.0'
testImplementation 'androidx.test:core:1.5.0'
```

## Notes

- The btar tool measures JVM test coverage via `./gradlew test jacocoTestReport`
- JaCoCo XML reports are parsed from `build/reports/jacoco/test/jacocoTestReport.xml`
- You may need to add the JaCoCo Gradle plugin to `build.gradle` for coverage reporting
- Focus on classes with pure business logic first — they're easiest to test without Android framework mocks
