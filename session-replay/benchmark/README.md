# Wireframe traversal performance

`WireframeTraversalPerformanceTest` measures the incremental main-thread cost
of collecting wireframes during the hierarchy walk that already detects mask
regions. It runs disabled and enabled traversals as alternating pairs over the
same attached 250-row hierarchy and verifies that masking output is identical.
It contains separate methods for classic Android Views and for a real Jetpack
Compose semantics tree.

The test is ignored by standard instrumentation runs. With a physical device
attached, opt in explicitly and run against release bytecode:

```bash
./gradlew :session-replay:connectedReleaseAndroidTest \
  -PmixpanelTestBuildType=release \
  -Pandroid.testInstrumentationRunnerArguments.runWireframeBenchmark=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.sessionreplay.sensitive_views.WireframeTraversalPerformanceTest
```

To run only Compose, append the method name to the runner argument:

```bash
-Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.sessionreplay.sensitive_views.WireframeTraversalPerformanceTest#shouldKeepComposeWireframeTraversalWithinFrameBudget
```

If the Android Gradle Plugin exposes the task under the generic name on the
installed version, use `:session-replay:connectedAndroidTest` with the same
properties instead.

The release guardrails are a p95 paired overhead of at most **2 ms** and a p95
wireframe-enabled traversal below **16.667 ms**. The test prints a
`WIREFRAME_TRAVERSAL_BENCHMARK_JSON=...` log line. Run five times on an older
physical device, let it cool between runs, and archive all five lines with the
device model, OS version, commit SHA, and Gradle/JDK versions.

Keep the device awake and past its keyguard while the test runs. Check whether
ADB reports USB or AC power before selecting the corresponding `svc power
stayon` mode. The test
rejects a hidden hierarchy instead of publishing misleading zero-work timing.

Save individual runs locally under ignored `session-replay/benchmark/results/`.
They are useful raw evidence, but are deliberately excluded from pull requests.
